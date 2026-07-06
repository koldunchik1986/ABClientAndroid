# План портирования FormTurotor.cs

Файл `FormTurotor.cs` из восстановленного IBC runtime реализует WinForms-диалог настройки режима `Остров Туротор / Гиблая Топь`.

## Источник анализа

- IBC runtime decompile: `IBClient/decompiled_runtime_v2_strings/ABClient.MyForms/FormTurotor.cs`.
- Связанные runtime-строки: `Остров Туротор / Гиблая Топь`, `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd`, `IntervalStart`, `IntervalEnd`, `Временной интервал с`, `до`.
- Связанные profile-поля: `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd`.

## Функциональность в C#

- Назначение: дать пользователю выбрать основной и запасной маршрут/точку для островного режима и задать часовой интервал работы.
- UI: две `ComboBox` с одинаковым набором из 9 вариантов, группа `IntervalStart`/`IntervalEnd`, два `NumericUpDown` с диапазоном `0..23`, кнопки OK/Cancel.
- Обработчик OK: `SDbkF6KP4A(...)` читает выбранные значения, проверяет непустые значения, проверяет часы `0..23` и запрещает равные start/end.
- Сохранение: в профиль пишутся `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd`.
- Нормализация маршрутов: выбранные строки обрезаются до первого шестисимвольного идентификатора клетки/региона перед сохранением.
- Runtime-использование: восстановленный `VulkAmRGuQQBxIOEF5d.BYePjRghxE()` сверяет текущий час с `TurotorIntervalStart`/`TurotorIntevalEnd` и запускает переход в выбранный маршрут.
- Семантика маршрута: при выборе цели через `QK477HhQwCpV5uhVVPvA()` runtime берет `TurotorToGoSecondary`, иначе `TurotorToGo`. Метод `QK477HhQwCpV5uhVVPvA()` делегирует в `Y2qPKQYd9ZDUjRRf7UA.LlfYfR769F()`, который проверяет, находится ли текущий час внутри `TurotorIntervalStart` / `TurotorIntevalEnd` с учетом `fiwEA79akn` server-time offset и wrap-around интервала через полночь.
- Teleport contract: для `6,"Остров Туротор"` injected JS заменяет `view_teleportsp();` на `document.location='main.php?get_id=16&act=3&sp='+telep[0][0]+'&vcode='+telep[0][3];`; для `7,"Гиблая Топь"` используется `telep[1][0]` / `telep[1][3]`.
- Scroll fallback: `U7mIaljKqo(...)` ищет action `Использовать Свиток Телепорта сейчас?` с `subid=6` для Туротора и `subid=7` для Гиблой Топи, затем делает redirect через найденный URL.

## Сравнение с ANClient

- В ANClient отдельной формы `FormTurotor` нет.
- Есть быстрый телепорт `Телепорт (Остров Туротор)` через `MainPhpFastIsland(...)`.
- Навигация учитывает `MapPath.IsIslandRequired` и вызывает fast-action островного телепорта перед построением пути на остров.
- Настройки основного/запасного маршрута и временного интервала в ANClient по inventory не подтверждены.

## Сравнение с Android

- Android уже содержит быстрый action `Телепорт (Остров Туротор)` в `FastActionManager.fastAttackIslandPot()`.
- Android `FastActionManager.mainPhpFastIsland(...)` реализует ANClient redirect-сценарий и дополнительный fallback через `w28_form`, `wsubid=22`, `wtelid=13`.
- Android `MapPath.isIslandRequired` и список island cells уже перенесены.
- Отдельной настройки `TurotorToGo`/`TurotorToGoSecondary`/`TurotorIntervalStart`/`TurotorIntevalEnd` в Android не найдено.

## План реализации на Android

- [ ] Не дублировать существующий quick-action: использовать текущий `FastActionManager.fastAttackIslandPot()` и `MapPath.isIslandRequired`.
- [ ] Добавить profile/settings поля `TurotorToGo`, `TurotorToGoSecondary`, `TurotorIntervalStart`, `TurotorIntevalEnd` после подтверждения формата хранения профиля Android.
- [ ] Добавить UI настройки островного режима в существующий экран настроек, а не создавать параллельный контур.
- [ ] Реализовать scheduler/handler островного режима: вне интервала использовать `TurotorToGo`, внутри интервала использовать `TurotorToGoSecondary`; поддержать wrap-around `start > end`.
- [ ] Перед защищенными переходами использовать `SessionManager.getValidVCodeForAction(...)`; не использовать `AppVars.VCode`.
- [ ] Сохранить ручной приоритет WebView: не запускать auto-probe при ручном HTML-контексте и островных кликах.

## Открытые вопросы

- [x] После string-decrypt уточнены подписи 9 вариантов в `ComboBox`: `11-458 Огры [20]`, `11-457 Огры-берсерки [21]`, `11-456 Огры-берсерки [22]`, `11-488 Огры-берсерки [23]`, `11-487 Огры-берсерки [24]`, `28-462 Огры-защитники [25]`, `28-463 Огры-защитники [26]`, `28-464 Огры-защитники [27]`, `28-465 Огры-защитники [28]`.
- [x] Семантика перехода на `TurotorToGoSecondary` уточнена: это маршрут для заданного часового интервала; основной `TurotorToGo` используется вне интервала.
- [x] Внутренняя семантика `Y2qPKQYd9ZDUjRRf7UA.LlfYfR769F()` уточнена: проверка текущего часа относительно `TurotorIntervalStart` / `TurotorIntevalEnd`, с учетом `fiwEA79akn` и перехода через полночь.
- [ ] Уточнить, распространяется ли режим на `Гиблая Топь` только через teleport entry/scroll или через отдельный сценарий боя/ресурсов.
