# Инструкция реализации: Быстрые действия (Fast Action)

## 1. Общее описание

Система "Быстрые действия" позволяет одним нажатием кнопки использовать свитки, зелья и абилки из инвентаря на указанного игрока. Система автоматически:
1. Навигирует на нужную вкладку инвентаря
2. Находит предмет в HTML
3. Извлекает параметры формы (vcode, wuid, wsubid и т.д.)
4. Генерирует HTML с авто-submit формой
5. Отправляет POST на сервер

## 2. Исходные C# файлы (эталон)

| Файл C# | Назначение | Android-аналог |
|----------|-----------|----------------|
| `ABForms/FormMainFast.cs` | UI: кнопки атак, `FastStartSafe`, `FastCancelSafe`, `FastAttackAsync` | `FastActionManager.java` (часть 1) + `QuickActionsBottomSheet.java` |
| `PostFilter/MainPhpFast.cs` | HTML-парсинг: `MainPhpFast`, `MainPhpFastHit`, `MainPhpFastPotion`, `MainPhpFastFog` и др. | `FastActionManager.java` (часть 2) |
| `PostFilter/MainPhp.cs:1429-1680` | Оркестратор: навигация на инвентарь → поиск предмета → авто-submit | `MainPhp.java` → `processMainPhpFast()` |
| `PostFilter/MainPhpDrink.cs` | Навигация: `MainPhpFindInv`, `MainPhpFindInvOld`, `MainPhpIsInv` | `MainPhp.java` → `mainPhpFindInv()`, `mainPhpIsInv()` |

## 3. Архитектура на Android

### 3.1. Файлы и зависимости

