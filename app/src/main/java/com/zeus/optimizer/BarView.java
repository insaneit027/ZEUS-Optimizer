package com.zeus.optimizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;

/** Barra arredondada com um ou vários segmentos coloridos. */
public class BarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private float[] values = new float[0];
    private int[] colors = new int[0];
    private float total = 1;

    public BarView(Context c, int heightDp) {
        super(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.dp(c, heightDp));
        lp.setMargins(0, Theme.dp(c, 10), 0, Theme.dp(c, 8));
        setLayoutParams(lp);
    }

    /** Uma barra simples de 0 a 100. */
    void setPercent(int pct, int color) {
        set(100, new float[]{pct}, new int[]{color});
    }

    void set(float total, float[] values, int[] colors) {
        this.total = total <= 0 ? 1 : total;
        this.values = values;
        this.colors = colors;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = h / 2f;
        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        paint.setColor(0xFF1B2538);
        canvas.drawRect(rect, paint);
        float x = 0;
        for (int i = 0; i < values.length; i++) {
            float seg = w * values[i] / total;
            if (seg <= 0) continue;
            paint.setColor(colors[i % colors.length]);
            canvas.drawRect(x, 0, Math.min(w, x + seg), h, paint);
            x += seg;
        }
        canvas.restore();
    }
}
