package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Locale;

/**
 * Sequential review of points that exceeded the configured speed limit.
 * The user can keep the recorded value, replace it with the average of the
 * neighbouring points, or type an exact value. "稍后处理" leaves the trip
 * pending so it can be reviewed again from the history screen.
 */
public final class SpeedReview {
    private SpeedReview() {
    }

    public static void review(Activity activity, Trip trip, Runnable onDone) {
        if (activity == null || trip == null || !trip.speedReviewPending
                || !trip.hasOverLimitPoints()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        reviewAt(activity, trip, 0, onDone);
    }

    private static void reviewAt(Activity activity, Trip trip, int index, Runnable onDone) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        while (index < trip.points.size() && !trip.points.get(index).overLimit) {
            index++;
        }
        if (index >= trip.points.size()) {
            trip.speedReviewPending = false;
            boolean saved = TripStore.update(activity, trip);
            AppLog.i("Trip", "Speed review completed: trip=" + trip.id + ", saved=" + saved);
            if (!saved) {
                Toast.makeText(activity, "超速点已处理，但保存失败", Toast.LENGTH_LONG).show();
            }
            if (onDone != null) {
                onDone.run();
            }
            return;
        }

        final Trip.Point point = trip.points.get(index);
        final int current = index;
        int ordinal = 1;
        for (int i = 0; i < current; i++) {
            if (trip.points.get(i).overLimit) {
                ordinal++;
            }
        }
        int total = trip.overLimitCount();
        String message = String.format(Locale.CHINA,
                "时间 %s · 速度 %s · 上限 %.0f km/h\n\n"
                        + "保留原始数据，或用前后点平均值替换，或手动输入一个数值。",
                Trip.formatDuration(point.elapsedMs / 1000),
                Trip.formatSpeed1(point.speedKmh),
                trip.speedLimitKmh);

        new AlertDialog.Builder(activity)
                .setTitle("处理超速点 " + ordinal + "/" + total)
                .setMessage(message)
                .setItems(new String[]{"保留数据", "取前后平均", "手动输入", "稍后处理"},
                        (dialog, which) -> {
                            if (which == 0) {
                                reviewAt(activity, trip, current + 1, onDone);
                            } else if (which == 1) {
                                point.speedKmh = Math.max(0, trip.averageSpeedAround(current));
                                AppLog.i("Trip", "Speed point replaced by neighbour average: "
                                        + Trip.round1(point.speedKmh));
                                reviewAt(activity, trip, current + 1, onDone);
                            } else if (which == 2) {
                                askManualValue(activity, trip, current, onDone);
                            } else {
                                if (onDone != null) {
                                    onDone.run();
                                }
                            }
                        })
                .setOnCancelListener(dialog -> {
                    if (onDone != null) {
                        onDone.run();
                    }
                })
                .show();
    }

    private static void askManualValue(Activity activity, Trip trip, int index, Runnable onDone) {
        final Trip.Point point = trip.points.get(index);
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
                        reviewAt(activity, trip, index, onDone))
                .setPositiveButton("确定", (dialog, which) -> {
                    double value;
                    try {
                        value = Double.parseDouble(input.getText().toString().trim());
                    } catch (Exception e) {
                        Toast.makeText(activity, "请输入数字", Toast.LENGTH_SHORT).show();
                        reviewAt(activity, trip, index, onDone);
                        return;
                    }
                    if (Double.isNaN(value) || value < 0 || value > 200) {
                        Toast.makeText(activity, "速度需在 0 – 200 km/h 之间", Toast.LENGTH_SHORT).show();
                        reviewAt(activity, trip, index, onDone);
                        return;
                    }
                    point.speedKmh = Trip.round1(value);
                    AppLog.i("Trip", "Speed point manually set: " + point.speedKmh);
                    reviewAt(activity, trip, index + 1, onDone);
                })
                .setOnCancelListener(dialog -> reviewAt(activity, trip, index, onDone))
                .show();
    }
}
