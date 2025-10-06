package ru.neverlands.abclient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
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

import ru.neverlands.abclient.databinding.ActivityLoginBinding;
import ru.neverlands.abclient.model.AuthResult;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.CryptoUtils;

public class LoginActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
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
                    selectedProfile.delete(this);
                    Toast.makeText(this, "Профиль '" + selectedProfile.UserNick + "' удален", Toast.LENGTH_SHORT).show();
                    loadProfiles(); // Перезагружаем список профилей
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private boolean checkAndRequestPermissions() {
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
            UserConfig lastUsed = profiles.get(0);
            for (UserConfig profile : profiles) {
                if (profile.LastLogin > lastUsed.LastLogin) {
                    lastUsed = profile;
                }
            }
            selectedProfile = lastUsed;
        }

        ArrayAdapter<UserConfig> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.profileSpinner.setAdapter(adapter);

        int selection = profiles.indexOf(selectedProfile);
        if (selection != -1) {
            binding.profileSpinner.setSelection(selection);
        }

        binding.profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedProfile = profiles.get(position);
                if (selectedProfile.isEncrypted) {
                    binding.passwordInputLayout.setHint("Пароль шифрования");
                    binding.passwordEditText.setText(""); // Очищаем поле при смене на шифрованный профиль
                } else {
                    binding.passwordInputLayout.setHint("Пароль");
                    binding.passwordEditText.setText(selectedProfile.UserPassword);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
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

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            AuthManager authManager = new AuthManager();
            AuthResult result = authManager.authorize(username, gamePassword);

            handler.post(() -> handleAuthResult(result, username, gamePassword, profileToLogin));
        });
    }

    private void handleAuthResult(AuthResult result, String username, String gamePassword, UserConfig profileToLogin) {
        binding.progressBar.setVisibility(View.GONE);
        binding.loginButton.setEnabled(true);

        if (result.isSuccess()) {
            onLoginSuccess(result.getCookies(), gamePassword, profileToLogin);
        } else if (result.isCaptchaRequired()) {
            showCaptchaDialog(username, gamePassword, result.getCaptchaUrl(), result.getVcode(), profileToLogin);
        } else {
            Toast.makeText(LoginActivity.this, result.getErrorMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onLoginSuccess(List<HttpCookie> cookies, String gamePassword, UserConfig profileToLogin) {
        // Сохраняем куки для последующей передачи в WebView
        AppVars.lastCookies = cookies;

        // Пароль сохраняется только после успешного входа
        if (!profileToLogin.isEncrypted && binding.rememberCheckBox.isChecked()) {
            profileToLogin.UserPassword = gamePassword;
        } else if (!profileToLogin.isEncrypted) {
            profileToLogin.UserPassword = "";
        }
        profileToLogin.save(LoginActivity.this);

        // Устанавливаем глобальный профиль для сессии
        AppVars.Profile = profileToLogin;

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

                executor.execute(() -> {
                    AuthManager authManager = new AuthManager();
                    AuthResult result = authManager.authorizeWithCaptcha(username, gamePassword, vcode, verify);
                    handler.post(() -> handleAuthResult(result, username, gamePassword, profileToLogin));
                });
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }
}