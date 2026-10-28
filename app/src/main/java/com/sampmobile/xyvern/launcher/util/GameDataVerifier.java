package com.sampmobile.xyvern.launcher.util;

import android.content.Context;

import com.sampmobile.xyvern.game.GpuTextureFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GameDataVerifier {

    /**
     * Texture databases the game boots. Each ships a .dat plus a .toc/.tmb pair
     * for the GPU variant. The old list checked "texdb/gta3.img", which the
     * published package does not contain at all — verification therefore always
     * reported "incomplete" and never protected against anything real.
     */
    private static final String[] TEXDB = {
            "gta3", "gta_int", "menu", "mobile", "player", "samp", "txd"
    };

    /** Non-texture files required for the engine to boot. */
    public static final String[] ESSENTIAL_FILES = {
            "data/gta.dat",
            "data/surface.dat",
            "data/fonts.dat",
            "data/handling.cfg"
    };

    public static class VerificationResult {
        public final boolean isValid;
        public final List<String> missingFiles;
        public final long totalSizeBytes;
        /** Structural faults that make the engine crash rather than merely error. */
        public final List<String> problems;

        public VerificationResult(boolean isValid, List<String> missingFiles,
                                  long totalSizeBytes, List<String> problems) {
            this.isValid = isValid;
            this.missingFiles = missingFiles;
            this.totalSizeBytes = totalSizeBytes;
            this.problems = problems;
        }
    }

    public static VerificationResult verifyGameData(Context context) {
        File baseDir = context.getExternalFilesDir(null);
        List<String> missing = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        if (baseDir == null || !baseDir.exists()) {
            for (String f : ESSENTIAL_FILES) missing.add(f);
            missing.add("texdb/");
            return new VerificationResult(false, missing, 0, problems);
        }

        long totalSize = 0;
        for (String rel : ESSENTIAL_FILES) {
            File f = new File(baseDir, rel);
            if (!f.exists() || f.length() == 0) missing.add(rel);
            else totalSize += f.length();
        }

        int gpuFmt = GpuTextureFormat.get(context);
        for (String db : TEXDB) {
            // Same rule the extractor used. Previously this kept "pvr" for
            // player on a PVR device while the extractor wrote DXT, so a healthy
            // install was reported as missing player.etc.dat forever.
            String variant = GpuTextureFormat.variantFor(db, gpuFmt);
            String base = "texdb/" + db + "/" + db + "." + variant;
            File dat = new File(baseDir, base + ".dat");
            File toc = new File(baseDir, base + ".toc");
            File tmb = new File(baseDir, base + ".tmb");

            // A .toc/.tmb with no .dat is exactly what made Mali devices crash:
            // the engine seeks into a file that is not there.
            boolean tocOrTmb = toc.exists() || tmb.exists();
            boolean datOk = dat.exists() && dat.length() > 0;

            if (tocOrTmb && !datOk) {
                problems.add(db + "." + variant + ": .toc/.tmb ada tapi .dat hilang");
                continue;
            }
            if (datOk) {
                totalSize += dat.length();
                if (!toc.exists()) missing.add(base + ".toc");
                if (!tmb.exists()) missing.add(base + ".tmb");
                continue;
            }
            // Nothing there at all: that database did not ship this variant.
            missing.add(base + ".dat");
        }

        boolean isValid = missing.isEmpty() && problems.isEmpty();
        return new VerificationResult(isValid, missing, totalSize, problems);
    }

    /** One-line status for the Settings row. */
    public static String summary(VerificationResult r) {
        if (r.isValid) {
            return "Semua file inti dan tekstur tersedia";
        }
        int n = r.missingFiles.size();
        int p = r.problems.size();
        StringBuilder b = new StringBuilder();
        if (n > 0) b.append(n).append(" file hilang");
        if (p > 0) {
            if (b.length() > 0) b.append(", ");
            b.append(p).append(" bermasalah");
        }
        return b.toString();
    }

    /** Human-readable detail used by the repair confirmation dialog. */
    public static String describe(VerificationResult r) {
        StringBuilder b = new StringBuilder();
        if (!r.problems.isEmpty()) {
            b.append("Bermasalah:\n");
            for (int i = 0; i < Math.min(5, r.problems.size()); i++) {
                b.append("  • ").append(r.problems.get(i)).append('\n');
            }
        }
        if (!r.missingFiles.isEmpty()) {
            b.append("Hilang:\n");
            for (int i = 0; i < Math.min(5, r.missingFiles.size()); i++) {
                b.append("  • ").append(r.missingFiles.get(i)).append('\n');
            }
            if (r.missingFiles.size() > 5) {
                b.append("  … dan ").append(r.missingFiles.size() - 5).append(" lainnya\n");
            }
        }
        return b.toString().trim();
    }
}
