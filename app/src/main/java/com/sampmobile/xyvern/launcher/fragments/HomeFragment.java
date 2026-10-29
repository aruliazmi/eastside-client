package com.sampmobile.xyvern.launcher.fragments;

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
import com.sampmobile.xyvern.R;
import com.sampmobile.xyvern.game.SAMP;
import com.sampmobile.xyvern.launcher.MainActivity;
import com.sampmobile.xyvern.launcher.util.GameDataRepair;
import com.sampmobile.xyvern.launcher.util.GameDataVerifier;
import com.sampmobile.xyvern.game.GameDataManager;
import com.sampmobile.xyvern.launcher.util.SampQueryAPI;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Obfuscate
public class HomeFragment extends Fragment {

    // Direct download URL untuk data pack resmi GTA SA / SAMP
    // Di-host di repo sendiri, bukan release repo pihak ketiga.
    // Server tujuan TIDAK lagi hard-coded di sini: lihat ServerStore.
    private static final String DATA_ZIP_URL =
            "https://github.com/aruliazmi/eastside-client/releases/download/data-v3/files.zip";

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
    private TextView tvServerName;
    private TextView tvServerEndpoint;
    private View dotServer;
    private View cardServer;
    private EditText etPlayerNickname;
    private Button btnQuickPlay;

    private final java.util.List<com.sampmobile.xyvern.launcher.data.ServerEntry> servers =
            new java.util.ArrayList<>();
    private com.sampmobile.xyvern.launcher.data.ServerEntry selectedServer;

    private ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler queryHandler;
    private Runnable queryRunnable;
    private boolean isDownloading = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // onDestroyView shuts the executor down, but the fragment instance is
        // reused when the ViewPager recreates this page. Without a fresh pool
        // every later submit throws RejectedExecutionException.
        if (executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(2);
        }

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
        tvServerName = view.findViewById(R.id.tv_server_name);
        tvServerEndpoint = view.findViewById(R.id.tv_server_endpoint);
        dotServer = view.findViewById(R.id.dot_server);
        cardServer = view.findViewById(R.id.card_server);
        etPlayerNickname = view.findViewById(R.id.et_player_nickname);
        btnQuickPlay = view.findViewById(R.id.btn_quick_play);

        // Server list comes from the store; the layout holds no host string.
        servers.clear();
        servers.addAll(com.sampmobile.xyvern.launcher.data.ServerStore.load(requireContext()));
        selectedServer = servers.get(
                com.sampmobile.xyvern.launcher.data.ServerStore.currentIndex(requireContext(), servers));
        bindServerCard();

