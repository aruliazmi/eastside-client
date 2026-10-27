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

    /** (database, variant) pairs the data package legitimately does not ship. */
    private static final String[][] MISSING_VARIANT = {
            {"player", "etc"},
            {"mobile", "pvr"},
            {"samp", "pvr"},
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

        String variant = GpuTextureFormat.tagOf(GpuTextureFormat.get(context));
        for (String db : TEXDB) {
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
            // Nothing there at all: only a problem if this variant is expected.
            if (!isVariantMissing(db, variant)) missing.add(base + ".dat");
        }

        boolean isValid = missing.isEmpty() && problems.isEmpty();
        return new VerificationResult(isValid, missing, totalSize, problems);
    }

    private static boolean isVariantMissing(String db, String variant) {
        for (String[] pair : MISSING_VARIANT) {
            if (pair[0].equals(db) && pair[1].equals(variant)) return true;
        }
        return false;
    }
}
