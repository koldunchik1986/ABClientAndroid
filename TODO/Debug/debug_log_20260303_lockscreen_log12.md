# Debug log: lockscreen runtime log 12 (2026-03-03)

## 1) Действие
- Проанализирован лог `Logs/logcat_runtime_20260302_12.txt` по фоновой работе при блокировке экрана.

## Результат
- `AutoModeFgService` работает стабильно весь интервал теста: регулярный `uiTick` с шагом ~1 сек.
- После `SCREEN_OFF` (06:01:42) сервис не останавливается и продолжает авто-контур.
- Во время lockscreen есть боевая активность и отправка ударов (`autoTurnOnce: submit posted`) в 06:03:31 и 06:03:34.
- После `SCREEN_ON`/`USER_PRESENT` бой продолжает отрабатывать, есть новые `submit posted` (06:13:12 / 06:13:14 / 06:13:16).
- Критических падений (`FATAL EXCEPTION`) в логе нет.

## 2) Наблюдаемые побочные сигналы
- Есть единичный JS-лог: `Uncaught ReferenceError: AutoSubmit is not defined` (06:13:09.530), после чего цикл восстанавливается и продолжает бить.
- Есть `Server time sync rejected: deltaMs=7195404, maxDeltaMs=300000` на `ch.php` (не блокирует авто-бой, но требует отдельной проверки контура синхронизации времени).

## 3) Вывод
- По этому логу основная проблема «фон останавливается через ~5 минут при lockscreen» не подтверждается.
- Текущее состояние: фон живой, авто-бой выполняется, требуется точечная доработка по исключению единичного `AutoSubmit` race и отдельная проверка фильтра синхронизации времени.

## 4) Исправления по результатам
- В `MainActivity` добавлен безопасный путь отправки боевого действия в WebView:
  - новый метод `submitAutoBattleActionToWebView(...)`,
  - retry `AUTO_SUBMIT_MAX_RETRY_COUNT=3` с `AUTO_SUBMIT_RETRY_DELAY_MS=180`,
  - fallback на `document.ff.submit()`/`document.forms[0].submit()`,
  - замена прямого вызова `AutoSubmit(...)` в observer `FightViewModel.getSubmitAction()`.
- В `WebViewRequestInterceptor` изменён фильтр дельты серверного времени:
  - `shouldApplyServerTime(...)` теперь учитывает `source`,
  - для `source=chat` при уже установленном серверном времени логируется `DEBUG skip`, а не `WARN reject`,
  - поведение `but.php`/жёсткой синхронизации не изменено.

## 5) Проверка сборки
- Выполнено: `./gradlew :app:compileDebugJavaWithJavac -x lint`.
- Результат: успешно, ошибок компиляции нет.
