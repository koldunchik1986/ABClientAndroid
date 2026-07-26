package ru.neverlands.anclient.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.util.LinkedHashSet;
import java.util.List;
import ru.neverlands.anclient.R;
import ru.neverlands.anclient.manager.AutoCutManager;
import ru.neverlands.anclient.manager.AutoFunctionsManager;

/**
 * Диалоги Авто-Травника и Авто-Лесоруба (D6).
 *
 * Зачем выделено из {@code QuickButtonsPanel}:
 * - девять диалогов занимали ~597 строк и образовывали сплошной связный блок;
 * - зависимости ограничены {@code Context} и {@link AutoFunctionsManager} — ни одной
 *   связи с кнопками панели, навигатором, вкладками и таймерами.
 *
 * Состав:
 * - настройки авто-травника / авто-лесоруба;
 * - списки трав и деревьев с редакторами отдельных записей;
 * - выбор серпов и топоров;
 * - общие настройки смен ресурсов (используются обоими режимами).
 *
 * Тела методов перенесены дословно; {@code context} и {@code autoFunctionsManager}
 * стали полями, поэтому обращения внутри кода не менялись.
 */
public final class AutoCutDialogs {

    private final Context context;
    private final AutoFunctionsManager autoFunctionsManager;

    public AutoCutDialogs(Context context, AutoFunctionsManager autoFunctionsManager) {
        this.context = context;
        this.autoFunctionsManager = autoFunctionsManager;
    }

    /** D6: реализация вынесена в {@link ru.neverlands.anclient.utils.UiUtils#dpToPx(Context, int)}. */
    private int dpToPx(int dp) {
        return ru.neverlands.anclient.utils.UiUtils.dpToPx(context, dp);
    }

    public void showAutoCutSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView cellsTitle = new TextView(context);
        cellsTitle.setText("Клетки для поиска (через запятую)");
        root.addView(cellsTitle);

        EditText cellsInput = new EditText(context);
        cellsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        cellsInput.setSingleLine(false);
        cellsInput.setMinLines(2);
        cellsInput.setHint("Например: 12-494, 13-501");
        cellsInput.setText(autoFunctionsManager.getAutoCutCellsCsv());
        root.addView(cellsInput);

        CheckBox writeChat = new CheckBox(context);
        writeChat.setText("Выводить результат в чат");
        writeChat.setChecked(autoFunctionsManager.isAutoCutWriteChatEnabled());
        writeChat.setPadding(0, pad / 2, 0, 0);
        root.addView(writeChat);

        CheckBox cleanupEnabled = new CheckBox(context);
        cleanupEnabled.setText("После набора массы заходить в инвентарь для cleanup");
        cleanupEnabled.setChecked(autoFunctionsManager.isAutoCutCleanupEnabled());
        cleanupEnabled.setPadding(0, pad / 2, 0, 0);
        root.addView(cleanupEnabled);

        CheckBox cutByTimers = new CheckBox(context);
        cutByTimers.setText("Срезать по таймерам");
        cutByTimers.setChecked(autoFunctionsManager.isAutoCutCutByTimersEnabled());
        cutByTimers.setPadding(0, pad / 2, 0, 0);
        root.addView(cutByTimers);

        TextView herbsSummary = new TextView(context);
        herbsSummary.setText("Выбрано трав: " + autoFunctionsManager.getAutoCutSelectedHerbCount()
                + ". Откройте полноценный список по группам 1-11.");
        herbsSummary.setPadding(0, pad, 0, 0);
        root.addView(herbsSummary);

