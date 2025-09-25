package ru.neverlands.abclient;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ru.neverlands.abclient.proxy.ProxyService;
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
    }

    /**
     * Запуск прокси-сервиса
     */
    public void startProxyService() {
        Intent serviceIntent = new Intent(this, ProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    /**
     * Остановка прокси-сервиса
     */
    public void stopProxyService() {
        stopService(new Intent(this, ProxyService.class));
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