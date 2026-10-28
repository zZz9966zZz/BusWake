package com.buswake.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_LOCATION = 201;
    private static final int REQ_NOTIFICATION = 202;

    private SharedPreferences prefs;
    private LinearLayout stopsContainer;
    private LinearLayout warningContainer;
    private TextView statusTitle;
    private TextView statusTag;
    private TextView statusDistance;
    private TextView statusDetail;
    private View statusDot;
    private ProgressBar statusProgress;
    private TextView stopCount;
    private TextView cta;
    private TextView ctaHint;
    private TextView headerSubtitle;
    private TextView headerBadge;
    private TextView tipsRadiusLine;
    private TextView tipsSpeedLine;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Stop> stops = new ArrayList<>();
    private boolean pendingStart;
    private int ctaMode = -1;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = StopStore.prefs(this);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Ui.BG);
        }
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshAll();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1220, 0xFF0D1A2C, 0xFF0B1220});
        root.setBackground(background);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(column, Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 30));
        scroll.addView(column, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(buildHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout statusCard = buildStatusCard();
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = Ui.dp(this, 22);
        column.addView(statusCard, statusLp);

        warningContainer = new LinearLayout(this);
        warningContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams warningLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        warningLp.topMargin = Ui.dp(this, 12);
        column.addView(warningContainer, warningLp);

        column.addView(buildStopsHeader(), marginsTop(26));

        stopsContainer = new LinearLayout(this);
        stopsContainer.setOrientation(LinearLayout.VERTICAL);
        column.addView(stopsContainer, marginsTop(12));

        column.addView(buildToolsRow(), marginsTop(12));

        column.addView(buildTipsCard(), marginsTop(14));

        TextView footer = Ui.text(this, "到站叫醒 v1.6  ·  站点坐标仅保存在本机", 10, 0xFF64748B);
        footer.setGravity(Gravity.CENTER);
        column.addView(footer, marginsTop(22));

        View divider = new View(this);
        divider.setBackgroundColor(0xFF172033);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setClipToOutline(true);
        logo.setBackground(Ui.rounded(this, Ui.SURFACE, 15));
        row.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = Ui.dp(this, 13);
        row.addView(titles, titlesLp);

        TextView title = Ui.strong(this, "到站叫醒", 25, Ui.TEXT);
        titles.addView(title);

        headerSubtitle = Ui.text(this, "BUS WAKE  ·  2 KM AHEAD", 9.5f, 0xFF64748B);
        headerSubtitle.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 5);
        titles.addView(headerSubtitle, subLp);

        headerBadge = Ui.chip(this, "2 KM", Ui.ACCENT, 0x1A38BDF8);
        headerBadge.setClickable(true);
        headerBadge.setFocusable(true);
        headerBadge.setContentDescription("叫醒设置");
        headerBadge.setOnClickListener(v -> openSettings());
        row.addView(headerBadge);
        return row;
    }

    private LinearLayout buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 20, 1, Ui.BORDER));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new View(this);
        statusDot.setBackground(Ui.circle(this, 0xFF64748B));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9));
        dotLp.rightMargin = Ui.dp(this, 8);
        top.addView(statusDot, dotLp);

        statusTitle = Ui.strong(this, "守护未开启", 12, Ui.MUTED);
        top.addView(statusTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusTag = Ui.chip(this, "待命", Ui.MUTED, 0x1A94A3B8);
        top.addView(statusTag);
        card.addView(top);

        statusDistance = Ui.strong(this, "准备出发？", 24, Ui.TEXT);
        LinearLayout.LayoutParams distLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        distLp.topMargin = Ui.dp(this, 12);
        card.addView(statusDistance, distLp);

        statusProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        statusProgress.setMax(5000);
        statusProgress.setProgress(0);
        statusProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        statusProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF263A55));
        statusProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        progressLp.topMargin = Ui.dp(this, 13);
        card.addView(statusProgress, progressLp);

        statusDetail = Ui.text(this, "选择下车站点并点击开始守护，进入设定范围会叫醒你。", 11.5f, Ui.MUTED);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = Ui.dp(this, 9);
        card.addView(statusDetail, detailLp);
        return card;
    }

    private View buildStopsHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = Ui.strong(this, "我的站点", 18, Ui.TEXT);
        row.addView(title);

        stopCount = Ui.text(this, "0 个", 11, Ui.MUTED);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countLp.leftMargin = Ui.dp(this, 8);
        row.addView(stopCount, countLp);

        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView add = Ui.strong(this, "＋ 添加", 12, Ui.ACCENT);
        add.setGravity(Gravity.CENTER);
        add.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        add.setBackground(Ui.outlineButton(this, 0x0F38BDF8, 0x2638BDF8, 0x6638BDF8, 10));
        add.setOnClickListener(v -> openMapPicker());
        row.addView(add);
        return row;
    }

    private View buildToolsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView settings = Ui.strong(this, "叫醒设置", 12, Ui.ACCENT);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        settings.setBackground(Ui.outlineButton(this, 0x0F38BDF8, 0x2638BDF8, 0x6638BDF8, 14));
        settings.setOnClickListener(v -> openSettings());
        row.addView(settings, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));

        View gap1 = new View(this);
        row.addView(gap1, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView trips = Ui.strong(this, "行程记录", 12, 0xFFBAE6FD);
        trips.setGravity(Gravity.CENTER);
        trips.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        trips.setBackground(Ui.outlineButton(this, 0x0A38BDF8, 0x2038BDF8, 0x5538BDF8, 14));
        trips.setOnClickListener(v -> openTrips());
        row.addView(trips, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));

        View gap2 = new View(this);
        row.addView(gap2, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView test = Ui.strong(this, "试听闹钟", 12, 0xFFCBD5E1);
        test.setGravity(Gravity.CENTER);
        test.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        test.setBackground(Ui.outlineButton(this, 0x0A94A3B8, 0x2094A3B8, 0x5594A3B8, 14));
        test.setOnClickListener(v -> confirmTestAlarm());
        row.addView(test, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));

        View gap3 = new View(this);
        row.addView(gap3, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView battery = Ui.strong(this, "数据与日志", 12, 0xFFCBD5E1);
        battery.setGravity(Gravity.CENTER);
        battery.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        battery.setBackground(Ui.outlineButton(this, 0x0A94A3B8, 0x2094A3B8, 0x5594A3B8, 14));
        battery.setOnClickListener(v -> openData());
        row.addView(battery, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        return row;
    }

    private void confirmTestAlarm() {
        new AlertDialog.Builder(this)
                .setTitle("试听到站闹钟")
                .setMessage("将立即按系统闹钟音量响铃并震动，同时打开测试闹钟界面。测试不会改变当前守护状态。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始测试", (d, w) -> {
                    Intent i = new Intent(this, GuardService.class)
                            .setAction(GuardService.ACTION_TEST);
                    if (Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(i);
                    } else {
                        startService(i);
                    }
                })
                .show();
    }

    private View buildTipsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        card.setBackground(Ui.rounded(this, 0xFF0F1A2B, 16, 1, 0xFF1C2B42));

        TextView title = Ui.strong(this, "使用提示", 13, 0xFFCBD5E1);
        card.addView(title);

        addTip(card, "上车后选择下车站点，点击底部“开始守护”，手机可以锁屏放进口袋。", 0);
        tipsRadiusLine = addTip(card, "", 8);
        tipsSpeedLine = addTip(card, "", 8);
        addTip(card, "请允许“精确位置”和通知权限；小米、华为等机型还需在系统设置中允许自启动。", 8);
        addTip(card, "守护时使用 GPS，过程不需要网络；地图选点需要联网加载底图。可在“叫醒设置”中随时调整距离和频率。", 8);
        addTip(card, "只有开启叫醒或记录行程时才会在后台运行；两者都未开启时，退出 App 后不会常驻后台。", 8);
        return card;
    }

    private TextView addTip(LinearLayout card, String text, int topMarginDp) {
        TextView line = Ui.text(this, "·  " + text, 11, Ui.MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, topMarginDp);
        card.addView(line, lp);
        return line;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 14));
        bar.setBackgroundColor(0xFF0A111D);

        cta = Ui.strong(this, "选择下车站点", 16, 0xFF052331);
        cta.setGravity(Gravity.CENTER);
        cta.setClickable(true);
        cta.setFocusable(true);
        cta.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 16));
        cta.setOnClickListener(v -> onCtaClicked());
        bar.addView(cta, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        ctaHint = Ui.text(this, "先在地图上选中你的下车站", 10.5f, 0xFF64748B);
        ctaHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(this, 8);
        bar.addView(ctaHint, hintLp);
        return bar;
    }

    private LinearLayout.LayoutParams marginsTop(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private void refreshAll() {
        stops.clear();
        stops.addAll(StopStore.loadStops(this));
        String selected = StopStore.selectedId(this);
        if (selected == null || findStop(selected) == null) {
            selected = stops.isEmpty() ? null : stops.get(0).id;
            if (selected != null) {
                StopStore.select(this, selected);
            }
        }
        stopCount.setText(stops.size() + " 个");
        refreshSettingsSummary();
        renderStops(selected);
        refreshStatus();
        refreshWarnings();
    }

    private void refreshSettingsSummary() {
        int radius = StopStore.getRadiusM(this);
        int intervalSeconds = Math.max(1, StopStore.getIntervalMs(this) / 1000);
        String radiusLabel = StopStore.formatRadius(radius);
        String shortLabel = StopStore.formatRadiusShort(radius);
        double speed = StopStore.getManualSpeed(this);
        int leadSeconds = StopStore.getLeadTimeSeconds(this);
        int recommended = StopStore.recommendDistanceM(speed, leadSeconds);

        headerBadge.setText(shortLabel);
        headerBadge.setContentDescription("叫醒设置，当前唤醒距离 " + radiusLabel);
        headerSubtitle.setText("BUS WAKE  ·  " + shortLabel + " AHEAD");
        if (tipsRadiusLine != null) {
            tipsRadiusLine.setText("·  进入站点 " + radiusLabel
                    + " 范围后，闹钟持续响铃并震动；当前每 " + intervalSeconds + " 秒定位一次。");
        }
        if (tipsSpeedLine != null) {
            tipsSpeedLine.setText("·  速度助手：" + StopStore.formatSpeed(speed) + " × 提前 "
                    + StopStore.formatLeadDuration(leadSeconds) + "，推荐唤醒距离 "
                    + StopStore.formatRadius(recommended) + "。");
        }
    }

    private void renderStops(String selectedId) {
        stopsContainer.removeAllViews();
        if (stops.isEmpty()) {
            stopsContainer.addView(buildEmptyCard());
            return;
        }
        boolean running = prefs.getBoolean(StopStore.KEY_GUARD_RUNNING, false);
        String activeId = prefs.getString(StopStore.KEY_ACTIVE_ID, null);
        String radiusLabel = StopStore.formatRadius(StopStore.getRadiusM(this));

        for (Stop stop : stops) {
            boolean selected = stop.id.equals(selectedId);
            boolean active = running && stop.id.equals(activeId);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 12));
            card.setBackground(Ui.rounded(this,
                    selected ? 0xFF12263A : Ui.SURFACE,
                    18, selected ? 1 : 1,
                    selected ? 0xAA38BDF8 : Ui.BORDER));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> selectStop(stop));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView check = Ui.strong(this, selected ? "✓" : "", 13, Ui.ACCENT_DARK);
            check.setGravity(Gravity.CENTER);
            check.setBackground(selected
                    ? Ui.circle(this, Ui.ACCENT)
                    : Ui.circle(this, 0x00000000, 1, 0xFF3A4A63));
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 24), Ui.dp(this, 24));
            checkLp.rightMargin = Ui.dp(this, 10);
            top.addView(check, checkLp);

            TextView name = Ui.strong(this, stop.name, 16, Ui.TEXT);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(name, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (active) {
                TextView chip = Ui.chip(this, "守护中", Ui.SUCCESS, 0x1F34D399);
                top.addView(chip);
            } else if (selected) {
                TextView chip = Ui.chip(this, "已选择", Ui.ACCENT, 0x1A38BDF8);
                top.addView(chip);
            }
            card.addView(top);

            TextView coords = Ui.text(this, String.format(Locale.CHINA, "%.5f, %.5f", stop.lat, stop.lng),
                    10.5f, 0xFF64748B);
            LinearLayout.LayoutParams coordsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            coordsLp.leftMargin = Ui.dp(this, 34);
            coordsLp.topMargin = Ui.dp(this, 6);
            card.addView(coords, coordsLp);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            actionsLp.leftMargin = Ui.dp(this, 34);
            actionsLp.topMargin = Ui.dp(this, 9);

            TextView radius = Ui.chip(this, radiusLabel + "内叫醒", Ui.ACCENT, 0x1438BDF8);
            actions.addView(radius);

            View spacer = new View(this);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

            TextView edit = Ui.strong(this, "编辑", 11.5f, 0xFF93C5FD);
            edit.setGravity(Gravity.CENTER);
            edit.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
            edit.setOnClickListener(v -> editStop(stop));
            actions.addView(edit);

            TextView delete = Ui.strong(this, "删除", 11.5f, Ui.DANGER);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
            delete.setOnClickListener(v -> confirmDelete(stop));
            actions.addView(delete);

            card.addView(actions, actionsLp);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = Ui.dp(this, 10);
            stopsContainer.addView(card, cardLp);
        }
    }

    private View buildEmptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 24));
        card.setBackground(Ui.rounded(this, 0xFF0F1A2B, 18, 1, 0xFF22324D));

        TextView icon = Ui.strong(this, "＋", 30, Ui.ACCENT);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.circle(this, 0x1A38BDF8, 1, 0x5538BDF8));
        card.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));

        TextView title = Ui.strong(this, "还没有下车站点", 15, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(this, 12);
        card.addView(title, titleLp);

        TextView desc = Ui.text(this, "在地图上点选你容易坐过站的位置，例如公司附近的公交站。", 11.5f, Ui.MUTED);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 7);
        card.addView(desc, descLp);

        TextView add = Ui.strong(this, "在地图上选点", 12, Ui.ACCENT_DARK);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 11));
        add.setOnClickListener(v -> openMapPicker());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        addLp.topMargin = Ui.dp(this, 16);
        card.addView(add, addLp);
        return card;
    }

    private void refreshStatus() {
        boolean running = prefs.getBoolean(StopStore.KEY_GUARD_RUNNING, false);
        boolean alarmActive = prefs.getBoolean(StopStore.KEY_ALARM_ACTIVE, false);
        String activeId = prefs.getString(StopStore.KEY_ACTIVE_ID, null);
        Stop active = findStop(activeId);

        if (alarmActive) {
            setDot(Ui.AMBER);
            statusTitle.setText("到站提醒");
            statusTag.setText("响铃中");
            statusTag.setTextColor(0xFF2A1E05);
            statusTag.setBackground(Ui.rounded(this, Ui.AMBER, 20));
            statusDistance.setText("该下车了！");
            statusDetail.setText("闹钟正在响铃和震动，请在闹钟界面按下“我醒了”停止。");
            statusProgress.setVisibility(View.GONE);
            ctaMode = 3;
            cta.setText("停止闹钟");
            cta.setTextColor(0xFF2A1E05);
            cta.setBackground(Ui.button(this, Ui.AMBER, 0xFFFCD34D, 16));
            ctaHint.setText("到站闹钟正在响铃 · 点击停止");
            return;
        }

        if (running && active != null) {
            setDot(Ui.SUCCESS);
            statusTitle.setText("守护中 · " + active.name);
            statusTag.setText("运行中");
            statusTag.setTextColor(0xFF062E25);
            statusTag.setBackground(Ui.rounded(this, Ui.SUCCESS, 20));

            double distance = prefs.getFloat(StopStore.KEY_LAST_DISTANCE, -1);
            long lastUpdate = prefs.getLong(StopStore.KEY_LAST_UPDATE, 0);
            long age = lastUpdate > 0 ? Math.max(0, System.currentTimeMillis() - lastUpdate) : -1;
            int radius = StopStore.getRadiusM(this);
            if (distance >= 0) {
                statusDistance.setText("距 " + active.name + " " + StopStore.formatDistance(distance));
                statusProgress.setMax(radius);
                int progress = (int) Math.max(0, Math.min(radius, radius - distance));
                statusProgress.setProgress(progress);
            } else {
                statusDistance.setText("正在获取卫星定位…");
                statusProgress.setMax(radius);
                statusProgress.setProgress(0);
            }
            statusProgress.setVisibility(View.VISIBLE);

            double speed = prefs.getFloat(StopStore.KEY_LAST_SPEED, -1);
            double accuracy = prefs.getFloat(StopStore.KEY_LAST_ACCURACY, -1);
            int intervalSeconds = Math.max(1, StopStore.getIntervalMs(this) / 1000);
            StringBuilder detail = new StringBuilder();
            if (speed >= 0) {
                detail.append(String.format(Locale.CHINA, "时速约 %.0f km/h", speed));
            } else {
                detail.append("等待车速");
            }
            if (accuracy > 0) {
                detail.append(String.format(Locale.CHINA, "  ·  精度 ±%.0f 米", accuracy));
            }
            detail.append("  ·  每 ").append(intervalSeconds).append(" 秒定位");
            if (age >= 0) {
                detail.append("  ·  ").append(ageText(age));
            }
            if (age > 45000) {
                detail.append("  ·  请检查定位");
            }
            statusDetail.setText(detail.toString());
            updateCta(true, active);
            return;
        }

        setDot(0xFF64748B);
        statusTitle.setText(stops.isEmpty() ? "等待添加站点" : "守护未开启");
        statusTag.setText("待命");
        statusTag.setTextColor(Ui.MUTED);
        statusTag.setBackground(Ui.rounded(this, 0x1A94A3B8, 20));
        if (stops.isEmpty()) {
            statusDistance.setText("先选一个下车站");
            statusDetail.setText("点击下方按钮，在地图上手动点选你容易坐过站的位置。");
        } else {
            statusDistance.setText("准备出发？");
            Stop selected = findStop(StopStore.selectedId(this));
            String radiusLabel = StopStore.formatRadius(StopStore.getRadiusM(this));
            if (selected != null) {
                statusDetail.setText("已选“" + selected.name + "”，点击开始守护，到站前 " + radiusLabel + " 会叫醒你。");
            } else {
                statusDetail.setText("选择下车站点并点击开始守护。");
            }
        }
        statusProgress.setVisibility(View.GONE);
        updateCta(false, null);
    }

    private String ageText(long ageMs) {
        if (ageMs < 5000) {
            return "刚刚更新";
        }
        if (ageMs < 60000) {
            return (ageMs / 1000) + " 秒前更新";
        }
        return (ageMs / 60000) + " 分钟前更新";
    }

    private void setDot(int color) {
        if (statusDot != null) {
            statusDot.setBackground(Ui.circle(this, color));
        }
    }

    private void updateCta(boolean running, Stop active) {
        int radius = StopStore.getRadiusM(this);
        String radiusLabel = StopStore.formatRadius(radius);
        int intervalSeconds = Math.max(1, StopStore.getIntervalMs(this) / 1000);
        int mode = running ? 1 : (stops.isEmpty() ? 2 : 0);
        boolean modeChanged = mode != ctaMode;
        ctaMode = mode;

        if (running) {
            cta.setText("停止守护");
            if (modeChanged) {
                cta.setTextColor(Ui.DANGER);
                cta.setBackground(Ui.outlineButton(this, 0x14F87171, 0x2AF87171, 0xAAF87171, 16));
            }
            ctaHint.setText(active != null
                    ? "正在守护 “" + active.name + "” · 前 " + radiusLabel + " 响铃 · 每 " + intervalSeconds + " 秒定位"
                    : "正在守护 · 前 " + radiusLabel + " 响铃");
        } else if (stops.isEmpty()) {
            cta.setText("选择下车站点");
            if (modeChanged) {
                cta.setTextColor(0xFF052331);
                cta.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 16));
            }
            ctaHint.setText("先在地图上选中你的下车站");
        } else {
            cta.setText("开始守护 · " + radiusLabel + "叫醒");
            if (modeChanged) {
                cta.setTextColor(0xFF052331);
                cta.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 16));
            }
            ctaHint.setText("到站前 " + radiusLabel + " 响铃 · 每 " + intervalSeconds + " 秒定位");
        }
    }

    private void refreshWarnings() {
        warningContainer.removeAllViews();
        boolean any = false;

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean notifications = nm == null || nm.areNotificationsEnabled();
        if (!notifications) {
            warningContainer.addView(warningCard(
                    "通知权限未开启",
                    "锁屏时可能无法弹出全屏闹钟。请允许通知，并关闭该应用的通知静默。",
                    "去开启",
                    this::openNotificationSettings));
            any = true;
        } else if (alarmChannelBlocked()) {
            warningContainer.addView(warningCard(
                    "到站闹钟通知被关闭",
                    "“到站闹钟”通知渠道被关闭，锁屏时可能看不到停止按钮。请重新允许该渠道。",
                    "去开启",
                    this::openNotificationSettings));
            any = true;
        }

        if (Build.VERSION.SDK_INT >= 34 && nm != null && !nm.canUseFullScreenIntent()) {
            warningContainer.addView(warningCard(
                    "全屏闹钟未授权",
                    "Android 14 需要单独允许“全屏通知”，否则锁屏时可能只响铃不亮屏。",
                    "去授权",
                    this::openFullScreenSettings));
            any = true;
        }

        if (!hasFineLocation()) {
            warningContainer.addView(warningCard(
                    hasCoarseLocation() ? "只允许了大致位置" : "定位权限未开启",
                    hasCoarseLocation()
                            ? "到站距离判定需要精确位置。请在权限设置中改为“精确位置”。"
                            : "到站提醒需要定位权限，请选择“使用应用时允许”并开启精确位置。",
                    "去设置",
                    this::openAppDetails));
            any = true;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gps = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gps) {
            warningContainer.addView(warningCard(
                    "GPS 定位已关闭",
                    "请打开手机定位服务，否则无法判断你与站点的距离。",
                    "去开启",
                    this::openLocationSettings));
            any = true;
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        if (!ignoring) {
            warningContainer.addView(warningCard(
                    "省电限制可能中断守护",
                    "建议允许本应用忽略电池优化，并在系统管家中允许后台运行和自启动。",
                    "去设置",
                    this::openBatterySettings));
            any = true;
        }

        if (!any) {
            LinearLayout ready = new LinearLayout(this);
            ready.setOrientation(LinearLayout.HORIZONTAL);
            ready.setGravity(Gravity.CENTER_VERTICAL);
            ready.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
            ready.setBackground(Ui.rounded(this, 0x1410B981, 12, 1, 0x3334D399));
            TextView dot = Ui.strong(this, "✓", 12, Ui.SUCCESS);
            ready.addView(dot);
            TextView text = Ui.text(this, "权限就绪，定位与通知均可用", 10.5f, 0xFF86EFAC);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(this, 7);
            ready.addView(text, lp);
            warningContainer.addView(ready);
        }
    }

    private View warningCard(String title, String desc, String action, Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(this, 0xFF211B14, 14, 1, 0xFF5A421A));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.strong(this, title, 12.5f, Ui.AMBER);
        col.addView(t);
        TextView d = Ui.text(this, desc, 10.5f, 0xFFB6A98F);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = Ui.dp(this, 4);
        col.addView(d, dLp);
        card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView button = Ui.strong(this, action, 11.5f, 0xFF2A1E05);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8));
        button.setBackground(Ui.button(this, Ui.AMBER, 0xFFFCD34D, 9));
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.leftMargin = Ui.dp(this, 10);
        card.addView(button, bLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 9);
        card.setLayoutParams(lp);
        return card;
    }

    private void onCtaClicked() {
        if (prefs.getBoolean(StopStore.KEY_ALARM_ACTIVE, false)) {
            Intent intent = new Intent(this, GuardService.class)
                    .setAction(GuardService.ACTION_STOP_ALARM);
            startService(intent);
            prefs.edit().putBoolean(StopStore.KEY_ALARM_ACTIVE, false).apply();
            handler.postDelayed(this::refreshAll, 300);
            return;
        }
        boolean running = prefs.getBoolean(StopStore.KEY_GUARD_RUNNING, false);
        if (running) {
            confirmStopGuard();
        } else if (stops.isEmpty()) {
            openMapPicker();
        } else {
            beginStartFlow();
        }
    }

    private void beginStartFlow() {
        if (stops.isEmpty()) {
            openMapPicker();
            return;
        }
        String selected = StopStore.selectedId(this);
        if (selected == null || findStop(selected) == null) {
            StopStore.select(this, stops.get(0).id);
        }

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
                    .setMessage("当前只允许了大致位置，到站距离判定可能不准。建议在系统设置中将定位权限改为“精确位置”。")
                    .setNegativeButton("仍然开始", (d, w) -> checkNotificationAndStart())
                    .setPositiveButton("去设置", (d, w) -> openAppDetails())
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationsEnabled()) {
            pendingStart = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            return;
        }
        checkNotificationAndStart();
    }

    private void checkNotificationAndStart() {
        if (!notificationsEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("通知权限未开启")
                    .setMessage("锁屏后，到站闹钟需要通过通知弹出全屏提醒。没有通知权限时可能无法安全停止闹钟，因此请先允许通知再开始守护。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去开启", (d, w) -> openNotificationSettings())
                    .show();
            return;
        }
        if (alarmChannelBlocked()) {
            new AlertDialog.Builder(this)
                    .setTitle("到站闹钟通知被关闭")
                    .setMessage("请先在通知设置中重新允许“到站闹钟”渠道，否则锁屏时可能没有停止按钮。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去开启", (d, w) -> openNotificationSettings())
                    .show();
            return;
        }
        startGuardNow();
    }

    private boolean alarmChannelBlocked() {
        if (Build.VERSION.SDK_INT < 26) {
            return false;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return false;
        }
        android.app.NotificationChannel channel =
                nm.getNotificationChannel(GuardService.CHANNEL_ALARM);
        return channel != null
                && channel.getImportance() == NotificationManager.IMPORTANCE_NONE;
    }

    private void startGuardNow() {
        String id = StopStore.selectedId(this);
        Stop stop = findStop(id);
        if (stop == null) {
            openMapPicker();
            return;
        }
        Intent intent = new Intent(this, GuardService.class)
                .setAction(GuardService.ACTION_START)
                .putExtra(GuardService.EXTRA_STOP_ID, stop.id);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        AppLog.i("Guard", "Start requested for " + stop.name
                + ", radius=" + StopStore.getRadiusM(this)
                + "m, interval=" + StopStore.getIntervalMs(this) + "ms");
        Toast.makeText(this, "守护已开启 · 到站前 "
                + StopStore.formatRadius(StopStore.getRadiusM(this)) + " 叫醒", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshAll, 700);
    }

    private void confirmStopGuard() {
        new AlertDialog.Builder(this)
                .setTitle("停止到站守护？")
                .setMessage("停止后将不再判断距离，也不会响铃。")
                .setNegativeButton("继续守护", null)
                .setPositiveButton("停止", (d, w) -> stopGuardNow())
                .show();
    }

    private void stopGuardNow() {
        Intent intent = new Intent(this, GuardService.class).setAction(GuardService.ACTION_STOP);
        startService(intent);
        prefs.edit().putBoolean(StopStore.KEY_GUARD_RUNNING, false).apply();
        AppLog.i("Guard", "Stop requested by user");
        Toast.makeText(this, "已停止守护", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshAll, 400);
    }

    private void selectStop(Stop stop) {
        String old = StopStore.selectedId(this);
        StopStore.select(this, stop.id);
        boolean running = prefs.getBoolean(StopStore.KEY_GUARD_RUNNING, false);
        String activeId = prefs.getString(StopStore.KEY_ACTIVE_ID, null);
        if (running && activeId != null && !activeId.equals(stop.id)) {
            new AlertDialog.Builder(this)
                    .setTitle("切换守护站点？")
                    .setMessage("当前正在守护另一个站点。切换后将立即改为守护“" + stop.name + "”。")
                    .setNegativeButton("取消", (d, w) -> {
                        StopStore.select(this, old);
                        refreshAll();
                    })
                    .setPositiveButton("切换", (d, w) -> {
                        startGuardNow();
                        refreshAll();
                    })
                    .show();
            return;
        }
        refreshAll();
    }

    private void confirmDelete(Stop stop) {
        boolean running = prefs.getBoolean(StopStore.KEY_GUARD_RUNNING, false);
        String activeId = prefs.getString(StopStore.KEY_ACTIVE_ID, null);
        boolean active = running && stop.id.equals(activeId);
        String message = active
                ? "该站点正在守护，删除后会先停止当前守护。"
                : "删除后需要重新在地图上选点。";
        new AlertDialog.Builder(this)
                .setTitle("删除“" + stop.name + "”？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    if (active) {
                        stopGuardNow();
                    }
                    stops.remove(stop);
                    StopStore.saveStops(this, stops);
                    String selected = StopStore.selectedId(this);
                    if (selected == null || findStop(selected) == null) {
                        StopStore.select(this, stops.isEmpty() ? null : stops.get(0).id);
                    }
                    refreshAll();
                })
                .show();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openTrips() {
        startActivity(new Intent(this, TripListActivity.class));
    }

    private void openData() {
        startActivity(new Intent(this, DataActivity.class));
    }

    private void openMapPicker() {
        Intent i = new Intent(this, MapPickerActivity.class);
        i.putExtra("next_index", stops.size() + 1);
        if (hasFineLocation() || hasCoarseLocation()) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Location last = null;
                for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    Location l = lm.getLastKnownLocation(provider);
                    if (l != null && (last == null || l.getTime() > last.getTime())) {
                        last = l;
                    }
                }
                if (last != null) {
                    i.putExtra("lat", last.getLatitude());
                    i.putExtra("lng", last.getLongitude());
                    i.putExtra("zoom", 15);
                }
            } catch (Exception ignored) {
            }
        } else {
            double lat = Double.longBitsToDouble(prefs.getLong(StopStore.KEY_LAST_MAP_LAT, Double.doubleToLongBits(0)));
            double lng = Double.longBitsToDouble(prefs.getLong(StopStore.KEY_LAST_MAP_LNG, Double.doubleToLongBits(0)));
            if (lat != 0 && lng != 0) {
                i.putExtra("lat", lat);
                i.putExtra("lng", lng);
                i.putExtra("zoom", 14);
            }
        }
        startActivity(i);
    }

    private void editStop(Stop stop) {
        Intent i = new Intent(this, MapPickerActivity.class);
        i.putExtra("stop_id", stop.id);
        i.putExtra("name", stop.name);
        i.putExtra("lat", stop.lat);
        i.putExtra("lng", stop.lng);
        i.putExtra("zoom", 16);
        startActivity(i);
    }

    private Stop findStop(String id) {
        if (id == null) {
            return null;
        }
        for (Stop s : stops) {
            if (id.equals(s.id)) {
                return s;
            }
        }
        return null;
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
                    beginStartFlow();
                }
            } else {
                pendingStart = false;
                new AlertDialog.Builder(this)
                        .setTitle("无法开始守护")
                        .setMessage("到站叫醒需要定位权限。请到系统设置中允许“位置信息”，并选择精确位置。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去设置", (d, w) -> openAppDetails())
                        .show();
            }
            refreshAll();
        } else if (requestCode == REQ_NOTIFICATION) {
            if (pendingStart) {
                pendingStart = false;
                checkNotificationAndStart();
            }
            refreshWarnings();
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

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) {
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                openAppDetails();
            }
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
