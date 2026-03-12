package ru.neverlands.abclient;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

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
            
            // Настройка использования прокси
            SwitchPreferenceCompat doProxyPref = findPreference("do_proxy");
            if (doProxyPref != null && AppVars.Profile != null) {
                doProxyPref.setChecked(AppVars.Profile.DoProxy);
                doProxyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.DoProxy = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка адреса прокси
            Preference proxyAddressPref = findPreference("proxy_address");
            if (proxyAddressPref != null && AppVars.Profile != null) {
                proxyAddressPref.setSummary(AppVars.Profile.ProxyAddress);
                proxyAddressPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    AppVars.Profile.ProxyAddress = value;
                    AppVars.Profile.save(requireContext());
                    preference.setSummary(value);
                    return true;
                });
            }
            
            // Настройка имени пользователя прокси
            Preference proxyUsernamePref = findPreference("proxy_username");
            if (proxyUsernamePref != null && AppVars.Profile != null) {
                proxyUsernamePref.setSummary(AppVars.Profile.ProxyUserName);
                proxyUsernamePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    AppVars.Profile.ProxyUserName = value;
                    AppVars.Profile.save(requireContext());
                    preference.setSummary(value);
                    return true;
                });
            }
            
            // Настройка пароля прокси
            Preference proxyPasswordPref = findPreference("proxy_password");
            if (proxyPasswordPref != null && AppVars.Profile != null) {
                proxyPasswordPref.setSummary(AppVars.Profile.ProxyPassword.isEmpty() ? "" : "********");
                proxyPasswordPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    AppVars.Profile.ProxyPassword = value;
                    AppVars.Profile.save(requireContext());
                    preference.setSummary(value.isEmpty() ? "" : "********");
                    return true;
                });
            }
            
            // Настройка автоматической рыбалки
            SwitchPreferenceCompat autoFishPref = findPreference("auto_fish");
            if (autoFishPref != null && AppVars.Profile != null) {
                autoFishPref.setChecked(AppVars.Profile.AutoFish);
                autoFishPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.AutoFish = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка автоматического сбора трав
            SwitchPreferenceCompat autoHerbPref = findPreference("auto_herb");
            if (autoHerbPref != null && AppVars.Profile != null) {
                autoHerbPref.setChecked(AppVars.Profile.AutoHerb);
                autoHerbPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.AutoHerb = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка автоматической добычи руды
            SwitchPreferenceCompat autoMinePref = findPreference("auto_mine");
            if (autoMinePref != null && AppVars.Profile != null) {
                autoMinePref.setChecked(AppVars.Profile.AutoMine);
                autoMinePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.AutoMine = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка автоматической рубки деревьев
            SwitchPreferenceCompat autoTreePref = findPreference("auto_tree");
            if (autoTreePref != null && AppVars.Profile != null) {
                autoTreePref.setChecked(AppVars.Profile.AutoTree);
                autoTreePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.AutoTree = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка автоматического копания
            SwitchPreferenceCompat autoDigPref = findPreference("auto_dig");
            if (autoDigPref != null && AppVars.Profile != null) {
                autoDigPref.setChecked(AppVars.Profile.AutoDig);
                autoDigPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.AutoDig = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка автоматической торговли
            SwitchPreferenceCompat autoTorgPref = findPreference("auto_torg");
            if (autoTorgPref != null && AppVars.Profile != null) {
                autoTorgPref.setChecked(AppVars.Profile.AutoTorg);
                autoTorgPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.AutoTorg = value;
                    AppVars.Profile.save(requireContext());
                    return true;
                });
            }
            
            // Настройка активности торговли
            SwitchPreferenceCompat torgActivePref = findPreference("torg_active");
            if (torgActivePref != null && AppVars.Profile != null) {
                torgActivePref.setChecked(AppVars.Profile.TorgActive);
                torgActivePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean value = (Boolean) newValue;
                    AppVars.Profile.TorgActive = value;
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
    }
}
