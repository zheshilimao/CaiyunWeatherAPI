package com.api.caiyunweather;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WeatherApiBridge {
    private final Activity activity;
    private final WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean requesting = new AtomicBoolean(false);

    public WeatherApiBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public boolean requestWeather() {
        if (!requesting.compareAndSet(false, true)) return true;
        final String apiKey = AppConfig.getApiKey(activity).trim();
        String base = AppConfig.getApiBase(activity).trim();
        if (base.startsWith("http://")) {
            base = "https://" + base.substring("http://".length());
        } else if (!base.startsWith("https://")) {
            base = "https://" + base;
        }
        base = base.replaceAll("/+$", "");
        final String apiBase = base;
        final double lon = AppConfig.getLon(activity);
        final double lat = AppConfig.getLat(activity);

        if (apiKey.isEmpty()) {
            finishError("API Key 为空");
            return false;
        }
        if (Double.isNaN(lon) || Double.isInfinite(lon) || Double.isNaN(lat) || Double.isInfinite(lat) || lon < -180 || lon > 180 || lat < -90 || lat > 90 || (lon == 0 && lat == 0)) {
            finishError("经纬度无效");
            return false;
        }
        if (!apiBase.startsWith("https://")) {
            finishError("API 地址必须使用 HTTPS");
            return false;
        }

        executor.execute(() -> {
            String lastError = "网络请求失败";
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpURLConnection conn = null;
                try {
                    String urlText = apiBase + "/" + apiKey + "/" + lon + "," + lat
                            + "/weather?dailysteps=3&hourlysteps=48&lang=zh_CN";
                    URL url = new URL(urlText);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setUseCaches(false);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("User-Agent", "CaiyunWeatherAPI/1.3.4 Android");

                    int code = conn.getResponseCode();
                    InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    String body = readAll(stream);
                    if (code == 200 && body != null && !body.isEmpty()) {
                        JSONObject json = new JSONObject(body);
                        if ("ok".equalsIgnoreCase(json.optString("status"))) {
                            finishSuccess(body);
                            return;
                        }
                        String apiError = json.optString("error", "API 返回失败");
                        lastError = "API 返回失败：" + apiError;
                        if (code >= 400 && code < 500 && code != 429) break;
                    } else {
                        lastError = "HTTP " + code + (body == null || body.isEmpty() ? "" : "：" + body);
                        if (code >= 400 && code < 500 && code != 429) break;
                    }
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? "网络请求失败" : e.getMessage();
                } finally {
                    if (conn != null) conn.disconnect();
                }
                if (attempt == 0) {
                    try { Thread.sleep(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
            finishError(lastError);
        });
        return true;
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }

    private void finishSuccess(String json) {
        activity.runOnUiThread(() -> {
            requesting.set(false);
            String quoted = JSONObject.quote(json);
            webView.evaluateJavascript("window.onNativeWeatherResult && window.onNativeWeatherResult(" + quoted + ");", null);
        });
    }

    private void finishError(String message) {
        activity.runOnUiThread(() -> {
            requesting.set(false);
            webView.evaluateJavascript("window.onNativeWeatherError && window.onNativeWeatherError(" + JSONObject.quote(message) + ");", null);
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
