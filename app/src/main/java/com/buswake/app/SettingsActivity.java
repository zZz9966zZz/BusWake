package com.buswake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class SettingsActivity extends Activity {
    private static final int REQ_RINGTONE = 501;
    private EditText distanceInput;
    private EditText intervalInput;
    private EditText speedInput;
    private EditText leadTimeInput;
    private TextView distancePreview;
    private TextView distanceEstimate;
    private TextView intervalPreview;
    private TextView intervalCount;
    private TextView speedPreview;
    private TextView recommendPreview;
    private TextView recommendDetail;
    private TextView applyRecommendation;
    private TextView soundValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        setContentView(buildScreen());
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
        Ui.pad(content, Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildIntroCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 14);

        content.addView(buildSpeedAssistantCard(), cardLp);

        LinearLayout distanceCard = buildDistanceCard();
        content.addView(distanceCard, cardLp);

        LinearLayout frequencyCard = buildFrequencyCard();
        content.addView(frequencyCard, cardLp);

        content.addView(buildSoundCard(), cardLp);

        if (StopStore.isGuardRunning(this)) {
            content.addView(buildLiveCard(), cardLp);
        }

        TextView footer = Ui.text(this, "到站叫醒 1.6  ·  设置仅保存在本机", 10, 0xFF64748B);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(this, 20);
        content.addView(footer, footerLp);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF172033);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        updatePreviews();
        updateSpeedAssistant();
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

        titles.addView(Ui.strong(this, "叫醒设置", 17, Ui.TEXT));
        TextView sub = Ui.text(this, "速度助手 · 唤醒距离 · 定位频率", 10.5f, 0xFF64748B);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 3);
        titles.addView(sub, subLp);

        TextView reset = Ui.strong(this, "恢复默认", 11.5f, Ui.ACCENT);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8));
        reset.setBackground(Ui.outlineButton(this, 0x0F38BDF8, 0x2638BDF8, 0x6638BDF8, 10));
        reset.setOnClickListener(v -> confirmReset());
        bar.addView(reset);
        return bar;
    }

    private View buildIntroCard() {
        LinearLayout card = card();
        TextView title = Ui.strong(this, "按你的通勤习惯调整", 14, Ui.TEXT);
        card.addView(title);

        TextView desc = Ui.text(this,
                "速度助手根据你输入的车速和提前时间计算推荐唤醒距离。推荐值不会自动覆盖当前距离，确认后点“应用到距离”。保存后无需重新开始守护。",
                11, Ui.MUTED);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 7);
        card.addView(desc, descLp);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = Ui.chip(this, "推荐距离按车速与提前时间计算", Ui.ACCENT, 0x1438BDF8);
        chipRow.addView(chip);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = Ui.dp(this, 10);
        card.addView(chipRow, chipLp);
        return card;
    }

    private LinearLayout buildSpeedAssistantCard() {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.strong(this, "速度助手", 15, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        speedPreview = Ui.strong(this, "80 km/h", 21, Ui.ACCENT);
        speedPreview.setGravity(Gravity.END);
        header.addView(speedPreview);
        card.addView(header);

        TextView desc = Ui.text(this, "输入车辆速度和希望提前的唤醒时间，自动计算推荐距离。", 11, Ui.MUTED);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 6);
        card.addView(desc, descLp);

        TextView speedLabel = Ui.strong(this, "车辆速度", 11.5f, 0xFFCBD5E1);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = Ui.dp(this, 13);
        card.addView(speedLabel, labelLp);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedInput = input("80", "例如 80", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        speedRow.addView(speedInput, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));

        TextView speedUnit = Ui.strong(this, "km/h", 13, Ui.MUTED);
        speedUnit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams speedUnitLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 62), Ui.dp(this, 48));
        speedUnitLp.leftMargin = Ui.dp(this, 8);
        speedRow.addView(speedUnit, speedUnitLp);
        LinearLayout.LayoutParams speedRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        speedRowLp.topMargin = Ui.dp(this, 7);
        card.addView(speedRow, speedRowLp);

        card.addView(buildPresetRow(new String[]{"40", "60", "80", "100", "120"}, 3), presetLp());

        TextView leadLabel = Ui.strong(this, "唤醒提前时间", 11.5f, 0xFFCBD5E1);
        LinearLayout.LayoutParams leadLabelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        leadLabelLp.topMargin = Ui.dp(this, 12);
        card.addView(leadLabel, leadLabelLp);

        LinearLayout leadRow = new LinearLayout(this);
        leadRow.setOrientation(LinearLayout.HORIZONTAL);
        leadRow.setGravity(Gravity.CENTER_VERTICAL);
        leadTimeInput = input("90", "例如 90", InputType.TYPE_CLASS_NUMBER);
        leadRow.addView(leadTimeInput, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));

        TextView leadUnit = Ui.strong(this, "秒", 13, Ui.MUTED);
        leadUnit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams leadUnitLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 62), Ui.dp(this, 48));
        leadUnitLp.leftMargin = Ui.dp(this, 8);
        leadRow.addView(leadUnit, leadUnitLp);
        LinearLayout.LayoutParams leadRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        leadRowLp.topMargin = Ui.dp(this, 7);
        card.addView(leadRow, leadRowLp);

        card.addView(buildPresetRow(new String[]{"30", "60", "90", "120", "180"}, 4), presetLp());

        LinearLayout recBox = new LinearLayout(this);
        recBox.setOrientation(LinearLayout.HORIZONTAL);
        recBox.setGravity(Gravity.CENTER_VERTICAL);
        recBox.setPadding(Ui.dp(this, 13), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11));
        recBox.setBackground(Ui.rounded(this, 0x14FBBF24, 14, 1, 0x55FBBF24));

        LinearLayout recCol = new LinearLayout(this);
        recCol.setOrientation(LinearLayout.VERTICAL);
        TextView recLabel = Ui.text(this, "推荐唤醒距离", 10, 0xFFD6C7A1);
        recCol.addView(recLabel);
        recommendPreview = Ui.strong(this, "2 公里", 24, Ui.AMBER);
        LinearLayout.LayoutParams recPreviewLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        recPreviewLp.topMargin = Ui.dp(this, 3);
        recCol.addView(recommendPreview, recPreviewLp);
        recommendDetail = Ui.text(this, "80 km/h × 1 分 30 秒", 10, 0xFFB6A98F);
        LinearLayout.LayoutParams recDetailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        recDetailLp.topMargin = Ui.dp(this, 4);
        recCol.addView(recommendDetail, recDetailLp);
        recBox.addView(recCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        applyRecommendation = Ui.strong(this, "应用到距离", 11.5f, 0xFF2A1E05);
        applyRecommendation.setGravity(Gravity.CENTER);
        applyRecommendation.setPadding(Ui.dp(this, 10), Ui.dp(this, 9), Ui.dp(this, 10), Ui.dp(this, 9));
        applyRecommendation.setBackground(Ui.button(this, Ui.AMBER, 0xFFFCD34D, 10));
        applyRecommendation.setOnClickListener(v -> applyRecommendedDistance());
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        applyLp.leftMargin = Ui.dp(this, 8);
        recBox.addView(applyRecommendation, applyLp);

        LinearLayout.LayoutParams recBoxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        recBoxLp.topMargin = Ui.dp(this, 12);
        card.addView(recBox, recBoxLp);

        TextView range = Ui.text(this,
                "车速 1 – 150 km/h，提前时间 10 – 600 秒；推荐距离限制在 0.2 – 20 公里。",
                10, 0xFF64748B);
        LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rangeLp.topMargin = Ui.dp(this, 8);
        card.addView(range, rangeLp);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSpeedAssistant();
            }
        };
        speedInput.addTextChangedListener(watcher);
        leadTimeInput.addTextChangedListener(watcher);

        speedInput.setText(String.format(Locale.CHINA, "%.0f", StopStore.getManualSpeed(this)));
        speedInput.setSelection(speedInput.getText().length());
        leadTimeInput.setText(String.valueOf(StopStore.getLeadTimeSeconds(this)));
        leadTimeInput.setSelection(leadTimeInput.getText().length());
        return card;
    }

    private void updateSpeedAssistant() {
        if (recommendPreview == null || speedInput == null || leadTimeInput == null) {
            return;
        }
        double speed = parseDouble(speedInput.getText().toString());
        int leadSeconds = parseInt(leadTimeInput.getText().toString());
        boolean speedValid = speed >= StopStore.MIN_SPEED_KMH && speed <= StopStore.MAX_SPEED_KMH;
        boolean leadValid = leadSeconds >= StopStore.MIN_LEAD_TIME_SECONDS
                && leadSeconds <= StopStore.MAX_LEAD_TIME_SECONDS;

        speedPreview.setText(speedValid ? StopStore.formatSpeed(speed) : "—");

        if (!speedValid || !leadValid) {
            recommendPreview.setText("—");
            recommendDetail.setText("请输入 1 – 150 km/h 和 10 – 600 秒");
            applyRecommendation.setEnabled(false);
            applyRecommendation.setAlpha(0.45f);
            applyRecommendation.setBackground(Ui.rounded(this, 0xFF4A3A1B, 10));
            applyRecommendation.setTextColor(0xFF9C8A63);
            updatePreviews();
            return;
        }

        int recommended = StopStore.recommendDistanceM(speed, leadSeconds);
        double rawMeters = speed / 3.6 * leadSeconds;
        recommendPreview.setText(StopStore.formatRadius(recommended));
        String detail = StopStore.formatSpeed(speed) + " × " + StopStore.formatLeadDuration(leadSeconds)
                + " = " + StopStore.formatRadius(recommended);
        if (rawMeters < StopStore.MIN_RADIUS_M || rawMeters > StopStore.MAX_RADIUS_M) {
            detail += "（已按范围限制）";
        }
        recommendDetail.setText(detail);
        applyRecommendation.setEnabled(true);
        applyRecommendation.setAlpha(1f);
        applyRecommendation.setBackground(Ui.button(this, Ui.AMBER, 0xFFFCD34D, 10));
        applyRecommendation.setTextColor(0xFF2A1E05);
        updatePreviews();
    }

    private void applyRecommendedDistance() {
        double speed = parseDouble(speedInput.getText().toString());
        int leadSeconds = parseInt(leadTimeInput.getText().toString());
        if (speed < StopStore.MIN_SPEED_KMH || speed > StopStore.MAX_SPEED_KMH
                || leadSeconds < StopStore.MIN_LEAD_TIME_SECONDS
                || leadSeconds > StopStore.MAX_LEAD_TIME_SECONDS) {
            return;
        }
        int recommended = StopStore.recommendDistanceM(speed, leadSeconds);
        distanceInput.setText(StopStore.formatKmInput(recommended));
        distanceInput.setSelection(distanceInput.getText().length());
        updatePreviews();
        Toast.makeText(this,
                "已填入 " + StopStore.formatRadius(recommended) + "，点击“保存设置”生效",
                Toast.LENGTH_SHORT).show();
    }

    private int parseInt(String text) {
        if (text == null || text.trim().isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private LinearLayout buildDistanceCard() {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.strong(this, "唤醒距离", 15, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        distancePreview = Ui.strong(this, "2 公里", 21, Ui.ACCENT);
        distancePreview.setGravity(Gravity.END);
        header.addView(distancePreview);
        card.addView(header);

        TextView desc = Ui.text(this, "车辆进入该范围时开始响铃、震动。", 11, Ui.MUTED);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 6);
        card.addView(desc, descLp);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        distanceInput = input("2", "例如 2", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputRow.addView(distanceInput, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1f));

        TextView unit = Ui.strong(this, "公里", 13, Ui.MUTED);
        unit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 48));
        unitLp.leftMargin = Ui.dp(this, 8);
        inputRow.addView(unit, unitLp);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = Ui.dp(this, 12);
        card.addView(inputRow, inputLp);

        card.addView(buildPresetRow(new String[]{"0.5", "1", "2"}, 0), presetLp());
        card.addView(buildPresetRow(new String[]{"3", "5", "10"}, 1), presetLp());

        distanceEstimate = Ui.text(this, "按 80 km/h 计算，约提前 90 秒提醒", 11.5f, Ui.AMBER);
        LinearLayout.LayoutParams estLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        estLp.topMargin = Ui.dp(this, 12);
        card.addView(distanceEstimate, estLp);

        TextView range = Ui.text(this, "可设置 0.2 – 20 公里，支持小数。", 10, 0xFF64748B);
        LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rangeLp.topMargin = Ui.dp(this, 5);
        card.addView(range, rangeLp);

        distanceInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePreviews();
            }
        });

        distanceInput.setText(StopStore.formatKmInput(StopStore.getRadiusM(this)));
        distanceInput.setSelection(distanceInput.getText().length());
        return card;
    }

    private LinearLayout buildFrequencyCard() {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.strong(this, "定位频率", 15, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        intervalPreview = Ui.strong(this, "每 2 秒", 21, Ui.ACCENT);
        intervalPreview.setGravity(Gravity.END);
        header.addView(intervalPreview);
        card.addView(header);

        TextView desc = Ui.text(this, "每隔多久获取一次位置。频率越高越准时，也更耗电。", 11, Ui.MUTED);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 6);
        card.addView(desc, descLp);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        intervalInput = input("2", "例如 2", InputType.TYPE_CLASS_NUMBER);
        inputRow.addView(intervalInput, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 48), 1f));

        TextView unit = Ui.strong(this, "秒", 13, Ui.MUTED);
        unit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 48));
        unitLp.leftMargin = Ui.dp(this, 8);
        inputRow.addView(unit, unitLp);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = Ui.dp(this, 12);
        card.addView(inputRow, inputLp);

        card.addView(buildPresetRow(new String[]{"1", "2", "3", "5", "10"}, 2), presetLp());

        intervalCount = Ui.text(this, "每小时最多约 1800 次定位", 11.5f, Ui.AMBER);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countLp.topMargin = Ui.dp(this, 12);
        card.addView(intervalCount, countLp);

        TextView range = Ui.text(this, "可设置 1 – 60 秒，建议 2 – 5 秒。", 10, 0xFF64748B);
        LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rangeLp.topMargin = Ui.dp(this, 5);
        card.addView(range, rangeLp);

        intervalInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePreviews();
            }
        });

        intervalInput.setText(String.valueOf(StopStore.getIntervalMs(this) / 1000));
        intervalInput.setSelection(intervalInput.getText().length());
        return card;
    }

    private LinearLayout buildSoundCard() {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.strong(this, "闹钟声音", 15, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView follows = Ui.chip(this, "跟随系统音量", Ui.MUTED, 0x1A94A3B8);
        header.addView(follows);
        card.addView(header);

        soundValue = Ui.strong(this, "", 15, Ui.ACCENT);
        soundValue.setSingleLine(true);
        soundValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueLp.topMargin = Ui.dp(this, 9);
        card.addView(soundValue, valueLp);

        TextView desc = Ui.text(this,
                "不选择时使用系统默认闹钟音乐。播放音量跟随系统闹钟音量，App 不会强制调大。",
                11, Ui.MUTED);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 6);
        card.addView(desc, descLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView choose = Ui.strong(this, "选择音乐", 12, Ui.ACCENT);
        choose.setGravity(Gravity.CENTER);
        choose.setBackground(Ui.outlineButton(this, 0x0F38BDF8, 0x2638BDF8, 0x6638BDF8, 11));
        choose.setOnClickListener(v -> chooseSound());
        row.addView(choose, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        TextView reset = Ui.strong(this, "使用系统默认", 12, 0xFFCBD5E1);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(Ui.outlineButton(this, 0x0A94A3B8, 0x2094A3B8, 0x5594A3B8, 11));
        reset.setOnClickListener(v -> resetSound());
        row.addView(reset, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(this, 12);
        card.addView(row, rowLp);

        updateSoundLabel();
        return card;
    }

    private void updateSoundLabel() {
        if (soundValue != null) {
            soundValue.setText(AlarmSoundStore.getDisplayName(this));
        }
    }

    private void chooseSound() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟音乐");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri);
        if (defaultUri != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultUri);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_RINGTONE);
        } catch (Exception e) {
            Toast.makeText(this, "系统没有可用的音乐选择器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_RINGTONE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri picked;
        if (Build.VERSION.SDK_INT >= 33) {
            picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
        } else {
            picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        }
        if (picked == null) {
            resetSound();
            return;
        }
        Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (picked.equals(defaultUri)) {
            AlarmSoundStore.clear(this);
            updateSoundLabel();
            AppLog.i("Sound", "System default alarm sound selected");
            Toast.makeText(this, "已使用系统默认闹钟音乐", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = getRingtoneTitle(picked);
        boolean ok = AlarmSoundStore.importFromUri(this, picked, title);
        if (ok) {
            updateSoundLabel();
            AppLog.i("Sound", "Custom alarm sound selected: " + title);
            Toast.makeText(this, "已选择：" + AlarmSoundStore.getDisplayName(this),
                    Toast.LENGTH_SHORT).show();
        } else {
            AppLog.e("Sound", "Failed to import custom alarm sound");
            Toast.makeText(this, "无法读取该音乐，请换一首再试", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private String getRingtoneTitle(Uri uri) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                String title = ringtone.getTitle(this);
                if (title != null && !title.trim().isEmpty()) {
                    return title;
                }
            }
        } catch (Exception ignored) {
        }
        return "自定义闹钟音乐";
    }

    private void resetSound() {
        AlarmSoundStore.clear(this);
        updateSoundLabel();
        AppLog.i("Sound", "Alarm sound reset to system default");
        Toast.makeText(this, "已使用系统默认闹钟音乐", Toast.LENGTH_SHORT).show();
    }

    private View buildLiveCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(this, 0x1410B981, 14, 1, 0x5534D399));

        TextView dot = Ui.strong(this, "●", 10, Ui.SUCCESS);
        card.addView(dot);
        TextView text = Ui.text(this, "守护正在运行，保存后新设置会立即应用。", 11, 0xFF86EFAC);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, 7);
        card.addView(text, lp);
        return card;
    }

    private LinearLayout buildPresetRow(String[] values, int type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            String suffix = type == 3 ? "" : (type == 2 || type == 4 ? " 秒" : " 公里");
            TextView item = presetButton(value + suffix);
            item.setOnClickListener(v -> {
                EditText target;
                if (type == 2) {
                    target = intervalInput;
                } else if (type == 3) {
                    target = speedInput;
                } else if (type == 4) {
                    target = leadTimeInput;
                } else {
                    target = distanceInput;
                }
                target.setText(value);
                target.setSelection(target.getText().length());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, Ui.dp(this, 38), 1f);
            if (i < values.length - 1) {
                lp.rightMargin = Ui.dp(this, 7);
            }
            row.addView(item, lp);
        }
        return row;
    }

    private TextView presetButton(String text) {
        TextView item = Ui.strong(this, text, 11.5f, 0xFFBAE6FD);
        item.setGravity(Gravity.CENTER);
        item.setBackground(Ui.outlineButton(this, 0x0D38BDF8, 0x2638BDF8, 0x4038BDF8, 10));
        return item;
    }

    private LinearLayout.LayoutParams presetLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 7);
        return lp;
    }

    private EditText input(String text, String hint, int type) {
        EditText edit = new EditText(this);
        edit.setText(text);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setTextColor(Ui.TEXT);
        edit.setHintTextColor(0xFF64748B);
        edit.setTextSize(17);
        edit.setTypeface(Ui.medium());
        edit.setInputType(type);
        edit.setBackground(Ui.rounded(this, 0xFF0D1829, 12, 1, 0xFF2A3B54));
        edit.setPadding(Ui.dp(this, 13), Ui.dp(this, 9), Ui.dp(this, 13), Ui.dp(this, 9));
        return edit;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 16));
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 18, 1, Ui.BORDER));
        return card;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 14));
        bar.setBackgroundColor(0xFF0A111D);

        TextView save = Ui.strong(this, "保存设置", 16, 0xFF052331);
        save.setGravity(Gravity.CENTER);
        save.setClickable(true);
        save.setFocusable(true);
        save.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 16));
        save.setOnClickListener(v -> saveSettings());
        bar.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        TextView hint = Ui.text(this, "守护中保存同样立即生效", 10.5f, 0xFF64748B);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        bar.addView(hint, lp);
        return bar;
    }

    private void updatePreviews() {
        if (distancePreview == null || intervalPreview == null) {
            return;
        }
        double km = parseDouble(distanceInput.getText().toString());
        if (km >= 0.2 && km <= 20.0) {
            int meters = (int) Math.round(km * 10) * 100;
            distancePreview.setText(StopStore.formatRadius(meters));
            double speed = parseDouble(speedInput.getText().toString());
            if (speed < StopStore.MIN_SPEED_KMH || speed > StopStore.MAX_SPEED_KMH) {
                speed = StopStore.DEFAULT_MANUAL_SPEED;
            }
            int seconds = StopStore.leadTimeSeconds(meters, speed);
            distanceEstimate.setText("按 " + StopStore.formatSpeed(speed)
                    + " 计算，约提前 " + formatLead(seconds) + "提醒");
            distanceEstimate.setTextColor(Ui.AMBER);
        } else {
            distancePreview.setText("—");
            distanceEstimate.setText("请输入 0.2 – 20 之间的公里数");
            distanceEstimate.setTextColor(Ui.DANGER);
        }

        String intervalText = intervalInput.getText().toString().trim();
        int seconds = -1;
        try {
            seconds = Integer.parseInt(intervalText);
        } catch (Exception ignored) {
        }
        if (seconds >= 1 && seconds <= 60) {
            intervalPreview.setText("每 " + seconds + " 秒");
            int count = 3600 / seconds;
            intervalCount.setText("每小时最多约 " + count + " 次定位");
            intervalCount.setTextColor(Ui.AMBER);
        } else {
            intervalPreview.setText("—");
            intervalCount.setText("请输入 1 – 60 之间的整数秒数");
            intervalCount.setTextColor(Ui.DANGER);
        }
    }

    private String formatLead(int seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (rest == 0) {
            return minutes + " 分钟";
        }
        return minutes + " 分 " + rest + " 秒";
    }

    private void saveSettings() {
        String distanceText = distanceInput.getText().toString().trim().replace(',', '.');
        double km = parseDouble(distanceText);
        if (Double.isNaN(km) || km < 0.2 || km > 20.0) {
            distanceInput.setError("请输入 0.2 – 20 公里");
            distanceInput.requestFocus();
            Toast.makeText(this, "唤醒距离需在 0.2 – 20 公里之间", Toast.LENGTH_SHORT).show();
            return;
        }

        double speed = parseDouble(speedInput.getText().toString());
        if (speed < StopStore.MIN_SPEED_KMH || speed > StopStore.MAX_SPEED_KMH) {
            speedInput.setError("请输入 1 – 150 km/h");
            speedInput.requestFocus();
            Toast.makeText(this, "车辆速度需在 1 – 150 km/h 之间", Toast.LENGTH_SHORT).show();
            return;
        }
        int leadSeconds = parseInt(leadTimeInput.getText().toString());
        if (leadSeconds < StopStore.MIN_LEAD_TIME_SECONDS
                || leadSeconds > StopStore.MAX_LEAD_TIME_SECONDS) {
            leadTimeInput.setError("请输入 10 – 600 秒");
            leadTimeInput.requestFocus();
            Toast.makeText(this, "唤醒提前时间需在 10 – 600 秒之间", Toast.LENGTH_SHORT).show();
            return;
        }

        String intervalText = intervalInput.getText().toString().trim();
        int seconds;
        try {
            seconds = Integer.parseInt(intervalText);
        } catch (Exception e) {
            intervalInput.setError("请输入整数秒数");
            intervalInput.requestFocus();
            Toast.makeText(this, "定位频率需为 1 – 60 秒的整数", Toast.LENGTH_SHORT).show();
            return;
        }
        if (seconds < 1 || seconds > 60) {
            intervalInput.setError("请输入 1 – 60 秒");
            intervalInput.requestFocus();
            Toast.makeText(this, "定位频率需在 1 – 60 秒之间", Toast.LENGTH_SHORT).show();
            return;
        }

        int meters = (int) Math.round(km * 10) * 100;
        StopStore.setManualSpeed(this, (float) speed);
        StopStore.setLeadTimeSeconds(this, leadSeconds);
        StopStore.setRadiusM(this, meters);
        StopStore.setIntervalMs(this, seconds * 1000);
        AppLog.i("Settings", "Saved: radius=" + meters + "m, interval=" + (seconds * 1000)
                + "ms, speed=" + speed + "km/h, lead=" + leadSeconds + "s");

        if (StopStore.isGuardRunning(this)) {
            try {
                startService(new Intent(this, GuardService.class)
                        .setAction(GuardService.ACTION_SETTINGS_CHANGED));
            } catch (Exception ignored) {
            }
        }

        Toast.makeText(this,
                String.format(Locale.CHINA, "已保存：%s / 每 %d 秒定位",
                        StopStore.formatRadius(meters), seconds),
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("恢复默认设置？")
                .setMessage("速度恢复为 80 km/h，提前时间 90 秒，唤醒距离 2 公里，定位频率每 2 秒。仍需点击“保存设置”才会生效。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (d, w) -> {
                    speedInput.setText(String.format(Locale.CHINA, "%.0f", StopStore.DEFAULT_MANUAL_SPEED));
                    speedInput.setSelection(speedInput.getText().length());
                    leadTimeInput.setText(String.valueOf(StopStore.DEFAULT_LEAD_TIME_SECONDS));
                    leadTimeInput.setSelection(leadTimeInput.getText().length());
                    distanceInput.setText(StopStore.formatKmInput(StopStore.DEFAULT_RADIUS_M));
                    distanceInput.setSelection(distanceInput.getText().length());
                    intervalInput.setText(String.valueOf(StopStore.DEFAULT_INTERVAL_MS / 1000));
                    intervalInput.setSelection(intervalInput.getText().length());
                    updateSpeedAssistant();
                    updatePreviews();
                })
                .show();
    }

    private double parseDouble(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
