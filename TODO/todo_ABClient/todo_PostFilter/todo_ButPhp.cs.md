
### 1. План портирования ButPhp.cs

Файл `ButPhp.cs` является фильтром для страницы `but.php`, которая представляет собой фрейм с кнопками управления чатом (отправить, смайлы и т.д.).

### 2. Функциональность в C#

- **Назначение:** Модифицировать HTML-код фрейма с кнопками чата для интеграции с клиентом.
- **Логика:**
    1.  **Синхронизация времени:** Парсит параметры `hour`, `min`, `sec` из URL страницы, чтобы вычислить разницу между временем сервера и клиента. Эта разница (`ServDiff`) сохраняется в `AppVars.Profile` и используется для отображения точного серверного времени в UI клиента.
    2.  **Именование кнопки:** Добавляет `name=butinp` к кнопке отправки сообщения (`b1.gif`). Это позволяет другим частям кода программно "нажимать" на эту кнопку для отправки сообщений в чат.
    3.  **Перехват смайлов:** Заменяет стандартные JavaScript-вызовы `smile_open('')` и `smile_open('2')` на вызовы `window.external.ShowSmiles(1)` и `window.external.ShowSmiles(2)`. Это позволяет перехватить клики по кнопкам смайлов и показать вместо стандартного окна нативное окно выбора смайлов (`FormSmiles`).

### 3. Решение для портирования на Android

Логика этого фильтра достаточно проста и может быть портирована напрямую. Вызовы `window.external` заменяются на вызовы к `JavascriptInterface`.

- **Архитектура:** Логика останется внутри `postfilter.ButPhp.java`.
- **Зависимости:**
    - Требуется передавать `address` в метод `process`, чтобы парсить время.
    - Требуется `WebAppInterface` с методом `showSmiles(index: Int)`.

### 4. План реализации

1.  **Обновить `Filter.java`:**
    - [x] Изменить сигнатуру вызова `ButPhp.process` в главном маршрутизаторе, чтобы передавать в него `address` и `array`: `return ButPhp.process(address, array);`.
2.  **Реализовать `ButPhp.java`:**
    - [x] Изменить метод `process`, чтобы он принимал `String address` и `byte[] array`.
    - [x] Портировать логику парсинга `hour`, `min`, `sec` из `address` и вычисления `AppVars.Profile.ServDiff`.
    - [x] Выполнить замену `html.replace("/b1.gif", "/b1.gif name=butinp")`.
    - [x] Выполнить замены `smile_open('')` на `AndroidBridge.showSmiles(1)` и `smile_open('2')` на `AndroidBridge.showSmiles(2)`.
    - [x] Вернуть измененный HTML в виде `byte[]`.
3.  **Обновить `WebAppInterface.java`:**
    - [x] Добавить метод, аннотированный `@JavascriptInterface`: `public void showSmiles(int index) { ... }`.
    - [x] Внутри метода пока можно разместить заглушку, например, `Toast.makeText(mContext, "Show smiles: " + index, Toast.LENGTH_SHORT).show();`.
4.  **Обновить `todo_PostFilter.md`:**
    - [x] Пометить `ButPhp.cs` как `[x]` проанализированный.

- [x] Модифицировать `Filter.java`.
- [x] Реализовать `ButPhp.java`.
- [x] Добавить метод `showSmiles` в `WebAppInterface.java`.
