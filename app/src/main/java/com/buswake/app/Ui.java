package com.buswake.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF0B1220;
    public static final int BG_DEEP = 0xFF070D17;
    public static final int SURFACE = 0xFF131F33;
    public static final int SURFACE_2 = 0xFF18263D;
    public static final int ACCENT = 0xFF38BDF8;
    public static final int ACCENT_DARK = 0xFF082F49;
    public static final int AMBER = 0xFFFBBF24;
    public static final int DANGER = 0xFFF87171;
    public static final int SUCCESS = 0xFF34D399;
    public static final int TEXT = 0xFFF8FAFC;
    public static final int MUTED = 0xFF94A3B8;
    public static final int BORDER = 0xFF22324D;

    private Ui() {
    }

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static Typeface regular() {
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public static TextView text(Context c, String value, float sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(regular());
        tv.setIncludeFontPadding(false);
        tv.setLineSpacing(dp(c, 3), 1f);
        return tv;
    }

    public static TextView strong(Context c, String value, float sp, int color) {
        TextView tv = text(c, value, sp, color);
        tv.setTypeface(medium());
        return tv;
    }

    public static GradientDrawable rounded(Context c, int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable rounded(Context c, int fill, float radiusDp, int strokeWidthDp, int strokeColor) {
        GradientDrawable d = rounded(c, fill, radiusDp);
        d.setStroke(dp(c, strokeWidthDp), strokeColor);
        return d;
    }

    public static GradientDrawable circle(Context c, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    public static GradientDrawable circle(Context c, int fill, int strokeWidthDp, int strokeColor) {
        GradientDrawable d = circle(c, fill);
        d.setStroke(dp(c, strokeWidthDp), strokeColor);
        return d;
    }

    public static StateListDrawable button(Context c, int normal, int pressed, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable p = rounded(c, pressed, radiusDp);
        GradientDrawable n = rounded(c, normal, radiusDp);
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, n);
        return s;
    }

    public static StateListDrawable outlineButton(Context c, int normal, int pressed, int stroke, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(c, pressed, radiusDp, 1, stroke));
        s.addState(new int[]{}, rounded(c, normal, radiusDp, 1, stroke));
        return s;
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    public static LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    public static void pad(View v, int left, int top, int right, int bottom) {
        v.setPadding(left, top, right, bottom);
    }

    public static void colorState(TextView v, int normal) {
        v.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{brighten(normal), normal}
        ));
    }

    public static int brighten(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] * 1.18f);
        return Color.HSVToColor(hsv);
    }

    public static TextView chip(Context c, String label, int textColor, int fillColor) {
        TextView tv = strong(c, label, 11, textColor);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
        tv.setBackground(rounded(c, fillColor, 20, 1, (textColor & 0x00FFFFFF) | 0x33000000));
        return tv;
    }
}
