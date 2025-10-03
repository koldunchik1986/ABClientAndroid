package ru.neverlands.abclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import ru.neverlands.abclient.adapter.ContactsAdapter;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.model.Contact;

public class ContactsActivity extends AppCompatActivity {

    private RecyclerView contactsRecyclerView;
    private ContactsAdapter contactsAdapter;
    private final List<Contact> contactList = new ArrayList<>();

    private EditText nickEditText;
    private Button addContactButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        nickEditText = findViewById(R.id.nickEditText);
        addContactButton = findViewById(R.id.addContactButton);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);

        setupRecyclerView();

        addContactButton.setOnClickListener(v -> addContactFromInput());

        loadContactsFromDb();
    }

    private void setupRecyclerView() {
        contactsAdapter = new ContactsAdapter(contactList,
                contact -> {
                    try {
                        Intent intent = new Intent(this, PinfoActivity.class);
                        String encodedNick = URLEncoder.encode(contact.nick, "windows-1251");
                        String url = "http://neverlands.ru/pinfo.cgi?" + encodedNick;
                        intent.putExtra("url", url);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Ошибка кодирования URL", Toast.LENGTH_SHORT).show();
                    }
                },
                contact -> {
                    Intent intent = new Intent(this, LogsActivity.class);
                    String url = "http://neverlands.ru/logs.fcg?fid=" + contact.warLogNumber;
                    intent.putExtra("url", url);
                    startActivity(intent);
                },
                this::showDeleteConfirmationDialog
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(contactsAdapter);
    }

    private void loadContactsFromDb() {
        ContactsManager.loadContacts(this, new ContactsManager.LoadContactsCallback() {
            @Override
            public void onSuccess(List<Contact> contacts) {
                contactList.clear();
                contactList.addAll(contacts);
                contactsAdapter.updateContacts(contactList);
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(ContactsActivity.this, "Ошибка загрузки контактов: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addContactFromInput() {
        String nick = nickEditText.getText().toString().trim();
        if (nick.isEmpty()) {
            Toast.makeText(this, "Ник не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        addContactButton.setEnabled(false);
        Toast.makeText(this, "Добавление " + nick + "...", Toast.LENGTH_SHORT).show();

        ContactsManager.addContact(this, nick, new ContactsManager.ContactOperationCallback() {
            @Override
            public void onSuccess(Contact contact) {
                addContactButton.setEnabled(true);
                Toast.makeText(ContactsActivity.this, "Контакт " + contact.nick + " добавлен", Toast.LENGTH_LONG).show();
                nickEditText.getText().clear();
                loadContactsFromDb();
            }

            @Override
            public void onFailure(String message) {
                addContactButton.setEnabled(true);
                Toast.makeText(ContactsActivity.this, "Ошибка: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDeleteConfirmationDialog(Contact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление контакта")
                .setMessage("Вы уверены, что хотите удалить '" + contact.nick + "' из списка контактов?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    ContactsManager.deleteContact(this, contact);
                    loadContactsFromDb();
                    Toast.makeText(this, "Контакт " + contact.nick + " удален", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
