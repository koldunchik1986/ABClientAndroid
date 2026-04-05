# Задача: миникарта в окне «Навигатор» + отдельные настройки «Карта навигатора»

## Чеклист реализации

- [x] Добавить профильные поля миникарты в `UserConfig` (`MapMiniWidth`, `MapMiniHeight`, `MapMiniScale`, `MapMiniCellFontSize`)
- [x] Расширить `<mapset>` load/save атрибутами `miniwidth`, `miniheight`, `miniscale`, `minicellfontsize`
- [x] Добавить настройки миникарты в `Настройки -> Карта`
- [x] Добавить миникарту в `Navigator.showDialog()` над строкой «Текущая позиция»
- [x] Сделать клик по миникарте в режиме `центр + выбор клетки` (без запуска `AutoMoving`)
- [x] Центрировать миникарту при выборе клетки из списка/автокомплита
- [x] Добавить отдельный JS bridge миникарты для `mapnav.js`
- [x] Перевести `mapnav.js` на динамические параметры `GetHalfMapWidth/GetHalfMapHeight/GetMapScale`
- [x] Использовать общий рендер текста клетки через `WebAppInterface` с отдельным mini-font (`MapMiniCellFontSize`)
- [x] Добавить трассировку цепочки миникарты в `Logcat + FileLogger` (`NAV_MINIMAP_TRACE`)
- [x] Прогнать техпроверку `:app:compileDebugJavaWithJavac`

## Затронутые файлы

- `app/src/main/java/ru/neverlands/abclient/model/UserConfig.java`
- `app/src/main/java/ru/neverlands/abclient/SettingsActivity.java`
- `app/src/main/java/ru/neverlands/abclient/ui/Navigator.java`
- `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java`
- `app/src/main/assets/mapnav.js`
- `app/src/main/res/xml/root_preferences.xml`

## Технические примечания

- Миникарта использует отдельные профильные параметры и не влияет на основную карту.
- `MoveTo` в mini bridge не вызывает `startAutoMoving`; навигация стартует только по кнопке «Начать».
- Для исключения гонок и утечек `WebView` в навигаторе очищается в `dialog.setOnDismissListener(...)`.
