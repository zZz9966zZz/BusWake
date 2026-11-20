package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpeedReview {
    private SpeedReview() {
    }

    public static void reviewAbove(Activity activity, Trip trip, double thresholdKmh, Runnable onDone) {
        if (activity == null || trip == null || activity.isFinishing()) {
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < trip.points.size(); i++) {
            if (trip.points.get(i).speedKmh > thresholdKmh) {
                indices.add(i);
            }
        }
        if (indices.isEmpty()) {
            Toast.makeText(activity, "没有符合条件的点", Toast.LENGTH_SHORT).show();
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        reviewAt(activity, trip, thresholdKmh, indices, 0, onDone);
    }

    private static void reviewAt(Activity activity, Trip trip, double thresholdKmh,
                                 List<Integer> indices, int position, Runnable onDone) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (position >= indices.size()) {
            save(activity, trip, onDone);
            return;
        }
        int index = indices.get(position);
        Trip.Point point = trip.points.get(index);
        String message = String.format(Locale.CHINA,
                "时间 %s · 速度 %s · 筛选条件：超过 %.1f km/h\n\n"
                        + "保留原始数据，或用前后点平均值替换，或手动输入一个数值。",
                Trip.formatDuration(point.elapsedMs / 1000),
                Trip.formatSpeed1(point.speedKmh), thresholdKmh);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 18);
        box.setPadding(pad, pad, pad, pad);
        TextView info = Ui.text(activity, message, 12, Ui.TEXT);
        box.addView(info);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("处理超速点 " + (position + 1) + "/" + indices.size())
                .setView(box)
                .create();
        dialog.setOnCancelListener(d -> save(activity, trip, onDone));

        addOption(activity, box, dialog, "保留数据", Ui.TEXT,
                0x1A94A3B8, 0x5594A3B8, () -> {
                    dialog.dismiss();
                    post(activity, () -> reviewAt(activity, trip, thresholdKmh, indices, position + 1, onDone));
                });
        addOption(activity, box, dialog, "取前后平均", Ui.ACCENT,
                0x0D38BDF8, 0x5538BDF8, () -> {
                    point.speedKmh = Math.max(0, trip.averageSpeedAround(index));
                    dialog.dismiss();
                    post(activity, () -> reviewAt(activity, trip, thresholdKmh, indices, position + 1, onDone));
                });
        addOption(activity, box, dialog, "手动输入", Ui.AMBER,
                0x14F59E0B, 0x66F59E0B, () -> {
                    dialog.dismiss();
                    post(activity, () -> askManual(activity, trip, thresholdKmh, indices, position, onDone));
                });
        addOption(activity, box, dialog, "取消剩余", Ui.DANGER,
                0x14F87171, 0x55F87171, () -> {
                    dialog.dismiss();
                    post(activity, () -> save(activity, trip, onDone));
                });
        dialog.show();
    }

    private static void post(Activity activity, Runnable action) {
        activity.getWindow().getDecorView().postDelayed(action, 120);
    }

    private static void addOption(Activity activity, LinearLayout box, AlertDialog dialog,
                                  String label, int textColor, int fill, int stroke,
                                  Runnable action) {
        TextView button = Ui.strong(activity, label, 13, textColor);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 11),
                Ui.dp(activity, 10), Ui.dp(activity, 11));
        button.setBackground(Ui.rounded(activity, fill, 10, 1, stroke));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 8);
        box.addView(button, lp);
    }

    private static void askManual(Activity activity, Trip trip, double thresholdKmh,
                                  List<Integer> indices, int position, Runnable onDone) {
        int index = indices.get(position);
        Trip.Point point = trip.points.get(index);
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.CHINA, "%.1f", point.speedKmh));
        input.setSelection(input.getText().length());
        int pad = Ui.dp(activity, 18);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(activity)
                .setTitle("手动输入速度（km/h）")
                .setView(input)
                .setNegativeButton("取消", (dialog, which) ->
                        reviewAt(activity, trip, thresholdKmh, indices, position, onDone))
                .setPositiveButton("确定", (dialog, which) -> {
                    double value;
                    try {
                        value = Double.parseDouble(input.getText().toString().trim());
                    } catch (Exception e) {
                        Toast.makeText(activity, "请输入数字", Toast.LENGTH_SHORT).show();
                        reviewAt(activity, trip, thresholdKmh, indices, position, onDone);
                        return;
                    }
                    if (Double.isNaN(value) || value < 0 || value > 200) {
                        Toast.makeText(activity, "速度需在 0 – 200 km/h 之间", Toast.LENGTH_SHORT).show();
                        reviewAt(activity, trip, thresholdKmh, indices, position, onDone);
                        return;
                    }
                    point.speedKmh = Trip.round1(value);
                    reviewAt(activity, trip, thresholdKmh, indices, position + 1, onDone);
                })
                .setOnCancelListener(dialog ->
                        reviewAt(activity, trip, thresholdKmh, indices, position, onDone))
                .show();
    }

    private static void save(Activity activity, Trip trip, Runnable onDone) {
        boolean saved = TripStore.update(activity, trip);
        if (!saved) {
            Toast.makeText(activity, "速度修改未能保存", Toast.LENGTH_LONG).show();
        }
        if (onDone != null) {
            onDone.run();
        }
    }
}
