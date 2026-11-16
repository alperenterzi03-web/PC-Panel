package com.pcpanel.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Yuvarlak uçlu dairesel ilerleme halkası ve ortasında yüzde/alt metin çizen özel görünüm.
 * API 17 (Android 4.2.2) ile tam uyumludur.
 */
public class CircularRingView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint percentPaint;
    private Paint subPaint;
    private RectF arcRect;

    private int progress = 0;
    private int ringColor = Color.parseColor("#22E3E8");
    private int trackColor = Color.parseColor("#1E2838");
    private String subText = "";

    public CircularRingView(Context context) {
        super(context);
        init(context, null);
    }

    public CircularRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CircularRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        float density = context.getResources().getDisplayMetrics().density;

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(13f * density);
        trackPaint.setColor(trackColor);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(13f * density);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ringColor);

        percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        percentPaint.setColor(Color.parseColor("#F0F6FC"));
        percentPaint.setTextAlign(Paint.Align.CENTER);
        percentPaint.setFakeBoldText(true);
        percentPaint.setTextSize(34f * density);

        subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(Color.parseColor("#7D8DA1"));
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextSize(12f * density);

        arcRect = new RectF();
    }

    public void setProgress(int value) {
        int clamped = Math.max(0, Math.min(100, value));
        if (this.progress != clamped) {
            this.progress = clamped;
            invalidate();
        }
    }

    public void setRingColor(int color) {
        if (this.ringColor != color) {
            this.ringColor = color;
            progressPaint.setColor(color);
            invalidate();
        }
    }

    public void setTrackColor(int color) {
        if (this.trackColor != color) {
            this.trackColor = color;
            trackPaint.setColor(color);
            invalidate();
        }
    }

    public void setSubText(String text) {
        String safe = text == null ? "" : text;
        if (!safe.equals(this.subText)) {
            this.subText = safe;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        if (size <= 0) {
            return;
        }

        float stroke = trackPaint.getStrokeWidth();
        float pad = stroke / 2f + 6f;
        float left = (w - size) / 2f + pad;
        float top = (h - size) / 2f + pad;
        float right = left + size - pad * 2f;
        float bottom = top + size - pad * 2f;
        arcRect.set(left, top, right, bottom);

        // Arka halka
        canvas.drawOval(arcRect, trackPaint);

        // İlerleme yayı (saat 12 yönünden başlar)
        if (progress > 0) {
            float sweep = (progress / 100f) * 360f;
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint);
        }

        // Ortadaki yüzde ve alt bilgi
        float cx = w / 2f;
        float cy = h / 2f;
        String pctStr = progress + "%";

        // Metin boyutunu halka çapına göre ölçekle
        float innerDiameter = size - stroke * 2f - 16f;
        percentPaint.setTextSize(Math.max(24f, innerDiameter * 0.28f));
        subPaint.setTextSize(Math.max(12f, innerDiameter * 0.105f));

        Paint.FontMetrics fm = percentPaint.getFontMetrics();
        if (subText.length() > 0) {
            float pctY = cy - (fm.ascent + fm.descent) / 2f - subPaint.getTextSize() * 0.45f;
            canvas.drawText(pctStr, cx, pctY, percentPaint);
            canvas.drawText(subText, cx, pctY + subPaint.getTextSize() * 1.45f, subPaint);
        } else {
            float pctY = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(pctStr, cx, pctY, percentPaint);
        }
    }
}
