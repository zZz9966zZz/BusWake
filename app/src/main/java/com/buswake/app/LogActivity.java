package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class LogActivity extends Activity {
    private static final int REQ_EXPORT = 701;
    private TextView logText;
    private ScrollView scrollView;
    private long lastSize = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long size = AppLog.totalBytes(LogActivity.this);
            if (size != lastSize) {
                lastSize = size;
                refreshLog();
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        setContentView(buildScreen());
        refreshLog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
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

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF070D17);
        scrollView.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        logText = Ui.text(this, "正在读取日志…", 9.5f, 0xFFB6C2D4);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setLineSpacing(Ui.dp(this, 2), 1f);
        scrollView.addView(logText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        View divider = new View(this);
        divider.setBackgroundColor(0xFF172033);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        bottom.setBackgroundColor(0xFF0A111D);
        TextView export = Ui.strong(this, "导出运行日志", 15, 0xFF052331);
        export.setGravity(Gravity.CENTER);
        export.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 14));
        export.setOnClickListener(v -> exportLog());
        bottom.addView(export, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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
        titles.addView(Ui.strong(this, "运行日志", 17, Ui.TEXT));
        TextView sub = Ui.text(this, "记录叫醒、行程与导出等关键事件", 10.5f, 0xFF64748B);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 3);
        titles.addView(sub, subLp);

        TextView clear = Ui.strong(this, "清空", 11.5f, 0xFFF87171);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8));
        clear.setBackground(Ui.outlineButton(this, 0x14F87171, 0x2AF87171, 0x88F87171, 10));
        clear.setOnClickListener(v -> confirmClear());
        bar.addView(clear);
        return bar;
    }

    private void refreshLog() {
        String text = AppLog.readAll(this);
        if (text.trim().isEmpty()) {
            text = "暂无运行日志。";
        }
        logText.setText(text);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        lastSize = AppLog.totalBytes(this);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空运行日志？")
                .setMessage("清空后无法恢复，但不影响站点和行程数据。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    AppLog.clear(this);
                    AppLog.i("Log", "User cleared runtime logs");
                    refreshLog();
                    Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void exportLog() {
        String name = "BusWake-log-" + ExportManager.timestamp() + ".txt";
        try {
            startActivityForResult(ExportManager.createDocument("text/plain", name), REQ_EXPORT);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件保存程序", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        String text = AppLog.readAllForExport(this);
        boolean ok = ExportManager.writeText(this, uri, text);
        if (ok) {
            AppLog.i("Export", "Runtime log exported");
            Toast.makeText(this, "运行日志已导出", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "导出失败，请换一个保存位置", Toast.LENGTH_LONG).show();
        }
    }
}
