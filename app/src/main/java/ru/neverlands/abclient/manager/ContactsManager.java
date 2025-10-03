package ru.neverlands.abclient.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ru.neverlands.abclient.db.AppDatabase;
import ru.neverlands.abclient.db.ContactDao;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.repository.ApiRepository;
import ru.neverlands.abclient.utils.CustomDebugLogger;

public class ContactsManager {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public interface ContactOperationCallback {
        void onSuccess(Contact contact);
        void onFailure(String message);
    }

    public interface LoadContactsCallback {
        void onSuccess(List<Contact> contacts);
        void onFailure(String message);
    }

    public static void addContact(Context context, String nick, ContactOperationCallback callback) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String logFileName = "AddCont_" + timestamp + ".txt";
        CustomDebugLogger.initialize(logFileName);
        CustomDebugLogger.log("Starting to add contact: " + nick);

        // Шаг 1: Получаем ID и имя
        ApiRepository.getPlayerId(nick, new ApiRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String serverResponse) {
                handler.post(() -> {
                    Toast.makeText(context, "Шаг 1: " + serverResponse, Toast.LENGTH_SHORT).show();
                    String[] parts = serverResponse.split("\\|");
                    if (parts.length < 1 || parts[0].isEmpty()) {
                        CustomDebugLogger.log("ERROR: Could not get ID from response: " + serverResponse);
                        CustomDebugLogger.close();
                        callback.onFailure("Не удалось получить ID из ответа: " + serverResponse);
                        return;
                    }
                    String playerId = parts[0];
                    CustomDebugLogger.log("Step 1 SUCCESS. PlayerID: " + playerId);

                    // Добавляем задержку в 1 секунду перед вторым запросом
                    handler.postDelayed(() -> {
                        CustomDebugLogger.log("1 second delay passed. Starting Step 2.");
                        // Шаг 2: Делаем второй, независимый запрос
                        getPlayerInfoAndSave(context, playerId, new ContactOperationCallback() {
                            @Override
                            public void onSuccess(Contact contact) {
                                CustomDebugLogger.log("Step 2 SUCCESS. Contact info received.");
                                CustomDebugLogger.close();
                                callback.onSuccess(contact);
                            }

                            @Override
                            public void onFailure(String message) {
                                CustomDebugLogger.log("Step 2 FAILED: " + message);
                                CustomDebugLogger.close();
                                callback.onFailure(message);
                            }
                        });
                    }, 1000);
                });
            }

            @Override
            public void onFailure(String message) {
                CustomDebugLogger.log("Step 1 FAILED: " + message);
                CustomDebugLogger.close();
                handler.post(() -> callback.onFailure("Ошибка на шаге 1: " + message));
            }
        });
    }

    private static void getPlayerInfoAndSave(Context context, String playerId, ContactOperationCallback callback) {
        ApiRepository.getPlayerInfo(playerId, new ApiRepository.ApiCallback<Contact>() {
            @Override
            public void onSuccess(Contact contact) {
                executor.execute(() -> {
                    ContactDao dao = AppDatabase.getDatabase(context).contactDao();
                    dao.insertOrUpdate(contact);
                    handler.post(() -> callback.onSuccess(contact));
                });
            }

            @Override
            public void onFailure(String message) {
                handler.post(() -> callback.onFailure(message));
            }
        });
    }
    public static void loadContacts(Context context, LoadContactsCallback callback) {
        executor.execute(() -> {
            try {
                ContactDao dao = AppDatabase.getDatabase(context).contactDao();
                List<Contact> contacts = dao.getAll();
                handler.post(() -> callback.onSuccess(contacts));
            } catch (Exception e) {
                handler.post(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Error loading contacts"));
            }
        });
    }

    public static void deleteContact(Context context, Contact contact) {
        executor.execute(() -> {
            ContactDao dao = AppDatabase.getDatabase(context).contactDao();
            dao.deleteById(contact.playerID);
        });
    }

    // Метод для обратной совместимости
    public static int getClassIdOfContact(String name) {
        // Эта логика больше не актуальна в новой системе, но нужна для старого кода.
        // Возвращаем 0 (нейтрал) как заглушку.
        return 0;
    }
}