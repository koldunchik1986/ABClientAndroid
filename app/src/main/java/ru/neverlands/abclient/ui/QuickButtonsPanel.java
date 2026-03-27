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
import android.widget.Button;
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
    private static final String[] TREASURE_SHOVEL_LABELS = new String[] {
            "Не переодевать лопату",
            "Любая лопата",
            "Лопата кладоискателя",
            "Походная лопатка",
            "Лопата археолога"
    };
    private static final String[] TREASURE_SHOVEL_VALUES = new String[] {
            AutoFunctionsManager.TREASURE_SHOVEL_NONE,
            AutoFunctionsManager.TREASURE_SHOVEL_ANY,
            AutoFunctionsManager.TREASURE_SHOVEL_SEEKER,
            AutoFunctionsManager.TREASURE_SHOVEL_TRAVEL,
            AutoFunctionsManager.TREASURE_SHOVEL_ARCHAEOLOGIST
    };
    
    private final Context context;
    private final QuickButtonsManager buttonsManager;
    private final AutoFunctionsManager autoFunctionsManager;
    private final Navigator navigator;
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
        this.navigator = new Navigator(context, this.autoFunctionsManager, this::loadAndUpdateButtons);
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

    // Принудительное обновление визуального состояния быстрых кнопок по runtime-флагам автофункций.
    public void refreshActionStates() {
        loadAndUpdateButtons();
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
            int fallbackIcon = getIconForAction(type, isEnabled);
            Glide.with(context)
                .load(iconUrl)
                .placeholder(fallbackIcon)
                .error(fallbackIcon)
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
            case AUTO_COMPASS:
                return null;
            case AUTO_BOSS:
                return "http://image.neverlands.ru/weapon/i_w28_27.gif";
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
            case AUTO_TREASURE:
                return "http://image.neverlands.ru/achievement/9/a_9_10.gif";
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
            case AUTO_COMPASS:
            case AUTO_BOSS:
            case AUTO_INVISIBLE:
            case LOCATION_TRACKING:
            case AUTO_DETECT:
            case AUTO_SUMMON:
            case AUTO_CURE:
            case AUTO_DRINK:
            case AUTO_MOVING:
            case AUTO_TREASURE:
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
            case AUTO_COMPASS:
                return R.drawable.ic_compas;
            case AUTO_BOSS:
                return R.drawable.ic_compas;
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
            case AUTO_TREASURE:
                return R.drawable.ic_auto_detect;
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
            case AUTO_COMPASS:
                autoFunctionsManager.toggleAutoCompass();
                Toast.makeText(context, autoFunctionsManager.isAutoCompassEnabled() ? "Авто-Компас ВКЛ" : "Авто-Компас ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
                break;
            case AUTO_BOSS:
                autoFunctionsManager.toggleAutoBoss();
                Toast.makeText(context, autoFunctionsManager.isAutoBossEnabled() ? "Авто-Боссы ВКЛ" : "Авто-Боссы ВЫКЛ", Toast.LENGTH_SHORT).show();
                loadAndUpdateButtons();
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
            case AUTO_TREASURE:
                autoFunctionsManager.toggleAutoTreasure();
                Toast.makeText(context, autoFunctionsManager.isAutoTreasureEnabled() ? "Авто-Клад ВКЛ" : "Авто-Клад ВЫКЛ", Toast.LENGTH_SHORT).show();
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
        } else if (button.getActionType() == QuickActionType.AUTO_COMPASS) {
            new AlertDialog.Builder(context)
                    .setTitle("Авто-Компас")
                    .setItems(new CharSequence[]{"Настройки авто-компаса", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showAutoCompassSettingsDialog();
                        } else {
                            showRemoveConfirmation(position);
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else if (button.getActionType() == QuickActionType.AUTO_BOSS) {
            new AlertDialog.Builder(context)
                    .setTitle("Авто-Боссы")
                    .setItems(new CharSequence[]{"Настройки Авто-Боссов", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showAutoBossSettingsDialog();
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
        } else if (button.getActionType() == QuickActionType.AUTO_CURE) {
            new AlertDialog.Builder(context)
                    .setTitle("Авто-Лечение")
                    .setItems(new CharSequence[]{"Настройки авто-лечения", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showAutoCureSettingsDialog();
                        } else {
                            showRemoveConfirmation(position);
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else if (button.getActionType() == QuickActionType.AUTO_TREASURE) {
            new AlertDialog.Builder(context)
                    .setTitle("Авто-Клад")
                    .setItems(new CharSequence[]{"Настройки авто-клада", "Удалить кнопку"}, (dialog, which) -> {
                        if (which == 0) {
                            showAutoTreasureSettingsDialog();
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

    private void showAutoTreasureSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        CheckBox useDig = new CheckBox(context);
        useDig.setText("Выкапывать клад при появлении кнопки \"Копать\"");
        useDig.setChecked(autoFunctionsManager.isAutoTreasureDigEnabled());
        root.addView(useDig);

        TextView shovelTitle = new TextView(context);
        shovelTitle.setText("Лопата в руку перед копкой");
        shovelTitle.setPadding(0, pad, 0, 0);
        root.addView(shovelTitle);

        Spinner shovelSpinner = new Spinner(context);
        ArrayAdapter<String> shovelAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                TREASURE_SHOVEL_LABELS
        );
        shovelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shovelSpinner.setAdapter(shovelAdapter);
        int shovelIndex = indexOfTreasureShovel(autoFunctionsManager.getAutoTreasureShovelOption());
        shovelSpinner.setSelection(shovelIndex >= 0 ? shovelIndex : 1);
        root.addView(shovelSpinner);

        CheckBox fixedCellEnabled = new CheckBox(context);
        fixedCellEnabled.setText("Клад точно здесь");
        fixedCellEnabled.setChecked(autoFunctionsManager.isAutoTreasureFixedCellEnabled());
        fixedCellEnabled.setPadding(0, pad, 0, 0);
        root.addView(fixedCellEnabled);

        EditText fixedCellInput = new EditText(context);
        fixedCellInput.setHint("Например: 12-494");
        fixedCellInput.setInputType(InputType.TYPE_CLASS_TEXT);
        fixedCellInput.setText(autoFunctionsManager.getAutoTreasureFixedCellRegNum());
        root.addView(fixedCellInput);

        Runnable syncControls = () -> {
            boolean digEnabled = useDig.isChecked();
            shovelTitle.setEnabled(digEnabled);
            shovelSpinner.setEnabled(digEnabled);
            boolean fixedEnabled = fixedCellEnabled.isChecked();
            fixedCellInput.setEnabled(fixedEnabled);
        };
        useDig.setOnCheckedChangeListener((buttonView, isChecked) -> syncControls.run());
        fixedCellEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> syncControls.run());
        syncControls.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Настройки Авто-Клада")
                .setView(scroll)
                .setPositiveButton("Сохранить", (d, which) -> {
                    autoFunctionsManager.setAutoTreasureDigEnabled(useDig.isChecked());
                    int selectedIndex = Math.max(0, Math.min(TREASURE_SHOVEL_VALUES.length - 1, shovelSpinner.getSelectedItemPosition()));
                    autoFunctionsManager.setAutoTreasureShovelOption(TREASURE_SHOVEL_VALUES[selectedIndex]);
                    autoFunctionsManager.setAutoTreasureFixedCellEnabled(fixedCellEnabled.isChecked());
                    String fixedRegNum = fixedCellInput.getText() == null ? "" : fixedCellInput.getText().toString();
                    autoFunctionsManager.setAutoTreasureFixedCellRegNum(fixedRegNum);
                    Toast.makeText(context, "Настройки авто-клада сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();

    }

    private void showAutoCureSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView woundsTitle = new TextView(context);
        woundsTitle.setText("Выбор травм для лечения");
        root.addView(woundsTitle);

        CheckBox woundLight = new CheckBox(context);
        woundLight.setText("Легкая");
        woundLight.setChecked(autoFunctionsManager.isAutoCureWoundLightEnabled());
        root.addView(woundLight);

        CheckBox woundMedium = new CheckBox(context);
        woundMedium.setText("Средняя");
        woundMedium.setChecked(autoFunctionsManager.isAutoCureWoundMediumEnabled());
        root.addView(woundMedium);

        CheckBox woundHeavy = new CheckBox(context);
        woundHeavy.setText("Тяжелая");
        woundHeavy.setChecked(autoFunctionsManager.isAutoCureWoundHeavyEnabled());
        root.addView(woundHeavy);

        CheckBox woundBattle = new CheckBox(context);
        woundBattle.setText("Боевая");
        woundBattle.setChecked(autoFunctionsManager.isAutoCureWoundBattleEnabled());
        root.addView(woundBattle);

        TextView targetsTitle = new TextView(context);
        targetsTitle.setText("Выбор типа игроков для лечения");
        targetsTitle.setPadding(0, pad, 0, 0);
        root.addView(targetsTitle);

        CheckBox targetFriends = new CheckBox(context);
        targetFriends.setText("Друзья");
        targetFriends.setChecked(autoFunctionsManager.isAutoCureTargetFriendsEnabled());
        root.addView(targetFriends);

        CheckBox targetNeutrals = new CheckBox(context);
        targetNeutrals.setText("Нейтралы");
        targetNeutrals.setChecked(autoFunctionsManager.isAutoCureTargetNeutralsEnabled());
        root.addView(targetNeutrals);

        TextView elixirTitle = new TextView(context);
        elixirTitle.setText("Лечение себя Эликсиром Мгновенного Исцеления");
        elixirTitle.setPadding(0, pad, 0, 0);
        root.addView(elixirTitle);

        CheckBox useSelfElixir = new CheckBox(context);
        useSelfElixir.setText("Использовать эликсир для лечения своих травм");
        useSelfElixir.setChecked(autoFunctionsManager.isAutoCureUseSelfElixirEnabled());
        root.addView(useSelfElixir);

        CheckBox elixirLight = new CheckBox(context);
        elixirLight.setText("Эликсир для легких травм");
        elixirLight.setChecked(autoFunctionsManager.isAutoCureSelfElixirLightEnabled());
        root.addView(elixirLight);

        CheckBox elixirMedium = new CheckBox(context);
        elixirMedium.setText("Эликсир для средних травм");
        elixirMedium.setChecked(autoFunctionsManager.isAutoCureSelfElixirMediumEnabled());
        root.addView(elixirMedium);

        CheckBox elixirHeavy = new CheckBox(context);
        elixirHeavy.setText("Эликсир для тяжелых травм");
        elixirHeavy.setChecked(autoFunctionsManager.isAutoCureSelfElixirHeavyEnabled());
        root.addView(elixirHeavy);

        TextView elixirBattleInfo = new TextView(context);
        elixirBattleInfo.setText("Боевая травма лечится только Боевой аптечкой.");
        root.addView(elixirBattleInfo);

        Runnable syncElixirControls = () -> {
            boolean enabled = useSelfElixir.isChecked();
            elixirLight.setEnabled(enabled);
            elixirMedium.setEnabled(enabled);
            elixirHeavy.setEnabled(enabled);
        };
        useSelfElixir.setOnCheckedChangeListener((buttonView, isChecked) -> syncElixirControls.run());
        syncElixirControls.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Настройки Авто-Лечения")
                .setView(scroll)
                .setPositiveButton("Сохранить", (d, which) -> {
                    autoFunctionsManager.setAutoCureWoundLightEnabled(woundLight.isChecked());
                    autoFunctionsManager.setAutoCureWoundMediumEnabled(woundMedium.isChecked());
                    autoFunctionsManager.setAutoCureWoundHeavyEnabled(woundHeavy.isChecked());
                    autoFunctionsManager.setAutoCureWoundBattleEnabled(woundBattle.isChecked());
                    autoFunctionsManager.setAutoCureTargetFriendsEnabled(targetFriends.isChecked());
                    autoFunctionsManager.setAutoCureTargetNeutralsEnabled(targetNeutrals.isChecked());
                    autoFunctionsManager.setAutoCureUseSelfElixirEnabled(useSelfElixir.isChecked());
                    autoFunctionsManager.setAutoCureSelfElixirLightEnabled(elixirLight.isChecked());
                    autoFunctionsManager.setAutoCureSelfElixirMediumEnabled(elixirMedium.isChecked());
                    autoFunctionsManager.setAutoCureSelfElixirHeavyEnabled(elixirHeavy.isChecked());
                    Toast.makeText(context, "Настройки авто-лечения сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
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

    private int indexOfTreasureShovel(String value) {
        if (value == null) {
            return -1;
        }
        String needle = value.trim();
        for (int i = 0; i < TREASURE_SHOVEL_VALUES.length; i++) {
            if (TREASURE_SHOVEL_VALUES[i].equalsIgnoreCase(needle)) {
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

    /**
     * Окно настроек "Авто-Компас" (long-press на QuickButton).
     *
     * Назначение:
     * - редактирует целевой ник и режим поиска по клеткам;
     * - выполняет ручный resolve локации цели через pinfo;
     * - сохраняет ручной CSV клеток (с приоритетом над авто-заполнением);
     * - запускает полный цикл "ПОИСК ЦЕЛИ" (hunt-all, как в C#-подобном сценарии).
     *
     * Зависимости:
     * - `AutoFunctionsManager.resolveAutoCompassLocation(...)` — одноразовый pinfo-resolve;
     * - `AutoFunctionsManager.startSettingsCompassTargetSearch(...)` — запуск полного автопоиска;
     * - `AutoFunctionsManager.setAutoCompass*` — сохранение runtime/prefs параметров;
     * - `R.color.purple_500` / `R.color.white` — единый визуальный стиль project UI.
     *
     * Важно для отладки:
     * - авто-заполненные клетки не затирают ручной ввод пользователя;
     * - при успешном resolve обновляется только "предзаполнение", а не принудительный manual override.
     */
    private void showAutoCompassSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView targetLabel = new TextView(context);
        targetLabel.setText("Целевой ник");
        root.addView(targetLabel);

        EditText targetInput = new EditText(context);
        targetInput.setHint("Nickname");
        targetInput.setSingleLine(true);
        targetInput.setText(autoFunctionsManager.getAutoCompassTargetNick());
        root.addView(targetInput);

        Button resolveLocationButton = new Button(context);
        resolveLocationButton.setText("Поиск локации игрока");
        resolveLocationButton.setAllCaps(false);
        resolveLocationButton.setTextColor(ContextCompat.getColor(context, R.color.white));
        resolveLocationButton.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500));
        LinearLayout.LayoutParams resolveButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        resolveButtonParams.topMargin = dpToPx(8);
        root.addView(resolveLocationButton, resolveButtonParams);

        TextView locationLabel = new TextView(context);
        locationLabel.setPadding(0, pad, 0, 0);
        locationLabel.setText("Текущая локация цели");
        root.addView(locationLabel);

        TextView locationValue = new TextView(context);
        String locationText = autoFunctionsManager.getAutoCompassLastLocationLabel();
        locationValue.setText(locationText == null || locationText.trim().isEmpty() ? "—" : locationText);
        root.addView(locationValue);

        TextView cellsLabel = new TextView(context);
        cellsLabel.setPadding(0, pad, 0, 0);
        cellsLabel.setText("Клетки поиска (автозаполненные или ручные)");
        root.addView(cellsLabel);

        String manualCellsCsv = autoFunctionsManager.getAutoCompassManualCellsCsv();
        String autoCellsCsv = autoFunctionsManager.getAutoCompassCellsCsv();
        final String[] autoCellsCsvRef = new String[]{autoCellsCsv == null ? "" : autoCellsCsv.trim()};
        boolean useAutoFilledCells = (manualCellsCsv == null || manualCellsCsv.trim().isEmpty())
                && !autoCellsCsvRef[0].isEmpty();

        EditText cellsInput = new EditText(context);
        cellsInput.setHint("Например: 8-321, 8-322, 8-323");
        cellsInput.setMinLines(2);
        cellsInput.setText(useAutoFilledCells ? autoCellsCsvRef[0] : manualCellsCsv);
        root.addView(cellsInput);

        // Одноразовый запрос pinfo из окна настроек:
        // обновляет "Текущую локацию цели" и список возможных клеток без старта авто-движения.
        resolveLocationButton.setOnClickListener(v -> {
            String targetNick = targetInput.getText() == null ? "" : targetInput.getText().toString().trim();
            if (targetNick.isEmpty()) {
                Toast.makeText(context, "Укажите ник цели для поиска", Toast.LENGTH_SHORT).show();
                return;
            }

            resolveLocationButton.setEnabled(false);
            resolveLocationButton.setText("Поиск...");
            new Thread(() -> {
                AutoFunctionsManager.CompassLocationResolveResult result =
                        autoFunctionsManager.resolveAutoCompassLocation(targetNick);
                targetInput.post(() -> {
                    resolveLocationButton.setEnabled(true);
                    resolveLocationButton.setText("Поиск локации игрока");
                    if (!result.success) {
                        if (!result.locationLabel.isEmpty()) {
                            locationValue.setText(result.locationLabel);
                        }
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    targetInput.setText(result.targetNick);
                    targetInput.setSelection(result.targetNick.length());
                    locationValue.setText(result.locationLabel.isEmpty() ? "—" : result.locationLabel);

                    String previousAutoCells = autoCellsCsvRef[0];
                    String currentCells = cellsInput.getText() == null ? "" : cellsInput.getText().toString().trim();
                    autoCellsCsvRef[0] = result.cellsCsv == null ? "" : result.cellsCsv.trim();
                    if (currentCells.isEmpty() || currentCells.equals(previousAutoCells)) {
                        cellsInput.setText(autoCellsCsvRef[0]);
                    }
                    Toast.makeText(context, "Локация и клетки обновлены", Toast.LENGTH_SHORT).show();
                });
            }, "auto-compass-resolve").start();
        });

        CheckBox huntAllCheck = new CheckBox(context);
        huntAllCheck.setPadding(0, pad, 0, 0);
        huntAllCheck.setText("Ходим ловим по клеткам");
        huntAllCheck.setChecked(autoFunctionsManager.isAutoCompassHuntMode());
        root.addView(huntAllCheck);

        TextView intervalLabel = new TextView(context);
        intervalLabel.setPadding(0, pad, 0, 0);
        intervalLabel.setText("Интервал опроса pinfo");
        root.addView(intervalLabel);

        final int[] intervalValues = new int[]{1, 2, 5};
        final String[] intervalLabels = new String[]{"1 сек", "2 сек", "5 сек"};
        Spinner intervalSpinner = new Spinner(context);
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, intervalLabels);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        intervalSpinner.setAdapter(intervalAdapter);
        int currentInterval = autoFunctionsManager.getAutoCompassPollIntervalSec();
        int selectedIntervalIndex = 1;
        for (int index = 0; index < intervalValues.length; index++) {
            if (intervalValues[index] == currentInterval) {
                selectedIntervalIndex = index;
                break;
            }
        }
        intervalSpinner.setSelection(selectedIntervalIndex);
        root.addView(intervalSpinner);

        // Кнопка "ПОИСК ЦЕЛИ" запускает именно полный цикл авто-компаса (hunt-all),
        // а не разовый шаг на ближайшую клетку.
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Настройки Авто-Компас")
                .setView(scroll)
                .setPositiveButton("Сохранить", (d, which) -> {
                    String targetNick = targetInput.getText() == null ? "" : targetInput.getText().toString();
                    autoFunctionsManager.setAutoCompassTargetNick(targetNick);
                    String cellsText = cellsInput.getText() == null ? "" : cellsInput.getText().toString();
                    if (cellsText.trim().equals(autoCellsCsvRef[0])) {
                        autoFunctionsManager.setAutoCompassManualCellsCsv("");
                    } else {
                        autoFunctionsManager.setAutoCompassManualCellsCsv(cellsText);
                    }
                    autoFunctionsManager.setAutoCompassHuntMode(huntAllCheck.isChecked());
                    int intervalSec = intervalValues[Math.max(0, Math.min(intervalValues.length - 1, intervalSpinner.getSelectedItemPosition()))];
                    autoFunctionsManager.setAutoCompassPollIntervalSec(intervalSec);
                    Toast.makeText(context, "Настройки авто-компаса сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("ПОИСК ЦЕЛИ", (d, which) -> {
                    String targetNick = targetInput.getText() == null ? "" : targetInput.getText().toString();
                    autoFunctionsManager.startSettingsCompassTargetSearch(targetNick);
                    loadAndUpdateButtons();
                })
                .setNegativeButton("Отмена", null)
                .show();

        Button searchTargetButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (searchTargetButton != null) {
            searchTargetButton.setAllCaps(false);
            searchTargetButton.setTextColor(ContextCompat.getColor(context, R.color.white));
            searchTargetButton.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500));
        }
    }

    /**
     * Окно настроек для авто-функции «Авто-Боссы».
     *
     * Назначение:
     * - управляет параметрами сценария BossAuto без дублирования логики в UI-слое;
     * - сохраняет только пользовательские настройки (опрос/таймауты/вопрос цели), а исполнение остаётся в BossAuto.
     *
     * Зависимости:
     * - `AutoFunctionsManager` предоставляет/сохраняет значения (`get/setAutoBoss*`);
     * - фактическое выполнение сценария (поиск цели, свиток, возврат) выполняется в `BossAuto.java`;
     * - ограничения диапазонов валидируются в `BossAuto` (UI передаёт «сырые» числа).
     */
    private void showAutoBossSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        CheckBox askTargetCheck = new CheckBox(context);
        askTargetCheck.setText("Писать цели в чат: %<nick> Подскажи на какой клетке Босс?");
        askTargetCheck.setChecked(autoFunctionsManager.isAutoBossAskTargetEnabled());
        root.addView(askTargetCheck);

        TextView waitScrollLabel = new TextView(context);
        waitScrollLabel.setPadding(0, pad, 0, 0);
        waitScrollLabel.setText("Ожидание перед свитком, сек (1..10)");
        root.addView(waitScrollLabel);

        EditText waitScrollInput = new EditText(context);
        waitScrollInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        waitScrollInput.setSingleLine(true);
        waitScrollInput.setText(String.valueOf(autoFunctionsManager.getAutoBossWaitBeforeScrollSec()));
        root.addView(waitScrollInput);

        TextView searchTimeoutLabel = new TextView(context);
        searchTimeoutLabel.setPadding(0, pad, 0, 0);
        searchTimeoutLabel.setText("Таймаут поиска цели, сек (60..1200)");
        root.addView(searchTimeoutLabel);

        EditText searchTimeoutInput = new EditText(context);
        searchTimeoutInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        searchTimeoutInput.setSingleLine(true);
        searchTimeoutInput.setText(String.valueOf(autoFunctionsManager.getAutoBossSearchTimeoutSec()));
        root.addView(searchTimeoutInput);

        TextView waitFightLabel = new TextView(context);
        waitFightLabel.setPadding(0, pad, 0, 0);
        waitFightLabel.setText("Таймаут ожидания старта боя, сек (10..120)");
        root.addView(waitFightLabel);

        EditText waitFightInput = new EditText(context);
        waitFightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        waitFightInput.setSingleLine(true);
        waitFightInput.setText(String.valueOf(autoFunctionsManager.getAutoBossWaitFightTimeoutSec()));
        root.addView(waitFightInput);

        new AlertDialog.Builder(context)
                .setTitle("Настройки Авто-Боссов")
                .setView(scroll)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    int waitScrollSec = autoFunctionsManager.getAutoBossWaitBeforeScrollSec();
                    int searchTimeoutSec = autoFunctionsManager.getAutoBossSearchTimeoutSec();
                    int waitFightTimeoutSec = autoFunctionsManager.getAutoBossWaitFightTimeoutSec();

                    try {
                        String value = waitScrollInput.getText() == null ? "" : waitScrollInput.getText().toString().trim();
                        if (!value.isEmpty()) {
                            waitScrollSec = Integer.parseInt(value);
                        }
                    } catch (Exception ignored) {
                    }

                    try {
                        String value = searchTimeoutInput.getText() == null ? "" : searchTimeoutInput.getText().toString().trim();
                        if (!value.isEmpty()) {
                            searchTimeoutSec = Integer.parseInt(value);
                        }
                    } catch (Exception ignored) {
                    }

                    try {
                        String value = waitFightInput.getText() == null ? "" : waitFightInput.getText().toString().trim();
                        if (!value.isEmpty()) {
                            waitFightTimeoutSec = Integer.parseInt(value);
                        }
                    } catch (Exception ignored) {
                    }

                    autoFunctionsManager.setAutoBossAskTargetEnabled(askTargetCheck.isChecked());
                    autoFunctionsManager.setAutoBossWaitBeforeScrollSec(waitScrollSec);
                    autoFunctionsManager.setAutoBossSearchTimeoutSec(searchTimeoutSec);
                    autoFunctionsManager.setAutoBossWaitFightTimeoutSec(waitFightTimeoutSec);
                    Toast.makeText(context, "Настройки Авто-Боссов сохранены", Toast.LENGTH_SHORT).show();
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
        if (navigator != null) {
            navigator.showDialog();
        }
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
