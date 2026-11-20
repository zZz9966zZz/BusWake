package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
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
        new AlertDialog.Builder(activity)
                .setTitle("处理超速点 " + (position + 1) + "/" + indices.size())
                .setMessage(message)
                .setItems(new String[]{"保留数据", "取前后平均", "手动输入", "取消剩余"},
                        (dialog, which) -> {
                            if (which == 0) {
                                reviewAt(activity, trip, thresholdKmh, indices, position + 1, onDone);
                            } else if (which == 1) {
                                point.speedKmh = Math.max(0, trip.averageSpeedAround(index));
                                reviewAt(activity, trip, thresholdKmh, indices, position + 1, onDone);
                            } else if (which == 2) {
                                askManual(activity, trip, thresholdKmh, indices, position, onDone);
                            } else {
                                save(activity, trip, onDone);
                            }
                        })
                .setOnCancelListener(dialog -> save(activity, trip, onDone))
                .show();
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
