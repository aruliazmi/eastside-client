package com.sampmobile.xyvern.game;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the AML mod payload into the directory AML scans at startup.
 *
 * AML reads {@code <external files>/mods/} (see LoadMods in AML's main.cpp) and
 * dlopen()s every .so it finds there, then calls __GetModInfo on it. CLEO and
 * MonetLoader are both AML mods, not standalone libraries: they only run when
 * AML loads them, which is why they live here instead of in jniLibs.
 *
 * The copies are refreshed whenever the bundled asset version changes, so a
 * launcher update replaces stale mods without leaving orphans behind.
 */
public final class AmlModInstaller {

    private static final String TAG = "AmlModInstaller";
    private static final String ASSET_DIR = "amlmods";
    private static final String MODS_DIR = "mods";
    private static final String PREFS = "aml_mods";
    private static final String KEY_VERSION = "installed_version";

    private AmlModInstaller() {
    }

    /** Copies the bundled AML mods into files/mods/ when the build changed. */
    public static void sync(Context ctx, String buildVersion) {
        try {
            File root = ctx.getExternalFilesDir(null);
            if (root == null) return;

            String installed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_VERSION, "");
            File modsDir = new File(root, MODS_DIR);

            if (buildVersion.equals(installed) && modsDir.isDirectory()) {
                return;
            }

            if (!modsDir.exists() && !modsDir.mkdirs()) {
                Log.w(TAG, "cannot create " + modsDir);
                return;
            }

            String[] assets = ctx.getAssets().list(ASSET_DIR);
            if (assets == null || assets.length == 0) {
                Log.i(TAG, "no bundled AML mods");
                return;
            }

            int copied = 0;
            for (String name : assets) {
                if (!name.endsWith(".so")) continue;
                File out = new File(modsDir, name);
                try (InputStream in = ctx.getAssets().open(ASSET_DIR + "/" + name);
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                }
                // AML chmods before dlopen; make sure the copy is executable.
                //noinspection ResultOfMethodCallIgnored
                out.setReadable(true, false);
                //noinspection ResultOfMethodCallIgnored
                out.setExecutable(true, false);
                copied++;
            }

            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_VERSION, buildVersion).apply();
            Log.i(TAG, "installed " + copied + " AML mods into " + modsDir);
        } catch (Exception e) {
            Log.e(TAG, "sync failed", e);
        }
    }
}
