package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Locale;

/** Asks for a speed threshold, then hands the matching points to SpeedReview. */
public final class SpeedFilterDialog {
    private SpeedFilterDialog() {
    }

    public static void show(Activity activity, Trip trip, Runnable onDone) {
        if (activity == null || trip == null || activity.isFinishing()) {
            return;
        }
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText("80");
        input.setSelection(input.getText().length());
        int pad = Ui.dp(activity, 18);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(activity)
                .setTitle("按速度筛选（km/h）")
                .setMessage("输入一个速度值，找出这条行程中所有超过它的点，然后逐点确认。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("筛选", (dialog, which) -> {
                    double threshold;
                    try {
                        threshold = Double.parseDouble(input.getText().toString().trim());
                    } catch (Exception e) {
                        Toast.makeText(activity, "请输入数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (Double.isNaN(threshold) || threshold < 0 || threshold > 200) {
                        Toast.makeText(activity, "速度需在 0 – 200 km/h 之间",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    int count = trip.countPointsAbove(threshold);
                    if (count == 0) {
                        Toast.makeText(activity, String.format(Locale.CHINA,
                                "这条行程没有超过 %.1f km/h 的点", threshold),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle("找到 " + count + " 个点")
                            .setMessage(String.format(Locale.CHINA,
                                    "超过 %.1f km/h 的点有 %d 个。接下来逐点确认：保留、取前后平均或手动输入。",
                                    threshold, count))
                            .setNegativeButton("取消", null)
                            .setPositiveButton("开始确认", (d, w) ->
                                    SpeedReview.reviewAbove(activity, trip, threshold, onDone))
                            .show();
                })
                .show();
    }
}
