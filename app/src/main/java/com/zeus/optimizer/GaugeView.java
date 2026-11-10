package com.zeus.optimizer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Medidor circular animado (estilo velocímetro, 270°). */
public class GaugeView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();
    private float shown;
    private String caption = "";
    private String suffix = "";
    private int[] colors = {Theme.CYAN, Theme.BLUE, Theme.PURPLE, Theme.GOLD, Theme.GOLD};
    private ValueAnimator anim;

    public GaugeView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF1B2538);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        big.setColor(Theme.TEXT);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        label.setColor(Theme.MUTED);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.1f);
    }

    void setColors(int... c) {
        colors = c;
        invalidate();
    }

    void setValue(float value, String caption, String suffix) {
        this.caption = caption;
        this.suffix = suffix;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, Math.max(0, Math.min(100, value)));
        anim.setDuration(1300);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int max = Theme.dp(getContext(), 210);
        int w = MeasureSpec.getSize(wSpec);
        int s = MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED ? max : Math.min(w, max);
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float stroke = w * 0.075f;
        float pad = stroke * 1.4f;
        oval.set(pad, pad, w - pad, h - pad);
        float cx = w / 2f, cy = h / 2f;

        track.setStrokeWidth(stroke);
        canvas.drawArc(oval, 135, 270, false, track);

        float[] pos = new float[colors.length];
        for (int i = 0; i < colors.length; i++) pos[i] = 0.75f * i / (colors.length - 1);
        SweepGradient sg = new SweepGradient(cx, cy, colors, pos);
        matrix.setRotate(135, cx, cy);
        sg.setLocalMatrix(matrix);

        float sweep = 270f * shown / 100f;
        if (sweep > 0.5f) {
            glow.setShader(sg);
            glow.setStrokeWidth(stroke * 1.6f);
            glow.setAlpha(110);
            glow.setMaskFilter(new BlurMaskFilter(stroke * 0.9f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawArc(oval, 135, sweep, false, glow);
            arc.setShader(sg);
            arc.setStrokeWidth(stroke);
            canvas.drawArc(oval, 135, sweep, false, arc);
        }

        big.setTextSize(w * 0.27f);
        String num = String.valueOf(Math.round(shown));
        canvas.drawText(num, cx, cy + big.getTextSize() * 0.25f, big);
        if (!suffix.isEmpty()) {
            label.setTextSize(w * 0.075f);
            label.setColor(Theme.MUTED);
            float nw = big.measureText(num);
            label.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(suffix, cx + nw / 2f + w * 0.01f, cy + big.getTextSize() * 0.25f, label);
            label.setTextAlign(Paint.Align.CENTER);
        }
        label.setTextSize(w * 0.058f);
        label.setColor(Theme.MUTED);
        canvas.drawText(caption.toUpperCase(), cx, cy + w * 0.2f, label);
    }
}
