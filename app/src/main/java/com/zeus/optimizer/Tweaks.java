package com.zeus.optimizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Catálogo de ajustes do Modo PRO. Cada ajuste guarda o valor original no diário
 * antes de mudar qualquer coisa, para que "Desfazer" devolva exatamente o que havia.
 */
final class Tweaks {

    static final int SESSION = 0, SYSTEM = 1;
    private static final String NULL = "\u0000null";

    /** Apps pesados que vivem acordando em segundo plano. */
    static final String[] HEAVY = {
            "com.facebook.katana", "com.facebook.orca", "com.facebook.lite", "com.instagram.android",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.kwai.video", "com.kwai.bulldog",
            "com.google.android.youtube", "com.netflix.mediaclient", "com.shopee.br", "com.mercadolibre",
            "com.einnovation.temu", "br.com.brainweb.ifood", "com.google.android.apps.photos",
            "com.google.android.apps.docs", "com.twitter.android", "com.snapchat.android", "com.pinterest",
            "com.amazon.mShop.android.shopping", "com.alibaba.aliexpresshd", "com.linkedin.android",
            "com.microsoft.skydrive", "com.dropbox.android", "com.dubox.drive", "com.kwai.kwaime"};

    /** Serviços de rastreamento/anúncios de fábrica que podem ser desativados com segurança. */
    static final String[] TELEMETRY = {
            "com.miui.analytics", "com.miui.msa.global", "com.miui.daemon", "com.facebook.services",
            "com.facebook.system", "com.facebook.appmanager", "com.samsung.android.da.daagent",
            "com.samsung.android.mdx.quickboard", "com.heytap.mcs", "com.oplus.statistics.rom",
            "com.coloros.activation"};

    // =================================================================== operações

    abstract static class Op {
        /** true se precisa do Shizuku (comandos ou chaves escondidas). */
        abstract boolean shellOnly();

        abstract void apply(Context c, String id, String pkg);

        abstract void undo(Context c, String id, String pkg);
    }

    /** Muda uma configuração do Android guardando o valor original. */
    static final class Put extends Op {
        final String ns, key, value;
        final boolean shell;

        Put(String ns, String key, String value, boolean shell) {
            this.ns = ns;
            this.key = key;
            this.value = value;
            this.shell = shell;
        }

        @Override
        boolean shellOnly() {
            return shell;
        }

        private String resolve(Context c) {
            if ("{maxhz}".equals(value)) return String.valueOf(Math.round(Turbo.display(c).max));
            return value;
        }

        @Override
        void apply(Context c, String id, String pkg) {
            String jk = id + "|" + ns + ":" + key;
            SharedPreferences j = journal(c);
            if (!j.contains(jk)) {
                String orig = Shell.getSetting(c, ns, key);
                j.edit().putString(jk, orig == null ? NULL : orig).apply();
            }
            Shell.putSetting(c, ns, key, resolve(c));
        }

        @Override
        void undo(Context c, String id, String pkg) {
            String jk = id + "|" + ns + ":" + key;
            SharedPreferences j = journal(c);
            if (!j.contains(jk)) return;
            String orig = j.getString(jk, NULL);
            Shell.putSetting(c, ns, key, NULL.equals(orig) ? null : orig);
            j.edit().remove(jk).apply();
        }
    }

    /** Roda um comando para ligar e outro para desligar. {pkg} vira o pacote do jogo. */
    static final class Cmd extends Op {
        final String on, off;

        Cmd(String on, String off) {
            this.on = on;
            this.off = off;
        }

        @Override
        boolean shellOnly() {
            return true;
        }

        @Override
        void apply(Context c, String id, String pkg) {
            Shell.run(on.replace("{pkg}", pkg == null ? "" : pkg));
        }

        @Override
        void undo(Context c, String id, String pkg) {
            if (off != null) Shell.run(off.replace("{pkg}", pkg == null ? "" : pkg));
        }
    }

    /** Aplica um comando a cada app instalado de uma lista. {pkg} e {uid} são substituídos. */
    static final class ForEach extends Op {
        final String[] pkgs;
        final String on, off;

        ForEach(String[] pkgs, String on, String off) {
            this.pkgs = pkgs;
            this.on = on;
            this.off = off;
        }

        @Override
        boolean shellOnly() {
            return true;
        }

        private String fill(String cmd, ApplicationInfo ai) {
            return cmd.replace("{pkg}", ai.packageName).replace("{uid}", String.valueOf(ai.uid));
        }

