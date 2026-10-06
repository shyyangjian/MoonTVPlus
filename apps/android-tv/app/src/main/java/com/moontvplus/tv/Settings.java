package com.moontvplus.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.HttpURLConnection;
import java.net.URL;

public final class Settings {
    private static final String PREFS = "moontv_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_SSL_TRUST_SELF_SIGNED = "trust_self_signed";

    private Settings() {}

    public static String getBaseUrl(Context context) {
        String url = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, null);
        if (url == null || url.trim().isEmpty()) {
            url = BuildConfig.BASE_URL;
        }
        return normalize(url);
    }

    public static void setBaseUrl(Context context, String url) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, normalize(url))
                .apply();
    }

    public static boolean isSslTrustSelfSigned(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SSL_TRUST_SELF_SIGNED, false);
    }

    public static void setSslTrustSelfSigned(Context context, boolean trust) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SSL_TRUST_SELF_SIGNED, trust)
                .apply();
    }

    /** 仅 http(s) 前缀，去末尾斜杠，去掉已带的 /tv */
    public static String normalize(String url) {
        String u = (url == null ? "" : url.trim());
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/tv")) u = u.substring(0, u.length() - 3);
        return u;
    }

    /** 检查服务可达性：GET {base}/api/server-config，不校验内容，只要不是网络错误即视为可达 */
    public static boolean checkReachable(String baseUrl) {
        try {
            URL url = new URL(baseUrl + "/api/server-config");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            // 4xx/5xx 都说明服务在线（可能只是没登录）
            return code >= 200 && code < 600;
        } catch (Exception e) {
            return false;
        }
    }
}
