# TODO: Портирование menuitemDoSearchBox -> "Авто-Клад" (2026-03-23)

## Цель
- [x] Перенести логику C# `menuitemDoSearchBox` ("Ходим, ищем клад") в Android.
- [x] Добавить quickbutton "Авто-Клад" с иконкой `http://image.neverlands.ru/achievement/75/a_75_10.gif`.

## Реализация
- [x] Добавить тип действия `AUTO_TREASURE` в `QuickActionType`.
- [x] Добавить переключение "Авто-Клад" в `AutoFunctionsManager` (`is/toggle/set` + sync с `Profile.AutoDig`).
- [x] Добавить runtime-флаг `AppVars.DoSearchBox`.
- [x] Добавить runtime-карту посещений клеток `AppVars.SearchBoxVisited`.
- [x] Портировать BFS-поиск следующей клетки (`FindNextDestForBox`) в `MapAjax`.
- [x] Добавить ротацию destination при достижении цели в режиме `DoSearchBox`.
- [x] Добавить автозапуск обхода из `MainPhp` при `DoSearchBox && !AutoMoving && NeverTimer passed`.
- [x] Добавить строку статуса `Ищем клад...` в `mainPhpWtime`.
- [x] Добавить иконку в `QuickButtonsPanel` и `FunctionListAdapter`.

## Проверка
- [ ] Ручной тест: включение/выключение "Авто-Клад" кнопкой.
- [ ] Ручной тест: после достижения клетки маршрут автоматически перестраивается.
- [ ] Ручной тест: статус в верхнем фрейме показывает "Ищем клад...".

## Дополнительно (C# parity: `checkDoStopOnDig`)
- [x] Добавить профильный флаг `DoStopOnDig` (default `true`) в `UserConfig`.
- [x] Подключить загрузку/сохранение узла профиля `<dostopondig>`.
- [x] Добавить переключатель `Останавливаться на кладе` в разделе `Карта` (`root_preferences.xml` + `SettingsActivity`).
- [x] В `MainPhp` добавить проверку маркера `["dig","Копать",` с остановкой `Авто-Клад`/навигации.
- [x] При срабатывании отправлять системное сообщение `На текущей клетке обнаружен клад!`.
- [x] При срабатывании подавать звуковой сигнал (Android-аналог `EventSounds.PlayAlarm()`).
