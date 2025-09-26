
### 1. План портирования NavScriptManager.cs

Файл `NavScriptManager.cs` представляет собой "мост" между Javascript, выполняющимся на HTML-странице карты, и C#-кодом приложения. Атрибут `[ComVisible(true)]` делает этот класс доступным для Javascript под именем `window.external`.

### 2. Функциональность в C#

- **Назначение:** Позволить Javascript-коду вызывать C#-методы и получать данные от приложения.
- **Методы:**
    - `MoveTo(dest)`: Вызывается из JS при клике на ячейку карты. Запускает процесс авто-навигации.
    - `GetCellLabel(x, y)`, `IsCellExists(x, y)`, `GenMoveLink(x, y)`: Методы, которые JS может вызвать, чтобы получить информацию о ячейке.
    - `IsCellInPath(x, y)`: Позволяет JS узнать, является ли ячейка частью текущего пути (для подсветки).
    - `CellDivText(...)`, `CellAltText(...)`: Вызываются из JS для получения готового HTML-кода для информационных слоев и подсказок на карте.

### 3. Решение для портирования на Android

Концепция "моста" между JS и нативным кодом является стандартной для `WebView` в Android.

- **`@JavascriptInterface`**: Это аннотация в Android, которая является прямым аналогом `[ComVisible(true)]`. Она помечает методы, которые можно вызывать из Javascript.
- **`WebView.addJavascriptInterface()`**: Этот метод используется для добавления объекта-моста в `WebView`.

**Важно:** Если будет принято решение отказаться от HTML-карты в пользу полностью нативной реализации, то этот класс и вся связанная с ним логика генерации HTML (`CellDivText`, `CellAltText`) станут ненужными.

### 4. План реализации (при сохранении HTML-карты)

### 4. План реализации (при сохранении HTML-карты)

Поскольку в Android-коде отсутствует реализация JS-моста для карты, этот план описывает его создание с нуля.

1.  **Создать класс `WebAppInterface` (или `NavScriptManager`):**
    - Этот класс будет содержать все методы, доступные из Javascript.
    - Он должен принимать в конструкторе `ViewModel` или `Repository` для доступа к данным и бизнес-логике.
      ```kotlin
      class WebAppInterface(private val mapViewModel: MapViewModel) {

          @JavascriptInterface
          fun moveTo(destination: String) {
              // Запускаем навигацию через ViewModel
              mapViewModel.navigateTo(destination)
          }

          @JavascriptInterface
          fun getCellLabel(x: Int, y: Int): String? {
              // Синхронный вызов, если данные уже загружены
              return mapViewModel.getLabelForCoordinates(x, y)
          }

          @JavascriptInterface
          fun isCellInPath(x: Int, y: Int): Boolean {
              return mapViewModel.isCellInCurrentPath(x, y)
          }

          // И так далее для остальных методов...
      }
      ```

2.  **Настроить `WebView`:**
    - В `Activity` или `Fragment`, где находится `WebView`, нужно включить Javascript и добавить интерфейс.
      ```kotlin
      val webView: WebView = findViewById(R.id.webView)
      
      // Включение Javascript
      webView.settings.javaScriptEnabled = true

      // Создание и добавление интерфейса. "external" - это имя, 
      // под которым объект будет доступен в JS (window.external)
      val mapViewModel = ... // Получаем ViewModel
      webView.addJavascriptInterface(WebAppInterface(mapViewModel), "external")

      // Загрузка HTML-страницы карты
      webView.loadUrl("file:///android_asset/map.html") // Пример
      ```

3.  **Адаптировать Javascript (`map.js`):**
    - Убедиться, что вызовы в `map.js` соответствуют методам в `WebAppInterface`.
    - Например, `window.external.MoveTo(dest)` в JS будет вызывать метод `moveTo(dest)` в Kotlin.

- [ ] Создать класс `WebAppInterface` со всеми необходимыми методами, помеченными `@JavascriptInterface`.
- [ ] Настроить `WebView` для работы с Javascript и подключить `WebAppInterface`.
- [ ] Проверить и при необходимости адаптировать JS-код в `map.js` для корректной работы с Android-мостом.
