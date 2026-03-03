# Задача: автопитьё эликсира восстановления по верхнему фрейму

## Входные условия
- Настройки AutoBoi:
  - `Пить зелья HP если HP ниже` (`LezDoDrinkHp` + `LezDrinkHp`).
  - `Пить зелья MA если MA ниже` (`LezDoDrinkMa` + `LezDrinkMa`).
- Триггер проверки: при обработке `main.php`, когда в HTML есть `ins_HP(curHp,maxHp,curMa,maxMa,...)`.
- Целевое действие: единый fast-action `Эликсир Восстановления` (восстанавливает и HP, и MA).

## План реализации
- [x] Проанализировать текущие точки интеграции (`MainPhp`, `FastActionManager`, `UserConfig`).
- [x] Добавить проверку порогов HP/MA при получении верхнего фрейма (`ins_HP`).
- [x] Вызвать `FastActionManager.fastAttackMomentRestoreElixir()` при срабатывании любого из порогов.
- [x] Гарантировать одно действие, если одновременно сработали HP и MA.
- [x] Добавить защиту от спама повторных триггеров (кулдаун/guard).
- [x] Проверить сборку debug.

## Технические заметки
- Проверка должна выполняться только вне боевого фрейма, чтобы не мешать боевому конвейеру.
- Если уже активен другой fast-конвейер (`AppVars.FastNeed == true`), новый старт не делать.
- Для диагностики добавить отдельный лог-префикс `AUTO_DRINK_TRACE`.

## Реализация
- [x] `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
  - добавлен метод `tryTriggerAutoDrinkRestoreElixir(...)`, который:
    - парсит `ins_HP(curHp,maxHp,curMa,maxMa,...)`,
    - проверяет пороги `LezDrinkHp`/`LezDrinkMa` при флагах `LezDoDrinkHp`/`LezDoDrinkMa`,
    - запускает единый fast-action `FastActionManager.fastAttackMomentRestoreElixir()` при `hpBelow || maBelow`,
    - не запускается в боевом фрейме, при активном `FastNeed`, на `get_id=43` и при видимой капче.
  - добавлен кулдаун триггера `AUTO_DRINK_TRIGGER_COOLDOWN_MS = 2500` для защиты от спама.
  - добавлено логирование с префиксом `AUTO_DRINK_TRACE`.

## Проверка
- [x] Сборка: `.\gradlew.bat assembleDebug`
- [x] Результат: `BUILD SUCCESSFUL`.

## Доп.фикс по логу `logcat_runtime_20260303_12.txt`
- [x] Убрано ошибочное исключение `get_id=61` из автопитья (именно на этом URL после боя приходил нужный `ins_HP`).
- [x] Добавлены диагностические причины пропуска с префиксом `AUTO_DRINK_TRACE`:
  - `auto-fight disabled in preferences`,
  - `FastNeed active`,
  - `get_id=43 action page`,
  - `captcha dialog visible`,
  - `ins_HP snapshot missing or invalid`,
  - `no-trigger` (пороги не достигнуты).
- [x] Сборка после фикса: `BUILD SUCCESSFUL`.
