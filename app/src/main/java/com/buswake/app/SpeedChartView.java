package com.buswake.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Locale;

public final class SpeedChartView extends View {
    private Trip trip;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedChartView(Context context) {
        super(context);
        gridPaint.setColor(0x22FFFFFF);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setStyle(Paint.Style.STROKE);

        axisPaint.setColor(0x55FFFFFF);
        axisPaint.setStrokeWidth(dp(1));
        axisPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(0xFF7D8DA6);
        textPaint.setTextSize(sp(9));
        textPaint.setTypeface(Ui.regular());

        linePaint.setColor(0xFF38BDF8);
        linePaint.setStrokeWidth(dp(2.4f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(0x2238BDF8);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setColor(0xFFFBBF24);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (trip == null || trip.points.size() < 2 || trip.totalDistanceM() < 1) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(11));
            canvas.drawText("暂无轨迹数据", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        float left = dp(40);
        float top = dp(20);
        float right = getWidth() - dp(12);
        float bottom = getHeight() - dp(30);
        if (right <= left || bottom <= top) {
            return;
        }

        double totalM = Math.max(1, trip.totalDistanceM());
        double totalKm = totalM / 1000.0;
        double maxSpeed = trip.maxSpeedKmh();
        double step = 20;
        double yMax = Math.max(40, Math.ceil(maxSpeed / step) * step);

        textPaint.setTextSize(sp(9));
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, i == 0 ? axisPaint : gridPaint);
            double value = yMax * i / 4.0;
            canvas.drawText(String.format(Locale.CHINA, "%.0f", value),
                    left - dp(7), y + dp(3), textPaint);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i <= 4; i++) {
            float x = left + (right - left) * i / 4f;
            canvas.drawLine(x, top, x, bottom, i == 0 ? axisPaint : gridPaint);
            double value = totalKm * i / 4.0;
            textPaint.setTextAlign(i == 0 ? Paint.Align.LEFT : Paint.Align.CENTER);
            canvas.drawText(String.format(Locale.CHINA, "%.1f", value),
                    i == 0 ? x + dp(2) : x, bottom + dp(14), textPaint);
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(8.5f));
        canvas.drawText("km/h", left, top - dp(6), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("路程 km", right, getHeight() - dp(3), textPaint);

        Path line = new Path();
        Path fill = new Path();
        boolean first = true;
        float firstX = left;
        float lastX = left;
        for (Trip.Point p : trip.points) {
            float x = left + (float) (p.distanceM / totalM) * (right - left);
            float y = bottom - (float) (Math.min(p.speedKmh, yMax) / yMax) * (bottom - top);
            if (first) {
                line.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
                firstX = x;
                first = false;
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
            lastX = x;
        }
        fill.lineTo(lastX, bottom);
        fill.lineTo(firstX, bottom);
        fill.close();

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
        canvas.restore();

        double max = trip.maxSpeedKmh();
        if (max > 0) {
            Trip.Point maxPoint = null;
            for (Trip.Point p : trip.points) {
                if (maxPoint == null || p.speedKmh > maxPoint.speedKmh) {
                    maxPoint = p;
                }
            }
            if (maxPoint != null) {
                float x = left + (float) (maxPoint.distanceM / totalM) * (right - left);
                float y = bottom - (float) (Math.min(maxPoint.speedKmh, yMax) / yMax) * (bottom - top);
                canvas.drawCircle(x, y, dp(3.2f), dotPaint);
            }
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
