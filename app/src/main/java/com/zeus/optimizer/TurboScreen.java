package com.zeus.optimizer;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Aba Turbo: Game Booster, monitor ao vivo, tela, rede, benchmark e modo avançado. */
final class TurboScreen extends Screen {

    private static final class GameApp {
        String pkg, label;
        Drawable icon;
        boolean game;
    }

    private List<GameApp> apps;
    private boolean loadingApps, showAll;

    private boolean pinging;
    private List<Turbo.Ping> pings;

    private boolean benching;
    private String benchStep = "";
    private Turbo.Bench bench;
    private TextView benchStepView;

    // Monitor ao vivo
    private TextView[] coreText;
    private BarView[] coreBar;
    private TextView monThermal, monRam, monHz, monGpu;

    TurboScreen(MainActivity a) {
        super(a);
    }

    private SharedPreferences prefs() {
        return a.getSharedPreferences("zeus", Context.MODE_PRIVATE);
    }

    @Override
    void build(LinearLayout root) {
        boolean adv = Turbo.hasAdvanced(a);
        boolean saver = Turbo.powerSave(a);
        int thermal = Turbo.thermalStatus(a);
        Turbo.DisplayInfo disp = Turbo.display(a);

        // ---------------- Destaque
        LinearLayout hero = Theme.col(a);
        GradientDrawable bg = Theme.gradient(a, 26, GradientDrawable.Orientation.TL_BR, 0xFF7C2D12, 0xFF3B0764, 0xFF0B1226);
        bg.setStroke(dp(1), 0xFF7C2D12);
        hero.setBackground(bg);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        hlp.setMargins(0, dp(4), 0, dp(12));
        hero.setLayoutParams(hlp);
        LinearLayout hr = Theme.row(a);
        hr.addView(Theme.icon(a, "🚀", Theme.ORANGE, 50));
        LinearLayout ht = Theme.col(a);
        ht.setPadding(dp(12), 0, 0, 0);
        TextView tt = Theme.text(a, "CENTRAL TURBO", 19, Theme.TEXT, true);
        tt.setLetterSpacing(0.08f);
        ht.addView(tt);
        ht.addView(Theme.small(a, "Tudo o que realmente deixa jogos mais lisos"));
        hr.addView(ht, Theme.weight(1));
        hero.addView(hr);
        LinearLayout chips = Theme.row(a);
        chips.setPadding(0, dp(14), 0, 0);
        chips.addView(chip("🌡 " + Turbo.thermalText(thermal), Turbo.thermalColor(thermal)));
        chips.addView(chip(saver ? "🔋 Economia ligada" : "🔋 Sem limite", saver ? Theme.RED : Theme.GREEN));
        chips.addView(chip("🖥 " + Math.round(disp.current) + " Hz", disp.current + 1 < disp.max ? Theme.GOLD : Theme.CYAN));
        hero.addView(chips);
        root.addView(hero);

        // ---------------- Avisos do que está limitando o desempenho
        if (saver) {
            root.addView(alert("🔋", Theme.RED, "Economia de bateria está limitando o FPS",
                    "Ela reduz a velocidade do processador e a taxa da tela. Desligue antes de jogar.",
                    "Desligar", () -> a.openSetting(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        }
        if (thermal >= 2) {
            root.addView(alert("🔥", Theme.ORANGE, "Celular quente: o processador está sendo freado",
                    "Com aquecimento " + Turbo.thermalText(thermal).toLowerCase(Locale.ROOT)
                            + " o Android reduz a velocidade para esfriar. Tire a capa, pare de carregar e espere uns minutos.",
                    null, null));
        }
        if (disp.max > disp.current + 1) {
            root.addView(alert("🖥", Theme.GOLD, "Sua tela suporta " + Math.round(disp.max) + " Hz",
                    "Agora está em " + Math.round(disp.current) + " Hz. Ative a taxa máxima em Tela › Suavidade / Taxa de atualização.",
                    "Abrir", () -> a.openSetting(Settings.ACTION_DISPLAY_SETTINGS)));
        }

        buildGameBooster(root, adv);
        buildMonitor(root, disp);
        buildDisplay(root, disp);
        buildNetwork(root, adv);
        buildBenchmark(root);
        buildAdvanced(root, adv);

        LinearLayout honest = Theme.card(a);
        honest.addView(Theme.text(a, "ℹ️  Sobre FPS", 15, Theme.TEXT, true));
        honest.addView(Theme.space(a, 4));
        honest.addView(Theme.small(a, "Sem root, nenhum app consegue aumentar a velocidade do processador ou da GPU — "
                + "apps que prometem \"+60 FPS\" enganam. O que funciona de verdade é: tela na taxa máxima, celular frio, "
                + "economia de bateria desligada, nada pesado rodando atrás e internet estável. É isso que o Turbo cuida."));
        root.addView(honest);

        startMonitor();
    }

    private TextView chip(String text, int color) {
        TextView t = Theme.text(a, text, 11.5f, color, true);
        t.setBackground(Theme.rounded(a, Theme.alpha(color, 0x26), 12));
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
        lp.setMargins(0, 0, dp(6), 0);
        t.setLayoutParams(lp);
        t.setSingleLine(true);
        return t;
    }

    private LinearLayout alert(String emoji, int color, String title, String desc, String action, Runnable run) {
        LinearLayout c = Theme.card(a);
        c.setBackground(Theme.outlined(a, Theme.alpha(color, 0x14), Theme.alpha(color, 0x66), 18));
        LinearLayout r = Theme.row(a);
        r.addView(Theme.icon(a, emoji, color, 40));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, dp(8), 0);
        tx.addView(Theme.text(a, title, 14.5f, Theme.TEXT, true));
        tx.addView(Theme.small(a, desc));
        r.addView(tx, Theme.weight(1));
        if (action != null) {
            Button b = Theme.secondary(a, action);
            b.setTextSize(13);
            b.setTextColor(color);
            b.setPadding(dp(12), dp(9), dp(12), dp(9));
            b.setOnClickListener(v -> run.run());
            r.addView(b);
        }
        c.addView(r);
        return c;
    }

    private LinearLayout header(LinearLayout card, String emoji, int color, String title, String desc) {
        LinearLayout r = Theme.row(a);
        r.addView(Theme.icon(a, emoji, color, 44));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, 0, 0);
        tx.addView(Theme.text(a, title, 16.5f, Theme.TEXT, true));
        if (desc != null) tx.addView(Theme.small(a, desc));
        r.addView(tx, Theme.weight(1));
        card.addView(r);
        return r;
    }

    // ================================================================= Game Booster

    private void buildGameBooster(LinearLayout root, boolean adv) {
        root.addView(Theme.section(a, "Game Booster"));
        LinearLayout c = Theme.card(a);
        header(c, "🎮", Theme.PURPLE, "Jogar com Boost",
                "Encerra apps em segundo plano, libera RAM e abre o jogo. Ao voltar ao ZEUS, tudo é restaurado.");
        c.addView(Theme.space(a, 6));
        c.addView(option("Ativar Não Perturbe durante o jogo", "boost_dnd", true));
        CheckBox anim = option(adv ? "Desligar animações durante o jogo" : "Desligar animações (precisa do Modo Avançado)",
                "boost_anim", true);
        anim.setEnabled(adv);
        anim.setAlpha(adv ? 1f : 0.5f);
        c.addView(anim);

        if (apps == null) {
            c.addView(Theme.space(a, 8));
            c.addView(Theme.small(a, "Procurando seus jogos..."));
            loadApps();
        } else {
            List<GameApp> list = new ArrayList<>();
            for (GameApp g : apps) if (g.game || showAll) list.add(g);
            c.addView(Theme.space(a, 6));
            if (list.isEmpty()) {
                c.addView(Theme.body(a, "Nenhum jogo detectado. Toque abaixo para escolher qualquer app."));
            }
            for (GameApp g : list) c.addView(gameRow(g));
            TextView more = Theme.text(a, showAll ? "Mostrar só os jogos ▴" : "Mostrar todos os apps ▾", 13.5f, Theme.CYAN, true);
            more.setPadding(0, dp(12), 0, dp(2));
            more.setOnClickListener(v -> {
                showAll = !showAll;
                a.refresh();
            });
            c.addView(more);
        }
        root.addView(c);
    }

    private CheckBox option(String text, String key, boolean def) {
        CheckBox cb = new CheckBox(a);
        cb.setText(text);
        cb.setTextColor(0xFFCBD5E1);
        cb.setTextSize(13.5f);
        cb.setButtonTintList(ColorStateList.valueOf(Theme.GOLD));
        cb.setChecked(prefs().getBoolean(key, def));
        cb.setOnCheckedChangeListener((b, on) -> prefs().edit().putBoolean(key, on).apply());
        return cb;
    }

    private View gameRow(GameApp g) {
        LinearLayout r = Theme.row(a);
        r.setPadding(0, dp(8), 0, dp(8));
        ImageView iv = new ImageView(a);
        if (g.icon != null) iv.setImageDrawable(g.icon);
        r.addView(iv, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, dp(8), 0);
        TextView n = Theme.text(a, g.label, 15, Theme.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(n);
        tx.addView(Theme.small(a, g.game ? "Jogo" : "App"));
        r.addView(tx, Theme.weight(1));
        Button play = Theme.primary(a, "Jogar ⚡");
        play.setTextSize(13.5f);
        play.setPadding(dp(14), dp(9), dp(14), dp(9));
        play.setOnClickListener(v -> boostAndLaunch(g));
        r.addView(play);
        return r;
    }

    @SuppressWarnings("deprecation")
    private void loadApps() {
        if (loadingApps) return;
        loadingApps = true;
        final Context ctx = a.getApplicationContext();
        new Thread(() -> {
            List<GameApp> list = new ArrayList<>();
            try {
                PackageManager pm = ctx.getPackageManager();
                for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                    if (ai.packageName.equals(ctx.getPackageName())) continue;
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
                    if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                    GameApp g = new GameApp();
                    g.pkg = ai.packageName;
                    g.label = String.valueOf(ai.loadLabel(pm));
                    try {
                        g.icon = ai.loadIcon(pm);
                    } catch (Exception ignored) {
                    }
                    g.game = ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                    list.add(g);
                }
                Collections.sort(list, (x, y) -> x.game != y.game ? (x.game ? -1 : 1)
                        : x.label.compareToIgnoreCase(y.label));
            } catch (Throwable ignored) {
            }
            a.ui.post(() -> {
                loadingApps = false;
                apps = list;
                if (a.tab() == MainActivity.TURBO) a.refresh();
            });
        }).start();
    }

    private void boostAndLaunch(GameApp g) {
        final Context ctx = a.getApplicationContext();
        final boolean wantDnd = prefs().getBoolean("boost_dnd", true);
        final boolean wantAnim = prefs().getBoolean("boost_anim", true);
        a.toast("🚀 Turbinando " + g.label + "...");
        new Thread(() -> {
            SharedPreferences.Editor ed = prefs().edit();
            long before = Optimizer.availMem(ctx);
            int n = Optimizer.killBackground(ctx);
            Optimizer.clearOwnCache(ctx);
            boolean dnd = false, anim = false;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (wantDnd && nm != null && nm.isNotificationPolicyAccessGranted()) {
                int prev = nm.getCurrentInterruptionFilter();
                if (prev == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    try {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                        dnd = true;
                    } catch (Exception ignored) {
                    }
                }
            }
            if (wantAnim && Turbo.hasAdvanced(ctx)) {
                ed.putFloat("anim_antes", Turbo.animScale(ctx));
                anim = Turbo.setAnimations(ctx, 0f);
            }
            ed.putBoolean("sessao_jogo", true).putBoolean("sessao_dnd", dnd).putBoolean("sessao_anim", anim).apply();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, Optimizer.availMem(ctx) - before);
            final boolean dndFinal = dnd;
            a.ui.post(() -> {
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(g.pkg);
                if (li == null) {
                    a.toast("Não foi possível abrir " + g.label);
                    return;
                }
                a.toast("🚀 Boost pronto: " + n + " apps encerrados"
                        + (freed > 0 ? " • " + Theme.size(freed) + " de RAM" : "")
                        + (dndFinal ? " • Não Perturbe ativo" : ""));
                a.open(li);
            });
        }).start();
    }

