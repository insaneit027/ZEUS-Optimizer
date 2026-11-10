package com.zeus.optimizer;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aba Início: nota de saúde, botão Otimizar Agora, resumo e recomendações. */
final class HomeScreen extends Screen {

    private boolean running;
    private Optimizer.Report last;
    private String stepText = "Preparando...";
    private int stepPct;
    private TextView stepView;
    private BarView stepBar;

    HomeScreen(MainActivity a) {
        super(a);
    }

    @Override
    void build(LinearLayout root) {
        Optimizer.Status s = Optimizer.status(a);
        int sp = s.storagePct(), rp = s.ramPct();
        long days = s.uptimeMs / AppAnalyzer.DAY;

        // ---------------- Destaque com o medidor
        LinearLayout hero = Theme.col(a);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = Theme.gradient(a, 26, GradientDrawable.Orientation.TL_BR, 0xFF172554, 0xFF0B1226, 0xFF1E1B4B);
        bg.setStroke(dp(1), 0xFF27345A);
        hero.setBackground(bg);
        hero.setPadding(dp(20), dp(18), dp(20), dp(20));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        hlp.setMargins(0, dp(4), 0, dp(14));
        hero.setLayoutParams(hlp);

        GaugeView gauge = new GaugeView(a);
        hero.addView(gauge, new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP));
        gauge.setValue(s.score, "saúde", "");

        String verdict = s.score >= 85 ? "Seu celular está voando ⚡"
                : s.score >= 60 ? "Dá para ficar mais rápido" : "Seu celular precisa de otimização";
        TextView vt = Theme.text(a, verdict, 18, Theme.TEXT, true);
        vt.setGravity(Gravity.CENTER);
        hero.addView(vt);
        TextView lastTv = Theme.small(a, lastOptimizationText());
        lastTv.setGravity(Gravity.CENTER);
        lastTv.setPadding(0, dp(4), 0, 0);
        hero.addView(lastTv);

