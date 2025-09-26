package ru.neverlands.abclient;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import ru.neverlands.abclient.databinding.ActivityProfileBinding;
import ru.neverlands.abclient.model.UserConfig;

public class ProfileActivity extends AppCompatActivity {
    private ActivityProfileBinding binding;
    private UserConfig profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String profileJson = getIntent().getStringExtra("profile");
        if (!TextUtils.isEmpty(profileJson)) {
            profile = new Gson().fromJson(profileJson, UserConfig.class);
        } else {
            profile = new UserConfig();
        }

        binding.usernameEditText.setText(profile.UserNick);
        binding.passwordEditText.setText(profile.UserPassword);
        binding.flashPasswordEditText.setText(profile.UserPasswordFlash);
        binding.autoLogonCheckBox.setChecked(profile.UserAutoLogon);
        binding.useProxyCheckBox.setChecked(profile.UseProxy);
        binding.proxyAddressEditText.setText(profile.ProxyAddress);
        binding.proxyUsernameEditText.setText(profile.ProxyUserName);
        binding.proxyPasswordEditText.setText(profile.ProxyPassword);

        binding.saveButton.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String username = binding.usernameEditText.getText().toString().trim();
        if (username.isEmpty()) {
            Toast.makeText(this, "Имя пользователя не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        profile.UserNick = username;
        profile.UserPassword = binding.passwordEditText.getText().toString().trim();
        profile.UserPasswordFlash = binding.flashPasswordEditText.getText().toString().trim();
        profile.UserAutoLogon = binding.autoLogonCheckBox.isChecked();
        profile.UseProxy = binding.useProxyCheckBox.isChecked();
        profile.ProxyAddress = binding.proxyAddressEditText.getText().toString().trim();
        profile.ProxyUserName = binding.proxyUsernameEditText.getText().toString().trim();
        profile.ProxyPassword = binding.proxyPasswordEditText.getText().toString().trim();

        profile.save(this);

        setResult(RESULT_OK);
        finish();
    }
}
