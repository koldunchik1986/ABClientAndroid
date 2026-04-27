package ru.neverlands.anclient;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import ru.neverlands.anclient.manager.RoomManager;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.LogcatFileRecorder;
import ru.neverlands.anclient.utils.ThemeModeManager;

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
        private static final int MAP_FONT_SIZE_MIN = 6;
        private static final int MAP_FONT_SIZE_MAX = 24;
        private static final int FRAME_FONT_SCALE_MIN = 50;
        private static final int FRAME_FONT_SCALE_MAX = 200;
        private static final int[] FRAME_FONT_SCALE_LIST_VALUES = {
                50, 60, 70, 80, 90, 100, 110, 125, 150, 175, 200
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

        private static int normalizeMapScaleValue(int value) {
            if (value < 50) return 50;
            if (value > 150) return 150;
            return value;
        }

        private static int parseMapScaleValue(String raw, int fallback) {
            int safeFallback = normalizeMapScaleValue(fallback);
            if (raw == null) {
                return safeFallback;
            }
            try {
                return normalizeMapScaleValue(Integer.parseInt(raw.trim()));
            } catch (Exception ignore) {
                return safeFallback;
            }
        }

        private static String buildMapScaleSummary(int value) {
            return value + "%";
        }

        private static int normalizeFrameFontScaleValue(int value) {
            if (value < FRAME_FONT_SCALE_MIN) return FRAME_FONT_SCALE_MIN;
            if (value > FRAME_FONT_SCALE_MAX) return FRAME_FONT_SCALE_MAX;
            return value;
        }

        private static int parseFrameFontScaleValue(String raw, int fallback) {
            int safeFallback = normalizeFrameFontScaleValue(fallback);
            if (raw == null) {
                return safeFallback;
            }
            try {
                return normalizeFrameFontScaleValue(Integer.parseInt(raw.trim()));
            } catch (Exception ignore) {
                return safeFallback;
            }
        }

        private static int snapFrameFontScaleListValue(int value) {
            int normalized = normalizeFrameFontScaleValue(value);
            int best = FRAME_FONT_SCALE_LIST_VALUES[0];
            int bestDelta = Math.abs(normalized - best);
            for (int candidate : FRAME_FONT_SCALE_LIST_VALUES) {
                int delta = Math.abs(normalized - candidate);
                if (delta < bestDelta) {
                    best = candidate;
                    bestDelta = delta;
                }
            }
            return best;
        }

        private static int normalizeMapFontSizeValue(int value) {
            if (value < MAP_FONT_SIZE_MIN) return MAP_FONT_SIZE_MIN;
            if (value > MAP_FONT_SIZE_MAX) return MAP_FONT_SIZE_MAX;
            return value;
        }

        private static int parseMapFontSizeValue(String raw, int fallback) {
            int safeFallback = normalizeMapFontSizeValue(fallback);
            if (raw == null) {
                return safeFallback;
            }
            try {
                return normalizeMapFontSizeValue(Integer.parseInt(raw.trim()));
            } catch (Exception ignore) {
                return safeFallback;
            }
        }

        private static String buildMapFontSizeSummary(int sizePx) {
            return "Текущий размер: " + sizePx + " px";
        }
        private static int normalizeMapCellCheckTimeoutMs(int value) {
            if (value < 0) return 0;
            if (value > 5000) return 5000;
            return value;
        }

        private static int parseMapCellCheckTimeoutMs(String raw, int fallback) {
            int safeFallback = normalizeMapCellCheckTimeoutMs(fallback);
            if (raw == null) {
                return safeFallback;
            }
            try {
                return normalizeMapCellCheckTimeoutMs(Integer.parseInt(raw.trim()));
            } catch (Exception ignore) {
                return safeFallback;
            }
        }

        private static String buildMapCellCheckTimeoutSummary(int timeoutMs) {
            return "Текущая задержка между шагами: " + timeoutMs + " мс";
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
            SwitchPreferenceCompat forceDarkThemePref = findPreference(ThemeModeManager.KEY_FORCE_DARK_THEME);
            if (forceDarkThemePref != null) {
                forceDarkThemePref.setChecked(ThemeModeManager.isForceDarkEnabled(requireContext()));
                forceDarkThemePref.setSummary(buildThemeModeSummary(forceDarkThemePref.isChecked()));
                forceDarkThemePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    ThemeModeManager.setForceDarkEnabled(requireContext(), value);
                    preference.setSummary(buildThemeModeSummary(value));
                    return true;
                });
            }

            ListPreference frameFontScalePref = findPreference("frame_font_scale_percent");
            if (frameFontScalePref != null && AppVars.Profile != null) {
                int currentScale = snapFrameFontScaleListValue(AppVars.Profile.FrameFontScale);
                if (currentScale != AppVars.Profile.FrameFontScale) {
                    AppVars.Profile.FrameFontScale = currentScale;
                    AppVars.Profile.save(requireContext());
                }
                frameFontScalePref.setValue(String.valueOf(currentScale));
                frameFontScalePref.setSummaryProvider(preference -> {
                    if (!(preference instanceof ListPreference)) {
                        return "";
                    }
                    CharSequence entry = ((ListPreference) preference).getEntry();
                    return entry == null ? "" : entry;
                });
                frameFontScalePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = snapFrameFontScaleListValue(parseFrameFontScaleValue(
                            String.valueOf(newValue),
                            AppVars.Profile.FrameFontScale
                    ));
                    AppVars.Profile.FrameFontScale = value;
                    AppVars.Profile.save(requireContext());
                    frameFontScalePref.setValue(String.valueOf(value));
                    return false;
                });
            }

            ListPreference chatFrameFontScalePref = findPreference("chat_frame_font_scale_percent");
            if (chatFrameFontScalePref != null && AppVars.Profile != null) {
                int currentScale = snapFrameFontScaleListValue(AppVars.Profile.ChatFrameFontScale);
                if (currentScale != AppVars.Profile.ChatFrameFontScale) {
                    AppVars.Profile.ChatFrameFontScale = currentScale;
                    AppVars.Profile.save(requireContext());
                }
                chatFrameFontScalePref.setValue(String.valueOf(currentScale));
                chatFrameFontScalePref.setSummaryProvider(preference -> {
                    if (!(preference instanceof ListPreference)) {
                        return "";
                    }
                    CharSequence entry = ((ListPreference) preference).getEntry();
                    return entry == null ? "" : entry;
                });
                chatFrameFontScalePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = snapFrameFontScaleListValue(parseFrameFontScaleValue(
                            String.valueOf(newValue),
                            AppVars.Profile.ChatFrameFontScale
                    ));
                    AppVars.Profile.ChatFrameFontScale = value;
                    AppVars.Profile.save(requireContext());
                    chatFrameFontScalePref.setValue(String.valueOf(value));
                    return false;
                });
            }

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
                int currentScale = normalizeMapScaleValue(AppVars.Profile.MapBigScale);
                if (!(currentScale == 50 || currentScale == 60 || currentScale == 70
                        || currentScale == 80 || currentScale == 90 || currentScale == 100
                        || currentScale == 125 || currentScale == 150)) {
                    currentScale = (currentScale <= 75) ? 70 : (currentScale <= 112 ? 100 : 125);
                    AppVars.Profile.MapBigScale = currentScale;
                    AppVars.Profile.save(requireContext());
                }
                mapScalePref.setValue(String.valueOf(currentScale));
                mapScalePref.setSummaryProvider(preference -> {
                    if (!(preference instanceof ListPreference)) {
                        return "";
                    }
                    CharSequence entry = ((ListPreference) preference).getEntry();
                    return entry == null ? "" : entry;
                });
                mapScalePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = parseMapScaleValue(String.valueOf(newValue), AppVars.Profile.MapBigScale);
                    AppVars.Profile.MapBigScale = value;
                    AppVars.Profile.save(requireContext());
                    mapScalePref.setValue(String.valueOf(value));
                    return false;
                });
            }

            Preference mapFontSizePref = findPreference("map_font_size");
            if (mapFontSizePref != null && AppVars.Profile != null) {
                int currentFontSize = normalizeMapFontSizeValue(AppVars.Profile.MapCellFontSize);
                if (currentFontSize != AppVars.Profile.MapCellFontSize) {
                    AppVars.Profile.MapCellFontSize = currentFontSize;
                    AppVars.Profile.save(requireContext());
                }
                mapFontSizePref.setSummary(buildMapFontSizeSummary(currentFontSize));
                mapFontSizePref.setOnPreferenceClickListener(preference -> {
                    EditText fontInput = new EditText(requireContext());
                    fontInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    fontInput.setSingleLine(true);
                    fontInput.setText(String.valueOf(AppVars.Profile.MapCellFontSize));
                    fontInput.setSelection(fontInput.getText().length());

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Размер шрифта карты")
                            .setView(fontInput)
                            .setPositiveButton("Сохранить", (dialog, which) -> {
                                int value = parseMapFontSizeValue(
                                        fontInput.getText() != null ? fontInput.getText().toString() : null,
                                        AppVars.Profile.MapCellFontSize
                                );
                                AppVars.Profile.MapCellFontSize = value;
                                AppVars.Profile.save(requireContext());
                                preference.setSummary(buildMapFontSizeSummary(value));
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
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
            SwitchPreferenceCompat mapRebuildFromPinfoPref = findPreference("map_rebuild_from_pinfo");
            if (mapRebuildFromPinfoPref != null && AppVars.Profile != null) {
                mapRebuildFromPinfoPref.setChecked(AppVars.Profile.MapRebuildFromPinfo);
                mapRebuildFromPinfoPref.setSummary("Синхронизировать название/регион клетки по ch.php и pinfo (может замедлять авто-навигацию; использует задержку (мс) между шагами)");
                mapRebuildFromPinfoPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.MapRebuildFromPinfo = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }

            EditTextPreference mapCellCheckTimeoutPref = findPreference("map_cell_check_timeout_ms");
            if (mapCellCheckTimeoutPref != null && AppVars.Profile != null) {
                int currentTimeout = normalizeMapCellCheckTimeoutMs(AppVars.Profile.MapCellCheckTimeoutMs);
                if (currentTimeout != AppVars.Profile.MapCellCheckTimeoutMs) {
                    AppVars.Profile.MapCellCheckTimeoutMs = currentTimeout;
                    AppVars.Profile.save(requireContext());
                }
                mapCellCheckTimeoutPref.setText(String.valueOf(currentTimeout));
                mapCellCheckTimeoutPref.setSummary(buildMapCellCheckTimeoutSummary(currentTimeout));
                mapCellCheckTimeoutPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int timeoutMs = parseMapCellCheckTimeoutMs(String.valueOf(newValue), AppVars.Profile.MapCellCheckTimeoutMs);
                    AppVars.Profile.MapCellCheckTimeoutMs = timeoutMs;
                    AppVars.Profile.save(requireContext());
                    mapCellCheckTimeoutPref.setText(String.valueOf(timeoutMs));
                    preference.setSummary(buildMapCellCheckTimeoutSummary(timeoutMs));
                    return false;
                });
            }

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

            ListPreference mapNavigatorScalePref = findPreference("map_navigator_scale_percent");
            if (mapNavigatorScalePref != null && AppVars.Profile != null) {
                int currentScale = normalizeMapScaleValue(AppVars.Profile.MapMiniScale);
                if (!(currentScale == 50 || currentScale == 60 || currentScale == 70
                        || currentScale == 80 || currentScale == 90 || currentScale == 100
                        || currentScale == 125 || currentScale == 150)) {
                    currentScale = (currentScale <= 75) ? 70 : (currentScale <= 112 ? 100 : 125);
                    AppVars.Profile.MapMiniScale = currentScale;
                    AppVars.Profile.save(requireContext());
                }
                mapNavigatorScalePref.setValue(String.valueOf(currentScale));
                mapNavigatorScalePref.setSummaryProvider(preference -> {
                    if (!(preference instanceof ListPreference)) {
                        return "";
                    }
                    CharSequence entry = ((ListPreference) preference).getEntry();
                    return entry == null ? "" : entry;
                });
                mapNavigatorScalePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = parseMapScaleValue(String.valueOf(newValue), AppVars.Profile.MapMiniScale);
                    AppVars.Profile.MapMiniScale = value;
                    AppVars.Profile.save(requireContext());
                    mapNavigatorScalePref.setValue(String.valueOf(value));
                    return false;
                });
            }

            Preference mapNavigatorSizePref = findPreference("map_navigator_size_cells");
            if (mapNavigatorSizePref != null && AppVars.Profile != null) {
                int normalizedWidth = normalizeMapSizeValue(AppVars.Profile.MapMiniWidth);
                int normalizedHeight = normalizeMapSizeValue(AppVars.Profile.MapMiniHeight);
                if (normalizedWidth != AppVars.Profile.MapMiniWidth || normalizedHeight != AppVars.Profile.MapMiniHeight) {
                    AppVars.Profile.MapMiniWidth = normalizedWidth;
                    AppVars.Profile.MapMiniHeight = normalizedHeight;
                    AppVars.Profile.save(requireContext());
                }
                mapNavigatorSizePref.setSummary(buildMapSizeSummary(normalizedWidth, normalizedHeight));
                mapNavigatorSizePref.setOnPreferenceClickListener(preference -> {
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    int pad = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
                    row.setPadding(pad, pad, pad, 0);

                    EditText xInput = new EditText(requireContext());
                    xInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    xInput.setSingleLine(true);
                    xInput.setText(String.valueOf(AppVars.Profile.MapMiniWidth));
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
                    yInput.setText(String.valueOf(AppVars.Profile.MapMiniHeight));
                    yInput.setSelection(yInput.getText().length());
                    LinearLayout.LayoutParams yParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    row.addView(yInput, yParams);

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Размер карты навигатора")
                            .setView(row)
                            .setPositiveButton("Сохранить", (dialog, which) -> {
                                int width = parseMapSizeComponent(xInput.getText().toString(), AppVars.Profile.MapMiniWidth);
                                int height = parseMapSizeComponent(yInput.getText().toString(), AppVars.Profile.MapMiniHeight);
                                AppVars.Profile.MapMiniWidth = width;
                                AppVars.Profile.MapMiniHeight = height;
                                AppVars.Profile.save(requireContext());
                                preference.setSummary(buildMapSizeSummary(width, height));
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                    return true;
                });
            }

            Preference mapNavigatorFontSizePref = findPreference("map_navigator_font_size");
            if (mapNavigatorFontSizePref != null && AppVars.Profile != null) {
                int currentFontSize = normalizeMapFontSizeValue(AppVars.Profile.MapMiniCellFontSize);
                if (currentFontSize != AppVars.Profile.MapMiniCellFontSize) {
                    AppVars.Profile.MapMiniCellFontSize = currentFontSize;
                    AppVars.Profile.save(requireContext());
                }
                mapNavigatorFontSizePref.setSummary(buildMapFontSizeSummary(currentFontSize));
                mapNavigatorFontSizePref.setOnPreferenceClickListener(preference -> {
                    EditText fontInput = new EditText(requireContext());
                    fontInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    fontInput.setSingleLine(true);
                    fontInput.setText(String.valueOf(AppVars.Profile.MapMiniCellFontSize));
                    fontInput.setSelection(fontInput.getText().length());

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Размер шрифта карты навигатора")
                            .setView(fontInput)
                            .setPositiveButton("Сохранить", (dialog, which) -> {
                                int value = parseMapFontSizeValue(
                                        fontInput.getText() != null ? fontInput.getText().toString() : null,
                                        AppVars.Profile.MapMiniCellFontSize
                                );
                                AppVars.Profile.MapMiniCellFontSize = value;
                                AppVars.Profile.save(requireContext());
                                preference.setSummary(buildMapFontSizeSummary(value));
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                    return true;
                });
            }

            SwitchPreferenceCompat showAllRoomEffectsPref = findPreference("show_all_room_effects");
            if (showAllRoomEffectsPref != null) {
                boolean current = PreferenceManager
                        .getDefaultSharedPreferences(requireContext())
                        .getBoolean("show_all_room_effects", true);
                showAllRoomEffectsPref.setChecked(current);
                RoomManager.setShowAllRoomEffectsEnabled(current);
                showAllRoomEffectsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    RoomManager.setShowAllRoomEffectsEnabled(value);
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

            // Потоковая запись logcat текущего процесса в files/Logs/Logcat
            // по 10-минутным сегментам. Используется для диагностики, когда
            // системный буфер logcat переполняется.
            SwitchPreferenceCompat recordLogcatToFilePref = findPreference("record_logcat_to_file");
            if (recordLogcatToFilePref != null && AppVars.Profile != null) {
                recordLogcatToFilePref.setChecked(AppVars.Profile.RecordLogcatToFile);
                recordLogcatToFilePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.RecordLogcatToFile = value;
                    AppVars.Profile.save(requireContext());
                    LogcatFileRecorder.setEnabled(requireContext(), value);
                    return true;
                });
            }

            // Запись proxy post/get трассы в files/Logs/pool/*.txt.
            // Формат файлов: *_proxy.txt, сегментация по 10 минут.
            SwitchPreferenceCompat recordProxyPoolLogPref = findPreference("record_proxy_pool_log");
            if (recordProxyPoolLogPref != null && AppVars.Profile != null) {
                recordProxyPoolLogPref.setChecked(AppVars.Profile.RecordProxyPoolLog);
                recordProxyPoolLogPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.RecordProxyPoolLog = value;
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
                    ru.neverlands.anclient.proxy.Cache.clear();
                    return true;
                });
            }
            
            // Очистка логов
            Preference clearLogsPref = findPreference("clear_logs");
            if (clearLogsPref != null) {
                clearLogsPref.setOnPreferenceClickListener(preference -> {
                    FileLogger.clearAllLogs();
                    return true;
                });
            }
        }

        private static String buildThemeModeSummary(boolean forceDark) {
            return forceDark
                    ? "Приложение всегда использует тёмную тему"
                    : "Используется системная тема Android";
        }
    }
}
