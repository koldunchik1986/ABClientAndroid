# Журнал отладки: Быстрые действия (Fast Action)
**Дата:** 2026-02-21

## Контекст

Портирование системы быстрых действий из C# (FormMainFast.cs + MainPhpFast.cs) на Android.
Предшествующие сессии: создание FastActionManager.java, интеграция с MainPhp.java, QuickActionsBottomSheet.

---

## Проблема 1: document.ff.submit() не работает

**Симптом:** После генерации HTML-формы (547 bytes) WebView показывает форму, но submit не происходит. В JS-консоли: `Uncaught SyntaxError: Invalid or unexpected token` на строке 1.

**Диагностика:**
1. Прочитан `WebViewRequestInterceptor.java:228` — вызов `HtmlUtils.injectJsFix()` на ВСЕХ ответах
2. В логах: `Filter.process returned 547 bytes` → `Intercepted OK: 2628 bytes` — размер увеличился на ~2000 bytes
3. `injectJsFix` вставил ~2000 bytes JS-стубов в `<head>` нашей сгенерированной формы
4. JS-стубы (`window.external = window.AndroidBridge; if (typeof top.start...`) конфликтуют с `document.ff.submit()`

**Решение:**
- Добавлен `GENERATED_PAGE_MARKER = "<!--ABCLIENT_GENERATED-->"` в `HtmlUtils.java`
- `injectJsFix()` проверяет маркер и пропускает инъекцию
- Маркер добавлен в `FastActionManager.HTML_HEAD`, `MainPhp.buildRedirectHtml()`, `Filter.buildRedirect()`

**Результат:** Формы теперь не получают JS-стубы. `document.ff.submit()` выполняется корректно.

**Файлы изменены:**
- `HtmlUtils.java` — добавлен `GENERATED_PAGE_MARKER`, логика пропуска в `injectJsFix()`
- `FastActionManager.java` — `HTML_HEAD` начинается с маркера
- `MainPhp.java` — `buildRedirectHtml()` начинается с маркера
- `Filter.java` — `buildRedirect()` начинается с маркера

---

## Проблема 2: Системные сообщения не отображаются

**Симптом:** После успешного submit формы (POST на сервер), ответ сервера с сообщением "нельзя нападать на себя" или "нельзя чаще раз в 5 секунд" не виден пользователю.

**Диагностика:**
1. Первоначально использовался `fetch()` + redirect вместо `document.ff.submit()`
2. `fetch()` выполнял POST в фоне и отбрасывал ответ — системные сообщения терялись
3. Затем redirect на `go=inf` загружал новую страницу через Filter — но ответ POST уже был потерян

**Решение:**
- Заменён `buildFetchAndRedirectScript()` на `buildSubmitScript()` с `document.ff.submit()`
- POST-ответ теперь рендерится напрямую в WebView (не через Filter, т.к. POST не перехватывается)
- Ответ сервера содержит системные сообщения — они видны пользователю

**Результат:** Пользователь видит все сообщения сервера ("Вы использовали свиток", "Нельзя чаще раз в 5 секунд" и т.д.)

**Файлы изменены:**
- `FastActionManager.java` — `buildSubmitScript()` вместо `buildFetchAndRedirectScript()`

---

## Проблема 3: Зелье "Превосходное Зелье Сильной Спины" не найдено

**Симптом:** При FastId=`"Зелье Сильной Спины"` зелье не находится в инвентаре, хотя оно есть.

**Диагностика:**
1. В логах: `magicreform('310909715','Блудя','Превосходное Зелье Сильной Спины','36dd7f...')`
2. Поиск по паттерну `'Зелье Сильной Спины'` (с кавычками) не совпадает с `'Превосходное Зелье Сильной Спины'`
3. Открывающая кавычка `'` стоит перед "Превосходное", а не перед "Зелье"

**Решение:**
В `mainPhpFastPotion()` добавлен двухстратегийный поиск:
```java
// Стратегия 1: точное совпадение с кавычками (как в C#)
String namepotion = "'" + fastId + "'";
int p0 = indexOfIgnoreCase(html, namepotion, 0);

// Стратегия 2: поиск без кавычек (для "Превосходное Зелье ..." и подобных)
if (p0 == -1) {
    p0 = indexOfIgnoreCase(html, fastId, 0);
}
```

**Результат:** Зелья с префиксами ("Превосходное", "Улучшенное" и т.д.) теперь находятся.

**Файлы изменены:**
- `FastActionManager.java` — `mainPhpFastPotion()` двойной поиск

---

## Проблема 4: Не переходит на нужную категорию инвентаря перед поиском

