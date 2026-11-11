package com.zeus.optimizer;

import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lista os apps instalados pelo usuário e indica quais podem ser removidos. */
final class AppAnalyzer {

    static final int REMOVE = 0, REVIEW = 1, KEEP = 2;

    static final class App {
        String pkg, label, reason;
        Drawable icon;
        long size, cache, lastUsed, screenTime7d;
        boolean frozen;
        /** Teclado, tela inicial, Shizuku: nunca sugerir remover nem congelar. */
        boolean essential;
        int level = KEEP;
    }

    static final class Result {
        final List<App> apps = new ArrayList<>();
        int systemCount;
        int removableCount;
        long removableBytes;
        long totalBytes;
        long totalScreenTime;
    }

    static final long DAY = 24L * 60 * 60 * 1000;
    private static final long MB = 1024L * 1024;

    static boolean hasUsageAccess(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int uid = android.os.Process.myUid();
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return c.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static Result load(Context ctx) {
        boolean usage = hasUsageAccess(ctx);
        Result r = new Result();
        PackageManager pm = ctx.getPackageManager();
        long now = System.currentTimeMillis();

        Map<String, UsageStats> year = new HashMap<>();
        Map<String, UsageStats> week = new HashMap<>();
        if (usage) {
            try {
                UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
                Map<String, UsageStats> m = usm.queryAndAggregateUsageStats(now - 365L * DAY, now);
                if (m != null) year = m;
                Map<String, UsageStats> w = usm.queryAndAggregateUsageStats(now - 7L * DAY, now);
                if (w != null) week = w;
            } catch (Exception ignored) {
            }
        }
        StorageStatsManager ssm = (StorageStatsManager) ctx.getSystemService(Context.STORAGE_STATS_SERVICE);
        Set<String> keep = protectedPackages(ctx, pm);

        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                r.systemCount++;
                continue;
            }
            if (ai.packageName.equals(ctx.getPackageName())) continue;

            App a = new App();
            a.pkg = ai.packageName;
            a.frozen = (ai.flags & ApplicationInfo.FLAG_SUSPENDED) != 0 || !ai.enabled;
            a.label = String.valueOf(ai.loadLabel(pm));
            try {
                a.icon = ai.loadIcon(pm);
            } catch (Exception ignored) {
            }

            long installed = now;
            try {
                installed = pm.getPackageInfo(a.pkg, 0).firstInstallTime;
            } catch (Exception ignored) {
            }

            if (usage && ssm != null) {
                try {
                    StorageStats s = ssm.queryStatsForPackage(ai.storageUuid, a.pkg, android.os.Process.myUserHandle());
                    a.cache = s.getCacheBytes();
                    a.size = s.getAppBytes() + s.getDataBytes();
                } catch (Exception ignored) {
                }
            }

            UsageStats u = year.get(a.pkg);
            a.lastUsed = u != null ? u.getLastTimeUsed() : 0;
            if (a.lastUsed < 946684800000L) a.lastUsed = 0; // antes de 2000 = sem registro
            UsageStats wk = week.get(a.pkg);
            a.screenTime7d = wk != null ? wk.getTotalTimeInForeground() : 0;

            long since = a.lastUsed > 0 ? (now - a.lastUsed) / DAY : -1;
            long age = (now - installed) / DAY;
            boolean launchable = pm.getLaunchIntentForPackage(a.pkg) != null;

            if (!usage) {
                a.level = KEEP;
                a.reason = "Ative o acesso de uso para analisar";
            } else if (Shell.SHIZUKU_PKG.equals(a.pkg)) {
                a.level = KEEP;
                a.essential = true;
                a.reason = "Necessário para o Modo PRO — mantenha";
            } else if (keep.contains(a.pkg) || !launchable) {
                a.level = KEEP;
                a.essential = keep.contains(a.pkg);
                a.reason = "Teclado, tela inicial ou serviço — mantenha se usa";
            } else if (since < 0 || since >= 60) {
                if (age < 14) {
                    a.level = KEEP;
                    a.reason = "Instalado recentemente";
                } else {
                    a.level = REMOVE;
                    a.reason = since < 0
                            ? "Sem uso registrado — pode remover"
                            : "Não usado há " + since + " dias — pode remover";
                }
            } else if (since >= 30) {
                a.level = REVIEW;
                a.reason = "Pouco usado: aberto há " + since + " dias";
            } else {
                a.level = KEEP;
                a.reason = since == 0 ? "Em uso: aberto hoje"
                        : "Em uso: aberto há " + since + (since == 1 ? " dia" : " dias");
            }
            if (a.cache >= 200 * MB && a.level == KEEP) {
                a.level = REVIEW;
                a.reason += " • cache alto, vale limpar";
            }
            if (a.level == REMOVE) {
                r.removableCount++;
                r.removableBytes += a.size;
            }
            r.totalBytes += a.size;
            r.totalScreenTime += a.screenTime7d;
            r.apps.add(a);
        }
        return r;
    }

    static String duration(long ms) {
        long min = ms / 60000;
        if (min < 1) return "menos de 1 min";
        if (min < 60) return min + " min";
        long h = min / 60;
        return h + "h " + (min % 60) + "min";
    }

    /** Teclados ativos e a tela inicial padrão nunca são sugeridos para remoção. */
    private static Set<String> protectedPackages(Context ctx, PackageManager pm) {
        Set<String> s = new HashSet<>();
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            for (InputMethodInfo i : imm.getEnabledInputMethodList()) s.add(i.getPackageName());
        } catch (Exception ignored) {
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) s.add(ri.activityInfo.packageName);
        } catch (Exception ignored) {
        }
        return s;
    }
}
