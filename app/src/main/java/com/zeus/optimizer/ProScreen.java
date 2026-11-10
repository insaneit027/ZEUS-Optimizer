package com.zeus.optimizer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Modo PRO: ajustes com nível de ADB via Shizuku, todos com Desfazer. */
final class ProScreen extends Screen {

    private String busy;          // texto da tarefa em andamento, ou null
    private TextView busyView;

    ProScreen(MainActivity a) {
        super(a);
    }

    private SharedPreferences prefs() {
        return a.getSharedPreferences("zeus", Context.MODE_PRIVATE);
    }

    @Override
    void build(LinearLayout root) {
        int lvl = Shell.level(a);

        TextView back = Theme.text(a, "‹  Voltar ao Turbo", 14, Theme.CYAN, true);
        back.setPadding(dp(4), dp(4), 0, dp(10));
        back.setOnClickListener(v -> a.go(MainActivity.TURBO));
        root.addView(back);

        // ---------------- Destaque / status
        LinearLayout hero = Theme.col(a);
        GradientDrawable bg = Theme.gradient(a, 26, GradientDrawable.Orientation.TL_BR, 0xFF422006, 0xFF1E1B4B, 0xFF0B1226);
        bg.setStroke(dp(1), 0xFF854D0E);
        hero.setBackground(bg);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        hlp.setMargins(0, 0, 0, dp(12));
        hero.setLayoutParams(hlp);
        LinearLayout hr = Theme.row(a);
        hr.addView(Theme.icon(a, "👑", Theme.GOLD, 50));
        LinearLayout ht = Theme.col(a);
        ht.setPadding(dp(12), 0, 0, 0);
        TextView tt = Theme.text(a, "MODO PRO", 20, Theme.GOLD, true);
        tt.setLetterSpacing(0.1f);
        ht.addView(tt);
        String st = lvl == Shell.SHIZUKU ? "Shizuku conectado ✅ — todas as funções liberadas"
                : lvl == Shell.SECURE ? "Modo Avançado ativo — parte das funções liberadas"
                : "Conecte o Shizuku para liberar";
        ht.addView(Theme.small(a, st));
        hr.addView(ht, Theme.weight(1));
        hero.addView(hr);
        if (lvl != Shell.SHIZUKU) buildShizukuSetup(hero);
        root.addView(hero);

        if (busy != null) {
            LinearLayout c = Theme.card(a);
            busyView = Theme.text(a, "⏳ " + busy, 14, Theme.GOLD, true);
            c.addView(busyView);
            root.addView(c);
        } else {
            busyView = null;
        }

        // ---------------- Durante o jogo
        root.addView(Theme.section(a, "Durante o jogo (Game Booster)"));
        LinearLayout ses = Theme.card(a);
        ses.addView(Theme.small(a, "Aplicados quando você toca em \"Jogar ⚡\" na aba Turbo e desfeitos sozinhos quando você volta ao ZEUS (ou pela notificação)."));
        ses.addView(Theme.space(a, 6));
        for (Tweaks.Tweak t : Tweaks.of(Tweaks.SESSION)) ses.addView(sessionRow(t));
        if (Build.VERSION.SDK_INT >= 33) {
            ses.addView(Theme.space(a, 10));
            ses.addView(Theme.text(a, "Escala de resolução do jogo", 14, Theme.TEXT, true));
            ses.addView(Theme.small(a, "O Android desenha o jogo com menos pixels e amplia. Menos pixels = mais FPS e menos calor. Alguns jogos ignoram."));
            ses.addView(chips("jogo_escala", new String[]{"", "0.9", "0.8", "0.7", "0.6"},
                    new String[]{"100%", "90%", "80%", "70%", "60%"}, lvl == Shell.SHIZUKU));
            ses.addView(Theme.space(a, 10));
            ses.addView(Theme.text(a, "Limite de FPS", 14, Theme.TEXT, true));
            ses.addView(Theme.small(a, "Um limite estável (ex.: 60) costuma dar menos travadas e menos calor do que deixar livre."));
            ses.addView(chips("jogo_fps", new String[]{"", "30", "45", "60", "90", "120"},
                    new String[]{"Livre", "30", "45", "60", "90", "120"}, lvl == Shell.SHIZUKU));
        }
        root.addView(ses);

        // ---------------- Sistema
        root.addView(Theme.section(a, "Ajustes do sistema"));
        for (Tweaks.Tweak t : Tweaks.of(Tweaks.SYSTEM)) root.addView(systemRow(t));

        // ---------------- Compilação
        root.addView(Theme.section(a, "Compilação (abrir e rodar mais rápido)"));
        LinearLayout comp = Theme.card(a);
        LinearLayout ch = Theme.row(a);
        ch.addView(Theme.icon(a, "🏎", Theme.ORANGE, 44));
        LinearLayout ct = Theme.col(a);
        ct.setPadding(dp(12), 0, 0, 0);
        ct.addView(Theme.text(a, "Compilar de antemão", 16, Theme.TEXT, true));
        ct.addView(Theme.small(a, "O Android traduz o código dos apps aos poucos. Compilar antes (speed-profile) deixa a abertura e os menus mais rápidos. Leva de segundos a alguns minutos."));
        ch.addView(ct, Theme.weight(1));
        comp.addView(ch);
        boolean shell = lvl == Shell.SHIZUKU;
        compileButton(comp, "🎮  Compilar meus jogos", shell, () -> compile(gamePackages(), "jogos"));
        compileButton(comp, "📱  Compilar interface do sistema e tela inicial", shell,
                () -> compile(systemUiPackages(), "interface do sistema"));
        compileButton(comp, "🟢  Compilar apps do Google (Play Services, Teclado, Chrome...)", shell,
                () -> compile(new String[]{"com.google.android.gms", "com.android.vending", "com.google.android.inputmethod.latin",
                        "com.android.chrome", "com.google.android.youtube", "com.google.android.webview"}, "apps do Google"));
        root.addView(comp);

        // ---------------- Desfazer tudo
        LinearLayout undo = Theme.card(a);
        undo.addView(Theme.text(a, "↩️  Desfazer tudo", 15, Theme.TEXT, true));
        undo.addView(Theme.small(a, "Volta todos os ajustes do sistema ao que eram antes do ZEUS mexer. O ZEUS guarda o valor original de cada configuração antes de mudar."));
        Button ub = Theme.secondary(a, "Desfazer todos os ajustes do sistema");
        ub.setEnabled(lvl != Shell.NONE && busy == null);
        ub.setOnClickListener(v -> runBusy("Desfazendo...", () -> {
            int n = Tweaks.undoAll(a.getApplicationContext());
            return n + " ajustes desfeitos";
        }));
        undo.addView(ub, Theme.fullWidth(a, 12));
        root.addView(undo);
    }

