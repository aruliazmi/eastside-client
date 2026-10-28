package com.sampmobile.xyvern.game;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Detects which texture-compression family the GPU can decode.
 *
 * GTA:SA ships its texture databases in three variants and its engine picks one
 * at startup from the GL extension list. The data package ships all three, but
 * not every database has all three (notably `player` has no ETC and `samp.pvr`
 * is missing its .dat). Extracting everything and letting the engine pick is how
 * a Mali device ends up reading a variant that does not exist — the parser then
 * walks past the end of the buffer and dies in
 * RwPluginRegistry::ReadDataChunks.
 *
 * So the variant is resolved once, stored, and used to filter extraction.
 */
public final class GpuTextureFormat {

    private static final String TAG = "GpuTextureFormat";
    private static final String PREFS = "com.sampmobile.xyvern";
    private static final String KEY = "texture_format";

    /** Matches TextureDatabaseFormat order in the engine. */
    public static final int FMT_DXT = 1;
    public static final int FMT_PVR = 4;
    public static final int FMT_ETC = 5;

    private GpuTextureFormat() {}

    /** "dxt" / "pvr" / "etc" — the on-disk suffix segment. */
    public static String tagOf(int fmt) {
        switch (fmt) {
            case FMT_PVR: return "pvr";
            case FMT_ETC: return "etc";
            default:      return "dxt";
        }
    }

    /**
     * Databases whose upstream package only ever ships DXT and PVR.
     * player.etc has never been published, so an ETC device has no exact match
     * and falls back to DXT - the only variant that is complete for every
     * database in the package.
     */
    private static final java.util.Set<String> DXT_PVR_ONLY =
            new java.util.HashSet<>(java.util.Arrays.asList("player", "playerhi"));

    /**
     * The variant that is actually on disk for {@code db} given the detected GPU
     * format. Both the extractor and the verifier MUST use this: they drifted
     * once, and verification then demanded player.etc.dat - a file that does not
     * exist anywhere - so a healthy install was reported as broken and the user
     * was pushed into a full 634 MB re-download.
     */
    public static String variantFor(String db, int fmt) {
        String gpu = tagOf(fmt);
        if (DXT_PVR_ONLY.contains(db)) {
            return ("pvr".equals(gpu) || "dxt".equals(gpu)) ? gpu : "dxt";
        }
        return gpu;
    }

    public static int get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY, 0);
    }

    public static void set(Context ctx, int fmt) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY, fmt).apply();
    }

    /**
     * Resolves the format from GL extensions. Must run before the game starts,
     * because the engine reads its own copy during renderer init.
     */
    public static int detect(Activity activity) {
        int cached = get(activity);
        if (cached != 0) return cached;

        final int[] result = {0};
        final CountDownLatch latch = new CountDownLatch(1);

        // A 1x1 GLSurfaceView is enough: we only need the extension string.
        final GLSurfaceView probe = new GLSurfaceView(activity);
        probe.setEGLContextClientVersion(2);
        probe.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                try {
                    String ext = gl.glGetString(GL10.GL_EXTENSIONS);
                    String renderer = gl.glGetString(GL10.GL_RENDERER);
                    result[0] = classify(ext, renderer);
                } catch (Throwable t) {
                    Log.w(TAG, "GL probe failed: " + t);
                } finally {
                    latch.countDown();
                }
            }

            @Override public void onSurfaceChanged(GL10 gl, int w, int h) {}
            @Override public void onDrawFrame(GL10 gl) {}
        });

        activity.runOnUiThread(() -> {
            try {
                activity.addContentView(probe, new android.view.ViewGroup.LayoutParams(1, 1));
            } catch (Throwable t) {
                Log.w(TAG, "cannot attach probe: " + t);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "GL probe timed out, defaulting to DXT");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        activity.runOnUiThread(() -> {
            try {
                android.view.ViewParent p = probe.getParent();
                if (p instanceof android.view.ViewGroup)
                    ((android.view.ViewGroup) p).removeView(probe);
            } catch (Throwable ignored) {}
        });

        int fmt = result[0] != 0 ? result[0] : FMT_DXT;
        set(activity, fmt);
        Log.i(TAG, "texture format = " + tagOf(fmt));
        return fmt;
    }

    /** Pure classification so it can be unit-tested without a GL context. */
    static int classify(String extensions, String renderer) {
        String ext = extensions == null ? "" : extensions.toUpperCase();
        String rnd = renderer == null ? "" : renderer.toUpperCase();

        // PowerVR first: it advertises PVRTC and nothing else useful here.
        if (ext.contains("GL_IMG_TEXTURE_COMPRESSION_PVRTC")) return FMT_PVR;

        // DXT/S3TC: Adreno, and most desktop-class / emulated GL.
        if (ext.contains("GL_EXT_TEXTURE_COMPRESSION_S3TC")
                || ext.contains("GL_EXT_TEXTURE_COMPRESSION_DXT1")
                || ext.contains("GL_AMD_COMPRESSED_ATC_TEXTURE")) {
            return FMT_DXT;
        }

        // Mali and other mobile GPUs typically expose only ETC1/ETC2.
        if (ext.contains("GL_OES_COMPRESSED_ETC1_RGB8")
                || ext.contains("GL_OES_COMPRESSED_ETC2_RGB8")
                || ext.contains("GL_AMD_COMPRESSED_3DC_TEXTURE")
                || rnd.contains("MALI")
                || rnd.contains("POWERVR")) {
            return FMT_ETC;
        }

        // Unknown: DXT is the most complete variant in the data package.
        return FMT_DXT;
    }
}
