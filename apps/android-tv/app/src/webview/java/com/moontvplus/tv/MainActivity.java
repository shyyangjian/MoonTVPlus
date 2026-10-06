package com.moontvplus.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.net.URLEncoder;

public class MainActivity extends Activity implements RemoteCommandHandler {
    private FrameLayout root;
    private WebView webView;
    private android.widget.TextView logOverlay;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private LocalRemoteServer localRemoteServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        // 1. 硬件加速（TV 视频播放几乎必开）
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        root = new FrameLayout(this);
        setContentView(root);
        setupLogOverlay();
        setupWebView();
        setupLocalRemoteServer();
        String baseUrl = Settings.getBaseUrl(this);
        if (baseUrl.isEmpty()) {
            startActivity(new android.content.Intent(this, SetupActivity.class));
            return;
        }
        webView.loadUrl(withLocalRemoteHash(buildTvUrl(baseUrl)));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        // 焦点保持：一旦 WebView 拿到焦点就锁住，避免 DPAD 时焦点漂移到系统层
        webView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.postDelayed(() -> v.requestFocus(), 100);
                }
            }
        });
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.addJavascriptInterface(new LocalRemoteBridge(), "MoonTVLocalRemote");
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // 关键修复：锁 viewport 为 1080p，让 CSS 渲染和电视屏幕对齐
        // 不锁的话 WebView 按默认 980px 宽渲染，页面被缩小到左上角，遥控焦点坐标全错
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        // 注入 1920 宽 CSS viewport（1080p TV 标准），页面按 1920px 设计并等比缩放填满
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        settings.setUserAgentString(settings.getUserAgentString() + " MoonTVPlusAndroidTV WebView");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                logDiag("onReceivedError " + request.getUrl() + " code=" + error.getErrorCode() + " " + error.getDescription());
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                logDiag("onReceivedHttpError " + request.getUrl() + " status=" + response.getStatusCode());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                logDiag("pageStarted " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                logDiag("pageFinished " + url);
                injectLocalRemoteInfo();
                injectWebViewDiagnostics();
                injectLockedViewport();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 默认严格校验；仅用户显式开启"信任自签证书"（私有 CA 环境）才放行
                if (Settings.isSslTrustSelfSigned(MainActivity.this)) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });
    }



    private class LocalRemoteBridge {
        @JavascriptInterface
        public String getRemoteUrl() {
            return localRemoteServer == null ? "" : String.valueOf(localRemoteServer.getRemoteUrl());
        }

        @JavascriptInterface
        public int getPort() {
            return localRemoteServer == null ? -1 : localRemoteServer.getPort();
        }

    }

    private void injectLocalRemoteInfo() {
        if (webView == null || localRemoteServer == null) return;
        String url = localRemoteServer.getRemoteUrl();
        if (url == null) return;
        String safeUrl = url.replace("\\", "\\\\").replace("'", "\\'");
        String script = "window.__MOONTV_LOCAL_REMOTE_URL='" + safeUrl + "';" +
                "window.dispatchEvent(new CustomEvent('moontv:local-remote-info',{detail:{url:'" + safeUrl + "'}}));";
        webView.evaluateJavascript(script, null);
    }

    private void setupLocalRemoteServer() {
        localRemoteServer = new LocalRemoteServer(this);
        localRemoteServer.start();
    }

    private void setupLogOverlay() {
        logOverlay = new android.widget.TextView(this);
        logOverlay.setBackgroundColor(0xCC000000);
        logOverlay.setTextColor(0xFFFFFFFF);
        logOverlay.setTextSize(10);
        logOverlay.setPadding(16, 8, 16, 8);
        logOverlay.setVisibility(View.GONE);
        logOverlay.setMaxLines(4);
        logOverlay.setEllipsize(android.text.TextUtils.TruncateAt.START);
        root.addView(logOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private final java.util.LinkedList<String> logQueue = new java.util.LinkedList<String>();

    private void logDiag(final String msg) {
        logQueue.addLast(msg);
        while (logQueue.size() > 6) logQueue.removeFirst();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (logOverlay == null) return;
                StringBuilder sb = new StringBuilder();
                for (String s : logQueue) {
                    sb.append(s).append("\n");
                }
                logOverlay.setText(sb.toString().trim());
                logOverlay.setVisibility(View.VISIBLE);
            }
        });
    }

    private void injectLockedViewport() {
        if (webView == null) return;
        // 强制 CSS viewport 为 1920px 宽，让页面按 1080p 设计并填满屏幕
        String script = "(function(){" +
            "var d=document;" +
            "if(!d.querySelector('meta[name=viewport]')){" +
                "var m=d.createElement('meta');m.name='viewport';" +
                "m.content='width=1920,initial-scale=1,user-scalable=no';" +
                "d.head.appendChild(m);" +
            "}" +
            "d.documentElement.style.width='1920px';" +
            "d.body.style.width='1920px';" +
            "d.body.style.minWidth='1920px';" +
            "d.body.style.height='1080px';" +
            "d.body.style.overflow='hidden';" +
            "})()";
        webView.evaluateJavascript(script, null);
    }

    private void injectWebViewDiagnostics() {
        if (webView == null) return;
        String script = "(function(){" +
            "var ua=navigator.userAgent;" +
            "var v=ua.match(/Chrome\\/([0-9]+)\\.|Android WebView/);" +
            "var chromeVer=v?ua.split('Chrome/')[1].split(' ')[0]:'?';" +
            "var hasMedia='MediaSource' in window;" +
            "var hasHls='Hls' in window;" +
            "var hasWebCodecs='WebCodecs' in window;" +
            "var hasWasm=('WebAssembly' in window);" +
            "var gpu='webgpu' in window;" +
            "document.title='[TV:'+chromeVer+' MS:'+hasMedia+' WC:'+hasWebCodecs+' WASM:'+hasWasm+' GPU:'+gpu+' '+document.title];'" +
            "})();" +
            "document.title";
        webView.evaluateJavascript(script, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                logDiag("diag " + value);
            }
        });
    }

    private int keyCodeForRemoteKey(String key, String digit) {
        if ("up".equals(key)) return KeyEvent.KEYCODE_DPAD_UP;
        if ("down".equals(key)) return KeyEvent.KEYCODE_DPAD_DOWN;
        if ("left".equals(key)) return KeyEvent.KEYCODE_DPAD_LEFT;
        if ("right".equals(key)) return KeyEvent.KEYCODE_DPAD_RIGHT;
        if ("ok".equals(key)) return KeyEvent.KEYCODE_DPAD_CENTER;
        if ("back".equals(key)) return KeyEvent.KEYCODE_BACK;
        if ("menu".equals(key)) return KeyEvent.KEYCODE_MENU;
        if ("home".equals(key)) return KeyEvent.KEYCODE_HOME;
        if ("playPause".equals(key)) return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if ("pageUp".equals(key)) return KeyEvent.KEYCODE_PAGE_UP;
        if ("pageDown".equals(key)) return KeyEvent.KEYCODE_PAGE_DOWN;
        if ("digit".equals(key) && digit != null && digit.length() == 1 && digit.charAt(0) >= '0' && digit.charAt(0) <= '9') {
            return KeyEvent.KEYCODE_0 + (digit.charAt(0) - '0');
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }


    private void dispatchLocalRemoteKey(String key, boolean repeat, String digit) {
        if (webView == null || key == null) return;
        String safeKey = key.replace("\\", "\\\\").replace("'", "\\'");
        String safeDigit = digit == null ? "" : digit.replace("\\", "\\\\").replace("'", "\\'");
        String script = "window.dispatchEvent(new CustomEvent('moontv:local-remote-key',{detail:{key:'"
                + safeKey + "',repeat:" + (repeat ? "true" : "false") + ",digit:'" + safeDigit + "'}}));";
        webView.evaluateJavascript(script, null);
    }

    @Override
    public void onRemoteKey(String key, boolean repeat, String digit) {
        mainHandler.post(() -> dispatchLocalRemoteKey(key, repeat, digit));
    }

    @Override
    public void onRemoteText(String mode, String text) {
        mainHandler.post(() -> {
            if (webView == null) return;
            String safeMode = mode == null ? "replace" : mode.replace("\\", "\\\\").replace("'", "\\'");
            String safeText = text == null ? "" : text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
            String script = "window.dispatchEvent(new CustomEvent('moontv:local-remote-text',{detail:{mode:'" + safeMode + "',text:'" + safeText + "'}}));";
            webView.evaluateJavascript(script, null);
        });
    }


    private String withLocalRemoteHash(String url) {
        String remoteUrl = localRemoteServer == null ? null : localRemoteServer.getRemoteUrl();
        if (remoteUrl == null || remoteUrl.isEmpty()) return url;
        try {
            return url + "#localRemoteUrl=" + URLEncoder.encode(remoteUrl, "UTF-8");
        } catch (Exception ignored) {
            return url;
        }
    }

    private static String buildTvUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        if (url.isEmpty()) {
            url = "https://ltv.860527.xyz:88";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/tv")) {
            return url;
        }
        return url + "/tv";
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN) {
            // 长按 MENU 1.5s：进入设置页（改地址/证书开关）
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (localRemoteServer != null) localRemoteServer.stop();
                    startActivity(new android.content.Intent(MainActivity.this, SetupActivity.class));
                    finish();
                }
            }, 1500L);
            return true;
        }
        // 遥控器方向键/OK/返回/数字键：WebView 焦点时直接转发给页面内部焦点元素
        if (webView != null && webView.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_0:
                case KeyEvent.KEYCODE_1:
                case KeyEvent.KEYCODE_2:
                case KeyEvent.KEYCODE_3:
                case KeyEvent.KEYCODE_4:
                case KeyEvent.KEYCODE_5:
                case KeyEvent.KEYCODE_6:
                case KeyEvent.KEYCODE_7:
                case KeyEvent.KEYCODE_8:
                case KeyEvent.KEYCODE_9:
                    return webView.dispatchKeyEvent(event);
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (webView != null && webView.hasFocus()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_0:
                case KeyEvent.KEYCODE_1:
                case KeyEvent.KEYCODE_2:
                case KeyEvent.KEYCODE_3:
                case KeyEvent.KEYCODE_4:
                case KeyEvent.KEYCODE_5:
                case KeyEvent.KEYCODE_6:
                case KeyEvent.KEYCODE_7:
                case KeyEvent.KEYCODE_8:
                case KeyEvent.KEYCODE_9:
                    return webView.dispatchKeyEvent(event);
                default:
                    break;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && webView != null) {
            // 窗口重新获得焦点（如全屏切换、返回键）时，强制焦点回到 WebView 页面
            webView.requestFocusFromTouch();
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
        if (localRemoteServer != null) {
            localRemoteServer.stop();
            localRemoteServer = null;
        }
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
