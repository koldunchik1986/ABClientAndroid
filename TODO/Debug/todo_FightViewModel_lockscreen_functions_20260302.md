# Детальный TODO: `FightViewModel.java` (lockscreen/background)

## Контекст
- [ ] `FightViewModel` формирует боевые submit-действия через `LezFight`.
- [ ] При остановке цепочки важно понять: ломается парсинг, генерация submit или доставка submit в WebView.

## Функции и задачи

### `processFightHtml(String)`
- [ ] Проверить, вызывается ли метод регулярно после блокировки экрана.
- [ ] Проверить, что при активном авто-бое публикуется `_submitAction`.

### `autoTurnOnce(String)`
- [ ] Проверить поведение «одноразового» автохода в фоне.
- [ ] Проверить ветку `IsWaitingForNextTurn` и ее влияние на пропуски ударов.

### `toggleAutoBattle()`
- [ ] Проверить, что локальный UI-флаг не конфликтует с `AppVars.Autoboi`.
- [ ] Проверить, нет ли рассинхрона после lifecycle-переходов.

### `autoSelect(String)`
- [ ] Проверить, продолжает ли публиковаться submit в lockscreen-кейсе.

### `onActionSubmitted()`
- [ ] Проверить, не очищается ли событие слишком рано при деградации UI-потока.

## Зависимости
- [ ] `LezFight` (парсинг боя + формирование `Result`).
- [ ] `MainActivity` observer (`evaluateJavascript("AutoSubmit(...)")`).
- [ ] `AppVars.Autoboi`.

## Ожидаемый результат анализа
- [ ] Подтверждение: проблема в генерации submit-команды или в ее доставке/исполнении WebView при фоне.

## Промежуточные выводы (статический анализ, 2026-03-02)
- [x] `FightViewModel` генерирует submit-действия в фоне (`new Thread`), но финальная отправка идет через observer в `MainActivity` (`evaluateJavascript`).
- [x] Даже при генерации `Result` цепочка упирается в активность WebView/UI-потока.
- [x] Отдельного фонового транспорта submit-команд (вне UI WebView) в текущей реализации нет.
- [ ] Нужна runtime-проверка: перестаёт ли формироваться `_submitAction` или перестаёт исполняться `AutoSubmit(...)`.

## Обновление по runtime-логу (2026-03-02)
- [x] По `Logs/logcat_runtime_20260302_11.txt` подтвержден рассинхрон: `processFightHtml` вызывался, но при `AppVars.Autoboi=AutoboiOn` внутренний флаг был `autoBattleActive=false`.
- [x] Внесён фикс: `processFightHtml(...)` перешёл на runtime-проверку (`UI flag || AppVars.Autoboi || Profile.LezDoAutoboi`).
