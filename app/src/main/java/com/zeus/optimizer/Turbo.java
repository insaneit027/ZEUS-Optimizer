package com.zeus.optimizer;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Ferramentas de desempenho: modo avançado, tela, CPU/GPU, térmico, ping e benchmark. */
final class Turbo {

    private Turbo() {
    }

    // =============================================================== Modo Avançado (ADB)

    static final String ADB_GRANT = "adb shell pm grant com.zeus.optimizer android.permission.WRITE_SECURE_SETTINGS";

    static boolean hasAdvanced(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static float animScale(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception e) {
            return 1f;
        }
    }

    static boolean setAnimations(Context c, float scale) {
        try {
            ContentResolver cr = c.getContentResolver();
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, scale);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static final String DNS_HOST = "one.one.one.one";

    static boolean fastDnsOn(Context c) {
        try {
            ContentResolver cr = c.getContentResolver();
            return "hostname".equals(Settings.Global.getString(cr, "private_dns_mode"))
                    && DNS_HOST.equals(Settings.Global.getString(cr, "private_dns_specifier"));
        } catch (Exception e) {
            return false;
        }
    }

    static boolean setFastDns(Context c, boolean on) {
        try {
            ContentResolver cr = c.getContentResolver();
            if (on) {
                Settings.Global.putString(cr, "private_dns_specifier", DNS_HOST);
                Settings.Global.putString(cr, "private_dns_mode", "hostname");
            } else {
                Settings.Global.putString(cr, "private_dns_mode", "opportunistic");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =============================================================== Energia e temperatura

    static boolean powerSave(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    /** -1 se o aparelho não informa. 0 = normal ... 6 = desligando. */
    static int thermalStatus(Context c) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm.getCurrentThermalStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    static String thermalText(int s) {
        switch (s) {
            case 0: return "Normal";
            case 1: return "Leve";
            case 2: return "Moderado";
            case 3: return "Alto";
            case 4: return "Crítico";
            case 5: return "Emergência";
            case 6: return "Desligando";
            default: return "Não informado";
        }
    }

    static int thermalColor(int s) {
        return s <= 0 ? Theme.GREEN : s == 1 ? Theme.GOLD : s == 2 ? Theme.ORANGE : Theme.RED;
    }

    // =============================================================== Tela

    static final class DisplayInfo {
        float current;
        float max;
        List<Float> rates = new ArrayList<>();
        int width, height;
    }

    static DisplayInfo display(Context c) {
        DisplayInfo info = new DisplayInfo();
        try {
            DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            info.current = d.getRefreshRate();
            Display.Mode cur = d.getMode();
            info.width = cur.getPhysicalWidth();
            info.height = cur.getPhysicalHeight();
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != info.width || m.getPhysicalHeight() != info.height) continue;
                float r = Math.round(m.getRefreshRate());
                if (!info.rates.contains(r)) info.rates.add(r);
                info.max = Math.max(info.max, r);
            }
            java.util.Collections.sort(info.rates);
            if (info.max == 0) info.max = Math.round(info.current);
        } catch (Exception ignored) {
        }
        return info;
    }

    // =============================================================== CPU e GPU

    static final class Cpu {
        int cores;
        long[] cur;   // kHz, -1 se não legível
        long[] max;
    }

    static Cpu cpu() {
        Cpu c = new Cpu();
        c.cores = Runtime.getRuntime().availableProcessors();
        c.cur = new long[c.cores];
        c.max = new long[c.cores];
        for (int i = 0; i < c.cores; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            c.cur[i] = readLong(base + "scaling_cur_freq");
            c.max[i] = readLong(base + "cpuinfo_max_freq");
        }
        return c;
    }

    private static long readLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return Long.parseLong(r.readLine().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String gpuCache;

    /** Nome da GPU via OpenGL ES (cria um contexto invisível de 1x1). */
    static synchronized String gpu() {
        if (gpuCache != null) return gpuCache;
        String result = "Não identificada";
        EGLDisplay d = null;
        EGLContext ctx = null;
        EGLSurface s = null;
        try {
            d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] ver = new int[2];
            EGL14.eglInitialize(d, ver, 0, ver, 1);
            int[] attr = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            EGL14.eglChooseConfig(d, attr, 0, cfg, 0, 1, n, 0);
            if (n[0] > 0) {
                ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT,
                        new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
                s = EGL14.eglCreatePbufferSurface(d, cfg[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
                if (EGL14.eglMakeCurrent(d, s, s, ctx)) {
                    String r = GLES20.glGetString(GLES20.GL_RENDERER);
                    if (r != null && !r.isEmpty()) result = r;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (d != null) {
                    EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (s != null) EGL14.eglDestroySurface(d, s);
                    if (ctx != null) EGL14.eglDestroyContext(d, ctx);
                }
            } catch (Throwable ignored) {
            }
        }
        gpuCache = result;
        return result;
    }

    // =============================================================== Rede

    static final class Ping {
        String name;
        long avg = -1, min = -1, jitter = -1;
        int lost;
    }

    static String connectionType(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) return "Sem conexão";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "Wi-Fi" + wifiSignal(c);
            }
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Dados móveis";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Cabo";
            return "Conectado";
        } catch (Exception e) {
            return "Desconhecida";
        }
    }

    @SuppressWarnings("deprecation")
    private static String wifiSignal(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int rssi = wm.getConnectionInfo().getRssi();
            if (rssi <= -127 || rssi >= 0) return "";
            String q = rssi >= -55 ? "excelente" : rssi >= -67 ? "bom" : rssi >= -75 ? "fraco" : "muito fraco";
            return " • sinal " + q + " (" + rssi + " dBm)";
        } catch (Exception e) {
            return "";
        }
    }

    /** Mede a latência abrindo conexões TCP (funciona sem root, ao contrário do ping ICMP). */
    static Ping ping(String name, String host, int port, int samples) {
        Ping p = new Ping();
        p.name = name;
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (Exception e) {
            p.lost = samples;
            return p;
        }
        List<Long> ok = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            long t = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(addr, port), 2500);
                ok.add((System.nanoTime() - t) / 1_000_000);
            } catch (Exception e) {
                p.lost++;
            }
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }
        }
        if (ok.isEmpty()) return p;
        long sum = 0, min = Long.MAX_VALUE, jit = 0;
        for (int i = 0; i < ok.size(); i++) {
            long v = ok.get(i);
            sum += v;
            min = Math.min(min, v);
            if (i > 0) jit += Math.abs(v - ok.get(i - 1));
        }
        p.avg = sum / ok.size();
        p.min = min;
        p.jitter = ok.size() > 1 ? jit / (ok.size() - 1) : 0;
        return p;
    }

    static String pingQuality(long ms) {
        if (ms < 0) return "Sem resposta";
        if (ms < 50) return "Excelente para jogos";
        if (ms < 90) return "Bom";
        if (ms < 150) return "Regular";
        return "Ruim — pode travar";
    }

    static int pingColor(long ms) {
        if (ms < 0) return Theme.RED;
        return ms < 50 ? Theme.GREEN : ms < 90 ? Theme.CYAN : ms < 150 ? Theme.GOLD : Theme.RED;
    }

    // =============================================================== Benchmark

    interface Progress {
        void on(String text, int pct);
    }

    static final class Bench {
        int cpuSingle, cpuMulti, memory, storage, total;
        double memGBs, writeMBs, readMBs;
    }

    private static long sieve(int n) {
        boolean[] composite = new boolean[n + 1];
        long count = 0;
        for (int i = 2; i <= n; i++) {
            if (!composite[i]) {
                count++;
                for (long j = (long) i * i; j <= n; j += i) composite[(int) j] = true;
            }
        }
        return count;
    }

    /** Quantas rodadas do crivo cabem em ~1,2 s numa thread. */
    private static long cpuRounds(long durationMs) {
        long end = System.currentTimeMillis() + durationMs;
        long rounds = 0;
        while (System.currentTimeMillis() < end) {
            sieve(200_000);
            rounds++;
        }
        return rounds;
    }

    static Bench benchmark(Context c, Progress p) {
        Bench b = new Bench();

        p.on("Processador (1 núcleo)...", 5);
        cpuRounds(300); // aquecimento
        long single = cpuRounds(1500);
        b.cpuSingle = (int) (single * 10);

        p.on("Processador (todos os núcleos)...", 30);
        int cores = Runtime.getRuntime().availableProcessors();
        AtomicLong total = new AtomicLong();
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < cores; i++) {
            Thread t = new Thread(() -> total.addAndGet(cpuRounds(1500)));
            ts.add(t);
            t.start();
        }
        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }
        b.cpuMulti = (int) (total.get() * 10);

        p.on("Memória RAM...", 60);
        int size = 32 * 1024 * 1024;
        byte[] src = new byte[size], dst = new byte[size];
        long t0 = System.nanoTime();
        long copied = 0;
        while (System.nanoTime() - t0 < 1_000_000_000L) {
            System.arraycopy(src, 0, dst, 0, size);
            copied += size;
        }
        double sec = (System.nanoTime() - t0) / 1e9;
        b.memGBs = copied / sec / (1024.0 * 1024 * 1024);
        b.memory = (int) (b.memGBs * 400);

        p.on("Armazenamento...", 80);
        File f = new File(c.getCacheDir(), "zeus-bench.tmp");
        byte[] buf = new byte[1024 * 1024];
        int mb = 64;
        try {
            long w0 = System.nanoTime();
            try (FileOutputStream out = new FileOutputStream(f)) {
                for (int i = 0; i < mb; i++) out.write(buf);
                out.getFD().sync();
            }
            b.writeMBs = mb / ((System.nanoTime() - w0) / 1e9);
            long r0 = System.nanoTime();
            try (FileInputStream in = new FileInputStream(f)) {
                while (in.read(buf) > 0) {
                    // só lendo
                }
            }
            b.readMBs = mb / ((System.nanoTime() - r0) / 1e9);
        } catch (Exception ignored) {
        } finally {
            f.delete();
        }
        b.storage = (int) Math.min(5000, b.writeMBs * 4 + b.readMBs * 0.5);
        b.total = b.cpuSingle + b.cpuMulti / 2 + b.memory + b.storage;
        p.on("Pronto!", 100);
        return b;
    }
}
