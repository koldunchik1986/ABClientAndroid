package ru.neverlands.anclient.manager;

import android.content.Context;
import android.content.SharedPreferences;
import ru.neverlands.anclient.utils.AppLog;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ru.neverlands.anclient.model.QuickActionType;
import ru.neverlands.anclient.model.QuickButton;

/**
 * Менеджер быстрых кнопок.
 * Управляет загрузкой, сохранением и назначением функций на кнопки.
 */
public class QuickButtonsManager {
    private static final String TAG = "QuickButtonsManager";
    private static final String PREFS_NAME = "quick_buttons_prefs";
    private static final String KEY_BUTTONS = "quick_buttons";
    private static final int BUTTON_COUNT = 20;

    private static QuickButtonsManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson;
    private List<QuickButton> buttons;

    private QuickButtonsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.buttons = loadButtons();
    }

    public static synchronized QuickButtonsManager getInstance(Context context) {
        if (instance == null) {
            instance = new QuickButtonsManager(context);
        }
        return instance;
    }

    /**
     * Загрузить кнопки из SharedPreferences.
     */
    public List<QuickButton> loadButtons() {
        String json = prefs.getString(KEY_BUTTONS, null);
        if (json == null || json.isEmpty()) {
            // Инициализируем пустыми кнопками
            buttons = new ArrayList<>();
            for (int i = 0; i < BUTTON_COUNT; i++) {
                buttons.add(new QuickButton(i, QuickActionType.NONE));
            }
            return buttons;
        }

        try {
            Type listType = new TypeToken<List<QuickButton>>(){}.getType();
            List<QuickButton> loaded = gson.fromJson(json, listType);
            if (loaded != null && loaded.size() == BUTTON_COUNT) {
                buttons = loaded;
            } else {
                // Неправильный размер - пересоздаем
                buttons = new ArrayList<>();
                for (int i = 0; i < BUTTON_COUNT; i++) {
                    if (i < loaded.size()) {
                        buttons.add(loaded.get(i));
                    } else {
                        buttons.add(new QuickButton(i, QuickActionType.NONE));
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error loading buttons", e);
            buttons = new ArrayList<>();
            for (int i = 0; i < BUTTON_COUNT; i++) {
                buttons.add(new QuickButton(i, QuickActionType.NONE));
            }
        }
        return buttons;
    }

    /**
     * Сохранить кнопки в SharedPreferences.
     */
    public void saveButtons() {
        String json = gson.toJson(buttons);
        prefs.edit().putString(KEY_BUTTONS, json).apply();
        AppLog.d(TAG, "Buttons saved: " + json);
    }

    /**
     * Получить список всех кнопок.
     */
    public List<QuickButton> getButtons() {
        AppLog.d(TAG, "getButtons called, buttons = " + (buttons != null ? buttons.size() : "null"));
        if (buttons == null) {
            loadButtons();
        }
        return buttons;
    }

    /**
     * Получить кнопку по позиции.
     */
    public QuickButton getButton(int position) {
        AppLog.d(TAG, "getButton position=" + position);
        if (position < 0 || position >= BUTTON_COUNT) {
            return null;
        }
        if (buttons == null) {
            loadButtons();
        }
        AppLog.d(TAG, "getButton returns: " + buttons.get(position).getActionType());
        return buttons.get(position);
    }

    /**
     * Назначить функцию на кнопку.
     */
    public void assignFunction(int position, QuickActionType actionType) {
        AppLog.d(TAG, "assignFunction: position=" + position + ", actionType=" + actionType);
        if (position < 0 || position >= BUTTON_COUNT) {
            AppLog.w(TAG, "Invalid position: " + position);
            return;
        }
        if (buttons == null) {
            loadButtons();
        }
        buttons.set(position, new QuickButton(position, actionType));
        saveButtons();
        AppLog.d(TAG, "assignFunction: saved, button at position " + position + " is now " + actionType);
        AppLog.d(TAG, "Assigned " + actionType + " to position " + position);
    }

    /**
     * Удалить функцию с кнопки (сбросить в пустую).
     */
    public void removeFunction(int position) {
        assignFunction(position, QuickActionType.NONE);
    }

    /**
     * Очистить все кнопки.
     */
    public void clearAll() {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            buttons.set(i, new QuickButton(i, QuickActionType.NONE));
        }
        saveButtons();
    }

    /**
     * Получить количество кнопок.
     */
    public int getButtonCount() {
        return BUTTON_COUNT;
    }
}