```
┌─────────────────────────────────────────────────────────────────────────┐
│ QuickActionsBottomSheet.java (UI)                                       │
│   Панель с кнопками атак. Вызывает FastActionManager.fastAttack*(nick) │
└────────────────────────┬────────────────────────────────────────────────┘
                         │ вызывает
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ FastActionManager.java (Управление + HTML-парсинг)                      │
│                                                                         │
│   Часть 1 — Управление (из FormMainFast.cs):                           │
│     fastStart(id, nick, count) → AppVars.FastNeed=true                 │
│     fastCancel() → AppVars.FastNeed=false                              │
│     fastAttack*(nick) → fastStart(weapon, nick) → reloadMainFrame()   │
│                                                                         │
│   Часть 2 — HTML-парсинг (из MainPhpFast.cs):                          │
│     processMainPhp(html) → switch(FastId) → mainPhpFast*(html)        │
│     mainPhpFastHit() — нападалки (w28_form, post_id=8)                │
│     mainPhpFastW28() — свитки (w28_form, post_id=25)                  │
│     mainPhpFastFog() — туман (abil_svitok, post_id=44)                │
│     mainPhpFastPotion() — зелья (magicreform, post_id=46)             │
└────────────────────────┬────────────────────────────────────────────────┘
                         │ вызывается из
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ MainPhp.java (PostFilter — оркестратор)                                 │
│                                                                         │
│   process(address, array):                                              │
│     if (AppVars.FastNeed) → processMainPhpFast(address, html)          │
│                                                                         │
│   processMainPhpFast(address, html):                                    │
│     1. getInventoryFilter(FastId) → "&im=0&wca=28" / "&im=0&wca=27"   │
│     2. mainPhpFindInv(html, filter) → redirect на инвентарь            │
│     3. mainPhpIsInv(html) → true → проверка категории → поиск предмета│
│     4. FastActionManager.processMainPhp(html) → форма с авто-submit   │
└────────────────────────┬────────────────────────────────────────────────┘
                         │ вызывается из
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Filter.java (PostFilter — маршрутизатор)                                │
│                                                                         │
│   process(context, address, array):                                     │
│     if (address.startsWith("http://neverlands.ru/main.php"))           │
│       → MainPhp.process(address, array)                                │
└────────────────────────┬────────────────────────────────────────────────┘
                         │ вызывается из
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ WebViewRequestInterceptor.java (WebView перехватчик)                    │
│                                                                         │
│   intercept(request):                                                   │
│     Только GET-запросы (POST не перехватываются!)                       │
│     → HttpURLConnection → получаем bytes                               │
│     → Filter.process(context, url, bytes)                              │
│     → HtmlUtils.injectJsFix(processed, url, contentType)              │
│     → WebResourceResponse                                              │
└────────────────────────┬────────────────────────────────────────────────┘
                         │ вызывается из
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ MainActivity.java                                                       │
│   shouldInterceptRequest(view, request) → intercept(request)           │
│   BroadcastReceiver → ACTION_WEBVIEW_LOAD_URL → webView.loadUrl(url)  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2. Файлы Android-проекта

| Файл | Путь | Назначение |
|------|------|-----------|
| `FastActionManager.java` | `app/src/main/java/ru/neverlands/abclient/manager/` | Управление + HTML-парсинг |
| `MainPhp.java` | `app/src/main/java/ru/neverlands/abclient/postfilter/` | Оркестратор навигации |
| `Filter.java` | `app/src/main/java/ru/neverlands/abclient/postfilter/` | Маршрутизатор URL→обработчик |
| `HtmlUtils.java` | `app/src/main/java/ru/neverlands/abclient/utils/` | JS-стубы, GENERATED_PAGE_MARKER |
| `WebViewRequestInterceptor.java` | `app/src/main/java/ru/neverlands/abclient/webview/` | Перехват GET-запросов WebView |
| `QuickActionsBottomSheet.java` | `app/src/main/java/ru/neverlands/abclient/ui/` | UI панель быстрых действий |
| `AppVars.java` | `app/src/main/java/ru/neverlands/abclient/utils/` | Глобальные переменные |
| `HelperStrings.java` | `app/src/main/java/ru/neverlands/abclient/utils/` | Утилиты парсинга строк |

### 3.3. Глобальные переменные (AppVars.java)

```java
// Fast Attack variables (портировано из AppVars.cs)
public static volatile boolean FastNeed = false;        // Флаг активности быстрого действия
public static volatile String FastId = null;            // ID предмета (напр. "i_svi_001.gif" или "Зелье Сильной Спины")
public static volatile String FastNick = null;          // Ник цели
public static volatile int FastCount = 0;               // Кол-во повторений (обычно 1)
public static volatile boolean FastWaitEndOfBoiActive = false;  // Ожидание конца боя
public static volatile boolean FastWaitEndOfBoiCancel = false;  // Отмена ожидания
public static volatile boolean FastNeedAbilDarkTeleport = false; // Темный телепорт
public static volatile boolean FastNeedAbilDarkFog = false;      // Темный туман
public static String VCode = "";                        // Текущий vcode сессии
public static final String ACTION_WEBVIEW_LOAD_URL = "ru.neverlands.abclient.ACTION_WEBVIEW_LOAD_URL";
```

## 4. Алгоритм работы (полный цикл)

### 4.1. Последовательность шагов

```
Пользователь нажимает кнопку в QuickActionsBottomSheet
  │
  ▼
QuickActionsBottomSheet.onAttackButtonClick("simple", "НикИгрока")
  │
  ▼
FastActionManager.fastAttack("НикИгрока")
  │
  ▼
FastActionManager.fastStart("i_svi_001.gif", "НикИгрока", 1)
  │  Устанавливает: AppVars.FastNeed=true, FastId="i_svi_001.gif",
  │  FastNick="НикИгрока", FastCount=1
  │
  ▼
FastActionManager.reloadMainFrame()
  │  Отправляет broadcast ACTION_WEBVIEW_LOAD_URL
  │  URL: "http://neverlands.ru/main.php?get_id=56&act=10&go=inf[&vcode=...]"
  │
  ▼
MainActivity.BroadcastReceiver → webView.loadUrl(url)
  │
  ▼
