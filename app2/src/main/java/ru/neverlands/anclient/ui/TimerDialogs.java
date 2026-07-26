package ru.neverlands.anclient.ui;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.util.List;
import ru.neverlands.anclient.R;
import ru.neverlands.anclient.manager.AppTimerManager;
import ru.neverlands.anclient.model.AppTimer;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.ParseUtils;

/**
 * Диалоги таймеров: список и форма создания/редактирования (D6).
 *
 * Зачем выделено из {@code QuickButtonsPanel}:
 * - блок занимал ~560 строк из 3631 и не имел ни одной связи с остальным состоянием панели
 *   (кнопки, менеджеры авто-функций, навигатор, вкладки) — зависел только от
 *   {@code Context} и {@link AppTimerManager};
 * - форма редактора сама по себе была самым большим методом файла (375 строк).
 *
 * Порт `FormTimers.cs` / `FormNewTimer.cs` из ПК-версии.
 *
 * Состояние намеренно передаётся через конструктор, а не статическими параметрами:
 * тела методов перенесены дословно и продолжают обращаться к {@code context}
 * и {@code appTimerManager} как к полям — это исключает ошибки переноса.
 */
public final class TimerDialogs {

    private static final String TAG = "TimerDialogs";

    private final Context context;
    private final AppTimerManager appTimerManager;

    public TimerDialogs(Context context, AppTimerManager appTimerManager) {
        this.context = context;
        this.appTimerManager = appTimerManager;
    }

    /** D6: реализация вынесена в {@link ru.neverlands.anclient.utils.UiUtils#dpToPx(Context, int)}. */
    private int dpToPx(int dp) {
        return ru.neverlands.anclient.utils.UiUtils.dpToPx(context, dp);
    }

    /** D6: список авто-функций и его лицензионная фильтрация вынесены в {@link AutoFunctionCatalog}. */
    private String[] getAllowedAutoFunctions() {
        return AutoFunctionCatalog.getAllowed();
    }

    private static final String[] TIMER_POTION_OPTIONS = new String[]{
            "Не пить, просто таймер",
            "Зелье Метаболизма",
            "Зелье Блаженства",
            "Зелье Сильной Спины",
            "Зелье Просветления",
            "Зелье Сокрушительных Ударов",
            "Зелье Стойкости",
            "Зелье Недосягаемости",
            "Зелье Точного Попадания",
            "Зелье Ловких Ударов",
            "Зелье Мужества",
            "Зелье Жизни",
            "Зелье Лечения",
            "Зелье Восстановления Маны",
            "Зелье Энергии",
            "Зелье Удачи",
            "Зелье Силы",
            "Зелье Ловкости",
            "Зелье Гения",
            "Зелье Боевой Славы",
            "Зелье Невидимости",
            "Зелье Секрет Волшебника",
            "Зелье Медитации",
            "Зелье Иммунитета",
            "Яд",
            "Зелье Лечения Отравлений",
            "Зелье Огненного Ореола",
            "Зелье Колкости",
            "Зелье Загрубелой Кожи",
            "Зелье Панциря",
            "Зелье Человек-гора",
            "Зелье Скорости",
            "Жажда Жизни",
            "Ментальная Жажда",
            "Зелье подвижности",
            "Ярость Берсерка",
            "Зелье Хрупкости",
            "Зелье Мифриловый Стержень",
            "Зелье Соколиный взор",
            "Секретное Зелье"
    };

    public void showTimersDialog() {
        final int pad = dpToPx(12);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(context);
        header.setText("Активные таймеры");
        root.addView(header);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        listParams.topMargin = dpToPx(8);
        root.addView(scrollView, listParams);

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.END);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bottomParams.topMargin = dpToPx(8);
        root.addView(bottom, bottomParams);

        Button cancelButton = new Button(context);
        cancelButton.setText("Отмена");
        cancelButton.setAllCaps(false);
        bottom.addView(cancelButton);

