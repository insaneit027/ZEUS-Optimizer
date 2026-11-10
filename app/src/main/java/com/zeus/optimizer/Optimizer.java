package com.zeus.optimizer;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Ações de otimização que realmente funcionam sem root. */
final class Optimizer {

    interface Step {
        void on(String text, int pct);
    }

    static final class Report {
        int appsClosed;
        long ramFreed;
        long cacheFreed;
        long junkFreed;
        int junkFiles;
        boolean junkSkipped;
    }

    /** Fotografia do estado atual do aparelho. */
    static final class Status {
        long storageTotal, storageFree;
        long ramTotal, ramAvail;
        boolean lowMemory;
        int battery = -1;
        float temp;
        boolean charging;
        long uptimeMs;
        float animScale = 1f;
        int score;
        int storagePct() { return pct(storageTotal - storageFree, storageTotal); }
        int ramPct() { return pct(ramTotal - ramAvail, ramTotal); }
    }

    private Optimizer() {
    }

    static int pct(long part, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, part * 100 / total));
    }

    static Status status(Context c) {
        Status s = new Status();
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getPath());
            s.storageTotal = st.getTotalBytes();
            s.storageFree = st.getAvailableBytes();
        } catch (Exception ignored) {
        }
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        s.ramTotal = mi.totalMem;
        s.ramAvail = mi.availMem;
        s.lowMemory = mi.lowMemory;
        Intent bat = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int l = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (l >= 0 && sc > 0) s.battery = l * 100 / sc;
            s.temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            int st = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            s.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        }
        s.uptimeMs = SystemClock.elapsedRealtime();
        try {
            s.animScale = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception ignored) {
        }

        int score = 100;
        int sp = s.storagePct(), rp = s.ramPct();
        if (sp >= 90) score -= 30; else if (sp >= 80) score -= 15; else if (sp >= 70) score -= 6;
        if (rp >= 85 || s.lowMemory) score -= 18; else if (rp >= 70) score -= 8;
        if (s.temp >= 42) score -= 18; else if (s.temp >= 38) score -= 7;
        long days = s.uptimeMs / AppAnalyzer.DAY;
        if (days >= 7) score -= 10; else if (days >= 3) score -= 4;
        if (s.animScale > 0.6f) score -= 4;
        s.score = Math.max(5, Math.min(100, score));
        return s;
    }

    static boolean hasStorageAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static long availMem(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    /** Encerra os processos em segundo plano dos apps instalados pelo usuário. */
    static int killBackground(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        int n = 0;
        for (ApplicationInfo a : c.getPackageManager().getInstalledApplications(0)) {
            if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || a.packageName.equals(c.getPackageName())) continue;
            try {
                am.killBackgroundProcesses(a.packageName);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    static long clearOwnCache(Context c) {
        long b = deleteChildren(c.getCacheDir());
        File ext = c.getExternalCacheDir();
        if (ext != null) b += deleteChildren(ext);
        return b;
    }

    private static long deleteChildren(File dir) {
        long freed = 0;
        File[] list = dir == null ? null : dir.listFiles();
        if (list == null) return 0;
        for (File f : list) {
            if (f.isDirectory()) {
                freed += deleteChildren(f);
                f.delete();
            } else {
                long len = f.length();
                if (f.delete()) freed += len;
            }
        }
        return freed;
    }

    /** Apaga arquivos e avisa a galeria. Retorna os que sumiram. */
    static List<File> delete(Context c, List<File> files) {
        List<File> done = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (File f : files) {
            boolean gone;
            try {
                gone = f.delete() || !f.exists();
            } catch (Exception e) {
                gone = false;
            }
            if (gone) {
                done.add(f);
                paths.add(f.getAbsolutePath());
            }
        }
        try {
            if (!paths.isEmpty()) {
                MediaScannerConnection.scanFile(c.getApplicationContext(), paths.toArray(new String[0]), null, null);
            }
        } catch (Exception ignored) {
        }
        return done;
    }

    /** Otimização completa em um toque. Roda em segundo plano. */
    static Report run(Context c, Step step) {
        Report r = new Report();
        long before = availMem(c);

        step.on("Encerrando apps em segundo plano...", 8);
        r.appsClosed = killBackground(c);

        step.on("Limpando cache temporário...", 28);
        r.cacheFreed = clearOwnCache(c);

        if (hasStorageAccess(c)) {
            step.on("Procurando lixo no armazenamento...", 40);
            Scanner.Result s = new Scanner(Environment.getExternalStorageDirectory(), false,
                    (n, p) -> step.on("Analisando " + n + " arquivos...", 40 + Math.min(35, n / 400))).scan();
            List<File> junk = new ArrayList<>();
            for (Scanner.Category cat : s.categories) if (cat.safe) junk.addAll(cat.files);
            step.on("Apagando " + junk.size() + " itens inúteis...", 80);
            for (File f : delete(c, junk)) {
                r.junkFreed += s.sizeOf(f);
                r.junkFiles++;
            }
        } else {
            r.junkSkipped = true;
        }

        step.on("Finalizando...", 94);
        try {
            Thread.sleep(900);
        } catch (InterruptedException ignored) {
        }
        r.ramFreed = Math.max(0, availMem(c) - before);
        c.getSharedPreferences("zeus", Context.MODE_PRIVATE).edit()
                .putLong("ultima_otimizacao", System.currentTimeMillis()).apply();
        step.on("Pronto!", 100);
        return r;
    }
}
