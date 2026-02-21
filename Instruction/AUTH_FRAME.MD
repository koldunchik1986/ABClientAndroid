# Архитектура: WebView Interceptor + Filter + Фреймы

## Общая схема

```
WebView загружает URL
    |
    v
shouldInterceptRequest() [CustomWebViewClient в MainActivity]
    |
    v
WebViewRequestInterceptor.intercept(request)
    |
    +-- URL не neverlands.ru? -> return null (WebView грузит сам)
    +-- POST запрос? -> return null
    +-- shouldIntercept() == false? -> return null
    |
    v
HTTP запрос к серверу (HttpURLConnection)
    |
    v
Filter.process(context, url, bytes)
    |
    +-- main.php?... -> MainPhp.process()
    +-- ch/msg.php -> MsgPhp.process()
    +-- *.js -> различные JS-фильтры
    +-- и т.д.
    |
    v
WebResourceResponse -> WebView рендерит обработанный HTML
```

## Критическое правило: frameset-страницы НЕ перехватываются

### Проблема (баг от 2026-02-18)

`main.php` без параметров возвращает **frameset** (1694 байт):

```html
<HTML>
<HEAD>...</HEAD>
<FRAMESET rows="*, 0">
  <FRAME name="main_top" src="main.php?get_id=...">
  <FRAME name="main_bottom" src="...">
</FRAMESET>
</HTML>
```

Когда `shouldInterceptRequest` перехватывает этот URL и возвращает `WebResourceResponse`,
**WebView НЕ обрабатывает `<frameset>`** из перехваченного ответа. Результат — белый экран.

### Решение

`shouldIntercept()` в `WebViewRequestInterceptor.java` пропускает frameset-страницы:

```java
// main.php без параметров — это frameset, НЕ перехватываем
if (urlString.endsWith("/main.php")) return false;
// ch.php без параметров — тоже frameset
if (urlString.endsWith("/ch.php")) return false;
```

А `main.php?get_id=56&act=10&im=0&wca=28` (с параметрами) — перехватывается,
потому что это реальный контент внутри фрейма.

### Как это работает

1. WebView загружает `http://neverlands.ru/main.php` -> НЕ перехватываем -> WebView рендерит frameset нативно
2. WebView видит `<FRAME src="main.php?get_id=...">` -> создаёт sub-frame
3. Sub-frame грузит `main.php?get_id=...` -> перехватываем -> Filter.process() -> MainPhp.process()
4. Обработанный HTML возвращается в sub-frame

## Файлы и их роли

### WebViewRequestInterceptor.java
**Путь:** `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`

Единственная точка входа для перехвата HTTP-запросов WebView.

Обязанности:
- Решает, перехватывать ли URL (`shouldIntercept()`)
- Делает HTTP-запрос к серверу с правильными cookies
- Обрабатывает gzip (если сервер игнорирует `Accept-Encoding: identity`)
- Обрабатывает "Cookie..." транзитную страницу
- Вызывает `Filter.process()` для модификации ответа
- Собирает cookies из ответа обратно в WebView CookieManager

Важные детали:
- **Cookies берутся из WebView CookieManager** (`CookieManager.getInstance().getCookie(url)`) — это актуальные сессионные cookies. Fallback на кастомный `CookiesManager.obtain()`.
- **Заголовки ответа ищутся case-insensitive** (`getHeaderIgnoreCase()`) — `HttpURLConnection.getHeaderFields()` хранит ключи как получено от сервера.
- **catch(Exception)** а не **catch(IOException)** — чтобы любая ошибка вернула `null` (WebView загрузит URL сам).

### Filter.java
**Путь:** `app/src/main/java/ru/neverlands/abclient/postfilter/Filter.java`

Диспетчер: маршрутизирует URL на нужный обработчик.

```
main.php -> MainPhp.process()
ch/msg.php -> MsgPhp.process()
ch/but.php -> ButPhp.process()
game.php -> GamePhp.process()
*.js -> HpJs, MapJs, GameJs, FightJs, etc.
```

Также содержит `buildRedirect(description, link)` — генерирует HTML с JS-redirect.

