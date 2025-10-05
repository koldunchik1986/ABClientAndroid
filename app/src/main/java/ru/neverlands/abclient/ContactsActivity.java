package ru.neverlands.abclient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import java.io.FileReader;
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
public class ContactsActivity extends AppCompatActivity {

    // --- UI Элементы ---
    private RecyclerView contactsRecyclerView;
    private EditText nickEditText;
    private Button addContactButton;

    // --- Адаптер и структуры данных для списка ---
    private ContactsAdapter contactsAdapter;
    /**
     * Основной список для отображения в RecyclerView. Содержит гетерогенные элементы: заголовки и контакты.
     * Зависимость: ContactsAdapter.DisplayableItem
     */
    private List<ContactsAdapter.DisplayableItem> displayList = new ArrayList<>();
    /**
     * "Источник правды" - полный плоский список всех контактов, загруженный из ContactsManager.
     */
    private List<Contact> allContacts = new ArrayList<>();
    /**
     * Хранит состояние (свернуто/развернуто) для каждой группы клана.
     * Ключ - clanName, значение - true (развернуто) или false (свернуто).
     */
    private Map<String, Boolean> groupExpansionStates = new HashMap<>();
    /**
     * Кэш с информацией о кланах (уровень), загруженной из clans.txt.
     * Ключ - clanId (например, "dshi"), значение - объект ClanInfo.
     */
    private Map<String, ClanInfo> clanInfoCache = new HashMap<>();

    /**
     * Внутренний класс для хранения информации о клане из clans.txt.
     */
    private static class ClanInfo {
        String clanId;
        String clanName;
        String clanLevel;
    }

