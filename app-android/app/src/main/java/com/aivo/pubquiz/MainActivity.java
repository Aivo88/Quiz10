package com.aivo.pubquiz;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.media.AudioAttributes;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    static final int RELAY_PORT = 8787;   // WebSocket relay (guests connect here)
    static final int HTTP_PORT  = 8788;   // local asset server for our own WebView
    private static final String BASE = "http://127.0.0.1:" + HTTP_PORT + "/Pubquizmulti/index.html";

    private WebView web;
    private RelayServer relay;
    private AssetHttpServer http;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { relay = new RelayServer(RELAY_PORT); relay.setReuseAddr(true); relay.start(); } catch (Exception ignored) {}
        try { http = new AssetHttpServer(HTTP_PORT, getAssets()); http.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false); } catch (Exception ignored) {}

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setKeepScreenOn(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u != null && "pubquiz".equals(u.getScheme())) { loadFromDeepLink(u); return true; }
                return false;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(() -> r.grant(r.getResources()));
            }
        });
        web.addJavascriptInterface(new Bridge(), "PQNative");
        WebView.setWebContentsDebuggingEnabled(true);
        setContentView(web);

        try {
            if (Build.VERSION.SDK_INT >= 23
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
        String port = u.getQueryParameter("port"); if (port == null) port = String.valueOf(RELAY_PORT);
        String room = u.getQueryParameter("room");
        String url = BASE + "?relay=" + Uri.encode("ws://" + ip + ":" + port)
                + (room != null ? "&room=" + Uri.encode(room) : "");
        web.loadUrl(url);
    }

    public class Bridge {
        @JavascriptInterface public String wifiIp() { return getWifiIp(); }
        @JavascriptInterface public int relayPort() { return RELAY_PORT; }
        @JavascriptInterface public void vibrate(int ms) { doVibrate(ms, false); }
        @JavascriptInterface public void vibrateTest() { doVibrate(400, true); }
        @JavascriptInterface public void scanQr() {
            runOnUiThread(() -> {
                try {
                    GmsBarcodeScannerOptions opts = new GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
                    GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(MainActivity.this, opts);
                    scanner.startScan()
                        .addOnSuccessListener(bc -> {
                            String raw = bc.getRawValue();
                            if (raw != null) web.evaluateJavascript(
                                "window.__onScan && window.__onScan(" + JSONObject.quote(raw) + ")", null);
                        })
                        .addOnFailureListener(e -> {})
                        .addOnCanceledListener(() -> {});
                } catch (Exception ignored) {}
            });
        }
    }

    private Vibrator getVib() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                return vm != null ? vm.getDefaultVibrator() : null;
            }
            return (Vibrator) getSystemService(VIBRATOR_SERVICE);
        } catch (Exception e) { return null; }
    }
    private void doVibrate(final int ms, final boolean toast) {
        try {
            if (ms <= 0) return;
            final Vibrator v = getVib();
            final boolean has = (v != null && v.hasVibrator());
            if (toast) runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "vibrate " + ms + (has ? " (has vibrator)" : " (NO vibrator!)"), Toast.LENGTH_SHORT).show());
            if (v == null) return;
            AudioAttributes aa = null;
            try {
                aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            } catch (Exception ignored) {}
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    // waveform: buzz - pause - buzz, full amplitude (255) -> hard for MIUI to swallow
                    long[] pat = new long[]{ 0, ms, 90, ms };
                    int[] amp = new int[]{ 0, 255, 0, 255 };
                    VibrationEffect eff = VibrationEffect.createWaveform(pat, amp, -1);
                    if (aa != null) v.vibrate(eff, aa); else v.vibrate(eff);
                } else {
                    v.vibrate(new long[]{ 0, ms, 90, ms }, -1);
                }
            } catch (Exception e) {
                try { v.vibrate(ms); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
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
        try { if (http != null) http.stop(); } catch (Exception ignored) {}
    }
}
