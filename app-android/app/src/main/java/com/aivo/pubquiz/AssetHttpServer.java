package com.aivo.pubquiz;

import android.content.res.AssetManager;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.io.InputStream;

/** Serves the bundled web app from assets over http://127.0.0.1:PORT/.
 *  localhost is a secure context, so getUserMedia (camera) + fetch work,
 *  and a plain http page may open ws:// (no mixed-content block). */
public class AssetHttpServer extends NanoHTTPD {
    private final AssetManager assets;
    public AssetHttpServer(int port, AssetManager assets) {
        super("127.0.0.1", port);
        this.assets = assets;
    }
    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null || uri.equals("/")) uri = "/Pubquizmulti/index.html";
        if (uri.startsWith("/")) uri = uri.substring(1);
        String path = "www/" + uri;
        try {
            InputStream is = assets.open(path);
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
