package ru.neverlands.abclient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.net.HttpCookie;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.databinding.ActivityLoginBinding;
import ru.neverlands.abclient.model.AuthResult;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.network.NetworkClient;
import ru.neverlands.abclient.proxy.ProxyRuntimeManager;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.CryptoUtils;

public class LoginActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final int COOKIE_WARMUP_MAX_ATTEMPTS = 2;
    private static final long COOKIE_WARMUP_DELAY_MS = 250L;
    private static final int AUTH_MAX_RETRY_ATTEMPTS = 1;
    private static final long AUTH_RETRY_DELAY_MS = 1200L;
    private static final String LOGIN_UI_PREFS = "login_ui_state";
    private static final String KEY_LAST_PROFILE_ID = "last_profile_id";
    private static final String KEY_ENCRYPTED_LOGIN_PASSWORD_PREFIX = "encrypted_login_password_";
    /**
     * При длительном полном сетевом timeout авто-retry только удваивает время ожидания входа.
     * Поэтому для "длинных" таймаутов повтор не выполняем.
     */
    private static final long AUTH_NO_RETRY_TIMEOUT_MS = 25_000L;
    /**
     * Извлекает из текста ошибки значение "after N ms" (формат OkHttp/SocketException).
     */
    private static final Pattern AUTH_AFTER_MS_PATTERN =
            Pattern.compile("after\\s+(\\d+)ms", Pattern.CASE_INSENSITIVE);
    private ActivityLoginBinding binding;
    private List<UserConfig> profiles;
    private UserConfig selectedProfile;

    private final ActivityResultLauncher<Intent> profileActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadProfiles();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setVersionName();

        if (checkAndRequestPermissions()) {
            initializeUi();
        }
    }

    private void setVersionName() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            binding.versionTextView.setText("v" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            binding.versionTextView.setText("");
        }
    }

    private void initializeUi() {
        loadProfiles();

        binding.loginButton.setOnClickListener(v -> login());
        binding.addProfileButton.setOnClickListener(v -> openProfileActivity(null));
        binding.editProfileButton.setOnClickListener(v -> {
            if (selectedProfile != null) {
                openProfileActivity(selectedProfile);
            }
        });

        binding.deleteProfileButton.setOnClickListener(v -> deleteSelectedProfile());
    }

    private void deleteSelectedProfile() {
        if (selectedProfile == null || selectedProfile.UserNick.isEmpty()) {
            Toast.makeText(this, "Не выбран профиль для удаления", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Удаление профиля")
                .setMessage("Вы уверены, что хотите удалить профиль '" + selectedProfile.UserNick + "'? Это действие необратимо.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    clearSavedEncryptedLoginPassword(selectedProfile);
                    selectedProfile.delete(this);
                    Toast.makeText(this, "Профиль '" + selectedProfile.UserNick + "' удален", Toast.LENGTH_SHORT).show();
                    loadProfiles(); // Перезагружаем список профилей
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private boolean checkAndRequestPermissions() {
        // На Android 10+ (API 29+) для работы с app-specific внешней папкой
        // (getExternalFilesDir) runtime-разрешение на storage не требуется.
        // Зависимости:
        // - UserConfig/DataManager/логи используют только app-specific storage,
        // - поэтому WRITE_EXTERNAL_STORAGE нужен только для старых Android (до API 29).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
                return false;
            } else {
                return true;
            }
        } else {
            // Permission is automatically granted on sdk < 23 upon installation
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                initializeUi();
            } else {
                // Permission denied
                Toast.makeText(this, "Разрешение на доступ к хранилищу необходимо для работы приложения", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void loadProfiles() {
        profiles = UserConfig.loadAllProfiles(this);

        if (profiles.isEmpty()) {
            // Если профилей нет, создаем пустой, чтобы пользователь мог его настроить
            selectedProfile = new UserConfig();
            profiles.add(selectedProfile);
        } else {
            // Ищем последний использованный профиль
            String persistedProfileId = getSharedPreferences(LOGIN_UI_PREFS, MODE_PRIVATE)
                    .getString(KEY_LAST_PROFILE_ID, "");
            UserConfig persistedProfile = findProfileById(persistedProfileId);
            if (persistedProfile != null) {
                selectedProfile = persistedProfile;
            } else {
                UserConfig lastUsed = profiles.get(0);
                for (UserConfig profile : profiles) {
                    if (profile.LastLogin > lastUsed.LastLogin) {
                        lastUsed = profile;
                    }
                }
                selectedProfile = lastUsed;
            }
        }

        ArrayAdapter<UserConfig> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.profileSpinner.setAdapter(adapter);

        // Для AutoCompleteTextView выбранный профиль выставляется через текст,
        // а не через индекс (setSelection у TextView управляет позицией курсора).
        if (selectedProfile != null) {
            binding.profileSpinner.setText(selectedProfile.toString(), false);
            applySelectedProfile(selectedProfile);
        }

        android.util.Log.i(
                "LoginActivity",
                "LOGIN_UI: profilesLoaded=" + profiles.size()
                        + ", selectedId=" + (selectedProfile == null ? "" : selectedProfile.id)
                        + ", selectedNick=" + (selectedProfile == null ? "" : selectedProfile.UserNick)
                        + ", encrypted=" + (selectedProfile != null && selectedProfile.isEncrypted)
                        + ", autoLogon=" + (selectedProfile != null && selectedProfile.UserAutoLogon)
                        + ", savedPasswordLen=" + safeLen(selectedProfile == null ? null : selectedProfile.UserPassword)
        );

        binding.profileSpinner.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < profiles.size()) {
                selectedProfile = profiles.get(position);
                applySelectedProfile(selectedProfile);
            }
        });
    }

    private void applySelectedProfile(UserConfig profile) {
        if (profile == null) return;

        if (profile.isEncrypted) {
            binding.passwordInputLayout.setHint("Пароль шифрования");
            String savedEncryptionPassword = getSavedEncryptedLoginPassword(profile);
            binding.passwordEditText.setText(savedEncryptionPassword);
            binding.rememberCheckBox.setChecked(!TextUtils.isEmpty(savedEncryptionPassword));
            android.util.Log.i(
                    "LoginActivity",
                    "LOGIN_UI: applySelectedProfile id=" + profile.id
                            + ", encrypted=true"
                            + ", autoLogon=" + binding.rememberCheckBox.isChecked()
                            + ", savedPasswordLen=" + safeLen(savedEncryptionPassword)
            );
            return;
        }

        binding.passwordInputLayout.setHint("Пароль");
        String savedPassword = profile.UserPassword != null ? profile.UserPassword : "";
        binding.passwordEditText.setText(savedPassword);
        binding.rememberCheckBox.setChecked(profile.UserAutoLogon && !TextUtils.isEmpty(savedPassword));
        android.util.Log.i(
                "LoginActivity",
                "LOGIN_UI: applySelectedProfile id=" + profile.id
                        + ", encrypted=false"
                        + ", autoLogon=" + profile.UserAutoLogon
                        + ", savedPasswordLen=" + safeLen(savedPassword)
        );
    }

    private UserConfig findProfileById(String profileId) {
        if (TextUtils.isEmpty(profileId) || profiles == null || profiles.isEmpty()) {
            return null;
        }
        for (UserConfig profile : profiles) {
            if (profile != null && profileId.equals(profile.id)) {
                return profile;
            }
        }
        return null;
    }

    private void persistLastProfileId(UserConfig profile) {
        if (profile == null || TextUtils.isEmpty(profile.id)) {
            return;
        }
        getSharedPreferences(LOGIN_UI_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_PROFILE_ID, profile.id)
                .apply();
    }

    private void clearSavedEncryptedLoginPassword(UserConfig profile) {
        if (profile == null || TextUtils.isEmpty(profile.id)) {
            return;
        }
        getSharedPreferences(LOGIN_UI_PREFS, MODE_PRIVATE)
                .edit()
                .remove(KEY_ENCRYPTED_LOGIN_PASSWORD_PREFIX + profile.id)
                .apply();
    }

    private String getSavedEncryptedLoginPassword(UserConfig profile) {
        if (profile == null || !profile.isEncrypted || TextUtils.isEmpty(profile.id)) {
            return "";
        }
        return getSharedPreferences(LOGIN_UI_PREFS, MODE_PRIVATE)
                .getString(KEY_ENCRYPTED_LOGIN_PASSWORD_PREFIX + profile.id, "");
    }

    /**
     * Stores/clears encryption password for encrypted profiles on login screen.
     * This value is kept outside of profile XML because profile.UserPassword stores
     * encrypted game password payload, not the encryption key entered on login screen.
     */
    private void persistEncryptedLoginPasswordSnapshot(UserConfig profile,
                                                       String encryptionPassword,
                                                       boolean rememberChecked,
                                                       String stage) {
        if (profile == null || !profile.isEncrypted || TextUtils.isEmpty(profile.id)) {
            return;
        }
        String normalizedPassword = encryptionPassword == null ? "" : encryptionPassword;
        String valueToStore = rememberChecked ? normalizedPassword : "";
        String key = KEY_ENCRYPTED_LOGIN_PASSWORD_PREFIX + profile.id;
        String previousValue = getSharedPreferences(LOGIN_UI_PREFS, MODE_PRIVATE)
                .getString(key, "");
        boolean changed = !TextUtils.equals(previousValue, valueToStore);
        getSharedPreferences(LOGIN_UI_PREFS, MODE_PRIVATE)
                .edit()
                .putString(key, valueToStore)
                .apply();
        persistLastProfileId(profile);
        android.util.Log.i(
                "LoginActivity",
                "LOGIN_UI: persistEncryptedRemember stage=" + stage
                        + ", profileId=" + profile.id
                        + ", rememberChecked=" + rememberChecked
                        + ", savedPasswordLen=" + safeLen(valueToStore)
                        + ", changed=" + changed
        );
    }

    /**
     * Синхронно фиксирует состояние "Запомнить пароль" для выбранного профиля.
     *
     * Важно:
     * - используется до auth-flow (stage=login_click) и после успешного входа (stage=login_success),
     *   чтобы пароль не терялся при переходе LoginActivity -> MainActivity -> Logout -> LoginActivity;
     * - для шифрованных профилей не применяется.
     */
    private void persistRememberPasswordSnapshot(UserConfig profile, String plainPassword, String stage) {
        if (profile == null || profile.isEncrypted) {
            return;
        }
        String normalizedPassword = plainPassword == null ? "" : plainPassword;
        String valueToStore = profile.UserAutoLogon ? normalizedPassword : "";
        boolean changed = !TextUtils.equals(profile.UserPassword, valueToStore);
        profile.UserPassword = valueToStore;
        profile.save(LoginActivity.this);
        persistLastProfileId(profile);
        android.util.Log.i(
                "LoginActivity",
                "LOGIN_UI: persistRemember stage=" + stage
                        + ", profileId=" + profile.id
                        + ", autoLogon=" + profile.UserAutoLogon
                        + ", savedPasswordLen=" + safeLen(profile.UserPassword)
                        + ", changed=" + changed
        );
    }

    private static int safeLen(String value) {
        return value == null ? 0 : value.length();
    }

    private void openProfileActivity(UserConfig profile) {
        Intent intent = new Intent(this, ProfileActivity.class);
        if (profile != null && profile.id != null) {
            intent.putExtra("profile_id", profile.id);
        }
        profileActivityLauncher.launch(intent);
    }

    private void login() {
        String username = selectedProfile.UserNick;
        String passwordOrKey = binding.passwordEditText.getText().toString().trim();

        if (username.isEmpty()) {
            Toast.makeText(this, "Выберите или создайте профиль", Toast.LENGTH_SHORT).show();
            return;
        }

        if (passwordOrKey.isEmpty()) {
            Toast.makeText(this, binding.passwordInputLayout.getHint(), Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);

        final UserConfig profileToLogin = selectedProfile;
        if (!profileToLogin.isEncrypted) {
            profileToLogin.UserAutoLogon = binding.rememberCheckBox.isChecked();
            persistRememberPasswordSnapshot(profileToLogin, passwordOrKey, "login_click");
        } else {
            persistEncryptedLoginPasswordSnapshot(
                    profileToLogin,
                    passwordOrKey,
                    binding.rememberCheckBox.isChecked(),
                    "login_click"
            );
        }
        android.util.Log.i(
                "LoginActivity",
                "LOGIN_UI: loginClick profileId=" + profileToLogin.id
                        + ", encrypted=" + profileToLogin.isEncrypted
                        + ", rememberChecked=" + binding.rememberCheckBox.isChecked()
                        + ", inputPasswordLen=" + safeLen(passwordOrKey)
        );
        String gamePassword;

        if (profileToLogin.isEncrypted) {
            try {
                gamePassword = CryptoUtils.decrypt(profileToLogin.UserPassword, passwordOrKey);
            } catch (Exception e) {
                Toast.makeText(this, "Неверный пароль шифрования", Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
                binding.loginButton.setEnabled(true);
                return;
            }
        } else {
            gamePassword = passwordOrKey;
        }

        clearCookiesAndAuthorize(username, gamePassword, profileToLogin);
    }

    private void clearCookiesAndAuthorize(String username, String gamePassword, UserConfig profileToLogin) {
        // Поднимаем proxy runtime до auth-flow, чтобы первый вход уже шёл через единый контур,
        // как в ПК версии (proxy стартует до основной логики приложения).
        android.util.Log.i(
                "LoginActivity",
                "PROXY_BOOT: login pre-auth start, doProxy=" + profileToLogin.DoProxy
                        + ", useProxy=" + profileToLogin.UseProxy
                        + ", address=" + (profileToLogin.ProxyAddress == null ? "" : profileToLogin.ProxyAddress)
        );
        boolean proxyReady = ProxyRuntimeManager.ensureStarted(getApplicationContext(), profileToLogin);
        if (!proxyReady) {
            String reason = ProxyRuntimeManager.getLastStartError();
            android.util.Log.e(
                    "LoginActivity",
                    "PROXY_FAIL: proxy runtime start failed before authorize, reason="
                            + (reason == null ? "" : reason)
            );
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            String message = (reason == null || reason.trim().isEmpty())
                    ? "Не удалось запустить прокси-контур входа"
                    : "Ошибка прокси: " + reason;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        android.util.Log.i(
                "LoginActivity",
                "PROXY_BOOT: proxy runtime ready, port=" + ProxyRuntimeManager.getActivePort()
        );

        // Пересобираем сетевой клиент, чтобы он использовал актуальный localhost proxy endpoint.
        NetworkClient.invalidateInstance();

        // Each new login must start from a clean cookie state (desktop behavior).
        AppVars.lastCookies = null;
        NetworkClient.clearCookies();
        clearCookiesAndAuthorizeInternal(username, gamePassword, profileToLogin, 0);
    }

    private void clearCookiesAndAuthorizeInternal(
            String username,
            String gamePassword,
            UserConfig profileToLogin,
            int warmupAttempt
    ) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        try {
            CookieManager.getInstance();
        } catch (Throwable t) {
            if (warmupAttempt < COOKIE_WARMUP_MAX_ATTEMPTS) {
                android.util.Log.w(
                        "LoginActivity",
                        "CookieManager warm-up failed, retry " + (warmupAttempt + 1) + "/" + COOKIE_WARMUP_MAX_ATTEMPTS,
                        t
                );
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> clearCookiesAndAuthorizeInternal(username, gamePassword, profileToLogin, warmupAttempt + 1),
                        COOKIE_WARMUP_DELAY_MS
                );
                return;
            }
            android.util.Log.w("LoginActivity", "CookieManager warm-up failed, continue authorize flow", t);
        }

        CookiesManager.clear(value -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            startAuthorizeRequest(username, gamePassword, profileToLogin, 0);
        });
    }

    /**
     * Запускает синхронный auth-flow в фоновом потоке с контролем retry-индекса.
     *
     * Зависимости:
     * - {@link AuthManager#authorize(String, String)} как основной HTTP-пайплайн входа;
     * - главный поток (`Handler`) для безопасной работы с UI и переходами активностей;
     * - {@link #handleAuthResult(AuthResult, String, String, UserConfig, int, long)} для развилки
     *   успех/капча/авто-повтор/финальная ошибка.
     */
    private void startAuthorizeRequest(String username, String gamePassword, UserConfig profileToLogin, int retryAttempt) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        final long attemptStartedAtMs = System.currentTimeMillis();
        android.util.Log.d(
                "LoginActivity",
                "Authorization attempt start: retryAttempt=" + retryAttempt + ", startedAtMs=" + attemptStartedAtMs
        );
        executor.execute(() -> {
            AuthManager authManager = new AuthManager();
            AuthResult result = authManager.authorize(username, gamePassword);
            handler.post(() -> handleAuthResult(
                    result,
                    username,
                    gamePassword,
                    profileToLogin,
                    retryAttempt,
                    attemptStartedAtMs
            ));
        });
        // Одноразовый executor для конкретной попытки входа.
        executor.shutdown();
    }

    /**
     * Обрабатывает итог попытки авторизации и выполняет один авто-retry для кратковременных
     * сетевых срывов ("failed to connect"/timeout), чтобы вход проходил с одного нажатия.
     *
     * Зависимости:
     * - {@link #isRetriableAuthError(String, int, long)} для классификации transient-сбоев;
     * - {@link #startAuthorizeRequest(String, String, UserConfig, int)} для повторной попытки;
     * - UI-состояние кнопки/прогресса (`binding.loginButton`, `binding.progressBar`).
     */
    private void handleAuthResult(AuthResult result,
                                  String username,
                                  String gamePassword,
                                  UserConfig profileToLogin,
                                  int retryAttempt,
                                  long attemptStartedAtMs) {
        long attemptElapsedMs = Math.max(0L, System.currentTimeMillis() - attemptStartedAtMs);
        android.util.Log.d(
                "LoginActivity",
                "Authorization attempt result: retryAttempt=" + retryAttempt
                        + ", elapsedMs=" + attemptElapsedMs
                        + ", success=" + (result != null && result.isSuccess())
                        + ", captcha=" + (result != null && result.isCaptchaRequired())
        );
        if (result.isSuccess()) {
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            onLoginSuccess(result.getCookies(), gamePassword, profileToLogin);
        } else if (result.isCaptchaRequired()) {
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            showCaptchaDialog(username, gamePassword, result.getCaptchaUrl(), result.getVcode(), profileToLogin);
        } else {
            String errorMessage = result != null && result.getErrorMessage() != null
                    ? result.getErrorMessage()
                    : "Ошибка авторизации";

            if (isRetriableAuthError(errorMessage, retryAttempt, attemptElapsedMs)) {
                android.util.Log.w(
                        "LoginActivity",
                        "Authorization transient error, auto-retry " + (retryAttempt + 1) + "/" + AUTH_MAX_RETRY_ATTEMPTS
                                + ", elapsedMs=" + attemptElapsedMs + ": " + errorMessage
                );
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.loginButton.setEnabled(false);
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> startAuthorizeRequest(username, gamePassword, profileToLogin, retryAttempt + 1),
                        AUTH_RETRY_DELAY_MS
                );
                return;
            }

            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            android.util.Log.w("LoginActivity", "Authorization error: " + errorMessage);
            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Классифицирует ошибки авторизации, которые безопасно повторять автоматически.
     *
     * Зависимости:
     * - текст ошибки из {@link AuthResult#getErrorMessage()},
     * - лимит авто-повторов {@link #AUTH_MAX_RETRY_ATTEMPTS}.
     */
    private boolean isRetriableAuthError(String errorMessage, int retryAttempt, long attemptElapsedMs) {
        if (retryAttempt >= AUTH_MAX_RETRY_ATTEMPTS || errorMessage == null) {
            return false;
        }
        long timeoutFromMessageMs = extractTimeoutMsFromError(errorMessage);
        if (attemptElapsedMs >= AUTH_NO_RETRY_TIMEOUT_MS
                || timeoutFromMessageMs >= AUTH_NO_RETRY_TIMEOUT_MS) {
            android.util.Log.d(
                    "LoginActivity",
                    "Authorization retry skipped: long-timeout detected, elapsedMs="
                            + attemptElapsedMs + ", timeoutFromMessageMs=" + timeoutFromMessageMs
            );
            return false;
        }
        String lower = errorMessage.toLowerCase();
        return lower.contains("failed to connect")
                || lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("connection reset")
                || lower.contains("unable to resolve host")
                || lower.contains("не удалось подключиться")
                || lower.contains("таймаут");
    }

    /**
     * Извлекает timeout в миллисекундах из хвоста ошибки вида "after 30000ms".
     *
     * Зависимости:
     * - формат текста ошибок OkHttp/SocketException;
     * - {@link #AUTH_AFTER_MS_PATTERN}.
     */
    private long extractTimeoutMsFromError(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return -1L;
        }
        Matcher matcher = AUTH_AFTER_MS_PATTERN.matcher(errorMessage);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private void onLoginSuccess(List<HttpCookie> cookies, String gamePassword, UserConfig profileToLogin) {
        // Сохраняем куки для последующей передачи в WebView
        AppVars.lastCookies = cookies;
        profileToLogin.LastLogin = currentDotNetTicks();

        // Дополнительно фиксируем состояние remember/password после успешного входа.
        if (!profileToLogin.isEncrypted) {
            persistRememberPasswordSnapshot(profileToLogin, gamePassword, "login_success");
        } else {
            profileToLogin.save(LoginActivity.this);
            persistLastProfileId(profileToLogin);
        }

        // Устанавливаем глобальный профиль для сессии
        AppVars.Profile = profileToLogin;
        
        // Синхронизируем состояние автобоя с профилем
        if (profileToLogin.LezDoAutoboi) {
            AppVars.Autoboi = ru.neverlands.abclient.model.AutoboiState.AutoboiOn;
        } else {
            AppVars.Autoboi = ru.neverlands.abclient.model.AutoboiState.AutoboiOff;
        }

        // Синхронизируем режим "Снежок/Ярость" (первый удар на осаде) с профилем.
        AppVars.DoFury = profileToLogin.hasAnyLezFuryGroup();
        profileToLogin.LezDoFury = AppVars.DoFury;
        AppVars.AutoFuryCheckScroll = AppVars.DoFury;
        AppVars.AutoFuryArmedScroll = false;
        AppVars.AutoFuryHand = "";
        AppVars.AutoFuryHandD = "";

        // Запускаем фоновое обновление всех контактов
        android.util.Log.d("LoginActivity", "Starting background contact refresh after successful login.");
        List<ru.neverlands.abclient.model.Contact> contactsToUpdate = ru.neverlands.abclient.manager.ContactsManager.getContactsFromCache();
        if (contactsToUpdate != null && !contactsToUpdate.isEmpty()) {
            updateContactsRecursive(contactsToUpdate, 0);
        }

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Рекурсивно обновляет список контактов один за другим с задержкой.
     */
    private void updateContactsRecursive(final List<ru.neverlands.abclient.model.Contact> contacts, final int index) {
        if (index >= contacts.size()) {
            android.util.Log.d("LoginActivity", "Background contact refresh completed.");
            // Опционально: можно показать Toast, но это может сбить пользователя с толку, т.к. он уже на другом экране
            // runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Контакты обновлены в фоне", Toast.LENGTH_SHORT).show());
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            final ru.neverlands.abclient.model.Contact oldContact = contacts.get(index);
            if (oldContact.playerID == null || oldContact.playerID.isEmpty()) {
                updateContactsRecursive(contacts, index + 1);
                return;
            }

            ru.neverlands.abclient.repository.ApiRepository.getPlayerInfo(oldContact.playerID, new ru.neverlands.abclient.repository.ApiRepository.ApiCallback<ru.neverlands.abclient.model.Contact>() {
                @Override
                public void onSuccess(ru.neverlands.abclient.model.Contact newContact) {
                    newContact.classId = oldContact.classId;
                    newContact.comment = oldContact.comment;
                    // Сохраняем персональный toolId при фоновом обновлении после логина.
                    newContact.toolId = oldContact.toolId;
                    ru.neverlands.abclient.manager.ContactsManager.updateContact(newContact);
                    updateContactsRecursive(contacts, index + 1);
                }

                @Override
                public void onFailure(String message) {
                    android.util.Log.e("LoginActivity", "Failed to refresh contact by ID " + oldContact.playerID + ": " + message);
                    updateContactsRecursive(contacts, index + 1);
                }
            });
        }, 500);
    }

    private static long currentDotNetTicks() {
        long unixEpochMs = System.currentTimeMillis();
        return (unixEpochMs + 62135596800000L) * 10_000L;
    }

    private void showCaptchaDialog(String username, String gamePassword, String captchaUrl, String vcode, UserConfig profileToLogin) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_captcha, null);
        builder.setView(dialogView);

        ImageView captchaImageView = dialogView.findViewById(R.id.captchaImageView);
        EditText captchaEditText = dialogView.findViewById(R.id.captchaEditText);
        ProgressBar captchaProgressBar = dialogView.findViewById(R.id.captchaProgressBar);

        captchaProgressBar.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(captchaUrl)
                .into(captchaImageView)
                .onLoadFailed(null);
        captchaProgressBar.setVisibility(View.GONE);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String verify = captchaEditText.getText().toString().trim();
            if (!verify.isEmpty()) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.loginButton.setEnabled(false);

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());
                final long attemptStartedAtMs = System.currentTimeMillis();

                executor.execute(() -> {
                    AuthManager authManager = new AuthManager();
                    AuthResult result = authManager.authorizeWithCaptcha(username, gamePassword, vcode, verify);
                    handler.post(() -> handleAuthResult(
                            result,
                            username,
                            gamePassword,
                            profileToLogin,
                            0,
                            attemptStartedAtMs
                    ));
                });
                executor.shutdown();
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }
}