WebViewRequestInterceptor.intercept(request)
  │  GET http://neverlands.ru/main.php?get_id=56&act=10&go=inf
  │  → HttpURLConnection → получает HTML с сервера (16KB — страница персонажа)
  │
  ▼
Filter.process(context, address, bytes)
  │  address начинается с "http://neverlands.ru/main.php"
  │  → MainPhp.process(address, array)
  │
  ▼
MainPhp.process(address, array)
  │  AppVars.FastNeed == true
  │  → processMainPhpFast(address, html)
  │
  ▼
MainPhp.processMainPhpFast(address, html)
  │  1. getInventoryFilter("i_svi_001.gif") → "&im=0&wca=28"
  │  2. mainPhpFindInv(html, "&im=0&wca=28")
  │     Мы НЕ на инвентаре → ищем ссылку → BuildRedirect на инвентарь
  │     → "main.php?get_id=56&act=10&go=inv&vcode=XXXX&im=0&wca=28"
  │
  ▼
WebView рендерит redirect-страницу (GENERATED_PAGE_MARKER → без JS-стубов)
  │  window.location = "main.php?get_id=56&act=10&go=inv&vcode=XXXX&im=0&wca=28"
  │
  ▼
WebViewRequestInterceptor.intercept(request) — ВТОРОЙ запрос
  │  GET http://neverlands.ru/main.php?...&im=0&wca=28
  │  → Сервер возвращает инвентарь (468KB — вкладка свитков wca=28)
  │
  ▼
MainPhp.processMainPhpFast(address, html)
  │  1. mainPhpIsInv(html) → true (мы на инвентаре)
  │  2. address содержит "im=0&wca=28" → мы на правильной вкладке
  │  3. FastActionManager.processMainPhp(html)
  │     → mainPhpFastHit(html, ["1","2","3","4"], "обычную нападалку")
  │     → Находит w28_form('vcode','wuid','1','wsolid')
  │     → Генерирует HTML с формой (717 bytes)
  │
  ▼
WebView рендерит форму (GENERATED_PAGE_MARKER → без JS-стубов)
  │  "Используем обычную нападалку на НикИгрока..."
  │  <form action="http://neverlands.ru/main.php" method=POST name=ff>
  │  document.ff.submit();
  │
  ▼
POST http://neverlands.ru/main.php (НЕ перехватывается — только GET!)
  │  Сервер обрабатывает атаку и возвращает результат
  │
  ▼
WebView отображает ответ сервера с системным сообщением
  ("Вы напали на НикИгрока" / "Нельзя чаще раз в 5 секунд" и т.д.)
