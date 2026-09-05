package com.aivo.pubquiz;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;

/** Dumb room-scoped relay. No game logic: forwards each message to the other
 *  clients in the same room. The game (host = authority) lives in the WebView. */
public class RelayServer extends WebSocketServer {
    public RelayServer(int port) { super(new InetSocketAddress("0.0.0.0", port)); }

    @Override public void onStart() { setConnectionLostTimeout(30); }
    @Override public void onOpen(WebSocket conn, ClientHandshake h) {}
    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {}
    @Override public void onError(WebSocket conn, Exception ex) {}

    @Override public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject o = new JSONObject(message);
            if (o.has("__join")) { conn.setAttachment(o.getString("__join")); return; }
        } catch (Exception ignored) { /* not a join control message */ }
        Object room = conn.getAttachment();
        for (WebSocket c : getConnections()) {
            if (c != conn && c.isOpen()) {
                Object r = c.getAttachment();
                boolean same = (room == null) ? (r == null) : room.equals(r);
                if (same) { try { c.send(message); } catch (Exception ignored) {} }
            }
        }
    }
}
