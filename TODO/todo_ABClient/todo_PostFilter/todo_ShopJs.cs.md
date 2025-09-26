### 1. План портирования ShopJs.cs

Файл `ShopJs.cs` представляет собой фильтр для javascript-файла `shop.js`, который используется в магазинах. Основная задача фильтра - добавить функционал массовой продажи вещей.

### 2. Функциональность в C#

- **Назначение:** Модифицировать `shop.js` для добавления возможности массовой продажи предметов.
- **Логика:** Фильтр ищет строку `AjaxPost('shop_ajax.php', data, function(xdata) {` и заменяет ее на код, который выполняет следующие действия:
    1. Вызывает `window.external.BulkSellOldArg1()` и `window.external.BulkSellOldArg2()` для получения аргументов для продажи.
    2. Если `arg1` (ID предмета) больше 0, вызывается функция `shop_item_sell(arg1, arg2)`.

### 3. Решение для портирования на Android

Эта функциональность может быть реализована с помощью `WebView` и `JavascriptInterface`.

### 4. План реализации

1.  **Создать JavascriptInterface:**
    - Создать класс `WebAppInterface` с методами `BulkSellOldArg1()` и `BulkSellOldArg2()`, которые будут возвращать соответствующие значения из Android-кода.
2.  **Внедрить Javascript:**
    - После загрузки страницы в `WebView`, внедрить javascript-код, который переопределяет функцию `AjaxPost` или ее callback, чтобы добавить вызовы методов `WebAppInterface`.
3.  **Реализовать логику в Android:**
    - Реализовать методы `BulkSellOldArg1()` и `BulkSellOldArg2()` в `WebAppInterface`, чтобы они возвращали ID предмета и количество для продажи.

- [ ] Создать `WebAppInterface` с методами `BulkSellOldArg1()` и `BulkSellOldArg2()`.
- [ ] Внедрить Javascript для переопределения `AjaxPost`.
- [ ] Реализовать логику в `WebAppInterface`.