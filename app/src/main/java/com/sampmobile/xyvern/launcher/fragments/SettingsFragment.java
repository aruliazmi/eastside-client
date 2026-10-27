package com.sampmobile.xyvern.launcher.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;
import com.sampmobile.xyvern.R;
import com.sampmobile.xyvern.launcher.MainActivity;
import com.sampmobile.xyvern.launcher.util.SharedPreferenceCore;
import com.sampmobile.xyvern.launcher.util.UpdateChecker;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Obfuscate
public class SettingsFragment extends Fragment {

    Wini mWini = null;
    EditText mNickName;
    SwitchCompat mKeyboardSwitch;
    SwitchCompat mVoiceSwitch;
    SwitchCompat mModifySwitch;
    SwitchCompat mAMLSwitch;
    SwitchCompat mCefSwitch;
    SwitchCompat mFPSSwitch;
    SeekBar mMessagesSeekBar;
    TextView mMessagesText;
    SeekBar mFPSSeekBar;
    TextView mFPSText;

    // Versi aplikasi + pembaruan dari GitHub Releases
    private TextView tvClientVersion;
    private TextView tvUpdateStatus;
    private TextView tvUpdateProgress;
    private android.widget.Button btnCheckUpdate;
    private ProgressBar pbUpdate;
    private View layoutUpdateProgress;
    private final ExecutorService updater = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private UpdateChecker.UpdateInfo mPendingUpdate;


    String[] titles = {"0.3.DL-R1", "0.3.7", "0.3.7-R5"};