        Button herbListButton = new Button(context);
        herbListButton.setText("Список трав");
        herbListButton.setAllCaps(false);
        herbListButton.setTextColor(ContextCompat.getColor(context, R.color.colorOnPrimarySurface));
        herbListButton.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500));
        root.addView(herbListButton);

        Button sicklesButton = new Button(context);
        sicklesButton.setText("Серпы");
        sicklesButton.setAllCaps(false);
        root.addView(sicklesButton);

        Button shiftsButton = new Button(context);
        shiftsButton.setText("Смены трав");
        shiftsButton.setAllCaps(false);
        root.addView(shiftsButton);

        herbListButton.setOnClickListener(v -> showAutoCutHerbListDialog("Все"));
        sicklesButton.setOnClickListener(v -> showAutoCutSickleSettingsDialog());
        shiftsButton.setOnClickListener(v -> showAutoCutShiftSettingsDialog());

        new AlertDialog.Builder(context)
                .setTitle("Настройки Авто-Травника")
                .setView(scroll)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    autoFunctionsManager.setAutoCutCellsCsv(cellsInput.getText() == null ? "" : cellsInput.getText().toString());
                    autoFunctionsManager.setAutoCutWriteChatEnabled(writeChat.isChecked());
                    autoFunctionsManager.setAutoCutCleanupEnabled(cleanupEnabled.isChecked());
                    autoFunctionsManager.setAutoCutCutByTimersEnabled(cutByTimers.isChecked());
                    Toast.makeText(context, "Настройки авто-травника сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public void showAutoCutHerbListDialog(String initialGroup) {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 8);
        final String[] groups = new String[]{"Все", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "Не определено"};
        final String[] selectedGroup = new String[]{TextUtils.isEmpty(initialGroup) ? "Все" : initialGroup};

        List<AutoCutManager.AutoCutHerb> herbs = autoFunctionsManager.getAutoCutHerbs();
        LinkedHashSet<String> selectedKeys = new LinkedHashSet<>();
        for (AutoCutManager.AutoCutHerb herb : herbs) {
            if (herb.selected) {
                selectedKeys.add(herb.key);
            }
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        ScrollView groupScroll = new ScrollView(context);
        LinearLayout groupColumn = new LinearLayout(context);
        groupColumn.setOrientation(LinearLayout.VERTICAL);
        groupScroll.addView(groupColumn);
        root.addView(groupScroll, new LinearLayout.LayoutParams(dpToPx(112), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(pad, 0, 0, 0);
        root.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView header = new TextView(context);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        right.addView(header);

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button selectVisible = new Button(context);
        selectVisible.setText("Отметить");
        selectVisible.setAllCaps(false);
        Button unselectVisible = new Button(context);
        unselectVisible.setText("Снять");
        unselectVisible.setAllCaps(false);
        actionRow.addView(selectVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actionRow.addView(unselectVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        right.addView(actionRow);

        ScrollView herbScroll = new ScrollView(context);
        LinearLayout herbList = new LinearLayout(context);
        herbList.setOrientation(LinearLayout.VERTICAL);
        herbScroll.addView(herbList);
        right.addView(herbScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(420)));

        final Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            herbList.removeAllViews();
            header.setText("Группа: " + selectedGroup[0] + " | выбрано: " + selectedKeys.size());
            int visibleCount = 0;
            for (AutoCutManager.AutoCutHerb herb : autoFunctionsManager.getAutoCutHerbs()) {
                String group = TextUtils.isEmpty(herb.group) ? "Не определено" : herb.group.trim();
                if (!"Все".equals(selectedGroup[0]) && !group.equals(selectedGroup[0])) {
                    continue;
                }
                CheckBox box = new CheckBox(context);
                box.setText(herb.displayLabel());
                box.setChecked(selectedKeys.contains(herb.key));
                box.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) selectedKeys.add(herb.key);
                    else selectedKeys.remove(herb.key);
                    header.setText("Группа: " + selectedGroup[0] + " | выбрано: " + selectedKeys.size());
                });
                box.setOnLongClickListener(v -> {
                    showAutoCutHerbEditDialog(herb);
                    return true;
                });
                herbList.addView(box);
                visibleCount++;
            }
            if (visibleCount == 0) {
                TextView empty = new TextView(context);
                empty.setText("В этой группе пока нет трав.");
                empty.setPadding(0, pad, 0, pad);
                herbList.addView(empty);
            }
        };

        for (String group : groups) {
            Button groupButton = new Button(context);
            groupButton.setText(group.equals("Все") ? "Все" : ("Гр. " + group));
            groupButton.setAllCaps(false);
            groupButton.setOnClickListener(v -> {
                selectedGroup[0] = group;
                renderRef[0].run();
            });
            groupColumn.addView(groupButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        selectVisible.setOnClickListener(v -> {
            for (AutoCutManager.AutoCutHerb herb : autoFunctionsManager.getAutoCutHerbs()) {
                String group = TextUtils.isEmpty(herb.group) ? "Не определено" : herb.group.trim();
                if ("Все".equals(selectedGroup[0]) || group.equals(selectedGroup[0])) {
                    selectedKeys.add(herb.key);
                }
            }
            renderRef[0].run();
        });
        unselectVisible.setOnClickListener(v -> {
            for (AutoCutManager.AutoCutHerb herb : autoFunctionsManager.getAutoCutHerbs()) {
                String group = TextUtils.isEmpty(herb.group) ? "Не определено" : herb.group.trim();
                if ("Все".equals(selectedGroup[0]) || group.equals(selectedGroup[0])) {
                    selectedKeys.remove(herb.key);
                }
            }
            renderRef[0].run();
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Список трав")
                .setView(root)
                .setPositiveButton("Сохранить", (d, which) -> {
                    autoFunctionsManager.setAutoCutHerbSelections(selectedKeys);
                    Toast.makeText(context, "Список трав сохранён", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(d -> renderRef[0].run());
        dialog.show();
    }

    public void showAutoCutSickleSettingsDialog() {
        String[] sickles = autoFunctionsManager.getAutoCutAvailableSickleNames();
        List<String> enabled = autoFunctionsManager.getAutoCutEnabledSickleNames();
        boolean[] checked = new boolean[sickles.length];
        for (int i = 0; i < sickles.length; i++) {
            checked[i] = enabled.contains(sickles[i]);
        }
        new AlertDialog.Builder(context)
                .setTitle("Серпы Авто-Травника")
                .setMultiChoiceItems(sickles, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    LinkedHashSet<String> selected = new LinkedHashSet<>();
                    for (int i = 0; i < sickles.length; i++) {
                        if (checked[i]) {
                            selected.add(sickles[i]);
                        }
                    }
                    if (selected.isEmpty()) {
                        Toast.makeText(context, "Нужно выбрать хотя бы один серп", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    autoFunctionsManager.setAutoCutEnabledSickleNames(selected);
                    Toast.makeText(context, "Список серпов сохранён", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public void showAutoCutShiftSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView hint = new TextView(context);
        hint.setText("Одна смена на строку, формат 00:50-06:50. Можно добавить новую строку со своей сменой.");
        root.addView(hint);

        EditText shiftsInput = new EditText(context);
        shiftsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        shiftsInput.setSingleLine(false);
        shiftsInput.setMinLines(5);
        shiftsInput.setText(autoFunctionsManager.getAutoCutShiftScheduleText());
        root.addView(shiftsInput);

        Button resetButton = new Button(context);
        resetButton.setText("Сбросить к 00:50/06:50/12:50/18:50");
        resetButton.setAllCaps(false);
        root.addView(resetButton);
        resetButton.setOnClickListener(v -> shiftsInput.setText("00:50-06:50\n06:50-12:50\n12:50-18:50\n18:50-00:50"));

        new AlertDialog.Builder(context)
                .setTitle("Смены трав")
                .setView(root)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String value = shiftsInput.getText() == null ? "" : shiftsInput.getText().toString();
                    if (!autoFunctionsManager.setAutoCutShiftScheduleText(value)) {
                        Toast.makeText(context, "Не распознано ни одной смены", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(context, "Смены трав сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showAutoCutHerbEditDialog(AutoCutManager.AutoCutHerb herb) {
        if (herb == null) {
            return;
        }
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText(herb.name);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        EditText skillInput = new EditText(context);
        skillInput.setHint("Умение");
        skillInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        skillInput.setText(String.valueOf(herb.skill));
        root.addView(skillInput);

        EditText growthInput = new EditText(context);
        growthInput.setHint("Время роста, минут");
        growthInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        growthInput.setText(String.valueOf(herb.growthMinutes));
        root.addView(growthInput);

        EditText groupInput = new EditText(context);
        groupInput.setHint("Группа трав");
        groupInput.setInputType(InputType.TYPE_CLASS_TEXT);
        groupInput.setText(herb.group);
        root.addView(groupInput);

        new AlertDialog.Builder(context)
                .setTitle("Правка травы")
                .setView(root)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    int skill = ru.neverlands.anclient.utils.ParseUtils.parseIntInRange(skillInput.getText() == null ? "" : skillInput.getText().toString(), 0, 999, herb.skill);
                    int growth = ru.neverlands.anclient.utils.ParseUtils.parseIntInRange(growthInput.getText() == null ? "" : growthInput.getText().toString(), 1, 24 * 60, herb.growthMinutes);
                    String group = groupInput.getText() == null ? "" : groupInput.getText().toString().trim();
                    autoFunctionsManager.updateAutoCutHerbMeta(herb.key, skill, growth, group);
                    Toast.makeText(context, "Трава обновлена", Toast.LENGTH_SHORT).show();
                    showAutoCutSettingsDialog();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public void showAutoLumberjackSettingsDialog() {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView cellsTitle = new TextView(context);
        cellsTitle.setText("Клетки для поиска деревьев (через запятую)");
        root.addView(cellsTitle);

        EditText cellsInput = new EditText(context);
        cellsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        cellsInput.setSingleLine(false);
        cellsInput.setMinLines(2);
        cellsInput.setHint("Например: 12-494, 13-501");
        cellsInput.setText(autoFunctionsManager.getAutoLumberjackCellsCsv());
        root.addView(cellsInput);

        CheckBox writeChat = new CheckBox(context);
        writeChat.setText("Выводить результат в чат");
        writeChat.setChecked(autoFunctionsManager.isAutoLumberjackWriteChatEnabled());
        writeChat.setPadding(0, pad / 2, 0, 0);
        root.addView(writeChat);

        CheckBox cleanupEnabled = new CheckBox(context);
        cleanupEnabled.setText("После набора массы заходить в инвентарь для cleanup");
        cleanupEnabled.setChecked(autoFunctionsManager.isAutoLumberjackCleanupEnabled());
        cleanupEnabled.setPadding(0, pad / 2, 0, 0);
        root.addView(cleanupEnabled);

        CheckBox cutByTimers = new CheckBox(context);
        cutByTimers.setText("Спиливать по таймерам");
        cutByTimers.setChecked(autoFunctionsManager.isAutoLumberjackCutByTimersEnabled());
        cutByTimers.setPadding(0, pad / 2, 0, 0);
        root.addView(cutByTimers);

        TextView treesSummary = new TextView(context);
        treesSummary.setText("Выбрано деревьев: " + autoFunctionsManager.getAutoLumberjackSelectedTreeCount()
                + ". Откройте список деревьев по группам.");
        treesSummary.setPadding(0, pad, 0, 0);
        root.addView(treesSummary);

        Button treeListButton = new Button(context);
        treeListButton.setText("Список деревьев");
        treeListButton.setAllCaps(false);
        treeListButton.setTextColor(ContextCompat.getColor(context, R.color.colorOnPrimarySurface));
        treeListButton.setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500));
        root.addView(treeListButton);

        Button axesButton = new Button(context);
        axesButton.setText("Топоры");
        axesButton.setAllCaps(false);
        root.addView(axesButton);

        Button shiftsButton = new Button(context);
        shiftsButton.setText("Смены ресурсов");
        shiftsButton.setAllCaps(false);
        root.addView(shiftsButton);

        treeListButton.setOnClickListener(v -> showAutoLumberjackTreeListDialog("Все"));
        axesButton.setOnClickListener(v -> showAutoLumberjackAxeSettingsDialog());
        shiftsButton.setOnClickListener(v -> showAutoCutShiftSettingsDialog());

        new AlertDialog.Builder(context)
                .setTitle("Настройки Авто-Лесоруба")
                .setView(scroll)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    autoFunctionsManager.setAutoLumberjackCellsCsv(cellsInput.getText() == null ? "" : cellsInput.getText().toString());
                    autoFunctionsManager.setAutoLumberjackWriteChatEnabled(writeChat.isChecked());
                    autoFunctionsManager.setAutoLumberjackCleanupEnabled(cleanupEnabled.isChecked());
                    autoFunctionsManager.setAutoLumberjackCutByTimersEnabled(cutByTimers.isChecked());
                    Toast.makeText(context, "Настройки авто-лесоруба сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public void showAutoLumberjackTreeListDialog(String initialGroup) {
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 8);
        final String[] groups = new String[]{"Все", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "Не определено"};
        final String[] selectedGroup = new String[]{TextUtils.isEmpty(initialGroup) ? "Все" : initialGroup};

        List<AutoCutManager.AutoCutHerb> trees = autoFunctionsManager.getAutoLumberjackTrees();
        LinkedHashSet<String> selectedKeys = new LinkedHashSet<>();
        for (AutoCutManager.AutoCutHerb tree : trees) {
            if (tree.selected) {
                selectedKeys.add(tree.key);
            }
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        ScrollView groupScroll = new ScrollView(context);
        LinearLayout groupColumn = new LinearLayout(context);
        groupColumn.setOrientation(LinearLayout.VERTICAL);
        groupScroll.addView(groupColumn);
        root.addView(groupScroll, new LinearLayout.LayoutParams(dpToPx(112), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(pad, 0, 0, 0);
        root.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView header = new TextView(context);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        right.addView(header);

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button selectVisible = new Button(context);
        selectVisible.setText("Отметить");
        selectVisible.setAllCaps(false);
        Button unselectVisible = new Button(context);
        unselectVisible.setText("Снять");
        unselectVisible.setAllCaps(false);
        actionRow.addView(selectVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actionRow.addView(unselectVisible, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        right.addView(actionRow);

        ScrollView treeScroll = new ScrollView(context);
        LinearLayout treeList = new LinearLayout(context);
        treeList.setOrientation(LinearLayout.VERTICAL);
        treeScroll.addView(treeList);
        right.addView(treeScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(420)));

        final Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            treeList.removeAllViews();
            header.setText("Группа: " + selectedGroup[0] + " | выбрано: " + selectedKeys.size());
            int visibleCount = 0;
            for (AutoCutManager.AutoCutHerb tree : autoFunctionsManager.getAutoLumberjackTrees()) {
                String group = TextUtils.isEmpty(tree.group) ? "Не определено" : tree.group.trim();
                if (!"Все".equals(selectedGroup[0]) && !group.equals(selectedGroup[0])) {
                    continue;
                }
                CheckBox box = new CheckBox(context);
                box.setText(tree.displayLabel());
                box.setChecked(selectedKeys.contains(tree.key));
                box.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) selectedKeys.add(tree.key);
                    else selectedKeys.remove(tree.key);
                    header.setText("Группа: " + selectedGroup[0] + " | выбрано: " + selectedKeys.size());
                });
                box.setOnLongClickListener(v -> {
                    showAutoLumberjackTreeEditDialog(tree);
                    return true;
                });
                treeList.addView(box);
                visibleCount++;
            }
            if (visibleCount == 0) {
                TextView empty = new TextView(context);
                empty.setText("В этой группе пока нет деревьев.");
                empty.setPadding(0, pad, 0, pad);
                treeList.addView(empty);
            }
        };

        for (String group : groups) {
            Button groupButton = new Button(context);
            groupButton.setText(group.equals("Все") ? "Все" : ("Гр. " + group));
            groupButton.setAllCaps(false);
            groupButton.setOnClickListener(v -> {
                selectedGroup[0] = group;
                renderRef[0].run();
            });
            groupColumn.addView(groupButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        selectVisible.setOnClickListener(v -> {
            for (AutoCutManager.AutoCutHerb tree : autoFunctionsManager.getAutoLumberjackTrees()) {
                String group = TextUtils.isEmpty(tree.group) ? "Не определено" : tree.group.trim();
                if ("Все".equals(selectedGroup[0]) || group.equals(selectedGroup[0])) {
                    selectedKeys.add(tree.key);
                }
            }
            renderRef[0].run();
        });
        unselectVisible.setOnClickListener(v -> {
            for (AutoCutManager.AutoCutHerb tree : autoFunctionsManager.getAutoLumberjackTrees()) {
                String group = TextUtils.isEmpty(tree.group) ? "Не определено" : tree.group.trim();
                if ("Все".equals(selectedGroup[0]) || group.equals(selectedGroup[0])) {
                    selectedKeys.remove(tree.key);
                }
            }
            renderRef[0].run();
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Список деревьев")
                .setView(root)
                .setPositiveButton("Сохранить", (d, which) -> {
                    autoFunctionsManager.setAutoLumberjackTreeSelections(selectedKeys);
                    Toast.makeText(context, "Список деревьев сохранён", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(d -> renderRef[0].run());
        dialog.show();
    }

    public void showAutoLumberjackAxeSettingsDialog() {
        String[] axes = autoFunctionsManager.getAutoLumberjackAvailableAxeNames();
        List<String> enabled = autoFunctionsManager.getAutoLumberjackEnabledAxeNames();
        boolean[] checked = new boolean[axes.length];
        for (int i = 0; i < axes.length; i++) {
            checked[i] = enabled.contains(axes[i]);
        }
        new AlertDialog.Builder(context)
                .setTitle("Топоры Авто-Лесоруба")
                .setMultiChoiceItems(axes, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    LinkedHashSet<String> selected = new LinkedHashSet<>();
                    for (int i = 0; i < axes.length; i++) {
                        if (checked[i]) {
                            selected.add(axes[i]);
                        }
                    }
                    if (selected.isEmpty()) {
                        Toast.makeText(context, "Нужно выбрать хотя бы один топор", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    autoFunctionsManager.setAutoLumberjackEnabledAxeNames(selected);
                    Toast.makeText(context, "Список топоров сохранён", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showAutoLumberjackTreeEditDialog(AutoCutManager.AutoCutHerb tree) {
        if (tree == null) {
            return;
        }
        final int pad = (int) (context.getResources().getDisplayMetrics().density * 12);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText(tree.name);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        EditText skillInput = new EditText(context);
        skillInput.setHint("Умение лесоруба");
        skillInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        skillInput.setText(String.valueOf(tree.skill));
        root.addView(skillInput);

        EditText growthInput = new EditText(context);
        growthInput.setHint("Время роста, минут");
        growthInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        growthInput.setText(String.valueOf(tree.growthMinutes));
        root.addView(growthInput);

        EditText groupInput = new EditText(context);
        groupInput.setHint("Группа дерева");
        groupInput.setInputType(InputType.TYPE_CLASS_TEXT);
        groupInput.setText(tree.group);
        root.addView(groupInput);

        new AlertDialog.Builder(context)
                .setTitle("Правка дерева")
                .setView(root)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    int skill = ru.neverlands.anclient.utils.ParseUtils.parseIntInRange(skillInput.getText() == null ? "" : skillInput.getText().toString(), 0, 9999, tree.skill);
                    int growth = ru.neverlands.anclient.utils.ParseUtils.parseIntInRange(growthInput.getText() == null ? "" : growthInput.getText().toString(), 1, 24 * 60, tree.growthMinutes);
                    String group = groupInput.getText() == null ? "" : groupInput.getText().toString().trim();
                    autoFunctionsManager.updateAutoLumberjackTreeMeta(tree.key, skill, growth, group);
                    Toast.makeText(context, "Дерево обновлено", Toast.LENGTH_SHORT).show();
                    showAutoLumberjackSettingsDialog();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
