# План портирования NativeMethods.cs

Файл `NativeMethods.cs` является мостом между управляемым кодом C# и нативными функциями операционной системы Windows (WinAPI). Он использует технологию `DllImport` для вызова функций напрямую из системных библиотек.

## Функциональность в C#

*   **`FindFirstUrlCacheEntry`, `FindNextUrlCacheEntry`, `DeleteUrlCacheEntry`**: Функции из `wininet.dll` для ручного перебора и удаления записей в кэше Internet Explorer.
*   **`InternetSetOption`**: Ключевая функция из `wininet.dll`, которая использовалась в `Proxy.cs` для программной установки локального прокси в качестве системного.
*   **`OleDraw`**: Функция из `ole32.dll` для низкоуровневой отрисовки OLE-объектов.
*   **`FlashWindow`**: Функция из `user32.dll`, которая заставляет иконку приложения на панели задач Windows мигать для привлечения внимания.

## Решение для портирования на Android

**Портировать этот класс невозможно.** Он на 100% состоит из вызовов, специфичных для Windows.

Вместо этого, для каждой функции необходимо найти ее концептуальный аналог в Android SDK.

## План реализации (замены)

1.  **Управление кэшем (`...UrlCacheEntry`)**
    *   **Замена**: `WebView.clearCache(true)`.
    *   **Статус**: Уже описано в `TODO/todo_Cache.cs.md`. Вся ручная работа с кэшем заменяется использованием встроенного механизма `WebView`.

2.  **Установка прокси (`InternetSetOption`)**
    *   **Замена**: `WebView.setWebViewClient(new MyWebViewClient())`.
    *   **Статус**: Уже описано в `TODO/todo_Proxy.cs.md`. Вместо изменения системных настроек, мы предоставляем `WebView` кастомный клиент, который перехватывает все запросы.

3.  **Отрисовка (`OleDraw`)**
    *   **Замена**: Не требуется.
    *   **Статус**: Отрисовкой `WebView` и других нативных компонентов полностью управляет система Android. Ручное вмешательство в этот процесс не нужно.

4.  **Привлечение внимания (`FlashWindow`)**
    *   **Замена**: `android.app.Notification`.
    *   **Статус**: Когда приложению, работающему в фоне, нужно привлечь внимание пользователя (например, пришло личное сообщение в чате), оно должно создать и показать системное уведомление.
    ```java
    // Пример создания уведомления
    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    String channelId = "abclient_channel";

    // Начиная с Android 8.0, все уведомления должны быть в канале
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel(channelId, "ABClient", NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
    }

    Intent intent = new Intent(context, MainActivity.class); // Интент для открытия приложения по клику
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

    Notification notification = new NotificationCompat.Builder(context, channelId)
        .setContentTitle("Новое сообщение")
        .setContentText("Вам пришло личное сообщение в чате.")
        .setSmallIcon(R.drawable.ic_notification_icon) // Нужно будет добавить иконку
        .setContentIntent(pendingIntent)
        .setAutoCancel(true) // Закрыть уведомление по клику
        .setLights(Color.BLUE, 500, 2000) // Мигание светодиода
        .build();

    notificationManager.notify(1, notification);
    ```