        Button addButton = new Button(context);
        addButton.setText("Добавить");
        addButton.setAllCaps(false);
        addButton.setTextColor(ContextCompat.getColor(context, R.color.colorOnPrimarySurface));
        addButton.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        addParams.leftMargin = dpToPx(8);
        bottom.addView(addButton, addParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Таймеры")
                .setView(root)
                .create();

        final int[] selectedTimerId = new int[]{-1};
        final Runnable[] rerenderRef = new Runnable[1];
        rerenderRef[0] = () -> {
            listContainer.removeAllViews();
            List<AppTimer> timers = appTimerManager.getTimers();
            if (timers.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("Нет активных таймеров");
                empty.setPadding(0, dpToPx(8), 0, dpToPx(8));
                listContainer.addView(empty);
                selectedTimerId[0] = -1;
                return;
            }

            for (AppTimer timer : timers) {
                final int timerId = timer.id;
                final boolean selected = timerId == selectedTimerId[0];

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                row.setClickable(true);
                row.setFocusable(true);
                row.setBackgroundColor(selected ? 0x334B2D90 : 0x00000000);

                TextView timerText = new TextView(context);
                timerText.setText(timer.toString());
                timerText.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                ));
                row.addView(timerText);

                ImageButton editButton = new ImageButton(context);
                editButton.setImageResource(android.R.drawable.ic_menu_edit);
                editButton.setBackgroundResource(android.R.color.transparent);
                editButton.setContentDescription("Изменить");
                editButton.setVisibility(selected ? View.VISIBLE : View.GONE);
                row.addView(editButton);

                ImageButton deleteButton = new ImageButton(context);
                deleteButton.setImageResource(android.R.drawable.ic_menu_delete);
                deleteButton.setBackgroundResource(android.R.color.transparent);
                deleteButton.setContentDescription("Удалить");
                deleteButton.setVisibility(selected ? View.VISIBLE : View.GONE);
                row.addView(deleteButton);

                row.setOnClickListener(view -> {
                    selectedTimerId[0] = timerId;
                    rerenderRef[0].run();
                });

                editButton.setOnClickListener(view -> showTimerEditorDialog(timer.copy(), () -> {
                    selectedTimerId[0] = timerId;
                    rerenderRef[0].run();
                }));

                deleteButton.setOnClickListener(view -> new AlertDialog.Builder(context)
                        .setTitle("Удаление таймера")
                        .setMessage("Удалить таймер?")
                        .setPositiveButton("Удалить", (deleteDialog, which) -> {
                            appTimerManager.removeTimerById(timerId);
                            selectedTimerId[0] = -1;
                            rerenderRef[0].run();
                        })
                        .setNegativeButton("Отмена", null)
                        .show());

                listContainer.addView(row);
            }
        };

        cancelButton.setOnClickListener(view -> dialog.dismiss());
        addButton.setOnClickListener(view -> showTimerEditorDialog(null, () -> {
            selectedTimerId[0] = -1;
            rerenderRef[0].run();
        }));

        dialog.setOnShowListener(dialogInterface -> rerenderRef[0].run());
        dialog.show();
    }

    /**
     * Форма создания/редактирования таймера (порт `FormNewTimer.cs`).
     *
     * Доступные действия:
     * - «Просто таймер»;
     * - «Пьём зелье по таймеру»;
     * - «Перемещаемся по таймеру»;
     * - «Одеваем комплект».
     */
    private void showTimerEditorDialog(AppTimer existingTimer, Runnable onSaved) {
        final boolean isEdit = existingTimer != null;
        final int pad = dpToPx(12);

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView nameLabel = new TextView(context);
        nameLabel.setText("Имя таймера");
        root.addView(nameLabel);

        EditText nameInput = new EditText(context);
        nameInput.setSingleLine(true);
        nameInput.setText(isEdit ? existingTimer.description : "");
        root.addView(nameInput);

        TextView delayLabel = new TextView(context);
        delayLabel.setPadding(0, pad, 0, 0);
        delayLabel.setText("Сработает через");
        root.addView(delayLabel);

        LinearLayout delayRow = new LinearLayout(context);
        delayRow.setOrientation(LinearLayout.HORIZONTAL);
        delayRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(delayRow);

        EditText hourInput = new EditText(context);
        hourInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hourInput.setSingleLine(true);
        hourInput.setHint("часы");
        LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(dpToPx(80), LinearLayout.LayoutParams.WRAP_CONTENT);
        delayRow.addView(hourInput, hourParams);

        TextView hourLabel = new TextView(context);
        hourLabel.setText("час");
        hourLabel.setPadding(dpToPx(4), 0, dpToPx(8), 0);
        delayRow.addView(hourLabel);

        EditText minuteInput = new EditText(context);
        minuteInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minuteInput.setSingleLine(true);
        minuteInput.setHint("мин");
        LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(dpToPx(80), LinearLayout.LayoutParams.WRAP_CONTENT);
        delayRow.addView(minuteInput, minuteParams);

        TextView minLabel = new TextView(context);
        minLabel.setText("мин");
        minLabel.setPadding(dpToPx(4), 0, 0, 0);
        delayRow.addView(minLabel);

        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(LinearLayout.VERTICAL);
        modeGroup.setPadding(0, pad, 0, 0);
        root.addView(modeGroup);

        // ===== НОВЫЕ РЕЖИМЫ ТАЙМЕРОВ =====
        RadioButton modeEnableAutoFunc = new RadioButton(context);
        modeEnableAutoFunc.setText("Включить Авто-Функцию");
        modeGroup.addView(modeEnableAutoFunc);

        // Контейнер для режима "Включить Авто-Функцию"
        LinearLayout enableAutoFuncContainer = new LinearLayout(context);
        enableAutoFuncContainer.setOrientation(LinearLayout.HORIZONTAL);
        enableAutoFuncContainer.setPadding(dpToPx(16), dpToPx(4), 0, 0);
        enableAutoFuncContainer.setVisibility(View.GONE);
        Spinner enableAutoFuncSpinner = new Spinner(context);
        final String[] allowedAutoFunctions = getAllowedAutoFunctions();
        ArrayAdapter<String> enableAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, allowedAutoFunctions);
        enableAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        enableAutoFuncSpinner.setAdapter(enableAdapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        enableAutoFuncContainer.addView(enableAutoFuncSpinner, spinnerParams);
        root.addView(enableAutoFuncContainer);

        RadioButton modeDisableAutoFunc = new RadioButton(context);
        modeDisableAutoFunc.setText("Выключить Авто-Функцию");
        modeGroup.addView(modeDisableAutoFunc);

        // Контейнер для режима "Выключить Авто-Функцию"
        LinearLayout disableAutoFuncContainer = new LinearLayout(context);
        disableAutoFuncContainer.setOrientation(LinearLayout.HORIZONTAL);
        disableAutoFuncContainer.setPadding(dpToPx(16), dpToPx(4), 0, 0);
        disableAutoFuncContainer.setVisibility(View.GONE);
        Spinner disableAutoFuncSpinner = new Spinner(context);
        ArrayAdapter<String> disableAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, allowedAutoFunctions);
        disableAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        disableAutoFuncSpinner.setAdapter(disableAdapter);
        disableAutoFuncContainer.addView(disableAutoFuncSpinner, spinnerParams);
        root.addView(disableAutoFuncContainer);

        // ===== ТРАДИЦИОННЫЕ РЕЖИМЫ =====

        RadioButton modePotion = new RadioButton(context);
        modePotion.setText("Пьем зелье по таймеру");
        modeGroup.addView(modePotion);

        RadioButton modeDestination = new RadioButton(context);
        modeDestination.setText("Перемещаемся по таймеру");
        modeGroup.addView(modeDestination);

        RadioButton modeComplect = new RadioButton(context);
        modeComplect.setText("Одеваем комплект");
        modeGroup.addView(modeComplect);

        // ===== КОНТЕЙНЕР ДЛЯ РЕЖИМА "ЗЕЛЬЕ" =====
        LinearLayout potionContainer = new LinearLayout(context);
        potionContainer.setOrientation(LinearLayout.VERTICAL);
        potionContainer.setVisibility(View.GONE);

        TextView potionTitle = new TextView(context);
        potionTitle.setPadding(0, pad, 0, 0);
        potionTitle.setText("Название зелья (из инвентаря)");
        potionContainer.addView(potionTitle);

        Spinner potionSpinner = new Spinner(context);
        ArrayAdapter<String> potionAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                TIMER_POTION_OPTIONS
        );
        potionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        potionSpinner.setAdapter(potionAdapter);
        potionContainer.addView(potionSpinner);

        LinearLayout drinkRow = new LinearLayout(context);
        drinkRow.setOrientation(LinearLayout.HORIZONTAL);
        drinkRow.setGravity(Gravity.CENTER_VERTICAL);
        drinkRow.setPadding(0, dpToPx(4), 0, 0);
        TextView drinkLabel = new TextView(context);
        drinkLabel.setText("Делать глотков");
        drinkRow.addView(drinkLabel);

        EditText drinkCountInput = new EditText(context);
        drinkCountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        drinkCountInput.setSingleLine(true);
        drinkCountInput.setText("1");
        LinearLayout.LayoutParams drinkCountParams = new LinearLayout.LayoutParams(dpToPx(64), LinearLayout.LayoutParams.WRAP_CONTENT);
        drinkCountParams.leftMargin = dpToPx(8);
        drinkRow.addView(drinkCountInput, drinkCountParams);
        potionContainer.addView(drinkRow);

        CheckBox recurCheck = new CheckBox(context);
        recurCheck.setText("Циклическое питье");
        potionContainer.addView(recurCheck);

        root.addView(potionContainer);

        // ===== КОНТЕЙНЕР ДЛЯ РЕЖИМА "ПЕРЕМЕЩЕНИЕ" =====
        LinearLayout destinationContainer = new LinearLayout(context);
        destinationContainer.setOrientation(LinearLayout.VERTICAL);
        destinationContainer.setVisibility(View.GONE);

        TextView destinationTitle = new TextView(context);
        destinationTitle.setPadding(0, pad, 0, 0);
        destinationTitle.setText("Клетка назначения");
        destinationContainer.addView(destinationTitle);

        EditText destinationInput = new EditText(context);
        destinationInput.setSingleLine(true);
        destinationInput.setHint("Например: 12-345");
        destinationContainer.addView(destinationInput);

        root.addView(destinationContainer);

        // ===== КОНТЕЙНЕР ДЛЯ РЕЖИМА "КОМПЛЕКТ" =====
        LinearLayout complectContainer = new LinearLayout(context);
        complectContainer.setOrientation(LinearLayout.VERTICAL);
        complectContainer.setVisibility(View.GONE);

        TextView complectTitle = new TextView(context);
        complectTitle.setPadding(0, pad, 0, 0);
        complectTitle.setText("Название комплекта");
        complectContainer.addView(complectTitle);

        // Получаем список сохраненных комплектов из профиля
        String[] complectOptions = getComplectOptionsArray();
        Spinner complectSpinner = new Spinner(context);
        ArrayAdapter<String> complectAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                complectOptions
        );
        complectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        complectSpinner.setAdapter(complectAdapter);
        complectContainer.addView(complectSpinner);

        root.addView(complectContainer);

        Runnable syncModeControls = () -> {
            boolean isEnable = modeEnableAutoFunc.isChecked();
            boolean isDisable = modeDisableAutoFunc.isChecked();
            boolean isPotion = modePotion.isChecked();
            boolean isDestination = modeDestination.isChecked();
            boolean isComplect = modeComplect.isChecked();

            // Управление видимостью контейнеров
            enableAutoFuncContainer.setVisibility(isEnable ? View.VISIBLE : View.GONE);
            disableAutoFuncContainer.setVisibility(isDisable ? View.VISIBLE : View.GONE);
            potionContainer.setVisibility(isPotion ? View.VISIBLE : View.GONE);
            destinationContainer.setVisibility(isDestination ? View.VISIBLE : View.GONE);
            complectContainer.setVisibility(isComplect ? View.VISIBLE : View.GONE);
        };

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> syncModeControls.run());

        int delayMinutes = 60;
        if (isEdit) {
            long deltaMs = Math.max(0L, existingTimer.triggerTime - System.currentTimeMillis());
            delayMinutes = (int) Math.max(0L, deltaMs / 60_000L);
        }
        int delayHours = delayMinutes / 60;
        int delayMinPart = delayMinutes % 60;
        hourInput.setText(String.valueOf(delayHours));
        minuteInput.setText(String.valueOf(delayMinPart));

        if (isEdit) {
            if (!TextUtils.isEmpty(existingTimer.enableAutoFunction)) {
                modeEnableAutoFunc.setChecked(true);
                for (int i = 0; i < allowedAutoFunctions.length; i++) {
                    if (allowedAutoFunctions[i].equals(existingTimer.enableAutoFunction)) {
                        enableAutoFuncSpinner.setSelection(i);
                        break;
                    }
                }
            } else if (!TextUtils.isEmpty(existingTimer.disableAutoFunction)) {
                modeDisableAutoFunc.setChecked(true);
                for (int i = 0; i < allowedAutoFunctions.length; i++) {
                    if (allowedAutoFunctions[i].equals(existingTimer.disableAutoFunction)) {
                        disableAutoFuncSpinner.setSelection(i);
                        break;
                    }
                }
            } else if (!TextUtils.isEmpty(existingTimer.potion)) {
                modePotion.setChecked(true);
                int potionIndex = findPotionOptionIndex(existingTimer.potion);
                potionSpinner.setSelection(Math.max(0, potionIndex));
                drinkCountInput.setText(String.valueOf(Math.max(1, existingTimer.drinkCount)));
                recurCheck.setChecked(existingTimer.isRecur);
            } else if (!TextUtils.isEmpty(existingTimer.destination)) {
                modeDestination.setChecked(true);
                destinationInput.setText(existingTimer.destination);
            } else if (!TextUtils.isEmpty(existingTimer.complect)) {
                modeComplect.setChecked(true);
                int complectIndex = findComplectOptionIndex(existingTimer.complect);
                complectSpinner.setSelection(Math.max(0, complectIndex));
            }
        } else {
            // По умолчанию при создании нового таймера - выбрать первый режим
            modeEnableAutoFunc.setChecked(true);
        }

        syncModeControls.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(isEdit ? "Изменить таймер" : "Новый таймер")
                .setView(scroll)
                .setPositiveButton("ОК", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (saveButton == null) {
                return;
            }
            // Фиолетовый фон для кнопки "ОК"
            saveButton.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500));
            saveButton.setTextColor(ContextCompat.getColor(context, R.color.colorOnPrimarySurface));
            saveButton.setOnClickListener(view -> {
                int hours = ParseUtils.parseIntInRange(hourInput.getText() == null ? "" : hourInput.getText().toString(), 0, 999, 0);
                int minutes = ParseUtils.parseIntInRange(minuteInput.getText() == null ? "" : minuteInput.getText().toString(), 0, 59, 0);
                int triggerMinutes = (hours * 60) + minutes;

                AppTimer timer = isEdit ? existingTimer.copy() : new AppTimer();
                timer.triggerTime = System.currentTimeMillis() + (triggerMinutes * 60_000L);
                timer.description = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                timer.potion = "";
                timer.destination = "";
                timer.complect = "";
                timer.enableAutoFunction = "";
                timer.disableAutoFunction = "";
                timer.drinkCount = 0;
                timer.isRecur = false;
                timer.everyMinutes = 0;

                if (modeEnableAutoFunc.isChecked()) {
                    int autoFuncIndex = enableAutoFuncSpinner.getSelectedItemPosition();
                    if (autoFuncIndex < 0 || autoFuncIndex >= allowedAutoFunctions.length) {
                        Toast.makeText(context, "Выберите авто-функцию для включения", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    timer.enableAutoFunction = allowedAutoFunctions[autoFuncIndex];
                    if (TextUtils.isEmpty(timer.description)) {
                        timer.description = "Включаем " + timer.enableAutoFunction;
                    }
                    FileLogger.trace(TAG, "Timer: ENABLE auto=" + timer.enableAutoFunction);
                } else if (modeDisableAutoFunc.isChecked()) {
                    int autoFuncIndex = disableAutoFuncSpinner.getSelectedItemPosition();
                    if (autoFuncIndex < 0 || autoFuncIndex >= allowedAutoFunctions.length) {
                        Toast.makeText(context, "Выберите авто-функцию для отключения", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    timer.disableAutoFunction = allowedAutoFunctions[autoFuncIndex];
                    if (TextUtils.isEmpty(timer.description)) {
                        timer.description = "Выключаем " + timer.disableAutoFunction;
                    }
                    FileLogger.trace(TAG, "Timer: DISABLE auto=" + timer.disableAutoFunction);
                } else if (modePotion.isChecked()) {
                    int potionIndex = potionSpinner.getSelectedItemPosition();
                    if (potionIndex <= 0 || potionIndex >= TIMER_POTION_OPTIONS.length) {
                        Toast.makeText(context, "Выберите зелье", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    timer.potion = TIMER_POTION_OPTIONS[potionIndex];
                    timer.drinkCount = ParseUtils.parseIntInRange(
                            drinkCountInput.getText() == null ? "" : drinkCountInput.getText().toString(),
                            1,
                            999,
                            1
                    );
                    timer.isRecur = recurCheck.isChecked();
                    if (timer.isRecur) {
                        timer.everyMinutes = triggerMinutes;
                    }
                    if (TextUtils.isEmpty(timer.description)) {
                        timer.description = "Выпьем " + timer.potion;
                    }
                } else if (modeDestination.isChecked()) {
                    timer.destination = destinationInput.getText() == null ? "" : destinationInput.getText().toString().trim();
                    if (TextUtils.isEmpty(timer.destination)) {
                        Toast.makeText(context, "Укажите клетку назначения", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(timer.description)) {
                        timer.description = "Идем на " + timer.destination;
                    }
                } else if (modeComplect.isChecked()) {
                    int complectIndex = complectSpinner.getSelectedItemPosition();
                    if (complectIndex <= 0 || complectIndex >= getComplectOptionsArray().length) {
                        Toast.makeText(context, "Выберите комплект", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    timer.complect = getComplectOptionsArray()[complectIndex];
                    String msg = "COMPLECT_TIMER_DIALOG_TRACE: selected index=" + complectIndex + ", complect=" + timer.complect;
                    AppLog.d(TAG, TAG, msg);
                    if (TextUtils.isEmpty(timer.description)) {
                        timer.description = "Одеваем комплект " + timer.complect;
                    }
                }

                if (isEdit) {
                    if (!appTimerManager.updateTimer(existingTimer.id, timer)) {
                        Toast.makeText(context, "Не удалось изменить таймер", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    AppTimer added = appTimerManager.addAppTimer(timer);
                    if (added == null) {
                        Toast.makeText(context, "Таймер не добавлен (время уже прошло)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (onSaved != null) {
                    onSaved.run();
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private int findPotionOptionIndex(String potionName) {
        if (TextUtils.isEmpty(potionName)) {
            return 0;
        }
        for (int index = 0; index < TIMER_POTION_OPTIONS.length; index++) {
            if (potionName.equalsIgnoreCase(TIMER_POTION_OPTIONS[index])) {
                return index;
            }
        }
        return 0;
    }

    /**
     * Получает список доступных комплектов из профиля.
     * Если комплектов нет, возвращает массив с одним пунктом "Комплектов не найдено".
     */
    private String[] getComplectOptionsArray() {
        if (AppVars.Profile == null || TextUtils.isEmpty(AppVars.Profile.SavedComplectsList)) {
            return new String[]{"Откройте инвентарь для обновления"};
        }
        String[] complects = AppVars.Profile.SavedComplectsList.split("\\|");
        if (complects.length == 0) {
            return new String[]{"Откройте инвентарь для обновления"};
        }
        return complects;
    }

    /**
     * Находит индекс комплекта в списке.
     * Если не найден, возвращает 0 (первый пункт).
     */
    private int findComplectOptionIndex(String complectName) {
        if (TextUtils.isEmpty(complectName)) {
            return 0;
        }
        String[] options = getComplectOptionsArray();
        for (int index = 0; index < options.length; index++) {
            if (complectName.equalsIgnoreCase(options[index])) {
                return index;
            }
        }
        return 0;
    }
}
