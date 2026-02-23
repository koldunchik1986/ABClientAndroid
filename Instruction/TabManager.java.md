# Инструкция по TabManager.java

## Назначение файла

Файл `TabManager.java` реализует систему управления вкладками в Android-приложении. Является аналогом `TabControl` из C# версии ABClient. Управляет горизонтальными вкладками с возможностью открывать новые, переключаться между ними и закрывать.

## Назначение вкладок

| Вкладка | Описание | Закрытие |
|---------|----------|----------|
| 0 (Основная) | main.php + чат | Запрещено |
| 1+ (Вспомогательные) | pinfo, форум, комнаты, бои и т.д. | Разрешено |

## Структура класса

### Вложенный класс: TabInfo

Хранит информацию о вспомогательной вкладке.

**Поля:**
- `title` (String) - заголовок вкладки
- `url` (String) - URL страницы
- `webView` (WebView) - WebView для отображения контента
- `contentView` (View) - корневой View вкладки
- `tabType` (TabType) - тип вкладки для определения набора кнопок

### Вложенный enum: TabType

Определяет тип вкладки для настройки кнопок панели действий.

| Значение | Описание | Кнопки |
|----------|----------|--------|
| `FORUM` | Вкладка форума | Назад, Обновить, Копировать URL |
| `PINFO` | Вкладка информации о персонаже | Обновить, В контакты, Закрыть |
| `OTHER` | Другие вкладки | Только Закрыть |

### Поля класса

| Поле | Тип | Назначение |
|------|-----|------------|
| `context` | Context | Контекст приложения |
| `tabLayout` | TabLayout | Компонент TabLayout из Material Design |
| `mainContent` | View | LinearLayout основной вкладки |
| `secondaryContainer` | FrameLayout | Контейнер для вспомогательных вкладок |
| `secondaryTabs` | List<TabInfo> | Список вспомогательных вкладок |
| `currentTabIndex` | int | Текущий индекс вкладки (0 = основная) |

## Методы и их назначение

### Конструктор: TabManager()

**Сигнатура:**
```java
public TabManager(Context context, TabLayout tabLayout, View mainContent, FrameLayout secondaryContainer)
```

**Назначение:** Инициализация менеджера вкладок, создание основной вкладки "Основная".

**Действия:**
1. Сохранение переданных параметров
2. Создание основной вкладки с текстом "Основная"
3. Установка слушателя `TabLayout.OnTabSelectedListener` для переключения вкладок

---

### Метод: openTab()

**Сигнатура:**
```java
public void openTab(String url, String title)
```

**Назначение:** Открыть URL в новой вспомогательной вкладке. Если вкладка с таким URL уже открыта — переключиться на неё и обновить.

**Зависимости и вызовы:**
1. **Декодирование кириллических ников** (строки 94-110)
   - Для `title = "PINFO"` выполняется декодирование URL из `windows-1251`
   - Извлекается ник из параметра `pinfo.cgi?`
   - Преобразуются спецсимволы: `|` → пробел, `%20` → пробел, `%2B` → `+`

2. **Нормализация URL** - метод `normalizeUrl()`
   - Убирает `www`
   - Убирает завершающие слеши
   - Для форума возвращает только домен

3. **Проверка дубликатов** - поиск существующей вкладки по нормализованному URL

4. **Создание новой вкладки:**
   - inflate `R.layout.tab_secondary`
   - настройка WebView через `setupSecondaryWebView()`
   - добавление в `secondaryContainer`
   - загрузка URL

5. **Определение типа вкладки** - метод `determineTabType()`
   - `FORUM` - если URL содержит `forum.neverlands.ru`
   - `PINFO` - если title = "PINFO" или URL содержит `pinfo`
   - `OTHER` - для всех остальных

6. **Настройка кнопок панели действий** - метод `setupActionButtons()`
   - В зависимости от типа вкладки настраиваются соответствующие кнопки

7. **Создание кастомного вида Tab:**
   - inflate `R.layout.tab_item_with_close`
   - установка заголовка
   - обработчик закрытия с динамическим поиском позиции

8. **Добавление в TabLayout** и переключение на новую вкладку

---

### Метод: closeTab()

**Сигнатура:**
```java
public void closeTab(int tabPosition)
```

**Назначение:** Закрыть вспомогательную вкладку по позиции в TabLayout. Позиция 0 — основная (нельзя закрыть).

**Параметры:**
- `tabPosition` - позиция в TabLayout (1+)

