# План портирования MyChat.cs

Файл `MyChat.cs` (класс `Chat`) — это статический менеджер, отвечающий за две функции: управление очередью сообщений для автоответчика и логирование системных сообщений в HTML-файлы.

## Функциональность в C#

1.  **Автоответчик**:
    *   `AddAnswer(string message)`: Потокобезопасно добавляет сообщение в очередь.
    *   `GetAnswer()`: Извлекает сообщение из очереди. Имеет встроенную защиту от флуда, не позволяя получать сообщения чаще, чем раз в 3 секунды.

2.  **Логирование чата**:
    *   `AddStringToChat(string message)`: Добавляет HTML-форматированное сообщение в `StringBuilder` (вероятно, для отображения в UI) и, если включена опция `ChatKeepLog`, дописывает это сообщение в HTML-файл.
    *   Логи сохраняются в подпапку профиля, имя файла генерируется на основе текущей даты (`chatГГММДД.html`).

## План портирования на Android

Эту утилиту необходимо портировать, адаптировав файловые операции под Android.

1.  **Создать `ChatManager.java`**: Сделать его синглтоном, чтобы к нему был доступ из разных частей приложения.
    ```java
    public class ChatManager {
        private static final ChatManager instance = new ChatManager();
        private ChatManager() {}
        public static ChatManager getInstance() { return instance; }
        // ... методы ...
    }
    ```

2.  **Реализовать автоответчик**:
    *   Использовать потокобезопасную очередь: `private final Queue<String> answersCollection = new ConcurrentLinkedQueue<>();`
    *   Реализовать `addAnswer` и `getAnswer` с сохранением логики задержки в 3 секунды (с помощью `System.currentTimeMillis()`).

3.  **Реализовать логирование**:
    *   **`addStringToChat(Context context, String message)`**: Метод должен будет принимать `Context` для доступа к файловой системе.
    *   **Путь к логам**: Для получения пути к директории логов использовать `context.getExternalFilesDir("logs")`. Это создаст папку `logs` в директории приложения на внешнем хранилище, доступную пользователю.
    *   **Запись в файл**: Использовать `FileWriter` и `BufferedWriter` для дозаписи в файл. Не забывать проверять существование файла и при необходимости создавать его с HTML-заголовком.
    *   **Кодировка**: При записи в файл явно указывать кодировку `windows-1251`, чтобы логи были читаемыми: `new OutputStreamWriter(new FileOutputStream(logFile, true), Russian.CODEPAGE)`.

    ```java
    public void addStringToChat(Context context, String message) {
        if (message == null || message.isEmpty()) return;

        // TODO: Добавить сообщение в StringBuilder для отображения в UI

        if (AppVars.Profile.isChatKeepLog()) {
            File logDir = new File(context.getExternalFilesDir(null), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // TODO: Реализовать getLogName() для генерации имени файла по дате
            File logFile = new File(logDir, getLogName());

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile, true), Russian.CODEPAGE))) {
                if (!logFile.exists() || logFile.length() == 0) {
                    // TODO: Записать HTML-заголовок
                }
                writer.write("<br>" + message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    ```
