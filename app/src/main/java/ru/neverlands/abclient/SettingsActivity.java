package ru.neverlands.abclient;

import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import ru.neverlands.abclient.model.Prims;
import ru.neverlands.abclient.ui.AutoBoiSettingsFragment;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Активность настроек приложения.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Фрагмент настроек.
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final int MAP_SIZE_MIN = 3;
        private static final int MAP_SIZE_MAX = 31;
        private static final String[] FISH_HAND_OPTIONS = new String[]{
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
        private static final String[] FISH_PRIM_LABELS = new String[]{
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
        private static final int[] FISH_PRIM_FLAGS = new int[]{
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

        private static int normalizeMapSizeValue(int value) {
            if (value < MAP_SIZE_MIN) value = MAP_SIZE_MIN;
            if (value > MAP_SIZE_MAX) value = MAP_SIZE_MAX;
            if ((value & 1) == 0) value -= 1;
            if (value < MAP_SIZE_MIN) value = MAP_SIZE_MIN;
            return value;
        }

        private static int parseMapSizeComponent(String raw, int fallback) {
            int safeFallback = normalizeMapSizeValue(fallback);
            if (raw == null) {
                return safeFallback;
            }
            try {
                return normalizeMapSizeValue(Integer.parseInt(raw.trim()));
            } catch (Exception ignore) {
                return safeFallback;
            }
        }

        private static String formatMapSizeValue(int width, int height) {
            return width + "*" + height;
        }

        private static String buildMapSizeSummary(int width, int height) {
            return "Текущий размер: " + formatMapSizeValue(width, height) + " (нечетные 3..31)";
        }
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            
            // Настройка обработчиков изменения настроек
            setupPreferenceListeners();
        }
        
        /**
         * Настройка обработчиков изменения настроек
         */
        private void setupPreferenceListeners() {
            // Настройка запроса подтверждения при выходе
            SwitchPreferenceCompat doPromptExitPref = findPreference("do_prompt_exit");
            if (doPromptExitPref != null) {
                doPromptExitPref.setChecked(AppVars.DoPromptExit);
                doPromptExitPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.DoPromptExit = value;
                    if (AppVars.Profile != null) {
                        AppVars.Profile.DoPromptExit = value;
                        AppVars.Profile.save(requireContext());
                    }
                    return true;
                });
            }
            
            // Настройка HTTP-логирования
            SwitchPreferenceCompat showOverWarningPref = findPreference("show_over_warning");
            if (showOverWarningPref != null && AppVars.Profile != null) {
                showOverWarningPref.setChecked(AppVars.Profile.ShowOverWarning);
                showOverWarningPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.ShowOverWarning = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            // C# parity (`checkDoStopOnDig`): остановка Авто-Клада при появлении кнопки "Копать".
            SwitchPreferenceCompat doStopOnDigPref = findPreference("do_stop_on_dig");
            if (doStopOnDigPref != null && AppVars.Profile != null) {
                doStopOnDigPref.setChecked(AppVars.Profile.DoStopOnDig);
                doStopOnDigPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoStopOnDig = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            ListPreference mapScalePref = findPreference("map_scale_percent");
            if (mapScalePref != null && AppVars.Profile != null) {
                int currentScale = AppVars.Profile.MapBigScale;
                if (currentScale < 50) currentScale = 50;
                if (currentScale > 100) currentScale = 100;
                if (!(currentScale == 50 || currentScale == 60 || currentScale == 70
                        || currentScale == 80 || currentScale == 90 || currentScale == 100)) {
                    currentScale = (currentScale <= 75) ? 70 : 80;
                    AppVars.Profile.MapBigScale = currentScale;
                    AppVars.Profile.save(requireContext());
                }
                String currentScaleStr = String.valueOf(currentScale);
                mapScalePref.setValue(currentScaleStr);
                mapScalePref.setSummaryProvider(preference -> {
                    if (!(preference instanceof ListPreference)) {
                        return "";
                    }
                    CharSequence entry = ((ListPreference) preference).getEntry();
                    return entry == null ? "" : entry;
                });
                mapScalePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value;
                    try {
                        value = Integer.parseInt(String.valueOf(newValue));
                    } catch (Exception ignore) {
                        value = 80;
                    }
                    if (value < 50) value = 50;
                    if (value > 100) value = 100;
                    AppVars.Profile.MapBigScale = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            // Настройка C#-флага `RazdChatReport` (UI: checkboxRazdChatReport).
            //
            // Зависимости:
            // - ключ `show_razd_chat_report` в `root_preferences.xml`;
            // - поле профиля `UserConfig.RazdChatReport` (load/save XML профиля);
            // - ветка `MainPhp.mainPhpGetSkinRes`, где этот флаг управляет отправкой
            //   системного сообщения "Результат разделки" в чат.
            Preference mapSizePref = findPreference("map_size_cells");
            if (mapSizePref != null && AppVars.Profile != null) {
                int normalizedWidth = normalizeMapSizeValue(AppVars.Profile.MapBigWidth);
                int normalizedHeight = normalizeMapSizeValue(AppVars.Profile.MapBigHeight);
                if (normalizedWidth != AppVars.Profile.MapBigWidth || normalizedHeight != AppVars.Profile.MapBigHeight) {
                    AppVars.Profile.MapBigWidth = normalizedWidth;
                    AppVars.Profile.MapBigHeight = normalizedHeight;
                    AppVars.Profile.save(requireContext());
                }
                mapSizePref.setSummary(buildMapSizeSummary(normalizedWidth, normalizedHeight));
                mapSizePref.setOnPreferenceClickListener(preference -> {
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    int pad = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
                    row.setPadding(pad, pad, pad, 0);

                    EditText xInput = new EditText(requireContext());
                    xInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    xInput.setSingleLine(true);
                    xInput.setText(String.valueOf(AppVars.Profile.MapBigWidth));
                    xInput.setSelection(xInput.getText().length());
                    LinearLayout.LayoutParams xParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    row.addView(xInput, xParams);

                    TextView sep = new TextView(requireContext());
                    sep.setText("  X  ");
                    sep.setTextSize(18f);
                    row.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    EditText yInput = new EditText(requireContext());
                    yInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    yInput.setSingleLine(true);
                    yInput.setText(String.valueOf(AppVars.Profile.MapBigHeight));
                    yInput.setSelection(yInput.getText().length());
                    LinearLayout.LayoutParams yParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    row.addView(yInput, yParams);

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Размер карты")
                            .setView(row)
                            .setPositiveButton("Сохранить", (dialog, which) -> {
                                int width = parseMapSizeComponent(xInput.getText().toString(), AppVars.Profile.MapBigWidth);
                                int height = parseMapSizeComponent(yInput.getText().toString(), AppVars.Profile.MapBigHeight);
                                AppVars.Profile.MapBigWidth = width;
                                AppVars.Profile.MapBigHeight = height;
                                AppVars.Profile.save(requireContext());
                                preference.setSummary(buildMapSizeSummary(width, height));
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                    return true;
                });
            }

            SwitchPreferenceCompat razdChatReportPref = findPreference("show_razd_chat_report");
            if (razdChatReportPref != null && AppVars.Profile != null) {
                razdChatReportPref.setChecked(AppVars.Profile.RazdChatReport);
                razdChatReportPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.RazdChatReport = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            // Главный флаг авто-питья блажа по усталости (C# `DoAutoDrinkBlaz`).
            //
            // Зависимости:
            // - ключ `do_auto_drink_blaz` в `root_preferences.xml`;
            // - поле `UserConfig.DoAutoDrinkBlaz` и XML-тег `<autodrinkblaz do="...">`;
            // - runtime-ветка авто-рыбалки/общей логики, где проверяется разрешение
            //   на автоматическое употребление блажа.
            SwitchPreferenceCompat doAutoDrinkBlazPref = findPreference("do_auto_drink_blaz");
            if (doAutoDrinkBlazPref != null && AppVars.Profile != null) {
                doAutoDrinkBlazPref.setChecked(AppVars.Profile.DoAutoDrinkBlaz);
                doAutoDrinkBlazPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoAutoDrinkBlaz = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            // Порог усталости для авто-питья блажа (C# `AutoDrinkBlazTied`).
            //
            // Зависимости:
            // - ключ `auto_drink_blaz_tied` (зависимый от `do_auto_drink_blaz`);
            // - поле `UserConfig.AutoDrinkBlazTied`;
            // - сериализация в `<autodrinkblaz tied="...">`.
            //
            // Правило валидации:
            // - принимаем только диапазон 0..100, иначе приводим к границам;
            // - при нечисловом вводе откатываемся к C#-совместимому дефолту 84.
            EditTextPreference autoDrinkBlazTiedPref = findPreference("auto_drink_blaz_tied");
            if (autoDrinkBlazTiedPref != null && AppVars.Profile != null) {
                autoDrinkBlazTiedPref.setText(String.valueOf(AppVars.Profile.AutoDrinkBlazTied));
                autoDrinkBlazTiedPref.setSummary(String.valueOf(AppVars.Profile.AutoDrinkBlazTied));
                autoDrinkBlazTiedPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String valueRaw = String.valueOf(newValue).trim();
                    int value;
                    try {
                        value = Integer.parseInt(valueRaw);
                    } catch (Exception ignore) {
                        value = 84;
                    }
                    if (value < 0) value = 0;
                    if (value > 100) value = 100;
                    AppVars.Profile.AutoDrinkBlazTied = value;
                    AppVars.Profile.save(requireContext());
                    autoDrinkBlazTiedPref.setText(String.valueOf(value));
                    preference.setSummary(String.valueOf(value));
                    return false;
                });
            }

            // Порядок поиска типа блажа (C# `AutoDrinkBlazOrder`).
            //
            // Зависимости:
            // - ключ `auto_drink_blaz_order` + массивы `auto_drink_blaz_order_*`;
            // - поле `UserConfig.AutoDrinkBlazOrder`;
            // - XML-тег `<autodrinkblazorder>` при сохранении профиля.
            //
            // Допустимые значения:
            // - 0: сначала зелье, затем эликсир;
            // - 1: сначала эликсир, затем зелье.
            ListPreference autoDrinkBlazOrderPref = findPreference("auto_drink_blaz_order");
            if (autoDrinkBlazOrderPref != null && AppVars.Profile != null) {
                String current = String.valueOf(Math.max(0, Math.min(1, AppVars.Profile.AutoDrinkBlazOrder)));
                autoDrinkBlazOrderPref.setValue(current);
                int idx = autoDrinkBlazOrderPref.findIndexOfValue(current);
                if (idx >= 0 && autoDrinkBlazOrderPref.getEntries() != null && idx < autoDrinkBlazOrderPref.getEntries().length) {
                    autoDrinkBlazOrderPref.setSummary(autoDrinkBlazOrderPref.getEntries()[idx]);
                }
                autoDrinkBlazOrderPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String valueRaw = String.valueOf(newValue);
                    int value;
                    try {
                        value = Integer.parseInt(valueRaw);
                    } catch (Exception ignore) {
                        value = 0;
                    }
                    if (value < 0 || value > 1) value = 0;
                    AppVars.Profile.AutoDrinkBlazOrder = value;
                    AppVars.Profile.save(requireContext());
                    int valueIndex = autoDrinkBlazOrderPref.findIndexOfValue(String.valueOf(value));
                    if (valueIndex >= 0 && autoDrinkBlazOrderPref.getEntries() != null
                            && valueIndex < autoDrinkBlazOrderPref.getEntries().length) {
                        preference.setSummary(autoDrinkBlazOrderPref.getEntries()[valueIndex]);
                    }
                    return true;
                });
            }

            // Флаг группировки одинаковых предметов в инвентаре (C# `DoInvPack`).
            //
            // Зависимости:
            // - ключ `do_inv_pack` в общих настройках;
            // - поле `UserConfig.DoInvPack`;
            // - postfilter-логика группировки инвентаря в `MainPhp`.
            SwitchPreferenceCompat doInvPackPref = findPreference("do_inv_pack");
            if (doInvPackPref != null && AppVars.Profile != null) {
                doInvPackPref.setChecked(AppVars.Profile.DoInvPack);
                doInvPackPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoInvPack = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            // Режим группировки с учетом/без учета долговечности (C# `DoInvPackDolg`).
            //
            // Зависимости:
            // - ключ `do_inv_pack_dolg`;
            // - поле `UserConfig.DoInvPackDolg`;
            // - алгоритм ключа группировки и суммирования долговечности в инвентарном парсере.
            SwitchPreferenceCompat doInvPackDolgPref = findPreference("do_inv_pack_dolg");
            if (doInvPackDolgPref != null && AppVars.Profile != null) {
                doInvPackDolgPref.setChecked(AppVars.Profile.DoInvPackDolg);
                doInvPackDolgPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoInvPackDolg = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            // Флаг сортировки/нормализации вывода инвентаря (C# `DoInvSort`).
            //
            // Зависимости:
            // - ключ `do_inv_sort`;
            // - поле `UserConfig.DoInvSort`;
            // - порядок отображения сгруппированных записей в postfilter-ветке инвентаря.
            SwitchPreferenceCompat doInvSortPref = findPreference("do_inv_sort");
            if (doInvSortPref != null && AppVars.Profile != null) {
                doInvSortPref.setChecked(AppVars.Profile.DoInvSort);
                doInvSortPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoInvSort = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            SwitchPreferenceCompat doHttpLogPref = findPreference("do_http_log");
            if (doHttpLogPref != null && AppVars.Profile != null) {
                doHttpLogPref.setChecked(AppVars.Profile.DoHttpLog);
                doHttpLogPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoHttpLog = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка текстового логирования
            SwitchPreferenceCompat doTexLogPref = findPreference("do_tex_log");
            if (doTexLogPref != null && AppVars.Profile != null) {
                doTexLogPref.setChecked(AppVars.Profile.DoTexLog);
                doTexLogPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoTexLog = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка показа производительности
            SwitchPreferenceCompat showPerformancePref = findPreference("show_performance");
            if (showPerformancePref != null && AppVars.Profile != null) {
                showPerformancePref.setChecked(AppVars.Profile.ShowPerformance);
                showPerformancePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.ShowPerformance = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Раздел "Автоматизация": только кнопки-переходы в окна настроек (без переключателей).
            bindAutomationSettingsEntry("auto_fight_settings", this::openAutoBoiSettingsDialog);
            bindAutomationSettingsEntry("auto_fish_settings", this::showAutoFishSettingsDialog);
            bindAutomationSettingsEntry("auto_herb_settings", this::showAutoFunctionsSettingsDialog);
            bindAutomationSettingsEntry("auto_mine_settings", this::showAutoFunctionsSettingsDialog);
            bindAutomationSettingsEntry("auto_tree_settings", this::showAutoFunctionsSettingsDialog);
            bindAutomationSettingsEntry("auto_dig_settings", this::showAutoFunctionsSettingsDialog);
            bindAutomationSettingsEntry("auto_torg_settings", this::showAutoFunctionsSettingsDialog);
            
            // Настройка обновления кэша
            SwitchPreferenceCompat cacheRefreshPref = findPreference("cache_refresh");
            if (cacheRefreshPref != null) {
                cacheRefreshPref.setChecked(AppVars.CacheRefresh);
                cacheRefreshPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.CacheRefresh = value;
                    return true;
                });
            }
            
            // Очистка кэша
            Preference clearCachePref = findPreference("clear_cache");
            if (clearCachePref != null) {
                clearCachePref.setOnPreferenceClickListener(preference -> {
                    ru.neverlands.abclient.proxy.Cache.clear();
                    return true;
                });
            }
            
            // Очистка логов
            Preference clearLogsPref = findPreference("clear_logs");
            if (clearLogsPref != null) {
                clearLogsPref.setOnPreferenceClickListener(preference -> {
                    ru.neverlands.abclient.utils.AppLogger.clearLogs();
                    return true;
                });
            }
        }

        private void bindAutomationSettingsEntry(String key, Runnable action) {
            Preference pref = findPreference(key);
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    action.run();
                    return true;
                });
            }
        }

        private void openAutoBoiSettingsDialog() {
            AutoBoiSettingsFragment fragment = new AutoBoiSettingsFragment();
            fragment.show(getParentFragmentManager(), "autoboi_settings_from_general");
        }

        private void showAutoFunctionsSettingsDialog() {
            Toast.makeText(
                    requireContext(),
                    "Настройки этой авто-функции откроются в следующем этапе.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        private void showAutoFishSettingsDialog() {
            if (AppVars.Profile == null) {
                Toast.makeText(requireContext(), "Профиль не загружен", Toast.LENGTH_SHORT).show();
                return;
            }

            final int pad = (int) (requireContext().getResources().getDisplayMetrics().density * 12);
            ScrollView scroll = new ScrollView(requireContext());
            LinearLayout root = new LinearLayout(requireContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad, pad, pad);
            scroll.addView(root);

            CheckBox autoWear = new CheckBox(requireContext());
            autoWear.setText("Автонадевание снастей");
            autoWear.setChecked(AppVars.Profile.FishAutoWear);
            root.addView(autoWear);

            TextView hand1Title = new TextView(requireContext());
            hand1Title.setText("Рука 1");
            hand1Title.setPadding(0, pad, 0, 0);
            root.addView(hand1Title);

            Spinner hand1Spinner = new Spinner(requireContext());
            ArrayAdapter<String> handAdapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    FISH_HAND_OPTIONS
            );
            handAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            hand1Spinner.setAdapter(handAdapter);
            int hand1Index = indexOfFishHand(AppVars.Profile.FishHandOne);
            hand1Spinner.setSelection(hand1Index >= 0 ? hand1Index : 1);
            root.addView(hand1Spinner);

            TextView hand2Title = new TextView(requireContext());
            hand2Title.setText("Рука 2");
            hand2Title.setPadding(0, pad, 0, 0);
            root.addView(hand2Title);

            Spinner hand2Spinner = new Spinner(requireContext());
            hand2Spinner.setAdapter(handAdapter);
            int hand2Index = indexOfFishHand(AppVars.Profile.FishHandTwo);
            hand2Spinner.setSelection(hand2Index >= 0 ? hand2Index : 0);
            root.addView(hand2Spinner);

            TextView autoDrinkTitle = new TextView(requireContext());
            autoDrinkTitle.setText("Автопитье");
            autoDrinkTitle.setPadding(0, pad, 0, 0);
            root.addView(autoDrinkTitle);

            LinearLayout tiedRow = new LinearLayout(requireContext());
            tiedRow.setOrientation(LinearLayout.HORIZONTAL);
            tiedRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView tiedLabel = new TextView(requireContext());
            tiedLabel.setText("Глоток, если усталка больше");
            tiedRow.addView(tiedLabel);
            EditText tiedHighInput = new EditText(requireContext());
            tiedHighInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            tiedHighInput.setText(String.valueOf(Math.max(0, Math.min(99, AppVars.Profile.FishTiedHigh))));
            LinearLayout.LayoutParams tiedParams = new LinearLayout.LayoutParams(
                    (int) (requireContext().getResources().getDisplayMetrics().density * 56),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tiedParams.leftMargin = (int) (requireContext().getResources().getDisplayMetrics().density * 8);
            tiedHighInput.setLayoutParams(tiedParams);
            tiedRow.addView(tiedHighInput);
            root.addView(tiedRow);

            CheckBox tiedZero = new CheckBox(requireContext());
            tiedZero.setText("Пить до нуля усталости");
            tiedZero.setChecked(AppVars.Profile.FishTiedZero);
            root.addView(tiedZero);

            CheckBox fishDrinkBliss = new CheckBox(requireContext());
            fishDrinkBliss.setText("Пить Эликсир Блаженства, если усталка больше порога");
            fishDrinkBliss.setChecked(AppVars.Profile.FishDrinkBliss);
            root.addView(fishDrinkBliss);

            CheckBox stopOverWeight = new CheckBox(requireContext());
            stopOverWeight.setText("Прекращать рыбалку при перегрузе");
            stopOverWeight.setChecked(AppVars.Profile.FishStopOverWeight);
            root.addView(stopOverWeight);

            CheckBox fishChatReport = new CheckBox(requireContext());
            fishChatReport.setText("Выводить результаты лова в чат");
            fishChatReport.setChecked(AppVars.Profile.FishChatReport);
            root.addView(fishChatReport);

            CheckBox fishChatReportColor = new CheckBox(requireContext());
            fishChatReportColor.setText("Выводить результаты лова в приват");
            fishChatReportColor.setChecked(AppVars.Profile.FishChatReportColor);
            root.addView(fishChatReportColor);

            TextView primsTitle = new TextView(requireContext());
            primsTitle.setText("Приманки");
            primsTitle.setPadding(0, pad, 0, 0);
            root.addView(primsTitle);

            final CheckBox[] primChecks = new CheckBox[FISH_PRIM_FLAGS.length];
            for (int i = 0; i < FISH_PRIM_FLAGS.length; i++) {
                CheckBox cb = new CheckBox(requireContext());
                cb.setText(FISH_PRIM_LABELS[i]);
                cb.setChecked((AppVars.Profile.FishEnabledPrims & FISH_PRIM_FLAGS[i]) != 0);
                primChecks[i] = cb;
                root.addView(cb);
            }

            new AlertDialog.Builder(requireContext())
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
                        AppVars.Profile.save(requireContext());
                        Toast.makeText(requireContext(), "Настройки авто-рыбалки сохранены", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Отмена", null)
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
    }
}