        if (running) {
            stepView = Theme.text(a, stepText, 14, Theme.GOLD, true);
            stepView.setGravity(Gravity.CENTER);
            stepView.setPadding(0, dp(16), 0, 0);
            hero.addView(stepView, new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP));
            stepBar = new BarView(a, 8);
            stepBar.setPercent(stepPct, Theme.GOLD);
            hero.addView(stepBar);
        } else {
            stepView = null;
            stepBar = null;
            Button go = Theme.primary(a, "⚡  OTIMIZAR AGORA");
            go.setTextSize(17);
            go.setLetterSpacing(0.06f);
            go.setPadding(dp(16), dp(16), dp(16), dp(16));
            go.setOnClickListener(v -> startOptimize());
            LinearLayout.LayoutParams glp = Theme.fullWidth(a, 18);
            hero.addView(go, glp);
        }
        root.addView(hero);

        // ---------------- Resultado da última otimização
        if (last != null) root.addView(resultCard(last));

        // ---------------- Estatísticas
        root.addView(Theme.section(a, "Agora"));
        root.addView(pair(
                statTile("💾", Theme.levelColor(sp) == Theme.GREEN ? Theme.CYAN : Theme.levelColor(sp),
                        "Armazenamento", sp + "%", Theme.size(s.storageFree) + " livres", sp),
                statTile("🧠", Theme.PURPLE, "Memória RAM", rp + "%",
                        Theme.size(s.ramAvail) + " livres de " + Theme.size(s.ramTotal), rp)));
        String up = days >= 1 ? days + (days == 1 ? " dia" : " dias") : (s.uptimeMs / 3600000) + " horas";
        root.addView(pair(
                statTile("🔋", s.battery <= 20 ? Theme.RED : Theme.GREEN, "Bateria",
                        s.battery >= 0 ? s.battery + "%" : "—",
                        fmt1(s.temp) + "°C" + (s.charging ? " • carregando" : ""), Math.max(0, s.battery)),
                statTile("⏱", days >= 3 ? Theme.ORANGE : Theme.BLUE, "Ligado há", up,
                        days >= 3 ? "Reinicie para acelerar" : "Tudo certo", -1)));

        // ---------------- Recomendações
        root.addView(Theme.section(a, "Recomendações"));
        int n = 0;
        if (!Optimizer.hasStorageAccess(a)) {
            root.addView(rec("🔒", Theme.GOLD, "Permita a limpeza de arquivos",
                    "Sem essa permissão o ZEUS não consegue apagar lixo do armazenamento.",
                    "Permitir", a::requestStorageAccess));
            n++;
        }
        if (sp >= 80) {
            root.addView(rec("🧹", Theme.RED, "Libere espaço",
                    "O armazenamento está " + sp + "% cheio. Acima de 85% o celular fica lento.",
                    "Limpar", () -> a.go(MainActivity.CLEAN)));
            n++;
        }
        if (days >= 3) {
            root.addView(rec("🔄", Theme.ORANGE, "Reinicie o celular",
                    "Ligado há " + days + " dias. Reiniciar libera memória e fecha processos travados.", null, null));
            n++;
        }
        if (!AppAnalyzer.hasUsageAccess(a)) {
            root.addView(rec("📊", Theme.BLUE, "Descubra apps esquecidos",
                    "Ative a análise de uso para ver quais apps você não abre há meses.",
                    "Ativar", a::requestUsageAccess));
            n++;
        }
        if (s.animScale > 0.6f) {
            root.addView(rec("✨", Theme.PURPLE, "Acelere as animações",
                    "Com animações em 0,5x o celular parece bem mais rápido ao abrir apps.",
                    "Como fazer", () -> a.go(MainActivity.TOOLS)));
            n++;
        }
        if (s.temp >= 40) {
            root.addView(rec("🌡", Theme.RED, "Celular quente",
                    "Está em " + fmt1(s.temp) + "°C. Feche jogos e tire a capa para esfriar.", null, null));
            n++;
        }
        if (n == 0) {
            LinearLayout ok = Theme.card(a);
            ok.addView(Theme.title(a, "🎉 Tudo em ordem"));
            ok.addView(Theme.small(a, "Nenhuma recomendação no momento."));
            root.addView(ok);
        }

        // ---------------- Aparelho
        root.addView(Theme.section(a, "Seu aparelho"));
        LinearLayout dev = Theme.card(a);
        dev.addView(kv("Modelo", cap(Build.MANUFACTURER) + " " + Build.MODEL));
        dev.addView(kv("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        String soc = Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null && !Build.SOC_MODEL.isEmpty()
                && !"unknown".equalsIgnoreCase(Build.SOC_MODEL) ? Build.SOC_MODEL : Build.HARDWARE;
        dev.addView(kv("Processador", soc + " • " + Runtime.getRuntime().availableProcessors() + " núcleos"));
        dev.addView(kv("Memória RAM", Theme.size(s.ramTotal)));
        dev.addView(kv("Armazenamento", Theme.size(s.storageTotal)));
        root.addView(dev);
    }

    private LinearLayout resultCard(Optimizer.Report r) {
        LinearLayout c = Theme.card(a);
        c.setBackground(Theme.outlined(a, 0xFF0B1F17, 0xFF14532D, 20));
        c.addView(Theme.text(a, "✅  Otimização concluída", 17, Theme.GREEN, true));
        c.addView(Theme.space(a, 6));
        c.addView(line("🚀", r.appsClosed > 0 ? r.appsClosed + " apps em segundo plano encerrados" : "Nenhum app pesado em segundo plano"));
        c.addView(line("🧠", r.ramFreed > 0 ? Theme.size(r.ramFreed) + " de RAM liberada" : "Memória RAM reorganizada"));
        if (r.junkSkipped) {
            c.addView(line("🔒", "Limpeza de arquivos pulada (falta permissão)"));
            addButton(c, Theme.secondary(a, "Permitir limpeza de arquivos"), v -> a.requestStorageAccess());
        } else {
            c.addView(line("🧹", Theme.size(r.junkFreed) + " de lixo removido (" + r.junkFiles + " itens)"));
        }
        if (r.cacheFreed > 0) c.addView(line("🗂", Theme.size(r.cacheFreed) + " de cache limpo"));
        c.addView(Theme.space(a, 6));
        c.addView(Theme.small(a, "Quer liberar ainda mais? Revise duplicados e arquivos grandes na aba Limpeza."));
        return c;
    }

    private LinearLayout line(String emoji, String text) {
        LinearLayout r = Theme.row(a);
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(Theme.text(a, emoji, 16, Theme.TEXT, false));
        TextView t = Theme.body(a, text);
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, Theme.weight(1));
        return r;
    }

    private LinearLayout rec(String emoji, int color, String title, String desc, String action, Runnable run) {
        LinearLayout c = Theme.card(a);
        LinearLayout r = Theme.row(a);
        r.addView(Theme.icon(a, emoji, color, 42));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, dp(8), 0);
        tx.addView(Theme.text(a, title, 15, Theme.TEXT, true));
        tx.addView(Theme.small(a, desc));
        r.addView(tx, Theme.weight(1));
        if (action != null && run != null) {
            Button b = Theme.secondary(a, action);
            b.setTextSize(13);
            b.setPadding(dp(12), dp(9), dp(12), dp(9));
            b.setTextColor(color);
            b.setOnClickListener(v -> run.run());
            r.addView(b);
        }
        c.addView(r);
        return c;
    }

    private void startOptimize() {
        if (running) return;
        running = true;
        last = null;
        stepText = "Preparando...";
        stepPct = 2;
        a.refresh();
        final Context ctx = a.getApplicationContext();
        new Thread(() -> {
            Optimizer.Report r = Optimizer.run(ctx, (t, p) -> a.ui.post(() -> {
                stepText = t;
                stepPct = p;
                if (stepView != null) stepView.setText(t);
                if (stepBar != null) stepBar.setPercent(p, Theme.GOLD);
            }));
            a.ui.post(() -> {
                running = false;
                last = r;
                if (a.tab() == MainActivity.HOME) a.refresh();
                a.toast("⚡ Otimização concluída!");
            });
        }).start();
    }

    private String lastOptimizationText() {
        long t = a.getSharedPreferences("zeus", Context.MODE_PRIVATE).getLong("ultima_otimizacao", 0);
        if (t == 0) return "Ainda não otimizado";
        long min = (System.currentTimeMillis() - t) / 60000;
        if (min < 1) return "Otimizado agora mesmo";
        if (min < 60) return "Última otimização: há " + min + " min";
        long h = min / 60;
        if (h < 24) return "Última otimização: há " + h + (h == 1 ? " hora" : " horas");
        long d = h / 24;
        return "Última otimização: há " + d + (d == 1 ? " dia" : " dias");
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String fmt1(float v) {
        return String.format(java.util.Locale.getDefault(), "%.1f", v);
    }
}
