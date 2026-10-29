package com.sampmobile.xyvern.launcher.fragments;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sampmobile.xyvern.R;
import com.sampmobile.xyvern.launcher.data.ServerEntry;
import com.sampmobile.xyvern.launcher.data.ServerStore;
import com.sampmobile.xyvern.launcher.util.SampQueryAPI;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bottom sheet for picking, adding and removing servers.
 *
 * Replaces the hard-wired single host. Queries each server in the background so
 * the row shows live player counts instead of a static name.
 */
final class ServerPickerDialog {

    interface OnServerChosen {
        void onChosen(ServerEntry entry);
    }

    private final Context context;
    private final List<ServerEntry> servers;
    private final OnServerChosen callback;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Dialog dialog;
    private LinearLayout listView;
    private TextView countView;

    ServerPickerDialog(Context context, List<ServerEntry> servers, OnServerChosen callback) {
        this.context = context;
        this.servers = servers;
        this.callback = callback;
    }

    void show() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_servers, null);
        dialog.setContentView(root);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // wrap_content + maxHeight: the sheet must grow with the list but
            // never exceed the screen. MATCH_PARENT height collapsed the
            // ScrollView to zero inside a WRAP_CONTENT window.
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.6f;
            w.setAttributes(lp);

            // Cap the sheet so a long list cannot push the header off screen.
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    Math.min((int) (dm.heightPixels * 0.72f),
                            WindowManager.LayoutParams.WRAP_CONTENT));
        }

        listView = root.findViewById(R.id.ll_server_list);
        countView = root.findViewById(R.id.tv_server_count);

        root.findViewById(R.id.tv_close_servers).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.iv_add_server).setOnClickListener(v -> showEditForm(null));

        rebuild();
        queryAll();
        dialog.show();
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void rebuild() {
        if (listView == null) return;
        listView.removeAllViews();

        int current = ServerStore.currentIndex(context, servers);
        countView.setText(servers.size() + " TERSIMPAN");

        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < servers.size(); i++) {
            final int index = i;
            final ServerEntry entry = servers.get(i);
            View row = inflater.inflate(R.layout.item_server, listView, false);

            if (i == current) {
                row.setBackgroundResource(R.drawable.es_row_active);
            }

            TextView name = row.findViewById(R.id.tv_row_name);
            TextView endpoint = row.findViewById(R.id.tv_row_endpoint);
            TextView players = row.findViewById(R.id.tv_row_players);
            TextView ping = row.findViewById(R.id.tv_row_ping);
            View dot = row.findViewById(R.id.dot_row);

            name.setText(entry.name);
            endpoint.setText(entry.endpoint());
            players.setText(entry.playersLabel());
            ping.setText(entry.pingLabel());

            if (!entry.queried) {
                dot.setBackgroundResource(R.drawable.bg_dot_gray);
                players.setTextColor(0xFFA89C8B);
            } else if (entry.online) {
                dot.setBackgroundResource(R.drawable.bg_dot_green);
                players.setTextColor(0xFFD4A548);
            } else {
                dot.setBackgroundResource(R.drawable.bg_dot_red);
                players.setTextColor(0xFFA34A42);
            }

            row.setOnClickListener(v -> {
                ServerStore.setCurrentIndex(context, index);
                if (callback != null) callback.onChosen(entry);
                dismiss();
            });

            // Keep at least one server on the list; hide the action otherwise.
            View del = row.findViewById(R.id.iv_row_delete);
            if (servers.size() <= 1) {
                del.setVisibility(View.GONE);
            } else {
                del.setOnClickListener(v -> {
                    // Mutate the same list the dialog renders and persist it.
                    // The old path called ServerStore.remove() (which reloads
                    // from disk and re-indexes) and then also re-loaded the
                    // list, so the row under the finger was not the row removed.
                    if (servers.size() <= 1) return;
                    servers.remove(index);
                    ServerStore.save(context, servers);
                    int cur = ServerStore.currentIndex(context, servers);
                    if (cur >= servers.size()) {
                        ServerStore.setCurrentIndex(context, servers.size() - 1);
                    }
                    rebuild();
                    queryAll();
                });
            }

            listView.addView(row);
        }
    }

    /** Query every server once so the list shows real player counts. */
    private void queryAll() {
        for (final ServerEntry entry : servers) {
            executor.execute(() -> {
                long t0 = System.currentTimeMillis();
                boolean ok = false;
                try (SampQueryAPI api = new SampQueryAPI(entry.host, entry.port)) {
                    String[] info = api.mo7164b();
                    if (info != null && info.length >= 3) {
                        entry.players = parseInt(info[1]);
                        entry.maxPlayers = parseInt(info[2]);
                        entry.online = true;
                        ok = true;
                    }
                } catch (Exception ignored) {
                }
                entry.pingMs = (int) (System.currentTimeMillis() - t0);
                entry.online = ok;
                entry.queried = true;

                if (listView != null) {
                    listView.post(this::rebuild);
                }
            });
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int indexOfIdentity(ServerEntry entry) {
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i) == entry) return i;
        }
        return -1;
    }

    /** Add (existing == null) or edit a server. */
    private void showEditForm(ServerEntry existing) {
        // Launcher memakai Theme.AppCompat.Light.NoActionBar, jadi AlertDialog
        // bawaan tampil putih. Bungkus dengan tema dialog gelap supaya isian,
        // judul, dan tombol senada dengan sisa aplikasi. Form di-inflate dari
        // context yang sama agar atribut bertema ikut terpakai.
        android.content.Context themed =
                new android.view.ContextThemeWrapper(context, R.style.Theme_SAMPLauncher_Dialog);

        View form = LayoutInflater.from(themed).inflate(R.layout.dialog_server_edit, null);
        EditText etName = form.findViewById(R.id.et_srv_name);
        EditText etHost = form.findViewById(R.id.et_srv_host);
        EditText etPort = form.findViewById(R.id.et_srv_port);
        TextView error = form.findViewById(R.id.tv_form_error);

        if (existing != null) {
            etName.setText(existing.name);
            etHost.setText(existing.host);
            etPort.setText(String.valueOf(existing.port));
        }

        // Strip scheme/path people paste from a browser ("samp://1.2.3.4:7777").
        etHost.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String v = s.toString();
                if (v.contains("://") || v.contains("/")) {
                    String cleaned = v.replace("samp://", "").replace("SAMP://", "");
                    int slash = cleaned.indexOf('/');
                    if (slash >= 0) cleaned = cleaned.substring(0, slash);
                    int colon = cleaned.lastIndexOf(':');
                    if (colon > 0) {
                        String maybePort = cleaned.substring(colon + 1);
                        if (!maybePort.isEmpty() && maybePort.matches("\\d{1,5}")) {
                            etPort.setText(maybePort);
                            cleaned = cleaned.substring(0, colon);
                        }
                    }
                    etHost.setText(cleaned);
                    etHost.setSelection(etHost.getText().length());
                }
            }
        });

        final ServerEntry[] holder = new ServerEntry[]{existing};
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(themed)
                .setTitle(existing == null ? "Tambah server" : "Ubah server")
                .setView(form)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
                .create();

        // setPositiveButton auto-dismisses, which threw away the form whenever
        // validation failed. Install the click after show() and dismiss by hand.
        dlg.setOnShowListener(d -> dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText().toString().trim();
                    String host = etHost.getText().toString().trim();
                    String portRaw = etPort.getText().toString().trim();

                    if (host.isEmpty()) {
                        showFormError(error, "Host / IP tidak boleh kosong.");
                        return;
                    }
                    int port = 7777;
                    if (!portRaw.isEmpty()) {
                        try {
                            port = Integer.parseInt(portRaw);
                        } catch (Exception e) {
                            showFormError(error, "Port harus berupa angka.");
                            return;
                        }
                    }
                    if (port <= 0 || port > 65535) {
                        showFormError(error, "Port harus di antara 1 sampai 65535.");
                        return;
                    }
                    if (name.isEmpty()) name = host + ":" + port;

                    ServerEntry entry = holder[0];
                    int existingIdx = -1;
                    if (entry != null) {
                        // Replace by identity, not by host: the host itself may
                        // have been edited, so a host-based lookup appended a
                        // duplicate instead of updating the row.
                        existingIdx = indexOfIdentity(entry);
                        entry.name = name;
                        entry.host = host;
                        entry.port = port;
                        entry.queried = false;
                        entry.online = false;
                    } else {
                        entry = new ServerEntry(name, host, port, "");
                    }

                    if (existingIdx >= 0) {
                        servers.set(existingIdx, entry);
                    } else {
                        servers.add(entry);
                    }
                    ServerStore.save(context, servers);

                    rebuild();
                    queryAll();
                    dlg.dismiss();
                }));
        dlg.show();
    }

    private void showFormError(TextView view, String message) {
        if (view == null) {
            toast(message);
            return;
        }
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
