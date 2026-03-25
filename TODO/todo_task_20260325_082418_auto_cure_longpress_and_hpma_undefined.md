# Задача: Авто-Лечение (long-press настройки) и фикс `undefined` в HP/MA

## Контекст
- В блоке «Ваш персонаж» в левой части HP/MA иногда отображались как `undefined`.
- Для quick-кнопки «Авто-Лечение» нужны отдельные настройки по long-press (аналогично «Авто-Бой»).
- Настройки лечения должны быть централизованы, без дублирования логики между UI и postfilter.

## План
- [x] Добавить единые ключи и методы настроек Авто-Лечения в `AutoFunctionsManager`.
- [x] Подключить эти настройки в `RoomManager` (фильтр целей и типов травм).
- [x] Подключить эти настройки в `MainPhp` (фильтр типов травм + self-эликсир).
- [x] Добавить long-press диалог «Настройки авто-лечения» в `QuickButtonsPanel`.
- [x] Убрать дублирующий раздел «Лечение» из боковых общих настроек (`root_preferences.xml`).
- [x] Добавить защитную нормализацию `inshp` в `HpmpJs`, чтобы исключить вывод `undefined`.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.

## Измененные файлы
- `app/src/main/java/ru/neverlands/abclient/manager/AutoFunctionsManager.java`
- `app/src/main/java/ru/neverlands/abclient/manager/RoomManager.java`
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
- `app/src/main/java/ru/neverlands/abclient/ui/QuickButtonsPanel.java`
- `app/src/main/java/ru/neverlands/abclient/postfilter/HpmpJs.java`
- `app/src/main/res/xml/root_preferences.xml`
