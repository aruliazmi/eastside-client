package com.samp.mobile.launcher.util;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Repairs an incomplete game-data install without re-downloading the whole
 * package.
 *
 * The published package is a single ~634 MB ZIP, and a plain re-download costs
 * the user the entire file even when only one texture database is missing. This
 * class exploits two facts:
 *
 *   1. A ZIP keeps its central directory at the end, so individual entries can
 *      be fetched with HTTP Range requests instead of reading the whole archive.
 *   2. An entry stored with ZIP_STORED (or deflated) can be inflated from just
 *      its local header + compressed bytes, which is what a ranged fetch gives.
 *
 * The tail of the archive (central directory) is a few hundred KB, so the cost
 * of resolving entries is negligible next to the package itself.
 */
public final class GameDataRepair {

    private static final String TAG = "GameDataRepair";

    /** Read the archive tail once; the central directory must sit inside it. */
    private static final int TAIL_BYTES = 2 * 1024 * 1024;

    public interface Progress {
        void onPhase(String phase, String detail);
        void onProgress(int permille, long done, long total);
        void onDone(int repaired, int failed);
        void onError(String message);
    }

    private GameDataRepair() {
    }

    /** One entry resolved from the remote central directory. */
    private static final class RemoteEntry {
        final String name;
        final long compressedSize;
        final long localHeaderOffset;
        final int method;

        RemoteEntry(String name, long compressedSize, long localHeaderOffset, int method) {
            this.name = name;
            this.compressedSize = compressedSize;
            this.localHeaderOffset = localHeaderOffset;
            this.method = method;
        }
    }

