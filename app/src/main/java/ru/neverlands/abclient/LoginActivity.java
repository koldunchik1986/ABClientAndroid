package ru.neverlands.abclient;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.neverlands.abclient.databinding.ActivityLoginBinding;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.proxy.CookiesManager;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Log;

/**
 * Активность для входа в игру.
 */
public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private OkHttpClient client;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Инициализация HTTP-клиента
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Загрузка сохраненных данных
        if (AppVars.Profile != null) {
            binding.usernameEditText.setText(AppVars.Profile.UserNick);
            binding.passwordEditText.setText(AppVars.Profile.UserPassword);
            binding.rememberCheckBox.setChecked(true);
        }
        
        // Обработчик кнопки входа
        binding.loginButton.setOnClickListener(v -> login());
    }
    
    /**
     * Вход в игру
     */
    private void login() {
        String username = binding.usernameEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Введите имя пользователя и пароль", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Показываем индикатор загрузки
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);
        
        // Создаем или обновляем профиль пользователя
        if (AppVars.Profile == null) {
            AppVars.Profile = new UserConfig();
        }
        
        AppVars.Profile.UserNick = username;
        
        if (binding.rememberCheckBox.isChecked()) {
            AppVars.Profile.UserPassword = password;
        } else {
            AppVars.Profile.UserPassword = "";
        }
        
        // Сохраняем профиль
        AppVars.Profile.save(this);
        
        // Выполняем запрос на вход
        RequestBody formBody = new FormBody.Builder()
                .add("action", "login")
                .add("login", username)
                .add("password", password)
                .build();
        
        Request request = new Request.Builder()
                .url("http://www.neverlands.ru/")
                .post(formBody)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Ошибка соединения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final boolean success = response.isSuccessful();
                final String responseBody = response.body().string();
                
                // Проверяем успешность входа
                boolean loginSuccess = success && !responseBody.contains("Неверный логин или пароль");
                
                if (loginSuccess) {
                    // Сохраняем куки
                    String cookies = response.header("Set-Cookie");
                    if (cookies != null) {
                        CookiesManager.assign("www.neverlands.ru", cookies);
                    }
                    
                    // Запускаем основную активность
                    runOnUiThread(() -> {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    // Показываем ошибку
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, R.string.login_error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}