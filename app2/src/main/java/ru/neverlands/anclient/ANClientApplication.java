package ru.neverlands.anclient;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ru.neverlands.anclient.model.LezBotsClassCollection;
import ru.neverlands.anclient.repository.ThingsRepository;
import ru.neverlands.anclient.license.LicenseRuntime;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.DataManager;
import ru.neverlands.anclient.utils.GameServerUrls;
import ru.neverlands.anclient.utils.ThemeModeManager;

/**
 * Основной класс приложения, инициализирующий глобальные переменные и компоненты.
 * Аналог Program.cs в оригинальном приложении.
 */
public class ANClientApplication extends Application {
    private static ANClientApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        ThemeModeManager.applyFromPreferences(this);
        
        // Инициализация глобальных переменных
        AppVars.init(this);

        // Единый список game endpoints нужен до LoginActivity/MainActivity/proxy routing.
        GameServerUrls.initialize(this);

        // LicenseRuntime держит активную capability-сессию после проверки profile.reg.
        LicenseRuntime.getInstance().initialize(this);
        
        // Инициализация менеджера данных
        DataManager.init(this);

        // Инициализация runtime-справочника типов противников.
        // Зависимости:
        // - `LezFight` / `UnderAttackManager` читают boss/bot классификацию через
        //   `LezBotsClassCollection.isBossName(...)`.
        // Переменные/файлы:
        // - bootstrap: `assets/info/bottypes.xml`;
        // - runtime: `<files>/info/bottypes.xml` (auto-upsert новых имён).
        LezBotsClassCollection.init(this);

        // Инициализация репозитория вещей
        // ThingsRepository.INSTANCE.initialize(this);

        // Инициализация менеджера кэша
        ru.neverlands.anclient.proxy.DiskCacheManager.init(this);
    }

    /**
     * Получение экземпляра приложения
     * @return экземпляр приложения
     */
    public static ANClientApplication getInstance() {
        return instance;
    }

    /**
     * Получение контекста приложения
     * @return контекст приложения
     */
    public static Context getAppContext() {
        return instance.getApplicationContext();
    }
}
