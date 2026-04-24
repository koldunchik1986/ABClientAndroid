package ru.neverlands.anclient;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import ru.neverlands.anclient.databinding.ActivityProfileBinding;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.CryptoUtils;

public class ProfileActivity extends AppCompatActivity {
    private ActivityProfileBinding binding;
    private UserConfig profile;
    private String originalUserNick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String profileId = getIntent().getStringExtra("profile_id");
        if (!TextUtils.isEmpty(profileId)) {
            java.util.List<UserConfig> profiles = UserConfig.loadAllProfiles(this);
            for (UserConfig p : profiles) {
                if (p.id.equals(profileId)) {
                    profile = p;
                    break;
                }
            }
        }

        if (profile == null) {
            profile = new UserConfig();
        }

        originalUserNick = profile.UserNick;

        binding.usernameEditText.setText(profile.UserNick);
        if (!profile.isEncrypted) {
            binding.passwordEditText.setText(profile.UserPassword);
            binding.flashPasswordEditText.setText(profile.UserPasswordFlash);
        }
        binding.autoLogonCheckBox.setChecked(profile.UserAutoLogon);
        binding.useProxyCheckBox.setChecked(profile.UseProxy);
        binding.proxyAddressEditText.setText(profile.ProxyAddress);
        binding.proxyUsernameEditText.setText(profile.ProxyUserName);
        binding.proxyPasswordEditText.setText(profile.ProxyPassword);
        binding.savePasswordsCheckBox.setChecked(profile.isEncrypted);

