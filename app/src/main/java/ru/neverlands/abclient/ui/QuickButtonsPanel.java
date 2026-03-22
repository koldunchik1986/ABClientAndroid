package ru.neverlands.abclient.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.adapter.FunctionListAdapter;
import ru.neverlands.abclient.manager.QuickButtonsManager;
import ru.neverlands.abclient.model.Prims;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.model.QuickButton;
import ru.neverlands.abclient.ContactsActivity;
import ru.neverlands.abclient.LogsActivity;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.manager.TabManager;
import ru.neverlands.abclient.manager.AutoFunctionsManager;
import ru.neverlands.abclient.model.Cell;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.ChatStats;
import ru.neverlands.abclient.utils.ExtMap;
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
    // Идентификаторы "плоских" категорий навигатора.
    // Используются в add/remove-потоке (UI -> applyCategoryCells -> UserConfig -> save()).
    private static final int NAV_CATEGORY_FAVORITES = 1;
    private static final int NAV_CATEGORY_CITY_VILLAGE = 2;
    private static final int NAV_CATEGORY_CITY_FORPOST = 3;
    private static final int NAV_CATEGORY_CITY_OKTAL = 4;
    private static final int NAV_CATEGORY_OBJECTS = 5;
    private static final int NAV_CATEGORY_TELEPORTS = 6;
    // Базовый шаблон извлечения "region-number" из строки выбора клетки.
    // Нужен для универсального ввода: поддерживаются и "8-259", и "8-259 - Название".
    private static final Pattern NAV_CELL_PATTERN = Pattern.compile("(\\d+-\\d+)");
    // Формат сериализации динамических подкатегорий Города в профиле:
    // "Имя|8-259,8-294;Другая|12-428".
    private static final String NAV_CITY_SUBCATEGORY_ENTRY_DELIMITER = ";";
    private static final String NAV_CITY_SUBCATEGORY_VALUE_DELIMITER = "|";
    private static final String[] FISH_HAND_OPTIONS = new String[] {
            "Нет",
            "Любая удочка",
            "Ореховая Удочка",
            "Ивовая Удочка",
            "Бамбуковая Удочка",
            "Бамбуковая 2-х коленная Удочка",
            "Бамбуковая 3-х коленная Удочка",
            "Телескопическая Удочка",
            "Телескопическая Облегченная Удочка",
            "Телескопический Спиннинг",
            "Сачок"
    };
    private static final String[] FISH_PRIM_LABELS = new String[] {
            "Хлеб",
            "Червяк",
            "Крупный червяк",
            "Опарыш",
            "Мотыль",
            "Блесна",
            "Донка",
            "Мормышка",
            "Заговоренная блесна"
    };
    private static final int[] FISH_PRIM_FLAGS = new int[] {
            Prims.Bread,
            Prims.Worm,
            Prims.BigWorm,
            Prims.Stink,
            Prims.Fly,
            Prims.Light,
            Prims.Donka,
            Prims.Morm,
            Prims.HiFlight
    };
    
    private final Context context;
    private final QuickButtonsManager buttonsManager;
    private final AutoFunctionsManager autoFunctionsManager;
    private final ImageButton[] buttons = new ImageButton[TOTAL_BUTTONS];
    private final TabManager tabManager;
    private OnQuickActionListener actionListener;

    public interface OnQuickActionListener {
        void onQuickAction(QuickActionType actionType);
    }

    /**
     * Универсальный callback для операций выбора/удаления клетки.
     *
     * Зависимости:
     * - используется в showNavigatorAddCellDialog(...) для передачи выбранной клетки наружу;
     * - используется в renderNavigatorCells(...) как callback удаления по кнопке "-";
     * - позволяет не дублировать однотипный код add/remove между категориями и подкатегориями.
     */
    private interface OnNavigatorCellPicked {
        void onCellPicked(String cellNum);
    }

    /**
     * Локальная модель одной динамической подкатегории в секции "Города".
     *
     * Поля:
     * - name: пользовательское имя подкатегории;
     * - cells: список клеток "region-number" для этой подкатегории.
     *
     * Зависимости:
     * - формируется из строки профиля через parseCitySubcategories(...);
     * - используется при отрисовке (addNavigatorCityLeafCategory);
     * - сохраняется обратно через encodeCitySubcategories(...).
     */
    private static final class CitySubcategory {
        final String name;
        final String[] cells;

        CitySubcategory(String name, String[] cells) {
            this.name = name;
            this.cells = cells;
        }
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
                return R.drawable.ic_globe;
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
                showNavigatorDialog();
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
        } else if (button.getActionType() == QuickActionType.AUTO_FISH) {
            new AlertDialog.Builder(context)
                    .setTitle("Авто-Рыбалка")
                    .setItems(new CharSequence[]{"Настройки авто-рыбалки", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showAutoFishSettingsDialog();
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
        } else if (button.getActionType() == QuickActionType.AUTO_MOVING) {
            new AlertDialog.Builder(context)
                    .setTitle("Навигатор")
                    .setItems(new CharSequence[]{"Запустить / выбрать пункт назначения", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showNavigatorDialog();
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

    /**
     * Настройки авто-рыбалки через long-press quick-кнопки.
     *
     * Зависимости:
     * - `AppVars.Profile` хранит значения (`FishAutoWear`, `FishHandOne`, `FishHandTwo`, `FishEnabledPrims`);
     * - `MainPhp`/`ParsedDressed` читают эти поля в runtime для выбора снастей/приманки;
     * - значения и названия опций соответствуют C# (`FormSettingsGeneral`).
     */
    /**
     * Единая форма настроек авто-рыбалки: чекбоксы приманок + два выпадающих списка по рукам.
     *
     * Зависимости:
     * - `AppVars.Profile` хранит значения (`FishAutoWear`, `FishHandOne`, `FishHandTwo`, `FishEnabledPrims`,
     *   `FishTiedHigh`, `FishTiedZero`, `FishStopOverWeight`, `FishChatReport`, `FishChatReportColor`);
     * - `MainPhp`/`ParsedDressed` читают эти поля в runtime для логики проверки экипировки;
     * - `Prims.DEFAULT_ALL` используется как защитный fallback, если пользователь снял все приманки.
     */
    private void showAutoFishSettingsDialog() {
        if (AppVars.Profile == null) {
            Toast.makeText(context, "Профиль не загружен", Toast.LENGTH_SHORT).show();
            return;
        }

        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        CheckBox autoWear = new CheckBox(context);
        autoWear.setText("Автонадевание снастей");
        autoWear.setChecked(AppVars.Profile.FishAutoWear);
        root.addView(autoWear);

        TextView hand1Title = new TextView(context);
        hand1Title.setText("???? 1");
        hand1Title.setPadding(0, pad, 0, 0);
        root.addView(hand1Title);

        Spinner hand1Spinner = new Spinner(context);
        ArrayAdapter<String> handAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, FISH_HAND_OPTIONS);
        handAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hand1Spinner.setAdapter(handAdapter);
        int hand1Index = indexOfFishHand(AppVars.Profile.FishHandOne);
        hand1Spinner.setSelection(hand1Index >= 0 ? hand1Index : 1);
        root.addView(hand1Spinner);

        TextView hand2Title = new TextView(context);
        hand2Title.setText("???? 2");
        hand2Title.setPadding(0, pad, 0, 0);
        root.addView(hand2Title);

        Spinner hand2Spinner = new Spinner(context);
        hand2Spinner.setAdapter(handAdapter);
        int hand2Index = indexOfFishHand(AppVars.Profile.FishHandTwo);
        hand2Spinner.setSelection(hand2Index >= 0 ? hand2Index : 0);
        root.addView(hand2Spinner);

        TextView autoDrinkTitle = new TextView(context);
        autoDrinkTitle.setText("Автопитье");
        autoDrinkTitle.setPadding(0, pad, 0, 0);
        root.addView(autoDrinkTitle);

        LinearLayout tiedRow = new LinearLayout(context);
        tiedRow.setOrientation(LinearLayout.HORIZONTAL);
        tiedRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tiedLabel = new TextView(context);
        tiedLabel.setText("Глоток, если усталка больше");
        tiedRow.addView(tiedLabel);
        EditText tiedHighInput = new EditText(context);
        tiedHighInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        tiedHighInput.setText(String.valueOf(Math.max(0, Math.min(99, AppVars.Profile.FishTiedHigh))));
        LinearLayout.LayoutParams tiedParams = new LinearLayout.LayoutParams(
                (int) (context.getResources().getDisplayMetrics().density * 56),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tiedParams.leftMargin = (int) (context.getResources().getDisplayMetrics().density * 8);
        tiedHighInput.setLayoutParams(tiedParams);
        tiedRow.addView(tiedHighInput);
        root.addView(tiedRow);

        CheckBox tiedZero = new CheckBox(context);
        tiedZero.setText("Пить до нуля усталости");
        tiedZero.setChecked(AppVars.Profile.FishTiedZero);
        root.addView(tiedZero);

        CheckBox fishDrinkBliss = new CheckBox(context);
        fishDrinkBliss.setText("Пить Эликсир Блаженства, если усталка больше порога");
        fishDrinkBliss.setChecked(AppVars.Profile.FishDrinkBliss);
        root.addView(fishDrinkBliss);

        CheckBox stopOverWeight = new CheckBox(context);
        stopOverWeight.setText("Прекращать рыбалку при перегрузе");
        stopOverWeight.setChecked(AppVars.Profile.FishStopOverWeight);
        root.addView(stopOverWeight);

        CheckBox fishChatReport = new CheckBox(context);
        fishChatReport.setText("Выводить результаты лова в чат");
        fishChatReport.setChecked(AppVars.Profile.FishChatReport);
        root.addView(fishChatReport);

        CheckBox fishChatReportColor = new CheckBox(context);
        fishChatReportColor.setText("Выводить результаты лова в приват");
        fishChatReportColor.setChecked(AppVars.Profile.FishChatReportColor);
        root.addView(fishChatReportColor);

        TextView primsTitle = new TextView(context);
        primsTitle.setText("Приманки");
        primsTitle.setPadding(0, pad, 0, 0);
        root.addView(primsTitle);

        final CheckBox[] primChecks = new CheckBox[FISH_PRIM_FLAGS.length];
        for (int i = 0; i < FISH_PRIM_FLAGS.length; i++) {
            CheckBox cb = new CheckBox(context);
            cb.setText(FISH_PRIM_LABELS[i]);
            cb.setChecked((AppVars.Profile.FishEnabledPrims & FISH_PRIM_FLAGS[i]) != 0);
            primChecks[i] = cb;
            root.addView(cb);
        }

        new AlertDialog.Builder(context)
                .setTitle("Настройки авто-рыбалки")
                .setView(scroll)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    AppVars.Profile.FishAutoWear = autoWear.isChecked();
                    AppVars.Profile.FishHandOne = FISH_HAND_OPTIONS[Math.max(0, hand1Spinner.getSelectedItemPosition())];
                    AppVars.Profile.FishHandTwo = FISH_HAND_OPTIONS[Math.max(0, hand2Spinner.getSelectedItemPosition())];
                    int tiedHigh = AppVars.Profile.FishTiedHigh;
                    try {
                        String value = tiedHighInput.getText() == null ? "" : tiedHighInput.getText().toString().trim();
                        if (!value.isEmpty()) {
                            tiedHigh = Integer.parseInt(value);
                        }
                    } catch (Exception ignored) {
                    }
                    AppVars.Profile.FishTiedHigh = Math.max(0, Math.min(99, tiedHigh));
                    AppVars.Profile.FishTiedZero = tiedZero.isChecked();
                    AppVars.Profile.FishDrinkBliss = fishDrinkBliss.isChecked();
                    AppVars.Profile.FishStopOverWeight = stopOverWeight.isChecked();
                    AppVars.Profile.FishChatReport = fishChatReport.isChecked();
                    AppVars.Profile.FishChatReportColor = fishChatReportColor.isChecked();

                    int mask = 0;
                    for (int i = 0; i < FISH_PRIM_FLAGS.length; i++) {
                        if (primChecks[i].isChecked()) {
                            mask |= FISH_PRIM_FLAGS[i];
                        }
                    }
                    if (mask == 0) {
                        mask = Prims.DEFAULT_ALL;
                    }
                    AppVars.Profile.FishEnabledPrims = mask;
                    AppVars.Profile.save(context);
                    Toast.makeText(context, "Настройки авто-рыбалки сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    private void showFishHandSelector(boolean firstHand) {
        if (AppVars.Profile == null) {
            return;
        }
        String current = firstHand ? AppVars.Profile.FishHandOne : AppVars.Profile.FishHandTwo;
        int selectedIndex = indexOfFishHand(current);
        if (selectedIndex < 0) selectedIndex = 0;
        final int safeSelectedIndex = selectedIndex;
        String title = firstHand ? "Предмет в руке 1" : "Предмет в руке 2";

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(FISH_HAND_OPTIONS, safeSelectedIndex, (dialog, which) -> {
                    if (firstHand) {
                        AppVars.Profile.FishHandOne = FISH_HAND_OPTIONS[which];
                    } else {
                        AppVars.Profile.FishHandTwo = FISH_HAND_OPTIONS[which];
                    }
                    AppVars.Profile.save(context);
                    Toast.makeText(context, title + ": " + FISH_HAND_OPTIONS[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showAutoFishSettingsDialog();
                })
                .setNegativeButton("Отмена", (dialog, which) -> showAutoFishSettingsDialog())
                .show();
    }

    private void showFishPrimsSelector() {
        if (AppVars.Profile == null) {
            return;
        }
        boolean[] selected = new boolean[FISH_PRIM_FLAGS.length];
        for (int i = 0; i < FISH_PRIM_FLAGS.length; i++) {
            selected[i] = (AppVars.Profile.FishEnabledPrims & FISH_PRIM_FLAGS[i]) != 0;
        }

        new AlertDialog.Builder(context)
                .setTitle("Разрешенные приманки")
                .setMultiChoiceItems(FISH_PRIM_LABELS, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    int mask = 0;
                    for (int i = 0; i < FISH_PRIM_FLAGS.length; i++) {
                        if (selected[i]) {
                            mask |= FISH_PRIM_FLAGS[i];
                        }
                    }
                    // Защита от пустого набора: C# дефолт включает все приманки.
                    if (mask == 0) {
                        mask = Prims.DEFAULT_ALL;
                    }
                    AppVars.Profile.FishEnabledPrims = mask;
                    AppVars.Profile.save(context);
                    Toast.makeText(context, "Приманки обновлены", Toast.LENGTH_SHORT).show();
                    showAutoFishSettingsDialog();
                })
                .setNegativeButton("Отмена", (dialog, which) -> showAutoFishSettingsDialog())
                .show();
    }

    private int indexOfFishHand(String value) {
        if (value == null) return -1;
        for (int i = 0; i < FISH_HAND_OPTIONS.length; i++) {
            if (FISH_HAND_OPTIONS[i].equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    private String safeFishHand(String value) {
        if (value == null || value.isEmpty()) return "Нет";
        return value;
    }

    private String describeFishPrims(int mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FISH_PRIM_FLAGS.length; i++) {
            if ((mask & FISH_PRIM_FLAGS[i]) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(FISH_PRIM_LABELS[i]);
            }
        }
        return sb.length() > 0 ? sb.toString() : "нет";
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

            CheckBox resetAtMidnight = new CheckBox(context);
            resetAtMidnight.setText("Сбрасывать статистику в полночь");
            resetAtMidnight.setChecked(AppVars.Profile != null && AppVars.Profile.StatsResetAtMidnight);
            LinearLayout.LayoutParams resetAtMidnightParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            resetAtMidnightParams.topMargin = dpToPx(8);
            root.addView(resetAtMidnight, resetAtMidnightParams);

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

            resetAtMidnight.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (AppVars.Profile != null && AppVars.Profile.StatsResetAtMidnight != isChecked) {
                    AppVars.Profile.StatsResetAtMidnight = isChecked;
                    AppVars.Profile.save(context);
                }
                // Перерисовываем окно сразу: если включили флаг и день уже сменился — статистика сбросится.
                statsText.setText(buildStatsText());
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
        // Legacy fallback-блок отображения статистики (оставлен для совместимости).
        // Важно: даже в этой ветке используем новую агрегированную модель предметов (шт.),
        // чтобы формат окна совпадал с основным `buildStatsText()`.
        long xp = ru.neverlands.abclient.utils.ChatStats.getTotalXp();
        java.util.Map<String, Long> items = ru.neverlands.abclient.utils.ChatStats.getItemCountByName();
        String logPath = ru.neverlands.abclient.utils.Chat.getCurrentLogPath();

        StringBuilder sb = new StringBuilder();
        sb.append("Опыт: ").append(xp).append("\n");
        sb.append("Предметов: ").append(items.size()).append("\n\n");

        if (!items.isEmpty()) {
            sb.append("Предметы (шт.):\n");
            for (java.util.Map.Entry<String, Long> entry : items.entrySet()) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" шт.\n");
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

    /**
     * Формирует текст для всплывающего окна "Статистика".
     *
     * Состав:
     * - Опыт (`ChatStats.getTotalXp()`),
     * - Поединки (`ChatStats.getTotalFights()`),
     * - Денежные средства (`ChatStats.getTotalNv()`),
     * - Доход рыбалки (`ChatStats.getTotalFishNv()`) и рыба по типам (`ChatStats.getFishCountByType()`),
     * - Ресурсы (`ChatStats.getTotalResourceKg()` и `ChatStats.getResourceKgByType()`),
     * - Предметы по названиям (`ChatStats.getItemCountByName()`), в формате "Название: N шт.".
     *
     * Зависимости:
     * - `ChatStats` хранит и восстанавливает статистику профиля из `Logs/<profile>_stat.txt`;
     * - порядок предметов/ресурсов определяется порядком накопления в `LinkedHashMap` внутри `ChatStats`;
     * - значение этого метода используется и для текста окна, и для копирования в буфер.
     */
    private String buildStatsText() {
        long xp = ChatStats.getTotalXp();
        long fights = ChatStats.getTotalFights();
        long totalNv = ChatStats.getTotalNv();
        long statsElapsedMs = ChatStats.getStatsElapsedMs();
        double totalFishNv = ChatStats.getTotalFishNv();
        double totalResourcesKg = ChatStats.getTotalResourceKg();
        java.util.Map<String, Double> resourceKgByType = ChatStats.getResourceKgByType();
        java.util.Map<String, Long> fishCountByType = ChatStats.getFishCountByType();
        java.util.Map<String, Long> itemCountByName = ChatStats.getItemCountByName();

        StringBuilder sb = new StringBuilder();
        sb.append("Статистика за: ").append(formatStatsDuration(statsElapsedMs)).append("\n");
        sb.append("Опыт: ").append(xp).append("\n");
        sb.append("Поединки: ").append(fights).append("\n");
        sb.append("Денежные средства (NV): ").append(totalNv).append("\n");
        sb.append("Доход рыбалки: ").append(formatNv(totalFishNv)).append(" NV\n");

        if (!fishCountByType.isEmpty()) {
            sb.append("Рыба (шт.):\n");
            for (java.util.Map.Entry<String, Long> entry : fishCountByType.entrySet()) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" шт.\n");
            }
            sb.append("\n");
        }

        sb.append("Ресурсы (кг): ").append(formatKg(totalResourcesKg)).append("\n\n");

        if (!resourceKgByType.isEmpty()) {
            sb.append("Ресурсы по типам:\n");
            for (java.util.Map.Entry<String, Double> entry : resourceKgByType.entrySet()) {
                sb.append("• ").append(entry.getKey()).append(": ").append(formatKg(entry.getValue())).append(" кг\n");
            }
            sb.append("\n");
        }

        if (!itemCountByName.isEmpty()) {
            sb.append("Предметы (шт.):\n");
            for (java.util.Map.Entry<String, Long> entry : itemCountByName.entrySet()) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" шт.\n");
            }
        }

        return sb.toString().trim();
    }

    // Формат длительности окна статистики в виде hh:mm:ss.
    // Зависимости: используется только buildStatsText() для строки "Статистика за: ...".
    private String formatStatsDuration(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    // Формат NV-значений (доход/потери) с двумя знаками после запятой.
    private String formatNv(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    // Формат массы ресурсов для статистики (всегда 2 знака после запятой).
    // Зависимость: вызывается только из buildStatsText() для строк ресурсов в кг.
    private String formatKg(double kilograms) {
        return String.format(java.util.Locale.US, "%.2f", kilograms);
    }

    // Утилита для перевода dp -> px.
    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Диалог навигатора — порт FormNavigator.cs.
     *
     * Поведение:
     * - Если AutoMoving уже включён → предлагает остановить навигацию.
     * - Если выключен → открывает выбор пункта назначения:
     *   список Избранных, стандартных локаций, телепортов;
     *   поле ввода ячейки вручную; чекбокс "Разрешить телепорты";
     *   кнопки «Добавить в избранное» и «Очистить избранное».
     *
     * Зависимости:
     * - {@link AppVars#AutoMoving}, {@link AppVars#AutoMovingDestinaton} — текущее состояние навигации;
     * - {@link AppVars#Profile#MapLocation} — текущая позиция игрока;
     * - {@link AppVars#Profile#NavigatorAllowTeleports} — флаг разрешения телепортов;
     * - {@link AppVars#Profile#FavLocations} — массив избранных ячеек;
     * - {@link ExtMap#Cells} — справочник ячеек (название/tooltip) из extmap.txt;
     * - {@link ExtMap#Teleports} — справочник телепортов из extmap.txt;
     * - {@link AutoFunctionsManager#startAutoMoving(String)} — запуск навигации;
     * - {@link AutoFunctionsManager#stopAutoMoving()} — остановка навигации.
     */
    private void showNavigatorDialog() {
        if (AppVars.AutoMoving) {
            String dest = AppVars.AutoMovingDestinaton != null ? AppVars.AutoMovingDestinaton : "?";
            new AlertDialog.Builder(context)
                    .setTitle("Навигатор")
                    .setMessage("Навигатор активен. Пункт назначения: " + dest + "\nОстановить?")
                    .setPositiveButton("Остановить", (d, w) -> {
                        autoFunctionsManager.stopAutoMoving();
                        Toast.makeText(context, "Навигатор остановлен", Toast.LENGTH_SHORT).show();
                        loadAndUpdateButtons();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }

        int pad = dpToPx(16);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        String currentLoc = (AppVars.Profile != null
                && AppVars.Profile.MapLocation != null
                && !AppVars.Profile.MapLocation.isEmpty())
                ? AppVars.Profile.MapLocation : "неизвестно";

        TextView locLabel = new TextView(context);
        locLabel.setText("Текущая позиция: " + currentLoc);
        locLabel.setTextColor(0xFF666666);
        root.addView(locLabel);

        android.widget.AutoCompleteTextView destInput = new android.widget.AutoCompleteTextView(context);
        destInput.setHint("Номер клетки (напр. 8-259)");
        destInput.setSingleLine();
        destInput.setThreshold(1);
        destInput.setAdapter(new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                buildNavigatorAutocompleteValues()
        ));
        root.addView(destInput);

        CheckBox allowTeleCb = new CheckBox(context);
        allowTeleCb.setText("Разрешить телепорты");
        allowTeleCb.setChecked(AppVars.Profile == null || AppVars.Profile.NavigatorAllowTeleports);
        root.addView(allowTeleCb);

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(260)));
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, dpToPx(4), 0, dpToPx(8));
        renderNavigatorCategories(listLayout, destInput);
        scroll.addView(listLayout);
        root.addView(scroll);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Навигатор")
                .setView(root)
                .setPositiveButton("Начать", null)
                .setNegativeButton("Отмена", null)
                .create();

        allowTeleCb.setOnCheckedChangeListener((cb, checked) -> {
            if (AppVars.Profile != null) {
                AppVars.Profile.NavigatorAllowTeleports = checked;
                AppVars.Profile.save(context);
            }
        });

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cell = resolveCellNumber(destInput.getText().toString());
            if (cell.isEmpty()) {
                Toast.makeText(context, "Введите пункт назначения", Toast.LENGTH_SHORT).show();
                return;
            }
            autoFunctionsManager.startAutoMoving(cell);
            Toast.makeText(context, "Навигатор запущен → " + cell, Toast.LENGTH_SHORT).show();
            loadAndUpdateButtons();
            dialog.dismiss();
        }));

        dialog.show();
    }

    /**
     * Полная перерисовка дерева категорий навигатора в диалоге.
     *
     * Поведение:
     * - очищает контейнер;
     * - рисует фиксированные разделы (Избранное, Города, Объекты, Телепорты);
     * - для "Города" дополнительно рендерит динамические подкатегории профиля.
     *
     * Зависимости:
     * - данные профиля: AppVars.Profile + UserConfig.*;
     * - источники карт: ExtMap.Cells / ExtMap.Teleports;
     * - UI-коллбеки add/remove вызывают повторный renderNavigatorCategories(...).
     */
    private void renderNavigatorCategories(LinearLayout parent, EditText destInput) {
        parent.removeAllViews();
        addNavigatorLeafCategory(parent, "Избранное", NAV_CATEGORY_FAVORITES, navFavCells(), 0, destInput);

        addNavigatorSectionHeader(parent, "Города", 0,
                () -> showAddCitySubcategoryDialog(() -> renderNavigatorCategories(parent, destInput)));
        for (CitySubcategory citySubcategory : getCitySubcategories()) {
            addNavigatorCityLeafCategory(parent, citySubcategory, dpToPx(10), destInput);
        }

        addNavigatorLeafCategory(parent, "Объекты", NAV_CATEGORY_OBJECTS, navObjectCells(), 0, destInput);
        addNavigatorLeafCategory(parent, "Телепорты", NAV_CATEGORY_TELEPORTS, navTelepCells(), 0, destInput);
    }

    /**
     * Рисует заголовок секции (например, "Города") с кнопкой "+" для добавления подкатегории.
     *
     * Зависимости:
     * - визуальный стиль: R.color.ab_autoboi_group_selected_bg / ab_autoboi_text_primary;
     * - onAddSubcategory запускает диалог создания подкатегории и затем ререндер.
     */
    private void addNavigatorSectionHeader(LinearLayout parent, String title, int leftPaddingPx, Runnable onAddSubcategory) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);

        if (onAddSubcategory != null) {
            ImageButton addBtn = buildNavigatorIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
            addBtn.setContentDescription("add_subcategory_" + title);
            addBtn.setOnClickListener(v -> onAddSubcategory.run());
            headerRow.addView(addBtn);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dpToPx(6);
        parent.addView(headerRow, lp);
    }

    /**
     * Рисует обычную (не динамическую) категорию:
     * - заголовок с кнопкой "+" для добавления клетки;
     * - список клеток с кнопками "-" удаления.
     *
     * Зависимости:
     * - categoryId определяет маршрут сохранения через applyCategoryCells(...);
     * - showNavigatorAddCellDialog(...) валидирует клетку по map.xml;
     * - renderNavigatorCells(...) обеспечивает единый рендер ячеек.
     */
    private void addNavigatorLeafCategory(
            LinearLayout parent,
            String title,
            int categoryId,
            String[] cells,
            int leftPaddingPx,
            EditText destInput
    ) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerLp.topMargin = dpToPx(6);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));

        ImageButton addBtn = buildNavigatorIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
        addBtn.setContentDescription("add_cell_" + categoryId);
        addBtn.setOnClickListener(v ->
                showNavigatorAddCellDialog(title, cell -> addCellToCategory(categoryId, cell), () -> renderNavigatorCategories(parent, destInput)));

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);
        headerRow.addView(addBtn);
        parent.addView(headerRow, headerLp);

        renderNavigatorCells(parent, leftPaddingPx, sortAndNormalizeCells(cells), destInput, cellNum -> {
            removeCellFromCategory(categoryId, cellNum);
            renderNavigatorCategories(parent, destInput);
        });
    }

    /**
     * Рисует динамическую подкатегорию внутри секции "Города".
     *
     * Отличия от обычной категории:
     * - имеет кнопку "+" (добавить клетку в эту подкатегорию);
     * - имеет кнопку удаления самой подкатегории;
     * - изменяет данные через addCellToCitySubcategory/removeCitySubcategory(...).
     */
    private void addNavigatorCityLeafCategory(
            LinearLayout parent,
            CitySubcategory subcategory,
            int leftPaddingPx,
            EditText destInput
    ) {
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(ContextCompat.getColor(context, R.color.ab_autoboi_group_selected_bg));
        headerRow.setPadding(leftPaddingPx + dpToPx(10), dpToPx(8), dpToPx(6), dpToPx(8));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerLp.topMargin = dpToPx(6);

        TextView titleView = new TextView(context);
        titleView.setText(subcategory.name);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.ab_autoboi_text_primary));

        ImageButton addBtn = buildNavigatorIconButton(android.R.drawable.ic_input_add, 0xFF2E7D32);
        addBtn.setContentDescription("add_cell_city_" + subcategory.name);
        addBtn.setOnClickListener(v ->
                showNavigatorAddCellDialog(subcategory.name, cell -> addCellToCitySubcategory(subcategory.name, cell), () -> renderNavigatorCategories(parent, destInput)));

        ImageButton removeSubcategoryBtn = buildNavigatorIconButton(android.R.drawable.ic_delete, 0xFFC62828);
        removeSubcategoryBtn.setContentDescription("remove_subcategory_city_" + subcategory.name);
        removeSubcategoryBtn.setOnClickListener(v -> {
            removeCitySubcategory(subcategory.name);
            renderNavigatorCategories(parent, destInput);
            Toast.makeText(context, "Удалена подкатегория: " + subcategory.name, Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleView, titleLp);
        headerRow.addView(addBtn);
        headerRow.addView(removeSubcategoryBtn);
        parent.addView(headerRow, headerLp);

        renderNavigatorCells(parent, leftPaddingPx, sortAndNormalizeCells(subcategory.cells), destInput, cellNum -> {
            removeCellFromCitySubcategory(subcategory.name, cellNum);
            renderNavigatorCategories(parent, destInput);
        });
    }

    /**
     * Общий рендер списка клеток для категории/подкатегории.
     *
     * Зависимости:
     * - buildNavigatorCellLabel(...) формирует отображение из ExtMap;
     * - OnNavigatorCellPicked onRemoveCell инкапсулирует конкретную стратегию удаления;
     * - destInput синхронизируется кликом по строке клетки.
     */
    private void renderNavigatorCells(
            LinearLayout parent,
            int leftPaddingPx,
            String[] cells,
            EditText destInput,
            OnNavigatorCellPicked onRemoveCell
    ) {
        if (cells == null || cells.length == 0) {
            TextView empty = new TextView(context);
            empty.setText("Пусто");
            empty.setTextColor(0xFF777777);
            empty.setPadding(leftPaddingPx + dpToPx(14), dpToPx(4), dpToPx(10), dpToPx(4));
            parent.addView(empty);
            return;
        }

        for (String cellNum : cells) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(leftPaddingPx + dpToPx(14), dpToPx(4), dpToPx(2), dpToPx(4));

            TextView item = new TextView(context);
            item.setText(buildNavigatorCellLabel(cellNum));
            item.setTextColor(0xFF0000AA);
            item.setOnClickListener(v -> destInput.setText(cellNum));

            ImageButton removeBtn = buildNavigatorIconButton(android.R.drawable.ic_delete, 0xFFC62828);
            removeBtn.setContentDescription("remove_cell_" + cellNum);
            removeBtn.setOnClickListener(v -> {
                onRemoveCell.onCellPicked(cellNum);
                Toast.makeText(context, "Удалено: " + cellNum, Toast.LENGTH_SHORT).show();
            });

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(item, itemLp);
            row.addView(removeBtn);
            parent.addView(row);
        }
    }

    /**
     * Создает стандартизированную иконку-кнопку для действий в навигаторе.
     *
     * Зависимости:
     * - используется для кнопок "+" и "-" на заголовках/строках;
     * - единая точка настройки отступов и прозрачного фона.
     */
    private ImageButton buildNavigatorIconButton(int drawableRes, int tintColor) {
        ImageButton btn = new ImageButton(context);
        btn.setImageResource(drawableRes);
        btn.setColorFilter(tintColor);
        btn.setBackgroundResource(android.R.color.transparent);
        btn.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        return btn;
    }

    /**
     * Диалог добавления клетки в выбранную категорию/подкатегорию.
     *
     * Гарантии:
     * - парсит и нормализует ввод через resolveCellNumber(...);
     * - принимает только клетки, существующие в ExtMap.Cells (данные map.xml);
     * - после успешного добавления вызывает onChanged для обновления UI.
     *
     * Зависимости:
     * - onCellPicked реализует место назначения (категория или подкатегория);
     * - buildNavigatorAutocompleteValues() дает подсказки полного формата.
     */
    private void showNavigatorAddCellDialog(String categoryTitle, OnNavigatorCellPicked onCellPicked, Runnable onChanged) {
        final android.widget.AutoCompleteTextView input = new android.widget.AutoCompleteTextView(context);
        input.setHint("Клетка (напр. 8-259)");
        input.setSingleLine();
        input.setThreshold(1);
        input.setAdapter(new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                buildNavigatorAutocompleteValues()
        ));

        new AlertDialog.Builder(context)
                .setTitle("Добавить в: " + categoryTitle)
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> {
                    String cell = resolveCellNumber(input.getText().toString());
                    if (cell.isEmpty()) {
                        Toast.makeText(context, "Неверный формат клетки", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (ExtMap.Cells == null || !ExtMap.Cells.containsKey(cell)) {
                        Toast.makeText(context, "Клетка не найдена в map.xml", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onCellPicked.onCellPicked(cell);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    Toast.makeText(context, "Добавлено: " + cell, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * Диалог создания новой подкатегории в секции "Города".
     *
     * Зависимости:
     * - sanitizeCitySubcategoryName(...) очищает имя;
     * - addCitySubcategory(...) проверяет уникальность и сохраняет в профиль;
     * - onChanged инициирует повторный рендер дерева категорий.
     */
    private void showAddCitySubcategoryDialog(Runnable onChanged) {
        final EditText input = new EditText(context);
        input.setHint("Название подкатегории");
        input.setSingleLine();

        new AlertDialog.Builder(context)
                .setTitle("Новая подкатегория (Города)")
                .setView(input)
                .setPositiveButton("Создать", (d, w) -> {
                    String name = sanitizeCitySubcategoryName(input.getText().toString());
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Введите название подкатегории", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!addCitySubcategory(name)) {
                        Toast.makeText(context, "Подкатегория уже существует", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    Toast.makeText(context, "Создана подкатегория: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    /**
     * Формирует элементы автодополнения для ввода клетки.
     *
     * Формат элемента:
     * - "<cellNum> - <name/tooltip>" (через buildNavigatorCellLabel).
     *
     * Зависимости:
     * - ExtMap.Cells должен быть инициализирован;
     * - сортировка по ключу клетки для стабильного порядка в UI.
     */
    private String[] buildNavigatorAutocompleteValues() {
        if (ExtMap.Cells == null || ExtMap.Cells.isEmpty()) {
            return new String[0];
        }
        ArrayList<String> values = new ArrayList<>();
        ArrayList<String> keys = new ArrayList<>(ExtMap.Cells.keySet());
        java.util.Collections.sort(keys);
        for (String cellNum : keys) {
            values.add(buildNavigatorCellLabel(cellNum));
        }
        return values.toArray(new String[0]);
    }

    /**
     * Строит человекочитаемую подпись клетки из map.xml/extmap:
     * - номер клетки;
     * - name/tooltip;
     * - fallback на название телепорта из ExtMap.Teleports.
     */
    private String buildNavigatorCellLabel(String cellNum) {
        if (cellNum == null || cellNum.trim().isEmpty()) {
            return "";
        }
        String normalized = cellNum.trim();
        Cell cell = ExtMap.Cells != null ? ExtMap.Cells.get(normalized) : null;
        String name = cell != null && cell.Name != null ? cell.Name.trim() : "";
        String tooltip = cell != null && cell.Tooltip != null ? cell.Tooltip.trim() : "";
        if (name.isEmpty() && ExtMap.Teleports != null && ExtMap.Teleports.containsKey(normalized)) {
            name = ExtMap.Teleports.get(normalized);
        }
        if (name.isEmpty() && tooltip.isEmpty()) {
            return normalized;
        }
        if (tooltip.isEmpty()) {
            return normalized + " — " + name;
        }
        if (name.isEmpty() || name.equalsIgnoreCase(tooltip)) {
            return normalized + " — " + tooltip;
        }
        return normalized + " — " + name + " (" + tooltip + ")";
    }

    /**
     * Извлекает canonical-номер клетки "N-NNN" из произвольной строки.
     *
     * Примеры поддерживаемого ввода:
     * - "8-259"
     * - "8-259 - Форпост"
     * - "Клетка 8-259 (что угодно)".
     */
    private String resolveCellNumber(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String raw = rawValue.trim();
        if (raw.isEmpty()) {
            return "";
        }
        Matcher matcher = NAV_CELL_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Нормализует массив клеток:
     * - фильтрует невалидные значения;
     * - удаляет дубликаты;
     * - сортирует по строковому номеру клетки.
     */
    private String[] sortAndNormalizeCells(String[] input) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (input != null) {
            for (String item : input) {
                String cell = resolveCellNumber(item);
                if (!cell.isEmpty()) {
                    set.add(cell);
                }
            }
        }
        String[] result = set.toArray(new String[0]);
        Arrays.sort(result);
        return result;
    }

    /**
     * Добавляет клетку в фиксированную категорию и сохраняет профиль.
     *
     * Зависимости:
     * - getCategoryCells(...) / applyCategoryCells(...);
     * - UserConfig.save(context) для персистентности.
     */
    private void addCellToCategory(int categoryId, String cellNum) {
        if (AppVars.Profile == null) {
            return;
        }
        String[] updated = appendCellToArray(getCategoryCells(categoryId), cellNum);
        applyCategoryCells(categoryId, updated);
        AppVars.Profile.save(context);
    }

    /**
     * Удаляет клетку из фиксированной категории и сохраняет профиль.
     */
    private void removeCellFromCategory(int categoryId, String cellNum) {
        if (AppVars.Profile == null) {
            return;
        }
        String[] updated = removeCellFromArray(getCategoryCells(categoryId), cellNum);
        applyCategoryCells(categoryId, updated);
        AppVars.Profile.save(context);
    }

    /**
     * Добавляет клетку в массив без дублей (set-like поведение).
     */
    private String[] appendCellToArray(String[] source, String cellNum) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (source != null) {
            for (String item : source) {
                String cell = resolveCellNumber(item);
                if (!cell.isEmpty()) {
                    set.add(cell);
                }
            }
        }
        String normalized = resolveCellNumber(cellNum);
        if (!normalized.isEmpty()) {
            set.add(normalized);
        }
        return set.toArray(new String[0]);
    }

    /**
     * Удаляет конкретную клетку из массива.
     */
    private String[] removeCellFromArray(String[] source, String cellNum) {
        String normalized = resolveCellNumber(cellNum);
        ArrayList<String> result = new ArrayList<>();
        if (source != null) {
            for (String item : source) {
                String cell = resolveCellNumber(item);
                if (!cell.isEmpty() && !cell.equals(normalized)) {
                    result.add(cell);
                }
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * Возвращает текущий список клеток фиксированной категории.
     *
     * Примечание:
     * - динамические подкатегории Города обрабатываются отдельно.
     */
    private String[] getCategoryCells(int categoryId) {
        if (AppVars.Profile == null) {
            return new String[0];
        }
        switch (categoryId) {
            case NAV_CATEGORY_FAVORITES:
                return navFavCells();
            case NAV_CATEGORY_CITY_VILLAGE:
                return navCityVillageCells();
            case NAV_CATEGORY_CITY_FORPOST:
                return navCityForpostCells();
            case NAV_CATEGORY_CITY_OKTAL:
                return navCityOktalCells();
            case NAV_CATEGORY_OBJECTS:
                return navObjectCells();
            case NAV_CATEGORY_TELEPORTS:
                return navTelepCells();
            default:
                return new String[0];
        }
    }

    /**
     * Применяет новый список клеток к фиксированной категории профиля.
     *
     * Зависимости:
     * - UserConfig setter-методы (внутри нормализация формата).
     */
    private void applyCategoryCells(int categoryId, String[] cells) {
        if (AppVars.Profile == null) {
            return;
        }
        switch (categoryId) {
            case NAV_CATEGORY_FAVORITES:
                AppVars.Profile.FavLocations = sortAndNormalizeCells(cells);
                break;
            case NAV_CATEGORY_CITY_VILLAGE:
                AppVars.Profile.setNavCityVillageLocations(cells);
                break;
            case NAV_CATEGORY_CITY_FORPOST:
                AppVars.Profile.setNavCityForpostLocations(cells);
                break;
            case NAV_CATEGORY_CITY_OKTAL:
                AppVars.Profile.setNavCityOktalLocations(cells);
                break;
            case NAV_CATEGORY_OBJECTS:
                AppVars.Profile.setNavObjectLocations(cells);
                break;
            case NAV_CATEGORY_TELEPORTS:
                AppVars.Profile.setNavTeleportLocations(cells);
                break;
            default:
                break;
        }
    }

    /**
     * Возвращает нормализованный список Избранного.
     */
    private String[] navFavCells() {
        if (AppVars.Profile == null || AppVars.Profile.FavLocations == null) return new String[0];
        return sortAndNormalizeCells(AppVars.Profile.FavLocations);
    }

    /**
     * Возвращает нормализованный список клеток Деревни.
     */
    private String[] navCityVillageCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityVillageLocations == null) return new String[] {"8-197"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityVillageLocations);
    }

    /**
     * Возвращает нормализованный список клеток Форпоста.
     */
    private String[] navCityForpostCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityForpostLocations == null) return new String[] {"8-259", "8-294"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityForpostLocations);
    }

    /**
     * Возвращает нормализованный список клеток Октала.
     */
    private String[] navCityOktalCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavCityOktalLocations == null) return new String[] {"12-428", "12-494", "12-521"};
        return sortAndNormalizeCells(AppVars.Profile.NavCityOktalLocations);
    }

    /**
     * Возвращает нормализованный список клеток Объектов.
     */
    private String[] navObjectCells() {
        if (AppVars.Profile == null || AppVars.Profile.NavObjectLocations == null) return new String[] {"8-227", "2-482", "9-494", "26-430"};
        return sortAndNormalizeCells(AppVars.Profile.NavObjectLocations);
    }

    /**
     * Возвращает список Телепортов:
     * - сначала пользовательский (если сохранен в профиле),
     * - иначе дефолт из ExtMap.Teleports.
     */
    private String[] navTelepCells() {
        if (AppVars.Profile != null
                && AppVars.Profile.NavTeleportLocations != null
                && AppVars.Profile.NavTeleportLocations.length > 0) {
            return sortAndNormalizeCells(AppVars.Profile.NavTeleportLocations);
        }
        if (ExtMap.Teleports == null || ExtMap.Teleports.isEmpty()) return new String[0];
        String[] keys = ExtMap.Teleports.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        return keys;
    }

    /**
     * Возвращает список динамических подкатегорий Города.
     *
     * Источник:
     * - UserConfig.NavCitySubcategories;
     * - fallback на дефолтные подкатегории, если строка пуста/битая.
     */
    private List<CitySubcategory> getCitySubcategories() {
        if (AppVars.Profile == null) {
            return buildDefaultCitySubcategories();
        }
        List<CitySubcategory> parsed = parseCitySubcategories(AppVars.Profile.NavCitySubcategories);
        if (parsed.isEmpty()) {
            return buildDefaultCitySubcategories();
        }
        return parsed;
    }

    /**
     * Формирует дефолтные подкатегории Города из legacy-списков профиля.
     */
    private List<CitySubcategory> buildDefaultCitySubcategories() {
        ArrayList<CitySubcategory> defaults = new ArrayList<>();
        defaults.add(new CitySubcategory("Деревня", navCityVillageCells()));
        defaults.add(new CitySubcategory("Форпост", navCityForpostCells()));
        defaults.add(new CitySubcategory("Октал", navCityOktalCells()));
        return defaults;
    }

    /**
     * Парсит строку профиля citysubcategories в список объектов подкатегорий.
     *
     * Формат строки:
     * - "Имя|8-259,8-294;Имя2|12-428".
     */
    private List<CitySubcategory> parseCitySubcategories(String raw) {
        ArrayList<CitySubcategory> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        String[] entries = raw.split(Pattern.quote(NAV_CITY_SUBCATEGORY_ENTRY_DELIMITER));
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String[] pair = entry.split(Pattern.quote(NAV_CITY_SUBCATEGORY_VALUE_DELIMITER), 2);
            String name = sanitizeCitySubcategoryName(pair[0]);
            if (name.isEmpty()) {
                continue;
            }
            String[] cells = new String[0];
            if (pair.length > 1 && pair[1] != null && !pair[1].trim().isEmpty()) {
                cells = sortAndNormalizeCells(pair[1].split(","));
            }
            result.add(new CitySubcategory(name, cells));
        }
        return result;
    }

    /**
     * Сериализует список подкатегорий Города в строку профиля.
     *
     * Зависимости:
     * - sanitizeCitySubcategoryName(...) и sortAndNormalizeCells(...),
     *   чтобы в файл профиля попадали только валидные данные.
     */
    private String encodeCitySubcategories(List<CitySubcategory> subcategories) {
        if (subcategories == null || subcategories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CitySubcategory subcategory : subcategories) {
            if (subcategory == null) {
                continue;
            }
            String name = sanitizeCitySubcategoryName(subcategory.name);
            if (name.isEmpty()) {
                continue;
            }
            String[] cells = sortAndNormalizeCells(subcategory.cells);
            if (sb.length() > 0) {
                sb.append(NAV_CITY_SUBCATEGORY_ENTRY_DELIMITER);
            }
            sb.append(name).append(NAV_CITY_SUBCATEGORY_VALUE_DELIMITER);
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cells[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Очищает имя подкатегории от служебных разделителей формата сериализации.
     */
    private String sanitizeCitySubcategoryName(String name) {
        if (name == null) {
            return "";
        }
        String sanitized = name
                .replace(NAV_CITY_SUBCATEGORY_ENTRY_DELIMITER, " ")
                .replace(NAV_CITY_SUBCATEGORY_VALUE_DELIMITER, " ")
                .replace(",", " ")
                .trim();
        while (sanitized.contains("  ")) {
            sanitized = sanitized.replace("  ", " ");
        }
        return sanitized;
    }

    /**
     * Ищет индекс подкатегории по имени (без учета регистра).
     */
    private int findCitySubcategoryIndex(List<CitySubcategory> subcategories, String name) {
        if (subcategories == null || subcategories.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < subcategories.size(); i++) {
            CitySubcategory subcategory = subcategories.get(i);
            if (subcategory != null && subcategory.name != null
                    && subcategory.name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Добавляет новую подкатегорию Города (если еще не существует).
     *
     * Возврат:
     * - true: создано;
     * - false: некорректное имя/дубликат/нет профиля.
     */
    private boolean addCitySubcategory(String name) {
        if (AppVars.Profile == null) {
            return false;
        }
        String normalizedName = sanitizeCitySubcategoryName(name);
        if (normalizedName.isEmpty()) {
            return false;
        }
        List<CitySubcategory> subcategories = getCitySubcategories();
        if (findCitySubcategoryIndex(subcategories, normalizedName) >= 0) {
            return false;
        }
        subcategories.add(new CitySubcategory(normalizedName, new String[0]));
        saveCitySubcategories(subcategories);
        return true;
    }

    /**
     * Удаляет подкатегорию Города по имени.
     */
    private void removeCitySubcategory(String name) {
        if (AppVars.Profile == null) {
            return;
        }
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, name);
        if (index < 0) {
            return;
        }
        subcategories.remove(index);
        saveCitySubcategories(subcategories);
    }

    /**
     * Добавляет клетку в выбранную подкатегорию Города.
     */
    private void addCellToCitySubcategory(String subcategoryName, String cellNum) {
        if (AppVars.Profile == null) {
            return;
        }
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, subcategoryName);
        if (index < 0) {
            return;
        }
        CitySubcategory existing = subcategories.get(index);
        subcategories.set(index, new CitySubcategory(existing.name, appendCellToArray(existing.cells, cellNum)));
        saveCitySubcategories(subcategories);
    }

    /**
     * Удаляет клетку из выбранной подкатегории Города.
     */
    private void removeCellFromCitySubcategory(String subcategoryName, String cellNum) {
        if (AppVars.Profile == null) {
            return;
        }
        List<CitySubcategory> subcategories = getCitySubcategories();
        int index = findCitySubcategoryIndex(subcategories, subcategoryName);
        if (index < 0) {
            return;
        }
        CitySubcategory existing = subcategories.get(index);
        subcategories.set(index, new CitySubcategory(existing.name, removeCellFromArray(existing.cells, cellNum)));
        saveCitySubcategories(subcategories);
    }

    /**
     * Сохраняет динамические подкатегории Города в профиль и на диск.
     *
     * Зависимости:
     * - UserConfig.NavCitySubcategories (строковая сериализация),
     * - UserConfig.save(context) для мгновенной персистентности.
     */
    private void saveCitySubcategories(List<CitySubcategory> subcategories) {
        if (AppVars.Profile == null) {
            return;
        }
        AppVars.Profile.NavCitySubcategories = encodeCitySubcategories(subcategories);
        AppVars.Profile.save(context);
    }

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
