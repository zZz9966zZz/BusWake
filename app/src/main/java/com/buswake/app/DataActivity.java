package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.List;

public final class DataActivity extends Activity {
    private static final int REQ_EXPORT_LOG = 801;
    private static final int REQ_EXPORT_BACKUP = 802;
    private static final int REQ_IMPORT_BACKUP = 803;

    private TextView logSummary;
    private TextView dataSummary;
    private TextView backupButton;
    private TextView restoreButton;
    private boolean exporting;
    private boolean restoring;
    private boolean pendingIncludeSettings;
    private final ArrayList<String> pendingTripIds = new ArrayList<>();
    private boolean pendingExport;

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
                "设置与站点会生成一个文件，每条行程单独生成一个文件。只选一项时导出该 JSON；选多项时打包成 ZIP。恢复时可导入单个文件或整个 ZIP。",
                10.5f, 0xFF7D8DA6);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 5);
        card.addView(desc, descLp);

        backupButton = Ui.strong(this, "选择备份内容", 13, 0xFF052331);
        backupButton.setGravity(Gravity.CENTER);
        backupButton.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 12));
        backupButton.setOnClickListener(v -> showBackupChooser());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        btnLp.topMargin = Ui.dp(this, 12);
        card.addView(backupButton, btnLp);

        restoreButton = outlineButton("导入备份（JSON / ZIP）", 0xFFCBD5E1, 0x0A94A3B8, 0x5594A3B8);
        restoreButton.setOnClickListener(v -> restoreBackup());
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        restoreLp.topMargin = Ui.dp(this, 8);
        card.addView(restoreButton, restoreLp);
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

    private void showBackupChooser() {
        if (exporting) {
            Toast.makeText(this, "正在导出，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Trip> trips = TripStore.loadAll(this);
        final String[] labels = new String[trips.size() + 1];
        final boolean[] checked = new boolean[trips.size() + 1];
        labels[0] = "设置与站点（1 个文件）";
        checked[0] = true;
        for (int i = 0; i < trips.size(); i++) {
            Trip t = trips.get(i);
            labels[i + 1] = "行程 · " + t.title() + " · "
                    + StopStore.formatDistance(t.totalDistanceM());
            checked[i + 1] = true;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要备份的内容")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) ->
                        checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("下一步", (dialog, which) -> {
                    boolean includeSettings = checked[0];
                    ArrayList<String> ids = new ArrayList<>();
                    for (int i = 0; i < trips.size(); i++) {
                        if (checked[i + 1]) {
                            ids.add(trips.get(i).id);
                        }
                    }
                    if (!includeSettings && ids.isEmpty()) {
                        Toast.makeText(this, "请至少选择一项", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    beginBackupExport(includeSettings, ids);
                })
                .show();
    }

    private void beginBackupExport(boolean includeSettings, ArrayList<String> tripIds) {
        pendingIncludeSettings = includeSettings;
        pendingTripIds.clear();
        pendingTripIds.addAll(tripIds);
        pendingExport = true;
        int count = (includeSettings ? 1 : 0) + tripIds.size();
        String mime = count == 1 ? "application/json" : "application/zip";
        String name;
        if (count == 1 && includeSettings) {
            name = "BusWake-settings-stops-" + ExportManager.timestampSeconds() + ".json";
        } else if (count == 1) {
            name = "BusWake-trip-" + ExportManager.timestampSeconds() + ".json";
        } else {
            name = "BusWake-backup-" + ExportManager.timestampSeconds() + ".zip";
        }
        try {
            startActivityForResult(ExportManager.createDocument(mime, name), REQ_EXPORT_BACKUP);
        } catch (Exception e) {
            pendingExport = false;
            Toast.makeText(this, "没有可用的文件保存程序", Toast.LENGTH_LONG).show();
        }
    }

    private void restoreBackup() {
        if (restoring) {
            return;
        }
        if (TripStore.isRecording(this) || StopStore.isGuardRunning(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("请先停止当前任务")
                    .setMessage("导入备份会修改站点、设置或历史行程。请先结束行程记录并停止叫醒守护。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        try {
            startActivityForResult(ExportManager.openBackupDocument(), REQ_IMPORT_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件选择程序", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQ_EXPORT_BACKUP) {
                pendingExport = false;
                pendingTripIds.clear();
            }
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            if (requestCode == REQ_EXPORT_BACKUP) {
                pendingExport = false;
                pendingTripIds.clear();
            }
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
        } else if (requestCode == REQ_IMPORT_BACKUP) {
            readBackup(uri);
        }
    }

    private void readBackup(Uri uri) {
        if (restoring) {
            return;
        }
        restoring = true;
        restoreButton.setEnabled(false);
        restoreButton.setAlpha(0.55f);
        restoreButton.setText("正在读取备份…");
        new Thread(() -> {
            try {
                BackupManager.BackupData data = BackupManager.readBackup(this, uri);
                runOnUiThread(() -> {
                    restoring = false;
                    restoreButton.setEnabled(true);
                    restoreButton.setAlpha(1f);
                    restoreButton.setText("导入备份（JSON / ZIP）");
                    confirmRestore(data);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    restoring = false;
                    restoreButton.setEnabled(true);
                    restoreButton.setAlpha(1f);
                    restoreButton.setText("导入备份（JSON / ZIP）");
                    Toast.makeText(this, "备份读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String buildRestoreMessage(BackupManager.BackupData data) {
        StringBuilder message = new StringBuilder();
        if (data.legacyFull) {
            message.append("旧版完整备份：将覆盖当前设置、站点和全部历史行程。\n\n");
        } else {
            if (data.hasSettingsStops) {
                message.append("设置与站点：覆盖当前设置和站点，历史行程不变。\n");
            }
            if (data.hasTrips) {
                message.append("行程：").append(data.trips.size())
                        .append(" 条，按 ID 合并，不删除其他已有行程。\n");
            }
            message.append("\n");
        }
        message.append("备份内容：站点 ").append(data.stops.size())
                .append(" 个，行程 ").append(data.trips.size()).append(" 条。\n")
                .append("自定义闹钟音乐不包含在备份中，导入后保持当前音乐设置。");
        return message.toString();
    }

    private void confirmRestore(BackupManager.BackupData data) {
        new AlertDialog.Builder(this)
                .setTitle("导入这份备份？")
                .setMessage(buildRestoreMessage(data))
                .setNegativeButton("取消", null)
                .setPositiveButton("确认导入", (d, w) -> applyBackup(data))
                .show();
    }

    private void applyBackup(BackupManager.BackupData data) {
        restoring = true;
        restoreButton.setEnabled(false);
        restoreButton.setText("正在导入…");
        new Thread(() -> {
            boolean ok = BackupManager.applySelection(this, data);
            runOnUiThread(() -> {
                restoring = false;
                restoreButton.setEnabled(true);
                restoreButton.setAlpha(1f);
                restoreButton.setText("导入备份（JSON / ZIP）");
                if (ok) {
                    AppLog.i("Restore", "Selected backup imported");
                    refreshSummary();
                    Toast.makeText(this, "备份已导入", Toast.LENGTH_LONG).show();
                } else {
                    AppLog.e("Restore", "Import failed");
                    Toast.makeText(this, "导入失败，请检查文件后重试", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void exportBackupTo(Uri uri) {
        if (exporting || !pendingExport) {
            return;
        }
        final boolean includeSettings = pendingIncludeSettings;
        final ArrayList<String> tripIds = new ArrayList<>(pendingTripIds);
        pendingExport = false;
        exporting = true;
        backupButton.setEnabled(false);
        backupButton.setAlpha(0.55f);
        backupButton.setText("正在生成备份…");
        new Thread(() -> {
            boolean ok = false;
            int count = 0;
            try {
                ArrayList<Trip> trips = new ArrayList<>();
                for (String id : tripIds) {
                    Trip t = TripStore.findById(this, id);
                    if (t != null) {
                        trips.add(t);
                    }
                }
                count = (includeSettings ? 1 : 0) + trips.size();
                if (count == 0) {
                    ok = false;
                } else if (count == 1 && includeSettings) {
                    String json = ExportManager.buildSettingsStopsJson(this);
                    ok = ExportManager.writeBytes(this, uri,
                            json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else if (count == 1) {
                    String json = ExportManager.buildTripJson(this, trips.get(0));
                    ok = ExportManager.writeBytes(this, uri,
                            json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    byte[] zip = ExportManager.buildBackupZip(this, includeSettings, trips);
                    ok = ExportManager.writeBytes(this, uri, zip);
                }
            } catch (Exception e) {
                AppLog.e("Export", "backup export failed", e);
            }
            final int finalCount = count;
            final boolean finalOk = ok;
            runOnUiThread(() -> {
                exporting = false;
                pendingTripIds.clear();
                backupButton.setEnabled(true);
                backupButton.setAlpha(1f);
                backupButton.setText("选择备份内容");
                if (finalOk) {
                    AppLog.i("Export", "Backup exported: files=" + finalCount);
                    Toast.makeText(this,
                            finalCount == 1 ? "备份已导出（1 个文件）"
                                    : "备份已打包导出（" + finalCount + " 个文件）",
                            Toast.LENGTH_LONG).show();
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
