# План портирования FormTreasure.cs

Файл `FormTreasure.cs` из восстановленного IBC runtime реализует WinForms-диалог авто-копания кладов: включение режима, выбор допустимых лопат и комплекта после выкапывания.

## Источник анализа

- IBC runtime decompile: `IBClient/decompiled_runtime_v2_strings/ABClient.MyForms/FormTreasure.cs`.
- Runtime-строки формы: `Поиск клада`, `Автоматически выкапывать`, `Использовать лопаты`, `Лопата кладоискателя`, `Лопата археолога`, `Походная лопатка`, `Комплект после выкапывания`, `Ни одной лопаты не выбрано. Автокопание кладов не запущено.`.
- Связанные global-state поля: `TKPDnOaJQc5chbOU6u.W4bBm7jSOr`, `TKPDnOaJQc5chbOU6u.d9PBFLSwqa`, `TKPDnOaJQc5chbOU6u.ujqBpNAlnH`.

## Функциональность в C#

- Назначение: включить/отключить автоматическое выкапывание кладов, выбрать комплект после выкапывания и задать разрешенные типы лопат.
- UI: основной `CheckBox` `Автоматически выкапывать`, группа `Использовать лопаты` из трех `CheckBox`, `ComboBox` `Комплект после выкапывания`, кнопки `Запустить`/`Отмена`.
- Обработчик OK: `Oebk3KKUCK(...)` сохраняет основной флаг `W4bBm7jSOr`, выбранное значение `ujqBpNAlnH`, затем сохраняет три shovel-флага в `d9PBFLSwqa[0..2]`.
- Mapping `d9PBFLSwqa`: `[0] = Лопата кладоискателя`, `[1] = Лопата археолога`, `[2] = Походная лопатка`.
- Runtime-использование: `xotL8MHOZqRXPpGhnFY.cs` читает `d9PBFLSwqa[0]`, `[1]`, `[2]` и строит список допустимых лопат для auto-digging.
- Валидация: если auto-digging включен, но ни одна лопата не выбрана, форма показывает предупреждение через main-form bridge и принудительно сбрасывает `W4bBm7jSOr = false`.
- Применение: после сохранения вызывается обновление/перестройка main-frame через `VulkAmRGuQQBxIOEF5d.sGrFLvsHXe()`.
- Cancel: `FhEkcCoh5B(...)` вызывает `VulkAmRGuQQBxIOEF5d.FRvFhp2Jc0()` без сохранения.

## Сравнение с ANClient

- В ANClient отдельной формы `FormTreasure` по inventory нет.
- Есть `ShopEntry` и `ShopAjaxPhp`, но они относятся к shop/inventory sell flow, а не к auto-digging.
- Строки auto-digging из `FormTreasure` в ANClient не подтверждены.

## Сравнение с Android

- Android содержит `ShopEntry.java` с группировкой shop items и кнопкой `Продать все`, но это не auto-digging.
- Android bridge содержит stub `WebAppInterface.startBulkOldSell(...)`, который относится к shop sell flow.
- Android не содержит подтвержденного аналога `FormTreasure` auto-digging с `W4bBm7jSOr` / `d9PBFLSwqa[0..2]` / `ujqBpNAlnH`.

## План реализации на Android

- [ ] Добавить настройки auto-digging: `W4bBm7jSOr`, `d9PBFLSwqa[0..2]`, `ujqBpNAlnH` в существующий settings/profile контур Android.
- [ ] Реализовать handler auto-digging после отдельного анализа runtime-веток `нужна лопата`, `Предмет: ... (1 шт)` и `Cj8JGINhZI(...)` inventory scan.
- [ ] Использовать существующий postfilter/WebView контур, не смешивать auto-digging с shop sell и clan treasury take.
- [ ] Для защищенных действий брать VCode через `SessionManager.getValidVCodeForAction("treasure_dig")` и обрабатывать `null` fallback reload/skip.
- [ ] Логировать критичный auto-digging flow через `AppLog`/файловый лог.

## Открытые вопросы

- [x] После string-decrypt уточнено: `d9PBFLSwqa[0..2]` не относится к `Все`/`Рары`/`Арты`; это выбор трех лопат.
- [ ] Найти endpoint auto-digging и полный набор параметров.
- [ ] Решить, нужен ли отдельный UI-экран auto-digging или достаточно настройки в существующем settings/profile.
