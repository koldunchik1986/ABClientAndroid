package ru.neverlands.abclient.ui;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

import java.util.List;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.adapter.FunctionListAdapter;
import ru.neverlands.abclient.manager.QuickButtonsManager;
import ru.neverlands.abclient.model.QuickActionType;
import ru.neverlands.abclient.model.QuickButton;
import ru.neverlands.abclient.ContactsActivity;
import ru.neverlands.abclient.PinfoActivity;
import ru.neverlands.abclient.LogsActivity;
import ru.neverlands.abclient.manager.ContactsManager;
import ru.neverlands.abclient.manager.FastActionManager;

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
    private final ImageButton[] buttons = new ImageButton[TOTAL_BUTTONS];
    private OnQuickActionListener actionListener;

    public interface OnQuickActionListener {
        void onQuickAction(QuickActionType actionType);
    }

    public QuickButtonsPanel(Context context, View rootView, OnQuickActionListener listener) {
        this.context = context;
        this.actionListener = listener;
        this.buttonsManager = QuickButtonsManager.getInstance(context);
        
        initButtons(rootView);
        loadAndUpdateButtons();
    }

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

    private void updateButtonAppearance(int position, QuickButton button) {
        Log.d(TAG, "updateButtonAppearance: position=" + position + ", button=" + (button != null ? button.getActionType() : "null"));
        if (position >= buttons.length || buttons[position] == null) {
            Log.w(TAG, "updateButtonAppearance: button at position " + position + " is null!");
            return;
        }
        
        if (button == null || button.isEmpty()) {
            buttons[position].setImageResource(R.drawable.ic_add);
            buttons[position].setContentDescription("Добавить функцию");
            Log.d(TAG, "updateButtonAppearance: set empty icon for position " + position);
        } else {
            loadIconForAction(buttons[position], button.getActionType());
            buttons[position].setContentDescription(button.getDisplayName());
            Log.d(TAG, "updateButtonAppearance: icon loaded for position " + position);
        }
        
        // Принудительно обновляем кнопку на UI потоке
        buttons[position].post(() -> buttons[position].invalidate());
    }

    private void loadIconForAction(ImageButton button, QuickActionType type) {
        String iconUrl = getIconUrlForAction(type);
        if (iconUrl != null) {
            Glide.with(context)
                .load(iconUrl)
                .placeholder(R.drawable.ic_add)
                .into(button);
        } else {
            button.setImageResource(R.drawable.ic_add);
        }
    }

    private String getIconUrlForAction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT:
                return null;
            case QUICK_ACTIONS:
                return null;
            case AUTO_RECALL:
                return null;
            case AUTO_HUNT:
                return null;
            case AUTO_ATTACK:
                return null;
            case AUTO_INVISIBLE:
                return null;
            case LOCATION_TRACKING:
                return null;
            case AUTO_DETECT:
                return null;
            case AUTO_SUMMON:
                return null;
            case AUTO_HEAL:
                return null;
            case OPEN_CONTACTS:
                return null;
            case OPEN_PINFO:
                return null;
            case OPEN_LOGS:
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

    private int getIconForAction(QuickActionType type) {
        switch (type) {
            case AUTO_FIGHT:
                return R.drawable.ic_add;
            case QUICK_ACTIONS:
                return R.drawable.ic_sort;
            case AUTO_RECALL:
                return R.drawable.ic_add;
            case AUTO_HUNT:
                return R.drawable.ic_add;
            case AUTO_ATTACK:
                return R.drawable.ic_add;
            case AUTO_INVISIBLE:
                return R.drawable.ic_add;
            case LOCATION_TRACKING:
                return R.drawable.ic_add;
            case AUTO_DETECT:
                return R.drawable.ic_add;
            case AUTO_SUMMON:
                return R.drawable.ic_add;
            case AUTO_HEAL:
                return R.drawable.ic_add;
            case OPEN_CONTACTS:
                return R.drawable.ic_add_contact;
            case OPEN_PINFO:
                return R.drawable.ic_add;
            case OPEN_LOGS:
                return R.drawable.ic_add;
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
                Toast.makeText(context, "Автобой", Toast.LENGTH_SHORT).show();
                break;
            case QUICK_ACTIONS:
                if (actionListener != null) {
                    actionListener.onQuickAction(actionType);
                }
                break;
            case AUTO_RECALL:
                Toast.makeText(context, "Авторыбалка", Toast.LENGTH_SHORT).show();
                break;
            case AUTO_HUNT:
                Toast.makeText(context, "Автоохота", Toast.LENGTH_SHORT).show();
                break;
            case AUTO_ATTACK:
                Toast.makeText(context, "Автонападение", Toast.LENGTH_SHORT).show();
                break;
            case AUTO_INVISIBLE:
                Toast.makeText(context, "АвтоНевид", Toast.LENGTH_SHORT).show();
                break;
            case LOCATION_TRACKING:
                Toast.makeText(context, "Слежение за локацией", Toast.LENGTH_SHORT).show();
                break;
            case AUTO_DETECT:
                Toast.makeText(context, "АвтоОбнаружение", Toast.LENGTH_SHORT).show();
                break;
            case AUTO_SUMMON:
                Toast.makeText(context, "АвтоПризыв", Toast.LENGTH_SHORT).show();
                break;
            case AUTO_HEAL:
                Toast.makeText(context, "АвтоЛечение", Toast.LENGTH_SHORT).show();
                break;
            case OPEN_CONTACTS:
                openContacts();
                break;
            case OPEN_PINFO:
                Toast.makeText(context, "Открыть PINFO - выберите игрока", Toast.LENGTH_SHORT).show();
                break;
            case OPEN_LOGS:
                openLogs();
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

    private void showButtonOptions(int position) {
        QuickButton button = buttonsManager.getButton(position);
        
        if (button.isEmpty()) {
            showFunctionSelector(position);
        } else {
            showRemoveConfirmation(position);
        }
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

    private void openContacts() {
        Intent intent = new Intent(context, ContactsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void openLogs() {
        Intent intent = new Intent(context, LogsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void refreshContacts() {
        Toast.makeText(context, "Обновление контактов...", Toast.LENGTH_SHORT).show();
        ContactsManager.refreshAllContacts(context, () -> {
            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(() -> 
                Toast.makeText(context, "Контакты обновлены", Toast.LENGTH_SHORT).show()
            );
        });
    }

    public void refresh() {
        loadAndUpdateButtons();
    }
}
