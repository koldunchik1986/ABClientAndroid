package ru.neverlands.anclient.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import ru.neverlands.anclient.license.LicenseStatus;
import ru.neverlands.anclient.utils.AppLog;

public final class LicenseRequestDialog {
    private static final String TAG = "LicenseRequestDialog";
    private static final String CHAIN = "ANCLIENT_LICENSE";

    private LicenseRequestDialog() {
    }

    /**
     * Dialog не владеет Activity Result API: экран передаёт сюда свой launcher,
     * чтобы системный выбор файла работал одинаково из login/profile flow.
     */
    public interface AttachProfileRegHandler {
        void onAttachProfileReg(String licensePath);
    }

    /** Callback вызывается только после успешной записи profile.reg в целевой путь ANClient. */
    public interface ProfileRegImportCallback {
        void onProfileRegImported(File destinationFile);
    }

    public static void show(AppCompatActivity activity, LicenseStatus status) {
        show(activity, status, null);
    }

    public static void show(AppCompatActivity activity,
                            LicenseStatus status,
                            AttachProfileRegHandler attachProfileRegHandler) {
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
        message.append("Процедура: нажмите 'Отправить файл' или 'Копировать запрос', передайте request.txt администратору, получите profile.reg и нажмите 'Прикрепить', чтобы выбрать файл на устройстве.");

        final Button attachButton = !TextUtils.isEmpty(licensePath) && attachProfileRegHandler != null
                ? new Button(activity)
                : null;
        if (attachButton != null) {
            attachButton.setText("Прикрепить");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(TextUtils.isEmpty(status.getTitle()) ? "Лицензия" : status.getTitle())
                .setView(buildDialogView(activity, message.toString(), attachButton))
                .setPositiveButton("OK", null);
        if (!TextUtils.isEmpty(requestPath)) {
            builder.setNeutralButton("Копировать запрос", (dialog, which) -> copyRequestToClipboard(activity, requestPath));
            builder.setNegativeButton("Отправить файл", (dialog, which) -> shareRequestFile(activity, requestPath));
        }
        AlertDialog dialog = builder.create();
        if (attachButton != null) {
            final String targetLicensePath = licensePath;
            attachButton.setOnClickListener(v -> {
                dialog.dismiss();
                attachProfileRegHandler.onAttachProfileReg(targetLicensePath);
            });
        }
        dialog.show();
    }

    private static View buildDialogView(AppCompatActivity activity, String message, Button attachButton) {
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(activity, 20);
        int topPadding = dp(activity, 16);
        container.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);

        TextView messageView = new TextView(activity);
        messageView.setText(message);
        messageView.setTextIsSelectable(true);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        container.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (attachButton != null) {
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            buttonParams.topMargin = dp(activity, 12);
            container.addView(attachButton, buttonParams);
        }

        scrollView.addView(container, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    public static Intent createProfileRegPickerIntent() {
        // ACTION_OPEN_DOCUMENT даёт доступ к profile.reg из любого провайдера файлов
        // без ручного копирования в Android/data/ru.neverlands.anclient/files/info.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain",
                "application/octet-stream",
                "application/x-binary",
                "*/*"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return Intent.createChooser(intent, "Выберите profile.reg");
    }

    public static void copyProfileRegFromUri(AppCompatActivity activity,
                                             Uri sourceUri,
                                             String destinationPath,
                                             ProfileRegImportCallback callback) {
        if (activity == null) {
            return;
        }
        if (sourceUri == null) {
            Toast.makeText(activity, "Файл profile.reg не выбран", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(destinationPath)) {
            Toast.makeText(activity, "Не задан путь для profile.reg", Toast.LENGTH_LONG).show();
            return;
        }

        // Копирование может читать большой ANREG2 bundle, поэтому не блокируем UI-поток.
        new Thread(() -> {
            try {
                String displayName = queryDisplayName(activity, sourceUri);
                File destinationFile = copyProfileRegFromUriBlocking(activity, sourceUri, destinationPath);
                AppLog.i(CHAIN, TAG, "LICENSE_PROFILE_REG_IMPORTED: source=" + displayName
                        + ", destination=" + destinationFile.getAbsolutePath()
                        + ", size=" + destinationFile.length());
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    Toast.makeText(activity, "profile.reg прикреплен", Toast.LENGTH_LONG).show();
                    if (callback != null) {
                        callback.onProfileRegImported(destinationFile);
                    }
                });
            } catch (Exception e) {
                AppLog.w(CHAIN, TAG, "LICENSE_PROFILE_REG_IMPORT_FAILED: " + e.getMessage(), e);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    Toast.makeText(activity, "Не удалось прикрепить profile.reg: " + safeError(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "license-profile-reg-import").start();
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

    private static File copyProfileRegFromUriBlocking(Context context,
                                                      Uri sourceUri,
                                                      String destinationPath) throws Exception {
        File destinationFile = new File(destinationPath);
        File parent = destinationFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + parent.getAbsolutePath());
        }

        // Сначала пишем во временный файл: старый рабочий profile.reg не трогаем,
        // пока выбранный файл не прошёл базовую проверку ANREG-envelope.
        File tempFile = new File(parent == null ? context.getCacheDir() : parent, "profile.reg.import.tmp");
        if (tempFile.exists() && !tempFile.delete()) {
            throw new IllegalStateException("Cannot replace temp file: " + tempFile.getAbsolutePath());
        }

        long copiedBytes = 0L;
        try (InputStream input = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream output = new FileOutputStream(tempFile)) {
            if (input == null) {
                throw new IllegalStateException("Cannot open selected file");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                copiedBytes += read;
            }
            output.getFD().sync();
        }

        if (copiedBytes <= 0L) {
            deleteQuietly(tempFile);
            throw new IllegalArgumentException("Selected profile.reg is empty");
        }
        if (!isProfileRegEnvelope(tempFile)) {
            deleteQuietly(tempFile);
            throw new IllegalArgumentException("Выбранный файл не похож на profile.reg");
        }

        if (destinationFile.exists() && !destinationFile.delete()) {
            deleteQuietly(tempFile);
            throw new IllegalStateException("Cannot replace profile.reg: " + destinationFile.getAbsolutePath());
        }
        if (!tempFile.renameTo(destinationFile)) {
            copyFile(tempFile, destinationFile);
            deleteQuietly(tempFile);
        }
        return destinationFile;
    }

    private static void copyFile(File source, File destination) throws Exception {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
    }

    private static boolean isProfileRegEnvelope(File file) throws Exception {
        // Здесь проверяется только контейнер ANREG1/ANREG2. Полная проверка подписи,
        // устройства и набора features остаётся в LicenseManager после импорта.
        byte[] prefixBytes = new byte[8];
        int read;
        try (FileInputStream input = new FileInputStream(file)) {
            read = input.read(prefixBytes);
        }
        if (read <= 0) {
            return false;
        }
        String prefix = new String(prefixBytes, 0, read, StandardCharsets.UTF_8);
        return prefix.startsWith("ANREG1:") || prefix.startsWith("ANREG2:");
    }

    private static String queryDisplayName(Context context, Uri uri) {
        if (context == null || uri == null) {
            return "";
        }
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    return value == null ? "" : value;
                }
            }
        } catch (Exception e) {
            AppLog.d(TAG, "cursor column read failed: " + e.getClass().getSimpleName());
        }
        return "";
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            AppLog.w(CHAIN, TAG, "LICENSE_PROFILE_REG_IMPORT_TEMP_DELETE_FAILED: " + file.getAbsolutePath());
        }
    }

    private static String safeError(Exception e) {
        if (e == null) {
            return "ошибка";
        }
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
