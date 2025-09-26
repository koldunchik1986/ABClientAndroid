
### 1. План портирования MyChat.cs

Файл `MyChat.cs` содержит статический класс `Chat`, который реализует две основные функции: очередь сообщений для автоответчика и логирование чата в HTML-файлы.

### 2. Функциональность в C#

- **Автоответчик:**
    - `AddAnswer(message)`: Добавляет сообщение в потокобезопасную очередь `AnswersCollection`.
    - `GetAnswer()`: Извлекает сообщение из очереди для отправки. Имеет встроенную задержку в 3 секунды между отправками для предотвращения спама.
- **Логирование чата:**
    - `AddStringToChat(message)`: Основной метод.
        1.  Добавляет сообщение в `StringBuilder` `ChatBody` (для отображения в текущей сессии).
        2.  Если в настройках включено логирование (`ChatKeepLog`), дописывает сообщение в HTML-файл.
        3.  Имя файла генерируется по дате (`chatYYYYMMDD.html`).
        4.  При создании нового файла, в него добавляется базовый HTML-заголовок.

### 3. Решение для портирования на Android

Логика автоответчика и логирования может быть перенесена с использованием стандартных средств Android и Java/Kotlin.

- **Автоответчик:** Очередь сообщений можно реализовать с помощью `java.util.concurrent.ConcurrentLinkedQueue`. Для периодической отправки сообщений можно использовать `Handler` с `postDelayed` или корутины с `delay()`.
- **Логирование:**
    - **Хранилище:** Вместо прямого доступа к файловой системе, следует использовать `context.getFilesDir()` для хранения логов в приватной директории приложения.
    - **Реализация:** Логику создания и дозаписи в файлы можно воспроизвести. Для упрощения работы с файлами можно использовать `FileWriter` или `FileOutputStream`.

### 4. План реализации

### 4. План реализации

Анализ показал, что в Android-коде уже есть заготовки для перехвата сообщений чата через `AndroidBridge`, но сама логика логирования и автоответчика отсутствует. План ниже описывает полную реализацию.

1.  **Создать `AndroidBridge` и подключить к `WebView`:**
    - Создать класс `AndroidBridge`, который будет выступать мостом.
    - В `MainActivity` или аналогичном классе, где настраивается `WebView` для чата, добавить его.
      ```kotlin
      // В MainActivity.kt
      val chatMsgWebView: WebView = findViewById(R.id.chat_msg_webview)
      chatMsgWebView.settings.javaScriptEnabled = true
      
      // ChatManager должен быть синглтоном или предоставлен через DI
      chatMsgWebView.addJavascriptInterface(AndroidBridge(ChatManager.getInstance()), "AndroidBridge")
      ```

2.  **Реализовать методы в `AndroidBridge`:**
    - Эти методы будут вызываться напрямую из отфильтрованного JS-кода (`ChMsgJs.java`).
      ```kotlin
      class AndroidBridge(private val chatManager: ChatManager) {

          @JavascriptInterface
          fun chatFilter(message: String): String {
              // Передаем сообщение в ChatManager для логирования
              chatManager.logMessage(message)

              // Здесь можно добавить логику фильтрации или модификации сообщения
              // пока просто возвращаем как есть
              return message 
          }

          @JavascriptInterface
          fun chatUpdated() {
              // Этот метод может быть использован для обновления UI, 
              // если чат не обновляется автоматически.
              // Например, можно посылать событие через LiveData.
              chatManager.notifyChatUpdated()
          }
          
          // ... другие методы моста
      }
      ```

3.  **Создать и реализовать `ChatManager` (синглтон):**
    - Этот класс будет содержать всю основную логику.
      ```kotlin
      object ChatManager {
          private val answerQueue = ConcurrentLinkedQueue<String>()
          private var lastAnswerTime = 0L

          fun addAnswer(message: String) {
              answerQueue.add(message)
          }

          fun getAnswer(): String? {
              if (answerQueue.isNotEmpty() && System.currentTimeMillis() - lastAnswerTime > 3000) {
                  lastAnswerTime = System.currentTimeMillis()
                  return answerQueue.poll()
              }
              return null
          }

          fun logMessage(message: String) {
              // TODO: Проверить настройку AppVars.Profile.ChatKeepLog
              // TODO: Реализовать логику записи в HTML-файл (см. предыдущий план)
          }

          fun notifyChatUpdated() {
              // TODO: Реализовать обновление UI через LiveData или колбэки
          }
      }
      ```

4.  **Реализовать отправку сообщений автоответчика:**
    - Нужно создать механизм, который будет периодически вызывать `ChatManager.getAnswer()` и отправлять сообщение в чат (например, через выполнение Javascript в `WebView`).
    - Это можно сделать с помощью `Handler.postDelayed` или корутины с `delay()`.

- [ ] Реализовать класс `AndroidBridge` с методами, помеченными `@JavascriptInterface`.
- [ ] Подключить `AndroidBridge` к `WebView` чата.
- [ ] Реализовать синглтон `ChatManager`.
- [ ] Реализовать в `ChatManager` логику очереди для автоответчика.
- [ ] Реализовать в `ChatManager` логику сохранения лога чата в HTML-файл.
- [ ] Создать фоновый процесс (Handler/Coroutine) для периодической отправки сообщений автоответчика.
