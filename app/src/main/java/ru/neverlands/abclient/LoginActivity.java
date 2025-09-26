package ru.neverlands.abclient;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import ru.neverlands.abclient.databinding.ActivityLoginBinding;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppVars.Profile = UserConfig.load(this);

        if (AppVars.Profile != null && !AppVars.Profile.UserNick.isEmpty()) {
            binding.usernameEditText.setText(AppVars.Profile.UserNick);
            binding.passwordEditText.setText(AppVars.Profile.UserPassword);
            binding.rememberCheckBox.setChecked(!AppVars.Profile.UserPassword.isEmpty());
        }

        binding.loginButton.setOnClickListener(v -> login());
    }

    private void login() {
        String username = binding.usernameEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Введите имя пользователя и пароль", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);

        if (AppVars.Profile == null) {
            AppVars.Profile = new UserConfig();
        }

        AppVars.Profile.UserNick = username;

        if (binding.rememberCheckBox.isChecked()) {
            AppVars.Profile.UserPassword = password;
        } else {
            AppVars.Profile.UserPassword = "";
        }

        AppVars.Profile.save(this);

        AuthManager.authorize(this, username, password, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
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
