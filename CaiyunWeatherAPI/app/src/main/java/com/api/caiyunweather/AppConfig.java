package com.api.caiyunweather;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {
    private static final String PREF = "weather_config";
    private static final String KEY_API = "api_key";
    private static final String KEY_BASE = "api_base";
    private static final String KEY_AUTO = "auto_location";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_PLACE = "place";
    private static final String KEY_REFRESH = "refresh_min";

    // 不在 APK 中内置用户的 API Key、经纬度或地点。首次使用请在“设置”中填写。
    public static final String DEFAULT_API_KEY = "";
    public static final String DEFAULT_API_BASE = "https://api.caiyunapp.com/v2.6";
    public static final double DEFAULT_LON = 0.0;
    public static final double DEFAULT_LAT = 0.0;
    public static final String DEFAULT_PLACE = "未设置位置";
    public static final int DEFAULT_REFRESH_MIN = 5;

    private AppConfig() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String getApiKey(Context c) { return p(c).getString(KEY_API, DEFAULT_API_KEY); }
    public static String getApiBase(Context c) { return p(c).getString(KEY_BASE, DEFAULT_API_BASE); }
    public static boolean isAutoLocation(Context c) { return p(c).getBoolean(KEY_AUTO, true); }
    public static double getLat(Context c) { return Double.longBitsToDouble(p(c).getLong(KEY_LAT, Double.doubleToLongBits(DEFAULT_LAT))); }
    public static double getLon(Context c) { return Double.longBitsToDouble(p(c).getLong(KEY_LON, Double.doubleToLongBits(DEFAULT_LON))); }
    public static String getPlace(Context c) { return p(c).getString(KEY_PLACE, DEFAULT_PLACE); }
    public static int getRefreshMin(Context c) { return p(c).getInt(KEY_REFRESH, DEFAULT_REFRESH_MIN); }

    public static void save(Context c, String key, String base, boolean auto, int refreshMin) {
        p(c).edit()
                .putString(KEY_API, key.trim())
                .putString(KEY_BASE, base.trim())
                .putBoolean(KEY_AUTO, auto)
                .putInt(KEY_REFRESH, refreshMin)
                .apply();
    }

    public static void saveLocation(Context c, double lat, double lon, String place) {
        p(c).edit().putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .putString(KEY_PLACE, place == null || place.isEmpty() ? "当前位置" : place)
                .apply();
    }

    public static boolean hasManualLocation(Context c) {
        return getLat(c) >= -90 && getLat(c) <= 90
                && getLon(c) >= -180 && getLon(c) <= 180
                && !(getLat(c) == 0.0 && getLon(c) == 0.0);
    }
}
