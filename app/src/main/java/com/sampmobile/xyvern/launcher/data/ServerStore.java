package com.sampmobile.xyvern.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent server list plus the currently selected server.
 *
 * Two keys in the existing "com.sampmobile.xyvern" preference file:
 *   SERVERS        JSON array of user-visible servers
 *   CURRENT_SERVER index into that array
 *
 * The list starts with the built-in server so an upgrade never lands the player
 * on an empty screen, but nothing else in the app hard-codes a host any more.
 */
public final class ServerStore {

    private static final String PREFS = "com.sampmobile.xyvern";
    private static final String KEY_LIST = "SERVERS";
    private static final String KEY_CURRENT = "CURRENT_SERVER";

    /** Seeds the list on first run. Keep in sync with config/app.json. */
    public static final String DEFAULT_HOST = "163.52.135.25";
    public static final int DEFAULT_PORT = 7777;
    public static final String DEFAULT_NAME = "Eastside Stories";
    public static final String DEFAULT_TAGLINE = "Open.MP · Roleplay · Madiun";

    private ServerStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<ServerEntry> load(Context ctx) {
        List<ServerEntry> list = ServerEntry.listFromJson(
                prefs(ctx).getString(KEY_LIST, null));
        if (list.isEmpty()) {
            list = new ArrayList<>();
            list.add(new ServerEntry(DEFAULT_NAME, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_TAGLINE));
            save(ctx, list);
        }
        return list;
    }

    public static void save(Context ctx, List<ServerEntry> list) {
        prefs(ctx).edit().putString(KEY_LIST, ServerEntry.listToJson(list)).apply();
    }

    public static int currentIndex(Context ctx, List<ServerEntry> list) {
        int idx = prefs(ctx).getInt(KEY_CURRENT, 0);
        if (idx < 0 || idx >= list.size()) idx = 0;
        return idx;
    }

    public static void setCurrentIndex(Context ctx, int idx) {
        prefs(ctx).edit().putInt(KEY_CURRENT, Math.max(0, idx)).apply();
    }

    /** The selected server, or the built-in one if the list is somehow empty. */
    public static ServerEntry current(Context ctx) {
        List<ServerEntry> list = load(ctx);
        return list.get(currentIndex(ctx, list));
    }

    public static void addOrUpdate(Context ctx, ServerEntry entry) {
        List<ServerEntry> list = load(ctx);
        for (int i = 0; i < list.size(); i++) {
            ServerEntry s = list.get(i);
            if (s.host.equalsIgnoreCase(entry.host) && s.port == entry.port) {
                list.set(i, entry);
                save(ctx, list);
                return;
            }
        }
        list.add(entry);
        save(ctx, list);
    }

    public static void remove(Context ctx, int index) {
        List<ServerEntry> list = load(ctx);
        if (index < 0 || index >= list.size()) return;
        // Never leave the player with an empty list.
        if (list.size() == 1) return;
        list.remove(index);
        save(ctx, list);
        int cur = prefs(ctx).getInt(KEY_CURRENT, 0);
        if (cur >= list.size()) setCurrentIndex(ctx, list.size() - 1);
    }
}
