package ru.neverlands.abclient;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Comparator;
import java.util.List;

import ru.neverlands.abclient.databinding.ActivityLogsBinding;

public class LogsActivity extends AppCompatActivity {

    private ActivityLogsBinding binding;
    private LogsAdapter adapter;
    private List<File> logFiles = new ArrayList<>();
    private int longClickedPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLogsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setTitle("Логи");

        setupRecyclerView();
        loadLogFiles();
    }

    private void setupRecyclerView() {
        binding.logsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogsAdapter();
        binding.logsRecyclerView.setAdapter(adapter);
        registerForContextMenu(binding.logsRecyclerView);
    }

    private void loadLogFiles() {
        logFiles.clear();
        File logsDir = getExternalFilesDir("Logs");
        if (logsDir.exists() && logsDir.isDirectory()) {
            File[] files = logsDir.listFiles();
            if (files != null) {
                logFiles.addAll(Arrays.asList(files));
                // Sort files by date, newest first
                Collections.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.logsRecyclerView) {
            getMenuInflater().inflate(R.menu.logs_context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (longClickedPosition == -1) {
            return super.onContextItemSelected(item);
        }

        File selectedFile = logFiles.get(longClickedPosition);

        int itemId = item.getItemId();
        if (itemId == R.id.action_open_log) {
            openLogFile(selectedFile);
            return true;
        } else if (itemId == R.id.action_send_log) {
            sendLogFile(selectedFile);
            return true;
        } else if (itemId == R.id.action_delete_log) {
            deleteLogFile(selectedFile);
            return true;
        }
        return super.onContextItemSelected(item);
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

    private void sendLogFile(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Отправить лог..."));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось отправить лог.", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteLogFile(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить лог")
                .setMessage("Вы уверены, что хотите удалить файл " + file.getName() + "?")
                .setPositiveButton("Да", (dialog, which) -> {
                    if (file.delete()) {
                        int position = logFiles.indexOf(file);
                        if (position != -1) {
                            logFiles.remove(position);
                            adapter.notifyItemRemoved(position);
                        }
                        Toast.makeText(LogsActivity.this, "Файл удален", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LogsActivity.this, "Не удалось удалить файл", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Нет", null)
                .show();
    }

    private class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            File logFile = logFiles.get(position);
            holder.textView.setText(logFile.getName());
            holder.itemView.setOnLongClickListener(v -> {
                longClickedPosition = holder.getAdapterPosition();
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return logFiles.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
            TextView textView;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
                itemView.setOnCreateContextMenuListener(this);
            }

            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                longClickedPosition = getAdapterPosition();
                getMenuInflater().inflate(R.menu.logs_context_menu, menu);
            }
        }
    }
}
