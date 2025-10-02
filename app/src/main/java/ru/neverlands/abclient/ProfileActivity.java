package ru.neverlands.abclient;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import ru.neverlands.abclient.databinding.ActivityProfileBinding;
import ru.neverlands.abclient.model.UserConfig;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import ru.neverlands.abclient.databinding.ActivityProfileBinding;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.CryptoUtils;

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
            File oldFile = new File(ru.neverlands.abclient.utils.DataManager.getProfilesDir(), originalUserNick + ".profile");
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
