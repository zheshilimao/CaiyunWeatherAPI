package com.api.caiyunweather;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final int REQ_SETTINGS = 1002;
    private static final int REQ_LOCATION = 1001;
    private static final long MANUAL_LOCATION_REFRESH_INTERVAL_MS = 10 * 60 * 1000L;
    private static final String LOCATION_REFRESH_PREFS = "location_refresh_prefs";
    private static final String KEY_LAST_LOCATION_SAVED_AT = "last_location_saved_at";
    private WebView webView;
    private SwipeRefreshLayout refreshLayout;
    private LocationManager locationManager;
    private boolean settingsOpened = false;
    private WeatherApiBridge weatherApiBridge;
    private boolean manualRefreshWaitingForPermission = false;
    private boolean manualLocationRequestActive = false;
    private LocationListener manualLocationListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable manualLocationTimeout = () -> {
        if (!manualLocationRequestActive) return;
        finishManualLocationRequest();
        // 定位超时仍刷新天气，沿用已保存的位置，避免手动刷新一直转圈。
        refreshWeatherUsingSavedLocation();
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 让天气页面延伸到状态栏区域，状态栏透明，与页面背景自然融合。
        // Edge-to-edge：天气页面背景延伸到状态栏，状态栏本身透明。
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        // 开屏背板：固定晴空蓝 + 导航栏取深端色
        applyBootBackground();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        refreshLayout = new SwipeRefreshLayout(this);
        refreshLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        refreshLayout.setColorSchemeColors(android.graphics.Color.rgb(105,199,255));
        webView = new WebView(this);
        refreshLayout.addView(webView, new SwipeRefreshLayout.LayoutParams(-1, -1));
        setContentView(refreshLayout);

        setupWebView();
        weatherApiBridge = new WeatherApiBridge(this, webView);
        webView.addJavascriptInterface(weatherApiBridge, "NativeWeather");
        refreshLayout.setOnRefreshListener(this::handleManualRefresh);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        // 先让天气页面立即显示；定位放到页面显示之后后台进行，避免启动时卡在“定位加载中”。
        webView.loadUrl("file:///android_asset/weather.html");
    }

    public void stopRefreshing() {
        runOnUiThread(() -> refreshLayout.setRefreshing(false));
    }

    /** 开屏铺底：固定用晴空蓝背板（#5a9bd0→#22608e→#0a2c4e），启动画面稳定统一，
        云层随后在背板之上渐渐浮现。不再用上次天气的截图/配色。 */
    private void applyBootBackground() {
        int[] colors = new int[]{
                android.graphics.Color.rgb(90, 155, 208),
                android.graphics.Color.rgb(34, 96, 142),
                android.graphics.Color.rgb(10, 44, 78)
        };
        getWindow().setBackgroundDrawable(
                new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors));
        getWindow().setNavigationBarColor(colors[2]);
    }

    /** 配置 WebView：JS 开关、缓存、桥接口与页面渲染回调。 */
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        // 主页面右侧系统滚动条仅是视觉提示，不需要显示；图表自身的横向滚动仍保留。
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(true);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                refreshLayout.setRefreshing(false);
                // 页面已经可见后再做一次性定位。已有缓存/上次位置不会被启动定位阻塞。
                view.postDelayed(() -> requestAutoLocation(), 300);
            }
        });
        webView.addJavascriptInterface(new ConfigBridge(this), "AndroidConfig");
    }


    /** 手动刷新时先尝试获取一次新定位，再使用最新坐标请求天气。 */
    private void handleManualRefresh() {
        if (!AppConfig.isAutoLocation(this)) {
            refreshWeatherUsingSavedLocation();
            return;
        }
        // 10 分钟内已成功更新过定位时，手动刷新只更新天气，避免重复启动定位。
        long lastLocationSavedAt = getSharedPreferences(LOCATION_REFRESH_PREFS, MODE_PRIVATE)
                .getLong(KEY_LAST_LOCATION_SAVED_AT, 0L);
        long now = System.currentTimeMillis();
        if (lastLocationSavedAt > 0L && now >= lastLocationSavedAt
                && now - lastLocationSavedAt < MANUAL_LOCATION_REFRESH_INTERVAL_MS) {
            refreshWeatherUsingSavedLocation();
            return;
        }
        if (!hasLocationPermission()) {
            manualRefreshWaitingForPermission = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        requestFreshLocationForManualRefresh();
    }

    private void refreshWeatherUsingSavedLocation() {
        runOnUiThread(() -> webView.evaluateJavascript(
                "if(window.refreshWeather){window.refreshWeather();}", null));
    }

    /** 不使用 last-known location，避免手动刷新仍拿到旧坐标。 */
    private void requestFreshLocationForManualRefresh() {
        if (manualLocationRequestActive) return;
        manualLocationRequestActive = true;
        manualLocationListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                if (!manualLocationRequestActive) return;
                finishManualLocationRequest();
                saveLocation(location);
            }
        };
        boolean requested = false;
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                        manualLocationListener, Looper.getMainLooper());
                requested = true;
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
                        manualLocationListener, Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        if (!requested) {
            finishManualLocationRequest();
            refreshWeatherUsingSavedLocation();
            return;
        }
        mainHandler.postDelayed(manualLocationTimeout, 8000);
    }

    private void finishManualLocationRequest() {
        manualLocationRequestActive = false;
        mainHandler.removeCallbacks(manualLocationTimeout);
        if (manualLocationListener != null) {
            try { locationManager.removeUpdates(manualLocationListener); }
            catch (SecurityException | IllegalArgumentException ignored) {}
            manualLocationListener = null;
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAutoLocation() {
        if (!AppConfig.isAutoLocation(this)) return;
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        updateLocationFromProvider();
    }

    private void updateLocationFromProvider() {
        try {
            Location best = null;
            for (String provider : locationManager.getProviders(true)) {
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                saveLocation(best);
                return;
            }
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location location) {
                    saveLocation(location);
                    try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
                }
            };
            // 这里只需要拿到一次定位；降低轮询频率，避免定位硬件在等待期间持续高功耗工作。
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 20, listener, Looper.getMainLooper());
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 20, listener, Looper.getMainLooper());
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { locationManager.removeUpdates(listener); } catch (SecurityException ignored) {}
            }, 7000);
        } catch (SecurityException ignored) {}
    }

    /**
     * 生成用于界面显示的道路地址。
     *
     * 显示策略：道路优先，POI（学校、幼儿园、小区、商铺等）不作为最终地点名。
     * 例如：良庆镇南宁市良庆区庆林路幼儿园 + thoroughfare=庆林路 + number=14号
     *      -> 南宁市良庆区良庆镇庆林路14号
     *
     * 这样既能保持地址足够精确，又避免把逆地理编码返回的具体 POI 名称直接展示出来。
     */
    private String buildPrecisePlaceName(Address a) {
        String city = clean(a.getLocality());
        String district = clean(a.getSubLocality());
        String feature = clean(a.getFeatureName());
        String road = clean(a.getThoroughfare());
        String number = clean(a.getSubThoroughfare());

        // 某些 Geocoder 会把“镇/街道”放在 featureName 的城市名前面，
        // 例如“良庆镇南宁市良庆区庆林路幼儿园”。道路优先时，
        // 我们只提取其中真正的镇/街道名称，不显示后面的 POI 名称。
        String town = extractTownFromFeaturePrefix(feature, city);

        StringBuilder sb = new StringBuilder();
        appendPlacePart(sb, city);
        appendPlacePart(sb, district);
        appendPlacePart(sb, town);
        appendPlacePart(sb, road);
        appendPlacePart(sb, number);

        // 若逆地理编码只返回到区，没有镇/街道、道路和门牌号，
        // 尝试从 featureName 提取更具体的地标名称（如小区、园区或村落）。
        if (town.isEmpty() && road.isEmpty() && number.isEmpty()) {
            String landmark = extractLandmarkName(feature, city, district, town);
            if (!landmark.isEmpty()) appendPlacePart(sb, landmark);
        }

        if (sb.length() > 0) return sb.toString();
        String normalizedFeature = stripAdministrativePrefix(feature, city, district);
        return !normalizedFeature.isEmpty() ? normalizedFeature : "当前位置";
    }

    /**
     * 从 featureName 中城市名之前的行政前缀提取“镇/街道/乡”等较具体的道路区域。
     * 例如“良庆镇南宁市良庆区庆林路幼儿园” -> “良庆镇”。
     * 不把省、市、区等更高层级行政区带入最终地址。
     */
    private String extractTownFromFeaturePrefix(String feature, String city) {
        String v = clean(feature);
        if (v.isEmpty() || city.isEmpty()) return "";

        int cityIndex = v.indexOf(city);
        if (cityIndex <= 0) return "";

        String prefix = v.substring(0, cityIndex);
        // 取城市名前最后一个“镇/街道/乡/苏木”等行政单元。
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([\u4e00-\u9fff]{2,12}(?:镇|街道|乡|苏木|民族乡))")
                .matcher(prefix);
        String last = "";
        while (m.find()) last = m.group(1);
        return last;
    }


    /** 从 featureName 中提取区县以下的具体地点名，避免只显示到区。 */
    private String extractLandmarkName(String feature, String city, String district, String town) {
        String value = clean(feature);
        if (value.isEmpty()) return "";
        if (!city.isEmpty()) value = value.replace(city, "");
        if (!district.isEmpty()) value = value.replace(district, "");
        if (!town.isEmpty()) value = value.replace(town, "");
        // 去掉常见的省级行政区名称，避免地标前残留冗余行政前缀。
        value = value.replaceAll("(广西壮族自治区|广东省|云南省|贵州省|湖南省|湖北省|江西省|福建省|浙江省|江苏省|安徽省|四川省|重庆市|上海市|北京市|天津市|河北省|河南省|山东省|山西省|陕西省|甘肃省|青海省|海南省|辽宁省|吉林省|黑龙江省|内蒙古自治区|宁夏回族自治区|新疆维吾尔自治区|西藏自治区|香港特别行政区|澳门特别行政区)", "");
        value = value.replaceAll("^[,，、\\s]+|[,，、\\s]+$", "").trim();
        if (value.isEmpty() || isPureHouseNumber(value)) return "";
        if (value.equals(city) || value.equals(district) || value.equals(town)) return "";
        return value;
    }

    private String clean(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) return "";
        return v;
    }


    private String stripAdministrativePrefix(String feature, String city, String district) {
        String v = clean(feature);
        if (v.isEmpty()) return v;

        // 优先从城市名开始保留，例如：
        // “良庆镇南宁市良庆区庆林路幼儿园” -> “南宁市良庆区庆林路幼儿园”。
        boolean cityFound = false;
        if (!city.isEmpty()) {
            int cityIndex = v.indexOf(city);
            if (cityIndex >= 0) {
                cityFound = true;
                if (cityIndex > 0) v = v.substring(cityIndex);
            }
        }

        // 只有完全没有城市名时，才用区县名作为第二兜底，避免把“南宁市良庆区...”
        // 又错误截成“良庆区...”。
        if (!cityFound && !district.isEmpty()) {
            int districtIndex = v.indexOf(district);
            if (districtIndex > 0) v = v.substring(districtIndex);
        }
        return v;
    }

    private boolean containsAdministrativePart(String feature, String city, String district) {
        boolean hasCity = !city.isEmpty() && feature.contains(city);
        boolean hasDistrict = !district.isEmpty() && feature.contains(district);
        return hasCity || hasDistrict;
    }

    private boolean isSamePlacePart(String a, String b) {
        return !clean(a).isEmpty() && clean(a).equals(clean(b));
    }

    private boolean isPureHouseNumber(String value) {
        String v = clean(value);
        return v.matches("^[0-9０-９]+(?:号|號)?$");
    }

    private void appendPlacePart(StringBuilder sb, String value) {
        String v = clean(value);
        if (v.isEmpty()) return;
        if (sb.indexOf(v) >= 0) return;
        // 中文地址不插入空格，直接形成“南宁市良庆区庆林路14号”。
        sb.append(v);
    }

    private void saveLocation(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        new Thread(() -> {
            String place = "当前位置";
            try {
                Geocoder geocoder = new Geocoder(this, Locale.SIMPLIFIED_CHINESE);
                List<Address> list = geocoder.getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    place = buildPrecisePlaceName(a);
                }
            } catch (IOException | RuntimeException ignored) {}
            final String finalPlace = place;
            runOnUiThread(() -> {
                AppConfig.saveLocation(this, lat, lon, finalPlace);
                getSharedPreferences(LOCATION_REFRESH_PREFS, MODE_PRIVATE).edit()
                        .putLong(KEY_LAST_LOCATION_SAVED_AT, System.currentTimeMillis())
                        .apply();
                webView.evaluateJavascript("if(window.refreshWeather){window.refreshWeather();}", null);
            });
        }).start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                if (manualRefreshWaitingForPermission) requestFreshLocationForManualRefresh();
                else updateLocationFromProvider();
            } else {
                if (manualRefreshWaitingForPermission) refreshWeatherUsingSavedLocation();
                Toast.makeText(this, "未开启定位，将使用上次保存的位置", Toast.LENGTH_SHORT).show();
            }
            manualRefreshWaitingForPermission = false;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // 设置页是否保存由 onActivityResult 处理；普通返回/恢复不主动请求天气，
        // 避免未保存退出时触发不必要的数据更新。
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @androidx.annotation.Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SETTINGS) return;
        settingsOpened = false;
        if (resultCode == Activity.RESULT_OK) {
            // 只有明确保存设置后，才更新定位配置并刷新天气。
            requestAutoLocation();
            new Handler(Looper.getMainLooper()).postDelayed(this::refreshWeatherFromResume, 700);
        } else {
            // 未保存退出：不刷新天气。
        }
    }

    private void refreshWeatherFromResume() {
        webView.evaluateJavascript(
                "if(window.refreshWeather){window.refreshWeather();}",
                null
        );
    }

    @Override protected void onDestroy() {
        finishManualLocationRequest();
        if (weatherApiBridge != null) weatherApiBridge.shutdown();
        super.onDestroy();
    }

    public void markSettingsOpened() { settingsOpened = true; }
}
