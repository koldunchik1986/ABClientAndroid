package ru.neverlands.abclient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.neverlands.abclient.adapter.ContactsAdapter;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.repository.ApiRepository;

/**
 * Activity для отображения и управления списком контактов.
 * Отвечает за загрузку данных, построение сгруппированного списка, обработку всех действий пользователя.
 */
public class ContactsActivity extends AppCompatActivity implements ContactsAdapter.OnGroupClassIdChangeListener {

    // --- UI Элементы ---
    private RecyclerView contactsRecyclerView;
    private EditText nickEditText;
    private Button addContactButton;

    // --- Адаптер и структуры данных для списка ---
    private ContactsAdapter contactsAdapter;
    private List<ContactsAdapter.DisplayableItem> displayList = new ArrayList<>();
    private List<Contact> allContacts = new ArrayList<>();
    private Map<String, Boolean> groupExpansionStates = new HashMap<>();
    private Map<String, ClanInfo> clanInfoCache = new HashMap<>();

    /**
     * Определяет глобальный тип сортировки для списка групп.
     */
    private enum SortType {
        DEFAULT, // По алфавиту
        ONLINE_STATUS, // По наличию онлайн-игроков
        CLASS_ID_FOE_FIRST, // Сначала враги
        CLASS_ID_FRIEND_FIRST // Сначала друзья
    }

    private SortType currentSort = SortType.DEFAULT;