        binding.saveButton.setOnClickListener(v -> prepareSaveProfile());
        binding.testProxyButton.setOnClickListener(v -> startProxyConnectivityTest());
    }

    /**
     * Запускает асинхронный тест proxy-настроек из текущей формы профиля.
     *
     * Зависимости:
     * - UI-поля `proxyAddressEditText`, `proxyUsernameEditText`, `proxyPasswordEditText`;
     * - `OkHttpClient` (одноразовый клиент для probe-запроса);
     * - `AppVars.BROWSER_USER_AGENT` (браузерный User-Agent без идентификаторов клиента).
     *
     * Поведение:
     * - валидирует адрес proxy (`host[:port]` или `[ipv6]:port`);
     * - отправляет GET `http://neverlands.ru/` строго через указанный proxy;
     * - при наличии логина/пароля добавляет `Proxy-Authorization` через `proxyAuthenticator`;
     * - выводит результат в `Toast` без сохранения профиля.
     */
    private void startProxyConnectivityTest() {
        final String rawAddress = safeTrim(binding.proxyAddressEditText.getText() == null
                ? null
                : binding.proxyAddressEditText.getText().toString());
        final String proxyUser = safeTrim(binding.proxyUsernameEditText.getText() == null
                ? null
                : binding.proxyUsernameEditText.getText().toString());
        final String proxyPassword = safeTrim(binding.proxyPasswordEditText.getText() == null
                ? null
                : binding.proxyPasswordEditText.getText().toString());

        final ProxyEndpointParseResult endpoint = parseProxyEndpoint(rawAddress);
        if (!endpoint.isValid()) {
            Toast.makeText(this, "ТЕСТ ПРОКСИ: " + endpoint.errorMessage, Toast.LENGTH_LONG).show();
            return;
        }

        binding.testProxyButton.setEnabled(false);
        Toast.makeText(this, "ТЕСТ ПРОКСИ: выполняется проверка...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            final String resultMessage = performProxyConnectivityTest(endpoint, proxyUser, proxyPassword);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(ProfileActivity.this, resultMessage, Toast.LENGTH_LONG).show();
                    binding.testProxyButton.setEnabled(true);
                }
            });
        }, "ProfileProxyTestThread").start();
    }

    /**
     * Выполняет реальный HTTP probe-запрос на сервер игры через заданный proxy.
     *
     * Зависимости:
     * - `okhttp3.OkHttpClient` c `proxy(...)` и `proxyAuthenticator(...)`;
     * - `AppVars.BROWSER_USER_AGENT` для анти-детект совместимости.
     *
     * @param endpoint разобранный proxy endpoint.
     * @param proxyUser логин proxy (может быть пустым).
     * @param proxyPassword пароль proxy (может быть пустым).
     * @return строка результата для UI.
     */
    @NonNull
    private String performProxyConnectivityTest(@NonNull ProxyEndpointParseResult endpoint,
                                                @NonNull String proxyUser,
                                                @NonNull String proxyPassword) {
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(endpoint.host, endpoint.port));
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(45, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(false);

            if (!proxyUser.isEmpty() || !proxyPassword.isEmpty()) {
                builder.proxyAuthenticator((route, response) -> {
                    if (response.request().header("Proxy-Authorization") != null) {
                        return null;
                    }
                    String credentials = Credentials.basic(proxyUser, proxyPassword, StandardCharsets.UTF_8);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credentials)
                            .build();
                });
            }

            OkHttpClient client = builder.build();
            Request request = new Request.Builder()
                    .url("http://neverlands.ru/")
                    .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                if (code >= 200 && code < 400) {
                    return "ТЕСТ ПРОКСИ: OK (HTTP " + code + ")";
                }
                if (code == 407) {
                    return "ТЕСТ ПРОКСИ: ошибка авторизации proxy (HTTP 407)";
                }
                return "ТЕСТ ПРОКСИ: ошибка ответа proxy/сервера (HTTP " + code + ")";
            }
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "ТЕСТ ПРОКСИ: ошибка соединения (" + message + ")";
        }
    }

    /**
     * Разбирает адрес прокси в формат `host[:port]` или `[ipv6]:port`.
     *
     * Зависимости:
     * - используется только для локальной валидации кнопки "ТЕСТ ПРОКСИ";
     * - совпадает по ожидаемому формату с профилем (`UserConfig.ProxyAddress`).
     */
    @NonNull
    private ProxyEndpointParseResult parseProxyEndpoint(String rawAddress) {
        if (TextUtils.isEmpty(rawAddress)) {
            return ProxyEndpointParseResult.error("адрес прокси пуст");
        }

        String value = rawAddress.trim();
        String host;
        int port = 8080;

        if (value.startsWith("[")) {
            int closeIndex = value.indexOf(']');
            if (closeIndex <= 1) {
                return ProxyEndpointParseResult.error("неверный IPv6-формат (ожидается [host]:port)");
            }
            host = value.substring(1, closeIndex).trim();
            String tail = value.substring(closeIndex + 1).trim();
            if (!tail.isEmpty()) {
                if (!tail.startsWith(":")) {
                    return ProxyEndpointParseResult.error("неверный суффикс после IPv6-адреса");
                }
                String portText = tail.substring(1).trim();
                if (portText.isEmpty()) {
                    return ProxyEndpointParseResult.error("порт прокси пуст");
                }
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException e) {
                    return ProxyEndpointParseResult.error("порт прокси должен быть числом");
                }
            }
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon != -1 && firstColon != lastColon) {
                return ProxyEndpointParseResult.error("IPv6 без [] не поддерживается");
            }
            if (lastColon > 0) {
                host = value.substring(0, lastColon).trim();
                String portText = value.substring(lastColon + 1).trim();
                if (portText.isEmpty()) {
                    return ProxyEndpointParseResult.error("порт прокси пуст");
                }
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException e) {
                    return ProxyEndpointParseResult.error("порт прокси должен быть числом");
                }
            } else {
                host = value;
            }
        }

        if (host.isEmpty()) {
            return ProxyEndpointParseResult.error("host прокси пуст");
        }
        if (port <= 0 || port > 65535) {
            return ProxyEndpointParseResult.error("порт прокси вне диапазона 1..65535");
        }
        return ProxyEndpointParseResult.success(host, port);
    }

    @NonNull
    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * DTO результата разбора адреса proxy для тестовой кнопки.
     *
     * Зависимости:
     * - используется `parseProxyEndpoint(...)` и `performProxyConnectivityTest(...)`;
     * - не влияет на сохранение `UserConfig` и runtime-логику прокси.
     */
    private static final class ProxyEndpointParseResult {
        final String host;
        final int port;
        final String errorMessage;

        private ProxyEndpointParseResult(String host, int port, String errorMessage) {
            this.host = host;
            this.port = port;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        static ProxyEndpointParseResult success(String host, int port) {
            return new ProxyEndpointParseResult(host, port, "");
        }

        static ProxyEndpointParseResult error(String message) {
            return new ProxyEndpointParseResult("", -1, message);
        }

        boolean isValid() {
            return errorMessage.isEmpty();
        }
    }

    private void prepareSaveProfile() {
        if (binding.savePasswordsCheckBox.isChecked()) {
            if (!profile.isEncrypted) {
                // Если пароли еще не зашифрованы, показываем диалог для создания пароля шифрования
                showCreateEncryptionPasswordDialog();
            } else {
                // Если уже зашифровано, просто сохраняем. В будущем можно добавить смену пароля.
                saveProfile(null);
            }
        } else {
            // Если шифрование отключается, нужно расшифровать пароли, если они были зашифрованы
            if (profile.isEncrypted) {
                showEnterEncryptionPasswordToDecryptDialog();
            } else {
                saveProfile(null);
            }
        }
    }

    private void showCreateEncryptionPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Создать пароль шифрования");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_password, null);
        builder.setView(view);

        final EditText passwordField1 = view.findViewById(R.id.password_field1);
        final EditText passwordField2 = view.findViewById(R.id.password_field2);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String pass1 = passwordField1.getText().toString();
            String pass2 = passwordField2.getText().toString();
            if (TextUtils.isEmpty(pass1) || !pass1.equals(pass2)) {
                Toast.makeText(ProfileActivity.this, "Пароли не совпадают или пустые", Toast.LENGTH_SHORT).show();
            } else {
                saveProfile(pass1);
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEnterEncryptionPasswordToDecryptDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите пароль шифрования");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_enter_password, null);
        builder.setView(view);

        final EditText passwordField = view.findViewById(R.id.password_field);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String pass = passwordField.getText().toString();
            try {
                // Пытаемся расшифровать, чтобы проверить пароль
                String decryptedPassword = CryptoUtils.decrypt(profile.UserPassword, pass);
                String decryptedFlashPassword = CryptoUtils.decrypt(profile.UserPasswordFlash, pass);

                // Сохраняем расшифрованные пароли
                profile.UserPassword = decryptedPassword;
                profile.UserPasswordFlash = decryptedFlashPassword;
                profile.isEncrypted = false;
                saveProfile(null); // Сохраняем в открытом виде

            } catch (Exception e) {
                Toast.makeText(ProfileActivity.this, "Неверный пароль шифрования", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.show();
    }


    private void saveProfile(String encryptionPassword) {
        String newUsername = binding.usernameEditText.getText().toString().trim();
        if (newUsername.isEmpty()) {
            Toast.makeText(this, "Имя пользователя не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        // Если имя пользователя было изменено, нужно удалить старый файл профиля
        if (originalUserNick != null && !originalUserNick.isEmpty() && !originalUserNick.equals(newUsername)) {
            File oldFile = new File(ru.neverlands.anclient.utils.DataManager.getProfilesDir(), originalUserNick + ".profile");
            if(oldFile.exists()) oldFile.delete();
        }

        profile.UserNick = newUsername;
        profile.UserAutoLogon = binding.autoLogonCheckBox.isChecked();
        profile.UseProxy = binding.useProxyCheckBox.isChecked();
        profile.ProxyAddress = binding.proxyAddressEditText.getText().toString().trim();
        profile.ProxyUserName = binding.proxyUsernameEditText.getText().toString().trim();
        profile.ProxyPassword = binding.proxyPasswordEditText.getText().toString().trim();

        // --- Логика сохранения пароля --- //

        // Случай 1: Происходит шифрование (пользователь создал пароль шифрования)
        if (encryptionPassword != null) {
            String password = binding.passwordEditText.getText().toString();
            String flashPassword = binding.flashPasswordEditText.getText().toString();
            try {
                profile.UserPassword = CryptoUtils.encrypt(password, encryptionPassword);
                profile.UserPasswordFlash = CryptoUtils.encrypt(flashPassword, encryptionPassword);
                profile.isEncrypted = true;
            } catch (Exception e) {
                Toast.makeText(this, "Ошибка шифрования", Toast.LENGTH_SHORT).show();
                return;
            }
        } 
        // Случай 2: Шифрование ОТКЛЮЧЕНО (галочка снята)
        else if (!binding.savePasswordsCheckBox.isChecked()) {
            // Пароли либо уже были расшифрованы в диалоге, либо вводятся как есть.
            profile.UserPassword = binding.passwordEditText.getText().toString();
            profile.UserPasswordFlash = binding.flashPasswordEditText.getText().toString();
            profile.isEncrypted = false;
        }
        // Случай 3 (неявный): Шифрование ВКЛЮЧЕНО, но новый пароль шифрования не вводится.
        // Это значит, что мы просто сохраняем другие изменения в профиле.
        // В этом случае мы НЕ ТРОГАЕМ поля паролей в объекте `profile`,
        // так как они уже содержат нужные зашифрованные значения, а поля ввода на экране пусты.

        profile.save(this);

        setResult(RESULT_OK);
        finish();
    }
}
