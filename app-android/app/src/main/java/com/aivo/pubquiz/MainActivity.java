package com.aivo.pubquiz;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final int PORT = 8787;
    private static final String BASE = "file:///android_asset/www/Pubquizmulti/index.html";
    private WebView web;
    private RelayServer relay;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { relay = new RelayServer(PORT); relay.setReuseAddr(true); relay.start(); } catch (Exception ignored) {}

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setKeepScreenOn(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url != null && url.startsWith("pubquiz://")) { loadFromDeepLink(Uri.parse(url)); return true; }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest req) { req.grant(req.getResources()); }
        });
        web.addJavascriptInterface(new Bridge(), "PQNative");
        WebView.setWebContentsDebuggingEnabled(true);
        setContentView(web);

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
