package com.buswake.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public final class TripDetailActivity extends Activity {
    private Trip trip;
    private WebView webView;
    private SpeedChartView chartView;
    private boolean routePosted;
    private boolean mapReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra("trip_id");
        trip = TripStore.findById(this, id);
        if (trip == null) {
            finish();
            return;
        }
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        setContentView(buildScreen());
        configureMap();
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
        Ui.pad(content, Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildStatsCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(buildMapCard(), cardParams());

        content.addView(buildChartCard(), cardParams());

        TextView footer = Ui.text(this, trip.points.size() + " 个采样点 · 数据仅保存在本机",
                10, 0xFF64748B);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, marginsTop(18));
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

        titles.addView(Ui.strong(this, "行程详情", 17, Ui.TEXT));
        TextView sub = Ui.text(this, trip.title(), 10.5f, 0xFF64748B);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 3);
        titles.addView(sub, subLp);
        return bar;
    }

    private View buildStatsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 17, 1, Ui.BORDER));

        addStat(card, StopStore.formatDistance(trip.totalDistanceM()), "路程");
        addStat(card, Trip.formatDuration(trip.durationMs() / 1000), "用时");
        addStat(card, String.format(Locale.CHINA, "%.0f", trip.avgSpeedKmh()), "均速 km/h");
        addStat(card, String.format(Locale.CHINA, "%.0f", trip.maxSpeedKmh()), "最高 km/h");
        return card;
    }

    private void addStat(LinearLayout parent, String value, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView v = Ui.strong(this, value, 16, Ui.TEXT);
        v.setGravity(Gravity.CENTER);
        col.addView(v);
        TextView l = Ui.text(this, label, 9.5f, 0xFF7D8DA6);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 4);
        col.addView(l, lp);
        parent.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private View buildMapCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 18, 1, Ui.BORDER));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.strong(this, "路线叠加", 14, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView hint = Ui.text(this, "可切换底图", 10, 0xFF64748B);
        header.addView(hint);
        card.addView(header);

        webView = new WebView(this);
        webView.setBackgroundColor(Ui.BG);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 300));
        lp.topMargin = Ui.dp(this, 10);
        card.addView(webView, lp);
        return card;
    }

    private View buildChartCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 18, 1, Ui.BORDER));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.strong(this, "路程 - 速度曲线", 14, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView legend = Ui.text(this, "蓝线：速度 · 黄点：最高", 9.5f, 0xFF64748B);
        header.addView(legend);
        card.addView(header);

        TextView axes = Ui.text(this, "横轴：累计路程（公里）　纵轴：速度（km/h）", 9.5f, 0xFF7D8DA6);
        LinearLayout.LayoutParams axesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        axesLp.topMargin = Ui.dp(this, 5);
        card.addView(axes, axesLp);

        chartView = new SpeedChartView(this);
        chartView.setTrip(trip);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 235));
        chartLp.topMargin = Ui.dp(this, 8);
        card.addView(chartView, chartLp);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        return lp;
    }

    private LinearLayout.LayoutParams marginsTop(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private void configureMap() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.postDelayed(() -> {
                    if (!routePosted) {
                        mapReady = true;
                        postRoute();
                    }
                }, 600);
            }
        });
        webView.addJavascriptInterface(new MapBridge(), "AndroidTrip");
        webView.loadUrl("file:///android_asset/trip_map.html");
    }

    private final class MapBridge {
        @JavascriptInterface
        public void onReady() {
            runOnUiThread(() -> {
                mapReady = true;
                postRoute();
            });
        }
    }

    private void postRoute() {
        if (!mapReady || routePosted || webView == null || trip == null) {
            return;
        }
        routePosted = true;
        String json = trip.mapJson();
        webView.evaluateJavascript("window.setRoute(" + json + ");", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidTrip");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
