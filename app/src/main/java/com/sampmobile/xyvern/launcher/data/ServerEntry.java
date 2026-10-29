package com.sampmobile.xyvern.launcher.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A server the player can join.
 *
 * The launcher used to be hard-wired to a single host: SERVER_IP/SERVER_PORT in
 * HomeFragment, a literal in fragment_home.xml, another literal in SAMP.java and
 * yet another in ConfigValidator's settings.ini template. Adding a second server
 * meant editing four files. This type is the single source of truth instead.
 */
public final class ServerEntry {

    public String name;
    public String host;
    public int port;
    /** Optional one-line description shown under the name. */
    public String tagline;

    /* ---- transient status, filled in by the query loop; never persisted ---- */
    public boolean online;
    public int players;
    public int maxPlayers;
    public int pingMs;
    public boolean queried;

    public ServerEntry(String name, String host, int port, String tagline) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.tagline = tagline;
    }

    public String endpoint() {
        return host + ":" + port;
    }

    public String playersLabel() {
        if (!queried) return "-- / --";
        if (!online) return "offline";
        return players + " / " + maxPlayers;
    }

    public String pingLabel() {
        if (!queried || !online) return "-- ms";
        return pingMs + " ms";
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("host", host);
        o.put("port", port);
        if (tagline != null) o.put("tagline", tagline);
        return o;
    }

    public static ServerEntry fromJson(JSONObject o) {
        String host = o.optString("host", "");
        if (host.isEmpty()) return null;
        int port = o.optInt("port", 7777);
        if (port <= 0 || port > 65535) port = 7777;
        String name = o.optString("name", host + ":" + port);
        String tagline = o.optString("tagline", "");
        return new ServerEntry(name, host, port, tagline);
    }

    public static String listToJson(List<ServerEntry> list) {
        JSONArray arr = new JSONArray();
        for (ServerEntry s : list) {
            try {
                arr.put(s.toJson());
            } catch (Exception ignored) {
            }
        }
        JSONObject root = new JSONObject();
        try {
            root.put("servers", arr);
        } catch (Exception ignored) {
        }
        return root.toString();
    }

    public static List<ServerEntry> listFromJson(String raw) {
        List<ServerEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONObject(raw).optJSONArray("servers");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                ServerEntry e = fromJson(arr.getJSONObject(i));
                if (e != null) out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
