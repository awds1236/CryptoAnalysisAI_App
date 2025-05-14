package com.coinsense.cryptoanalysisai.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class LongShortRatioView extends View {
    private float longRatio = 50f;
    private float shortRatio = 50f;
    private Paint longPaint;
    private Paint shortPaint;
    private Paint textPaint;
    private RectF rectF;

    public LongShortRatioView(Context context) {
        super(context);
        init();
    }

    public LongShortRatioView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        longPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        longPaint.setColor(Color.parseColor("#4CAF50")); // Green

        shortPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shortPaint.setColor(Color.parseColor("#F44336")); // Red

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        rectF = new RectF();
    }

    public void setRatios(float longRatio, float shortRatio) {
        this.longRatio = longRatio;
        this.shortRatio = shortRatio;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        rectF.set(0, 0, width, height);

        // Draw short part (background)
        canvas.drawRect(rectF, shortPaint);

        // Draw long part
        float longWidth = width * (longRatio / 100f);
        canvas.drawRect(0, 0, longWidth, height, longPaint);

        // Draw text
        float centerX = width / 2f;
        float centerY = height / 2f - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText(String.format("%.1f%% vs %.1f%%", longRatio, shortRatio),
                centerX, centerY, textPaint);
    }
}