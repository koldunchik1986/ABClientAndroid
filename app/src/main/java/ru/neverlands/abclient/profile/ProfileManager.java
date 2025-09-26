package ru.neverlands.abclient.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// TODO: Заменить на реальные Activity
// import ru.neverlands.abclient.ui.ProfilesActivity;
// import ru.neverlands.abclient.ui.SettingsActivity;

/**
 * Управляет загрузкой, сохранением и выбором профилей.
 */
public class ProfileManager {
    private static final ProfileManager instance = new ProfileManager();
    private ProfileManager() {}
    public static ProfileManager getInstance() { return instance; }

    private static final String PROFILE_PREFS_PREFIX = "profile_";

    /**
     * Запускает процесс выбора профиля.
     * Это аналог метода Process() из ConfigSelector.cs.
     */
    /*
    public void startProfileSelection(Activity activity) {
        List<UserConfig> profiles = loadAllProfiles(activity);
        if (profiles.isEmpty()) {
            // Если профилей нет, сразу открываем экран создания
            createNewProfile(activity);
            return;
        }

        // TODO: Реализовать логику авто-входа
        // UserConfig defaultProfile = profiles.get(0);
        // if (defaultProfile.isAutoLogon()) { ... }

        // Открываем экран выбора профилей
        // Intent intent = new Intent(activity, ProfilesActivity.class);
        // activity.startActivity(intent);
    }
    */

    /**
     * Загружает все профили, найденные в SharedPreferences.
     */
    /*
    public List<UserConfig> loadAllProfiles(Context context) {
        List<UserConfig> profiles = new ArrayList<>();
        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            String[] prefFiles = prefsDir.list();
            if (prefFiles != null) {
                for (String fileName : prefFiles) {
                    if (fileName.startsWith(PROFILE_PREFS_PREFIX)) {
                        String profileName = fileName.substring(PROFILE_PREFS_PREFIX.length(), fileName.lastIndexOf('.'));
                        SharedPreferences prefs = context.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE);
                        profiles.add(new UserConfig(prefs));
                    }
                }
            }
        }
        // TODO: Отсортировать профили по дате последнего использования
        return profiles;
    }
    */

    public void createNewProfile(Activity activity) {
        // Intent intent = new Intent(activity, SettingsActivity.class);
        // activity.startActivityForResult(intent, REQUEST_CODE_CREATE_PROFILE);
    }

    public void editProfile(Activity activity, String profileName) {
        // Intent intent = new Intent(activity, SettingsActivity.class);
        // intent.putExtra("profile_name", profileName);
        // activity.startActivityForResult(intent, REQUEST_CODE_EDIT_PROFILE);
    }
}
