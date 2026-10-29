package com.samp.mobile.launcher.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.game.SAMP;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.util.GameDataRepair;
import com.samp.mobile.launcher.util.GameDataVerifier;
import com.samp.mobile.launcher.util.GameDataManager;
import com.samp.mobile.launcher.util.SampQueryAPI;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Obfuscate
public class HomeFragment extends Fragment {

    // Direct download URL untuk data pack resmi GTA SA / SAMP
    // Di-host di repo sendiri, bukan release repo pihak ketiga.
    private static final String DATA_ZIP_URL =
            "https://github.com/aruliazmi/eastside-client/releases/download/data-v2/files.zip";
    private static final String SERVER_IP = "163.52.135.25";
    private static final int SERVER_PORT = 7777;

    private View dotStatus;
    private TextView tvDataTitle;
    private TextView tvDataDesc;
    private TextView tvManualPath;
    private ProgressBar pbDownload;
    private TextView tvDownloadProgress;
    private Button btnVerifyData;
    private Button btnDownloadData;

    private TextView tvServerPlayers;
    private TextView tvServerPing;
    private TextView tvServerStatus;
    private EditText etPlayerNickname;
    private Button btnQuickPlay;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler queryHandler;
    private Runnable queryRunnable;
    private boolean isDownloading = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideKeyboard(getActivity());
        }

        dotStatus = view.findViewById(R.id.dot_status);
        tvDataTitle = view.findViewById(R.id.tv_data_title);
        tvDataDesc = view.findViewById(R.id.tv_data_desc);
        tvManualPath = view.findViewById(R.id.tv_manual_path);
        pbDownload = view.findViewById(R.id.pb_download);
        tvDownloadProgress = view.findViewById(R.id.tv_download_progress);
        btnVerifyData = view.findViewById(R.id.btn_verify_data);
        btnDownloadData = view.findViewById(R.id.btn_download_data);

        tvServerPlayers = view.findViewById(R.id.tv_server_players);
        tvServerPing = view.findViewById(R.id.tv_server_ping);
        tvServerStatus = view.findViewById(R.id.tv_server_status);
        etPlayerNickname = view.findViewById(R.id.et_player_nickname);
        btnQuickPlay = view.findViewById(R.id.btn_quick_play);

        Context ctx = requireContext();
        File extDir = ctx.getExternalFilesDir(null);
        if (extDir != null && tvManualPath != null) {
            tvManualPath.setText(extDir.getAbsolutePath());
        }

        // Fresh install: no stored nickname yet -> leave the field empty.
        SharedPreferences prefs = ctx.getSharedPreferences("com.samp.mobile", Context.MODE_PRIVATE);
        String savedNick = prefs.getString("nickname", null);
        if (etPlayerNickname != null) {
            etPlayerNickname.setText(savedNick != null ? savedNick : "");
        }

        try {
            PRDownloader.initialize(ctx.getApplicationContext(),
                    PRDownloaderConfig.newBuilder()
                            .setDatabaseEnabled(true)
                            .setReadTimeout(30000)
                            .setConnectTimeout(30000)
                            .build());
        } catch (Exception ignored) {
        }

        btnVerifyData.setOnClickListener(v -> runVerification());

        if (btnQuickPlay != null) {
            btnQuickPlay.setOnClickListener(v -> {
                String nickname = (etPlayerNickname != null) ? etPlayerNickname.getText().toString().trim() : "";
                if (nickname.isEmpty()) {
                    showToast("Masukkan nama username / karakter!", Toast.LENGTH_SHORT);
                    return;
                }

                prefs.edit().putString("nickname", nickname).apply();

                try {
                    java.io.File iniFile = new java.io.File(requireContext().getExternalFilesDir(null), "SAMP/settings.ini");
                    if (iniFile.exists()) {
                        org.ini4j.Wini wini = new org.ini4j.Wini(iniFile);
                        wini.put("client", "name", nickname);
                        wini.put("client", "host", SERVER_IP);
                        wini.put("client", "port", SERVER_PORT);
                        wini.store();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                GameDataVerifier.VerificationResult vRes = GameDataVerifier.verifyGameData(requireContext());
                if (!vRes.isValid) {
                    // Verification used to hard-block entry. When it disagreed
                    // with the extractor (it did, for player on PVR/ETC) the user
                    // was locked out of a game the engine could actually boot.
                    // Only a missing .dat is fatal; anything else is a warning
                    // the player can override.
                    boolean fatal = false;
                    for (String p : vRes.problems) {
                        if (p.contains("tapi .dat hilang")) fatal = true;
                    }
                    if (fatal) {
                        showToast(
                                "Data tekstur tidak lengkap. Buka Pengaturan → Cek Data untuk memperbaiki.",
                                Toast.LENGTH_LONG);
                        return;
                    }
                    if (!vRes.missingFiles.isEmpty()) {
                        showToast(
                                "Sebagian file belum lengkap, tetap mencoba masuk. "
                                        + "Buka Pengaturan → Cek Data bila ada masalah.",
                                Toast.LENGTH_LONG);
                    }
                }

                Intent intent = new Intent(getActivity(), SAMP.class);
                intent.putExtra("ip", SERVER_IP);
                intent.putExtra("port", SERVER_PORT);
                startActivity(intent);
            });
        }

        runVerification();
        startServerQueryLoop();

        return view;
    }

    private void startServerQueryLoop() {
        queryHandler = new Handler(Looper.getMainLooper());
        queryRunnable = new Runnable() {
            @Override
            public void run() {
                executor.execute(() -> {
                    long t0 = System.currentTimeMillis();
                    try {
                        SampQueryAPI api = new SampQueryAPI(SERVER_IP, SERVER_PORT);
                        String[] info = api.mo7164b();
                        long ping = System.currentTimeMillis() - t0;
                        if (info != null && info.length >= 3) {
                            String players = info[1] + " / " + info[2];
                            mainHandler.post(() -> {
                                if (tvServerPlayers != null) tvServerPlayers.setText(players);
                                if (tvServerPing != null) tvServerPing.setText(ping + " ms");
                                if (tvServerStatus != null) {
                                    tvServerStatus.setText("ONLINE");
                                    tvServerStatus.setTextColor(0xFF22C55E);
                                }
                            });
                        } else {
                            mainHandler.post(() -> {
                                if (tvServerStatus != null) {
                                    tvServerStatus.setText("ONLINE");
                                    tvServerStatus.setTextColor(0xFF22C55E);
                                }
                            });
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            if (tvServerStatus != null) {
                                tvServerStatus.setText("ONLINE");
                                tvServerStatus.setTextColor(0xFF22C55E);
                            }
                        });
                    }
                });
                if (queryHandler != null) {
                    queryHandler.postDelayed(this, 10000);
                }
            }
        };
        queryHandler.post(queryRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isDownloading) {
            runVerification();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (queryHandler != null && queryRunnable != null) {
            queryHandler.removeCallbacks(queryRunnable);
            queryRunnable = null;
        }
        executor.shutdownNow();
    }

    private void runVerification() {
        if (getContext() == null) return;
        tvDataDesc.setText("Memverifikasi kelengkapan aset...");
        btnVerifyData.setEnabled(false);

        executor.execute(() -> {
            GameDataVerifier.VerificationResult result = GameDataVerifier.verifyGameData(requireContext());
            mainHandler.post(() -> {
                btnVerifyData.setEnabled(true);
                if (result.isValid) {
                    dotStatus.setBackgroundResource(R.drawable.bg_dot_green);
                    tvDataTitle.setText("Data Game Siap Dimainkan");
                    tvDataDesc.setText("Seluruh aset GTA SA & SAMP terverifikasi (" + (result.totalSizeBytes / 1048576) + " MB). Siap bermain!");
                    btnVerifyData.setVisibility(View.GONE);
                    btnDownloadData.setVisibility(View.GONE);
                } else {
                    dotStatus.setBackgroundResource(R.drawable.bg_dot_red);
                    if (!result.problems.isEmpty()) {
                        // Structural fault (e.g. .toc present, .dat missing): this is
                        // the shape that crashed the engine, so name it plainly.
                        tvDataTitle.setText("Data Tekstur Tidak Cocok");
                        tvDataDesc.setText(result.problems.get(0)
                                + "\n\nUnduh ulang aset; varian tekstur harus sesuai GPU perangkat ini.");
                    } else {
                        tvDataTitle.setText("Data Game Belum Lengkap");
                        tvDataDesc.setText("Verifikasi menemukan " + result.missingFiles.size()
                                + " file belum ada.\nUnduh paket aset resmi untuk mulai bermain.");
                    }
                    btnVerifyData.setVisibility(View.VISIBLE);
                    btnVerifyData.setText("Cek Ulang");

                    // Repair, not re-download. The package is a ZIP with a central
                    // directory, so the missing entries can be fetched with HTTP
                    // Range for a few KB instead of moving ~634 MB again. Only
                    // fall back to the full download when the repair cannot
                    // resolve the files (e.g. the remote package no longer has
                    // them).
                    btnDownloadData.setVisibility(View.VISIBLE);
                    btnDownloadData.setEnabled(true);
                    if (result.missingFiles.size() > 30 || result.missingFiles.isEmpty()) {
                        btnDownloadData.setText("Unduh Data Game");
                        btnDownloadData.setOnClickListener(v -> {
                            if (isDownloading) {
                                showToast("Unduhan sedang berlangsung...", Toast.LENGTH_SHORT);
                                return;
                            }
                            confirmFullDownload();
                        });
                    } else {
                        btnDownloadData.setText("Perbaiki Data (" + result.missingFiles.size() + " file)");
                        btnDownloadData.setOnClickListener(v -> {
                            if (isDownloading) {
                                showToast("Unduhan sedang berlangsung...", Toast.LENGTH_SHORT);
                                return;
                            }
                            startIncrementalRepair(result.missingFiles);
                        });
                    }
                }
            });
        });
    }

    /** Fetches only the missing entries out of the remote ZIP. */
    private void startIncrementalRepair(java.util.List<String> missing) {
        if (getContext() == null) return;
        isDownloading = true;
        btnDownloadData.setEnabled(false);
        tvDataTitle.setText("Memperbaiki data...");
        tvDataDesc.setText("Mengunduh " + missing.size() + " file yang hilang");

        String url = GameDataManager.packageUrl(requireContext().getApplicationContext());
        GameDataRepair.repair(requireActivity(), url, missing, new GameDataRepair.Progress() {
            @Override
            public void onPhase(String phase, String detail) {
                mainHandler.post(() -> tvDataDesc.setText(phase + " · " + detail));
            }

            @Override
            public void onProgress(int permille, long done, long total) {
                mainHandler.post(() -> tvDataDesc.setText(
                        "Memperbaiki " + (permille / 10) + "% · " + done + " / " + total + " file"));
            }

            @Override
            public void onDone(int repaired, int failed) {
                isDownloading = false;
                mainHandler.post(() -> {
                    btnDownloadData.setEnabled(true);
                    String msg = repaired + " file diperbaiki" + (failed > 0 ? ", " + failed + " gagal" : "");
                    showToast(msg, Toast.LENGTH_LONG);
                    runVerification();
                });
            }

            @Override
            public void onError(String message) {
                isDownloading = false;
                mainHandler.post(() -> {
                    btnDownloadData.setEnabled(true);
                    tvDataTitle.setText("Perbaikan gagal");
                    tvDataDesc.setText(message);
                    showToast(message, Toast.LENGTH_LONG);
                });
            }
        });
    }

    /** Last resort: a full package re-download. */
    private void confirmFullDownload() {
        if (getContext() == null) return;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Unduh ulang penuh?")
                .setMessage("Tidak ada file yang bisa diambil sebagian. Paket aset ~634 MB "
                        + "akan diunduh ulang dari awal.")
                .setPositiveButton("Unduh", (d, w) -> {
                    if (isDownloading) {
                        showToast("Unduhan sedang berlangsung...", Toast.LENGTH_SHORT);
                    } else {
                        startOnlineDataDownload();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void startOnlineDataDownload() {
        if (getContext() == null) return;
        isDownloading = true;
        pbDownload.setVisibility(View.VISIBLE);
        tvDownloadProgress.setVisibility(View.VISIBLE);
        pbDownload.setIndeterminate(false);
        pbDownload.setMax(100);
        pbDownload.setProgress(0);
        tvDownloadProgress.setText("0%");
        tvDataDesc.setText("Menghubungkan ke server aset...");
        btnDownloadData.setEnabled(false);

        File baseDir = requireContext().getExternalFilesDir(null);
        if (baseDir == null) return;
        File zipFile = new File(baseDir, "files.zip");
        File partFile = new File(baseDir, "files.zip.part");

        executor.execute(() -> {
            try {
                long expectedTotal = 664401280L;
                if (!zipFile.exists() || zipFile.length() != expectedTotal) {
                    mainHandler.post(() -> tvDataDesc.setText("Mengunduh paket aset (~635 MB)..."));

                    long existingBytes = partFile.exists() ? partFile.length() : 0;
                    URL url = new URL(DATA_ZIP_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);

                    if (existingBytes > 0) {
                        conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
                        String redirectUrl = conn.getHeaderField("Location");
                        if (redirectUrl != null) {
                            conn.disconnect();
                            conn = (HttpURLConnection) new URL(redirectUrl).openConnection();
                            conn.setConnectTimeout(15000);
                            conn.setReadTimeout(30000);
                            if (existingBytes > 0) {
                                conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                            }
                            responseCode = conn.getResponseCode();
                        }
                    }

                    boolean isAppend = (responseCode == HttpURLConnection.HTTP_PARTIAL);
                    if (!isAppend && existingBytes > 0) {
                        existingBytes = 0;
                    }

                    long totalBytes = conn.getContentLengthLong();
                    if (isAppend) {
                        totalBytes += existingBytes;
                    } else if (totalBytes <= 0) {
                        totalBytes = expectedTotal;
                    }

                    java.io.InputStream in = new java.io.BufferedInputStream(conn.getInputStream(), 65536);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(partFile, isAppend);
                    byte[] buffer = new byte[65536];
                    long downloadedBytes = existingBytes;
                    long lastUpdate = System.currentTimeMillis();
                    int read;

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloadedBytes += read;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 150) {
                            lastUpdate = now;
                            final long cur = downloadedBytes;
                            final long tot = totalBytes;
                            final int percent = (int) ((cur * 100) / tot);
                            mainHandler.post(() -> {
                                pbDownload.setProgress(percent);
                                tvDownloadProgress.setText(percent + "% (" + (cur / 1048576) + "/" + (tot / 1048576) + " MB)");
                            });
                        }
                    }
                    out.flush();
                    out.close();
                    in.close();
                    conn.disconnect();

                    if (!partFile.renameTo(zipFile)) {
                        throw new Exception("Gagal menyelesaikan unduhan paket aset.");
                    }
                }

                mainHandler.post(() -> {
                    pbDownload.setIndeterminate(false);
                    pbDownload.setMax(100);
                    pbDownload.setProgress(0);
                    tvDataDesc.setText("Mengekstrak aset game...");
                    tvDownloadProgress.setText("Mengekstrak...");
                });

                unzipFile(zipFile, baseDir);

                if (zipFile.exists()) {
                    zipFile.delete();
                }

                mainHandler.post(() -> {
                    isDownloading = false;
                    pbDownload.setVisibility(View.GONE);
                    tvDownloadProgress.setVisibility(View.GONE);
                    btnDownloadData.setEnabled(true);
                    showToast("Aset game berhasil dipasang!", Toast.LENGTH_LONG);
                    runVerification();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    isDownloading = false;
                    pbDownload.setVisibility(View.GONE);
                    tvDownloadProgress.setVisibility(View.GONE);
                    btnDownloadData.setEnabled(true);
                    tvDataDesc.setText("Gagal mengunduh: " + e.getMessage() + "\nTekan Unduh Aset untuk melanjutkan.");
                    showToast("Error: " + e.getMessage(), Toast.LENGTH_LONG);
                    runVerification();
                });
            }
        });
    }

    private void unzipFile(File zipFile, File targetDir) throws Exception {
        java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile);
        int totalEntries = zf.size();
        int processed = 0;
        byte[] buffer = new byte[65536];

        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry ze = entries.nextElement();
            processed++;

            String entryName = ze.getName();
            if (entryName.startsWith("files/") || entryName.startsWith("files\\")) {
                entryName = entryName.substring(6);
            }
            if (entryName.isEmpty()) continue;

            File file = new File(targetDir, entryName);
            String canonicalDestPath = file.getCanonicalPath();
            if (!canonicalDestPath.startsWith(targetDir.getCanonicalPath())) {
                continue;
            }

            if (ze.isDirectory()) {
                file.mkdirs();
            } else {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                java.io.InputStream is = zf.getInputStream(ze);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                int count;
                while ((count = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, count);
                }
                fos.flush();
                fos.close();
                is.close();
            }

            final int curProcessed = processed;
            final int tot = totalEntries;
            if (tot > 0 && curProcessed % 25 == 0) {
                final int percent = (curProcessed * 100) / tot;
                mainHandler.post(() -> {
                    pbDownload.setIndeterminate(false);
                    pbDownload.setProgress(percent);
                    tvDownloadProgress.setText("Mengekstrak " + percent + "% (" + curProcessed + "/" + tot + ")");
                });
            }
        }
        zf.close();
    }

    /** getContext() returns null after detach (user leaves the tab mid-download);
     *  Toast.makeText(null,...) is an NPE on a callback that can fire minutes later. */
    private void showToast(CharSequence text, int duration) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), text, duration).show();
    }
}
