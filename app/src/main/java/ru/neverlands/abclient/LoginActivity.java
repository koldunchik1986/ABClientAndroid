package ru.neverlands.abclient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import java.util.List;

import ru.neverlands.abclient.databinding.ActivityLoginBinding;
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

        AuthManager.authorize(this, username, gamePassword, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    // Пароль сохраняется только после успешного входа
                    if (!profileToLogin.isEncrypted && binding.rememberCheckBox.isChecked()) {
                        profileToLogin.UserPassword = gamePassword;
                    } else if (!profileToLogin.isEncrypted) {
                        profileToLogin.UserPassword = "";
                    }
                    // Для зашифрованных профилей пароль не пересохраняем на этом этапе
                    profileToLogin.save(LoginActivity.this);

                    // Устанавливаем глобальный профиль для сессии
                    AppVars.Profile = profileToLogin;

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