```

### 4.2. Навигация по категориям инвентаря

Когда `reloadMainFrame()` загружает `go=inf`, сервер может вернуть:
- **Страницу персонажа** (16KB) — если мы были не в инвентаре → `mainPhpFindInv` находит ссылку на инвентарь
- **Полный инвентарь** (468-695KB) — если сервер сохранил session state → `mainPhpIsInv` = true, но мы можем быть НЕ на нужной вкладке

**Критично:** Перед поиском предмета ОБЯЗАТЕЛЬНО проверяем категорию URL:

```java
// processMainPhpFast — порядок шагов:
if (mainPhpIsInv(html)) {
    String filterClean = filter.startsWith("&") ? filter.substring(1) : filter;

    // СНАЧАЛА проверяем категорию!
    if (!address.contains(filterClean)) {
        // Redirect на main.php?im=0&wca=28 (или wca=27)
        return Filter.buildRedirect("Переключение на нужную категорию",
                "main.php?" + filterClean);
    }

    // ПОТОМ ищем предмет
    String fastHtml = FastActionManager.processMainPhp(html);
    ...
}
```

**Без этой проверки** поиск идёт по ВСЕМУ инвентарю (695KB HTML вместо 28-468KB), что медленно и может найти предмет на неправильной вкладке.

### 4.3. Категории инвентаря (getInventoryFilter)

| FastId | Категория | Фильтр URL |
|--------|-----------|-----------|
| `i_svi_001.gif` — `i_w28_86.gif` (все свитки) | Свитки (wca=28) | `&im=0&wca=28` |
| `"Телепорт (Остров Туротор)"` | Свитки (wca=28) | `&im=0&wca=28` |
| `"Яд"`, `"Зелье Сильной Спины"`, ... (35 зелий) | Зелья (wca=27) | `&im=0&wca=27` |
| `"Эликсир Блаженства"`, ... (3 эликсира) | Эликсиры (im=6) | `&im=6` |

## 5. HTML-парсеры (FastActionManager)

### 5.1. mainPhpFastHit — Нападалки

**Паттерн HTML:** `w28_form('vcode','wuid','wsubid','wsolid')`

**Поиск:** Перебирает все `w28_form(...)` в HTML, проверяет `wsubid` на совпадение с допустимыми значениями.

| FastId | wsubid | Описание |
|--------|--------|----------|
| `i_svi_001.gif` | 1, 2, 3, 4 | Обычная нападалка |
| `i_svi_002.gif` | 5, 6, 7, 8 | Кровавая нападалка |
| `i_w28_26.gif` | 26 | Боевая нападалка |
| `i_w28_26X.gif` | 29 | Закрытая боевая |
| `i_svi_205.gif` | 14 | Закрытая нападалка |
| `i_w28_24.gif` | 24 | Обычная кулачка |
| `i_w28_25.gif` | 25 | Закрытая кулачка |

**Генерируемая форма:**
```html
<form action="http://neverlands.ru/main.php" method=POST name=ff>
  <input name=post_id type=hidden value="8">
  <input name=vcode type=hidden value="...">
  <input name=wuid type=hidden value="...">
  <input name=wsubid type=hidden value="...">
  <input name=wsolid type=hidden value="...">
  <input name=pnick type=hidden value="НикЦели">
  <input name=agree type=hidden value="Выполнить">
