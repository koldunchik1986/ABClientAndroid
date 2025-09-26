package ru.neverlands.abclient;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.util.List;

import ru.neverlands.abclient.databinding.ActivityLoginBinding;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;

public class LoginActivity extends AppCompatActivity {
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

        loadProfiles();

        binding.loginButton.setOnClickListener(v -> login());
        binding.addProfileButton.setOnClickListener(v -> openProfileActivity(null));
        binding.editProfileButton.setOnClickListener(v -> {
            if (selectedProfile != null) {
                openProfileActivity(selectedProfile);
            }
        });
    }

    private void loadProfiles() {
        profiles = UserConfig.loadProfiles(this);

        if (profiles.isEmpty()) {
            profiles.add(new UserConfig());
        }

        selectedProfile = UserConfig.load(this);

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
                binding.passwordEditText.setText(selectedProfile.UserPassword);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void openProfileActivity(UserConfig profile) {
        Intent intent = new Intent(this, ProfileActivity.class);
        if (profile != null) {
            intent.putExtra("profile", new Gson().toJson(profile));
        }
        profileActivityLauncher.launch(intent);
    }

    private void login() {
        String username = selectedProfile.UserNick;
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
