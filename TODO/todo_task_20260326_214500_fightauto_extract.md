# Задача: вынос Auto-Боя и завершения боя в FightAuto.java

## Контекст
- [x] Требуется вынести боевую обработку из `MainPhp` в отдельный модуль `FightAuto`.
- [x] Нужно сохранить текущую логику finish-flow (FightLink/FEND/CAPTCHA/manual/auto-refresh).
- [x] Нужно сохранить проверки порогов HP/MA/DoStop/DoExit для Auto-Боя.

## План
- [x] Восстановить и завершить `FightAuto.processFight(...)`.
- [x] Подключить bridge-host из `MainPhp` в `FightAuto` без дублирования инфраструктурных helper-методов.
- [x] Переключить точку вызова обработки боя на `FightAuto.processFight(...)`.
- [x] Проверить сборку `:app:compileDebugJavaWithJavac`.

## Выполнено
- [x] `app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java`
  - Вынесена полная логика боевой обработки:
    - разбор состояния боя,
    - unified finish-flow,
    - ручная/авто-капча,
    - ожидание хода и авто-рефреш,
    - safety-остановка Auto-Боя по порогам.
  - Добавлены локальные helper-методы finish-flow:
    - `buildFightEndFormSubmitHtml(...)`,
    - `inspectFightFinishPageMarkers(...)`,
    - `logFinishFlowDecision(...)`.
- [x] `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
  - Добавлен `FIGHT_AUTO_HOST` (bridge-адаптер к существующим helper-методам `MainPhp`).
  - Точка обработки боя переключена на `FightAuto.processFight(address, html, FIGHT_AUTO_HOST)`.
  - Старый объёмный `mainPhpFight(...)` очищен до thin-wrapper без изменения поведения.
  - Добавлены подробные комментарии по правилам/зависимостям для bridge и обёртки.
  - Дополнительно дочищены дубли после выноса в `FightAuto`:
    - удалены локальные `FinishFlowDecision` и `FightFinishPageMarkers`;
    - удалены локальные `buildFightEndFormSubmitHtml(...)`, `inspectFightFinishPageMarkers(...)`, `logFinishFlowDecision(...)`;
    - удалены неиспользуемые поля `lastAutoFinishRedirectAtMs` и `lastAutoSkinProbeFightLog`.
- [x] Комментарии и документация в `FightAuto.java`
  - Добавлены детальные русскоязычные JavaDoc-описания зависимостей и правил:
    - на уровне класса/DTO/enum/bridge-интерфейса;
    - для `processFight(...)`;
    - для helper-методов finish-flow.

## Проверка
- [x] Сборка: `./gradlew :app:compileDebugJavaWithJavac` — успешно.
- [x] Кодировка файлов: UTF-8 without BOM.

## Примечания
- [x] Логика не менялась концептуально: перенос в модуль + сохранение текущего поведения.
