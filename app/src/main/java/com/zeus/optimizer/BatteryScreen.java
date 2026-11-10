package com.zeus.optimizer;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aba Bateria: informações em tempo real e dicas para durar mais. */
final class BatteryScreen extends Screen {

    private static final String EXTRA_CYCLES = "android.os.extra.CYCLE_COUNT";

    private GaugeView gauge;
    private LinearLayout tTemp, tVolt, tCurrent, tTime;
    private TextView statusLine;

    BatteryScreen(MainActivity a) {
        super(a);
    }

    private static final class Info {
        int level = -1, health, plugged, status, voltage, cycles = -1;
        float temp;
        String tech = "";
        long currentUa = Long.MIN_VALUE;
        long chargeLeftMs = -1;
        boolean charging;
    }

    private Info read() {
        Info i = new Info();
        Intent b = a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (l >= 0 && s > 0) i.level = l * 100 / s;
            i.temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            i.voltage = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            i.health = b.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            i.plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            i.status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String t = b.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            i.tech = t == null ? "" : t;
            i.cycles = b.getIntExtra(EXTRA_CYCLES, -1);
            i.charging = i.status == BatteryManager.BATTERY_STATUS_CHARGING || i.status == BatteryManager.BATTERY_STATUS_FULL;
        }
        try {
            BatteryManager bm = (BatteryManager) a.getSystemService(Context.BATTERY_SERVICE);
            long cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (cur != Integer.MIN_VALUE && cur != 0) i.currentUa = cur;
            if (Build.VERSION.SDK_INT >= 28) i.chargeLeftMs = bm.computeChargeTimeRemaining();
        } catch (Exception ignored) {
        }
        return i;
    }

    @Override
    void build(LinearLayout root) {
        Info i = read();

        LinearLayout hero = Theme.col(a);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Theme.gradient(a, 26, android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                0xFF052E2B, 0xFF0B1226, 0xFF14352A));
        hero.setPadding(dp(20), dp(18), dp(20), dp(20));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        hlp.setMargins(0, dp(4), 0, dp(14));
        hero.setLayoutParams(hlp);
        gauge = new GaugeView(a);
        gauge.setColors(Theme.RED, Theme.GOLD, Theme.GREEN, Theme.GREEN, Theme.CYAN);
        hero.addView(gauge, new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP));
        gauge.setValue(Math.max(0, i.level), i.charging ? "carregando" : "bateria", "%");
        statusLine = Theme.text(a, statusText(i), 15, Theme.TEXT, true);
        statusLine.setGravity(Gravity.CENTER);
        hero.addView(statusLine);
        root.addView(hero);

        root.addView(Theme.section(a, "Em tempo real"));
        tTemp = statTile("🌡", tempColor(i.temp), "Temperatura", HomeScreen.fmt1(i.temp) + "°C", tempText(i.temp), -1);
        tVolt = statTile("⚡", Theme.GOLD, "Voltagem", i.voltage > 0 ? String.format(java.util.Locale.getDefault(), "%.2f V", i.voltage / 1000f) : "—", "Tensão atual", -1);
        root.addView(pair(tTemp, tVolt));
        tCurrent = statTile("🔌", Theme.CYAN, i.charging ? "Corrente de carga" : "Consumo", currentText(i), plugText(i.plugged), -1);
        tTime = statTile("⏳", Theme.PURPLE, "Carga completa", timeText(i), i.charging ? "Estimativa do Android" : "Conecte o carregador", -1);
        root.addView(pair(tCurrent, tTime));

        root.addView(Theme.section(a, "Saúde"));
        LinearLayout h = Theme.card(a);
        h.addView(kv("Estado", healthText(i.health)));
        h.addView(kv("Tecnologia", i.tech.isEmpty() ? "—" : i.tech));
        h.addView(kv("Ciclos de carga", i.cycles >= 0 ? String.valueOf(i.cycles) : "Não informado pelo aparelho"));
        h.addView(kv("Fonte", plugText(i.plugged)));
        root.addView(h);

        root.addView(Theme.section(a, "Para a bateria durar mais"));
        LinearLayout tips = Theme.card(a);
        String[] list = {
                "Mantenha a carga entre 20% e 80% sempre que puder.",
                "Evite usar o celular pesado enquanto carrega: o calor desgasta a bateria.",
                "Ative o tema escuro — em telas AMOLED economiza bastante.",
                "Reduza o tempo para a tela apagar para 30 segundos.",
                "Desative o Wi-Fi, Bluetooth e localização quando não estiver usando.",
                "Use o modo de economia de bateria abaixo de 20%."
        };
        for (String t : list) {
            TextView tv = Theme.body(a, "•  " + t);
            tv.setPadding(0, dp(4), 0, dp(4));
            tips.addView(tv);
        }
        addButton(tips, Theme.secondary(a, "🔋  Economia de bateria"), v -> a.openSetting(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        addButton(tips, Theme.secondary(a, "📈  Ver o que gasta bateria"), v -> a.openSetting(Intent.ACTION_POWER_USAGE_SUMMARY));
        root.addView(tips);

        // Atualiza os números a cada 3 segundos enquanto a aba estiver aberta
        final int myGen = a.gen;
        a.ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (a.gen != myGen || a.tab() != MainActivity.BATTERY) return;
                update(read());
                a.ui.postDelayed(this, 3000);
            }
        }, 3000);
    }

    private void update(Info i) {
        setValue(tTemp, HomeScreen.fmt1(i.temp) + "°C", tempText(i.temp));
        setValue(tCurrent, currentText(i), plugText(i.plugged));
        setValue(tTime, timeText(i), i.charging ? "Estimativa do Android" : "Conecte o carregador");
        if (i.voltage > 0) setValue(tVolt, String.format(java.util.Locale.getDefault(), "%.2f V", i.voltage / 1000f), "Tensão atual");
        statusLine.setText(statusText(i));
    }

    private static void setValue(LinearLayout tile, String value, String sub) {
        if (tile == null) return;
        View v = tile.findViewWithTag("value");
        View s = tile.findViewWithTag("sub");
        if (v instanceof TextView) ((TextView) v).setText(value);
        if (s instanceof TextView) ((TextView) s).setText(sub);
    }

    private static String statusText(Info i) {
        if (i.status == BatteryManager.BATTERY_STATUS_FULL) return "Carga completa ✅";
        if (i.charging) return "Carregando ⚡";
        if (i.level >= 0 && i.level <= 15) return "Bateria fraca — conecte o carregador";
        return "Usando a bateria";
    }

    private static int tempColor(float t) {
        return t >= 42 ? Theme.RED : t >= 37 ? Theme.ORANGE : Theme.GREEN;
    }

    private static String tempText(float t) {
        return t >= 42 ? "Muito quente!" : t >= 37 ? "Morna" : "Normal";
    }

    private static String currentText(Info i) {
        if (i.currentUa == Long.MIN_VALUE) return "—";
        long ma = Math.abs(i.currentUa);
        if (ma > 20000) ma = ma / 1000; // a maioria informa em µA; alguns já em mA
        return ma + " mA";
    }

    private static String timeText(Info i) {
        if (i.status == BatteryManager.BATTERY_STATUS_FULL) return "Completa";
        if (!i.charging || i.chargeLeftMs <= 0) return "—";
        long min = i.chargeLeftMs / 60000;
        return min >= 60 ? (min / 60) + "h " + (min % 60) + "min" : min + " min";
    }

    private static String plugText(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "Tomada";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Sem fio";
            default: return "Bateria";
        }
    }

    static String healthText(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Boa ✅";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Superaquecida ⚠";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Gasta ⚠";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Sobretensão ⚠";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Fria demais";
            default: return "Desconhecida";
        }
    }
}
