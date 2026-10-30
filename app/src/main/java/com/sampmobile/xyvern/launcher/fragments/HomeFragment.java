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

    // NOTE: the ASET GAME block (verification status, VERIFIKASI/UNDUH buttons,
    // download progress and the manual path footer) was removed from the home
    // layout. SettingsFragment already performs the same verification through
    // GameDataVerifier and the same repair through GameDataRepair, so the home
    // copy was a duplicate control surface. The fields and the download helper
    // methods that only fed it were deleted with it.

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

    /** getContext() returns null after detach (user leaves the tab mid-download);
     *  Toast.makeText(null,...) is an NPE on a callback that can fire minutes later. */
    private void showToast(CharSequence text, int duration) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), text, duration).show();
    }
}