</form>
<script>document.ff.submit();</script>
```

### 5.2. mainPhpFastW28 — Свитки/Порталы

**Паттерн HTML:** `w28_form('vcode','wuid','wsubid','wsolid')`

| FastId | wsubid | Описание |
|--------|--------|----------|
| `i_w28_27.gif` | 27 | Свиток защиты |
| `i_w28_86.gif` | 86 | Портал |
| `i_w28_22.gif` | 22 | Телепорт |

**Генерируемая форма:** как `mainPhpFastHit`, но `post_id=25`.

### 5.3. mainPhpFastFog — Туман

**Паттерн HTML:** `abil_svitok('wuid','wmid','wmsolid','name','wmcode')`

**Поиск:** Ищет `'Свиток Искажающего Тумана'`, затем в окружающем HTML-теге ищет `abil_svitok(`.

**Генерируемая форма:**
```html
<form action="http://neverlands.ru/main.php" method=POST name=ff>
  <input name=post_id type=hidden value="44">
  <input name=uid type=hidden value="...">
  <input name=mid type=hidden value="...">
  <input name=curs type=hidden value="...">
  <input name=vcode type=hidden value="...">
  <input name=fnick type=hidden value="НикЦели">
  <input name=agree type=hidden value="Выполнить">
</form>
```

### 5.4. mainPhpFastPotion — Зелья

**Паттерн HTML:** `magicreform('wuid','target','potionName','wmcode')`

**Поиск:**
1. Стратегия 1: Точное совпадение с кавычками `'Зелье Сильной Спины'`
2. Стратегия 2 (fallback): Без кавычек `Зелье Сильной Спины` — для вариантов с префиксом ("Превосходное Зелье Сильной Спины")

**Генерируемая форма:**
```html
<form action="http://neverlands.ru/main.php" method=POST name=ff>
  <input name=magicrestart type=hidden value="1">
  <input name=magicreuid type=hidden value="...">
  <input name=vcode type=hidden value="...">
  <input name=post_id type=hidden value="46">
  <input name=fornickname type=hidden value="НикЦели">
  <input name=agree type=hidden value="Применить">
</form>
```

### 5.5. Сводная таблица post_id

| post_id | Категория | Используется в |
|---------|-----------|---------------|
| **8** | Все нападалки + тотем | `mainPhpFastHit` |
| **25** | Утилитарные свитки (телепорт, защита, портал, невид) | `mainPhpFastW28` |
| **44** | Туман (abil_svitok) | `mainPhpFastFog` |
| **46** | Все зелья (magicreform) | `mainPhpFastPotion` |

## 6. Ключевые механизмы

### 6.1. GENERATED_PAGE_MARKER

**Проблема:** `HtmlUtils.injectJsFix()` вставляет JS-стубы (`window.external = window.AndroidBridge; ...`) в `<head>` ВСЕХ HTML-ответов. Эти стубы ломают `document.ff.submit()` в наших генерируемых формах (вызывают `Uncaught SyntaxError`).

**Решение:** Генерируемые страницы (формы, редиректы) начинаются с маркера:
```java
public static final String GENERATED_PAGE_MARKER = "<!--ABCLIENT_GENERATED-->";
```

`injectJsFix()` проверяет наличие маркера и пропускает инъекцию:
```java
if (html.contains(GENERATED_PAGE_MARKER)) {
    return body; // НЕ инжектируем стубы
}
```

**Файлы с маркером:**
- `HtmlUtils.java` — определение константы + проверка в `injectJsFix()`
- `FastActionManager.java` — `HTML_HEAD` начинается с маркера
- `MainPhp.java` — `buildRedirectHtml()` начинается с маркера
- `Filter.java` — `buildRedirect()` начинается с маркера

### 6.2. POST не перехватывается

`WebViewRequestInterceptor.intercept()` (строка 64):
```java
if (!request.getMethod().equalsIgnoreCase("GET")) {
    return null; // Пропускаем POST
}
```

**Следствие:** Ответ сервера на `document.ff.submit()` (POST) рендерится WebView напрямую, БЕЗ прохождения через Filter. Это нормально — ответ содержит системное сообщение о результате действия.

### 6.3. Абсолютные URL в формах

Все формы используют абсолютный URL:
```html
<form action="http://neverlands.ru/main.php" method=POST name=ff>
```
А не относительный `action=main.php`, т.к. WebView может иметь неожиданный base URL после серии редиректов.

### 6.4. Поле agree

Все формы содержат скрытое поле `agree`:
```html
<input name=agree type=hidden value="Выполнить">
```
Это эмулирует нажатие кнопки "Выполнить" в оригинальном UI сервера.

## 7. Навигация на инвентарь (mainPhpFindInv)

### 7.1. Стратегии поиска vcode

Метод `mainPhpFindInv(html, filter)` ищет ссылку на инвентарь с помощью 5 стратегий:

| # | Стратегия | Детектор | Паттерн vcode |
|---|-----------|----------|---------------|
| 1 | Арена | `view_arena()` | `var vcode = [..., "hash", ...]` → `vcode[1]` |
| 2 | Здания | `view_moor()` / `view_taverna()` / ... | `var vcode = [[1,"hash"],...]` → вложенный `[1,"hash"]` |
| 3 | Кнопка "Инвентарь" | `value="Инвентарь"` | `onclick="location='main.php?...'"` |
| 4 | JSON-массив | `["inv","Инвентарь","hash"...]` | Третий элемент массива |
| 5 | Кнопка "Вернуться" | `value="Вернуться"` | Redirect на `main.php` |

**Генерируемый URL:** `main.php?get_id=56&act=10&go=inv&vcode={hash}{filter}`

### 7.2. mainPhpIsInv — определение инвентаря

```java
private static boolean mainPhpIsInv(String html) {
    return html.contains("<a href=\"?im=0\"><img") || html.contains("<a href=?im=0><img");
}
```

## 8. Статус реализации

### Реализовано [x]

- [x] `FastActionManager.java` — полный класс
- [x] `fastStart(id, nick, count)` / `fastCancel()`
- [x] 13 методов `fastAttack*(nick)` (нападалки, кулачки, туман, яд, зелья, невид, портал, защита)
- [x] `processMainPhp(html)` — диспетчер по FastId
- [x] `mainPhpFastHit(html, validSubIds, desc)` — универсальный парсер w28_form
- [x] `mainPhpFastW28(html, targetSubId, desc)` — парсер для свитков/порталов
- [x] `mainPhpFastFog(html)` — парсер abil_svitok для тумана
- [x] `mainPhpFastPotion(html)` — парсер magicreform для зелий (с fallback без кавычек)
- [x] `processMainPhpFast(address, html)` в MainPhp.java — оркестратор
- [x] `mainPhpFindInv(html, filter)` — 5 стратегий навигации на инвентарь
- [x] `mainPhpIsInv(html)` — определение страницы инвентаря
- [x] `getInventoryFilter(FastId)` — маппинг FastId → фильтр категории
- [x] `GENERATED_PAGE_MARKER` — предотвращение инъекции JS-стубов в формы
- [x] `buildSubmitScript()` — `document.ff.submit()` с console.log
- [x] `QuickActionsBottomSheet.java` — UI панель (11 кнопок)
- [x] Интеграция с `MainPhp.process()` — вызов `processMainPhpFast` при `FastNeed`
- [x] Навигация на правильную вкладку категории ПЕРЕД поиском предмета
- [x] Поиск зелий с префиксом ("Превосходное Зелье...")

### Не реализовано [ ]

- [ ] `mainPhpFastElixir(html)` — эликсиры (im=6). В C#: ищет `"Использовать <ElixirName> сейчас?"` → GET redirect
- [ ] `mainPhpFastTotem(html)` — тотем. В C#: ищет `["fig","Напасть","vcode"]` → POST с post_id=8
- [ ] `mainPhpFastTeleport(html)` — полная реализация (сейчас использует `mainPhpFastW28` с wsubid=22, но без `wtelid` — случайного пункта назначения)
- [ ] `mainPhpFastSelfRass(html)` — саморассеивание (wsubid=23). В C#: post_id=25, без pnick
- [ ] `mainPhpFastOpenNevid(html)` — обнаружение (wsubid=28). В C#: post_id=25, без pnick
- [ ] `FastAttackAsync` — фоновый поток с ожиданием окончания боя цели (зависит от NeverApi)
- [ ] Чат-сообщения при выполнении/отмене действия (в C#: `WriteChatMsgSafe`)
- [ ] Кнопки для эликсиров, тотема, острова в UI (QuickActionsBottomSheet)
- [ ] `NeverTimer` — cooldown перед выполнением (в C#: `DateTime.Now > AppVars.NeverTimer`)

## 9. Отличия Android от C#

| Аспект | C# (ПК) | Android |
|--------|---------|---------|
| Перехват запросов | Прокси перехватывает ВСЕ запросы (GET+POST) | `shouldInterceptRequest` перехватывает только GET |
| Фреймы | `NavigateFrame("main_top", "main.php")` — навигация sub-frame | `loadUrl(url)` — навигация всего WebView |
| POST-ответ | Проходит через Filter → можно обработать | Рендерится напрямую WebView (без Filter) |
| Начальная страница | `main.php` в фрейме → сервер возвращает go=inf | `main.php?get_id=56&act=10&go=inf` напрямую |
| JS-стубы | Не нужны (есть window.external = COM object) | `injectJsFix` с `GENERATED_PAGE_MARKER` для избежания конфликтов |
| Формы | `action=main.php` (относительный) | `action="http://neverlands.ru/main.php"` (абсолютный) |
| Зелья с префиксом | Ищет с кавычками `'Зелье...'` | + fallback без кавычек для "Превосходное Зелье..." |
| Категория перед поиском | Сначала поиск, потом redirect на категорию | Сначала redirect на категорию, потом поиск (оптимизация) |