        if (cardServer != null) {
            cardServer.setOnClickListener(v -> openServerPicker());
        }
        View editNick = view.findViewById(R.id.iv_edit_nickname);
        if (editNick != null && etPlayerNickname != null) {
            editNick.setOnClickListener(v -> {
                etPlayerNickname.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etPlayerNickname, 0);
            });
        }

        View headerSettings = view.findViewById(R.id.iv_header_settings);
        if (headerSettings != null && getActivity() instanceof MainActivity) {
            headerSettings.setOnClickListener(v -> ((MainActivity) getActivity()).changeTab(2));
        }

        Context ctx = requireContext();
        File extDir = ctx.getExternalFilesDir(null);
        if (extDir != null && tvManualPath != null) {
            tvManualPath.setText(extDir.getAbsolutePath());
        }

        // Fresh install: no stored nickname yet -> leave the field empty.
        SharedPreferences prefs = ctx.getSharedPreferences("com.sampmobile.xyvern", Context.MODE_PRIVATE);
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
                intent.putExtra("ip", selectedServer.host);
                intent.putExtra("port", selectedServer.port);
                startActivity(intent);
            });
        }

        runVerification();
        startServerQueryLoop();

        return view;
    }

    private void openServerPicker() {
        if (getContext() == null) return;
        new ServerPickerDialog(requireContext(), servers, entry -> {
            // Re-read the store: the dialog can add, edit or delete entries, so
            // the list it was handed is the current truth, not this fragment's
            // stale copy. Rebuild `servers` before trusting the index.
            servers.clear();
            servers.addAll(com.sampmobile.xyvern.launcher.data.ServerStore.load(requireContext()));
            int idx = com.sampmobile.xyvern.launcher.data.ServerStore.currentIndex(
                    requireContext(), servers);
            selectedServer = servers.get(idx);
            bindServerCard();
            refreshServerStatus();
        }).show();
    }

    /** Paints the server card from {@link #selectedServer}. */
    private void bindServerCard() {
        if (selectedServer == null) return;
        if (tvServerName != null) tvServerName.setText(selectedServer.name);
        if (tvServerEndpoint != null) tvServerEndpoint.setText(selectedServer.endpoint());
        if (tvServerPlayers != null) tvServerPlayers.setText(selectedServer.playersLabel());
        if (tvServerPing != null) tvServerPing.setText(selectedServer.pingLabel());
        applyServerStatus(selectedServer);
    }

    private void applyServerStatus(com.sampmobile.xyvern.launcher.data.ServerEntry entry) {
        if (tvServerStatus == null || dotServer == null) return;
        if (!entry.queried) {
            tvServerStatus.setText("MEMUAT");
            tvServerStatus.setTextColor(0xFF8F8577);
            dotServer.setBackgroundResource(R.drawable.bg_dot_gray);
        } else if (entry.online) {
            tvServerStatus.setText("ONLINE");
            tvServerStatus.setTextColor(0xFF7FA65C);
            dotServer.setBackgroundResource(R.drawable.bg_dot_green);
        } else {
            tvServerStatus.setText("OFFLINE");
            tvServerStatus.setTextColor(0xFFA34A42);
            dotServer.setBackgroundResource(R.drawable.bg_dot_red);
        }
    }

    private void refreshServerStatus() {
        if (selectedServer == null) return;
        final com.sampmobile.xyvern.launcher.data.ServerEntry target = selectedServer;
        executor.execute(() -> {
            long t0 = System.currentTimeMillis();
            boolean ok = false;
            try (SampQueryAPI api = new SampQueryAPI(target.host, target.port)) {
                String[] info = api.mo7164b();
                if (info != null && info.length >= 3) {
                    try {
                        target.players = Integer.parseInt(info[1].trim());
                        target.maxPlayers = Integer.parseInt(info[2].trim());
                    } catch (Exception ignored) {
                    }
                    ok = true;
                }
            } catch (Exception ignored) {
            }
            target.pingMs = (int) (System.currentTimeMillis() - t0);
            target.online = ok;
            target.queried = true;

            mainHandler.post(() -> {
                if (!isAdded() || selectedServer != target) return;
                if (tvServerPlayers != null) tvServerPlayers.setText(target.playersLabel());
                if (tvServerPing != null) tvServerPing.setText(target.pingLabel());
                applyServerStatus(target);
            });
        });
    }

    private void startServerQueryLoop() {
        queryHandler = new Handler(Looper.getMainLooper());
        queryRunnable = new Runnable() {
            @Override
            public void run() {
                refreshServerStatus();
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
        // Server bisa berubah dari tempat lain (Settings, atau dialog yang
        // ditutup tanpa callback). Sinkronkan kartu sebelum query berikutnya.
        if (getContext() != null) {
            servers.clear();
            servers.addAll(com.sampmobile.xyvern.launcher.data.ServerStore.load(requireContext()));
            selectedServer = servers.get(
                    com.sampmobile.xyvern.launcher.data.ServerStore.currentIndex(
                            requireContext(), servers));
            bindServerCard();
        }
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
                long expectedTotal = 1957396608L;
                if (!zipFile.exists() || zipFile.length() != expectedTotal) {
                    mainHandler.post(() -> tvDataDesc.setText("Mengunduh paket aset (~1.8 GB)..."));

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