**Действия:**
1. Валидация позиции (не 0, не больше количества вкладок)
2. Остановка загрузки WebView: `webView.stopLoading()`
3. Уничтожение WebView: `webView.destroy()`
4. Удаление View из контейнера: `secondaryContainer.removeView()`
5. Удаление из списка `secondaryTabs`
6. Удаление Tab из TabLayout: `tabLayout.removeTab()`
7. Переключение на предыдущую вкладку

---

### Метод: closeCurrentTab()

**Сигнатура:**
```java
public void closeCurrentTab()
```

**Назначение:** Закрыть текущую активную вспомогательную вкладку.

---

### Метод: switchToTab()

**Сигнатура:**
```java
private void switchToTab(int position)
```

**Назначение:** Переключиться на вкладку по позиции.

**Логика:**
- При `position == 0`: показать `mainContent`, скрыть `secondaryContainer` и все вкладки
- При `position > 0`: скрыть `mainContent`, показать `secondaryContainer` и нужную вкладку

---

### Метод: normalizeUrl()

**Сигнатура:**
```java
private String normalizeUrl(String url)
```

**Назначение:** Нормализовать URL для корректного сравнения.

**Правила:**
1. Убирает `www.`
2. Убирает завершающие слеши
3. Для форума возвращает только домен `forum.neverlands.ru`

---

### Метод: setupSecondaryWebView()

**Сигнатура:**
```java
@SuppressLint("SetJavaScriptEnabled")
private void setupSecondaryWebView(WebView webView)
```

**Назначение:** Настройка WebView для вспомогательной вкладки.

**Настройки WebSettings:**
- `setJavaScriptEnabled(true)` - включить JavaScript
- `setAllowFileAccess(true)` - разрешить доступ к файлам
- `setDomStorageEnabled(true)` - включить DOM storage
- `setUseWideViewPort(true)` - использовать широкий viewport
- `setLoadWithOverviewMode(true)` - загружать в режиме обзора
- `setSupportZoom(true)` - поддержка зума
- `setBuiltInZoomControls(true)` - встроенные элементы зума
- `setDisplayZoomControls(false)` - скрыть кнопки зума
- `setMixedContentMode(MIXED_CONTENT_ALWAYS_ALLOW)` - разрешить смешанный контент

**Дополнительно:**
- Добавление JS-моста: `webView.addJavascriptInterface(webAppInterface, "AndroidBridge")`
- Настройка CookieManager
- Установка WebViewClient с перехватом запросов

**WebViewClient перехватывает:**
1. `shouldInterceptRequest()` - вызов `WebViewRequestInterceptor.intercept()`
2. `shouldOverrideUrlLoading()` - перехват не форумных ссылок для открытия в новых вкладках
3. `onPageFinished()` - инъекция JS перехватчика кликов

---

### Метод: injectClickInterceptor()

**Сигнатура:**
```java
private void injectClickInterceptor(WebView view)
```

**Назначение:** Инъекция JavaScript для перехвата кликов по ссылкам во вторичных вкладках.

**Логика:**
1. Проверка наличия флага `_clickInterceptorInjected`
2. Добавление слушателя `click` на document
3. Перехват ТОЛЬКО не форумных ссылок:
   - `pinfo.cgi`
   - `ch.php`
   - `log.php`
   - `fight`
   - `pname.cgi`
   - `pbots.cgi`
4. Вызов `AndroidBridge.openInNewTab(href, title)` для открытия в новой вкладке
5. Форумные ссылки (`forum.neverlands.ru`) открываются внутри WebView без перехвата

---

### Метод: destroyAll()

**Сигнатура:**
```java
public void destroyAll()
```

**Назначение:** Уничтожить все вспомогательные вкладки (вызывается при onDestroy).

**Действия:**
1. Остановка загрузки и уничтожение всех WebView
2. Очистка списка `secondaryTabs`

---

### Метод: determineTabType()

**Сигнатура:**
```java
private TabType determineTabType(String url, String title)
```

**Назначение:** Определить тип вкладки по URL и заголовку для настройки кнопок панели действий.

**Логика:**
- `FORUM` - если URL содержит `forum.neverlands.ru`
- `PINFO` - если title = "PINFO" или URL содержит `pinfo`
- `OTHER` - для всех остальных случаев

---

### Метод: setupActionButtons()

**Сигнатура:**
```java
private void setupActionButtons(View contentView, TabInfo tabInfo)
```

**Назначение:** Настроить кнопки панели действий в зависимости от типа вкладки.

**Для FORUM:**
- Кнопка 1: "Назад" → `webView.goBack()`
- Кнопка 2: "Обновить" → `webView.reload()`
- Кнопка 3: "Копировать URL" → копирование URL в буфер обмена

**Для PINFO:**
- Кнопка 1: "Обновить" → `webView.reload()`
- Кнопка 2: "В контакты" → добавление игрока в контакты
- Кнопка 3: "Закрыть" → закрытие текущей вкладки

