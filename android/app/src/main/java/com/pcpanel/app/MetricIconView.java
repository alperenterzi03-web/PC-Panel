package com.pcpanel.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * CPU, GPU, RAM ve DİSK için minimalist çizgi ikonlar çizen özel görünüm.
 * API 17 (Android 4.2.2) ile tam uyumludur.
 */
public class MetricIconView extends View {

    private Paint strokePaint;
    private Paint fillPaint;
    private RectF rect;
    private String iconType = "cpu";

    public MetricIconView(Context context) {
        super(context);
        init(context);
    }

    public MetricIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MetricIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int color = Color.parseColor("#8FA3B8");

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.0f * density);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setColor(color);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(color);

        rect = new RectF();
        Object tag = getTag();
        if (tag != null) {
            iconType = tag.toString();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float s = Math.min(w, h);
        if (s <= 0) {
            return;
        }
        float ox = (w - s) / 2f;
        float oy = (h - s) / 2f;

        if ("gpu".equals(iconType)) {
            drawGpu(canvas, ox, oy, s);
        } else if ("ram".equals(iconType)) {
            drawRam(canvas, ox, oy, s);
        } else if ("disk".equals(iconType)) {
            drawDisk(canvas, ox, oy, s);
        } else {
            drawCpu(canvas, ox, oy, s);
        }
    }

    private void drawCpu(Canvas c, float ox, float oy, float s) {
        // Dış çip karesi
        rect.set(ox + s * 0.24f, oy + s * 0.24f, ox + s * 0.76f, oy + s * 0.76f);
        c.drawRoundRect(rect, s * 0.06f, s * 0.06f, strokePaint);

        // İç çekirdek karesi
        rect.set(ox + s * 0.38f, oy + s * 0.38f, ox + s * 0.62f, oy + s * 0.62f);
        c.drawRect(rect, fillPaint);

        // 4 kenarda 3'er pin
        float[] pos = {0.36f, 0.50f, 0.64f};
        for (float p : pos) {
            c.drawLine(ox + s * p, oy + s * 0.10f, ox + s * p, oy + s * 0.24f, strokePaint);
            c.drawLine(ox + s * p, oy + s * 0.76f, ox + s * p, oy + s * 0.90f, strokePaint);
            c.drawLine(ox + s * 0.10f, oy + s * p, ox + s * 0.24f, oy + s * p, strokePaint);
            c.drawLine(ox + s * 0.76f, oy + s * p, ox + s * 0.90f, oy + s * p, strokePaint);
        }
    }

    private void drawGpu(Canvas c, float ox, float oy, float s) {
        // Ekran kartı gövdesi
        rect.set(ox + s * 0.12f, oy + s * 0.22f, ox + s * 0.90f, oy + s * 0.72f);
        c.drawRoundRect(rect, s * 0.07f, s * 0.07f, strokePaint);

        // Sol montaj braketi
        c.drawLine(ox + s * 0.12f, oy + s * 0.14f, ox + s * 0.12f, oy + s * 0.84f, strokePaint);

        // Fan dairesi ve merkezi
        c.drawCircle(ox + s * 0.54f, oy + s * 0.47f, s * 0.16f, strokePaint);
        c.drawCircle(ox + s * 0.54f, oy + s * 0.47f, s * 0.05f, fillPaint);

        // Alt PCIe slot çizgisi
        c.drawLine(ox + s * 0.28f, oy + s * 0.82f, ox + s * 0.72f, oy + s * 0.82f, strokePaint);
    }

    private void drawRam(Canvas c, float ox, float oy, float s) {
        // RAM modülü gövdesi
        rect.set(ox + s * 0.10f, oy + s * 0.26f, ox + s * 0.90f, oy + s * 0.68f);
        c.drawRoundRect(rect, s * 0.06f, s * 0.06f, strokePaint);

        // 3 bellek yongası
        float[] xs = {0.20f, 0.43f, 0.66f};
        for (float x : xs) {
            rect.set(ox + s * x, oy + s * 0.36f, ox + s * (x + 0.14f), oy + s * 0.56f);
            c.drawRect(rect, fillPaint);
        }

        // Alt pin dişleri
        float[] pins = {0.20f, 0.32f, 0.44f, 0.58f, 0.70f, 0.80f};
        for (float p : pins) {
            c.drawLine(ox + s * p, oy + s * 0.68f, ox + s * p, oy + s * 0.82f, strokePaint);
        }
    }

    private void drawDisk(Canvas c, float ox, float oy, float s) {
        // Üst elips
        rect.set(ox + s * 0.20f, oy + s * 0.14f, ox + s * 0.80f, oy + s * 0.34f);
        c.drawOval(rect, strokePaint);

        // Yan dikey çizgiler
        c.drawLine(ox + s * 0.20f, oy + s * 0.24f, ox + s * 0.20f, oy + s * 0.76f, strokePaint);
        c.drawLine(ox + s * 0.80f, oy + s * 0.24f, ox + s * 0.80f, oy + s * 0.76f, strokePaint);

        // Orta yay
        rect.set(ox + s * 0.20f, oy + s * 0.40f, ox + s * 0.80f, oy + s * 0.60f);
        c.drawArc(rect, 0f, 180f, false, strokePaint);

        // Alt yay
        rect.set(ox + s * 0.20f, oy + s * 0.66f, ox + s * 0.80f, oy + s * 0.86f);
        c.drawArc(rect, 0f, 180f, false, strokePaint);
    }
}