    /** Chamado quando o usuário volta ao ZEUS depois de jogar. */
    static boolean endGameSession(Context c) {
        SharedPreferences p = c.getSharedPreferences("zeus", Context.MODE_PRIVATE);
        if (!p.getBoolean("sessao_jogo", false)) return false;
        if (p.getBoolean("sessao_dnd", false)) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm.isNotificationPolicyAccessGranted()) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            } catch (Exception ignored) {
            }
        }
        if (p.getBoolean("sessao_anim", false)) Turbo.setAnimations(c, p.getFloat("anim_antes", 1f));
        p.edit().putBoolean("sessao_jogo", false).putBoolean("sessao_dnd", false).putBoolean("sessao_anim", false).apply();
        return true;
    }

    // ================================================================= Monitor ao vivo

    private void buildMonitor(LinearLayout root, Turbo.DisplayInfo disp) {
        root.addView(Theme.section(a, "Monitor ao vivo"));
        LinearLayout c = Theme.card(a);
        header(c, "📈", Theme.CYAN, "Desempenho agora", "Atualiza sozinho a cada 1,5 segundo");
        c.addView(Theme.space(a, 8));

        Turbo.Cpu cpu = Turbo.cpu();
        boolean readable = false;
        for (long v : cpu.cur) if (v > 0) readable = true;
        coreText = new TextView[cpu.cores];
        coreBar = new BarView[cpu.cores];
        if (readable) {
            for (int i = 0; i < cpu.cores; i++) {
                LinearLayout r = Theme.row(a);
                TextView l = Theme.text(a, "Núcleo " + (i + 1), 12.5f, Theme.MUTED, false);
                r.addView(l, new LinearLayout.LayoutParams(dp(70), Theme.WRAP));
                BarView b = new BarView(a, 6);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(6), 1f);
                blp.setMargins(dp(4), 0, dp(10), 0);
                r.addView(b, blp);
                TextView v = Theme.text(a, "", 12.5f, Theme.TEXT, true);
                v.setGravity(Gravity.END);
                r.addView(v, new LinearLayout.LayoutParams(dp(78), Theme.WRAP));
                r.setPadding(0, dp(3), 0, dp(3));
                c.addView(r);
                coreText[i] = v;
                coreBar[i] = b;
            }
        } else {
            c.addView(kv("Processador", cpu.cores + " núcleos"));
            c.addView(Theme.small(a, "Este aparelho não libera a leitura da velocidade de cada núcleo."));
        }
        c.addView(Theme.space(a, 6));
        monGpu = (TextView) kvValue(c, "Placa de vídeo (GPU)", "detectando...");
        monThermal = (TextView) kvValue(c, "Aquecimento", "—");
        monRam = (TextView) kvValue(c, "RAM livre", "—");
        monHz = (TextView) kvValue(c, "Taxa da tela", Math.round(disp.current) + " Hz");
        root.addView(c);

        new Thread(() -> {
            String g = Turbo.gpu();
            a.ui.post(() -> {
                if (monGpu != null) monGpu.setText(g);
            });
        }).start();
        updateMonitor();
    }

    private View kvValue(LinearLayout parent, String key, String value) {
        LinearLayout r = kv(key, value);
        parent.addView(r);
        return r.getChildAt(1);
    }

    private void updateMonitor() {
        Turbo.Cpu cpu = Turbo.cpu();
        if (coreText != null) {
            for (int i = 0; i < cpu.cores && i < coreText.length; i++) {
                if (coreText[i] == null) continue;
                long cur = cpu.cur[i], max = cpu.max[i];
                if (cur <= 0) {
                    coreText[i].setText("dormindo");
                    coreBar[i].setPercent(0, Theme.CYAN);
                } else {
                    coreText[i].setText(String.format(Locale.getDefault(), "%.2f GHz", cur / 1_000_000f));
                    int pct = max > 0 ? (int) (cur * 100 / max) : 0;
                    coreBar[i].setPercent(pct, pct >= 85 ? Theme.ORANGE : Theme.CYAN);
                }
            }
        }
        int th = Turbo.thermalStatus(a);
        if (monThermal != null) {
            monThermal.setText(Turbo.thermalText(th));
            monThermal.setTextColor(Turbo.thermalColor(th));
        }
        Optimizer.Status s = Optimizer.status(a);
        if (monRam != null) monRam.setText(Theme.size(s.ramAvail) + " de " + Theme.size(s.ramTotal));
        if (monHz != null) monHz.setText(Math.round(Turbo.display(a).current) + " Hz");
    }

    private void startMonitor() {
        final int myGen = a.gen;
        a.ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (a.gen != myGen || a.tab() != MainActivity.TURBO) return;
                updateMonitor();
                a.ui.postDelayed(this, 1500);
            }
        }, 1500);
    }

    // ================================================================= Tela

    private void buildDisplay(LinearLayout root, Turbo.DisplayInfo d) {
        root.addView(Theme.section(a, "Tela"));
        LinearLayout c = Theme.card(a);
        header(c, "🖥", Theme.BLUE, "Taxa de atualização",
                "Mais Hz = imagem mais lisa. Jogos só passam de 60 FPS se a tela também passar.");
        c.addView(Theme.space(a, 6));
        c.addView(kv("Agora", Math.round(d.current) + " Hz"));
        c.addView(kv("Máxima da sua tela", Math.round(d.max) + " Hz"));
        StringBuilder sb = new StringBuilder();
        for (Float r : d.rates) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Math.round(r));
        }
        if (sb.length() > 0) c.addView(kv("Suportadas", sb + " Hz"));
        if (d.width > 0) c.addView(kv("Resolução", d.width + " × " + d.height));
        if (d.max > 61) {
            c.addView(Theme.space(a, 6));
            c.addView(Theme.small(a, "Para travar a tela em " + Math.round(d.max) + " Hz o tempo todo (usa mais bateria), rode no computador:"));
            String cmd = "adb shell settings put system min_refresh_rate " + Math.round(d.max);
            c.addView(code(cmd));
            addButton(c, Theme.secondary(a, "Copiar comando"), v -> a.copy(cmd));
        } else {
            c.addView(Theme.space(a, 6));
            c.addView(Theme.small(a, "Sua tela é de 60 Hz, então os jogos chegam no máximo a 60 FPS."));
        }
        addButton(c, Theme.secondary(a, "Abrir configurações de tela"), v -> a.openSetting(Settings.ACTION_DISPLAY_SETTINGS));
        root.addView(c);
    }

    private TextView code(String text) {
        TextView t = Theme.text(a, text, 12, Theme.GOLD, false);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setBackground(Theme.rounded(a, 0xFF020617, 10));
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        lp.setMargins(0, dp(8), 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    // ================================================================= Rede

    private void buildNetwork(LinearLayout root, boolean adv) {
        root.addView(Theme.section(a, "Internet para jogos"));
        LinearLayout c = Theme.card(a);
        header(c, "📶", Theme.GREEN, "Teste de ping", Turbo.connectionType(a));
        if (pings != null) {
            c.addView(Theme.space(a, 8));
            for (Turbo.Ping p : pings) {
                LinearLayout r = Theme.row(a);
                r.setPadding(0, dp(6), 0, dp(6));
                LinearLayout tx = Theme.col(a);
                tx.addView(Theme.text(a, p.name, 14, Theme.TEXT, true));
                tx.addView(Theme.small(a, p.avg < 0 ? "Sem resposta"
                        : Turbo.pingQuality(p.avg) + " • variação " + p.jitter + " ms" + (p.lost > 0 ? " • " + p.lost + " perdidos" : "")));
                r.addView(tx, Theme.weight(1));
                r.addView(Theme.text(a, p.avg < 0 ? "—" : p.avg + " ms", 20, Turbo.pingColor(p.avg), true));
                c.addView(r);
            }
        }
        Button test = pinging ? Theme.secondary(a, "Testando...") : Theme.primary(a, pings == null ? "📶  Testar ping" : "Testar de novo");
        test.setEnabled(!pinging);
        test.setOnClickListener(v -> runPing());
        c.addView(test, Theme.fullWidth(a, 12));
        c.addView(Theme.space(a, 10));
        c.addView(Theme.small(a, "Dicas: jogue no Wi-Fi 5 GHz perto do roteador, feche downloads e streaming, e prefira servidores do Brasil no jogo."));
        root.addView(c);

        LinearLayout dns = Theme.card(a);
        boolean on = Turbo.fastDnsOn(a);
        header(dns, "🌐", Theme.CYAN, "DNS rápido (Cloudflare)",
                on ? "ATIVADO — páginas e logins dos jogos respondem mais rápido" : "Troca o DNS da operadora por um mais rápido e privado");
        if (adv) {
            Button b = on ? Theme.secondary(a, "Desativar DNS rápido") : Theme.primary(a, "Ativar DNS rápido");
            b.setOnClickListener(v -> {
                boolean ok = Turbo.setFastDns(a, !on);
                a.toast(ok ? (!on ? "🌐 DNS rápido ativado" : "DNS voltou ao automático") : "Não foi possível alterar o DNS");
                a.refresh();
            });
            dns.addView(b, Theme.fullWidth(a, 12));
        } else {
            dns.addView(Theme.space(a, 8));
            dns.addView(Theme.body(a, "Vá em Configurações › Rede e internet › DNS privado, escolha \"Nome do host\" e cole:"));
            dns.addView(code(Turbo.DNS_HOST));
            LinearLayout r = Theme.row(a);
            Button copy = Theme.secondary(a, "Copiar");
            copy.setOnClickListener(v -> a.copy(Turbo.DNS_HOST));
            Button open = Theme.secondary(a, "Abrir rede");
            open.setOnClickListener(v -> a.openSetting(Settings.ACTION_WIRELESS_SETTINGS));
            LinearLayout.LayoutParams l1 = Theme.weight(1);
            l1.setMargins(0, dp(10), dp(6), 0);
            LinearLayout.LayoutParams l2 = Theme.weight(1);
            l2.setMargins(dp(6), dp(10), 0, 0);
            r.addView(copy, l1);
            r.addView(open, l2);
            dns.addView(r);
            dns.addView(Theme.space(a, 6));
            dns.addView(Theme.small(a, "Com o Modo Avançado ativo isso vira um botão de 1 toque."));
        }
        root.addView(dns);
    }

    private void runPing() {
        if (pinging) return;
        pinging = true;
        a.refresh();
        new Thread(() -> {
            List<Turbo.Ping> res = new ArrayList<>();
            res.add(Turbo.ping("Google", "www.google.com", 443, 6));
            res.add(Turbo.ping("Cloudflare", "1.1.1.1", 443, 6));
            res.add(Turbo.ping("Servidores em São Paulo (AWS)", "ec2.sa-east-1.amazonaws.com", 443, 6));
            a.ui.post(() -> {
                pinging = false;
                pings = res;
                if (a.tab() == MainActivity.TURBO) a.refresh();
            });
        }).start();
    }

    // ================================================================= Benchmark

    private void buildBenchmark(LinearLayout root) {
        root.addView(Theme.section(a, "Benchmark"));
        LinearLayout c = Theme.card(a);
        header(c, "⏱", Theme.GOLD, "Teste de velocidade", "Processador, memória e armazenamento em cerca de 10 segundos");
        int prev = prefs().getInt("bench_anterior", 0);
        if (bench != null) {
            c.addView(Theme.space(a, 10));
            LinearLayout r = Theme.row(a);
            r.addView(Theme.text(a, String.valueOf(bench.total), 36, Theme.GOLD, true));
            TextView pts = Theme.small(a, "  pontos");
            r.addView(pts, Theme.weight(1));
            if (prev > 0 && prev != bench.total) {
                int diff = (bench.total - prev) * 100 / prev;
                r.addView(Theme.tag(a, (diff >= 0 ? "+" : "") + diff + "% vs anterior", diff >= 0 ? Theme.GREEN : Theme.RED));
            }
            c.addView(r);
            int maxPart = Math.max(1, Math.max(Math.max(bench.cpuSingle, bench.cpuMulti / 2), Math.max(bench.memory, bench.storage)));
            c.addView(benchRow("Processador (1 núcleo)", bench.cpuSingle, maxPart, Theme.CYAN, null));
            c.addView(benchRow("Processador (multi)", bench.cpuMulti / 2, maxPart, Theme.BLUE, null));
            c.addView(benchRow("Memória", bench.memory, maxPart, Theme.PURPLE,
                    String.format(Locale.getDefault(), "%.1f GB/s", bench.memGBs)));
            c.addView(benchRow("Armazenamento", bench.storage, maxPart, Theme.ORANGE,
                    String.format(Locale.getDefault(), "grava %.0f MB/s", bench.writeMBs)));
        } else if (prev > 0 && !benching) {
            c.addView(Theme.space(a, 8));
            c.addView(Theme.body(a, "Último resultado: " + prev + " pontos"));
        }
        if (benching) {
            benchStepView = Theme.text(a, benchStep, 14, Theme.GOLD, true);
            benchStepView.setPadding(0, dp(12), 0, 0);
            c.addView(benchStepView);
        } else {
            benchStepView = null;
            Button b = Theme.primary(a, bench == null ? "⏱  Iniciar benchmark" : "Rodar de novo");
            b.setOnClickListener(v -> runBench());
            c.addView(b, Theme.fullWidth(a, 12));
            c.addView(Theme.space(a, 8));
            c.addView(Theme.small(a, "Dica: rode antes e depois do Otimizar Agora, ou com o celular frio e quente, para comparar."));
        }
        root.addView(c);
    }

    private LinearLayout benchRow(String label, int value, int max, int color, String extra) {
        LinearLayout col = Theme.col(a);
        col.setPadding(0, dp(6), 0, 0);
        LinearLayout r = Theme.row(a);
        r.addView(Theme.text(a, label, 13, Theme.MUTED, false), Theme.weight(1));
        r.addView(Theme.text(a, value + (extra != null ? "  •  " + extra : ""), 13, Theme.TEXT, true));
        col.addView(r);
        BarView b = new BarView(a, 6);
        ((LinearLayout.LayoutParams) b.getLayoutParams()).setMargins(0, dp(6), 0, dp(2));
        b.setPercent(value * 100 / max, color);
        col.addView(b);
        return col;
    }

    private void runBench() {
        if (benching) return;
        benching = true;
        benchStep = "Preparando...";
        if (bench != null) prefs().edit().putInt("bench_anterior", bench.total).apply();
        a.refresh();
        final Context ctx = a.getApplicationContext();
        new Thread(() -> {
            Turbo.Bench b = Turbo.benchmark(ctx, (t, p) -> a.ui.post(() -> {
                benchStep = t + "  " + p + "%";
                if (benchStepView != null) benchStepView.setText(benchStep);
            }));
            a.ui.post(() -> {
                benching = false;
                bench = b;
                if (prefs().getInt("bench_anterior", 0) == 0) prefs().edit().putInt("bench_anterior", b.total).apply();
                if (a.tab() == MainActivity.TURBO) a.refresh();
            });
        }).start();
    }

    // ================================================================= Modo Avançado

    private void buildAdvanced(LinearLayout root, boolean adv) {
        root.addView(Theme.section(a, "Modo Avançado"));
        LinearLayout c = Theme.card(a);
        header(c, "⚙️", adv ? Theme.GREEN : Theme.MUTED, adv ? "Modo Avançado ATIVO ✅" : "Modo Avançado",
                adv ? "O ZEUS pode mudar animações e DNS direto, sem você entrar nas configurações."
                        : "Libere uma permissão especial uma única vez e ganhe controles que apps comuns não têm.");
        if (adv) {
            float sc = Turbo.animScale(a);
            c.addView(Theme.space(a, 8));
            c.addView(kv("Animações agora", sc == 0 ? "Desligadas" : (sc + "x")));
            LinearLayout r = Theme.row(a);
            r.addView(animButton("Normal", 1f, sc), weighted(0));
            r.addView(animButton("Rápida 0,5x", 0.5f, sc), weighted(1));
            r.addView(animButton("Desligar", 0f, sc), weighted(2));
            c.addView(r);
            c.addView(Theme.space(a, 6));
            c.addView(Theme.small(a, "\"Rápida 0,5x\" é o melhor equilíbrio: tudo abre na metade do tempo e continua bonito."));
        } else {
            c.addView(Theme.space(a, 10));
            c.addView(Theme.text(a, "Opção 1 — com computador", 14, Theme.TEXT, true));
            c.addView(Theme.small(a, "Ative a Depuração USB nas Opções do desenvolvedor, ligue o celular no PC e rode:"));
            c.addView(code(Turbo.ADB_GRANT));
            addButton(c, Theme.secondary(a, "Copiar comando"), v -> a.copy(Turbo.ADB_GRANT));
            c.addView(Theme.space(a, 12));
            c.addView(Theme.text(a, "Opção 2 — sem computador (Android 11+)", 14, Theme.TEXT, true));
            c.addView(Theme.small(a, "Ative a \"Depuração por Wi-Fi\" nas Opções do desenvolvedor, instale um app de ADB no "
                    + "próprio celular (como o LADB), pareie e rode o mesmo comando sem o \"adb shell\" do começo."));
            c.addView(Theme.space(a, 8));
            c.addView(Theme.small(a, "É seguro: a permissão só deixa o ZEUS mudar configurações do sistema como animações e DNS. "
                    + "Para desfazer, basta desinstalar o app."));
            addButton(c, Theme.secondary(a, "Verificar de novo"), v -> a.refresh());
        }
        root.addView(c);
    }

    private LinearLayout.LayoutParams weighted(int idx) {
        LinearLayout.LayoutParams lp = Theme.weight(1);
        lp.setMargins(idx == 0 ? 0 : dp(4), dp(10), idx == 2 ? 0 : dp(4), 0);
        return lp;
    }

    private Button animButton(String label, float value, float current) {
        boolean sel = Math.abs(current - value) < 0.01f;
        Button b = sel ? Theme.primary(a, label) : Theme.secondary(a, label);
        b.setTextSize(13);
        b.setPadding(dp(6), dp(11), dp(6), dp(11));
        b.setOnClickListener(v -> {
            if (Turbo.setAnimations(a, value)) a.toast("✨ Animações: " + label);
            else a.toast("Não foi possível alterar as animações");
            a.refresh();
        });
        return b;
    }
}
