# Инструкция: Перехват и обработка сетевых запросов в WebViewClient

## 1. Назначение

Основная цель перехвата запросов в `WebView` — получение полного контроля над сетевым трафиком, который генерирует веб-содержимое. Это позволяет реализовывать такие функции, как:

-   **Кэширование:** Сохранение статических ресурсов (картинки, стили, скрипты) на устройстве для экономии трафика и ускорения загрузки.
-   **Модификация ответов:** Изменение содержимого файлов "на лету" перед их отображением. Например, для внедрения собственного JavaScript (JS-инъекций), блокировки рекламы или изменения верстки.
-   **Офлайн-доступ:** Отображение закэшированного контента при отсутствии сети.

## 2. Ключевой компонент: `CustomWebViewClient`

В `MainActivity.java` реализован внутренний класс `CustomWebViewClient`, который наследуется от стандартного `android.webkit.WebViewClient`.

```java
private class CustomWebViewClient extends WebViewClient {
    // ... методы
}
```

Именно этот класс назначается всем экземплярам `WebView` в приложении, что позволяет централизованно управлять их поведением.

## 3. Метод `shouldInterceptRequest`

Это сердце всего механизма. Система вызывает этот метод для **каждого** ресурса, который `WebView` собирается загрузить (HTML-страницы, картинки, CSS, JS и т.д.).

**Сигнатура:**
`public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)`

**Принцип работы:**
-   Мы получаем на вход объект `request`, содержащий всю информацию об исходящем запросе (URL, заголовки).
-   Наша задача — вернуть объект `WebResourceResponse`, который содержит данные для `WebView`. `WebView` использует наш ответ вместо того, чтобы идти в сеть.
-   Если мы возвращаем `null`, мы сообщаем `WebView`: "Я не хочу обрабатывать этот запрос, действуй как обычно" (т.е. иди в сеть).

## 4. Алгоритм работы `shouldInterceptRequest`

Логика метода построена как конвейер с несколькими этапами:

#### Шаг 1: Получение URL и отсев исключений

```java
final String url = request.getUrl().toString();

if (url.contains("main.php?get_id=") || url.contains("main.php?mselect=")) {
    return null; // Позволяем WebView обработать это самостоятельно
}
```
-   **`url`**: Извлекается полный URL запроса.
-   **Условие `if`**: Это "белый список". Если URL содержит `get_id=` или `mselect=`, это важные навигационные ссылки. Мы не вмешиваемся и возвращаем `null`, чтобы `WebView` сам корректно обработал переход и историю навигации.

#### Шаг 2: Попытка загрузки из Assets

```java
String fileName = Uri.parse(url).getPath();
// ...
try {
    byte[] data = readAssetFile(fileName);
    // ...
    return new WebResourceResponse(mimeType, "UTF-8", new ByteArrayInputStream(data));
} catch (IOException e) {
    // Файл не найден в assets, продолжаем
}
```
-   Код пытается найти ресурс во внутренних "ассетах" приложения (папка `app/src/main/assets`).
-   Это используется для подмены стандартных скриптов игры на локальные, модифицированные версии.
-   **Особый случай `ch_list.js`**: Для этого файла дополнительно внедряется JS-код `window.external = window.AndroidBridge;`, который обеспечивает работу старого кода, обращающегося к `window.external`.

#### Шаг 3: Попытка загрузки из дискового кэша

```java
byte[] cachedData = ru.neverlands.abclient.proxy.DiskCacheManager.get(url);
if (cachedData != null) {
    // ...
    return new WebResourceResponse(mimeType, "windows-1251", new ByteArrayInputStream(processedData));
}
```
-   Если файл не найден в `assets`, происходит проверка в дисковом кэше.
-   `DiskCacheManager.get(url)`: Пытается найти ранее сохраненный ответ для данного URL.
-   Если данные найдены, они возвращаются в `WebView`.

#### Шаг 4: Выполнение сетевого запроса вручную (если ресурс не найден локально)

Если ресурса нет ни в `assets`, ни в кэше, приложение само выполняет HTTP-запрос.

```java
try {
    URL urlObj = new URL(url);
    HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
    // ... настройка заголовков и Cookie ...
    InputStream inputStream = connection.getInputStream();
    // ... чтение ответа ...
    byte[] data = baos.toByteArray();
    // ... сохранение в кэш ...
    // ... обработка и применение фильтров ...
    return new WebResourceResponse(mimeType, "windows-1251", new ByteArrayInputStream(data));
} catch (IOException e) {
    // ... обработка ошибки ...
}
```
-   **`HttpURLConnection`**: Создается стандартное Java-соединение.
-   **Копирование заголовков**: Заголовки из оригинального запроса `WebView` копируются в новый `HttpURLConnection`.
-   **`connection.setRequestProperty("Cookie", CookiesManager.obtain(url))`**: **Важный момент!** Здесь к запросу вручную добавляются `Cookie`, чтобы он был авторизованным.
-   **Чтение ответа**: Ответ сервера читается в массив байт `data`.
-   **Кэширование**: Если URL подлежит кэшированию (`isCacheable(url)`), ответ сохраняется в `DiskCacheManager`.
-   **Обработка `gzip`**: Если ответ сжат, он распаковывается.
-   **Специальная обработка**: Для некоторых URL (например, `ch.php?lo=1`) из ответа извлекаются дополнительные данные (`AppVars.chatListU`).
-   **JS-инъекции**: В HTML-страницы внедряются дополнительные скрипты (`injectJsFix`).
-   **Фильтры**: Ответ пропускается через систему пост-фильтров (`Filter.process`).
-   **Возврат `WebResourceResponse`**: `WebView` получает финальный, обработанный результат.

## 5. Другие важные методы клиента

#### `shouldOverrideUrlLoading`
```java
@Override
public boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (url != null && url.startsWith("http://neverlands.ru/pinfo.cgi")) {
        // ... открыть PinfoActivity ...
        return true; // Запрос обработан
    }
    return false; // WebView загружает URL сам
}
```
-   Этот метод перехватывает только "основные" переходы по ссылкам.
-   Он используется для того, чтобы ссылки на информацию о персонаже (`pinfo.cgi`) открывались в отдельном, нативном окне `PinfoActivity`, а не в `WebView`.

#### `onCreateWindow`
```java
@Override
public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
    // ...
    tempWebView.setWebViewClient(new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            binding.appBarMain.contentMain.webView.loadUrl(url);
            return true;
        }
    });
    // ...
    return true;
}
```
-   Этот метод вызывается, когда JS пытается открыть новое окно (`window.open()` или ссылка с `target="_blank"`).
-   Именно он исправляет баг с навигационными кнопками. Логика создает временный невидимый `WebView`, чтобы "поймать" URL, предназначенный для нового окна, а затем загружает этот URL в наш основной, видимый `WebView`.

## 6. Зависимости

Для работы этого механизма используются только стандартные классы Android SDK и Java, а также внутренние классы проекта:
-   `ru.neverlands.abclient.proxy.DiskCacheManager`: Управление дисковым кэшем.
-   `ru.neverlands.abclient.postfilter.Filter`: Применение фильтров к ответам.
-   `ru.neverlands.abclient.utils.Russian`: Корректная работа с кодировкой `windows-1251`.
-   `ru.neverlands.abclient.utils.AppVars`: Глобальные переменные.
-   `ru.neverlands.abclient.bridge.WebAppInterface`: Мост между Java и JS.
