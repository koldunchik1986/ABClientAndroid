package ru.neverlands.abclient;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ru.neverlands.abclient.repository.ThingsRepository;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.DataManager;

/**
 * Основной класс приложения, инициализирующий глобальные переменные и компоненты.
 * Аналог Program.cs в оригинальном приложении.
 */
public class ABClientApplication extends Application {
    private static ABClientApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // Инициализация глобальных переменных
        AppVars.init(this);
        
        // Инициализация менеджера данных
        DataManager.init(this);

        // Инициализация репозитория вещей
        // ThingsRepository.INSTANCE.initialize(this);

        // Инициализация менеджера кэша
        ru.neverlands.abclient.proxy.DiskCacheManager.init(this);
    }

    /**
     * Получение экземпляра приложения
     * @return экземпляр приложения
     */
    public static ABClientApplication getInstance() {
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