### MainPhp.java
**Путь:** `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

Обработчик main.php (контент внутри фрейма main_top).

Последовательность:
1. Сохраняет `AppVars.lastMainPhpResponse` (для snapshot)
2. `Russian.getString(array)` — декодирует windows-1251 в Java String
3. `Filter.removeDoctype(html)` — убирает DOCTYPE
4. Извлекает `VCode` из HTML
5. **FastAction**: если `AppVars.FastNeed == true` -> `FastActionManager.processMainPhp(html)` генерирует auto-submit форму
6. Обработка боя (`mainPhpFight`)
7. Обработка инвентаря (`mainPhpInv`) — если HTML содержит `/invent/0.gif`
8. Обработка карты (`MapAjax.process`)
9. `Russian.getBytes(html)` — кодирует обратно в windows-1251

### FastActionManager.java
**Путь:** `app/src/main/java/ru/neverlands/abclient/manager/FastActionManager.java`

Управление быстрыми действиями (портирование FormMainFast.cs + MainPhpFast.cs из C#).

Две части:
- **Управление** (fastStart, fastCancel, fastAttack*): устанавливает `AppVars.FastNeed/FastId/FastNick`, вызывает `reloadMainFrame()`
- **Парсинг HTML** (processMainPhp): ищет `w28_form()`, `abil_svitok()`, `magicreform()` вызовы в HTML, генерирует auto-submit форму

`reloadMainFrame()` навигирует на правильную вкладку инвентаря:
- Нападалки/свитки (`i_svi_*`, `i_w28_*`) -> `main.php?get_id=56&act=10&im=0&wca=28`
- Зелья (без `.gif`) -> `main.php?get_id=56&act=10&im=0&wca=27`

### MainActivity.java — CustomWebViewClient
**Путь:** `app/src/main/java/ru/neverlands/abclient/MainActivity.java`

```java
private class CustomWebViewClient extends WebViewClient {
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return WebViewRequestInterceptor.intercept(request);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        // Инжектирует JS-фиксы (window.external, top.frames, etc.)
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // Перенаправляет pinfo.cgi в PinfoActivity
    }
}
```

## Поток данных: быстрое действие

```
1. Пользователь нажимает кнопку в QuickActionsBottomSheet
   -> FastActionManager.fastAttack(nick)

2. fastStart("i_svi_001.gif", nick)
   -> AppVars.FastNeed = true
   -> AppVars.FastId = "i_svi_001.gif"
   -> AppVars.FastNick = nick

3. reloadMainFrame()
   -> getInventoryTabUrl() = "main.php?get_id=56&act=10&im=0&wca=28"
   -> broadcast ACTION_WEBVIEW_LOAD_URL

4. MainActivity.broadcastReceiver получает broadcast
   -> webView.loadUrl("http://neverlands.ru/main.php?get_id=56&act=10&im=0&wca=28")

5. shouldInterceptRequest -> WebViewRequestInterceptor.intercept()
   -> HTTP GET main.php?get_id=56&act=10&im=0&wca=28
   -> Filter.process() -> MainPhp.process()

6. MainPhp.process() видит AppVars.FastNeed == true
   -> FastActionManager.processMainPhp(html)
   -> Парсит w28_form() в HTML
   -> Генерирует auto-submit форму:
      <form action=main.php method=POST name=ff>
        <input name=post_id value="8">
        <input name=vcode value="...">
        ...
      </form>
      <script>document.ff.submit();</script>

7. WebView рендерит форму -> JS auto-submit -> POST main.php
   -> Действие выполнено!
```

## Кодировка

Вся цепочка работает в **windows-1251**:
- Сервер neverlands.ru отдаёт `Content-Type: text/html; charset=windows-1251`
- `Russian.getString(bytes)` — декодирует windows-1251 в Java String
- `Russian.getBytes(string)` — кодирует Java String в windows-1251
- `WebResourceResponse(mime, "windows-1251", stream)` — WebView интерпретирует как windows-1251

**ВНИМАНИЕ**: `Jsoup.parse(html).outerHtml()` конвертирует в UTF-8 и может добавить `<meta charset="utf-8">`.
Если используете Jsoup, результат `doc.outerHtml()` будет в UTF-8 как Java String, но `Russian.getBytes()` корректно перекодирует его обратно в windows-1251 для отдачи в WebView.

## Логирование

Теги для `adb logcat -s`:
- `WebViewInterceptor:D` — все перехваченные URL, коды ответов, размеры, первые байты HEX, заголовки
- `MainPhp:D` — обработка main.php, первые 200 символов HTML до/после
- `FastActionManager:D` — быстрые действия
- `JS_CONSOLE:E` — JavaScript ошибки из WebView

Команда:
```
adb logcat -d -s WebViewInterceptor:D MainPhp:D FastActionManager:D JS_CONSOLE:E > logcat.txt
```

## Известные особенности

1. **ch.php?lo=1 возвращает 546 / 0 байт** — это нормально для long-polling, сервер отвечает пустым ответом когда нет обновлений
2. **`top.start is not a function`** на msg.php — безвредно, top.start определяется в onPageFinished но msg.php вызывает до этого
3. **`ins_HP is not defined`** на building_v03.js — функция определяется позже, безвредно
4. **POST запросы не перехватываются** — `shouldInterceptRequest` в Android WebView не предоставляет тело POST запроса, поэтому POST проходят напрямую через WebView
