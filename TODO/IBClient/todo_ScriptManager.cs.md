# План портирования ScriptManager.cs для IBC item bridge

Файл `ScriptManager.cs` из восстановленного IBC runtime содержит JS bridge, доступный страницам через `window.external`. Этот анализ фиксирует recovered-методы item bridge: `GetItemID`, `GetItemVcode`, `GetItemPrice`.

## Источник анализа

- IBC runtime decompile: `IBClient/decompiled_runtime_v2/ABClient/ScriptManager.cs`.
- Целевые методы: `GetItemID()` строки 2189-2266, `GetItemVcode()` строки 2269-2297, `GetItemPrice()` строки 2300-2338.
- Связанные global-state поля: `vf0BEtfHl7`, `qZIBY9ZYgG`, `KMIBQYxb5T`, `imJBUbacx4`, `D1j5y323ty`.

## Функциональность в C#

- `GetItemID()` возвращает `0`, если нет контекста item action (`vf0BEtfHl7` пустой). Иначе парсит первый CSV-токен из `qZIBY9ZYgG` как `int` item id.
- `GetItemID()` формирует диагностическое сообщение через main-form bridge, когда данные вещи неполные или вещь уже занята/отсутствует.
- `GetItemVcode()` возвращает пустую строку без item context. Иначе берет последний CSV-токен из `qZIBY9ZYgG` и trim по пробелам/одинарным кавычкам.
- `GetItemPrice()` возвращает пустую строку, если нет item context или `KMIBQYxb5T` пустой. Иначе возвращает `KMIBQYxb5T` как цену.
- Методы не выполняют HTTP сами: они отдают JS-слою id/vcode/price, а отправка действия происходит в HTML/JS или через main-form callbacks.
- Decrypted `xotL8MHOZqRXPpGhnFY.cs` подтвердил два прямых JS-injection use-case: `shop_ajax.php` вызывает `shop_item_sell(id, vcode)`, а `market_ajax.php?action=place_item_put` вызывает `place_item_put(id, price, vcode)`.
- Клан-казна использует отдельный direct-redirect контур `main.php?get_id=29&uid=...`, поэтому `GetItemID` / `GetItemVcode` / `GetItemPrice` нельзя считать доказанным контуром взятия из казны.
- Уточнение по клан-казне: `D28HZRpBoY(...)` не берет id/vcode через `ScriptManager`, а использует очередь `k1HBZSBytl` из выбранного комплекта (`complects.xml`, `MNlBVcP8Me`) и парсит готовую ссылку `main.php?get_id=29&uid=<itemId>...` прямо из HTML казны.

## Сравнение с ANClient

- В ANClient есть `ScriptManager`, но по текущему diff отдельная форма `FormTreasure` и `GetItemID`/`GetItemVcode`/`GetItemPrice` как IBC-контур не подтверждены.
- ANClient `ShopEntry`/`ShopAjaxPhp` покрывает shop sell flow и не заменяет IBC item bridge.

## Сравнение с Android

- Android уже имеет `WebAppInterface` как основной `AndroidBridge`/`window.external` bridge.
- В Android есть множество `@JavascriptInterface`, но `GetItemID`, `GetItemVcode`, `GetItemPrice` не найдены.
- Текущий `ShopEntry` вызывает `AndroidBridge.startBulkOldSell(...)`, но bridge-метод пока является stub и не связан с recovered `GetItem*` bridge.

## План реализации на Android

- [ ] Не создавать новый bridge-класс: расширять существующий `WebAppInterface`.
- [ ] Перед добавлением методов восстановить, где Android должен хранить аналоги `vf0BEtfHl7`, `qZIBY9ZYgG`, `KMIBQYxb5T`, `imJBUbacx4`.
- [ ] Реализовать `@JavascriptInterface public int GetItemID()`, `public String GetItemVcode()`, `public String GetItemPrice()` с поведением IBC и безопасным fallback.
- [ ] Использовать `SessionManager` для любых новых защищенных запросов, даже если IBC брал vcode из CSV.
- [ ] Логировать критичный flow через `AppLog`, не `android.util.Log` и не `System.out.println`.
- [ ] Проверить, что ручные HTML-клики shop/market и казны выполняются с первого раза и не конфликтуют с background probes.

## Открытые вопросы

- [x] Найден HTML-injection, который вызывает `window.external.GetItemID()` / `GetItemVcode()` / `GetItemPrice()` для shop/market flows.
- [ ] Дешифровать/разобрать, где выставляются `vf0BEtfHl7`, `qZIBY9ZYgG`, `KMIBQYxb5T`, `imJBUbacx4`.
- [ ] Сопоставить IBC item bridge с Android `ShopEntry`/inventory flow, чтобы не смешать shop sell, market place-put и clan treasury take.
