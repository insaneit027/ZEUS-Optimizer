package com.zeus.optimizer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Varre o armazenamento compartilhado e separa o que é seguro apagar
 * do que o usuário precisa revisar. Nunca entra em Android/data e Android/obb.
 */
final class Scanner {

    interface Progress {
        void update(int files, String path);
    }

    static final String[] TYPE_NAMES = {"Fotos", "Vídeos", "Áudios", "Documentos", "APKs", "Compactados", "Outros"};
    static final int T_IMG = 0, T_VID = 1, T_AUD = 2, T_DOC = 3, T_APK = 4, T_ZIP = 5, T_OTHER = 6;

    static final class Category {
        final String icon;
        final String title;
        final String desc;
        final boolean safe;
        final List<File> files = new ArrayList<>();
        long bytes;

        Category(String icon, String title, String desc, boolean safe) {
            this.icon = icon;
            this.title = title;
            this.desc = desc;
            this.safe = safe;
        }
    }

    static final class Result {
        final List<Category> categories = new ArrayList<>();
        final Map<File, Long> sizes = new HashMap<>();
        final long[] typeBytes = new long[TYPE_NAMES.length];
        final int[] typeCount = new int[TYPE_NAMES.length];
        int scanned;
        long totalBytes;

        long sizeOf(File f) {
            Long l = sizes.get(f);
            return l == null ? 0 : l;
        }

        long safeBytes() {
            long b = 0;
            for (Category c : categories) if (c.safe) b += c.bytes;
            return b;
        }

        long reviewBytes() {
            long b = 0;
            for (Category c : categories) if (!c.safe) b += c.bytes;
            return b;
        }

        boolean isEmpty() {
            for (Category c : categories) if (!c.files.isEmpty()) return false;
            return true;
        }

        void remove(Set<File> gone) {
            for (Category c : categories) {
                c.files.removeIf(gone::contains);
                long b = 0;
                for (File f : c.files) b += sizeOf(f);
                c.bytes = b;
            }
        }
    }

    private static final long MB = 1024L * 1024;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private final Category apk = new Category("📦", "Instaladores APK",
            "Arquivos .apk servem só para instalar apps. Apagar não desinstala nada.", true);
    private final Category thumbs = new Category("🖼", "Miniaturas da galeria",
            "Cache de miniaturas (.thumbnails). O Android recria quando precisar.", true);
    private final Category temp = new Category("🧾", "Temporários e logs",
            "Downloads incompletos (.part, .crdownload), arquivos .tmp e registros .log.", true);
    private final Category trash = new Category("🗑", "Lixeira de fotos",
            "Arquivos que já foram para a lixeira (.trashed). Seriam apagados depois de qualquer jeito.", true);
    private final Category empty = new Category("📁", "Pastas vazias",
            "Pastas sem nenhum arquivo dentro.", true);
    private final Category dup = new Category("👯", "Arquivos duplicados",
            "Cópias idênticas. A cópia mais antiga de cada arquivo é mantida e não aparece aqui.", false);
    private final Category waSent = new Category("💬", "Mídia enviada pelo WhatsApp",
            "Cópias das fotos e vídeos que você enviou. O original normalmente continua na galeria.", false);
    private final Category waBackup = new Category("💾", "Backups antigos do WhatsApp",
            "Backups locais com data no nome. O backup mais recente (msgstore.db) não é tocado.", false);
    private final Category oldDl = new Category("⏳", "Downloads antigos (+90 dias)",
            "Arquivos da pasta Download que não são mexidos há mais de 3 meses.", false);
    private final Category large = new Category("🐘", "Arquivos grandes (+100 MB)",
            "Vídeos, backups e outros arquivos pesados. Confira se ainda precisa deles.", false);

    private final File root;
    private final String rootPath;
    private final boolean deep;
    private final Progress progress;
    private final Result result = new Result();
    private final List<File> pending = new ArrayList<>();
    private final Set<File> dupSet = new HashSet<>();
    private final long now = System.currentTimeMillis();
    private long lastReport;

    /** @param deep se false, faz só a varredura rápida (sem procurar duplicados). */
    Scanner(File root, boolean deep, Progress progress) {
        this.root = root;
        this.rootPath = root.getAbsolutePath();
        this.deep = deep;
        this.progress = progress;
    }

    Result scan() {
        walk(root, 0, false);
        if (deep) findDuplicates();
        for (File f : pending) {
            if (dupSet.contains(f)) continue;
            long len = result.sizeOf(f);
            String rel = relative(f).toLowerCase(Locale.ROOT);
            if (len >= 100 * MB) {
                add(large, f, len);
            } else if (rel.startsWith("/download") && now - f.lastModified() > 90 * DAY) {
                add(oldDl, f, len);
            }
        }
        Collections.addAll(result.categories, apk, thumbs, temp, trash, empty, dup, waSent, waBackup, oldDl, large);
        return result;
    }

