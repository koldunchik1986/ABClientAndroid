# Анализ логирования в ProxyService.java

## Обзор

Данный документ содержит анализ существующего логирования в файле `proxy/ProxyService.java` и предложения по добавлению нового логирования для более детальной отладки работы прокси-сервиса.

## Константа TAG

`private static final String TAG = "ProxyService";`

## Существующее логирование по функциям/методам

### `startProxyServer()`
*   **Тип:** `Log.e`
*   **Сообщение:** `"Port " + port + " is busy, trying next port"` (с трассировкой стека `e`)
*   **Назначение:** Логирует ошибку, если порт занят во время запуска сервера, и попытку использовать следующий порт.

*   **Тип:** `Log.d`
*   **Сообщение:** `"Proxy server started on port " + port`
*   **Назначение:** Логирует успешный запуск прокси-сервера с указанием используемого порта.

*   **Тип:** `Log.e`
*   **Сообщение:** `"Error accepting connection"` (с трассировкой стека `e`)
*   **Назначение:** Логирует ошибки при приеме клиентских соединений.

*   **Тип:** `Log.e`
*   **Сообщение:** `"Error starting proxy server"` (с трассировкой стека `e`)
*   **Назначение:** Логирует общие ошибки, возникающие при запуске прокси-сервера.

### `stopProxyServer()`
*   **Тип:** `Log.e`
*   **Сообщение:** `"Error closing server socket"` (с трассировкой стека `e`)
*   **Назначение:** Логирует ошибки при закрытии серверного сокета.

*   **Тип:** `Log.d`
*   **Сообщение:** `"Proxy server stopped"`
*   **Назначение:** Логирует успешную остановку прокси-сервера.

### `getUpstreamProxy()`
*   **Тип:** `Log.e`
*   **Сообщение:** `"Invalid proxy port"` (с трассировкой стека `e`)
*   **Назначение:** Логирует ошибку, если порт вышестоящего прокси указан неверно.

## Предлагаемое дополнительное логирование

Для более детального отслеживания жизненного цикла сервиса, обработки соединений и конфигурации прокси, предлагается добавить следующие `Log.d` сообщения:

```java
// В onCreate()
@Override
public void onCreate() {
    super.onCreate();
    executorService = Executors.newCachedThreadPool();
    Log.d(TAG, "ProxyService onCreate. ExecutorService initialized.");
}

// В onStartCommand(Intent intent, int flags, int startId)
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "ProxyService onStartCommand called. isRunning: " + isRunning);
    if (isRunning) {
        return START_STICKY;
    }
    
    createNotificationChannel();
    startForeground(NOTIFICATION_ID, createNotification());
    startProxyServer();
    Log.d(TAG, "ProxyService started foreground and server initiated.");
    return START_STICKY;
}

// В onDestroy()
@Override
public void onDestroy() {
    Log.d(TAG, "ProxyService onDestroy called. Stopping server.");
    stopProxyServer();
    super.onDestroy();
    Log.d(TAG, "ProxyService destroyed.");
}

// В startProxyServer() - внутри цикла while (!serverSocket.isClosed())
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
                    Log.d(TAG, "Accepted new client connection from: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
                    executorService.submit(new SessionHandler(clientSocket));
                    Log.d(TAG, "Submitted SessionHandler for client: " + clientSocket.getInetAddress().getHostAddress());
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        Log.e(TAG, "Error accepting connection", e);
                    } else {
                        Log.d(TAG, "ServerSocket closed while accepting connection. Exiting accept loop.");
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error starting proxy server", e);
            stopSelf();
        }
    });
}

// В getUpstreamProxy()
public static Proxy getUpstreamProxy() {
    if (AppVars.Profile != null && AppVars.Profile.DoProxy && !AppVars.Profile.ProxyAddress.isEmpty()) {
        Log.d(TAG, "Upstream proxy is enabled. Address: " + AppVars.Profile.ProxyAddress);
        String[] parts = AppVars.Profile.ProxyAddress.split(":");
        if (parts.length == 2) {
            try {
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                Log.d(TAG, "Returning HTTP proxy: " + host + ":" + port);
                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid proxy port in AppVars.Profile.ProxyAddress: " + AppVars.Profile.ProxyAddress, e);
            }
        } else {
            Log.e(TAG, "Invalid proxy address format in AppVars.Profile.ProxyAddress: " + AppVars.Profile.ProxyAddress);
        }
    } else {
        Log.d(TAG, "Upstream proxy is NOT enabled or address is empty. Returning NO_PROXY.");
    }
    
    return Proxy.NO_PROXY; // Изменено с null на Proxy.NO_PROXY для ясности и согласованности
}
```