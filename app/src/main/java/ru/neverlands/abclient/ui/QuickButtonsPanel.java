package ru.neverlands.abclient.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

import java.net.URLEncoder;
import java.util.List;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.adapter.FunctionListAdapter;
import ru.neverlands.abclient.manager.QuickButtonsManager;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.model.QuickButton;
import ru.neverlands.abclient.ContactsActivity;
import ru.neverlands.abclient.LogsActivity;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.TabManager;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.utils.ChatStats;
import androidx.fragment.app.FragmentActivity;

/**
 * Панель быстрых кнопок.
 * Управляет 20 кнопками (10 сверху + 10 снизу) на основной вкладке.
 */
public class QuickButtonsPanel {
    private static final String TAG = "QuickButtonsPanel";
    private static final int BUTTONS_PER_ROW = 10;
    private static final int TOTAL_BUTTONS = 20;
    private static final int REQUEST_CODE_CONTACTS = 1002;
    
    private final Context context;
    private final QuickButtonsManager buttonsManager;
    private final AutoFunctionsManager autoFunctionsManager;
    private final ImageButton[] buttons = new ImageButton[TOTAL_BUTTONS];
    private final TabManager tabManager;
    private OnQuickActionListener actionListener;

    public interface OnQuickActionListener {
        void onQuickAction(QuickActionType actionType);
    }

    // Конструктор: связывает панель с менеджером кнопок, автофункциями и табами.
    public QuickButtonsPanel(Context context, View rootView, TabManager tabManager, OnQuickActionListener listener) {
        this.context = context;
        this.actionListener = listener;
        this.buttonsManager = QuickButtonsManager.getInstance(context);
        this.autoFunctionsManager = AutoFunctionsManager.getInstance(context);
        this.tabManager = tabManager;
        
        initButtons(rootView);
        loadAndUpdateButtons();
    }

    // Инициализация 20 кнопок (10 верх + 10 низ) и привязка обработчиков клика/лонг-клика.
    private void initButtons(View rootView) {
        Log.d(TAG, "initButtons: starting...");
        
        // Верхние кнопки (0-9)
        int[] topButtonIds = {
            R.id.quick_button_0, R.id.quick_button_1, R.id.quick_button_2, R.id.quick_button_3,
            R.id.quick_button_4, R.id.quick_button_5, R.id.quick_button_6, R.id.quick_button_7,
            R.id.quick_button_8, R.id.quick_button_9
        };

        // Нижние кнопки (10-19)
        int[] bottomButtonIds = {
            R.id.quick_button_bottom_0, R.id.quick_button_bottom_1, R.id.quick_button_bottom_2, R.id.quick_button_bottom_3,
            R.id.quick_button_bottom_4, R.id.quick_button_bottom_5, R.id.quick_button_bottom_6, R.id.quick_button_bottom_7,
            R.id.quick_button_bottom_8, R.id.quick_button_bottom_9
        };

        // Инициализация верхних кнопок
        for (int i = 0; i < BUTTONS_PER_ROW; i++) {
            buttons[i] = rootView.findViewById(topButtonIds[i]);
            Log.d(TAG, "initButtons: top button[" + i + "] = " + (buttons[i] != null ? "OK" : "NULL"));
            final int position = i;
            
            if (buttons[i] != null) {
                buttons[i].setOnClickListener(v -> executeAction(position));
                buttons[i].setOnLongClickListener(v -> {
                    showButtonOptions(position);
                    return true;
                });
            }
        }

        // Инициализация нижних кнопок
        for (int i = 0; i < BUTTONS_PER_ROW; i++) {
            buttons[BUTTONS_PER_ROW + i] = rootView.findViewById(bottomButtonIds[i]);
            Log.d(TAG, "initButtons: bottom button[" + (BUTTONS_PER_ROW + i) + "] = " + (buttons[BUTTONS_PER_ROW + i] != null ? "OK" : "NULL"));
            final int position = BUTTONS_PER_ROW + i;
            
            if (buttons[BUTTONS_PER_ROW + i] != null) {
                buttons[BUTTONS_PER_ROW + i].setOnClickListener(v -> executeAction(position));
                buttons[BUTTONS_PER_ROW + i].setOnLongClickListener(v -> {
                    showButtonOptions(position);
                    return true;
                });
            }
        }
        
        Log.d(TAG, "initButtons: finished");
    }

