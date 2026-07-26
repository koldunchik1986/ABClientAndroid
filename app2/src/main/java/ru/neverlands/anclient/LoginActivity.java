package ru.neverlands.anclient;


import ru.neverlands.anclient.utils.AppLog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.databinding.ActivityLoginBinding;
import ru.neverlands.anclient.license.LicenseStatus;
import ru.neverlands.anclient.license.LicenseValidationHandler;
import ru.neverlands.anclient.model.AuthResult;
import ru.neverlands.anclient.model.UserConfig;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.proxy.ProxyRuntimeManager;
import ru.neverlands.anclient.proxy.CookiesManager;
import ru.neverlands.anclient.ui.LicenseRequestDialog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.CryptoUtils;
import ru.neverlands.anclient.utils.GameServerUrls;

public class LoginActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final int COOKIE_WARMUP_MAX_ATTEMPTS = 2;
    private static final long COOKIE_WARMUP_DELAY_MS = 250L;
    private static final int AUTH_MAX_RETRY_ATTEMPTS = 1;
    private static final long AUTH_RETRY_DELAY_MS = 1200L;
    private static final long SERVER_PING_REFRESH_MS = 10_000L;
    private static final int SERVER_PING_TIMEOUT_MS = 2_500;

    /**
     * Отложенный старт фонового обновления контактов после успешного входа.
     *
     * Зачем: сразу после логина клиент и так грузит главный фрейм, чат, комнату и probe-запросы.
     * По логам {@code logs/Critical/20260726_14_20_*} очередь локального прокси в этот момент
     * доходила до {@code waitMs=7877}, а сервер отвечал страницей «Сеанс работы прерван»
     * (причина «попытка войти в другом окне») и {@code Server error: 535} на {@code info.cgi}.
     * Даём входу полностью отработать, и только потом начинаем обновлять контакты.
     */
    private static final long LOGIN_CONTACT_REFRESH_START_DELAY_MS = 8_000L;

    /**
     * Пауза между запросами {@code info.cgi} при фоновом обновлении контактов.
     *
     * Раньше здесь было 500 мс — вдвое агрессивнее, чем в
     * {@code ContactsManager.updateContactsRecursive(...)} (1200 мс), что и приводило
     * к anti-rate-limit ответам {@code 535/536}. Приведено к единому значению.
     */
    private static final long LOGIN_CONTACT_REFRESH_STEP_DELAY_MS = 1_200L;
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
    private String pendingProfileRegImportPath = "";
    private ArrayAdapter<String> serverAdapter;
    private final Handler serverPingHandler = new Handler(Looper.getMainLooper());
    private ExecutorService serverPingExecutor;
    private Runnable serverPingRunnable;
    private final Map<String, Long> serverPingMsByCode = new ConcurrentHashMap<>();

    private final ActivityResultLauncher<Intent> profileActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadProfiles();
                }
            });

    private final ActivityResultLauncher<Intent> profileRegImportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // После успешного импорта сразу повторяем login: пользователь уже выбрал
                // корректный profile.reg, дополнительное ручное нажатие не требуется.
                String destinationPath = pendingProfileRegImportPath;
                pendingProfileRegImportPath = "";
                if (result.getResultCode() != RESULT_OK) {
                    return;
                }
                Intent data = result.getData();
                Uri uri = data == null ? null : data.getData();
                LicenseRequestDialog.copyProfileRegFromUri(
                        this,
                        uri,
                        destinationPath,
                        destinationFile -> login()
                );
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
            AppLog.w("LoginActivity", "versionName lookup failed", e);
            binding.versionTextView.setText("");
        }
    }

    private void initializeUi() {
        setupServerSelector();
        loadProfiles();

        binding.loginButton.setOnClickListener(v -> login());
        binding.addProfileButton.setOnClickListener(v -> openProfileActivity(null));
        binding.editProfileButton.setOnClickListener(v -> {
            if (selectedProfile != null) {
                openProfileActivityForEdit(selectedProfile);
            }
        });

        binding.deleteProfileButton.setOnClickListener(v -> deleteSelectedProfile());
    }

    private void setupServerSelector() {
        GameServerUrls.initialize(this);
        serverAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(Arrays.asList(buildServerDisplayNames()))
        );
        binding.serverSpinner.setAdapter(serverAdapter);
        binding.serverSpinner.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            String serverCode = GameServerUrls.codeForDisplayValue(item == null ? "" : item.toString());
            applyServerCodeToSelectedProfile(serverCode, "selector_change", true);
        });
        binding.editServersButton.setOnClickListener(v -> showEditServersDialog());
        startServerPingRefresh();
    }

    private void showEditServersDialog() {
        EditText editor = new EditText(this);
        editor.setSingleLine(false);
        editor.setMinLines(5);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setText(GameServerUrls.editableServerListText());
        editor.setSelection(editor.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Серверы Neverlands")
                .setMessage("Формат строки: CODE=host|server. Для neverlands.ru параметр server можно не указывать.")
                .setView(editor)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton("Отмена", null)
                .setNeutralButton("Сброс", null)
                .create();
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    GameServerUrls.saveEditableServerList(this, editor.getText().toString());
                    onServerListChanged("edit_save");
                    dialog.dismiss();
                    Toast.makeText(this, "Список серверов сохранен", Toast.LENGTH_SHORT).show();
                } catch (IllegalArgumentException e) {
                    editor.setError(e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                GameServerUrls.resetServerList(this);
                editor.setText(GameServerUrls.editableServerListText());
                editor.setSelection(editor.getText().length());
                onServerListChanged("edit_reset");
                Toast.makeText(this, "Список серверов сброшен", Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    private void onServerListChanged(String stage) {
        serverPingMsByCode.clear();
        if (selectedProfile != null) {
            selectedProfile.GameServerCode = GameServerUrls.normalizeServerCode(selectedProfile.GameServerCode);
            if (!TextUtils.isEmpty(selectedProfile.UserNick)) {
                selectedProfile.save(this);
            }
        }
        updateServerDisplayNames();
        refreshServerPingsAsync();
        AppLog.i("LoginActivity", "LOGIN_UI: serverListChanged stage=" + stage
                + ", selected=" + (selectedProfile == null ? "" : selectedProfile.GameServerCode));
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

        AppLog.i(
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

        String serverCode = GameServerUrls.normalizeServerCode(profile.GameServerCode);
        profile.GameServerCode = serverCode;
        binding.serverSpinner.setText(serverDisplayName(serverCode), false);

        if (profile.isEncrypted) {
            binding.passwordInputLayout.setHint("Пароль шифрования");
            String savedEncryptionPassword = getSavedEncryptedLoginPassword(profile);
            binding.passwordEditText.setText(savedEncryptionPassword);
            binding.rememberCheckBox.setChecked(!TextUtils.isEmpty(savedEncryptionPassword));
            AppLog.i(
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
        AppLog.i(
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
        AppLog.i(
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
        AppLog.i(
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
        openProfileActivity(profile, null);
    }

    private void openProfileActivity(UserConfig profile, String encryptionPassword) {
        Intent intent = new Intent(this, ProfileActivity.class);
        if (profile != null && profile.id != null) {
            intent.putExtra("profile_id", profile.id);
        }
        if (!TextUtils.isEmpty(encryptionPassword)) {
            intent.putExtra(ProfileActivity.EXTRA_ENCRYPTION_PASSWORD, encryptionPassword);
        }
        profileActivityLauncher.launch(intent);
    }

    private void openProfileActivityForEdit(UserConfig profile) {
        if (profile == null) {
            return;
        }
        if (!profile.isEncrypted) {
            openProfileActivity(profile);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите пароль шифрования");
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_enter_password, null);
        builder.setView(view);

        final EditText passwordField = view.findViewById(R.id.password_field);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String encryptionPassword = passwordField.getText().toString();
            try {
                decryptProfileSecret(profile.UserPassword, encryptionPassword);
                decryptProfileSecret(profile.UserPasswordFlash, encryptionPassword);
                openProfileActivity(profile, encryptionPassword);
            } catch (Exception e) {
                Toast.makeText(LoginActivity.this, "Неверный пароль шифрования", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        builder.show();
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

        final UserConfig profileToLogin = selectedProfile;
        String selectedServerText = binding.serverSpinner.getText() == null
                ? ""
                : binding.serverSpinner.getText().toString();
        applyServerCodeToSelectedProfile(
                GameServerUrls.codeForDisplayValue(selectedServerText),
                "login_click",
                false
        );
        String gamePassword;
        String flashPassword;

        if (profileToLogin.isEncrypted) {
            try {
                gamePassword = CryptoUtils.decrypt(profileToLogin.UserPassword, passwordOrKey);
                flashPassword = decryptProfileSecret(profileToLogin.UserPasswordFlash, passwordOrKey);
                // D1: ленивая миграция шифрования на актуальный формат ANC1 (AES-256-GCM).
                migrateProfileEncryptionIfLegacy(profileToLogin, passwordOrKey, gamePassword, flashPassword);
            } catch (Exception e) {
                Toast.makeText(this, "Неверный пароль шифрования", Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
                binding.loginButton.setEnabled(true);
                return;
            }
        } else {
            gamePassword = passwordOrKey;
            flashPassword = profileToLogin.UserPasswordFlash == null ? "" : profileToLogin.UserPasswordFlash;
        }

        UserConfig licenseDiagnostics = createLicenseDiagnosticsProfile(profileToLogin, gamePassword, flashPassword);
        // Лицензионный gate должен выполниться до любой сетевой авторизации. Для шифрованного
        // профиля request.txt получает уже расшифрованные diagnostic-поля, но исходный профиль не мутируется.
        LicenseStatus licenseStatus = LicenseValidationHandler.validateBeforeLogin(this, licenseDiagnostics);
        if (!licenseStatus.isAllowed()) {
            showLicenseDialog(licenseStatus);
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);

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
        AppLog.i(
                "LoginActivity",
                "LOGIN_UI: loginClick profileId=" + profileToLogin.id
                        + ", encrypted=" + profileToLogin.isEncrypted
                        + ", server=" + profileToLogin.GameServerCode
                        + ", rememberChecked=" + binding.rememberCheckBox.isChecked()
                        + ", inputPasswordLen=" + safeLen(passwordOrKey)
        );

        clearCookiesAndAuthorize(username, gamePassword, flashPassword, profileToLogin);
    }

    private UserConfig createLicenseDiagnosticsProfile(UserConfig source,
                                                       String plainUserPassword,
                                                       String plainFlashPassword) {
        UserConfig result = new UserConfig();
        if (source == null) {
            return result;
        }
        result.id = source.id;
        result.UserNick = source.UserNick;
        result.isEncrypted = source.isEncrypted;
        result.UserPassword = plainUserPassword == null ? "" : plainUserPassword;
        result.UserPasswordFlash = plainFlashPassword == null ? "" : plainFlashPassword;
        result.GameServerCode = source.GameServerCode;
        result.UseProxy = source.UseProxy;
        result.DoProxy = source.DoProxy;
        result.ProxyAddress = source.ProxyAddress;
        result.ProxyUserName = source.ProxyUserName;
        result.ProxyPassword = source.ProxyPassword;
        return result;
    }

    private String decryptProfileSecret(String encryptedText, String encryptionPassword) throws Exception {
        if (TextUtils.isEmpty(encryptedText)) {
            return "";
        }
        return CryptoUtils.decrypt(encryptedText, encryptionPassword);
    }

    /**
     * Ленивая миграция шифрования профиля на актуальный формат {@code ANC1} (D1).
     *
     * <p>Зачем: прежняя схема использовала 3DES со статичной солью и 1000 итераций PBKDF2.
     * Чтобы у пользователей не пропали уже сохранённые пароли, чтение старого формата оставлено,
     * но при первом успешном входе профиль незаметно перешифровывается в AES-256-GCM
     * тем же ключом шифрования, который пользователь только что ввёл.</p>
     *
     * <p>Вызывается только после успешной расшифровки, т.е. ключ гарантированно верный.
     * Ошибка перешифровки не блокирует вход: профиль просто остаётся в старом формате.</p>
     *
     * @param profileToLogin   профиль, с которым выполняется вход
     * @param encryptionPassword ключ шифрования, введённый пользователем
     * @param gamePassword     уже расшифрованный игровой пароль
     * @param flashPassword    уже расшифрованный flash-пароль
     */
    private void migrateProfileEncryptionIfLegacy(UserConfig profileToLogin,
                                                  String encryptionPassword,
                                                  String gamePassword,
                                                  String flashPassword) {
        if (profileToLogin == null || !profileToLogin.isEncrypted) {
            return;
        }
        boolean legacyMainPassword = CryptoUtils.isLegacyFormat(profileToLogin.UserPassword);
        boolean legacyFlashPassword = !TextUtils.isEmpty(profileToLogin.UserPasswordFlash)
                && CryptoUtils.isLegacyFormat(profileToLogin.UserPasswordFlash);
        if (!legacyMainPassword && !legacyFlashPassword) {
            return;
        }
        try {
            profileToLogin.UserPassword = CryptoUtils.encrypt(
                    gamePassword == null ? "" : gamePassword, encryptionPassword);
            if (TextUtils.isEmpty(profileToLogin.UserPasswordFlash)) {
                profileToLogin.UserPasswordFlash = "";
            } else {
                profileToLogin.UserPasswordFlash = CryptoUtils.encrypt(
                        flashPassword == null ? "" : flashPassword, encryptionPassword);
            }
            profileToLogin.save(getApplicationContext());
            AppLog.i("LoginActivity", "CRYPTO_MIGRATION: profile re-encrypted to ANC1 (AES-256-GCM), profileId="
                    + profileToLogin.id);
        } catch (Exception e) {
            // Не блокируем вход: профиль остаётся в старом формате и будет прочитан legacy-путём.
            AppLog.w("LoginActivity", "CRYPTO_MIGRATION: re-encrypt to ANC1 failed, keeping legacy format", e);
        }
    }

    private void showLicenseDialog(LicenseStatus status) {
        LicenseRequestDialog.show(this, status, this::requestProfileRegAttach);
    }

    private void requestProfileRegAttach(String licensePath) {
        pendingProfileRegImportPath = licensePath == null ? "" : licensePath;
        try {
            AppLog.i("ANCLIENT_LICENSE", "LoginActivity", "LICENSE_PROFILE_REG_PICKER_OPEN: destination="
                    + pendingProfileRegImportPath);
            profileRegImportLauncher.launch(LicenseRequestDialog.createProfileRegPickerIntent());
        } catch (Exception e) {
            pendingProfileRegImportPath = "";
            AppLog.w("ANCLIENT_LICENSE", "LoginActivity", "LICENSE_PROFILE_REG_PICKER_FAILED: "
                    + e.getMessage(), e);
            Toast.makeText(this, "Не удалось открыть выбор profile.reg", Toast.LENGTH_LONG).show();
        }
    }

    private void clearCookiesAndAuthorize(String username,
                                          String gamePassword,
                                          String flashPassword,
                                          UserConfig profileToLogin) {
        // Поднимаем proxy runtime до auth-flow, чтобы первый вход уже шёл через единый контур,
        // как в ПК версии (proxy стартует до основной логики приложения).
        AppLog.i(
                "LoginActivity",
                "PROXY_BOOT: login pre-auth start, doProxy=" + profileToLogin.DoProxy
                        + ", useProxy=" + profileToLogin.UseProxy
                        + ", address=" + (profileToLogin.ProxyAddress == null ? "" : profileToLogin.ProxyAddress)
        );
        boolean proxyReady = ProxyRuntimeManager.ensureStarted(getApplicationContext(), profileToLogin);
        if (!proxyReady) {
            String reason = ProxyRuntimeManager.getLastStartError();
            AppLog.e(
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
        AppLog.i(
                "LoginActivity",
                "PROXY_BOOT: proxy runtime ready, port=" + ProxyRuntimeManager.getActivePort()
        );

        // Пересобираем сетевой клиент, чтобы он использовал актуальный localhost proxy endpoint.
        NetworkClient.invalidateInstance();

        // Each new login must start from a clean cookie state (desktop behavior).
        AppVars.lastCookies = null;
        NetworkClient.clearCookies();
        clearCookiesAndAuthorizeInternal(username, gamePassword, flashPassword, profileToLogin, 0);
    }

    private void clearCookiesAndAuthorizeInternal(
            String username,
            String gamePassword,
            String flashPassword,
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
                AppLog.w(
                        "LoginActivity",
                        "CookieManager warm-up failed, retry " + (warmupAttempt + 1) + "/" + COOKIE_WARMUP_MAX_ATTEMPTS,
                        t
                );
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> clearCookiesAndAuthorizeInternal(username, gamePassword, flashPassword, profileToLogin, warmupAttempt + 1),
                        COOKIE_WARMUP_DELAY_MS
                );
                return;
            }
            AppLog.w("LoginActivity", "CookieManager warm-up failed, continue authorize flow", t);
        }

        CookiesManager.clear(value -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            startAuthorizeRequest(username, gamePassword, flashPassword, profileToLogin, 0);
        });
    }

    /**
     * Запускает синхронный auth-flow в фоновом потоке с контролем retry-индекса.
     *
     * Зависимости:
     * - {@link AuthManager#authorize(String, String, String)} как основной HTTP-пайплайн входа;
     * - главный поток (`Handler`) для безопасной работы с UI и переходами активностей;
     * - {@link #handleAuthResult(AuthResult, String, String, String, UserConfig, int, long)} для развилки
     *   успех/капча/авто-повтор/финальная ошибка.
     */
    private void startAuthorizeRequest(String username,
                                       String gamePassword,
                                       String flashPassword,
                                       UserConfig profileToLogin,
                                       int retryAttempt) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        final long attemptStartedAtMs = System.currentTimeMillis();
        AppLog.d(
                "LoginActivity",
                "Authorization attempt start: retryAttempt=" + retryAttempt + ", startedAtMs=" + attemptStartedAtMs
        );
        executor.execute(() -> {
            AuthManager authManager = new AuthManager();
            AuthResult result = authManager.authorize(username, gamePassword, flashPassword, profileToLogin.GameServerCode);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    AppLog.d("LoginActivity", "Authorization result ignored after activity destruction");
                    return;
                }
                handleAuthResult(
                        result,
                        username,
                        gamePassword,
                        flashPassword,
                        profileToLogin,
                        retryAttempt,
                        attemptStartedAtMs
                );
            });
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
     * - {@link #startAuthorizeRequest(String, String, String, UserConfig, int)} для повторной попытки;
     * - UI-состояние кнопки/прогресса (`binding.loginButton`, `binding.progressBar`).
     */
    private void handleAuthResult(AuthResult result,
                                  String username,
                                  String gamePassword,
                                  String flashPassword,
                                  UserConfig profileToLogin,
                                  int retryAttempt,
                                  long attemptStartedAtMs) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        long attemptElapsedMs = Math.max(0L, System.currentTimeMillis() - attemptStartedAtMs);
        AppLog.d(
                "LoginActivity",
                "Authorization attempt result: retryAttempt=" + retryAttempt
                        + ", elapsedMs=" + attemptElapsedMs
                        + ", success=" + (result != null && result.isSuccess())
                        + ", captcha=" + (result != null && result.isCaptchaRequired())
        );
        if (result.isSuccess()) {
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            onLoginSuccess(result.getCookies(), gamePassword, flashPassword, profileToLogin);
        } else if (result.isCaptchaRequired()) {
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            showCaptchaDialog(username, gamePassword, flashPassword, result.getCaptchaUrl(), result.getVcode(), profileToLogin);
        } else {
            String errorMessage = result != null && result.getErrorMessage() != null
                    ? result.getErrorMessage()
                    : "Ошибка авторизации";

            if (isRetriableAuthError(errorMessage, retryAttempt, attemptElapsedMs)) {
                AppLog.w(
                        "LoginActivity",
                        "Authorization transient error, auto-retry " + (retryAttempt + 1) + "/" + AUTH_MAX_RETRY_ATTEMPTS
                                + ", elapsedMs=" + attemptElapsedMs + ": " + errorMessage
                );
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.loginButton.setEnabled(false);
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> {
                            if (!isFinishing() && !isDestroyed()) {
                                startAuthorizeRequest(username, gamePassword, flashPassword, profileToLogin, retryAttempt + 1);
                            }
                        },
                        AUTH_RETRY_DELAY_MS
                );
                return;
            }

            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            AppLog.w("LoginActivity", "Authorization error: " + errorMessage);
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
            AppLog.d(
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

    private void onLoginSuccess(List<HttpCookie> cookies,
                                String gamePassword,
                                String flashPassword,
                                UserConfig profileToLogin) {
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
        AppVars.setRuntimeAuthCredentials(profileToLogin, gamePassword, flashPassword);
        
        // Синхронизируем состояние автобоя с профилем
        if (profileToLogin.LezDoAutoboi) {
            AppVars.Autoboi = ru.neverlands.anclient.model.AutoboiState.AutoboiOn;
        } else {
            AppVars.Autoboi = ru.neverlands.anclient.model.AutoboiState.AutoboiOff;
        }

        // Синхронизируем режим "Снежок/Ярость" (первый удар на осаде) с профилем.
        AppVars.DoFury = profileToLogin.hasAnyLezFuryGroup();
        profileToLogin.LezDoFury = AppVars.DoFury;
        AppVars.AutoFuryCheckScroll = AppVars.DoFury;
        AppVars.AutoFuryArmedScroll = false;
        AppVars.AutoFuryHand = "";
        AppVars.AutoFuryHandD = "";

        // Фоновое обновление контактов запускаем НЕ сразу: см. LOGIN_CONTACT_REFRESH_START_DELAY_MS.
        // Иначе пачка info.cgi накладывается на загрузку главного фрейма/чата/probe-запросов,
        // очередь прокси переполняется и сервер рвёт сессию.
        ru.neverlands.anclient.manager.ContactsManager.initialize(getApplicationContext());
        List<ru.neverlands.anclient.model.Contact> contactsToUpdate = ru.neverlands.anclient.manager.ContactsManager.getContactsFromCache();
        int contactsQueueSize = contactsToUpdate == null ? 0 : contactsToUpdate.size();
        AppLog.d("LoginActivity", "Background contact refresh scheduled: queueSize=" + contactsQueueSize
                + ", startDelayMs=" + LOGIN_CONTACT_REFRESH_START_DELAY_MS
                + ", stepDelayMs=" + LOGIN_CONTACT_REFRESH_STEP_DELAY_MS);
        if (contactsToUpdate != null && !contactsToUpdate.isEmpty()) {
            final List<ru.neverlands.anclient.model.Contact> delayedContacts = contactsToUpdate;
            // Handler на main-looper: цепочка должна пережить finish() этой Activity.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                AppLog.d("LoginActivity", "Starting background contact refresh after login settle window.");
                updateContactsRecursive(delayedContacts, 0);
            }, LOGIN_CONTACT_REFRESH_START_DELAY_MS);
        }

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Рекурсивно обновляет список контактов один за другим с задержкой.
     */
    private void updateContactsRecursive(final List<ru.neverlands.anclient.model.Contact> contacts, final int index) {
        if (index >= contacts.size()) {
            AppLog.d("LoginActivity", "Background contact refresh completed.");
            // Опционально: можно показать Toast, но это может сбить пользователя с толку, т.к. он уже на другом экране
            // runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Контакты обновлены в фоне", Toast.LENGTH_SHORT).show());
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            final ru.neverlands.anclient.model.Contact oldContact = contacts.get(index);
            if (oldContact.playerID == null || oldContact.playerID.isEmpty()) {
                AppLog.w("LoginActivity", "Skipping background contact refresh: missing playerID, nick=" + oldContact.nick);
                updateContactsRecursive(contacts, index + 1);
                return;
            }

            ru.neverlands.anclient.repository.ApiRepository.getPlayerInfo(oldContact.playerID, new ru.neverlands.anclient.repository.ApiRepository.ApiCallback<ru.neverlands.anclient.model.Contact>() {
                @Override
                public void onSuccess(ru.neverlands.anclient.model.Contact newContact) {
                    newContact.classId = oldContact.classId;
                    newContact.comment = oldContact.comment;
                    // Сохраняем персональный toolId при фоновом обновлении после логина.
                    newContact.toolId = oldContact.toolId;
                    AppLog.d("LoginActivity", "Background contact refreshed: nick=" + newContact.nick
                            + ", onlineStatus=" + newContact.onlineStatus
                            + ", isOnline=" + newContact.isOnline());
                    ru.neverlands.anclient.manager.ContactsManager.updateContact(newContact);
                    updateContactsRecursive(contacts, index + 1);
                }

                @Override
                public void onFailure(String message) {
                    AppLog.e("LoginActivity", "Failed to refresh contact by ID " + oldContact.playerID + ": " + message);
                    updateContactsRecursive(contacts, index + 1);
                }
            });
            // Единый anti-rate-limit интервал (было 500 мс -> сервер отвечал 535/536).
        }, LOGIN_CONTACT_REFRESH_STEP_DELAY_MS);
    }

    private static long currentDotNetTicks() {
        long unixEpochMs = System.currentTimeMillis();
        return (unixEpochMs + 62135596800000L) * 10_000L;
    }

    private void showCaptchaDialog(String username,
                                   String gamePassword,
                                   String flashPassword,
                                   String captchaUrl,
                                   String vcode,
                                   UserConfig profileToLogin) {
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
                    AuthResult result = authManager.authorizeWithCaptcha(
                            username,
                            gamePassword,
                            flashPassword,
                            profileToLogin.GameServerCode,
                            vcode,
                            verify
                    );
                    handler.post(() -> {
                        if (isFinishing() || isDestroyed()) {
                            AppLog.d("LoginActivity", "Captcha authorization result ignored after activity destruction");
                            return;
                        }
                        handleAuthResult(
                                result,
                                username,
                                gamePassword,
                                flashPassword,
                                profileToLogin,
                                0,
                                attemptStartedAtMs
                        );
                    });
                });
                executor.shutdown();
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }

    private void applyServerCodeToSelectedProfile(String serverCode, String stage, boolean saveIfExisting) {
        if (selectedProfile == null) {
            return;
        }
        String normalized = GameServerUrls.codeForDisplayValue(serverCode);
        boolean changed = !TextUtils.equals(selectedProfile.GameServerCode, normalized);
        selectedProfile.GameServerCode = normalized;
        binding.serverSpinner.setText(serverDisplayName(normalized), false);
        if (saveIfExisting && changed && !TextUtils.isEmpty(selectedProfile.UserNick)) {
            selectedProfile.save(this);
            persistLastProfileId(selectedProfile);
        }
        AppLog.i(
                "LoginActivity",
                "LOGIN_UI: serverSelected stage=" + stage
                        + ", profileId=" + selectedProfile.id
                        + ", server=" + normalized
                        + ", changed=" + changed
        );
    }

    private void startServerPingRefresh() {
        if (serverPingExecutor == null || serverPingExecutor.isShutdown()) {
            serverPingExecutor = Executors.newSingleThreadExecutor();
        }
        if (serverPingRunnable != null) {
            serverPingHandler.removeCallbacks(serverPingRunnable);
        }
        serverPingRunnable = new Runnable() {
            @Override
            public void run() {
                refreshServerPingsAsync();
                serverPingHandler.postDelayed(this, SERVER_PING_REFRESH_MS);
            }
        };
        serverPingHandler.post(serverPingRunnable);
    }

    private void refreshServerPingsAsync() {
        ExecutorService executor = serverPingExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        List<GameServerUrls.ServerEntry> entries = GameServerUrls.serverEntries();
        executor.execute(() -> {
            for (GameServerUrls.ServerEntry entry : entries) {
                serverPingMsByCode.put(
                        entry.code,
                        GameServerUrls.measureTcpPingMs(entry.code, SERVER_PING_TIMEOUT_MS)
                );
            }
            serverPingHandler.post(this::updateServerDisplayNames);
        });
    }

    private void updateServerDisplayNames() {
        if (binding == null || serverAdapter == null) {
            return;
        }
        String selectedServerCode = selectedProfile == null
                ? GameServerUrls.DEFAULT_SERVER_CODE
                : GameServerUrls.normalizeServerCode(selectedProfile.GameServerCode);
        serverAdapter.clear();
        serverAdapter.addAll(buildServerDisplayNames());
        serverAdapter.notifyDataSetChanged();
        binding.serverSpinner.setText(serverDisplayName(selectedServerCode), false);
    }

    private String[] buildServerDisplayNames() {
        List<GameServerUrls.ServerEntry> entries = GameServerUrls.serverEntries();
        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            GameServerUrls.ServerEntry entry = entries.get(i);
            names[i] = GameServerUrls.displayName(entry.code, pingValueOrNull(serverPingMsByCode.get(entry.code)));
        }
        return names;
    }

    private String serverDisplayName(String serverCode) {
        String normalized = GameServerUrls.normalizeServerCode(serverCode);
        return GameServerUrls.displayName(normalized, pingValueOrNull(serverPingMsByCode.get(normalized)));
    }

    private Long pingValueOrNull(Long value) {
        return value == null || value == Long.MIN_VALUE ? null : value;
    }

    @Override
    protected void onDestroy() {
        if (serverPingRunnable != null) {
            serverPingHandler.removeCallbacks(serverPingRunnable);
            serverPingRunnable = null;
        }
        if (serverPingExecutor != null) {
            serverPingExecutor.shutdownNow();
            serverPingExecutor = null;
        }
        super.onDestroy();
    }
}
