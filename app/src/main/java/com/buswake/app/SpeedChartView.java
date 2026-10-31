package com.buswake.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public final class SpeedChartView extends View {
    public interface OnPointSelectedListener {
        void onPointSelected(Trip.Point point);
    }

    private Trip trip;
    private OnPointSelectedListener listener;
    private int selectedIndex = -1;
    private float chartLeft;
    private float chartTop;
    private float chartRight;
    private float chartBottom;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipText = new Paint(Paint.ANTI_ALIAS_FLAG);

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

        rulerPaint.setColor(0xFFFBBF24);
        rulerPaint.setStrokeWidth(dp(1.3f));
        rulerPaint.setStyle(Paint.Style.STROKE);
        rulerPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));

        tooltipPaint.setColor(0xF20B1220);
        tooltipPaint.setStyle(Paint.Style.FILL);
        tooltipStroke.setColor(0xFFFBBF24);
        tooltipStroke.setStrokeWidth(dp(1));
        tooltipStroke.setStyle(Paint.Style.STROKE);
        tooltipText.setColor(0xFFF8FAFC);
        tooltipText.setTextSize(sp(9.5f));
        tooltipText.setTypeface(Ui.medium());
    }

    public void setOnPointSelectedListener(OnPointSelectedListener listener) {
        this.listener = listener;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
        selectedIndex = -1;
        if (trip != null && trip.points.size() >= 2) {
            double half = trip.totalDistanceM() / 2.0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < trip.points.size(); i++) {
                double diff = Math.abs(trip.points.get(i).distanceM - half);
                if (diff < best) {
                    best = diff;
                    selectedIndex = i;
                }
            }
        }
        invalidate();
        notifyPoint();
    }

    private void notifyPoint() {
        if (listener != null && trip != null && selectedIndex >= 0
                && selectedIndex < trip.points.size()) {
            listener.onPointSelected(trip.points.get(selectedIndex));
        }
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

        chartLeft = dp(40);
        chartTop = dp(20);
        chartRight = getWidth() - dp(12);
        chartBottom = getHeight() - dp(30);
        if (chartRight <= chartLeft || chartBottom <= chartTop) {
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
            float y = chartBottom - (chartBottom - chartTop) * i / 4f;
            canvas.drawLine(chartLeft, y, chartRight, y, i == 0 ? axisPaint : gridPaint);
            double value = yMax * i / 4.0;
            canvas.drawText(String.format(Locale.CHINA, "%.0f", value),
                    chartLeft - dp(7), y + dp(3), textPaint);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i <= 4; i++) {
            float x = chartLeft + (chartRight - chartLeft) * i / 4f;
            canvas.drawLine(x, chartTop, x, chartBottom, i == 0 ? axisPaint : gridPaint);
            double value = totalKm * i / 4.0;
            textPaint.setTextAlign(i == 0 ? Paint.Align.LEFT : Paint.Align.CENTER);
            canvas.drawText(String.format(Locale.CHINA, "%.1f", value),
                    i == 0 ? x + dp(2) : x, chartBottom + dp(14), textPaint);
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(8.5f));
        canvas.drawText("km/h", chartLeft, chartTop - dp(6), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("路程 km", chartRight, getHeight() - dp(3), textPaint);

        Path line = new Path();
        Path fill = new Path();
        boolean first = true;
        float firstX = chartLeft;
        float lastX = chartLeft;
        for (Trip.Point p : trip.points) {
            float x = chartLeft + (float) (p.distanceM / totalM) * (chartRight - chartLeft);
            float y = chartBottom - (float) (Math.min(p.speedKmh, yMax) / yMax)
                    * (chartBottom - chartTop);
            if (first) {
                line.moveTo(x, y);
                fill.moveTo(x, chartBottom);
                fill.lineTo(x, y);
                firstX = x;
                first = false;
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
            lastX = x;
        }
        fill.lineTo(lastX, chartBottom);
        fill.lineTo(firstX, chartBottom);
        fill.close();

        canvas.save();
        canvas.clipRect(chartLeft, chartTop, chartRight, chartBottom);
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
                float x = chartLeft + (float) (maxPoint.distanceM / totalM)
                        * (chartRight - chartLeft);
                float y = chartBottom - (float) (Math.min(maxPoint.speedKmh, yMax) / yMax)
                        * (chartBottom - chartTop);
                canvas.drawCircle(x, y, dp(3.2f), dotPaint);
            }
        }

        drawRuler(canvas, totalM, yMax);
    }

    private void drawRuler(Canvas canvas, double totalM, double yMax) {
        if (selectedIndex < 0 || selectedIndex >= trip.points.size()) {
            return;
        }
        Trip.Point p = trip.points.get(selectedIndex);
        float x = chartLeft + (float) (p.distanceM / totalM) * (chartRight - chartLeft);
        float y = chartBottom - (float) (Math.min(p.speedKmh, yMax) / yMax)
                * (chartBottom - chartTop);

        canvas.drawLine(x, chartTop, x, chartBottom, rulerPaint);
        Paint pointFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointFill.setColor(0xFFFBBF24);
        pointFill.setStyle(Paint.Style.FILL);
        Paint pointStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointStroke.setColor(0xFFF8FAFC);
        pointStroke.setStyle(Paint.Style.STROKE);
        pointStroke.setStrokeWidth(dp(1.5f));
        canvas.drawCircle(x, y, dp(5), pointFill);
        canvas.drawCircle(x, y, dp(5), pointStroke);

        String distance = p.distanceM < 1000
                ? String.format(Locale.CHINA, "%.0f m", p.distanceM)
                : String.format(Locale.CHINA, "%.2f km", p.distanceM / 1000.0);
        String label = distance + "  ·  " + Trip.formatSpeed1(p.speedKmh);
        float textWidth = tooltipText.measureText(label);
        float boxWidth = textWidth + dp(16);
        float boxHeight = dp(24);
        float boxX = Math.max(chartLeft, Math.min(x - boxWidth / 2f, chartRight - boxWidth));
        float boxY = chartTop + dp(3);
        RectF box = new RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight);
        canvas.drawRoundRect(box, dp(7), dp(7), tooltipPaint);
        canvas.drawRoundRect(box, dp(7), dp(7), tooltipStroke);
        canvas.drawText(label, boxX + dp(8), boxY + dp(16), tooltipText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (trip == null || trip.points.size() < 2) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                selectAt(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                selectAt(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void selectAt(float x) {
        if (chartRight <= chartLeft || trip == null || trip.points.size() < 2) {
            return;
        }
        float clamped = Math.max(chartLeft, Math.min(chartRight, x));
        double target = trip.totalDistanceM() * (clamped - chartLeft) / (chartRight - chartLeft);
        int bestIndex = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < trip.points.size(); i++) {
            double diff = Math.abs(trip.points.get(i).distanceM - target);
            if (diff < best) {
                best = diff;
                bestIndex = i;
            }
        }
        if (bestIndex != selectedIndex) {
            selectedIndex = bestIndex;
            invalidate();
            notifyPoint();
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
