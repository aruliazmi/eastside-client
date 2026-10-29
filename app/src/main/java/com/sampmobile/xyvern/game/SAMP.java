package com.sampmobile.xyvern.game;

import android.widget.FrameLayout;

import com.sampmobile.xyvern.cef.CefClientManager;
import com.sampmobile.xyvern.cef.CefJavaManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.joom.paranoid.Obfuscate;
import com.sampmobile.xyvern.game.ui.AttachEdit;
import com.sampmobile.xyvern.game.ui.CustomKeyboard;
import com.sampmobile.xyvern.game.ui.LoadingScreen;
import androidx.activity.OnBackPressedCallback;
import com.sampmobile.xyvern.game.ui.dialog.DialogManager;
import com.sampmobile.xyvern.launcher.util.SharedPreferenceCore;
import com.sampmobile.xyvern.launcher.util.SignatureChecker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Obfuscate
public class SAMP extends GTASA implements CustomKeyboard.InputListener, HeightProvider.HeightListener {
    private static final String TAG = "SAMP";
    private static SAMP instance;

    private CustomKeyboard mKeyboard;
    private DialogManager mDialog;
    private HeightProvider mHeightProvider;

    private AttachEdit mAttachEdit;
    private LoadingScreen mLoadingScreen;
    private CefClientManager mCefClientManager;
    private CefJavaManager mCefJavaManager;
    public static SAMP getInstance() {
        return instance;
    }

    private void showTab() {}
    private void hideTab() {}
    private void setTab(int id, String name, int score, int ping) {}
    private void clearTab() {}

    private void showLoadingScreen() {}

