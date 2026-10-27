package com.sampmobile.xyvern.launcher.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.joom.paranoid.Obfuscate;
import com.sampmobile.xyvern.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Obfuscate
public class FilesFragment extends Fragment {

    private File rootDir;
    private File currentDir;

    private TextView tvCurrentPath;
    private TextView tvEmptyFiles;
    private RecyclerView rvFileList;
    private Button btnDirUp;
    private Button btnRefresh;
    private Button btnCleanCache;

    private FileAdapter adapter;
    private final List<File> fileItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);

        Context ctx = requireContext();
        rootDir = ctx.getExternalFilesDir(null);
        if (rootDir == null) {
            rootDir = ctx.getFilesDir();
        }
        currentDir = rootDir;

        tvCurrentPath = view.findViewById(R.id.tv_current_path);
        tvEmptyFiles = view.findViewById(R.id.tv_empty_files);
        rvFileList = view.findViewById(R.id.rv_file_list);
        btnDirUp = view.findViewById(R.id.btn_dir_up);
        btnRefresh = view.findViewById(R.id.btn_refresh_files);
        btnCleanCache = view.findViewById(R.id.btn_clean_cache);

        rvFileList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter();
        rvFileList.setAdapter(adapter);

        btnDirUp.setOnClickListener(v -> navigateUp());
        btnRefresh.setOnClickListener(v -> loadCurrentDirectory());
        btnCleanCache.setOnClickListener(v -> cleanCache());

        loadCurrentDirectory();

        return view;
    }

    private void loadCurrentDirectory() {
        if (currentDir == null || !currentDir.exists()) {
            currentDir = rootDir;
        }

        String displayPath = currentDir.getAbsolutePath();
        if (rootDir != null) {
            String rootPath = rootDir.getAbsolutePath();
            if (displayPath.startsWith(rootPath)) {
                displayPath = "/files" + displayPath.substring(rootPath.length());
            }
        }
        tvCurrentPath.setText(displayPath);

        File[] files = currentDir.listFiles();
        fileItems.clear();

        if (files != null && files.length > 0) {
            // Sort: directories first, then alphabetical
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            fileItems.addAll(Arrays.asList(files));
        }

        adapter.notifyDataSetChanged();
        tvEmptyFiles.setVisibility(fileItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void navigateUp() {
        if (currentDir == null || rootDir == null) return;
        if (currentDir.equals(rootDir)) {
            Toast.makeText(getContext(), "Sudah di folder root game", Toast.LENGTH_SHORT).show();
            return;
        }
        File parent = currentDir.getParentFile();
        if (parent != null && parent.exists() && parent.getAbsolutePath().startsWith(rootDir.getAbsolutePath())) {
            currentDir = parent;
            loadCurrentDirectory();
        } else {
            currentDir = rootDir;
            loadCurrentDirectory();
        }
    }

    private void cleanCache() {
        if (rootDir == null) return;
        int cleaned = 0;
        long freedBytes = 0;

        File[] candidates = new File[] {
                new File(rootDir, "chatlog.txt"),
                new File(rootDir, "CINFO.BIN"),
                new File(rootDir, "models/MINFO.BIN"),
                new File(rootDir, "SAMP/chatlog.txt")
        };

        for (File f : candidates) {
            if (f.exists()) {
                freedBytes += f.length();
                if (f.delete()) {
                    cleaned++;
                }
            }
        }

        loadCurrentDirectory();
        String msg = "Cache dibersihkan: " + cleaned + " file (" + (freedBytes / 1024) + " KB).";
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void openTextFile(File file) {
        try {
            StringBuilder content = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            int linesCount = 0;
            while ((line = br.readLine()) != null && linesCount < 1000) {
                content.append(line).append("\n");
                linesCount++;
            }
            br.close();

            EditText input = new EditText(getContext());
            input.setText(content.toString());
            input.setTextColor(0xFFFFFFFF);
            input.setTextSize(12);
            input.setPadding(24, 24, 24, 24);
            input.setBackgroundColor(0xFF0F172A);

            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(file.getName())
                    .setView(input)
                    .setPositiveButton("Simpan", (d, w) -> {
                        try {
                            FileWriter fw = new FileWriter(file, false);
                            fw.write(input.getText().toString());
                            fw.flush();
                            fw.close();
                            Toast.makeText(getContext(), "Tersimpan!", Toast.LENGTH_SHORT).show();
                            loadCurrentDirectory();
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Tutup", null)
                    .show();

        } catch (Exception e) {
            Toast.makeText(getContext(), "Tidak dapat membaca file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hapus File?")
                .setMessage("Yakin ingin menghapus " + file.getName() + "?")
                .setPositiveButton("Hapus", (d, w) -> {
                    if (file.isDirectory()) {
                        deleteRecursive(file);
                    } else {
                        file.delete();
                    }
                    loadCurrentDirectory();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    deleteRecursive(sub);
                }
            }
        }
        f.delete();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = fileItems.get(position);
            holder.tvName.setText(file.getName());

            if (file.isDirectory()) {
                holder.ivIcon.setImageResource(R.drawable.ic_folder_nav);
                holder.ivIcon.setColorFilter(0xFFF59E0B);
                File[] children = file.listFiles();
                int count = (children != null) ? children.length : 0;
                holder.tvDetails.setText(count + " item");
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_file_doc);
                holder.ivIcon.setColorFilter(0xFF38BDF8);
                long bytes = file.length();
                String sizeStr = (bytes > 1048576) ? (bytes / 1048576) + " MB" : (bytes / 1024) + " KB";
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
                holder.tvDetails.setText(sizeStr + " • " + sdf.format(new Date(file.lastModified())));
            }

            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    currentDir = file;
                    loadCurrentDirectory();
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".txt") || name.endsWith(".cfg") || name.endsWith(".ini") || name.endsWith(".log") || name.endsWith(".json")) {
                        openTextFile(file);
                    } else {
                        Toast.makeText(getContext(), file.getName() + " (" + (file.length() / 1024) + " KB)", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            holder.ivDelete.setOnClickListener(v -> confirmDelete(file));
        }

        @Override
        public int getItemCount() {
            return fileItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvDetails;
            ImageView ivDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_file_icon);
                tvName = itemView.findViewById(R.id.tv_file_name);
                tvDetails = itemView.findViewById(R.id.tv_file_details);
                ivDelete = itemView.findViewById(R.id.iv_file_delete);
            }
        }
    }
}