**Симптом:** При 500+ предметах в инвентаре поиск идёт по ВСЕМУ HTML (695KB), вместо перехода на отфильтрованную вкладку (28KB для wca=28, 28KB для wca=27).

**Диагностика:**
1. `reloadMainFrame()` загружает `go=inf`
2. Сервер возвращает полный инвентарь (session state после предыдущего POST)
3. `processMainPhpFast` обнаруживает `isInv=true` — мы на инвентаре
4. Код СРАЗУ вызывает `FastActionManager.processMainPhp(html)` → находит предмет в 695KB HTML
5. Redirect на категорию (строка 124) вызывается только если предмет НЕ найден
6. Но предмет НАЙДЕН (просто медленно) → redirect никогда не срабатывает

**Решение:**
Переставлена проверка категории ПЕРЕД поиском предмета:

```java
// БЫЛО (баг):
if (mainPhpIsInv(html)) {
    String fastHtml = FastActionManager.processMainPhp(html); // поиск СРАЗУ
    if (fastHtml != null) return ...; // найдено в полном HTML
    if (!address.endsWith(filterClean)) return redirect; // никогда не достигается
}

// СТАЛО (исправлено):
if (mainPhpIsInv(html)) {
    if (!address.contains(filterClean)) {
        return redirect; // СНАЧАЛА переход на категорию
    }
    String fastHtml = FastActionManager.processMainPhp(html); // ПОТОМ поиск
}
```

**Подтверждение из логов (после фикса):**
```
18:45:38.180 processMainPhpFast: FastId=i_svi_001.gif, address=...go=inf
18:45:38.182 processMainPhpFast: filter=&im=0&wca=28, isInv=false
18:45:38.184 processMainPhpFast: redirect на инвентарь: ...go=inv&vcode=...&im=0&wca=28
  ↓ WebView загружает URL с wca=28
18:45:38.713 processMainPhpFast: FastId=i_svi_001.gif, address=...&im=0&wca=28
18:45:38.717 processMainPhpFast: filter=&im=0&wca=28, isInv=true
18:45:38.735 processMainPhpFast: УСПЕХ, предмет найден (717 bytes)
```

Зелья тоже корректно переходят на категорию:
```
18:45:45.832 processMainPhpFast: filter=&im=0&wca=27, isInv=true
18:45:45.832 на инвентаре, но не на нужной категории (im=0&wca=27), переключаем
  ↓ WebView загружает main.php?im=0&wca=27
18:45:46.243 processMainPhpFast: address=...main.php?im=0&wca=27
18:45:46.244 filter=&im=0&wca=27, isInv=true
18:45:46.253 УСПЕХ, предмет найден (689 bytes, зелье)
```

**Результат:** Инвентарь теперь фильтруется по категории перед поиском.

**Файлы изменены:**
- `MainPhp.java` — `processMainPhpFast()` переставлена проверка категории

---

## Проблема 5: Туман (i_svi_213.gif) не найден после redirect на wca=28

**Симптом:** Из логов:
```
18:45:49.494 isInv=true, w28_form=true, magicreform=false
18:45:49.900 mainPhpFastFog: w28_form=true, magicreform=false, abil_svitok=false
18:45:49.902 Туман не найден
18:45:49.909 предмет не найден на правильной вкладке (im=0&wca=28), отмена
```

**Анализ:** Страница `main.php?im=0&wca=28` (468KB, вкладка свитков) содержит `w28_form(...)` но НЕ содержит `abil_svitok(...)`. Свиток Искажающего Тумана ищется через `abil_svitok`, а не через `w28_form`.

**Статус:** Известная проблема. Туман — это абилка, а не обычный свиток. Для его нахождения нужна другая вкладка или другой подход. В C# `MainPhpFastFog` тоже ищет через `abil_svitok`. Возможно, на нужной вкладке инвентаря `abil_svitok` отсутствует — нужно исследовать, на какой именно странице отображается туман.

**TODO:** Исследовать, где сервер рендерит `abil_svitok(...)` для тумана (возможно, на основной странице, а не в инвентаре).

---

## Итог сессии

| Проблема | Статус | Решение |
|----------|--------|---------|
| JS-стубы ломают submit | [x] Решено | GENERATED_PAGE_MARKER |
| Системные сообщения не видны | [x] Решено | document.ff.submit() вместо fetch() |
| Зелья с префиксом не найдены | [x] Решено | Fallback поиск без кавычек |
| Нет перехода на категорию | [x] Решено | Проверка категории ПЕРЕД поиском |
| Туман не найден на wca=28 | [ ] Открыто | Требуется исследование abil_svitok |

### Сборки

Все 4 фикса успешно собраны через `./gradlew assembleDebug` и протестированы пользователем.
