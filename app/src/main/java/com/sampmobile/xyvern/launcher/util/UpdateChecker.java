package com.sampmobile.xyvern.launcher.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.sampmobile.xyvern.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Update pipeline backed by GitHub Releases.
 *
 * Source of truth is the "latest" release of {@link #REPO}. The release tag must
 * be semver ("v1.6.5"); it is compared against BuildConfig.VERSION_NAME so the
 * check works even when versionCode is not embedded anywhere else.
 *
 * No third-party HTTP client: the whole thing is HttpURLConnection + org.json,
 * both already on the platform.
 */
public final class UpdateChecker {

    public static final String REPO = "aruliazmi/eastside-client";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + REPO + "/releases/latest";

    /** Populated when a newer release exists. */
    public static final class UpdateInfo {
        public final String tag;
        public final String version;
        public final String notes;
        public final String apkUrl;
        public final long apkSize;

        UpdateInfo(String tag, String version, String notes, String apkUrl, long apkSize) {
            this.tag = tag;
            this.version = version;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
        }
    }

    public interface CheckCallback {
        /** info == null means already up to date. */
        void onResult(UpdateInfo info, String currentVersion);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(int percent, long done, long total);
        void onComplete(File apk);
        void onError(String message);
    }

    private UpdateChecker() {}

    public static String currentVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /** Compares dotted numeric versions. Returns >0 when a is newer than b. */
    static int compareVersions(String a, String b) {
        String[] pa = strip(a).split("\\.");
        String[] pb = strip(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? toInt(pa[i]) : 0;
            int vb = i < pb.length ? toInt(pb[i]) : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }

    private static String strip(String v) {
        if (v == null) return "0";
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);
        return s.isEmpty() ? "0" : s;
    }

    private static int toInt(String s) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
            else break;
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Runs on a caller-supplied background thread. */
    public static void check(CheckCallback cb) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API_LATEST).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "EastsideClient/" + BuildConfig.VERSION_NAME);

            int code = conn.getResponseCode();
            if (code != 200) {
                cb.onError("GitHub membalas HTTP " + code);
                return;
            }

            String body = readAll(conn.getInputStream());
            JSONObject json = new JSONObject(body);

            String tag = json.optString("tag_name", "");
            String notes = json.optString("body", "");
            String version = strip(tag);

            String apkUrl = null;
            long apkSize = 0L;
            JSONArray assets = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String name = a.optString("name", "");
                    if (name.toLowerCase().endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url", null);
                        apkSize = a.optLong("size", 0L);
                        break;
                    }
                }
            }

            if (apkUrl == null) {
                cb.onError("Rilis " + tag + " tidak punya file APK.");
                return;
            }

            String current = BuildConfig.VERSION_NAME;
            if (compareVersions(version, current) > 0) {
                cb.onResult(new UpdateInfo(tag, version, notes, apkUrl, apkSize), current);
            } else {
                cb.onResult(null, current);
            }
        } catch (Exception e) {
            cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Runs on a caller-supplied background thread. Writes into cacheDir. */
    public static void download(Context ctx, UpdateInfo info, DownloadCallback cb) {
        HttpURLConnection conn = null;
        File part = new File(ctx.getCacheDir(), "update.apk.part");
        File out = new File(ctx.getCacheDir(), "update.apk");
        try {
            if (out.exists()) out.delete();
            if (part.exists()) part.delete();

            conn = (HttpURLConnection) new URL(info.apkUrl).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "EastsideClient/" + BuildConfig.VERSION_NAME);

            int code = conn.getResponseCode();
            // GitHub redirects release assets to objects.githubusercontent.com
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) {
                    cb.onError("Redirect tanpa Location.");
                    return;
                }
                conn = (HttpURLConnection) new URL(loc).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);
                code = conn.getResponseCode();
            }
            if (code != 200) {
                cb.onError("Unduhan gagal, HTTP " + code);
                return;
            }

            long total = conn.getContentLengthLong();
            if (total <= 0) total = info.apkSize;

            InputStream in = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(part);
            byte[] buf = new byte[65536];
            long done = 0L;
            int read;
            int lastPercent = -1;
            while ((read = in.read(buf)) != -1) {
                fos.write(buf, 0, read);
                done += read;
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    if (pct != lastPercent) {
                        lastPercent = pct;
                        cb.onProgress(pct, done, total);
                    }
                }
            }
            fos.flush();
            fos.close();
            in.close();

            if (!part.renameTo(out)) {
                cb.onError("Gagal menyelesaikan berkas unduhan.");
                return;
            }
            cb.onComplete(out);
        } catch (Exception e) {
            cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Hands the APK to the system installer. Returns false when the user still
     * needs to grant "install unknown apps" for this package.
     */
    public static boolean install(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Exception ignored) {
            }
            return false;
        }

        Uri uri = FileProvider.getUriForFile(
                activity, activity.getPackageName() + ".provider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(i);
        return true;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        in.close();
        return bos.toString("UTF-8");
    }
}