    Spinner autoCompleteTextView;
    ArrayAdapter<String> adapter;

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View view = layoutInflater.inflate(R.layout.fragment_settings, viewGroup, false);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideKeyboard(getActivity());
        }

        // Ánh xạ các thành phần giao diện
        mNickName = view.findViewById(R.id.settings_nickname);
        autoCompleteTextView = view.findViewById(R.id.spinner);
        mKeyboardSwitch = view.findViewById(R.id.keyboard_switch);
        mFPSSwitch = view.findViewById(R.id.fps_switch);
        mVoiceSwitch = view.findViewById(R.id.voice_switch);
        mModifySwitch = view.findViewById(R.id.modify_switch);
        mAMLSwitch = view.findViewById(R.id.aml_switch);
        mAMLSwitch.setChecked(requireContext()
            .getSharedPreferences("com.sampmobile.xyvern", android.content.Context.MODE_PRIVATE)
            .getBoolean("aml_enabled", false));
        mCefSwitch = view.findViewById(R.id.cef_switch);
        mMessagesSeekBar = view.findViewById(R.id.messages_seekbar);
        mMessagesText = view.findViewById(R.id.messages_count);
        mFPSSeekBar = view.findViewById(R.id.fps_seekbar);
        mFPSText = view.findViewById(R.id.fps_count);

        tvClientVersion   = view.findViewById(R.id.tv_client_version);
        tvUpdateStatus    = view.findViewById(R.id.tv_update_status);
        tvUpdateProgress  = view.findViewById(R.id.tv_update_progress);
        btnCheckUpdate    = view.findViewById(R.id.btn_check_update);
        pbUpdate          = view.findViewById(R.id.pb_update);
        layoutUpdateProgress = view.findViewById(R.id.layout_update_progress);

        tvClientVersion.setText(UpdateChecker.currentVersion());
        btnCheckUpdate.setOnClickListener(v -> onCheckUpdateClicked());


        adapter = new ArrayAdapter<String>(getActivity(), R.layout.spinner_item, titles);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        autoCompleteTextView.setAdapter(adapter);

        // Migrasi sekali: petakan nilai VERSION lama (0-4) ke skema baru (0-1).
        // 4 (dulu R5) -> 1, nilai > 1 lainnya -> 0. Disimpan permanen.
        SharedPreferenceCore versionPrefs = new SharedPreferenceCore();
        int rawVersion = versionPrefs.getInt(requireContext().getApplicationContext(), "VERSION", 0);
        if (rawVersion > 1) {
            versionPrefs.setInt(requireContext().getApplicationContext(), "VERSION", rawVersion == 4 ? 1 : 0);
        }

        File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
        try {
            com.sampmobile.xyvern.launcher.util.ConfigValidator.validateConfigFiles(getActivity());
            mWini = new Wini(file);

            // The launcher pref is the source of truth for the player-visible name.
            // A fresh install has no pref yet, so the field stays empty instead of
            // inheriting whatever placeholder the engine wrote into settings.ini.
            android.content.SharedPreferences nickPrefs = requireContext()
                    .getSharedPreferences("com.sampmobile.xyvern", android.content.Context.MODE_PRIVATE);
            String nn = nickPrefs.contains("nickname")
                    ? nickPrefs.getString("nickname", "")
                    : "";
            if (nn == null) nn = "";
            mNickName.setText(nn);

            mWini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }

        autoCompleteTextView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "VERSION", position);
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if (file.exists()) {
                    try {
                        if (mWini != null) {
                            mWini.put("client", "version", titles[new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "VERSION")]);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        mNickName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String text = charSequence.toString();
                if (getActivity() != null) {
                    android.content.SharedPreferences sp = getActivity().getSharedPreferences("com.sampmobile.xyvern", android.content.Context.MODE_PRIVATE);
                    sp.edit().putString("nickname", text).apply();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        mModifySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "MODIFIED_DATA", b);
            }
        });

        mAMLSwitch.setOnCheckedChangeListener((buttonView, enabled) -> {
            requireContext().getSharedPreferences("com.sampmobile.xyvern", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("aml_enabled", enabled)
                .apply();
            Toast.makeText(requireContext(), "AML setting saved. Restart the game process to apply.", Toast.LENGTH_LONG).show();
        });

        mCefSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "CEF_ENABLED", b);
                Toast.makeText(requireContext(), b ? "CEF WebView diaktifkan" : "CEF WebView dimatikan. Restart game untuk menerapkan.", Toast.LENGTH_LONG).show();
            }
        });

        mKeyboardSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "ANDROID_KEYBOARD", b);
                try {
                    if (mWini != null) {
                        mWini.put("gui", "androidkeyboard", b);
                        mWini.store();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mVoiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "VOICE_CHAT", b);
                try {
                    if (mWini != null) {
                        mWini.put("gui", "VoiceChatEnable", b);
                        mWini.store();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mFPSSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                new SharedPreferenceCore().setBoolean(requireContext().getApplicationContext(), "FPS_DISPLAY", b);
                try {
                    if (mWini != null) {
                        mWini.put("gui", "fps", b ? 1 : 0);
                        mWini.store();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mMessagesSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int realProgress = 0;
                switch (progress) {
                    case 0: realProgress = 6; break;
                    case 1: realProgress = 9; break;
                    case 2: realProgress = 12; break;
                    case 3: realProgress = 15; break;
                }
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "MESSAGE_COUNT", realProgress);
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if (file.exists()) {
                    try {
                        if (mWini != null) {
                            mWini.put("gui", "ChatMaxMessages", realProgress);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mMessagesText.setText(String.valueOf(realProgress));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mFPSSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int realProgress = 0;
                switch (progress) {
                    case 0: realProgress = 30; break;
                    case 1: realProgress = 60; break;
                    case 2: realProgress = 90; break;
                    case 3: realProgress = 120; break;
                }
                new SharedPreferenceCore().setInt(requireContext().getApplicationContext(), "FPS_LIMIT", realProgress);
                File file = new File(getActivity().getExternalFilesDir(null) + "/SAMP/settings.ini");
                if (file.exists()) {
                    try {
                        if (mWini != null) {
                            mWini.put("gui", "FPSLimit", realProgress);
                            mWini.store();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mFPSText.setText(String.valueOf(realProgress));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        mKeyboardSwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "ANDROID_KEYBOARD"));
        mVoiceSwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "VOICE_CHAT"));
        mFPSSwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "FPS_DISPLAY"));
        mModifySwitch.setChecked(new SharedPreferenceCore().getBoolean(requireContext().getApplicationContext(), "MODIFIED_DATA"));
        mAMLSwitch.setChecked(requireContext().getSharedPreferences("com.sampmobile.xyvern", android.content.Context.MODE_PRIVATE)
            .getBoolean("aml_enabled", false));
        // CEF default ON; only OFF when the user explicitly disabled it before.
        android.content.SharedPreferences cefPrefs = requireContext().getApplicationContext()
                .getSharedPreferences(com.sampmobile.xyvern.launcher.util.SharedPreferenceCore.APP_PREFERENCES, 0);
        boolean cefOn = !cefPrefs.contains("CEF_ENABLED") || cefPrefs.getBoolean("CEF_ENABLED", true);
        mCefSwitch.setChecked(cefOn);
        autoCompleteTextView.setSelection(new SharedPreferenceCore().getInt(requireContext().getApplicationContext(), "VERSION"));

        int fps = new SharedPreferenceCore().getInt(getContext(), "FPS_LIMIT");
        switch (fps) {
            case 30: mFPSSeekBar.setProgress(0); break;
            case 60: mFPSSeekBar.setProgress(1); break;
            case 90: mFPSSeekBar.setProgress(2); break;
            case 120: mFPSSeekBar.setProgress(3); break;
        }
        mFPSText.setText(String.valueOf(fps));

        int message = new SharedPreferenceCore().getInt(getContext(), "MESSAGE_COUNT");
        switch (message) {
            case 6: mMessagesSeekBar.setProgress(0); break;
            case 9: mMessagesSeekBar.setProgress(1); break;
            case 12: mMessagesSeekBar.setProgress(2); break;
            case 15: mMessagesSeekBar.setProgress(3); break;
        }
        mMessagesText.setText(String.valueOf(message));
    }

    // ==========================================================
    // Pembaruan aplikasi (GitHub Releases)
    // ==========================================================

    private void onCheckUpdateClicked() {
        // Kalau sudah ada hasil cek sebelumnya, tombol berubah jadi "Pasang".
        if (mPendingUpdate != null) {
            promptInstall(mPendingUpdate);
            return;
        }

        btnCheckUpdate.setEnabled(false);
        tvUpdateStatus.setText("Menghubungi GitHub...");
        updater.execute(() -> UpdateChecker.check(new UpdateChecker.CheckCallback() {
            @Override
            public void onResult(UpdateChecker.UpdateInfo info, String currentVersion) {
                ui.post(() -> {
                    if (!isAdded()) return;
                    btnCheckUpdate.setEnabled(true);

                    if (info == null) {
                        mPendingUpdate = null;
                        tvUpdateStatus.setText("Sudah versi terbaru");
                        tvUpdateStatus.setTextColor(requireContext().getColor(R.color.es_live));
                        btnCheckUpdate.setText("CEK ULANG");
                        Toast.makeText(getContext(),
                                "Eastside Client " + currentVersion + " adalah versi terbaru.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        mPendingUpdate = info;
                        tvUpdateStatus.setText("Tersedia v" + info.version + " — ketuk untuk memasang");
                        tvUpdateStatus.setTextColor(requireContext().getColor(R.color.es_brass));
                        btnCheckUpdate.setText("PASANG");
                        showReleaseNotes(info);
                    }
                });
            }

            @Override
            public void onError(String message) {
                ui.post(() -> {
                    if (!isAdded()) return;
                    btnCheckUpdate.setEnabled(true);
                    tvUpdateStatus.setText("Gagal memeriksa pembaruan");
                    tvUpdateStatus.setTextColor(requireContext().getColor(R.color.es_warn));
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        }));
    }

    private void showReleaseNotes(UpdateChecker.UpdateInfo info) {
        if (getContext() == null) return;
        String notes = info.notes == null ? "" : info.notes.trim();
        if (notes.length() > 1200) notes = notes.substring(0, 1200) + "...";

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Eastside Client " + info.tag)
                .setMessage(notes.isEmpty() ? "Tidak ada catatan rilis." : notes)
                .setPositiveButton("Unduh & Pasang", (d, w) -> promptInstall(info))
                .setNegativeButton("Nanti", null)
                .show();
    }

    private void promptInstall(UpdateChecker.UpdateInfo info) {
        if (getContext() == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Unduh pembaruan?")
                .setMessage("Versi " + info.version + " akan diunduh"
                        + (info.apkSize > 0 ? " (" + (info.apkSize / 1048576) + " MB)" : "")
                        + ", lalu pemasang Android akan terbuka.")
                .setPositiveButton("Unduh", (d, w) -> startUpdateDownload(info))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void startUpdateDownload(UpdateChecker.UpdateInfo info) {
        if (getContext() == null) return;

        layoutUpdateProgress.setVisibility(View.VISIBLE);
        pbUpdate.setProgress(0);
        tvUpdateProgress.setText("Mengunduh 0%");
        btnCheckUpdate.setEnabled(false);
        tvUpdateStatus.setText("Mengunduh v" + info.version + "...");
        tvUpdateStatus.setTextColor(requireContext().getColor(R.color.es_brass));

        final android.content.Context appCtx = requireContext().getApplicationContext();
        updater.execute(() -> UpdateChecker.download(appCtx, info, new UpdateChecker.DownloadCallback() {
            @Override
            public void onProgress(int percent, long done, long total) {
                ui.post(() -> {
                    if (!isAdded()) return;
                    pbUpdate.setProgress(percent);
                    tvUpdateProgress.setText("Mengunduh " + percent + "%  ·  "
                            + (done / 1048576) + "/" + (total / 1048576) + " MB");
                });
            }

            @Override
            public void onComplete(File apk) {
                ui.post(() -> {
                    if (!isAdded()) return;
                    layoutUpdateProgress.setVisibility(View.GONE);
                    btnCheckUpdate.setEnabled(true);
                    tvUpdateStatus.setText("Unduhan selesai — buka pemasang");
                    tvUpdateStatus.setTextColor(requireContext().getColor(R.color.es_live));

                    boolean opened = UpdateChecker.install(requireActivity(), apk);
                    if (!opened) {
                        Toast.makeText(getContext(),
                                "Izinkan \"Instal aplikasi tidak dikenal\" untuk Eastside Client, lalu tekan PASANG lagi.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                ui.post(() -> {
                    if (!isAdded()) return;
                    layoutUpdateProgress.setVisibility(View.GONE);
                    btnCheckUpdate.setEnabled(true);
                    tvUpdateStatus.setText("Unduhan gagal");
                    tvUpdateStatus.setTextColor(requireContext().getColor(R.color.es_warn));
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        }));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        updater.shutdownNow();
    }
}
