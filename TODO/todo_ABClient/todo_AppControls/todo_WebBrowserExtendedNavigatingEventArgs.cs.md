# План портирования WebBrowserExtendedNavigatingEventArgs.cs

Файл `WebBrowserExtendedNavigatingEventArgs.cs` - это класс, определяющий аргументы для кастомного события.

## Функциональность в C#

*   **Назначение**: Служить контейнером данных для события `BeforeNavigate` в кастомном контроле `ExtendedWebBrowser`.
*   **Реализация**: 
    *   Наследуется от `System.ComponentModel.CancelEventArgs`, что добавляет ему свойство `Cancel` типа `bool`.
    *   Добавляет свойства `Address` (URL) и `Frame` (имя фрейма) для передачи их в обработчик события.

## Проверка на существующую реализацию в Android

- **Результат:** Неприменимо.
- **Объяснение**: Этот класс неразрывно связан с `ExtendedWebBrowser.cs`. Поскольку `ExtendedWebBrowser` не портируется, а его функциональность заменяется `WebViewClient`, то и этот класс аргументов не нужен. В Android метод `WebViewClient.shouldOverrideUrlLoading` получает в качестве аргумента объект `WebResourceRequest`, который уже содержит всю необходимую информацию (URL, заголовки и т.д.).

## Решение для портирования на Android

Никаких действий не требуется.

## План реализации

- [x] **Задача решена.** Функциональность нерелевантна для Android.
- [ ] **Обновить `todo_AppControls.md`**, отметив `WebBrowserExtendedNavigatingEventArgs.cs` как проанализированный и не требующий портирования (`[+]`).
