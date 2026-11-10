package com.zeus.optimizer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Executa comandos com nível de ADB através do Shizuku.
 * Sem Shizuku, cai para a permissão WRITE_SECURE_SETTINGS (só configurações).
 */
final class Shell {

    static final int NONE = 0, SECURE = 1, SHIZUKU = 2;
    static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    static final int REQ_SHIZUKU = 7701;

    private static Method newProcess;

    private Shell() {
    }

    static boolean shizukuInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(SHIZUKU_PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean shizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean shizukuGranted() {
        try {
            return shizukuRunning() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static void requestShizuku() {
        try {
            if (shizukuRunning() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQ_SHIZUKU);
        } catch (Throwable ignored) {
        }
    }

    static int level(Context c) {
        if (shizukuGranted()) return SHIZUKU;
        if (Turbo.hasAdvanced(c)) return SECURE;
        return NONE;
    }

    static final class Result {
        int code = -1;
        String out = "";

        boolean ok() {
            return code == 0;
        }
    }

    /** Roda um comando no shell do Shizuku (uid shell). Bloqueante: chame fora da thread principal. */
    static Result run(String cmd) {
        Result r = new Result();
        if (!shizukuGranted()) {
            r.out = "Shizuku indisponível";
            return r;
        }
        try {
            if (newProcess == null) {
                newProcess = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                newProcess.setAccessible(true);
            }
            Process p = (Process) newProcess.invoke(null, new String[]{"sh", "-c", cmd + " 2>&1"}, null, null);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (sb.length() < 64000) sb.append(line).append('\n');
                }
            }
            r.code = p.waitFor();
            r.out = sb.toString().trim();
        } catch (Throwable t) {
            r.out = String.valueOf(t);
        }
        return r;
    }

    // ------------------------------------------------------------ configurações

    /** Lê uma configuração. Retorna null se não existir. */
    static String getSetting(Context c, String ns, String key) {
        if (shizukuGranted()) {
            Result r = run("settings get " + ns + " " + key);
            if (!r.ok() || r.out.isEmpty() || "null".equals(r.out)) return null;
            return r.out;
        }
        try {
            switch (ns) {
                case "global": return Settings.Global.getString(c.getContentResolver(), key);
                case "secure": return Settings.Secure.getString(c.getContentResolver(), key);
                default: return Settings.System.getString(c.getContentResolver(), key);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Grava (ou apaga, se value for null) uma configuração. */
    static boolean putSetting(Context c, String ns, String key, String value) {
        if (shizukuGranted()) {
            Result r = value == null ? run("settings delete " + ns + " " + key)
                    : run("settings put " + ns + " " + key + " '" + value.replace("'", "") + "'");
            return r.ok();
        }
        try {
            switch (ns) {
                case "global": return Settings.Global.putString(c.getContentResolver(), key, value);
                case "secure": return Settings.Secure.putString(c.getContentResolver(), key, value);
                default: return Settings.System.putString(c.getContentResolver(), key, value);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
