package com.aivo.pubquiz;

import android.content.res.AssetManager;
import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Serves the web app from assets over http://127.0.0.1:PORT/.
 *  localhost is a secure context, so getUserMedia (camera) + fetch work,
 *  and a plain http page may open ws:// (no mixed-content block).
 *
 *  OTA: files downloaded by "Tarkista paivitykset" land in overrideDir and
 *  take precedence over the bundled asset, so small fixes (questions, MP html)
 *  reach every device without rebuilding the APK. */
public class AssetHttpServer extends NanoHTTPD {
    private final AssetManager assets;
    private final File overrideDir;   // filesDir/www ; may not exist yet

    public AssetHttpServer(int port, AssetManager assets, File overrideDir) {
        super("127.0.0.1", port);
        this.assets = assets;
        this.overrideDir = overrideDir;
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null || uri.equals("/")) uri = "/Pubquizmulti/index.html";
        if (uri.startsWith("/")) uri = uri.substring(1);
        int q = uri.indexOf('?'); if (q >= 0) uri = uri.substring(0, q);

        // 1) OTA-updated copy in device storage wins
        try {
            if (overrideDir != null) {
                File f = new File(overrideDir, uri);
                String base = overrideDir.getCanonicalPath();
                if (f.exists() && f.isFile() && f.getCanonicalPath().startsWith(base) && f.length() > 0) {
                    return newChunkedResponse(Response.Status.OK, mime(uri), new FileInputStream(f));
                }
            }
        } catch (Exception ignored) {}

        // 2) bundled asset
        try {
            InputStream is = assets.open("www/" + uri);
            return newChunkedResponse(Response.Status.OK, mime(uri), is);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found: " + uri);
        }
    }

    private static String mime(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js"))   return "application/javascript";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".css"))  return "text/css";
        if (l.endsWith(".png"))  return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".svg"))  return "image/svg+xml";
        if (l.endsWith(".webmanifest") || l.endsWith("manifest.json")) return "application/manifest+json";
        return "application/octet-stream";
    }
}
