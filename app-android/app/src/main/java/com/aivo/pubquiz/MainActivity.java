package com.aivo.pubquiz;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final int PORT = 8787;
    // Served over https via WebViewAssetLoader -> secure origin (camera + fetch work).
    private static final String BASE =
            "https://appassets.androidplatform.net/assets/www/Pubquizmulti/index.html";
    private WebView web;
    private RelayServer relay;
    private WebViewAssetLoader assetLoader;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { relay = new RelayServer(PORT); relay.setReuseAddr(true); relay.start(); } catch (Exception ignored) {}

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        web.setKeepScreenOn(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return assetLoader.shouldInterceptRequest(req.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u != null && "pubquiz".equals(u.getScheme())) { loadFromDeepLink(u); return true; }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest r) { r.grant(r.getResources()); }
        });
        web.addJavascriptInterface(new Bridge(), "PQNative");
        WebView.setWebContentsDebuggingEnabled(true);
        setContentView(web);

        try {
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{ Manifest.permission.CAMERA }, 1);
            }
        } catch (Exception ignored) {}

        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null && "pubquiz".equals(data.getScheme())) loadFromDeepLink(data);
        else web.loadUrl(BASE);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "pubquiz".equals(data.getScheme())) loadFromDeepLink(data);
    }

    private void loadFromDeepLink(Uri u) {
        String ip = u.getQueryParameter("ip");
        String port = u.getQueryParameter("port"); if (port == null) port = String.valueOf(PORT);
        String room = u.getQueryParameter("room");
        String url = BASE + "?relay=" + Uri.encode("ws://" + ip + ":" + port)
                + (room != null ? "&room=" + Uri.encode(room) : "");
        web.loadUrl(url);
    }

    public class Bridge {
        @JavascriptInterface public String wifiIp() { return getWifiIp(); }
        @JavascriptInterface public int relayPort() { return PORT; }
        @JavascriptInterface public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || ms <= 0) return;
                if (android.os.Build.VERSION.SDK_INT >= 26)
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(ms);
            } catch (Exception ignored) {}
        }
    }

    static String getWifiIp() {
        try {
            List<NetworkInterface> ifs = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : ifs) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a.isSiteLocalAddress() && a.getHostAddress().indexOf(':') < 0) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (relay != null) relay.stop(); } catch (Exception ignored) {}
    }
}