    // ================================================================= Shizuku

    private void buildShizukuSetup(LinearLayout hero) {
        hero.addView(Theme.space(a, 12));
        boolean installed = Shell.shizukuInstalled(a);
        boolean running = Shell.shizukuRunning();
        if (!installed) {
            hero.addView(Theme.body(a, "1. Instale o Shizuku (grátis, código aberto)."));
            hero.addView(Theme.body(a, "2. Abra ele e toque em \"Iniciar\" pela Depuração por Wi-Fi (Android 11+) ou por computador."));
            hero.addView(Theme.body(a, "3. Volte aqui e toque em Conectar."));
            Button b = Theme.primary(a, "Baixar o Shizuku");
            b.setOnClickListener(v -> {
                if (!a.open(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Shell.SHIZUKU_PKG)))) {
                    a.open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
                }
            });
            hero.addView(b, Theme.fullWidth(a, 12));
        } else if (!running) {
            hero.addView(Theme.body(a, "O Shizuku está instalado mas parado. Abra ele e toque em \"Iniciar\". Depois de reiniciar o celular é preciso iniciar de novo."));
            Button b = Theme.primary(a, "Abrir o Shizuku");
            b.setOnClickListener(v -> {
                Intent i = a.getPackageManager().getLaunchIntentForPackage(Shell.SHIZUKU_PKG);
                if (i != null) a.open(i);
            });
            hero.addView(b, Theme.fullWidth(a, 12));
            addButton(hero, Theme.secondary(a, "Verificar de novo"), v -> a.refresh());
        } else {
            hero.addView(Theme.body(a, "O Shizuku está rodando. Toque em Conectar e escolha \"Permitir\"."));
            Button b = Theme.primary(a, "🔌  Conectar ao Shizuku");
            b.setOnClickListener(v -> Shell.requestShizuku());
            hero.addView(b, Theme.fullWidth(a, 12));
        }
    }

    // ================================================================= linhas

    private View sessionRow(Tweaks.Tweak t) {
        boolean avail = t.available(a);
        LinearLayout r = Theme.row(a);
        r.setPadding(0, dp(6), 0, dp(6));
        r.setGravity(android.view.Gravity.TOP);
        CheckBox cb = new CheckBox(a);
        cb.setButtonTintList(ColorStateList.valueOf(Theme.GOLD));
        cb.setChecked(Tweaks.sessionEnabled(a, t));
        cb.setEnabled(avail);
        cb.setOnCheckedChangeListener((b, on) -> Tweaks.setSessionEnabled(a, t, on));
        r.addView(cb);
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(4), dp(6), 0, 0);
        tx.addView(Theme.text(a, t.emoji + "  " + t.title, 14.5f, avail ? Theme.TEXT : Theme.DIM, true));
        tx.addView(Theme.small(a, t.desc));
        if (!avail) tx.addView(Theme.text(a, t.needsShell() ? "Precisa do Shizuku" : "Precisa do Shizuku ou do Modo Avançado", 12, Theme.GOLD, true));
        r.addView(tx, Theme.weight(1));
        return r;
    }

    private View systemRow(Tweaks.Tweak t) {
        boolean avail = t.available(a);
        boolean on = Tweaks.systemActive(a, t);
        LinearLayout c = Theme.card(a);
        if (on) c.setBackground(Theme.outlined(a, 0xFF0B1F17, 0xFF14532D, 20));
        LinearLayout r = Theme.row(a);
        r.addView(Theme.icon(a, t.emoji, on ? Theme.GREEN : Theme.BLUE, 40));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, 0, 0);
        LinearLayout title = Theme.row(a);
        title.addView(Theme.text(a, t.title, 14.5f, Theme.TEXT, true), Theme.weight(1));
        if (on) title.addView(Theme.tag(a, "ATIVO", Theme.GREEN));
        else if (t.warn) title.addView(Theme.tag(a, "ATENÇÃO", Theme.ORANGE));
        tx.addView(title);
        tx.addView(Theme.small(a, t.desc));
        r.addView(tx, Theme.weight(1));
        c.addView(r);
        Button b = on ? Theme.secondary(a, "Desfazer") : Theme.primary(a, "Aplicar");
        if (!avail) {
            b = Theme.secondary(a, t.needsShell() ? "Precisa do Shizuku" : "Precisa do Shizuku ou Modo Avançado");
            b.setEnabled(false);
            b.setAlpha(0.6f);
        } else {
            b.setEnabled(busy == null);
            b.setOnClickListener(v -> runBusy(on ? "Desfazendo " + t.title + "..." : "Aplicando " + t.title + "...", () -> {
                Tweaks.applySystem(a.getApplicationContext(), t, !on);
                return on ? "Desfeito: " + t.title : "✅ Aplicado: " + t.title;
            }));
        }
        c.addView(b, Theme.fullWidth(a, 12));
        return c;
    }

    private View chips(String key, String[] values, String[] labels, boolean enabled) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Theme.row(a);
        String cur = prefs().getString(key, "");
        for (int i = 0; i < values.length; i++) {
            final String val = values[i];
            boolean sel = val.equals(cur);
            TextView chip = Theme.text(a, labels[i], 13, sel ? 0xFF1A1300 : Theme.TEXT, true);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(sel ? Theme.rounded(a, Theme.GOLD, 20) : Theme.outlined(a, Theme.SURFACE2, Theme.STROKE, 20));
            chip.setAlpha(enabled ? 1f : 0.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
            lp.setMargins(0, 0, dp(8), 0);
            if (enabled) {
                chip.setOnClickListener(v -> {
                    prefs().edit().putString(key, val).apply();
                    a.refresh();
                });
            }
            row.addView(chip, lp);
        }
        hs.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        lp.setMargins(0, dp(8), 0, 0);
        hs.setLayoutParams(lp);
        return hs;
    }

    private void compileButton(LinearLayout parent, String label, boolean enabled, Runnable run) {
        Button b = Theme.secondary(a, label);
        b.setTextSize(13.5f);
        b.setEnabled(enabled && busy == null);
        b.setAlpha(enabled ? 1f : 0.5f);
        b.setOnClickListener(v -> run.run());
        parent.addView(b, Theme.fullWidth(a, 10));
    }

    // ================================================================= tarefas

    private interface Job {
        String run();
    }

    private void runBusy(String text, Job job) {
        if (busy != null) return;
        busy = text;
        a.refresh();
        new Thread(() -> {
            String msg;
            try {
                msg = job.run();
            } catch (Throwable t) {
                msg = "Erro: " + t.getMessage();
            }
            final String m = msg;
            a.ui.post(() -> {
                busy = null;
                a.toast(m);
                if (a.tab() == MainActivity.PRO) a.refresh();
            });
        }).start();
    }

    private void setBusy(String text) {
        a.ui.post(() -> {
            busy = text;
            if (busyView != null) busyView.setText("⏳ " + text);
        });
    }

    private void compile(String[] pkgs, String what) {
        runBusy("Compilando " + what + "...", () -> {
            List<ApplicationInfo> list = Tweaks.installed(a, pkgs);
            int ok = 0, i = 0;
            for (ApplicationInfo ai : list) {
                i++;
                String label = String.valueOf(ai.loadLabel(a.getPackageManager()));
                setBusy("Compilando " + label + " (" + i + "/" + list.size() + ")...");
                if (Shell.run("cmd package compile -m speed-profile -f " + ai.packageName).out.contains("Success")) ok++;
            }
            return list.isEmpty() ? "Nada para compilar" : "🏎 " + ok + " de " + list.size() + " compilados (" + what + ")";
        });
    }

    @SuppressWarnings("deprecation")
    private String[] gamePackages() {
        List<String> out = new ArrayList<>();
        for (ApplicationInfo ai : a.getPackageManager().getInstalledApplications(0)) {
            boolean game = ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            if (game) out.add(ai.packageName);
        }
        return out.toArray(new String[0]);
    }

    private String[] systemUiPackages() {
        List<String> out = new ArrayList<>();
        out.add("com.android.systemui");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                out.add(ri.activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
        out.add("com.android.settings");
        return out.toArray(new String[0]);
    }

    /** Para os testes e a aba Turbo saberem se há ajustes de partida disponíveis. */
    static int availableSessionTweaks(Context c) {
        int n = 0;
        for (Tweaks.Tweak t : Tweaks.of(Tweaks.SESSION)) if (t.available(c) && Tweaks.sessionEnabled(c, t)) n++;
        return n;
    }

}