    private String relative(File f) {
        String p = f.getAbsolutePath();
        return p.length() > rootPath.length() ? p.substring(rootPath.length()) : "";
    }

    private void walk(File dir, int depth, boolean inThumbs) {
        if (depth > 16) return;
        String rel = relative(dir).toLowerCase(Locale.ROOT);
        if (rel.startsWith("/android/data") || rel.startsWith("/android/obb")) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        if (list.length == 0) {
            if (depth > 1 && !rel.startsWith("/android")) add(empty, dir, 0);
            return;
        }
        boolean th = inThumbs || dir.getName().equalsIgnoreCase(".thumbnails");
        for (File f : list) {
            try {
                if (Files.isSymbolicLink(f.toPath())) continue;
            } catch (Exception e) {
                continue;
            }
            if (f.isDirectory()) walk(f, depth + 1, th);
            else file(f, rel, th);
        }
    }

    private void report(String where) {
        long t = System.currentTimeMillis();
        if (t - lastReport > 200 && progress != null) {
            lastReport = t;
            progress.update(result.scanned, where);
        }
    }

    static int typeOf(String n) {
        int dot = n.lastIndexOf('.');
        String e = dot >= 0 ? n.substring(dot + 1) : "";
        switch (e) {
            case "jpg": case "jpeg": case "png": case "webp": case "heic": case "heif": case "gif": case "bmp": case "dng":
                return T_IMG;
            case "mp4": case "mkv": case "3gp": case "mov": case "avi": case "webm": case "m4v":
                return T_VID;
            case "mp3": case "m4a": case "aac": case "ogg": case "opus": case "wav": case "flac": case "amr":
                return T_AUD;
            case "pdf": case "doc": case "docx": case "xls": case "xlsx": case "ppt": case "pptx": case "txt": case "epub": case "odt":
                return T_DOC;
            case "apk": case "apks": case "xapk":
                return T_APK;
            case "zip": case "rar": case "7z": case "tar": case "gz":
                return T_ZIP;
            default:
                return T_OTHER;
        }
    }

    private void file(File f, String relDir, boolean inThumbs) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.equals(".nomedia")) return;
        result.scanned++;
        report(relDir.isEmpty() ? "/" : relDir);
        long len = f.length();
        int type = typeOf(n);
        result.typeBytes[type] += len;
        result.typeCount[type]++;
        result.totalBytes += len;

        if (inThumbs) { add(thumbs, f, len); return; }
        if (n.startsWith(".trashed-")) { add(trash, f, len); return; }
        if (type == T_APK) { add(apk, f, len); return; }
        if (n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".crdownload")
                || n.endsWith(".part") || n.endsWith(".partial") || n.endsWith(".log")) {
            add(temp, f, len);
            return;
        }
        boolean whatsapp = relDir.contains("/whatsapp/") || relDir.contains("/whatsapp business/");
        if (whatsapp && relDir.endsWith("/databases") && n.matches("msgstore-\\d{4}-\\d{2}-\\d{2}.*")) {
            add(waBackup, f, len);
            return;
        }
        if (whatsapp && relDir.contains("/media/") && relDir.endsWith("/sent")) {
            add(waSent, f, len);
            return;
        }
        result.sizes.put(f, len);
        pending.add(f);
    }

    private void findDuplicates() {
        Map<Long, List<File>> bySize = new HashMap<>();
        for (File f : pending) {
            long len = result.sizeOf(f);
            if (len >= 256 * 1024) bySize.computeIfAbsent(len, k -> new ArrayList<>()).add(f);
        }
        for (List<File> group : bySize.values()) {
            if (group.size() < 2) continue;
            report("Procurando duplicados...");
            Map<String, List<File>> byHash = new HashMap<>();
            for (File f : group) {
                String h = hash(f);
                if (h != null) byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(f);
            }
            for (List<File> same : byHash.values()) {
                if (same.size() < 2) continue;
                Collections.sort(same, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 1; i < same.size(); i++) {
                    File f = same.get(i);
                    add(dup, f, result.sizeOf(f));
                    dupSet.add(f);
                }
            }
        }
    }

    private static String hash(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            long len = raf.length();
            byte[] buf = new byte[64 * 1024];
            if (len <= 16 * MB) {
                readRange(raf, md, buf, 0, len);
            } else {
                readRange(raf, md, buf, 0, 4 * MB);
                readRange(raf, md, buf, len / 2, 4 * MB);
                readRange(raf, md, buf, len - 4 * MB, 4 * MB);
            }
            StringBuilder sb = new StringBuilder().append(len).append(':');
            for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void readRange(RandomAccessFile raf, MessageDigest md, byte[] buf, long start, long count)
            throws IOException {
        raf.seek(start);
        long left = count;
        while (left > 0) {
            int r = raf.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) break;
            md.update(buf, 0, r);
            left -= r;
        }
    }

    private void add(Category c, File f, long len) {
        c.files.add(f);
        c.bytes += len;
        result.sizes.put(f, len);
    }
}
