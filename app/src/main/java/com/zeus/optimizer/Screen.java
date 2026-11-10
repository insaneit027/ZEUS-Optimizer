package com.zeus.optimizer;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Base das abas do app. */
abstract class Screen {
    final MainActivity a;

    Screen(MainActivity a) {
        this.a = a;
    }

    abstract void build(LinearLayout root);

    void onActivityResult(int requestCode, int resultCode) {
    }

    int dp(float v) {
        return Theme.dp(a, v);
    }

    Button addButton(LinearLayout parent, Button b, View.OnClickListener l) {
        if (l != null) b.setOnClickListener(l);
        parent.addView(b, Theme.fullWidth(a, 12));
        return b;
    }

    /** Bloco pequeno de estatística, usado em grades de 2 colunas. */
    LinearLayout statTile(String emoji, int color, String label, String value, String sub, int barPct) {
        LinearLayout t = Theme.col(a);
        t.setBackground(Theme.outlined(a, Theme.SURFACE, Theme.STROKE, 18));
        t.setPadding(dp(14), dp(14), dp(14), dp(12));
        LinearLayout top = Theme.row(a);
        top.addView(Theme.icon(a, emoji, color, 34));
        TextView lb = Theme.text(a, label, 12.5f, Theme.MUTED, true);
        lb.setPadding(dp(10), 0, 0, 0);
        top.addView(lb, Theme.weight(1));
        t.addView(top);
        t.addView(Theme.space(a, 10));
        TextView v = Theme.text(a, value, 21, Theme.TEXT, true);
        v.setTag("value");
        t.addView(v);
        TextView s = Theme.small(a, sub);
        s.setTag("sub");
        t.addView(s);
        if (barPct >= 0) {
            BarView bar = new BarView(a, 6);
            bar.setPercent(barPct, color);
            t.addView(bar);
        }
        return t;
    }

    /** Linha com dois blocos lado a lado. */
    LinearLayout pair(View left, View right) {
        LinearLayout r = Theme.row(a);
        r.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Theme.MATCH, 1f);
        l1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, Theme.MATCH, 1f);
        l2.setMargins(dp(6), 0, 0, 0);
        r.addView(left, l1);
        r.addView(right, l2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        lp.setMargins(0, 0, 0, dp(12));
        r.setLayoutParams(lp);
        return r;
    }

    /** Linha "chave .... valor". */
    LinearLayout kv(String key, String value) {
        LinearLayout r = Theme.row(a);
        r.setPadding(0, dp(6), 0, dp(6));
        r.addView(Theme.text(a, key, 14, Theme.MUTED, false), Theme.weight(1));
        TextView v = Theme.text(a, value, 14, Theme.TEXT, true);
        v.setGravity(android.view.Gravity.END);
        r.addView(v, Theme.weight(1.3f));
        return r;
    }
}