    /**
     * Repairs every path listed in {@code missing} by fetching just those entries
     * out of the remote package.
     *
     * @param missing   paths relative to the game root, as reported by
     *                  {@link GameDataVerifier.VerificationResult#missingFiles}
     * @param packageUrl the same URL the launcher would download for a full install
     */
    public static void repair(Activity activity, String packageUrl,
                              List<String> missing, Progress cb) {
        new Thread(() -> {
            try {
                File root = activity.getExternalFilesDir(null);
                if (root == null) {
                    cb.onError("Penyimpanan tidak tersedia.");
                    return;
                }
                if (missing == null || missing.isEmpty()) {
                    cb.onDone(0, 0);
                    return;
                }

                cb.onPhase("Menyiapkan", "Membaca indeks paket...");

                URL url = new URL(packageUrl);
                long totalSize = contentLength(url);
                if (totalSize <= 0) {
                    cb.onError("Server tidak memberi ukuran paket.");
                    return;
                }

                byte[] tail = fetchRange(url, Math.max(0, totalSize - TAIL_BYTES), totalSize - 1);
                long tailStart = Math.max(0, totalSize - TAIL_BYTES);
                List<RemoteEntry> entries = parseCentralDirectory(tail, tailStart, totalSize);
                if (entries.isEmpty()) {
                    cb.onError("Indeks paket tidak terbaca. Perlu unduh ulang penuh.");
                    return;
                }

                // The zip may wrap everything in one top-level folder ("files/").
                // Verification reports paths without that prefix, so match both.
                Set<String> wanted = new HashSet<>(missing);
                List<RemoteEntry> toFetch = new ArrayList<>();
                for (RemoteEntry e : entries) {
                    if (e.name.endsWith("/")) continue;
                    String bare = stripWrappingFolder(e.name);
                    if (wanted.contains(bare) || wanted.contains(e.name)) toFetch.add(e);
                }

                if (toFetch.isEmpty()) {
                    cb.onError("File yang hilang tidak ada di paket. Perlu unduh ulang penuh.");
                    return;
                }

                int repaired = 0;
                int failed = 0;
                long done = 0;
                for (RemoteEntry e : toFetch) {
                    String bare = stripWrappingFolder(e.name);
                    cb.onPhase("Memperbaiki", bare);

                    try {
                        File out = new File(root, bare);
                        File parent = out.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();

                        byte[] raw = fetchEntryBytes(url, e);
                        if (raw == null) {
                            failed++;
                            continue;
                        }
                        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                            os.write(raw);
                        }
                        repaired++;
                        Log.i(TAG, "repaired " + bare + " (" + raw.length + " bytes)");
                    } catch (Exception ex) {
                        Log.w(TAG, "failed " + bare + ": " + ex.getMessage());
                        failed++;
                    }

                    done++;
                    cb.onProgress((int) Math.min(1000, done * 1000 / toFetch.size()), done, toFetch.size());
                }

                cb.onDone(repaired, failed);
            } catch (Exception ex) {
                Log.e(TAG, "repair failed", ex);
                cb.onError(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
        }, "game-data-repair").start();
    }

    /** Removes a single wrapping folder such as "files/" so paths match the game root. */
    private static String stripWrappingFolder(String name) {
        int slash = name.indexOf('/');
        if (slash < 0 || slash == name.length() - 1) return name;
        String first = name.substring(0, slash);
        // Only treat it as a wrapper when every sibling shares it; here we only
        // see one name, so require the well-known wrapper used by the package.
        return ("files".equals(first)) ? name.substring(slash + 1) : name;
    }

    private static long contentLength(URL url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "M-SAMP-Launcher");
            int code = c.getResponseCode();
            if (code != 200 && code != 206) return -1;
            return c.getContentLengthLong();
        } finally {
            c.disconnect();
        }
    }

    private static byte[] fetchRange(URL url, long from, long to) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setRequestProperty("Range", "bytes=" + from + "-" + to);
            c.setRequestProperty("User-Agent", "M-SAMP-Launcher");
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code != 206 && code != 200) throw new Exception("HTTP " + code);
            try (InputStream in = new BufferedInputStream(c.getInputStream())) {
                return readAll(in);
            }
        } finally {
            c.disconnect();
        }
    }

    /** Local header + compressed payload for one entry. */
    private static byte[] fetchEntryBytes(URL url, RemoteEntry e) throws Exception {
        // A local file header is 30 bytes plus the variable name/extra fields.
        // Fetch a generous slab and walk the header to find where data starts.
        int probe = 1024;
        byte[] head = fetchRange(url, e.localHeaderOffset, e.localHeaderOffset + probe - 1);
        if (head.length < 30) return null;

        int nameLen = u16(head, 26);
        int extraLen = u16(head, 28);
        int dataStart = 30 + nameLen + extraLen;
        if (dataStart > head.length) return null;

        long dataOffset = e.localHeaderOffset + dataStart;
        byte[] compressed = fetchRange(url, dataOffset, dataOffset + e.compressedSize - 1);
        if (compressed.length != e.compressedSize) return null;

        if (e.method == 0) return compressed;               // ZIP_STORED
        if (e.method == 8) return inflateRaw(compressed);   // ZIP_DEFLATED
        throw new Exception("metode kompresi tidak didukung: " + e.method);
    }

    private static byte[] inflateRaw(byte[] deflated) throws Exception {
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
        try {
            inf.setInput(deflated);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(
                    Math.max(64, deflated.length * 3));
            byte[] buf = new byte[64 * 1024];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break;
                } else {
                    out.write(buf, 0, n);
                }
            }
            return out.toByteArray();
        } finally {
            inf.end();
        }
    }

    /**
     * Walks the ZIP central directory that lives in the fetched tail.
     * Offsets inside the directory are absolute, which is why {@code tailStart}
     * must be subtracted before indexing into the tail buffer.
     */
    private static List<RemoteEntry> parseCentralDirectory(byte[] tail, long tailStart, long totalSize)
            throws Exception {
        List<RemoteEntry> out = new ArrayList<>();
        int eocd = -1;
        for (int i = tail.length - 22; i >= 0; i--) {
            if (tail[i] == 'P' && tail[i + 1] == 'K' && tail[i + 2] == 5 && tail[i + 3] == 6) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) return out;

        long cdSize = u32(tail, eocd + 12);
        long cdOffset = u32(tail, eocd + 16);

        // The central directory must be fully inside the tail we fetched.
        if (cdOffset < tailStart) return out;
        int p = (int) (cdOffset - tailStart);
        long end = Math.min(p + cdSize, tail.length);

        while (p + 46 <= end) {
            if (!(tail[p] == 'P' && tail[p + 1] == 'K' && tail[p + 2] == 1 && tail[p + 3] == 2)) break;
            int method = u16(tail, p + 10);
            long compSize = u32(tail, p + 20);
            int nameLen = u16(tail, p + 28);
            int extraLen = u16(tail, p + 30);
            int commentLen = u16(tail, p + 32);
            long localOff = u32(tail, p + 42);

            if (p + 46 + nameLen > tail.length) break;
            String name = new String(tail, p + 46, nameLen, StandardCharsets.UTF_8);
            out.add(new RemoteEntry(name, compSize, localOff, method));
            p += 46 + nameLen + extraLen + commentLen;
        }
        return out;
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static long u32(byte[] b, int off) {
        return (b[off] & 0xffL)
                | ((b[off + 1] & 0xffL) << 8)
                | ((b[off + 2] & 0xffL) << 16)
                | ((b[off + 3] & 0xffL) << 24);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
