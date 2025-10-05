package ru.neverlands.abclient;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.utils.AppVars;

public class PinfoActivity extends AppCompatActivity {

    private WebView webView;
    private String nick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pinfo);

        webView = findViewById(R.id.pinfoWebView);
        setupWebView();

        Intent intent = getIntent();
        String url = intent.getStringExtra("url");

        if (url != null) {
            extractNickFromUrl(url);
            webView.loadUrl(url);
        }

        if (getSupportActionBar() != null && nick != null) {
            getSupportActionBar().setTitle(nick);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupWebView() {
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
    }

    private void extractNickFromUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            this.nick = uri.getQuery(); // In pinfo.cgi?nick, the query is the nick
        } catch (Exception e) {
            this.nick = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pinfo_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (nick == null) {
            Toast.makeText(this, "Не удалось извлечь ник", Toast.LENGTH_SHORT).show();
            return super.onOptionsItemSelected(item);
        }

        if (id == R.id.action_pinfo_private) {
            // TODO: Implement private message logic
            Toast.makeText(this, "Приват для " + nick, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_pinfo_add_contact) {
            if (AppVars.Profile != null) {
                final CharSequence[] items = {"Враг", "Друг", "Нейтрал"};
                new AlertDialog.Builder(this)
                        .setTitle("Добавить контакт: " + nick)
                        .setItems(items, (dialog, which) -> {
                            int classId = 0;
                            switch (which) {
                                case 0: classId = 1; break; // Foe
                                case 1: classId = 2; break; // Friend
                                case 2: classId = 0; break; // Neutral
                            }
                            Contact contact = new Contact();
                            contact.nick = nick;
                            contact.classId = classId;
                            AppVars.Profile.contacts.put(nick.toLowerCase(), contact);
                            AppVars.Profile.save(this);
                            Toast.makeText(this, nick + " добавлен в контакты", Toast.LENGTH_SHORT).show();
                        })
                        .show();
            }
            return true;
        } else if (id == R.id.action_pinfo_add_clan) {
            // TODO: Implement add clan logic
            Toast.makeText(this, "Добавить клан игрока " + nick, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_pinfo_compas) {
            // TODO: Implement compas logic
            Toast.makeText(this, "Компас для " + nick, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == android.R.id.home) {
            finish(); // Handle Up button
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
