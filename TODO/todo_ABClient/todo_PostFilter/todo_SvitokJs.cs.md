### 1. План портирования SvitokJs.cs

Файл `SvitokJs.cs` представляет собой фильтр для javascript-файла `svitok.js`, который используется при использовании свитков. Основная задача фильтра - добавить отслеживание использования свитков.

### 2. Функциональность в C#

- **Назначение:** Модифицировать `svitok.js` для добавления отслеживания использования свитков.
- **Логика:** Фильтр ищет HTML-код формы использования свитка и добавляет в `onclick` событие кнопки "выполнить" вызов `window.external.TraceDrinkPotion(fornickname.value, \'\'+wnametxt+'\')`.

### 3. Решение для портирования на Android

Эта функциональность может быть реализована с помощью `WebView` и `JavascriptInterface`.

### 4. План реализации

1.  **Создать JavascriptInterface:**
    - Создать класс `WebAppInterface` с методом `TraceDrinkPotion(fornickname, wnametxt)`.
2.  **Внедрить Javascript:**
    - После загрузки страницы в `WebView`, внедрить javascript-код, который переопределяет функцию `magicreform` или ее аналоги, чтобы добавить вызовы методов `WebAppInterface`.
3.  **Реализовать логику в Android:**
    - Реализовать метод `TraceDrinkPotion` в `WebAppInterface` для логирования или отслеживания использования свитков.

- [ ] Создать `WebAppInterface` с методом `TraceDrinkPotion(fornickname, wnametxt)`.
- [ ] Внедрить Javascript для переопределения `magicreform`.
- [ ] Реализовать логику в `WebAppInterface`.
