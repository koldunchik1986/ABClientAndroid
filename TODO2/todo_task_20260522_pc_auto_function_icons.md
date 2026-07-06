# Задача: иконки авто-функций в ANClient

## Контекст

Нужно заменить текстовые кнопки авто-функций на панели `ANClient` на иконки, используя те же источники иконок, которые применяются в `app2`.

## Найденный источник в app2

Иконки авто-функций заданы в `app2/src/main/java/ru/neverlands/anclient/ui/QuickButtonsPanel.java` в методе `getIconUrlForAction()`:

- [x] `AUTO_FISH` / Авторыбалка: `http://image.neverlands.ru/achievement/40/a_40_10.gif`.
- [x] `AUTO_SKIN` / Авторазделка: `http://image.neverlands.ru/achievement/70/a_70_10.gif`.
- [x] `AUTO_CUT` / Авто-Травник: `http://image.neverlands.ru/achievement/20/a_20_10.gif`.
- [x] `AUTO_LUMBERJACK` / Авто-Лесоруб: `http://image.neverlands.ru/achievement/30/a_30_10.gif`.
- [x] `AUTO_MINE` / Авто-Шахтёр: `http://image.neverlands.ru/achievement/60/a_60_10.gif`.

## Найденный UI-контур в ANClient

- [x] `buttonAutoFish`, `buttonAutoSkin`, `buttonHerbAutoCut` объявлены в `ANClient/ANForms/FormMain.Designer.cs` как текстовые `ToolStripButton`.
- [x] `buttonAutoLumberjack`, `buttonAutoMine` создаются динамически в `ANClient/ANForms/FormMain.cs`.
- [x] Состояние кнопок синхронизируется через `SyncAutoCutToolbarButtons()` и не требует отдельного контура.

## План реализации

- [x] Скачать пять GIF-иконок из URL, найденных в `app2`, в `ANClient/Icons/AutoFunctions/` для локального внедрения в приложение.
- [x] Добавить файлы иконок в `ANClient.csproj` как `None` и в `Properties/Resources.resx` как `ResXFileRef`.
- [x] В `FormMain.cs` добавить общий helper настройки `ToolStripButton` на `Image`-режим с сохранением tooltip.
- [x] Применить helper к `buttonAutoFish`, `buttonAutoSkin`, `buttonHerbAutoCut`, `buttonAutoLumberjack`, `buttonAutoMine`.
- [x] Оставить бизнес-логику авто-функций без изменений: меняется только визуальное представление кнопок.
- [x] Собрать проект через MSBuild и выполнить targeted checks на UTF-8/BOM/mojibake и наличие ресурсов.

## Инварианты

- Не создавать новый контур авто-функций.
- Не менять обработчики кликов и состояния `Checked`.
- Сохранять tooltips с русскими названиями функций.
- Не трогать `ABClient/` и unrelated dirty files.
