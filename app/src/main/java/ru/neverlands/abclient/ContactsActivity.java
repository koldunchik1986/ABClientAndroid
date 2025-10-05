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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.neverlands.abclient.adapter.ContactsAdapter;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.model.Contact;
import java.io.File;
import ru.neverlands.abclient.repository.ApiRepository;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

public class ContactsActivity extends AppCompatActivity {

    private RecyclerView contactsRecyclerView;
    private ContactsAdapter contactsAdapter;
    private List<ContactsAdapter.DisplayableItem> displayList = new ArrayList<>();
    private List<Contact> allContacts = new ArrayList<>();
    private Map<String, Boolean> groupExpansionStates = new HashMap<>();

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
        downloadClanList();
    }

    private void setupRecyclerView() {
        contactsAdapter = new ContactsAdapter(displayList,
                this::handleInfoClick,
                this::handleWarStatusClick,
                this::showContactContextMenu,
                this::handleGroupClick,
                this::showGroupContextMenu
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(contactsAdapter);
    }

    private void loadContactsFromManager() {
        ContactsManager.loadContacts(this, new ContactsManager.LoadContactsCallback() {
            @Override
            public void onSuccess(List<Contact> contacts) {
                allContacts = contacts;
                buildDisplayList();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(ContactsActivity.this, "Ошибка загрузки контактов: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void buildDisplayList() {
        displayList.clear();
        if (allContacts == null) return;

        // Sort contacts by clan name, then by nick
        Collections.sort(allContacts, (c1, c2) -> {
            String clan1 = (c1.clanName == null || c1.clanName.equals("none")) ? "" : c1.clanName;
            String clan2 = (c2.clanName == null || c2.clanName.equals("none")) ? "" : c2.clanName;
            int clanCompare = clan1.compareTo(clan2);
            if (clanCompare != 0) {
                return clanCompare;
            }
            return c1.nick.compareTo(c2.nick);
        });

        String currentClan = "";
        List<Contact> noClanContacts = new ArrayList<>();

        for (Contact contact : allContacts) {
            String contactClan = (contact.clanName == null || contact.clanName.equals("none")) ? "" : contact.clanName;

            if (contactClan.isEmpty()) {
                noClanContacts.add(contact);
                continue;
            }

            if (!contactClan.equals(currentClan)) {
                currentClan = contactClan;
                if (!groupExpansionStates.containsKey(currentClan)) {
                    groupExpansionStates.put(currentClan, true); // Expand new groups by default
                }
                displayList.add(new ContactsAdapter.GroupHeaderItem(contact.clanName, contact.clanIco));
            }

            if (Boolean.TRUE.equals(groupExpansionStates.get(currentClan))) {
                displayList.add(new ContactsAdapter.ContactItem(contact));
            }
        }

        // Add no-clan contacts at the end
        for (Contact contact : noClanContacts) {
            displayList.add(new ContactsAdapter.ContactItem(contact));
        }

        contactsAdapter.updateItems(displayList);
    }

    private void handleGroupClick(ContactsAdapter.GroupHeaderItem groupHeaderItem) {
        boolean isExpanded = Boolean.TRUE.equals(groupExpansionStates.get(groupHeaderItem.clanName));
        groupExpansionStates.put(groupHeaderItem.clanName, !isExpanded);
        buildDisplayList();
    }

    private void handleInfoClick(Contact contact) {
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
    }

    private void handleWarStatusClick(Contact contact) {
        if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
            Intent intent = new Intent(this, LogsActivity.class);
            String url = "http://neverlands.ru/logs.fcg?fid=" + contact.warLogNumber;
            intent.putExtra("url", url);
            startActivity(intent);
        }
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
                contact.classId = 0; // By default, all new contacts are neutral
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
                                    updatedContact.classId = contact.classId; // Preserve old classId
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
    private void showGroupContextMenu(ContactsAdapter.GroupHeaderItem group) {
        final CharSequence[] items = {
                "Обновить всех в группе",
                "Удалить всех из группы",
                "Добавить весь клан",
                "--- Тип ---",
                "Сделать всех Друзьями",
                "Сделать всех Врагами",
                "Сделать всех Нейтралами"
        };

        new AlertDialog.Builder(this)
                .setTitle("Группа: " + group.clanName)
                .setItems(items, (dialog, which) -> {
                    // Note: The separator is a text item and will be handled as a no-op.
                    switch (which) {
                        case 0: // Обновить всех
                            updateGroup(group.clanName);
                            break;
                        case 1: // Удалить всех
                            deleteGroup(group.clanName);
                            break;
                        case 2: // Добавить весь клан
                            importClanMembers(group);
                            break;
                        case 3: // Separator
                            break;
                        case 4: // Сделать всех Друзьями
                            setClassIdForGroup(group.clanName, 2);
                            break;
                        case 5: // Сделать всех Врагами
                            setClassIdForGroup(group.clanName, 1);
                            break;
                        case 6: // Сделать всех Нейтралами
                            setClassIdForGroup(group.clanName, 0);
                            break;
                    }
                })
                .show();
    }

    private void importClanMembers(ContactsAdapter.GroupHeaderItem group) {
        Toast.makeText(this, "Импорт клана '" + group.clanName + "'...", Toast.LENGTH_SHORT).show();

        if (group.clanIco == null || group.clanIco.isEmpty()) {
            Toast.makeText(this, "Невозможно определить ID клана (отсутствует иконка)", Toast.LENGTH_LONG).show();
            return;
        }

        final String clanId = group.clanIco.replace(".gif", "");

        new Thread(() -> {
            File infoDir = new File(getExternalFilesDir(null), "info");
            File clanFile = new File(infoDir, "clans.txt");

            if (!clanFile.exists()) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Файл кланов не найден. Обновите список.", Toast.LENGTH_LONG).show());
                return;
            }

            boolean clanFound = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(clanFile), "windows-1251"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] clanParts = line.split("\\|");
                    if (clanParts.length > 5 && clanParts[0].equalsIgnoreCase(clanId)) {
                        clanFound = true;
                        String[] players = clanParts[5].split("#");
                        for (String player : players) {
                            String[] playerParts = player.split(",");
                            if (playerParts.length > 1) {
                                String nick = playerParts[1];
                                ContactsManager.addContact(ContactsActivity.this, nick, new ContactsManager.ContactOperationCallback() {
                                    @Override
                                    public void onSuccess(Contact contact) {
                                        Log.d("ClanImport", "Added/Updated: " + contact.nick);
                                    }

                                    @Override
                                    public void onFailure(String message) {
                                        Log.e("ClanImport", "Failed to add " + nick + ": " + message);
                                    }
                                });
                                try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
                            }
                        }
                        break; 
                    }
                }

                final boolean finalClanFound = clanFound;
                runOnUiThread(() -> {
                    if (finalClanFound) {
                        Toast.makeText(ContactsActivity.this, "Импорт клана завершен. Обновление списка...", Toast.LENGTH_LONG).show();
                        new Handler(Looper.getMainLooper()).postDelayed(this::loadContactsFromManager, 3000);
                    } else {
                        Toast.makeText(ContactsActivity.this, "Клан не найден в файле clans.txt", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Ошибка чтения файла кланов.", Toast.LENGTH_LONG).show());
                Log.e("ClanImport", "Error reading clans.txt", e);
            }
        }).start();
    }

    private void updateGroup(String clanName) {
        Toast.makeText(this, "Обновление группы " + clanName + "...", Toast.LENGTH_SHORT).show();
        for (Contact contact : allContacts) {
            if (clanName.equals(contact.clanName)) {
                ContactsManager.addContact(this, contact.nick, new ContactsManager.ContactOperationCallback() {
                    @Override
                    public void onSuccess(Contact updatedContact) {
                        updatedContact.classId = contact.classId; // Preserve old classId
                        ContactsManager.updateContact(updatedContact);
                    }
                    @Override
                    public void onFailure(String message) {
                        // Silently fail for single user update in group
                    }
                });
            }
        }
        // Reload after a delay to allow updates to process
        new Handler(Looper.getMainLooper()).postDelayed(this::loadContactsFromManager, 5000);
    }

    private void deleteGroup(String clanName) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление группы")
                .setMessage("Вы уверены, что хотите удалить всех контактов из группы '" + clanName + "'?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    for (Contact contact : allContacts) {
                        if (clanName.equals(contact.clanName)) {
                            ContactsManager.deleteContact(contact.nick);
                        }
                    }
                    loadContactsFromManager();
                    Toast.makeText(this, "Группа " + clanName + " удалена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void setClassIdForGroup(String clanName, int classId) {
        for (Contact contact : allContacts) {
            if (clanName.equals(contact.clanName)) {
                contact.classId = classId;
                ContactsManager.updateContact(contact);
            }
        }
        loadContactsFromManager();
    }

    private void downloadClanList() {
        String url = "http://service.neverlands.ru/info/clans.txt";
        File infoDir = new File(getExternalFilesDir(null), "info");
        File destinationFile = new File(infoDir, "clans.txt");

        ApiRepository.downloadFile(url, destinationFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String filePath) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Список кланов обновлен", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Ошибка обновления списка кланов: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }
}