    // Загружаем конфигурацию кнопок из менеджера и синхронизируем UI.
    private void loadAndUpdateButtons() {
        Log.d(TAG, "loadAndUpdateButtons: starting...");
        List<QuickButton> buttonList = buttonsManager.getButtons();
        Log.d(TAG, "loadAndUpdateButtons: buttons count = " + buttonList.size());
        
        for (int i = 0; i < TOTAL_BUTTONS; i++) {
            Log.d(TAG, "loadAndUpdateButtons: checking button[" + i + "], ImageButton=" + (buttons[i] != null ? "OK" : "NULL"));
            if (i < buttonList.size()) {
                QuickButton btn = buttonList.get(i);
                Log.d(TAG, "loadAndUpdateButtons: button[" + i + "] = " + (btn != null ? btn.getActionType() : "null"));
                updateButtonAppearance(i, btn);
            }
        }
    }

    // Обновляем иконку/подпись/состояние одной кнопки.
    private void updateButtonAppearance(int position, QuickButton button) {
        Log.d(TAG, "updateButtonAppearance: position=" + position + ", button=" + (button != null ? button.getActionType() : "null"));
        if (position >= buttons.length || buttons[position] == null) {
            Log.w(TAG, "updateButtonAppearance: button at position " + position + " is null!");
            return;
        }
        
        if (button == null || button.isEmpty()) {
            buttons[position].setImageResource(R.drawable.ic_add);
            buttons[position].setContentDescription("Добавить функцию");
            buttons[position].setAlpha(0.3f);
            buttons[position].setBackgroundResource(R.drawable.quick_button_empty);
            Log.d(TAG, "updateButtonAppearance: set empty icon for position " + position);
        } else {
            // Для заданной функции учитываем включено/выключено (для авто-режимов).
            boolean isEnabled = autoFunctionsManager.isFunctionEnabled(button.getActionType());
            loadIconForAction(buttons[position], button.getActionType(), isEnabled);
            buttons[position].setContentDescription(button.getDisplayName() + (isEnabled ? " (ВКЛ)" : " (ВЫКЛ)"));
            Log.d(TAG, "updateButtonAppearance: icon loaded for position " + position + ", enabled=" + isEnabled);
        }
        
        // Принудительно обновляем кнопку на UI потоке
        buttons[position].post(() -> buttons[position].invalidate());
    }

    // Загрузка иконки: либо URL (Glide), либо локальный drawable.
    private void loadIconForAction(ImageButton button, QuickActionType type, boolean isEnabled) {
        String iconUrl = getIconUrlForAction(type);
        if (iconUrl != null) {
            Glide.with(context)
                .load(iconUrl)
                .placeholder(R.drawable.ic_add)
                .error(getIconForAction(type, isEnabled))
                .into(button);
        } else {
            button.setImageResource(getIconForAction(type, isEnabled));
        }
        
        // Визуальная индикация состояния только для автофункций
        if (isAutoFunction(type)) {
            updateButtonVisualState(button, isEnabled);
        } else {
            // Для обычных функций - обычный вид
            button.setAlpha(1.0f);
            button.setBackgroundResource(R.drawable.quick_button_normal);
        }
    }
    
    // Визуальная индикация включенного/выключенного авто-режима.
    private void updateButtonVisualState(ImageButton button, boolean isEnabled) {
        if (isEnabled) {
            // Включено - полная непрозрачность + зеленоватая подсветка
            button.setAlpha(1.0f);
            button.setBackgroundResource(R.drawable.quick_button_enabled);
        } else {
            // Выключено - полупрозрачность
            button.setAlpha(0.6f);
            button.setBackgroundResource(R.drawable.quick_button_disabled);
        }
    }

