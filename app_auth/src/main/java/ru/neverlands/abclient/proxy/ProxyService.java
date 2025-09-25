package ru.neverlands.abclient.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.R;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Сервис для работы с прокси-сервером.
 * Аналог Proxy.cs в оригинальном приложении.
 */
public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "proxy_service_channel";
    
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newCachedThreadPool();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            return START_STICKY;
        }
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        startProxyServer();
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        stopProxyServer();
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Создание канала уведомлений для Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.proxy_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Создание уведомления для foreground service
     * @return уведомление
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.proxy_notification_title))
                .setContentText(getString(R.string.proxy_notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build();
    }
    
    /**
     * Запуск прокси-сервера
     */
    private void startProxyServer() {
        executorService.submit(() -> {
            try {
                int port = AppVars.LocalProxyPort;
                int maxAttempts = 10;
                
                // Пытаемся найти свободный порт
                for (int attempt = 0; attempt < maxAttempts; attempt++) {
                    try {
                        serverSocket = new ServerSocket();
                        serverSocket.setReuseAddress(true);
                        serverSocket.bind(new InetSocketAddress(port));
                        break;
                    } catch (IOException e) {
                        Log.e(TAG, "Port " + port + " is busy, trying next port", e);
                        port++;
                        
                        if (attempt == maxAttempts - 1) {
                            throw e; // Если все попытки неудачны, выбрасываем исключение
                        }
                    }
                }
                
                AppVars.LocalProxyPort = port;
                Log.d(TAG, "Proxy server started on port " + port);
                isRunning = true;
                
                // Принимаем соединения
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.submit(new SessionHandler(clientSocket));
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            Log.e(TAG, "Error accepting connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting proxy server", e);
                stopSelf();
            }
        });
    }
    
    /**
     * Остановка прокси-сервера
     */
    private void stopProxyServer() {
        isRunning = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        Log.d(TAG, "Proxy server stopped");
    }
    
    /**
     * Получение прокси для внешних соединений
     * @return прокси или null, если прокси не используется
     */
    public static Proxy getUpstreamProxy() {
        if (AppVars.Profile != null && AppVars.Profile.DoProxy && !AppVars.Profile.ProxyAddress.isEmpty()) {
            String[] parts = AppVars.Profile.ProxyAddress.split(":");
            if (parts.length == 2) {
                try {
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid proxy port", e);
                }
            }
        }
        
        return null;
    }
}