        @Override
        void apply(Context c, String id, String gamePkg) {
            StringBuilder sb = new StringBuilder();
            Set<String> done = new HashSet<>();
            for (ApplicationInfo ai : installed(c, pkgs)) {
                if (ai.packageName.equals(gamePkg)) continue;
                sb.append(fill(on, ai)).append(" >/dev/null 2>&1; ");
                done.add(ai.packageName);
            }
            if (sb.length() > 0) Shell.run(sb.toString());
            journal(c).edit().putStringSet(id + "|lista", done).apply();
        }

        @Override
        void undo(Context c, String id, String gamePkg) {
            if (off == null) return;
            Set<String> done = journal(c).getStringSet(id + "|lista", null);
            if (done == null) return;
            StringBuilder sb = new StringBuilder();
            for (ApplicationInfo ai : installed(c, done.toArray(new String[0]))) {
                sb.append(fill(off, ai)).append(" >/dev/null 2>&1; ");
            }
            if (sb.length() > 0) Shell.run(sb.toString());
            journal(c).edit().remove(id + "|lista").apply();
        }
    }

    static List<ApplicationInfo> installed(Context c, String[] pkgs) {
        List<ApplicationInfo> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String p : pkgs) {
            try {
                out.add(pm.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    // =================================================================== ajuste

    static final class Tweak {
        final String id, emoji, title, desc;
        final int scope, minSdk;
        final boolean defOn, warn;
        final List<Op> ops;

        Tweak(String id, int scope, String emoji, String title, String desc, int minSdk, boolean defOn, boolean warn, Op... ops) {
            this.id = id;
            this.scope = scope;
            this.emoji = emoji;
            this.title = title;
            this.desc = desc;
            this.minSdk = minSdk;
            this.defOn = defOn;
            this.warn = warn;
            this.ops = Arrays.asList(ops);
        }

        boolean needsShell() {
            for (Op o : ops) if (o.shellOnly()) return true;
            return false;
        }

        boolean supported() {
            return Build.VERSION.SDK_INT >= minSdk;
        }

        boolean available(Context c) {
            if (!supported()) return false;
            int lvl = Shell.level(c);
            return needsShell() ? lvl == Shell.SHIZUKU : lvl >= Shell.SECURE;
        }

        void apply(Context c, String pkg) {
            for (Op o : ops) {
                try {
                    o.apply(c, id, pkg);
                } catch (Throwable ignored) {
                }
            }
        }

        void undo(Context c, String pkg) {
            for (int i = ops.size() - 1; i >= 0; i--) {
                try {
                    ops.get(i).undo(c, id, pkg);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static SharedPreferences journal(Context c) {
        return c.getSharedPreferences("zeus_diario", Context.MODE_PRIVATE);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("zeus", Context.MODE_PRIVATE);
    }

    // =================================================================== catálogo

    static final List<Tweak> ALL = new ArrayList<>();

    static {
        // ---------------- durante o jogo
        ALL.add(new Tweak("jogo_modo", SESSION, "🎯", "Modo de Jogo: desempenho",
                "Coloca o jogo no perfil \"desempenho\" do Modo de Jogo do Android (cmd game mode performance). É o aviso oficial do sistema para priorizar desempenho. Só jogos compatíveis.",
                31, true, false, new Cmd("cmd game mode performance {pkg}", "cmd game mode standard {pkg}")));
        ALL.add(new Tweak("jogo_hz", SESSION, "🖥", "Tela na taxa máxima",
                "Segura a tela no máximo de Hz que ela suporta durante a partida (peak e min refresh rate).",
                26, true, false,
                new Put("system", "peak_refresh_rate", "{maxhz}", true),
                new Put("system", "min_refresh_rate", "{maxhz}", true)));
        ALL.add(new Tweak("jogo_anim", SESSION, "✨", "Animações desligadas",
                "Zera as três escalas de animação do Android durante a partida.",
                26, true, false,
                new Put("global", "window_animation_scale", "0", false),
                new Put("global", "transition_animation_scale", "0", false),
                new Put("global", "animator_duration_scale", "0", false)));
        ALL.add(new Tweak("jogo_bg", SESSION, "🚫", "Apps pesados proibidos em segundo plano",
                "TikTok, Instagram, Facebook, Kwai, YouTube, Netflix, Shopee, Mercado Livre, Temu, iFood, Fotos, Drive e parecidos são fechados e perdem a permissão de rodar escondidos (appops RUN_ANY_IN_BACKGROUND). Voltam ao normal no fim.",
                26, true, false,
                new ForEach(HEAVY, "am force-stop {pkg}; cmd appops set {pkg} RUN_ANY_IN_BACKGROUND ignore",
                        "cmd appops set {pkg} RUN_ANY_IN_BACKGROUND allow")));
        ALL.add(new Tweak("jogo_dados", SESSION, "📵", "Sem internet em segundo plano para apps pesados",
                "Os mesmos apps entram na lista de bloqueio de dados em segundo plano do Android (cmd netpolicy). Sobra mais banda e menos ping para o jogo.",
                26, true, false,
                new ForEach(HEAVY, "cmd netpolicy add restrict-background-blacklist {uid}",
                        "cmd netpolicy remove restrict-background-blacklist {uid}")));
        ALL.add(new Tweak("jogo_dexopt", SESSION, "⏸", "Pausar otimização de apps em segundo plano",
                "Cancela a recompilação de apps que o Android faz escondido (bg-dexopt) e pesa no processador no meio da partida.",
                26, true, false,
                new Cmd("pm art cancel-bg-dexopt-job || pm cancel-bg-dexopt-job", null)));
        ALL.add(new Tweak("jogo_popup", SESSION, "🔕", "Sem janelinhas de notificação",
                "Desliga os pop-ups (heads-up) que cobrem a tela no meio da partida.",
                26, true, false, new Put("global", "heads_up_notifications_enabled", "0", false)));
        ALL.add(new Tweak("jogo_vibra", SESSION, "📳", "Sem vibração de toque",
                "Desliga a vibração curta ao tocar em botões (haptic feedback).",
                26, true, false, new Put("system", "haptic_feedback_enabled", "0", false)));
        ALL.add(new Tweak("jogo_sons", SESSION, "🔇", "Sem sons do sistema",
                "Desliga o clique de toque, os tons do teclado de discagem e o som de bloqueio.",
                26, true, false,
                new Put("system", "sound_effects_enabled", "0", false),
                new Put("system", "dtmf_tone", "0", false),
                new Put("system", "lockscreen_sounds_enabled", "0", false)));
        ALL.add(new Tweak("jogo_rotacao", SESSION, "🔒", "Travar rotação",
                "Desliga a rotação automática para a tela não girar sozinha no meio da partida.",
                26, false, false, new Put("system", "accelerometer_rotation", "0", false)));
        ALL.add(new Tweak("jogo_filtro", SESSION, "🌙", "Desligar luz noturna e filtros",
                "Desliga a Luz noturna e o Escurecer ainda mais enquanto você joga, para cores corretas.",
                26, true, false,
                new Put("secure", "night_display_activated", "0", false),
                new Put("secure", "reduce_bright_colors_activated", "0", false)));
        ALL.add(new Tweak("jogo_fixo", SESSION, "🔥", "Desempenho fixo (avançado)",
                "Pede ao Android o modo de desempenho fixo (cmd power set-fixed-performance-mode-enabled). Em alguns aparelhos deixa o FPS mais estável, mas ESQUENTA mais. Muitos celulares ignoram.",
                26, false, true,
                new Cmd("cmd power set-fixed-performance-mode-enabled true", "cmd power set-fixed-performance-mode-enabled false")));

        // ---------------- sistema (permanente)
        ALL.add(new Tweak("sys_desfoque", SYSTEM, "🪟", "Desligar desfoque das janelas",
                "Tira o efeito de vidro desfocado do painel de notificações e menus (disable_window_blurs). Alivia a GPU em celulares médios.",
                31, false, false, new Put("global", "disable_window_blurs", "1", false)));
        ALL.add(new Tweak("sys_buscas", SYSTEM, "📡", "Desligar buscas de Wi-Fi e Bluetooth em segundo plano",
                "O Android fica procurando redes e aparelhos mesmo com Wi-Fi e Bluetooth desligados, para melhorar a localização. Isso gasta bateria.",
                26, false, false,
                new Put("global", "wifi_scan_always_enabled", "0", false),
                new Put("global", "ble_scan_always_enabled", "0", false)));
        ALL.add(new Tweak("sys_dados", SYSTEM, "📶", "Desligar \"dados móveis sempre ativos\"",
                "Com isso ligado, o celular mantém os dados móveis conectados mesmo no Wi-Fi. Desligar economiza bateria.",
                26, false, false, new Put("global", "mobile_data_always_on", "0", false)));
        ALL.add(new Tweak("sys_logs", SYSTEM, "🧾", "Desligar registros detalhados",
                "Desliga o registro detalhado de Wi-Fi e o log de Bluetooth que alguns celulares deixam ligados de fábrica.",
                26, false, false,
                new Put("global", "wifi_verbose_logging_enabled", "0", false),
                new Put("secure", "bluetooth_hci_log", "0", false)));
        ALL.add(new Tweak("sys_dev", SYSTEM, "🐞", "Desligar opções de desenvolvedor que tiram FPS",
                "Desliga mostrar toques, local do ponteiro e mostrar atualizações de tela, caso tenham ficado ligados sem querer.",
                26, false, false,
                new Put("system", "show_touches", "0", false),
                new Put("system", "pointer_location", "0", false)));
        ALL.add(new Tweak("sys_conteudo", SYSTEM, "🎞", "Não baixar os Hz para vídeos",
                "Impede que apps e vídeos abaixem a taxa de atualização para combinar com o conteúdo (match_content_frame_rates).",
                31, false, false, new Put("secure", "match_content_frame_rates", "0", false)));
        ALL.add(new Tweak("sys_hz", SYSTEM, "⚡", "Travar a tela na taxa máxima sempre",
                "A tela fica sempre no máximo de Hz, não só nos jogos. Tudo fica mais liso, mas gasta mais bateria.",
                26, false, true, new Put("system", "min_refresh_rate", "{maxhz}", true)));
        ALL.add(new Tweak("sys_cores", SYSTEM, "🎨", "Cores mais vivas",
                "Aumenta a saturação da tela inteira em 25% pelo SurfaceFlinger. Volta ao normal ao desfazer ou ao reiniciar.",
                26, false, false, new Cmd("service call SurfaceFlinger 1022 f 1.25", "service call SurfaceFlinger 1022 f 1.0")));
        ALL.add(new Tweak("sys_telemetria", SYSTEM, "🕵", "Desativar rastreamento de fábrica",
                "Desativa (pm disable-user) serviços de análise e anúncios que vêm instalados: análises e anúncios da Xiaomi, serviços do Facebook, diagnóstico da Samsung e estatísticas da Oppo. Só os que existirem no seu celular.",
                26, false, false, new ForEach(TELEMETRY, "pm disable-user --user 0 {pkg}", "pm enable {pkg}")));
    }

    static Tweak byId(String id) {
        for (Tweak t : ALL) if (t.id.equals(id)) return t;
        return null;
    }

    static List<Tweak> of(int scope) {
        List<Tweak> l = new ArrayList<>();
        for (Tweak t : ALL) if (t.scope == scope && t.supported()) l.add(t);
        return l;
    }

    // =================================================================== estado

    static boolean sessionEnabled(Context c, Tweak t) {
        return prefs(c).getBoolean("tw_" + t.id, t.defOn);
    }

    static void setSessionEnabled(Context c, Tweak t, boolean on) {
        prefs(c).edit().putBoolean("tw_" + t.id, on).apply();
    }

    static boolean systemActive(Context c, Tweak t) {
        return prefs(c).getStringSet("ativos", new HashSet<>()).contains(t.id);
    }

    static void applySystem(Context c, Tweak t, boolean on) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet("ativos", new HashSet<>()));
        if (on) {
            t.apply(c, null);
            s.add(t.id);
        } else {
            t.undo(c, null);
            s.remove(t.id);
        }
        prefs(c).edit().putStringSet("ativos", s).apply();
    }

    /** Aplica os ajustes de partida escolhidos e anota quais foram, para desfazer no fim. */
    static int startSession(Context c, String pkg) {
        Set<String> applied = new HashSet<>();
        for (Tweak t : of(SESSION)) {
            if (!sessionEnabled(c, t) || !t.available(c)) continue;
            t.apply(c, pkg);
            applied.add(t.id);
        }
        String scale = prefs(c).getString("jogo_escala", "");
        String fps = prefs(c).getString("jogo_fps", "");
        if (Build.VERSION.SDK_INT >= 33 && Shell.shizukuGranted() && (!scale.isEmpty() || !fps.isEmpty())) {
            StringBuilder cmd = new StringBuilder("cmd game set");
            if (!scale.isEmpty()) cmd.append(" --downscale ").append(scale);
            if (!fps.isEmpty()) cmd.append(" --fps ").append(fps);
            Shell.run(cmd.append(" ").append(pkg).toString());
            applied.add("jogo_set");
        }
        prefs(c).edit().putStringSet("sessao_tweaks", applied).putString("sessao_pkg", pkg).apply();
        return applied.size();
    }

    static void endSession(Context c) {
        Set<String> applied = prefs(c).getStringSet("sessao_tweaks", null);
        String pkg = prefs(c).getString("sessao_pkg", null);
        if (applied == null || applied.isEmpty()) return;
        for (String id : applied) {
            if ("jogo_set".equals(id)) {
                if (pkg != null) Shell.run("cmd game reset " + pkg);
                continue;
            }
            Tweak t = byId(id);
            if (t != null) t.undo(c, pkg);
        }
        prefs(c).edit().remove("sessao_tweaks").remove("sessao_pkg").apply();
    }

    /** Desfaz todos os ajustes permanentes ativos. */
    static int undoAll(Context c) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet("ativos", new HashSet<>()));
        int n = 0;
        for (String id : s) {
            Tweak t = byId(id);
            if (t != null) {
                t.undo(c, null);
                n++;
            }
        }
        prefs(c).edit().putStringSet("ativos", new HashSet<>()).apply();
        return n;
    }
}
