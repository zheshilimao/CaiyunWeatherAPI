package com.api.caiyunweather;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;

public class ConfigBridge {
    private final Activity activity;

    public ConfigBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface public String getApiKey() { return AppConfig.getApiKey(activity); }
    @JavascriptInterface public String getApiBase() { return AppConfig.getApiBase(activity); }
    @JavascriptInterface public String getLocation() {
        return AppConfig.getLon(activity) + "," + AppConfig.getLat(activity);
    }
    @JavascriptInterface public String getPlaceName() { return AppConfig.getPlace(activity); }
    @JavascriptInterface public boolean isAutoLocation() { return AppConfig.isAutoLocation(activity); }
    @JavascriptInterface public int getRefreshMin() { return AppConfig.getRefreshMin(activity); }

    @JavascriptInterface public int getStatusBarInsetDp() {
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id == 0) return 0;
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(activity.getResources().getDimensionPixelSize(id) / density);
    }

    @JavascriptInterface public void refreshFinished() {
        ((MainActivity) activity).stopRefreshing();
    }

    @JavascriptInterface public void openSettings() {
        activity.runOnUiThread(() -> {
            ((MainActivity) activity).markSettingsOpened();
            activity.startActivity(new Intent(activity, SettingsActivity.class));
        });
    }
}
