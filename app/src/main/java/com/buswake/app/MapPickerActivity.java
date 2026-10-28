package com.buswake.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class MapPickerActivity extends Activity {
    private static final int REQ_LOCATION = 301;
    private WebView webView;
    private TextView coordinateText;
    private EditText nameInput;
    private TextView saveButton;
    private TextView locateButton;

    private double pickedLat = Double.NaN;
    private double pickedLng = Double.NaN;
    private String editingId;
    private boolean hasPin;
    private boolean locating;
    private LocationManager locationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationListener oneShotListener;
    private Runnable locateTimeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.BG);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        editingId = getIntent().getStringExtra("stop_id");
        hasPin = getIntent().hasExtra("lat") && getIntent().hasExtra("lng");
        if (hasPin) {
            pickedLat = getIntent().getDoubleExtra("lat", 0);
            pickedLng = getIntent().getDoubleExtra("lng", 0);
        }
        setContentView(buildScreen());
        configureMap();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        topBar.setBackgroundColor(Ui.BG);

        TextView back = Ui.strong(this, "←", 26, Ui.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.button(this, 0x00000000, 0x22FFFFFF, 12));
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = Ui.dp(this, 8);
        topBar.addView(titleCol, titleLp);

        TextView title = Ui.strong(this, editingId != null ? "编辑站点" : "选择下车站点", 17, Ui.TEXT);
        titleCol.addView(title);
        TextView subtitle = Ui.text(this, "点击地图上的位置，手动选点", 10.5f, 0xFF64748B);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 3);
        titleCol.addView(subtitle, subLp);

        locateButton = Ui.strong(this, "定位", 12, Ui.ACCENT);
        locateButton.setGravity(Gravity.CENTER);
        locateButton.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        locateButton.setBackground(Ui.outlineButton(this, 0x0F38BDF8, 0x2638BDF8, 0x6638BDF8, 10));
        locateButton.setOnClickListener(v -> locateMe());
        topBar.addView(locateButton);
        root.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(0xFF172033);
        root.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        webView = new WebView(this);
        webView.setBackgroundColor(Ui.BG);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildBottomPanel(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View buildBottomPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 16));
        panel.setBackground(Ui.rounded(this, Ui.SURFACE, 22, 1, Ui.BORDER));

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.strong(this, "已选位置", 11.5f, Ui.MUTED);
        labelRow.addView(label);
        View spacer = new View(this);
        labelRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        coordinateText = Ui.strong(this, hasPin
                        ? String.format(Locale.CHINA, "%.5f, %.5f", pickedLat, pickedLng)
                        : "尚未选点",
                10.5f, hasPin ? Ui.ACCENT : 0xFF64748B);
        labelRow.addView(coordinateText);
        panel.addView(labelRow);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("站点名称，例如：望京西站");
        nameInput.setHintTextColor(0xFF64748B);
        nameInput.setTextColor(Ui.TEXT);
        nameInput.setTextSize(15);
        nameInput.setTypeface(Ui.regular());
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setBackground(Ui.rounded(this, 0xFF0D1829, 12, 1, 0xFF2A3B54));
        nameInput.setPadding(Ui.dp(this, 13), Ui.dp(this, 10), Ui.dp(this, 13), Ui.dp(this, 10));
        String initialName = getIntent().getStringExtra("name");
        if (initialName == null || initialName.trim().isEmpty()) {
            int next = getIntent().getIntExtra("next_index", 1);
            initialName = "下车站点 " + next;
        }
        nameInput.setText(initialName);
        nameInput.setSelection(initialName.length());
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        inputLp.topMargin = Ui.dp(this, 10);
        panel.addView(nameInput, inputLp);

        TextView hint = Ui.text(this, "点击地图选点，拖动图钉可微调；双指缩放地图。", 10, 0xFF64748B);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(this, 8);
        panel.addView(hint, hintLp);

        saveButton = Ui.strong(this, "保存这个站点", 15, 0xFF052331);
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setClickable(true);
        saveButton.setFocusable(true);
        saveButton.setOnClickListener(v -> saveStop());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        saveLp.topMargin = Ui.dp(this, 12);
        panel.addView(saveButton, saveLp);
        updateSaveState();
        return panel;
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
        });
        webView.addJavascriptInterface(new MapBridge(), "AndroidMap");

        double lat = getIntent().getDoubleExtra("lat", 35.0);
        double lng = getIntent().getDoubleExtra("lng", 105.0);
        int zoom = getIntent().getIntExtra("zoom", hasPin ? 16 : 4);
        if (!hasPin && !getIntent().hasExtra("lat")) {
            lat = 35.0;
            lng = 105.0;
            zoom = 4;
        }
        String url = "file:///android_asset/map.html?lat=" + lat + "&lng=" + lng
                + "&zoom=" + zoom + "&marker=" + (hasPin ? "1" : "0");
        webView.loadUrl(url);
    }

    private final class MapBridge {
        @JavascriptInterface
        public void onPick(String latValue, String lngValue) {
            try {
                double lat = Double.parseDouble(latValue);
                double lng = Double.parseDouble(lngValue);
                runOnUiThread(() -> updatePicked(lat, lng));
            } catch (Exception ignored) {
            }
        }
    }

    private void updatePicked(double lat, double lng) {
        pickedLat = lat;
        pickedLng = lng;
        hasPin = true;
        coordinateText.setText(String.format(Locale.CHINA, "%.5f, %.5f", lat, lng));
        coordinateText.setTextColor(Ui.ACCENT);
        updateSaveState();
    }

    private void updateSaveState() {
        if (saveButton == null) {
            return;
        }
        if (hasPin) {
            saveButton.setAlpha(1f);
            saveButton.setBackground(Ui.button(this, Ui.ACCENT, 0xFF7DD3FC, 14));
            saveButton.setTextColor(0xFF052331);
            saveButton.setEnabled(true);
        } else {
            saveButton.setAlpha(0.55f);
            saveButton.setBackground(Ui.rounded(this, 0xFF243247, 14));
            saveButton.setTextColor(0xFF7A8BA5);
            saveButton.setEnabled(false);
        }
    }

    private void saveStop() {
        if (!hasPin) {
            Toast.makeText(this, "请先在地图上点选一个位置", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            name = "下车站点";
        }
        List<Stop> stops = StopStore.loadStops(this);
        if (editingId != null) {
            for (Stop s : stops) {
                if (editingId.equals(s.id)) {
                    s.name = name;
                    s.lat = pickedLat;
                    s.lng = pickedLng;
                    break;
                }
            }
            Toast.makeText(this, "站点已更新", Toast.LENGTH_SHORT).show();
        } else {
            Stop stop = Stop.create(name, pickedLat, pickedLng);
            stops.add(stop);
            StopStore.select(this, stop.id);
            Toast.makeText(this, "已添加 · " + name, Toast.LENGTH_SHORT).show();
        }
        StopStore.saveStops(this, stops);
        StopStore.prefs(this).edit()
                .putLong(StopStore.KEY_LAST_MAP_LAT, Double.doubleToLongBits(pickedLat))
                .putLong(StopStore.KEY_LAST_MAP_LNG, Double.doubleToLongBits(pickedLng))
                .apply();
        setResult(RESULT_OK, new Intent());
        finish();
    }

    private void locateMe() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }
        if (locating) {
            return;
        }
        locating = true;
        locateButton.setText("定位中");
        locateButton.setAlpha(0.6f);

        Location best = null;
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!locationManager.isProviderEnabled(provider)) {
                    continue;
                }
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            }
        } catch (Exception ignored) {
        }

        if (best != null && System.currentTimeMillis() - best.getTime() < 120000) {
            showMyLocation(best);
            finishLocating();
            return;
        }

        oneShotListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                showMyLocation(location);
                finishLocating();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, oneShotListener, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        1000L, 0f, oneShotListener, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {
        } catch (Exception ignored) {
        }

        locateTimeout = () -> {
            if (locating) {
                finishLocating();
                Toast.makeText(this, "暂时无法获取定位，请检查系统定位是否开启", Toast.LENGTH_LONG).show();
            }
        };
        handler.postDelayed(locateTimeout, 12000);
    }

    private void showMyLocation(Location location) {
        if (location == null || webView == null) {
            return;
        }
        String js = String.format(Locale.US, "window.setMyLocation(%.7f,%.7f,%.1f)",
                location.getLatitude(), location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0);
        webView.evaluateJavascript(js, null);
        Toast.makeText(this, "已定位到你的位置", Toast.LENGTH_SHORT).show();
    }

    private void finishLocating() {
        locating = false;
        locateButton.setText("定位");
        locateButton.setAlpha(1f);
        if (oneShotListener != null) {
            try {
                locationManager.removeUpdates(oneShotListener);
            } catch (Exception ignored) {
            }
            oneShotListener = null;
        }
        if (locateTimeout != null) {
            handler.removeCallbacks(locateTimeout);
            locateTimeout = null;
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                locateMe();
            } else {
                Toast.makeText(this, "需要定位权限才能回到当前位置", Toast.LENGTH_LONG).show();
            }
        }
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
        finishLocating();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidMap");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