**Для OTHER:**
- Кнопки 1, 2 скрыты
- Кнопка 3: "Закрыть" → закрытие текущей вкладки

---

### Метод: copyToClipboard()

**Сигнатура:**
```java
private void copyToClipboard(String text)
```

**Назначение:** Скопировать текст в буфер обмена устройства.

**Использование:** Для кнопки "Копировать URL" на вкладках форума.

---

### Метод: addToContacts()

**Сигнатура:**
```java
private void addToContacts(String playerName)
```

**Назначение:** Добавить игрока в контакты.

**Примечание:** В текущей реализации - заглушка (Toast-сообщение). Требует интеграции с ContactManager.

---

### Методы-геттеры

| Метод | Назначение |
|-------|------------|
| `getCurrentTabIndex()` | Получить текущий индекс вкладки |
| `getSecondaryTabCount()` | Получить количество вспомогательных вкладок |

## Зависимости

### Android SDK
- `android.content.Context`
- `android.content.ClipData`
- `android.content.ClipboardManager`
- `android.annotation.SuppressLint`
- `android.util.Log`
- `android.view.LayoutInflater`
- `android.view.View`
- `android.webkit.CookieManager`
- `android.webkit.WebSettings`
- `android.webkit.WebView`
- `android.webkit.WebResourceRequest`
- `android.webkit.WebResourceResponse`
- `android.webkit.WebViewClient`
- `android.widget.Button`
- `android.widget.FrameLayout`
- `android.widget.Toast`
- `android.widget.ImageButton`
- `android.widget.TextView`
- `com.google.android.material.tabs.TabLayout`

### Стандартные Java
- `java.net.URLDecoder`
- `java.util.ArrayList`
- `java.util.List`

### Внутренние классы проекта
- `ru.neverlands.abclient.R` - ресурсы приложения
- `ru.neverlands.abclient.bridge.WebAppInterface` - JS-мост для коммуникации с WebView
- `ru.neverlands.abclient.webview.WebViewRequestInterceptor` - перехватчик запросов WebView

## Исправления и особенности

### Исправление бага с кнопками закрытия (строки 155-173)

**Проблема:** Раньше кнопка закрытия использовала захваченную при создании позицию, которая становилась неактуальной после закрытия других вкладок.

**Решение:** При клике на кнопку закрытия выполняется динамический поиск актуальной позиции:
```java
for (int i = 0; i < tabLayout.getTabCount(); i++) {
    TabLayout.Tab tab = tabLayout.getTabAt(i);
    if (tab != null && tab.getCustomView() == finalTabView) {
        position = i;
        break;
    }
}
```

### Добавление панели кнопок действий

**Описание:** На каждой вспомогательной вкладке добавлена панель кнопок (аналог функционала ПК-версии). Набор кнопок зависит от типа вкладки:

**Для вкладок форума:**
- Кнопка 1: "Назад" - переход на предыдущую страницу в истории WebView
- Кнопка 2: "Обновить" - перезагрузка текущей страницы
- Кнопка 3: "Копировать URL" - копирование текущего URL в буфер обмена

**Для вкладок PINFO:**
- Кнопка 1: "Обновить" - перезагрузка текущей страницы
- Кнопка 2: "В контакты" - добавление персонажа в контакты (заглушка)
- Кнопка 3: "Закрыть" - закрытие текущей вкладки

**Для других вкладок:**
- Только кнопка 3: "Закрыть"

**Файлы:**
- `action_buttons_bar.xml` - layout панели кнопок
- `tab_secondary.xml` - модифицирован для включения панели кнопок
- `colors.xml` - добавлен цвет `tab_button_bar_background`

## Связь с другими компонентами

| Компонент | Взаимодействие |
|-----------|----------------|
| `MainActivity` | Создает экземпляр TabManager, вызывает `openTab()`, `closeCurrentTab()`, `destroyAll()` |
| `WebAppInterface` | Предоставляет метод `openInNewTab()` для JS-моста |
| `WebViewRequestInterceptor` | Перехватывает запросы для фильтрации/модификации |
| `R.layout.tab_secondary` | Layout для содержимого вспомогательной вкладки (включает панель кнопок) |
| `R.layout.tab_item_with_close` | Layout для кастомного Tab с кнопкой закрытия |
| `R.layout.action_buttons_bar` | Layout для панели кнопок действий (Назад, Обновить, Копировать URL и др.) |
| `R.color.tab_button_bar_background` | Цвет фона панели кнопок |
| `ContactManager` (заглушка) | Интерфейс для добавления игроков в контакты |
