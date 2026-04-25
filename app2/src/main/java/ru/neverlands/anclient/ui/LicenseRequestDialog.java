package ru.neverlands.anclient.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import ru.neverlands.anclient.license.LicenseStatus;
import ru.neverlands.anclient.utils.AppLog;

public final class LicenseRequestDialog {
    private static final String TAG = "LicenseRequestDialog";
    private static final String CHAIN = "ANCLIENT_LICENSE";

    private LicenseRequestDialog() {
    }

    public static void show(AppCompatActivity activity, LicenseStatus status) {
        if (activity == null || status == null) {
            return;
        }

        String requestPath = status.getRequestPath();
        String licensePath = status.getLicensePath();
        StringBuilder message = new StringBuilder();
        if (!TextUtils.isEmpty(status.getMessage())) {
            message.append(status.getMessage()).append("\n\n");
        }
        if (!TextUtils.isEmpty(requestPath)) {
            message.append("Файл запроса:\n").append(requestPath).append("\n\n");
        }
        if (!TextUtils.isEmpty(licensePath)) {
            message.append("Куда положить profile.reg:\n").append(licensePath).append("\n\n");
        }
        message.append("Процедура: нажмите 'Отправить файл' или 'Копировать запрос', передайте request.txt администратору, получите profile.reg и положите его в указанную папку.");

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(TextUtils.isEmpty(status.getTitle()) ? "Лицензия" : status.getTitle())
                .setMessage(message.toString())
                .setPositiveButton("OK", null);
        if (!TextUtils.isEmpty(requestPath)) {
            builder.setNeutralButton("Копировать запрос", (dialog, which) -> copyRequestToClipboard(activity, requestPath));
            builder.setNegativeButton("Отправить файл", (dialog, which) -> shareRequestFile(activity, requestPath));
        }
        builder.show();
    }

    private static void shareRequestFile(AppCompatActivity activity, String requestPath) {
        try {
            File requestFile = new File(requestPath);
            if (!requestFile.exists() || requestFile.length() <= 0L) {
                Toast.makeText(activity, "request.txt не найден или пустой", Toast.LENGTH_LONG).show();
                return;
            }
            Uri fileUri = FileProvider.getUriForFile(
                    activity,
                    activity.getApplicationContext().getPackageName() + ".provider",
                    requestFile
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "ANClient request.txt");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), "ANClient request.txt", fileUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            AppLog.i(CHAIN, TAG, "LICENSE_REQUEST_SHARE: path=" + requestFile.getAbsolutePath());
            activity.startActivity(Intent.createChooser(intent, "Отправить request.txt..."));
        } catch (Exception e) {
            AppLog.w(CHAIN, TAG, "LICENSE_REQUEST_SHARE_FAILED: " + e.getMessage(), e);
            Toast.makeText(activity, "Не удалось отправить request.txt", Toast.LENGTH_LONG).show();
        }
    }

    private static void copyRequestToClipboard(AppCompatActivity activity, String requestPath) {
        try {
            String requestText = readUtf8File(requestPath).trim();
            if (requestText.isEmpty()) {
                Toast.makeText(activity, "request.txt пустой", Toast.LENGTH_LONG).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ANClient request.txt", requestText));
                Toast.makeText(activity, "Запрос скопирован", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            AppLog.w(CHAIN, TAG, "LICENSE_REQUEST_COPY_FAILED: " + e.getMessage(), e);
            Toast.makeText(activity, "Не удалось скопировать request.txt", Toast.LENGTH_LONG).show();
        }
    }

    private static String readUtf8File(String path) throws Exception {
        File file = new File(path);
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
