package com.zeus.optimizer;

import android.Manifest;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Aba Ferramentas: Modo Jogo, botão rápido, lembrete, animações e atalhos. */
final class ToolsScreen extends Screen {

    ToolsScreen(MainActivity a) {
        super(a);
    }

    @Override
    void build(LinearLayout root) {
        // ---------------- Modo Jogo
        NotificationManager nm = (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean dndAccess = nm != null && nm.isNotificationPolicyAccessGranted();
        boolean gameOn = a.getSharedPreferences("zeus", Context.MODE_PRIVATE).getBoolean("modo_jogo", false)
                && nm != null && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;

        LinearLayout game = Theme.col(a);
        GradientDrawable gbg = Theme.gradient(a, 22, GradientDrawable.Orientation.TL_BR, 0xFF3B0764, 0xFF1E1B4B, 0xFF0B1226);
        gbg.setStroke(dp(1), 0xFF4C1D95);
        game.setBackground(gbg);
        game.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        glp.setMargins(0, dp(4), 0, dp(12));
        game.setLayoutParams(glp);
        LinearLayout gr = Theme.row(a);
        gr.addView(Theme.icon(a, "🎮", Theme.PURPLE, 48));
        LinearLayout gt = Theme.col(a);
        gt.setPadding(dp(12), 0, 0, 0);
        gt.addView(Theme.text(a, "Modo Jogo", 18, Theme.TEXT, true));
        gt.addView(Theme.small(a, gameOn ? "ATIVADO — notificações silenciadas" : "Encerra apps em segundo plano e silencia notificações"));
        gr.addView(gt, Theme.weight(1));
        game.addView(gr);
        Button gb = gameOn ? Theme.secondary(a, "Desativar Modo Jogo") : Theme.primary(a, "🎮  Ativar Modo Jogo");
        gb.setOnClickListener(v -> {
            if (!dndAccess) {
                a.toast("Permita o acesso ao \"Não perturbe\" para o ZEUS Optimizer");
                a.openSetting(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                return;
            }
            toggleGame(nm, !gameOn);
        });
        game.addView(gb, Theme.fullWidth(a, 14));
        root.addView(game);

        // ---------------- Botão rápido
        LinearLayout tile = card("⚡", Theme.GOLD, "Botão ZEUS Boost",
                "Otimize sem abrir o app: puxe a cortina de notificações e toque em ZEUS Boost.");
        if (Build.VERSION.SDK_INT >= 33) {
            addButton(tile, Theme.secondary(a, "Adicionar às configurações rápidas"), v -> addTile());
        } else {
            tile.addView(Theme.space(a, 8));
            tile.addView(Theme.small(a, "Como adicionar: puxe a cortina duas vezes, toque no lápis (editar) e arraste \"ZEUS Boost\" para cima."));
        }
        root.addView(tile);

        // ---------------- Lembrete
        LinearLayout rem = card("⏰", Theme.CYAN, "Lembrete semanal",
                "Receba uma notificação por semana para otimizar o celular.");
        Switch sw = new Switch(a);
        sw.setText(ReminderReceiver.enabled(a) ? "Ativado" : "Desativado");
        sw.setTextColor(Theme.TEXT);
        sw.setChecked(ReminderReceiver.enabled(a));
        android.content.res.ColorStateList thumb = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.GOLD, 0xFFCBD5E1});
        android.content.res.ColorStateList trk = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Theme.alpha(Theme.GOLD, 0x80), 0xFF475569});
        sw.setThumbTintList(thumb);
        sw.setTrackTintList(trk);
        sw.setOnCheckedChangeListener((b, on) -> {
            if (on && Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
            }
            ReminderReceiver.setEnabled(a, on);
            sw.setText(on ? "Ativado" : "Desativado");
            a.toast(on ? "Lembrete semanal ativado" : "Lembrete desativado");
        });
        rem.addView(sw, Theme.fullWidth(a, 10));
        root.addView(rem);

        // ---------------- Animações
        float scale = 1f;
        try {
            scale = Settings.Global.getFloat(a.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception ignored) {
        }
        LinearLayout anim = card("✨", Theme.PURPLE, "Acelerar animações",
                "Hoje: " + (scale == 0 ? "desligadas" : scale + "x") + (scale <= 0.5f ? " ✅" : " — o ideal é 0,5x"));
        anim.addView(Theme.space(a, 8));
        String[] steps = {
                "1. Abra Configurações › Sobre o telefone.",
                "2. Toque 7 vezes em \"Número da versão\" (ou \"Versão MIUI\").",
                "3. Volte e entre em \"Opções do desenvolvedor\".",
                "4. Mude as 3 opções de \"Escala de animação\" para 0,5x."
        };
        for (String s : steps) {
            TextView t = Theme.body(a, s);
            t.setPadding(0, dp(3), 0, dp(3));
            anim.addView(t);
        }
        addButton(anim, Theme.secondary(a, "Abrir opções do desenvolvedor"), v -> openDeveloper());
        root.addView(anim);

        // ---------------- Atalhos
        root.addView(Theme.section(a, "Atalhos do sistema"));
        root.addView(pair(shortcut("💾", "Armazenamento", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                shortcut("📶", "Uso de dados", Settings.ACTION_DATA_USAGE_SETTINGS)));
        root.addView(pair(shortcut("🌙", "Tela e brilho", Settings.ACTION_DISPLAY_SETTINGS),
                shortcut("📲", "Apps padrão", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
        root.addView(pair(shortcut("🔔", "Notificações", Settings.ACTION_APPLICATION_SETTINGS),
                shortcut("🔄", "Atualizar sistema", "android.settings.SYSTEM_UPDATE_SETTINGS")));

        // ---------------- Sobre
        LinearLayout about = Theme.card(a);
        about.addView(Theme.text(a, "⚡ ZEUS Optimizer 2.0", 15, Theme.GOLD, true));
        about.addView(Theme.small(a, "Sem anúncios. Sem coleta de dados. Tudo roda no seu celular."));
        root.addView(about);
    }

    private LinearLayout card(String emoji, int color, String title, String desc) {
        LinearLayout c = Theme.card(a);
        LinearLayout r = Theme.row(a);
        r.addView(Theme.icon(a, emoji, color, 44));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, 0, 0);
        tx.addView(Theme.text(a, title, 16, Theme.TEXT, true));
        tx.addView(Theme.small(a, desc));
        r.addView(tx, Theme.weight(1));
        c.addView(r);
        return c;
    }

    private LinearLayout shortcut(String emoji, String label, String action) {
        LinearLayout t = Theme.col(a);
        t.setBackground(Theme.outlined(a, Theme.SURFACE, Theme.STROKE, 18));
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.addView(Theme.icon(a, emoji, Theme.BLUE, 36));
        TextView l = Theme.text(a, label, 14, Theme.TEXT, true);
        l.setPadding(0, dp(10), 0, 0);
        t.addView(l);
        t.setOnClickListener(v -> a.openSetting(action));
        return t;
    }

    private void toggleGame(NotificationManager nm, boolean on) {
        try {
            nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    : NotificationManager.INTERRUPTION_FILTER_ALL);
        } catch (Exception e) {
            a.toast("Não foi possível alterar o Não Perturbe");
            return;
        }
        a.getSharedPreferences("zeus", Context.MODE_PRIVATE).edit().putBoolean("modo_jogo", on).apply();
        if (on) {
            new Thread(() -> {
                int n = Optimizer.killBackground(a.getApplicationContext());
                a.ui.post(() -> a.toast("🎮 Modo Jogo ativado • " + n + " apps encerrados. Bom jogo!"));
            }).start();
        } else {
            a.toast("Modo Jogo desativado");
        }
        a.refresh();
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            StatusBarManager sbm = a.getSystemService(StatusBarManager.class);
            sbm.requestAddTileService(new ComponentName(a, BoostTileService.class), "ZEUS Boost",
                    Icon.createWithResource(a, R.drawable.ic_bolt), a.getMainExecutor(),
                    res -> a.ui.post(() -> a.toast(res == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                            ? "O botão já está nas configurações rápidas" : "Pronto! Puxe a cortina para usar")));
        } catch (Exception e) {
            a.toast("Adicione manualmente: puxe a cortina, toque no lápis e arraste ZEUS Boost");
        }
    }

    private void openDeveloper() {
        boolean enabled = false;
        try {
            enabled = Settings.Global.getInt(a.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        } catch (Exception ignored) {
        }
        if (enabled) {
            a.openSetting(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        } else {
            a.toast("Primeiro toque 7x em \"Número da versão\" para liberar as opções");
            a.open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        }
    }
}
