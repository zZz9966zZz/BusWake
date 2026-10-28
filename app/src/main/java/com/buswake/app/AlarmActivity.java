package com.buswake.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AlarmActivity extends Activity {
    private TextView timeText;
    private TextView dateText;
    private TextView titleText;
    private TextView stopText;
    private TextView distanceText;
    private TextView hintText;
    private String stopName = "下车站点";
    private boolean testAlarm;
    private double distance = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(0xFF0A0F19);
        window.setNavigationBarColor(0xFF070D17);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        readIntent(getIntent());
        setContentView(buildScreen());
        AlarmController.start(this);
        updateClock();
        handler.post(clockTicker);
    }

    private void readIntent(Intent intent) {
        testAlarm = intent != null && intent.getBooleanExtra(GuardService.EXTRA_TEST, false);
        String name = intent != null ? intent.getStringExtra(GuardService.EXTRA_STOP_NAME) : null;
        if (name != null && !name.trim().isEmpty()) {
            stopName = name;
        }
        if (intent != null) {
            distance = intent.getDoubleExtra(GuardService.EXTRA_DISTANCE, -1);
        }
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF211706, 0xFF0E1624, 0xFF070D17}));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(this, 26), Ui.dp(this, 22), Ui.dp(this, 26), Ui.dp(this, 18));
        scroll.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        timeText = Ui.strong(this, "--:--", 16, 0xFFE2E8F0);
        timeText.setLetterSpacing(0.08f);
        content.addView(timeText);

        dateText = Ui.text(this, "", 10.5f, 0xFF94A3B8);
        dateText.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = Ui.dp(this, 4);
        content.addView(dateText, dateLp);

        AlarmOrbView orb = new AlarmOrbView(this);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 190), Ui.dp(this, 190));
        orbLp.topMargin = Ui.dp(this, 18);
        content.addView(orb, orbLp);

        TextView chip = Ui.chip(this, testAlarm
                        ? "FUNCTION TEST"
                        : "STOP ALERT · " + StopStore.formatRadiusShort(StopStore.getRadiusM(this)),
                testAlarm ? Ui.ACCENT : Ui.AMBER,
                testAlarm ? 0x1F38BDF8 : 0x22FBBF24);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = Ui.dp(this, 10);
        content.addView(chip, chipLp);

        titleText = Ui.strong(this, testAlarm ? "测试闹钟" : "该下车了！", 32, Ui.TEXT);
        titleText.setGravity(Gravity.CENTER);
        titleText.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(this, 9);
        content.addView(titleText, titleLp);

        stopText = Ui.strong(this, stopName, 21, testAlarm ? Ui.ACCENT : Ui.AMBER);
        stopText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopLp.topMargin = Ui.dp(this, 8);
        content.addView(stopText, stopLp);

        distanceText = Ui.text(this, "", 13, 0xFFCBD5E1);
        distanceText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams distLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        distLp.topMargin = Ui.dp(this, 8);
        content.addView(distanceText, distLp);

        hintText = Ui.text(this, "闹钟会持续响铃和震动，直到你按下停止。", 11, 0xFF94A3B8);
        hintText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(this, 14);
        content.addView(hintText, hintLp);

        if (testAlarm) {
            distanceText.setText("测试站点 · 无需下车");
            hintText.setText("听到声音并感到震动，说明到站叫醒功能正常。");
        } else if (distance >= 0) {
            distanceText.setText("已经进入站点 " + radiusLabel() + " 范围，距离约 " + StopStore.formatDistance(distance));
        } else {
            distanceText.setText("已经进入站点 " + radiusLabel() + " 范围");
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 20));
        bottom.setBackgroundColor(0xFF070D17);

        TextView stopButton = Ui.strong(this, "我醒了 · 停止闹钟", 17, 0xFF2A1E05);
        stopButton.setGravity(Gravity.CENTER);
        stopButton.setClickable(true);
        stopButton.setFocusable(true);
        stopButton.setBackground(Ui.button(this, Ui.AMBER, 0xFFFCD34D, 18));
        stopButton.setOnClickListener(v -> stopAlarm());
        bottom.addView(stopButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        TextView footer = Ui.text(this, "请确认带好随身物品再下车", 10.5f, 0xFF64748B);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(this, 11);
        bottom.addView(footer, footerLp);

        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private String radiusLabel() {
        return StopStore.formatRadius(StopStore.getRadiusM(this));
    }

    private void updateClock() {
        Date now = new Date();
        if (timeText != null) {
            timeText.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(now));
        }
        if (dateText != null) {
            dateText.setText(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(now));
        }
    }

    private void stopAlarm() {
        AlarmController.stop();
        Intent i = new Intent(this, GuardService.class)
                .setAction(GuardService.ACTION_STOP_ALARM);
        try {
            startService(i);
        } catch (Exception ignored) {
        }
        finishAndRemoveTask();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "请按下方“我醒了”按钮停止闹钟", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        if (stopText != null) {
            stopText.setText(stopName);
            titleText.setText(testAlarm ? "测试闹钟" : "该下车了！");
            if (testAlarm) {
                distanceText.setText("测试站点 · 无需下车");
                hintText.setText("听到声音并感到震动，说明到站叫醒功能正常。");
            } else if (distance >= 0) {
                distanceText.setText("已经进入站点 " + radiusLabel() + " 范围，距离约 " + StopStore.formatDistance(distance));
            }
        }
        AlarmController.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmController.start(this);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTicker);
        super.onDestroy();
    }

    private static final class AlarmOrbView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase;
        private ValueAnimator animator;

        AlarmOrbView(android.content.Context context) {
            super(context);
            paint.setStrokeCap(Paint.Cap.ROUND);
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2400L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (animator != null && !animator.isStarted()) {
                animator.start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) {
                animator.cancel();
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(getWidth(), getHeight()) / 2f - dp(2);
            float baseR = maxR * 0.54f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            for (int i = 0; i < 3; i++) {
                float p = (phase + i / 3f) % 1f;
                float r = baseR + p * (maxR - baseR);
                int alpha = (int) (95 * (1f - p));
                paint.setColor(Color.argb(alpha, 251, 191, 36));
                canvas.drawCircle(cx, cy, r, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF131F33);
            canvas.drawCircle(cx, cy, baseR, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3f));
            paint.setColor(0xFFFBBF24);
            canvas.drawCircle(cx, cy, baseR, paint);

            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(0x88FBBF24);
            for (int i = 0; i < 12; i++) {
                double angle = Math.toRadians(i * 30 - 90);
                float outer = baseR - dp(10);
                float inner = outer - (i % 3 == 0 ? dp(8) : dp(4));
                canvas.drawLine(
                        cx + (float) Math.cos(angle) * inner,
                        cy + (float) Math.sin(angle) * inner,
                        cx + (float) Math.cos(angle) * outer,
                        cy + (float) Math.sin(angle) * outer,
                        paint);
            }

            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR);
            int minute = cal.get(java.util.Calendar.MINUTE);
            float hourAngle = (float) Math.toRadians((hour + minute / 60f) * 30f - 90f);
            float minuteAngle = (float) Math.toRadians(minute * 6f - 90f);

            paint.setStrokeWidth(dp(6f));
            paint.setColor(0xFFF8FAFC);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(hourAngle) * baseR * 0.45f,
                    cy + (float) Math.sin(hourAngle) * baseR * 0.45f,
                    paint);
            paint.setStrokeWidth(dp(4f));
            paint.setColor(0xFFFBBF24);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(minuteAngle) * baseR * 0.66f,
                    cy + (float) Math.sin(minuteAngle) * baseR * 0.66f,
                    paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFF8FAFC);
            canvas.drawCircle(cx, cy, dp(6), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1f));
            paint.setColor(0x44FBBF24);
            RectF innerRing = new RectF(cx - baseR * 0.8f, cy - baseR * 0.8f,
                    cx + baseR * 0.8f, cy + baseR * 0.8f);
            canvas.drawArc(innerRing, -90, 360, false, paint);
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }
}
