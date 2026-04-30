package ru.neverlands.anclient;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ru.neverlands.anclient.databinding.ActivityLogsBinding;

public class LogsActivity extends AppCompatActivity {

    private ActivityLogsBinding binding;
    private LogsAdapter adapter;
    private final List<File> logFiles = new ArrayList<>();
    private final Set<File> selectedEntries = new LinkedHashSet<>();
    private File logsRootDir;
    private File currentDir;
    private boolean selectionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setTitle("Логи");

        setupRecyclerView();
        setupSelectionActions();
        logsRootDir = getExternalFilesDir("Logs");
        currentDir = logsRootDir;
        loadLogFiles(currentDir);
    }

    private void setupRecyclerView() {
        binding.logsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogsAdapter();
        binding.logsRecyclerView.setAdapter(adapter);
    }

    private void setupSelectionActions() {
        binding.logsSelectAllButton.setOnClickListener(v -> toggleSelectAllCurrentEntries());
        binding.logsOpenButton.setOnClickListener(v -> openSelectedEntry());
        binding.logsSendButton.setOnClickListener(v -> sendSelectedEntries());
        binding.logsDeleteButton.setOnClickListener(v -> deleteSelectedEntries());
        binding.logsClearSelectionButton.setOnClickListener(v -> clearSelectionState(true));
        updateSelectionBar();
    }

    private void loadLogFiles(File directory) {
        clearSelectionState(false);
        logFiles.clear();
        if (directory != null && directory.exists() && directory.isDirectory()) {
            currentDir = directory;
            File[] files = directory.listFiles();
            if (files != null) {
                logFiles.addAll(Arrays.asList(files));
                Collections.sort(logFiles, (f1, f2) -> {
                    if (f1.isDirectory() != f2.isDirectory()) {
                        return f1.isDirectory() ? -1 : 1;
                    }
                    return Long.compare(f2.lastModified(), f1.lastModified());
                });
            }
        }
        updateTitle();
        updateSelectionBar();
        adapter.notifyDataSetChanged();
    }

    private void updateTitle() {
        if (logsRootDir == null || currentDir == null || logsRootDir.equals(currentDir)) {
            setTitle("Логи");
            return;
        }
        String rootPath = logsRootDir.getAbsolutePath();
        String currentPath = currentDir.getAbsolutePath();
        String suffix = currentPath.startsWith(rootPath)
                ? currentPath.substring(rootPath.length())
                : currentDir.getName();
        while (suffix.startsWith(File.separator)) {
            suffix = suffix.substring(1);
        }
        setTitle(suffix.isEmpty() ? "Логи" : "Логи / " + suffix);
    }

    private boolean canNavigateUp() {
        return logsRootDir != null && currentDir != null && !logsRootDir.equals(currentDir);
    }

    private void navigateUp() {
        if (!canNavigateUp()) {
            return;
        }
        File parent = currentDir.getParentFile();
        if (parent != null && isInsideLogsRoot(parent)) {
            loadLogFiles(parent);
        }
    }

    private boolean isInsideLogsRoot(File file) {
        if (logsRootDir == null || file == null) {
            return false;
        }
        try {
            String rootPath = logsRootDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (selectionMode) {
            clearSelectionState(true);
            return;
        }
        if (canNavigateUp()) {
            navigateUp();
            return;
        }
        super.onBackPressed();
    }

    private void openLogEntry(File file) {
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            loadLogFiles(file);
            return;
        }
        openLogFile(file);
    }

    private void openLogFile(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Открыть лог с помощью..."));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось найти приложение для открытия логов.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleSelection(File file) {
        if (file == null) {
            return;
        }
        if (selectedEntries.contains(file)) {
            selectedEntries.remove(file);
        } else {
            selectedEntries.add(file);
        }
        selectionMode = !selectedEntries.isEmpty();
        updateSelectionBar();
        adapter.notifyDataSetChanged();
    }

    private void toggleSelectAllCurrentEntries() {
        if (logFiles.isEmpty()) {
            return;
        }
        if (selectedEntries.size() == logFiles.size()) {
            selectedEntries.clear();
        } else {
            selectedEntries.clear();
            selectedEntries.addAll(logFiles);
        }
        selectionMode = !selectedEntries.isEmpty();
        updateSelectionBar();
        adapter.notifyDataSetChanged();
    }

    private void clearSelectionState(boolean notifyAdapter) {
        selectedEntries.clear();
        selectionMode = false;
        updateSelectionBar();
        if (notifyAdapter && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateSelectionBar() {
        if (binding == null) {
            return;
        }
        int selectedCount = selectedEntries.size();
        selectionMode = selectedCount > 0;
        binding.logsSelectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        binding.logsSelectionCount.setText("Выбрано: " + selectedCount);
        boolean canOpen = selectedCount == 1;
        binding.logsOpenButton.setEnabled(canOpen);
        binding.logsOpenButton.setAlpha(canOpen ? 1f : 0.35f);
        binding.logsSendButton.setEnabled(selectedCount > 0);
        binding.logsDeleteButton.setEnabled(selectedCount > 0);
        binding.logsSelectAllButton.setEnabled(!logFiles.isEmpty());
    }

    private List<File> getSelectedEntriesSnapshot() {
        return new ArrayList<>(selectedEntries);
    }

    private File getSingleSelectedEntry() {
        for (File file : selectedEntries) {
            return file;
        }
        return null;
    }

    private void openSelectedEntry() {
        if (selectedEntries.size() != 1) {
            Toast.makeText(this, "Открыть можно только один элемент.", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = getSingleSelectedEntry();
        openLogEntry(file);
        if (file != null && file.isFile()) {
            clearSelectionState(true);
        }
    }

    private void sendSelectedEntries() {
        List<File> entries = getSelectedEntriesSnapshot();
        if (entries.isEmpty()) {
            return;
        }
        sendLogEntries(entries);
        clearSelectionState(true);
    }

    private void deleteSelectedEntries() {
        List<File> entries = getSelectedEntriesSnapshot();
        if (entries.isEmpty()) {
            return;
        }
        deleteLogEntries(entries);
    }

    private void sendLogEntries(List<File> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            for (File entry : entries) {
                if (entry == null || !isInsideLogsRoot(entry)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    collectFileUris(entry, uris);
                } else if (entry.isFile()) {
                    uris.add(FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", entry));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось подготовить логи к отправке.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "Нет файлов для отправки.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
            intent.setType("text/plain");
            if (uris.size() == 1) {
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Отправить логи..."));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось отправить логи.", Toast.LENGTH_SHORT).show();
        }
    }

    private void collectFileUris(File directory, ArrayList<Uri> uris) {
        if (directory == null || !directory.isDirectory() || !isInsideLogsRoot(directory)) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectFileUris(file, uris);
            } else if (file.isFile()) {
                uris.add(FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file));
            }
        }
    }

    private void deleteLogEntries(List<File> entries) {
        List<File> safeEntries = new ArrayList<>();
        if (entries != null) {
            for (File file : entries) {
                if (file != null && isInsideLogsRoot(file) && (logsRootDir == null || !logsRootDir.equals(file))) {
                    safeEntries.add(file);
                }
            }
        }
        if (safeEntries.isEmpty()) {
            Toast.makeText(this, "Нельзя удалить эту папку.", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = safeEntries.size() == 1 ? "Удалить лог" : "Удалить логи";
        String message = safeEntries.size() == 1
                ? "Вы уверены, что хотите удалить " + safeEntries.get(0).getName() + "?"
                : "Вы уверены, что хотите удалить выбранные элементы (" + safeEntries.size() + ")?";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Да", (dialog, which) -> {
                    int deletedCount = 0;
                    for (File entry : safeEntries) {
                        if (deleteRecursive(entry)) {
                            deletedCount++;
                        }
                    }
                    clearSelectionState(false);
                    loadLogFiles(currentDir);
                    if (deletedCount == safeEntries.size()) {
                        Toast.makeText(LogsActivity.this, "Удалено", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LogsActivity.this, "Удалено: " + deletedCount + " из " + safeEntries.size(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .show();
    }

    private boolean deleteRecursive(File file) {
        if (file == null || !isInsideLogsRoot(file)) {
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_entry, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            File logFile = logFiles.get(position);
            boolean selected = selectedEntries.contains(logFile);
            holder.textView.setText(logFile.isDirectory() ? logFile.getName() + "/" : logFile.getName());
            holder.checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selected);
            holder.itemView.setActivated(selected);
            holder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelection(logFile);
                } else {
                    openLogEntry(logFile);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                toggleSelection(logFile);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return logFiles.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            CheckBox checkBox;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.logEntryName);
                checkBox = itemView.findViewById(R.id.logEntryCheckbox);
            }
        }
    }
}