    /**
     * Внутренний класс для хранения информации о клане из clans.txt.
     */
    private static class ClanInfo {
        String clanId, clanName, clanLevel;
    }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contacts_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sort) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Инициализирует RecyclerView и адаптер, передавая ему все необходимые колбэки для обработки нажатий.
     */
    private void setupRecyclerView() {
        contactsAdapter = new ContactsAdapter(displayList,
                this::handleInfoClick,
                this::handleWarStatusClick,
                this::showContactContextMenu,
                this::handleGroupClick,
                this::showGroupContextMenu,
                this // OnGroupClassIdChangeListener
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(contactsAdapter);
    }

    /**
     * Загружает `clans.txt` с сервера и запускает его парсинг.
     * Зависимости: ApiRepository.downloadFile, parseAndCacheClanInfo.
     */
    private void downloadClanList() {
        String url = "http://service.neverlands.ru/info/clans.txt";
        File infoDir = new File(getExternalFilesDir(null), "info");
        File destinationFile = new File(infoDir, "clans.txt");

        ApiRepository.downloadFile(url, destinationFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String filePath) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Список кланов обновлен", Toast.LENGTH_SHORT).show());
                parseAndCacheClanInfo();
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Ошибка обновления списка кланов: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void parseAndCacheClanInfo() {
        new Thread(() -> {
            File infoDir = new File(getExternalFilesDir(null), "info");
            File clanFile = new File(infoDir, "clans.txt");
            if (!clanFile.exists()) return;

            Map<String, ClanInfo> tempCache = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(clanFile), "windows-1251"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] clanParts = line.split("\\|");
                    if (clanParts.length > 3) {
                        ClanInfo info = new ClanInfo();
                        info.clanId = clanParts[0];
                        info.clanName = clanParts[1];
                        info.clanLevel = clanParts[3];
                        tempCache.put(info.clanId, info);
                    }
                }
                clanInfoCache.clear();
                clanInfoCache.putAll(tempCache);
                runOnUiThread(this::buildDisplayList);
            } catch (IOException e) {
                Log.e("ClanInfoParser", "Error parsing clans.txt", e);
            }
        }).start();
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

        Map<String, List<Contact>> groupedContacts = new HashMap<>();
        List<Contact> noClanContacts = new ArrayList<>();

        for (Contact contact : allContacts) {
            String clanName = (contact.clanName == null || contact.clanName.isEmpty() || contact.clanName.equals("none")) ? "" : contact.clanName;
            if (clanName.isEmpty()) {
                noClanContacts.add(contact);
            }
            else {
                List<Contact> group = groupedContacts.get(clanName);
                if (group == null) {
                    group = new ArrayList<>();
                    groupedContacts.put(clanName, group);
                }
                group.add(contact);
            }
        }

        Map<String, Integer> groupClassIds = new HashMap<>();
        for (Map.Entry<String, List<Contact>> entry : groupedContacts.entrySet()) {
            sortContactList(entry.getValue(), currentSort);
            List<Contact> groupMembers = entry.getValue();
            if (groupMembers != null && !groupMembers.isEmpty()) {
                groupClassIds.put(entry.getKey(), groupMembers.get(0).classId);
            }
        }
        sortContactList(noClanContacts, currentSort);

        List<String> sortedClanNames = new ArrayList<>(groupedContacts.keySet());
        if (currentSort == SortType.CLASS_ID_FOE_FIRST || currentSort == SortType.CLASS_ID_FRIEND_FIRST) {
            boolean foeFirst = currentSort == SortType.CLASS_ID_FOE_FIRST;
            Collections.sort(sortedClanNames, (clan1, clan2) -> {
                int classId1 = groupClassIds.getOrDefault(clan1, 0);
                int classId2 = groupClassIds.getOrDefault(clan2, 0);
                int weight1 = getSortWeight(classId1, foeFirst);
                int weight2 = getSortWeight(classId2, foeFirst);
                if (weight1 != weight2) {
                    return Integer.compare(weight1, weight2);
                }
                return clan1.compareTo(clan2);
            });
        } else {
            Collections.sort(sortedClanNames);
        }

        for (String clanName : sortedClanNames) {
            List<Contact> clanMembers = groupedContacts.get(clanName);
            if (clanMembers == null || clanMembers.isEmpty()) continue;

            if (!groupExpansionStates.containsKey(clanName)) {
                groupExpansionStates.put(clanName, true);
            }

            Contact representative = clanMembers.get(0);
            String clanId = representative.clanIco.replace(".gif", "");
            ClanInfo clanInfo = clanInfoCache.get(clanId);
            String clanLevel = (clanInfo != null) ? clanInfo.clanLevel : "N/A";
            
            int onlineMemberCount = 0;
            for (Contact c : clanMembers) {
                if (c.onlineStatus == 1) {
                    onlineMemberCount++;
                }
            }

            int groupClassId = groupClassIds.getOrDefault(clanName, 0);

            displayList.add(new ContactsAdapter.GroupHeaderItem(clanName, representative.clanIco, clanLevel, clanMembers.size(), onlineMemberCount, groupClassId));

            if (Boolean.TRUE.equals(groupExpansionStates.get(clanName))) {
                for (Contact member : clanMembers) {
                    displayList.add(new ContactsAdapter.ContactItem(member));
                }
            }
        }

        if (!noClanContacts.isEmpty()) {
            for (Contact contact : noClanContacts) {
                displayList.add(new ContactsAdapter.ContactItem(contact));
            }
        }

        contactsAdapter.updateItems(displayList);
    }

    private void sortContactList(List<Contact> listToSort, SortType sortType) {
        switch (sortType) {
            case ONLINE_STATUS:
                Collections.sort(listToSort, (c1, c2) -> {
                    int onlineCompare = Integer.compare(c2.onlineStatus, c1.onlineStatus);
                    if (onlineCompare != 0) {
                        return onlineCompare;
                    }
                    return Integer.compare(c2.playerLevel, c1.playerLevel);
                });
                break;
            default:
                Collections.sort(listToSort, (c1, c2) -> Integer.compare(c2.playerLevel, c1.playerLevel));
                break;
        }
    }

    private int getSortWeight(int classId, boolean foeFirst) {
        if (foeFirst) {
            if (classId == 1) return 0;
            if (classId == 2) return 1;
            return 2;
        } else {
            if (classId == 2) return 0;
            if (classId == 1) return 1;
            return 2;
        }
    }

    private void handleGroupClick(ContactsAdapter.GroupHeaderItem groupHeaderItem) {
        boolean isExpanded = Boolean.TRUE.equals(groupExpansionStates.get(groupHeaderItem.clanName));
        groupExpansionStates.put(groupHeaderItem.clanName, !isExpanded);
        buildDisplayList();
    }

    private void showSortDialog() {
        final CharSequence[] items = {"По умолчанию", "Сначала онлайн", "Сначала враги", "Сначала друзья"};
        new AlertDialog.Builder(this).setTitle("Сортировка").setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: currentSort = SortType.DEFAULT; break;
                case 1: currentSort = SortType.ONLINE_STATUS; break;
                case 2: currentSort = SortType.CLASS_ID_FOE_FIRST; break;
                case 3: currentSort = SortType.CLASS_ID_FRIEND_FIRST; break;
            }
            buildDisplayList();
        }).show();
    }

    @Override
    public void onClassIdChanged(ContactsAdapter.GroupHeaderItem group, int newClassId) {
        Toast.makeText(this, "Смена статуса группы '" + group.clanName + "'...", Toast.LENGTH_SHORT).show();
        for (Contact contact : allContacts) {
            if (group.clanName.equals(contact.clanName)) {
                if (contact.classId != newClassId) {
                    contact.classId = newClassId;
                    ContactsManager.updateContact(contact);
                }
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::buildDisplayList, 200);
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
                contact.classId = getClassIdForClan(contact.clanName);
                ContactsManager.updateContact(contact);

                runOnUiThread(() -> {
                    addContactButton.setEnabled(true);
                    Toast.makeText(ContactsActivity.this, "Контакт " + contact.nick + " добавлен", Toast.LENGTH_LONG).show();
                    nickEditText.getText().clear();
                    loadContactsFromManager();
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    addContactButton.setEnabled(true);
                    Toast.makeText(ContactsActivity.this, "Ошибка: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showContactContextMenu(Contact contact) {
        final CharSequence[] items = {"Обновить контакт", "Удалить контакт", "Сделать Другом", "Сделать Врагом", "Сделать Нейтралом"};
        new AlertDialog.Builder(this).setTitle(contact.nick).setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: // Обновить
                    Toast.makeText(this, "Обновление " + contact.nick + "...", Toast.LENGTH_SHORT).show();
                    ContactsManager.addContact(this, contact.nick, new ContactsManager.ContactOperationCallback() {
                        @Override
                        public void onSuccess(Contact updatedContact) {
                            updatedContact.classId = getClassIdForClan(updatedContact.clanName);
                            ContactsManager.updateContact(updatedContact);
                            runOnUiThread(() -> {
                                Toast.makeText(ContactsActivity.this, "Контакт " + updatedContact.nick + " обновлен", Toast.LENGTH_LONG).show();
                                loadContactsFromManager();
                            });
                        }
                        @Override
                        public void onFailure(String message) {
                            runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Ошибка: " + message, Toast.LENGTH_LONG).show());
                        }
                    });
                    break;
                case 1: showDeleteConfirmationDialog(contact); break;
                case 2: contact.classId = 2; ContactsManager.updateContact(contact); buildDisplayList(); break;
                case 3: contact.classId = 1; ContactsManager.updateContact(contact); buildDisplayList(); break;
                case 4: contact.classId = 0; ContactsManager.updateContact(contact); buildDisplayList(); break;
            }
        }).show();
    }

    private int getClassIdForClan(String clanName) {
        if (clanName == null || clanName.isEmpty() || clanName.equals("none")) {
            return 0;
        }
        for (Contact contact : allContacts) {
            if (clanName.equals(contact.clanName)) {
                return contact.classId;
            }
        }
        return 0;
    }

    private void showDeleteConfirmationDialog(Contact contact) {
        new AlertDialog.Builder(this).setTitle("Удаление контакта").setMessage("Вы уверены, что хотите удалить '" + contact.nick + "' из списка контактов?").setPositiveButton("Удалить", (dialog, which) -> {
            ContactsManager.deleteContact(contact.nick);
            loadContactsFromManager();
            Toast.makeText(this, "Контакт " + contact.nick + " удален", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Отмена", null).show();
    }

    private void showGroupContextMenu(ContactsAdapter.GroupHeaderItem group) {
        final CharSequence[] items = {"Обновить всех в группе", "Удалить всех из группы", "Импортировать весь клан"};
        new AlertDialog.Builder(this).setTitle("Группа: " + group.clanName).setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: updateGroup(group.clanName); break;
                case 1: deleteGroup(group.clanName); break;
                case 2: importClanMembers(group); break;
            }
        }).show();
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
                                        contact.classId = group.groupClassId;
                                        ContactsManager.updateContact(contact);
                                        Log.d("ClanImport", "Added/Updated: " + contact.nick + " with classId: " + contact.classId);
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

    /**
     * Удаляет всех контактов из указанной группы.
     * Логика изменена для безопасного удаления: сначала собираем ID, затем удаляем.
     */
    private void deleteGroup(String clanName) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление группы")
                .setMessage("Вы уверены, что хотите удалить всех контактов из группы '" + clanName + "'?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    List<String> nicksToDelete = new ArrayList<>();
                    for (Contact contact : allContacts) {
                        if (clanName.equals(contact.clanName)) {
                            nicksToDelete.add(contact.nick);
                        }
                    }
                    for (String nick : nicksToDelete) {
                        ContactsManager.deleteContact(nick);
                    }
                    loadContactsFromManager();
                    Toast.makeText(this, "Группа " + clanName + " удалена", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * Обновляет информацию для всех контактов в указанной группе.
     * Запускает последовательное рекурсивное обновление, чтобы избежать перегрузки сети.
     */
    private void updateGroup(String clanName) {
        Toast.makeText(this, "Обновление группы " + clanName + "...", Toast.LENGTH_SHORT).show();
        List<Contact> contactsToUpdate = new ArrayList<>();
        for (Contact contact : allContacts) {
            if (clanName.equals(contact.clanName)) {
                contactsToUpdate.add(contact);
            }
        }

        if (!contactsToUpdate.isEmpty()) {
            updateContactsRecursive(contactsToUpdate, 0);
        }
    }

    /**
     * Рекурсивно обновляет список контактов один за другим, чтобы избежать "взрыва" запросов.
     * @param contactsToUpdate список контактов для обновления.
     * @param index индекс текущего контакта для обновления.
     */
    private void updateContactsRecursive(final List<Contact> contactsToUpdate, final int index) {
        if (index >= contactsToUpdate.size()) {
            // Базовый случай: все контакты обновлены
            runOnUiThread(() -> {
                Toast.makeText(this, "Обновление группы завершено", Toast.LENGTH_SHORT).show();
                loadContactsFromManager();
            });
            return;
        }

        Contact contactToUpdate = contactsToUpdate.get(index);
        ContactsManager.addContact(this, contactToUpdate.nick, new ContactsManager.ContactOperationCallback() {
            @Override
            public void onSuccess(Contact updatedContact) {
                updatedContact.classId = contactToUpdate.classId; // Сохраняем старый classId
                ContactsManager.updateContact(updatedContact);
                // Рекурсивный вызов для следующего контакта
                updateContactsRecursive(contactsToUpdate, index + 1);
            }

            @Override
            public void onFailure(String message) {
                Log.e("UpdateGroup", "Не удалось обновить контакт " + contactToUpdate.nick + ": " + message);
                // Продолжаем со следующим контактом даже в случае ошибки
                updateContactsRecursive(contactsToUpdate, index + 1);
            }
        });
    }

    private void setClassIdForGroup(String clanName, int classId) {
        // ...
    }
}
