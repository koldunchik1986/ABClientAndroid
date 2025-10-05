
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
    private List<Contact> contactList = new ArrayList<>();

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

        loadContactsFromManager();
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
                    if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
                        Intent intent = new Intent(this, LogsActivity.class);
                        String url = "http://neverlands.ru/logs.fcg?fid=" + contact.warLogNumber;
                        intent.putExtra("url", url);
                        startActivity(intent);
                    }
                },
                this::showContactContextMenu
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(contactsAdapter);
    }

    private void loadContactsFromManager() {
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
                // By default, all new contacts are neutral
                contact.classId = 0;
                ContactsManager.updateContact(contact);
                addContactButton.setEnabled(true);
                Toast.makeText(ContactsActivity.this, "Контакт " + contact.nick + " добавлен", Toast.LENGTH_LONG).show();
                nickEditText.getText().clear();
                loadContactsFromManager();
            }

            @Override
            public void onFailure(String message) {
                addContactButton.setEnabled(true);
                Toast.makeText(ContactsActivity.this, "Ошибка: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showContactContextMenu(Contact contact) {
        final CharSequence[] items = {
                "Обновить контакт",
                "Удалить контакт",
                "Сделать Другом",
                "Сделать Врагом",
                "Сделать Нейтралом"
        };

        new AlertDialog.Builder(this)
                .setTitle(contact.nick)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: // Обновить контакт
                            Toast.makeText(this, "Обновление " + contact.nick + "...", Toast.LENGTH_SHORT).show();
                            ContactsManager.addContact(this, contact.nick, new ContactsManager.ContactOperationCallback() {
                                @Override
                                public void onSuccess(Contact updatedContact) {
                                    updatedContact.classId = contact.classId;
                                    ContactsManager.updateContact(updatedContact);
                                    Toast.makeText(ContactsActivity.this, "Контакт " + updatedContact.nick + " обновлен", Toast.LENGTH_LONG).show();
                                    loadContactsFromManager();
                                }
                                @Override
                                public void onFailure(String message) {
                                    Toast.makeText(ContactsActivity.this, "Ошибка: " + message, Toast.LENGTH_LONG).show();
                                }
                            });
                            break;
                        case 1: // Удалить контакт
                            showDeleteConfirmationDialog(contact);
                            break;
                        case 2: // Сделать Другом
                            contact.classId = 2;
                            ContactsManager.updateContact(contact);
                            loadContactsFromManager();
                            break;
                        case 3: // Сделать Врагом
                            contact.classId = 1;
                            ContactsManager.updateContact(contact);
                            loadContactsFromManager();
                            break;
                        case 4: // Сделать Нейтралом
                            contact.classId = 0;
                            ContactsManager.updateContact(contact);
                            loadContactsFromManager();
                            break;
                    }
                })
                .show();
    }

    private void showDeleteConfirmationDialog(Contact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление контакта")
                .setMessage("Вы уверены, что хотите удалить '" + contact.nick + "' из списка контактов?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    ContactsManager.deleteContact(contact.nick);
                    loadContactsFromManager();
                    Toast.makeText(this, "Контакт " + contact.nick + " удален", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