    // Карта внешних иконок (из image.neverlands.ru) для некоторых быстрых действий.
    private String getIconUrlForAction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT:
                return "http://image.neverlands.ru/achievement/2/a_2_10.gif";
            case QUICK_ACTIONS:
                return null;
            case AUTO_FISH:
                return "http://image.neverlands.ru/achievement/40/a_40_10.gif";
            case AUTO_BAIT:
                return null;
            case AUTO_SKIN:
                return "http://image.neverlands.ru/achievement/70/a_70_10.gif";
            case AUTO_ATTACK:
                return "http://image.neverlands.ru/achievement/13/a_13_10.gif";
            case AUTO_INVISIBLE:
                return "http://image.neverlands.ru/weapon/i_w27_53.gif";
            case LOCATION_TRACKING:
                return "http://image.neverlands.ru/signs/compass.gif";
            case AUTO_DETECT:
                return "http://image.neverlands.ru/achievement/26/a_26_10.gif";
            case AUTO_SUMMON:
                return "http://image.neverlands.ru/achievement/11/a_11_10.gif";
            case AUTO_CURE:
                return "http://image.neverlands.ru/achievement/150/a_150_10.gif";
            case AUTO_DRINK:
                return null;
            case AUTO_MOVING:
                return null;
            case AUTO_CUT:
                return null;
            case AUTO_REFRESH:
                return null;
            case OPEN_CONTACTS:
                return null;
            case OPEN_PINFO:
                return null;
            case OPEN_LOGS:
                return null;
            case OPEN_STATS:
                return null;
            case REFRESH_CONTACTS:
                return null;
            case QUICK_SELF_RASS:
                return "http://image.neverlands.ru/weapon/i_w28_23.gif";
            case QUICK_OPEN_NEVID:
                return "http://image.neverlands.ru/weapon/i_w28_28.gif";
            case QUICK_TELEPORT:
                return "http://image.neverlands.ru/weapon/i_w28_22.gif";
            case QUICK_ISLAND:
                return "http://image.neverlands.ru/weapon/i_w28_22.gif";
            case QUICK_TOTEM:
                return "http://image.neverlands.ru/signs/totems/9.gif";
            case QUICK_ELIXIR_BLAZ:
                return "http://image.neverlands.ru/weapon/i_w61_107.gif";
            case QUICK_ELIXIR_CURE:
                return "http://image.neverlands.ru/weapon/i_w61_104.gif";
            case QUICK_ELIXIR_RESTORE:
                return "http://image.neverlands.ru/weapon/i_w61_101.gif";
            default:
                return null;
        }
    }

    // Локальные иконки/плейсхолдеры; при необходимости можно различать ON/OFF.
    private int getIconForAction(QuickActionType type, boolean isEnabled) {
        // Для автофункций пока возвращаем те же иконки, но с разной прозрачностью
        // Позже нужно создать отдельные иконки для вкл/выкл состояний
        int iconRes = getIconForAction(type);
        
        // Для автофункций можно добавить визуальную индикацию
        if (isEnabled && isAutoFunction(type)) {
            // В будущем здесь будет переход на _on иконку
        }
        
        return iconRes;
    }
    
    // Авто-функции имеют ON/OFF визуальное состояние.
    private boolean isAutoFunction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT:
            case AUTO_FISH:
            case AUTO_BAIT:
            case AUTO_SKIN:
            case AUTO_ATTACK:
            case AUTO_INVISIBLE:
            case LOCATION_TRACKING:
            case AUTO_DETECT:
            case AUTO_SUMMON:
            case AUTO_CURE:
            case AUTO_DRINK:
            case AUTO_MOVING:
            case AUTO_CUT:
            case AUTO_REFRESH:
                return true;
            default:
                return false;
        }
    }
    
    // Fallback иконки по типу (локальные ресурсы приложения).
    private int getIconForAction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT:
                return R.drawable.ic_auto_fight;
            case QUICK_ACTIONS:
                return R.drawable.ic_sort;
            case AUTO_FISH:
                return R.drawable.ic_auto_fish;
            case AUTO_BAIT:
                return R.drawable.ic_add;
            case AUTO_SKIN:
                return R.drawable.ic_lez_fight;
            case AUTO_ATTACK:
                return R.drawable.ic_auto_attack;
            case AUTO_INVISIBLE:
                return R.drawable.ic_auto_invisible;
            case LOCATION_TRACKING:
                return R.drawable.ic_location;
            case AUTO_DETECT:
                return R.drawable.ic_auto_detect;
            case AUTO_SUMMON:
                return R.drawable.ic_auto_summon;
            case AUTO_CURE:
                return R.drawable.ic_red_cross;
            case AUTO_DRINK:
                return R.drawable.ic_add;
            case AUTO_MOVING:
                return R.drawable.ic_add;
            case AUTO_CUT:
                return R.drawable.ic_add;
            case AUTO_REFRESH:
                return R.drawable.ic_refresh;
            case OPEN_CONTACTS:
                return R.drawable.ic_add_contact;
            case OPEN_PINFO:
                return R.drawable.ic_info;
            case OPEN_LOGS:
                return R.drawable.ic_add;
            case OPEN_STATS:
                return R.drawable.ic_info;
            case REFRESH_CONTACTS:
                return R.drawable.ic_refresh;
            case QUICK_SELF_RASS:
                return R.drawable.ic_back;
            case QUICK_OPEN_NEVID:
                return R.drawable.ic_expand_more;
            case QUICK_TELEPORT:
                return R.drawable.ic_sort;
            case QUICK_ISLAND:
                return R.drawable.ic_add;
            case QUICK_TOTEM:
                return R.drawable.ic_add;
            case QUICK_ELIXIR_BLAZ:
                return R.drawable.ic_add;
            case QUICK_ELIXIR_CURE:
                return R.drawable.ic_add;
            case QUICK_ELIXIR_RESTORE:
                return R.drawable.ic_add;
            default:
                return R.drawable.ic_add;
        }
    }

    // Исполнение действия кнопки: либо назначение, либо запуск функции.
    private void executeAction(int position) {
        Log.d(TAG, "executeAction: position=" + position);
        QuickButton button = buttonsManager.getButton(position);
        Log.d(TAG, "executeAction: button=" + (button != null ? button.getActionType() : "null"));
        
        if (button == null || button.isEmpty()) {
            Log.d(TAG, "executeAction: button is empty, showing selector");
            showFunctionSelector(position);
            return;
        }

        QuickActionType actionType = button.getActionType();
        Log.d(TAG, "executeAction: actionType=" + actionType);
        
        switch (actionType) {
            case AUTO_FIGHT:
                Log.d(TAG, "executeAction: AUTO_FIGHT");
                autoFunctionsManager.toggleAutoFight();
                Toast.makeText(context, autoFunctionsManager.isAutoFightEnabled() ? "Авто-Бой ВКЛ" : "Авто-Бой ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case QUICK_ACTIONS:
                if (actionListener != null) {
                    actionListener.onQuickAction(actionType);
                }
                break;
            case AUTO_FISH:
                autoFunctionsManager.toggleAutoFish();
                Toast.makeText(context, autoFunctionsManager.isAutoFishEnabled() ? "Авто-Рыбалка ВКЛ" : "Авто-Рыбалка ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_SKIN:
                autoFunctionsManager.toggleAutoSkin();
                Toast.makeText(context, autoFunctionsManager.isAutoSkinEnabled() ? "Авто-Охота ВКЛ" : "Авто-Охота ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_ATTACK:
                showAutoAttackToolSelector();
                break;
            case AUTO_INVISIBLE:
                autoFunctionsManager.toggleAutoInvisible();
                Toast.makeText(context, autoFunctionsManager.isAutoInvisibleEnabled() ? "Авто-Невид ВКЛ" : "Авто-Невид ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case LOCATION_TRACKING:
                autoFunctionsManager.toggleLocationTracking();
                Toast.makeText(context, autoFunctionsManager.isLocationTrackingEnabled() ? "Слежение ВКЛ" : "Слежение ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_DETECT:
                autoFunctionsManager.toggleAutoDetect();
                Toast.makeText(context, autoFunctionsManager.isAutoDetectEnabled() ? "Авто-Обнаружение ВКЛ" : "Авто-Обнаружение ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_SUMMON:
                autoFunctionsManager.toggleAutoSummon();
                Toast.makeText(context, autoFunctionsManager.isAutoSummonEnabled() ? "Авто-Тотем ВКЛ" : "Авто-Тотем ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_CURE:
                autoFunctionsManager.toggleAutoCure();
                Toast.makeText(context, autoFunctionsManager.isAutoCureEnabled() ? "Авто-Лечение ВКЛ" : "Авто-Лечение ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_BAIT:
                autoFunctionsManager.toggleAutoBait();
                Toast.makeText(context, autoFunctionsManager.isAutoBaitEnabled() ? "Авто-Приманка ВКЛ" : "Авто-Приманка ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_DRINK:
                autoFunctionsManager.toggleAutoDrink();
                Toast.makeText(context, autoFunctionsManager.isAutoDrinkEnabled() ? "Авто-Питье ВКЛ" : "Авто-Питье ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_MOVING:
                autoFunctionsManager.toggleAutoMoving();
                Toast.makeText(context, autoFunctionsManager.isAutoMovingEnabled() ? "Авто-Движение ВКЛ" : "Авто-Движение ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_CUT:
                autoFunctionsManager.toggleAutoCut();
                Toast.makeText(context, autoFunctionsManager.isAutoCutEnabled() ? "Авто-Травник ВКЛ" : "Авто-Травник ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_REFRESH:
                autoFunctionsManager.toggleAutoRefresh();
                Toast.makeText(context, autoFunctionsManager.isAutoRefreshEnabled() ? "Авто-Обновление ВКЛ" : "Авто-Обновление ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case OPEN_CONTACTS:
                openContacts();
                break;
            case OPEN_PINFO:
                openPinfo();
                break;
            case OPEN_LOGS:
                openLogs();
                break;
            case OPEN_STATS:
                openStats();
                break;
            case REFRESH_CONTACTS:
                refreshContacts();
                break;
            case QUICK_SELF_RASS:
                executeQuickAction("selfRass", "Рассеять невид");
                break;
            case QUICK_OPEN_NEVID:
                executeQuickAction("openNevid", "Обнаружение");
                break;
            case QUICK_TELEPORT:
                executeQuickAction("teleport", "Телепорт");
                break;
            case QUICK_ISLAND:
                executeQuickAction("island", "Остров");
                break;
            case QUICK_TOTEM:
                executeQuickAction("totem", "Тотем");
                break;
            case QUICK_ELIXIR_BLAZ:
                executeQuickAction("elixirBlaz", "Эликсир Блаженства");
                break;
            case QUICK_ELIXIR_CURE:
                executeQuickAction("elixirCure", "Эликсир Исцеления");
                break;
            case QUICK_ELIXIR_RESTORE:
                executeQuickAction("elixirRestore", "Эликсир Восстановления");
                break;
            default:
                Toast.makeText(context, "Функция не реализована", Toast.LENGTH_SHORT).show();
        }
    }

    // Быстрые действия (эликсиры/тотем/невид) делегируются в FastActionManager.
    private void executeQuickAction(String actionKey, String actionName) {
        Log.d(TAG, "executeQuickAction: actionKey=" + actionKey + ", actionName=" + actionName);
        Toast.makeText(context, actionName, Toast.LENGTH_SHORT).show();
        
        switch (actionKey) {
            case "selfRass":
                FastActionManager.fastAttackSelfRass();
                break;
            case "openNevid":
                FastActionManager.fastAttackOpenNevid();
                break;
            case "teleport":
                FastActionManager.fastAttackTeleport("");
                break;
            case "island":
                FastActionManager.fastAttackIslandPot();
                break;
            case "totem":
                FastActionManager.fastAttackTotem("");
                break;
            case "elixirBlaz":
                FastActionManager.fastAttackBlazElixir();
                break;
            case "elixirCure":
                FastActionManager.fastAttackMomentCureElixir();
                break;
            case "elixirRestore":
                FastActionManager.fastAttackMomentRestoreElixir();
                break;
            default:
                Log.w(TAG, "executeQuickAction: unknown actionKey=" + actionKey);
        }
    }

    // Лонг-клик по кнопке: меню настроек или удаление.
    private void showButtonOptions(int position) {
        QuickButton button = buttonsManager.getButton(position);

        if (button.isEmpty()) {
            showFunctionSelector(position);
        } else if (button.getActionType() == QuickActionType.AUTO_FIGHT) {
            // Для AUTO_FIGHT показываем меню: Настройки / Удалить
            new AlertDialog.Builder(context)
                .setTitle("Авто-Бой")
                .setItems(new CharSequence[]{"Настройки автобоя", "Удалить кнопку"}, (dialog, which) -> {
                    if (which == 0) {
                        openAutoBoiSettings();
                    } else {
                        showRemoveConfirmation(position);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
        } else if (button.getActionType() == QuickActionType.AUTO_ATTACK) {
            // Для AUTO_ATTACK показываем меню: выбор инструмента / удалить кнопку.
            new AlertDialog.Builder(context)
                    .setTitle("Авто-Нападение")
                    .setItems(new CharSequence[]{"Выбрать инструмент", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showAutoAttackToolSelector();
                        } else {
                            showRemoveConfirmation(position);
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else if (button.getActionType() == QuickActionType.LOCATION_TRACKING) {
            new AlertDialog.Builder(context)
                    .setTitle("Слежение за локацией")
                    .setItems(new CharSequence[]{"Интервал опроса локации", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showWalkersPollIntervalSelector();
                        } else {
                            showRemoveConfirmation(position);
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            showRemoveConfirmation(position);
        }
    }

    // Открывает настройки авто-боя во фрагменте.
    private void openAutoBoiSettings() {
        if (context instanceof FragmentActivity) {
            AutoBoiSettingsFragment fragment = new AutoBoiSettingsFragment();
            fragment.show(((FragmentActivity) context).getSupportFragmentManager(), "autoboi_settings");
        }
    }

    /**
     * Выбор инструмента авто-нападения (аналог dropdown из C# `FormAutoAttack.cs`).
     *
     * Зависимости:
     * - `AutoFunctionsManager.setAutoAttackToolId(...)` — сохраняет выбор в prefs,
     * - `AppVars.AutoAttackToolId` — runtime-синхронизация для post-filter потока.
     */
    private void showAutoAttackToolSelector() {
        final CharSequence[] labels = new CharSequence[]{
                "0 — Отключено",
                "1 — Боевые",
                "2 — Закрытые боевые",
                "3 — Кулачки",
                "4 — Закрытые кулачки",
                "5 — Портал"
        };
        int selected = autoFunctionsManager.getAutoAttackToolId();
        if (selected < 0 || selected > 5) selected = 0;

        new AlertDialog.Builder(context)
                .setTitle("Инструмент авто-нападения")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    autoFunctionsManager.setAutoAttackToolId(which);
                    loadAndUpdateButtons();
                    Toast.makeText(context, "Выбран инструмент: " + labels[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // Диалог выбора функции для конкретной кнопки.
    private void showWalkersPollIntervalSelector() {
        final int[] values = new int[]{1, 2, 5, 10};
        final CharSequence[] labels = new CharSequence[]{"1 сек", "2 сек", "5 сек", "10 сек"};
        int currentValue = autoFunctionsManager.getWalkersPollIntervalSec();
        int selectedIndex = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index] == currentValue) {
                selectedIndex = index;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("Интервал опроса локации")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    int sec = values[which];
                    autoFunctionsManager.setWalkersPollIntervalSec(sec);
                    Toast.makeText(context, "Интервал опроса: " + sec + " сек", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showFunctionSelector(int position) {
        View dialogView = View.inflate(context, R.layout.dialog_select_function, null);
        android.widget.ListView listView = dialogView.findViewById(R.id.functions_list);
        
        FunctionListAdapter adapter = new FunctionListAdapter(context, selectedType -> {
            buttonsManager.assignFunction(position, selectedType);
            loadAndUpdateButtons();
            Toast.makeText(context, "Функция \"" + selectedType.getDisplayName() + "\" добавлена", Toast.LENGTH_SHORT).show();
        });
        
        listView.setAdapter(adapter);
        
        String title = position < BUTTONS_PER_ROW ? "Выберите функцию (верхний ряд)" : "Выберите функцию (нижний ряд)";
        
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton("Отмена", null)
            .create();
        
        // Устанавливаем слушатель для закрытия диалога при выборе из списка
        adapter.setDialog(dialog);
        
        dialog.show();
    }

    // Подтверждение удаления назначенной функции с кнопки.
    private void showRemoveConfirmation(int position) {
        QuickButton button = buttonsManager.getButton(position);
        String rowName = position < BUTTONS_PER_ROW ? "верхнего" : "нижнего";
        
        new AlertDialog.Builder(context)
            .setTitle("Удалить функцию")
            .setMessage("Удалить \"" + button.getDisplayName() + "\" с кнопки " + rowName + " ряда?")
            .setPositiveButton("Удалить", (dialog, which) -> {
                buttonsManager.removeFunction(position);
                loadAndUpdateButtons();
                Toast.makeText(context, "Функция удалена", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // Открыть окно контактов.
    private void openContacts() {
        Intent intent = new Intent(context, ContactsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // Открыть окно логов.
    private void openLogs() {
        Intent intent = new Intent(context, LogsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // Открыть окно статистики боя/лута.
    private void openStats() {
        // Основная реализация: кастомный диалог с иконками и действиями.
        boolean useNewStatsDialog = true;
        if (useNewStatsDialog) {
            int padding = dpToPx(16);
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(padding, padding, padding, padding);

            TextView statsText = new TextView(context);
            statsText.setText(buildStatsText());
            root.addView(statsText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            actionsParams.topMargin = dpToPx(8);
            actions.setLayoutParams(actionsParams);

            ImageButton resetButton = new ImageButton(context);
            resetButton.setImageResource(R.drawable.ic_refresh);
            resetButton.setBackgroundResource(android.R.color.transparent);
            resetButton.setContentDescription("Сброс статистики");

            ImageButton copyButton = new ImageButton(context);
            copyButton.setImageResource(R.drawable.ic_copy);
            copyButton.setBackgroundResource(android.R.color.transparent);
            copyButton.setContentDescription("Копировать в буфер");

            ImageButton closeButton = new ImageButton(context);
            closeButton.setImageResource(R.drawable.ic_close);
            closeButton.setBackgroundResource(android.R.color.transparent);
            closeButton.setContentDescription("Закрыть");

            int iconPad = dpToPx(6);
            resetButton.setPadding(iconPad, iconPad, iconPad, iconPad);
            copyButton.setPadding(iconPad, iconPad, iconPad, iconPad);
            closeButton.setPadding(iconPad, iconPad, iconPad, iconPad);

            actions.addView(resetButton);
            actions.addView(copyButton);
            actions.addView(closeButton);
            root.addView(actions);

            AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Статистика")
                .setView(root)
                .create();

            resetButton.setOnClickListener(v -> {
                ChatStats.reset();
                statsText.setText(buildStatsText());
                Toast.makeText(context, "Статистика сброшена", Toast.LENGTH_SHORT).show();
            });

            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("stats", buildStatsText());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Буфер недоступен", Toast.LENGTH_SHORT).show();
                }
            });

            closeButton.setOnClickListener(v -> dialog.dismiss());

            dialog.show();
            return;
        }
        long xp = ru.neverlands.abclient.utils.ChatStats.getTotalXp();
        java.util.List<String> loot = ru.neverlands.abclient.utils.ChatStats.getLootLog();
        String logPath = ru.neverlands.abclient.utils.Chat.getCurrentLogPath();

        StringBuilder sb = new StringBuilder();
        sb.append("Опыт: ").append(xp).append("\n");
        sb.append("Лут: ").append(loot.size()).append("\n\n");

        if (!loot.isEmpty()) {
            sb.append("Последние находки:\n");
            int start = Math.max(0, loot.size() - 10);
            for (int i = start; i < loot.size(); i++) {
                sb.append("• ").append(loot.get(i)).append("\n");
            }
            sb.append("\n");
        }

        if (logPath != null && !logPath.isEmpty()) {
            sb.append("Лог: ").append(logPath).append("\n");
        }

        new AlertDialog.Builder(context)
            .setTitle("Статистика")
            .setMessage(sb.toString())
            .setPositiveButton("ОК", null)
            .show();
    }

    // Формирование текста статистики из ChatStats (XP/поединки/дроп).
    private String buildStatsText() {
        long xp = ChatStats.getTotalXp();
        long fights = ChatStats.getTotalFights();
        java.util.List<String> loot = ChatStats.getLootLog();

        StringBuilder sb = new StringBuilder();
        sb.append("Опыт: ").append(xp).append("\n");
        sb.append("Поединки: ").append(fights).append("\n");
        sb.append("Лут/дроп с ботов: ").append(loot.size()).append("\n\n");

        if (!loot.isEmpty()) {
            sb.append("Последние находки:\n");
            int start = Math.max(0, loot.size() - 10);
            for (int i = start; i < loot.size(); i++) {
                sb.append("• ").append(loot.get(i)).append("\n");
            }
        }

        return sb.toString().trim();
    }

    // Утилита для перевода dp -> px.
    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    // Диалог ввода ника и переход в pinfo.
    private void openPinfo() {
        View dialogView = View.inflate(context, R.layout.dialog_input_nick, null);
        android.widget.EditText editText = dialogView.findViewById(R.id.input_nick);
        
        new AlertDialog.Builder(context)
            .setTitle("Введите ник игрока")
            .setView(dialogView)
            .setPositiveButton("Открыть", (dialog, which) -> {
                String nick = editText.getText().toString().trim();
                if (nick.isEmpty()) {
                    Toast.makeText(context, "Введите ник", Toast.LENGTH_SHORT).show();
                    return;
                }
                openPinfoTab(nick);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // Открыть вкладку pinfo с нужной кодировкой Windows-1251.
    private void openPinfoTab(String nick) {
        try {
            String encodedNick = URLEncoder.encode(nick, "windows-1251");
            String url = "http://neverlands.ru/pinfo.cgi?" + encodedNick;
            
            if (tabManager != null) {
                tabManager.openTab(url, "PINFO");
            } else {
                Toast.makeText(context, "TabManager не доступен", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error encoding nick", e);
            Toast.makeText(context, "Ошибка кодирования URL", Toast.LENGTH_SHORT).show();
        }
    }

    // Принудительное обновление списка контактов (с уведомлением).
    private void refreshContacts() {
        Toast.makeText(context, "Обновление контактов...", Toast.LENGTH_SHORT).show();
        ContactsManager.refreshAllContacts(context, () -> {
            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(() -> 
                Toast.makeText(context, "Контакты обновлены", Toast.LENGTH_SHORT).show()
            );
        });
    }

    // Внешний вызов для пересинхронизации кнопок (например, после настроек).
    public void refresh() {
        loadAndUpdateButtons();
    }
}
