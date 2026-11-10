package com.zeus.optimizer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Cores, tipografia e componentes visuais do ZEUS Optimizer. */
final class Theme {
    static final int BG = 0xFF070B14;
    static final int SURFACE = 0xFF0F172A;
    static final int SURFACE2 = 0xFF1E293B;
    static final int STROKE = 0xFF233047;
    static final int TEXT = 0xFFF8FAFC;
    static final int MUTED = 0xFF94A3B8;
    static final int DIM = 0xFF64748B;
    static final int GOLD = 0xFFFACC15;
    static final int GOLD2 = 0xFFF59E0B;
    static final int BLUE = 0xFF3B82F6;
    static final int CYAN = 0xFF22D3EE;
    static final int GREEN = 0xFF22C55E;
    static final int ORANGE = 0xFFF97316;
    static final int RED = 0xFFEF4444;
    static final int PURPLE = 0xFFA855F7;
    static final int PINK = 0xFFEC4899;

    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Theme() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        double k = b / 1024.0;
        if (k < 1024) return String.format(Locale.getDefault(), "%.0f KB", k);
        double m = k / 1024;
        if (m < 1024) return String.format(Locale.getDefault(), "%.1f MB", m);
        return String.format(Locale.getDefault(), "%.2f GB", m / 1024);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static int scoreColor(int score) {
        return score >= 80 ? GREEN : score >= 55 ? GOLD : RED;
    }

    static int levelColor(int pct) {
        return pct >= 90 ? RED : pct >= 75 ? ORANGE : GREEN;
    }

    // ---------------------------------------------------------------- fundos

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable outlined(Context c, int color, int stroke, float radiusDp) {
        GradientDrawable g = rounded(c, color, radiusDp);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static GradientDrawable gradient(Context c, float radiusDp, GradientDrawable.Orientation o, int... colors) {
        GradientDrawable g = new GradientDrawable(o, colors);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    // ---------------------------------------------------------------- layouts

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(outlined(c, SURFACE, STROKE, 20));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        return l;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, WRAP, w);
    }

    static View space(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpH)));
        return v;
    }

    // ---------------------------------------------------------------- textos

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static TextView title(Context c, String s) {
        return text(c, s, 17, TEXT, true);
    }

    static TextView body(Context c, String s) {
        return text(c, s, 14, 0xFFCBD5E1, false);
    }

    static TextView small(Context c, String s) {
        return text(c, s, 12.5f, MUTED, false);
    }

    static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.ROOT), 12, DIM, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(c, 4), dp(c, 10), 0, dp(c, 8));
        return t;
    }

    static TextView tag(Context c, String s, int color) {
        TextView t = text(c, s, 10.5f, color, true);
        t.setLetterSpacing(0.06f);
        t.setBackground(rounded(c, alpha(color, 0x2E), 8));
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return t;
    }

    /** Ícone redondo com emoji sobre fundo colorido translúcido. */
    static TextView icon(Context c, String emoji, int color, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setTextSize(sizeDp * 0.45f);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(alpha(color, 0x30));
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    // ---------------------------------------------------------------- botões

    private static Button baseButton(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        int p = dp(c, 13);
        b.setPadding(p, p, p, p);
        return b;
    }

    static Button primary(Context c, String s) {
        Button b = baseButton(c, s);
        b.setTextColor(0xFF1A1300);
        b.setBackground(gradient(c, 16, GradientDrawable.Orientation.LEFT_RIGHT, GOLD, GOLD2));
        return b;
    }

    static Button secondary(Context c, String s) {
        Button b = baseButton(c, s);
        b.setTextColor(TEXT);
        b.setBackground(outlined(c, SURFACE2, STROKE, 14));
        return b;
    }

    static Button danger(Context c, String s) {
        Button b = baseButton(c, s);
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(gradient(c, 14, GradientDrawable.Orientation.LEFT_RIGHT, 0xFFDC2626, 0xFFB91C1C));
        return b;
    }

    static LinearLayout.LayoutParams fullWidth(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(c, topDp), 0, 0);
        return lp;
    }
}