    private void hideLoadingScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLoadingScreen != null) mLoadingScreen.hide();
            }
        });
    }

    public void setPauseState(boolean pause) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mDialog == null || mAttachEdit == null) return;
                if (pause) {
                    mDialog.hideWithoutReset();
                    mAttachEdit.hideWithoutReset();
                } else {
                    if(mDialog.isShow) mDialog.showWithOldContent();
                    if(mAttachEdit.isShow) mAttachEdit.showWithoutReset();
                }
            }
        });
    }

    public void exitGame(){
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);
        finishAndRemoveTask();
        System.exit(0);
    }

    public void showDialog(int dialogId, int dialogTypeId, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) {
        // Malformed server packets can carry null arrays for empty dialog
        // strings; new String(null) is an NPE on the native-caller thread.
        final String caption      = bArr  != null ? new String(bArr,  StandardCharsets.UTF_8) : "";
        final String content      = bArr2 != null ? new String(bArr2, StandardCharsets.UTF_8) : "";
        final String leftBtnText  = bArr3 != null ? new String(bArr3, StandardCharsets.UTF_8) : "";
        final String rightBtnText = bArr4 != null ? new String(bArr4, StandardCharsets.UTF_8) : "";
        runOnUiThread(() -> { this.mDialog.show(dialogId, dialogTypeId, caption, content, leftBtnText, rightBtnText); });
    }

    private native void onInputEnd(byte[] str);
    @Override
    public void OnInputEnd(String str) {
        if (str != null) {
            String trimmed = str.trim();
            if (trimmed.equalsIgnoreCase("/q") || trimmed.equalsIgnoreCase("/quit")) {
                exitGame();
                return;
            }
        }

        byte[] toReturn = null;
        try {
            toReturn = str != null ? str.getBytes("windows-874") : new byte[0];
        } catch(UnsupportedEncodingException e) {}

        try {
            onInputEnd(toReturn);
        } catch (UnsatisfiedLinkError e5) {
        }
    }

    private void showKeyboard() {
        runOnUiThread(() -> {
            Log.d("AXL", "showKeyboard()");
            // The engine thread can call this before onCreate() finished building
            // the overlays; a null here used to be an NPE on the UI thread.
            if (mKeyboard == null) {
                Log.w(TAG, "showKeyboard: keyboard overlay not ready yet");
                return;
            }
            mKeyboard.ShowInputLayout();
        });
    }

    private void hideKeyboard() {
        runOnUiThread(() -> { if (mKeyboard != null) mKeyboard.HideInputLayout(); });
    }

    private void showEditObject() {
        runOnUiThread(() -> { if (mAttachEdit != null) mAttachEdit.show(); });
    }

    private void hideEditObject() {
        runOnUiThread(() -> { if (mAttachEdit != null) mAttachEdit.hide(); });
    }

    // =========================================================================
    // KHU VỰC SỬA LOGIC ONCREATE - GHI SETTINGS.INI TRỰC TIẾP VÀ KHỞI CHẠY ENGINE
    // =========================================================================
    /** versionName of this build, used to decide when to refresh AML mods. */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "**** onCreate");
        instance = this;

        // ============================================================
        // AML (AndroidModLoader) must be loaded BEFORE super.onCreate():
        // NvEventQueueActivity.onCreate() calls init(false), which boots the
        // GTASA engine and installs its hooks. Loading libAML.so afterwards
        // meant "Working with mods..." ran against an already-initialized
        // engine, so every AML mod (CLEO, MonetLoader) failed to hook and
        // nothing in files/mods/ had any effect.
        // ============================================================
        try {
            File extFiles = getExternalFilesDir(null);
            if (extFiles != null) {
                File modsDir = new File(extFiles, "mods");
                if (!modsDir.exists()) modsDir.mkdirs();
                File cleoDir = new File(extFiles, "cleo");
                if (!cleoDir.exists()) cleoDir.mkdirs();
            }

            boolean amlEnabled = getSharedPreferences("com.sampmobile.xyvern", MODE_PRIVATE)
                    .getBoolean("aml_enabled", false);
            if (amlEnabled) {
                // Refresh files/mods/ before AML starts scanning it. CLEO and
                // MonetLoader are AML mods, so they are loaded by AML itself
                // and must not be loadLibrary()'d here.
                AmlModInstaller.sync(this, versionName());
                System.loadLibrary("AML");
                Log.i(TAG, "AndroidModLoader loaded (pre-engine)");
            } else {
                Log.i(TAG, "AndroidModLoader disabled in settings");
            }
        } catch (Throwable error) {
            Log.e(TAG, "Error initializing AML", error);
        }

        super.onCreate(savedInstanceState);

        // ============================================================
        // The UI overlays MUST be created AFTER super.onCreate().
        // NvEventQueueActivity.systemInit() calls setContentView(R.layout.main_render_screen),
        // which replaces the whole content view. Anything addContentView()'d before
        // that point was silently thrown away - which is exactly why tapping the chat
        // box showed nothing: CustomKeyboard's (invisible) input bar no longer existed
        // in the view hierarchy, so no IME could attach to it.
        // ============================================================
        mKeyboard = new CustomKeyboard(this);
        mDialog = new DialogManager(this);
        mAttachEdit = new AttachEdit(this);
        mLoadingScreen = new LoadingScreen(this);

        if(!SignatureChecker.isSignatureValid(this, getPackageName())) {
            Toast.makeText(this, "Use original launcher! No remake", Toast.LENGTH_LONG).show();
            return;
        }

        // ============================================================
        // SA:MP MOBILE CEF (can be toggled in launcher Settings)
        // ============================================================
        try {
            android.content.SharedPreferences cefPrefs = getSharedPreferences(
                    com.sampmobile.xyvern.launcher.util.SharedPreferenceCore.APP_PREFERENCES, MODE_PRIVATE);
            boolean cefEnabled = !cefPrefs.contains("CEF_ENABLED") || cefPrefs.getBoolean("CEF_ENABLED", true);
            com.sampmobile.xyvern.cef.CefJavaManager.setWebViewEnabled(cefEnabled);
            if (!cefEnabled) {
                Log.i(TAG, "SA:MP Mobile CEF disabled in settings");
            } else {
                FrameLayout rootLayout = findViewById(android.R.id.content);

                mCefClientManager = new CefClientManager(this);
                mCefJavaManager = new CefJavaManager(rootLayout, this);

                mCefClientManager.setJavaManager(mCefJavaManager);
                mCefJavaManager.setClientManager(mCefClientManager);

                Log.i(TAG, "SA:MP Mobile CEF initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SA:MP Mobile CEF", e);
        }

        // Server is chosen in the launcher (multi-server). Fall back to the
        // stored selection so a direct game launch still works.
        String ip = null;
        int port = 7777;
        try {
            android.content.Intent it = getIntent();
            if (it != null) {
                String extraIp = it.getStringExtra("ip");
                int extraPort = it.getIntExtra("port", 0);
                if (extraIp != null && !extraIp.trim().isEmpty()) {
                    ip = extraIp.trim();
                    if (extraPort > 0 && extraPort <= 65535) port = extraPort;
                }
            }
            if (ip == null) {
                com.sampmobile.xyvern.launcher.data.ServerEntry cur =
                        com.sampmobile.xyvern.launcher.data.ServerStore.current(this);
                if (cur != null) {
                    ip = cur.host;
                    port = cur.port;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve target server", e);
        }
        if (ip == null || ip.isEmpty()) {
            ip = com.sampmobile.xyvern.launcher.data.ServerStore.DEFAULT_HOST;
            port = com.sampmobile.xyvern.launcher.data.ServerStore.DEFAULT_PORT;
        }

        try {
            initAssetManager(getAssets());
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "initAssetManager failed", e);
        }

        final String finalIp = ip;
        final int finalPort = port;
        GameDataManager.checkAndPrepare(this, new GameDataManager.ReadyCallback() {
            @Override public void onReady() {
                if (isFinishing() || isDestroyed()) return;
                startGameAfterDataReady(finalIp, finalPort);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                onEventBackPressed();
                setEnabled(true);
            }
        });
    }

    private void startGameAfterDataReady(String ip, int port) {
        mLoadingScreen.setProgress(0);
        new Thread(() -> {
            for (int i = 0; i <= 100; i++) {
                final int progress = i;
                runOnUiThread(() -> mLoadingScreen.setProgress(progress));
                try { Thread.sleep(25); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                try {
                    updateSettingsIni(ip, port);
                    File external = getExternalFilesDir(null);
                    if (external == null) {
                        Log.e(TAG, "External files directory unavailable");
                        Toast.makeText(this, "Storage unavailable", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    String storagePath = external.getAbsolutePath();
                    if (!storagePath.endsWith("/")) storagePath += "/";
                    initializeSAMP(storagePath);
                } catch (Exception e) {
                    Log.e(TAG, "Gagal inisialisasi game", e);
                    Toast.makeText(this, "Gagal memulai game: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void updateSettingsIni(String ip, int port) {
        File iniFile = new File(getExternalFilesDir(null), "SAMP/settings.ini");
        
        try {
            if (!iniFile.getParentFile().exists()) iniFile.getParentFile().mkdirs();

            SharedPreferenceCore spCore = new SharedPreferenceCore();
            Context ctx = getApplicationContext();
            
            android.content.SharedPreferences sp = getSharedPreferences("com.sampmobile.xyvern", MODE_PRIVATE);
            String nickname = sp.getString("nickname", "");
            if (nickname == null) nickname = "";
            nickname = nickname.trim();
            // Never write a blank name into sa-mp.cfg: the engine needs a placeholder
            // until the player picks one in the launcher.
            if (nickname.isEmpty()) nickname = "Player";
            if (nickname.length() > 24) nickname = nickname.substring(0, 24);
            
            String[] versions = {"0.3.DL-R1", "0.3.7", "0.3.7-R5"};
            int vIdx = spCore.getInt(ctx, "VERSION", 0);
            String version = (vIdx >= 0 && vIdx < versions.length) ? versions[vIdx] : "0.3.DL-R1";

            StringBuilder sb = new StringBuilder();
            sb.append("[client]\n");
            sb.append("name=").append(nickname).append("\n");
            sb.append("host=").append(ip).append("\n");
            sb.append("port=").append(port).append("\n");
            sb.append("password=\n");
            sb.append("version=").append(version).append("\n\n");
            
            sb.append("[gui]\n");
            sb.append("androidkeyboard=").append(spCore.getBoolean(ctx, "ANDROID_KEYBOARD") ? "true" : "false").append("\n");
            sb.append("VoiceChatEnable=").append(spCore.getBoolean(ctx, "VOICE_CHAT") ? "true" : "false").append("\n");
            sb.append("fps=").append(spCore.getBoolean(ctx, "FPS_DISPLAY") ? "true" : "false").append("\n");
            sb.append("ChatMaxMessages=").append(spCore.getInt(ctx, "MESSAGE_COUNT", 6)).append("\n");
            sb.append("FPSLimit=").append(spCore.getInt(ctx, "FPS_LIMIT", 60)).append("\n");

            java.io.FileOutputStream fos = new java.io.FileOutputStream(iniFile);
            fos.write(sb.toString().getBytes());
            fos.close();
            
            Log.d(TAG, "settings.ini saved for " + nickname + " to " + ip + ":" + port);
        } catch (Exception e) {
            Log.e(TAG, "Gagal menulis settings.ini: " + e.getMessage());
        }
    }

    private native void initializeSAMP(String storagePath);
    public native void initAssetManager(android.content.res.AssetManager assetManager);

    @Override
    public void onStart() {
        Log.i(TAG, "**** onStart");
        super.onStart();
    }

    @Override
    public void onRestart() {
        Log.i(TAG, "**** onRestart");
        super.onRestart();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "**** onResume");
        super.onResume();
    }

    public native void onEventBackPressed();

    @SuppressLint("GestureBackNavigation")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            onEventBackPressed();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "**** onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "**** onStop");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "**** onDestroy");
        super.onDestroy();
        instance = null;
        // Mematikan paksa proses game saat keluar untuk menghindari crash "destroyed mutex" 
        // yang sering terjadi di thread native saat activity dihancurkan.
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus && mCefJavaManager != null && mCefJavaManager.isShow()) {
            return;
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onHeightChanged(int orientation, int height) {}
}
