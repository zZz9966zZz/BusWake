package com.buswake.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class DataActivity extends Activity {
    private static final int REQ_EXPORT_LOG = 801;
    private static final int REQ_EXPORT_BACKUP = 802;

    private TextView logSummary;
    private TextView dataSummary;
    private TextView backupButton;
    private boolean exporting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        setContentView(buildScreen());
        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
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
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(content, Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 26));
        scroll.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildLogCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 14);
        content.addView(buildBackupCard(), cardLp);
        content.addView(buildBatteryCard(), cardLp);

        TextView footer = Ui.text(this, "所有数据仅保存在本机；导出文件由你选择保存位置。", 10, 0xFF64748B);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(this, 20);
        content.addView(footer, footerLp);
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
        titles.addView(Ui.strong(this, "数据与日志", 17, Ui.TEXT));
        TextView sub = Ui.text(this, "查看日志 · 备份设置与路线", 10.5f, 0xFF64748B);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 3);
        titles.addView(sub, subLp);
        return bar;
    }

    private View buildLogCard() {
        LinearLayout card = card();
        card.addView(Ui.strong(this, "运行日志", 15, Ui.TEXT));
        logSummary = Ui.text(this, "正在统计…", 11, Ui.MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        card.addView(logSummary, lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView view = outlineButton("查看日志", Ui.ACCENT, 0x0F38BDF8, 0x6638BDF8);
        view.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        row.addView(view, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView export = outlineButton("导出日志", 0xFFCBD5E1, 0x0A94A3B8, 0x5594A3B8);
        export.setOnClickListener(v -> exportLog());
        row.addView(export, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(this, 10);
        card.addView(row, rowLp);
        return card;
    }

    private View buildBackupCard() {
        LinearLayout card = card();
        card.addView(Ui.strong(this, "数据备份", 15, Ui.TEXT));
        dataSummary = Ui.text(this, "正在统计…", 11, Ui.MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        card.addView(dataSummary, lp);

        TextView desc = Ui.text(this,
                "导出一个 JSON 文件，包含 App 设置、全部站点和全部历史行程（含路线与逐点速度）。",
                10.5f, 0xFF7D8DA6);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 5);
        card.addView(desc, descLp);

        backupButton = Ui.strong(this, "导出设置、站点和行程", 13, 0xFF052331);
        backupButton.setGravity(Gravity.CENTER);
        backupButton.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 12));
        backupButton.setOnClickListener(v -> exportBackup());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        btnLp.topMargin = Ui.dp(this, 12);
        card.addView(backupButton, btnLp);
        return card;
    }

    private View buildBatteryCard() {
        LinearLayout card = card();
        card.addView(Ui.strong(this, "后台与省电", 15, Ui.TEXT));
        TextView desc = Ui.text(this,
                "仅在叫醒守护或行程记录时保留后台服务。若系统清理后台，可在这里检查电池优化设置。",
                10.5f, 0xFF7D8DA6);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 6);
        card.addView(desc, descLp);

        TextView battery = outlineButton("省电设置", 0xFFCBD5E1, 0x0A94A3B8, 0x5594A3B8);
        battery.setOnClickListener(v -> openBatterySettings());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        btnLp.topMargin = Ui.dp(this, 10);
        card.addView(battery, btnLp);
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 17, 1, Ui.BORDER));
        return card;
    }

    private TextView outlineButton(String text, int color, int fill, int stroke) {
        TextView view = Ui.strong(this, text, 12, color);
        view.setGravity(Gravity.CENTER);
        view.setBackground(Ui.outlineButton(this, fill, fill | 0x20000000, stroke, 11));
        return view;
    }

    private void refreshSummary() {
        long bytes = AppLog.totalBytes(this);
        String text = AppLog.readAll(this);
        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        logSummary.setText("约 " + (bytes / 1024) + " KB · " + lines + " 行；包含叫醒、行程和导出事件。");

        List<Stop> stops = StopStore.loadStops(this);
        List<Trip> trips = TripStore.loadAll(this);
        dataSummary.setText("站点 " + stops.size() + " 个 · 历史行程 " + trips.size()
                + " 条 · 设置与路线均在本机。");
    }

    private void exportLog() {
        try {
            startActivityForResult(ExportManager.createDocument(
                    "text/plain", "BusWake-log-" + ExportManager.timestamp() + ".txt"), REQ_EXPORT_LOG);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件保存程序", Toast.LENGTH_LONG).show();
        }
    }

    private void exportBackup() {
        if (exporting) {
            return;
        }
        try {
            startActivityForResult(ExportManager.createDocument(
                    "application/json", "BusWake-backup-" + ExportManager.timestamp() + ".json"),
                    REQ_EXPORT_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件保存程序", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_EXPORT_LOG) {
            boolean ok = ExportManager.writeText(this, uri, AppLog.readAllForExport(this));
            if (ok) {
                AppLog.i("Export", "Runtime log exported from data screen");
                Toast.makeText(this, "运行日志已导出", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "导出失败，请换一个保存位置", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_EXPORT_BACKUP) {
            exportBackupTo(uri);
        }
    }

    private void exportBackupTo(Uri uri) {
        if (exporting) {
            return;
        }
        exporting = true;
        backupButton.setEnabled(false);
        backupButton.setAlpha(0.55f);
        backupButton.setText("正在生成备份…");
        new Thread(() -> {
            String json = ExportManager.buildBackupJson(this);
            boolean ok = ExportManager.writeText(this, uri, json);
            runOnUiThread(() -> {
                exporting = false;
                backupButton.setEnabled(true);
                backupButton.setAlpha(1f);
                backupButton.setText("导出设置、站点和行程");
                if (ok) {
                    AppLog.i("Export", "Full backup exported");
                    Toast.makeText(this,
                            "备份已导出：设置、站点和全部行程", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            "导出失败，请换一个保存位置", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }
}
