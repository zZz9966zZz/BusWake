package com.buswake.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TripListActivity extends Activity {
    private static final int REQ_LOCATION = 401;
    private static final int REQ_NOTIFICATION = 402;

    private LinearLayout recordCard;
    private LinearLayout listContainer;
    private View recordDot;
    private TextView recordStatus;
    private TextView recordDetail;
    private TextView recordButton;
    private TextView historyCount;
    private boolean pendingStart;
    private boolean lastRecordingState = false;
    private int lastSavedCounter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshRecording();
            int counter = TripStore.savedCounter(TripListActivity.this);
            if (counter != lastSavedCounter) {
                lastSavedCounter = counter;
                refreshList();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        lastSavedCounter = TripStore.savedCounter(this);
        setContentView(buildScreen());
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1220, 0xFF0D1A2C, 0xFF0B1220}));

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(0xFF172033);
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(content, Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        recordCard = buildRecordCard();
        content.addView(recordCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(buildHistoryHeader(), marginsTop(24));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, marginsTop(11));

        TextView footer = Ui.text(this, "路线与速度仅保存在本机，不会上传", 10, 0xFF64748B);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, marginsTop(20));
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));

        TextView back = Ui.strong(this, "←", 26, Ui.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.button(this, 0x00000000, 0x22FFFFFF, 12));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = Ui.dp(this, 8);
        bar.addView(titles, titlesLp);

        titles.addView(Ui.strong(this, "行程记录", 17, Ui.TEXT));
        TextView sub = Ui.text(this, "手动记录路线 · 逐点速度 · 历史回放", 10.5f, 0xFF64748B);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 3);
        titles.addView(sub, subLp);
        return bar;
    }

    private LinearLayout buildRecordCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 18, 1, Ui.BORDER));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        recordDot = new View(this);
        recordDot.setBackground(Ui.circle(this, 0xFF64748B));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9));
        dotLp.rightMargin = Ui.dp(this, 8);
        top.addView(recordDot, dotLp);

        recordStatus = Ui.strong(this, "手动记录行程", 13, Ui.MUTED);
        top.addView(recordStatus, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        recordDetail = Ui.text(this,
                "上车后点开始，下车后点结束。记录全程 GPS 路线和每个时刻的速度。",
                11.5f, Ui.MUTED);        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = Ui.dp(this, 8);
        card.addView(recordDetail, detailLp);

        recordButton = Ui.strong(this, "开始记录", 15, 0xFF052331);
        recordButton.setGravity(Gravity.CENTER);
        recordButton.setClickable(true);
        recordButton.setFocusable(true);
        recordButton.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 14));
        recordButton.setOnClickListener(v -> {
            if (TripStore.isRecording(this)) {
                stopRecording();
            } else {
                startFlow();
            }
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        buttonLp.topMargin = Ui.dp(this, 14);
        card.addView(recordButton, buttonLp);

        TextView hint = Ui.text(this, "记录时固定每秒采样，锁屏后仍会继续。", 10, 0xFF64748B);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(this, 8);
        card.addView(hint, hintLp);
        return card;
    }

    private View buildHistoryHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.strong(this, "历史行程", 18, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        historyCount = Ui.text(this, "0 条", 11, Ui.MUTED);
        row.addView(historyCount);
        return row;
    }

    private LinearLayout.LayoutParams marginsTop(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private void refreshAll() {
        refreshRecording();
        refreshList();
    }

    private void refreshRecording() {
        boolean recording = TripStore.isRecording(this);
        boolean changed = recording != lastRecordingState;
        lastRecordingState = recording;

        if (recording) {
            recordDot.setBackground(Ui.circle(this, Ui.DANGER));
            recordStatus.setText("正在记录");
            recordStatus.setTextColor(Ui.DANGER);
            long started = TripStore.recordingStartedAt(this);
            long elapsed = Math.max(0, (System.currentTimeMillis() - started) / 1000);
            double distance = TripStore.recordingDistanceM(this);
            double speed = TripStore.recordingSpeedKmh(this);
            int points = TripStore.recordingPointCount(this);
            recordDetail.setText(String.format(Locale.CHINA,
                    "%s · %s · 当前 %s · %d 个采样点",
                    Trip.formatDuration(elapsed),
                    StopStore.formatDistance(distance),
                    StopStore.formatSpeed(speed),
                    points));
            recordButton.setText("结束并保存");
            if (changed) {
                recordButton.setTextColor(Ui.DANGER);
                recordButton.setBackground(Ui.outlineButton(this, 0x14F87171, 0x2AF87171, 0xAAF87171, 14));
            }
            if (changed) {
                recordCard.setBackground(Ui.rounded(this, 0xFF1D1720, 18, 1, 0x88F87171));
            }
        } else {
            recordDot.setBackground(Ui.circle(this, Ui.ACCENT));
            recordStatus.setText("手动记录行程");
            recordStatus.setTextColor(Ui.TEXT);
            recordDetail.setText("上车后点开始，下车后点结束。记录全程 GPS 路线和每个时刻的速度。");
            recordButton.setText("开始记录");
            if (changed) {
                recordButton.setTextColor(0xFF052331);
                recordButton.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 14));
            }
            if (changed) {
                recordCard.setBackground(Ui.rounded(this, Ui.SURFACE, 18, 1, Ui.BORDER));
            }
        }
    }

    private void refreshList() {
        List<Trip> trips = new ArrayList<>(TripStore.loadAll(this));
        historyCount.setText(trips.size() + " 条");
        listContainer.removeAllViews();
        if (trips.isEmpty()) {
            listContainer.addView(buildEmptyCard());
            return;
        }
        for (Trip trip : trips) {
            listContainer.addView(buildTripCard(trip), tripCardParams());
        }
    }

    private View buildTripCard(Trip trip) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 10));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16, 1, Ui.BORDER));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openTrip(trip));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.strong(this, trip.title(), 15, Ui.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView points = Ui.chip(this, trip.points.size() + " 点", Ui.MUTED, 0x1494A3B8);
        top.addView(points);
        card.addView(top);

        TextView sub = Ui.text(this, trip.subtitle()
                + " · 最高 " + String.format(Locale.CHINA, "%.0f km/h", trip.maxSpeedKmh()),
                11, Ui.MUTED);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 6);
        card.addView(sub, subLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionLp.topMargin = Ui.dp(this, 7);

        TextView view = Ui.strong(this, "查看路线与速度曲线", 11.5f, Ui.ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(this, 9), Ui.dp(this, 7), Ui.dp(this, 9), Ui.dp(this, 7));
        view.setBackground(Ui.outlineButton(this, 0x0D38BDF8, 0x2638BDF8, 0x5538BDF8, 9));
        view.setOnClickListener(v -> openTrip(trip));
        actions.addView(view);

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView delete = Ui.strong(this, "删除", 11.5f, Ui.DANGER);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
        delete.setOnClickListener(v -> confirmDelete(trip));
        actions.addView(delete);
        card.addView(actions, actionLp);
        return card;
    }

    private View buildEmptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24));
        card.setBackground(Ui.rounded(this, 0xFF0F1A2B, 16, 1, 0xFF22324D));
        TextView title = Ui.strong(this, "还没有行程记录", 14, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView desc = Ui.text(this, "乘车时点“开始记录”，到站后点“结束并保存”，这里会显示路线和速度曲线。", 11, Ui.MUTED);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 7);
        card.addView(desc, lp);
        return card;
    }

    private LinearLayout.LayoutParams tripCardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 10);
        return lp;
    }

    private void startFlow() {
        if (!hasFineLocation() && !hasCoarseLocation()) {
            pendingStart = true;
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }
        if (!hasFineLocation()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要精确位置")
                    .setMessage("路线记录和速度曲线需要精确位置，请在系统设置中将定位权限改为“精确位置”。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去设置", (d, w) -> openAppDetails())
                    .show();
            return;
        }
        checkGpsAndNotification();
    }

    private void checkGpsAndNotification() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle("GPS 未开启")
                    .setMessage("路线记录需要 GPS。打开定位服务后再开始，否则轨迹会缺失。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去开启", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    })
                    .show();
            return;
        }
        if (!notificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("通知权限未开启")
                    .setMessage("记录时需要通过常驻通知随时结束行程。请先允许通知再开始。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去开启", (d, w) -> openNotificationSettings())
                    .show();
            return;
        }
        startRecordingNow();
    }

    private void startRecordingNow() {
        Intent i = new Intent(this, TripRecordService.class)
                .setAction(TripRecordService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Toast.makeText(this, "开始记录行程", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshAll, 400);
    }

    private void stopRecording() {
        Intent i = new Intent(this, TripRecordService.class)
                .setAction(TripRecordService.ACTION_STOP);
        startService(i);
        Toast.makeText(this, "正在结束并保存…", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshAll, 900);
    }

    private void openTrip(Trip trip) {
        startActivity(new Intent(this, TripDetailActivity.class)
                .putExtra("trip_id", trip.id));
    }

    private void confirmDelete(Trip trip) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条行程？")
                .setMessage(trip.title() + "\n删除后路线和速度数据无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    TripStore.delete(this, trip.id);
                    refreshList();
                })
                .show();
    }

    private boolean hasFineLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCoarseLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationsEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasFineLocation() || hasCoarseLocation()) {
                if (pendingStart) {
                    pendingStart = false;
                    startFlow();
                }
            } else {
                pendingStart = false;
                new AlertDialog.Builder(this)
                        .setTitle("无法记录行程")
                        .setMessage("需要定位权限才能记录路线。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去设置", (d, w) -> openAppDetails())
                        .show();
            }
        } else if (requestCode == REQ_NOTIFICATION) {
            if (notificationsEnabled()) {
                startRecordingNow();
            } else {
                Toast.makeText(this, "通知权限未开启，无法开始记录", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(i);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }
}