    // --- Методы жизненного цикла Activity ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        // Инициализация UI
        nickEditText = findViewById(R.id.nickEditText);
        addContactButton = findViewById(R.id.addContactButton);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);

        // Настройка RecyclerView и обработчиков
        setupRecyclerView();
        addContactButton.setOnClickListener(v -> addContactFromInput());

        // Первичная загрузка данных
        loadContactsFromManager();
        downloadClanList();
    }

    // --- Настройка UI ---

    /**
     * Инициализирует RecyclerView и адаптер, передавая ему все необходимые колбэки для обработки нажатий.
     */
    private void setupRecyclerView() {
        contactsAdapter = new ContactsAdapter(displayList,
                this::handleInfoClick, // Нажатие на кнопку "инфо"
                this::handleWarStatusClick, // Нажатие на статус боя
                this::showContactContextMenu, // Долгое нажатие на контакт
                this::handleGroupClick, // Обычное нажатие на группу (свернуть/развернуть)
                this::showGroupContextMenu // Долгое нажатие на группу
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(contactsAdapter);
    }

    // --- Логика загрузки и обработки данных ---

    /**
     * Загружает `clans.txt` с сервера и запускает его парсинг.
     * Зависимости: `ApiRepository.downloadFile`, `parseAndCacheClanInfo`
     */
    private void downloadClanList() {
        String url = "http://service.neverlands.ru/info/clans.txt";
        File infoDir = new File(getExternalFilesDir(null), "info");
        File destinationFile = new File(infoDir, "clans.txt");

        ApiRepository.downloadFile(url, destinationFile, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String filePath) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Список кланов обновлен", Toast.LENGTH_SHORT).show());
                // После успешной загрузки, парсим файл в кэш
                parseAndCacheClanInfo();
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> Toast.makeText(ContactsActivity.this, "Ошибка обновления списка кланов: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * Парсит локальный файл `clans.txt` и кэширует информацию о кланах в `clanInfoCache`.
     * Выполняется в фоновом потоке. После успешного парсинга инициирует перерисовку списка.
     */
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
                Log.d("ClanInfoParser", "Successfully parsed and cached " + clanInfoCache.size() + " clans.");
                // Перерисовываем список, чтобы отобразить загруженный clanLevel
                runOnUiThread(this::buildDisplayList);
            } catch (IOException e) {
                Log.e("ClanInfoParser", "Error reading or parsing clans.txt", e);
            }
        }).start();
    }

    /**
     * Загружает "плоский" список контактов из ContactsManager и запускает построение сгруппированного списка.
     */
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

    /**
     * Главный метод построения иерархического списка для адаптера.
     * Сортирует контакты, группирует их по кланам, подсчитывает участников и формирует `displayList`.
     */
    private void buildDisplayList() {
        displayList.clear();
        if (allContacts == null) return;

        // Сортировка: сначала по имени клана, затем по нику
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

            // Откладываем контакты без клана, чтобы добавить их в конце
            if (contactClan.isEmpty()) {
                noClanContacts.add(contact);
                continue;
            }

            // Если начался новый клан, создаем для него заголовок
            if (!contactClan.equals(currentClan)) {
                currentClan = contactClan;
                if (!groupExpansionStates.containsKey(currentClan)) {
                    groupExpansionStates.put(currentClan, true); // Новые группы по умолчанию развернуты
                }

                // Получаем доп. информацию о клане из кэша
                String clanId = contact.clanIco.replace(".gif", "");
                ClanInfo clanInfo = clanInfoCache.get(clanId);
                String clanLevel = (clanInfo != null) ? clanInfo.clanLevel : "N/A";

                // Считаем кол-во участников в группе (общее и онлайн)
                int totalMemberCount = 0;
                int onlineMemberCount = 0;
                for (Contact c : allContacts) {
                    if (currentClan.equals(c.clanName)) {
                        totalMemberCount++;
                        if (c.onlineStatus == 1) {
                            onlineMemberCount++;
                        }
                    }
                }

                displayList.add(new ContactsAdapter.GroupHeaderItem(contact.clanName, contact.clanIco, clanLevel, totalMemberCount, onlineMemberCount));
            }

            // Добавляем контакт в список, если его группа развернута
            if (Boolean.TRUE.equals(groupExpansionStates.get(currentClan))) {
                displayList.add(new ContactsAdapter.ContactItem(contact));
            }
        }

        // Добавляем контакты без клана в самый конец
        for (Contact contact : noClanContacts) {
            displayList.add(new ContactsAdapter.ContactItem(contact));
        }

        contactsAdapter.updateItems(displayList);
    }

    // --- Обработчики UI событий ---

    /**
     * Обрабатывает обычное нажатие на заголовок группы (свернуть/развернуть).
     */
    private void handleGroupClick(ContactsAdapter.GroupHeaderItem groupHeaderItem) {
        boolean isExpanded = Boolean.TRUE.equals(groupExpansionStates.get(groupHeaderItem.clanName));
        groupExpansionStates.put(groupHeaderItem.clanName, !isExpanded);
        buildDisplayList(); // Перестраиваем список с учетом нового состояния
    }

    /**
     * Обрабатывает нажатие на кнопку "инфо" у контакта.
     */
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

    /**
     * Обрабатывает нажатие на статус боя у контакта.
     */
    private void handleWarStatusClick(Contact contact) {
        if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
            Intent intent = new Intent(this, LogsActivity.class);
            String url = "http://neverlands.ru/logs.fcg?fid=" + contact.warLogNumber;
            intent.putExtra("url", url);
            startActivity(intent);
        }
    }

    /**
     * Добавляет новый контакт. Вызывается по нажатию на кнопку.
     * `classId` по умолчанию устанавливается в 0 (Нейтрал).
     */
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
                contact.classId = 0; // Нейтрал по умолчанию
                ContactsManager.updateContact(contact);
                addContactButton.setEnabled(true);
                Toast.makeText(ContactsActivity.this, "Контакт " + contact.nick + " добавлен", Toast.LENGTH_LONG).show();
                nickEditText.getText().clear();
                loadContactsFromManager(); // Обновляем список
            }

            @Override
            public void onFailure(String message) {
                addContactButton.setEnabled(true);
                Toast.makeText(ContactsActivity.this, "Ошибка: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // --- Контекстные меню ---

    /**
     * Показывает контекстное меню для отдельного контакта.
     */
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
                                    updatedContact.classId = contact.classId; // Сохраняем старый classId
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

    /**
     * Показывает диалог подтверждения перед удалением контакта.
     */
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

    /**
     * Показывает контекстное меню для группы контактов (клана).
     */
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
                    switch (which) {
                        case 0: updateGroup(group.clanName); break;
                        case 1: deleteGroup(group.clanName); break;
                        case 2: importClanMembers(group); break;
                        case 3: /* Separator */ break;
                        case 4: setClassIdForGroup(group.clanName, 2); break;
                        case 5: setClassIdForGroup(group.clanName, 1); break;
                        case 6: setClassIdForGroup(group.clanName, 0); break;
                    }
                })
                .show();
    }

    /**
     * Импортирует всех членов клана в контакты.
     */
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
                                    public void onSuccess(Contact contact) { Log.d("ClanImport", "Added/Updated: " + contact.nick); }
                                    @Override
                                    public void onFailure(String message) { Log.e("ClanImport", "Failed to add " + nick + ": " + message); }
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
     * Обновляет информацию для всех контактов в указанной группе.
     */
    private void updateGroup(String clanName) {
        Toast.makeText(this, "Обновление группы " + clanName + "...", Toast.LENGTH_SHORT).show();
        for (Contact contact : allContacts) {
            if (clanName.equals(contact.clanName)) {
                ContactsManager.addContact(this, contact.nick, new ContactsManager.ContactOperationCallback() {
                    @Override
                    public void onSuccess(Contact updatedContact) {
                        updatedContact.classId = contact.classId; // Сохраняем старый classId
                        ContactsManager.updateContact(updatedContact);
                    }
                    @Override
                    public void onFailure(String message) { /* Молчаливое падение для одного юзера */ }
                });
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::loadContactsFromManager, 5000);
    }

    /**
     * Удаляет всех контактов из указанной группы.
     */
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

    /**
     * Устанавливает `classId` для всех контактов в указанной группе.
     */
    private void setClassIdForGroup(String clanName, int classId) {
        for (Contact contact : allContacts) {
            if (clanName.equals(contact.clanName)) {
                contact.classId = classId;
                ContactsManager.updateContact(contact);
            }
        }
        loadContactsFromManager();
    }
}
