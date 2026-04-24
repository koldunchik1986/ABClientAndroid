# Задача 2026-04-24: рефакторинг MainActivity по актуальному TODO

## Цель

Актуализировать план `TODO/MainActivity_Refactoring_Summary.md` под фактический код и начать безопасное выполнение без изменения runtime-поведения авто-боя, чата и ручной навигации.

## Фактический статус перед стартом

- [x] Правила перечитаны: `.cursor/rules/AI.txt`, `.cursor/rules/general rule.txt`, `.cursor/rules/UTF8.ps1`, `AGENTS.MD`.
- [x] `SessionManager` уже реализован и используется вместо прямого runtime-доступа к `AppVars.VCode`.
- [x] В Java-коде прямые обращения к `AppVars.VCode` не найдены; остались только устаревшие комментарии.
- [x] `FightContextChoiceHandler` создан, `requestAutoTurnInternal(...)` переведён на decision-handler для current/cached/server-probe выбора.
- [ ] `ChatPollRecoveryHandler` отсутствует, `onChatPollResponseMeta(...)` остаётся в `MainActivity`.
- [ ] `ManualNavGuardHandler`, `SubmitRetryHandler`, `CaptchaDialogBuilder`, `FightStateHolder`, `ChatStateHolder`, `CaptchaStateHolder` отсутствуют.
- [ ] `getMainWebViewOrNull()` и `isMainBindingReady()` отсутствуют.

## План выполнения

- [x] Обновить TODO-документы фактическим статусом на 2026-04-24.
- [x] Создать `FightContextChoiceHandler` для выбора боевого HTML-контекста без server-probe-дублирования.
- [x] Перевести `requestAutoTurnInternal(...)` на `FightContextChoiceHandler`, оставив server-probe в существующем контуре `requestAutoTurnFromServerProbe(...)`.
- [x] Обновить stale-комментарии `AppVars.VCode` в `MainActivity.java`, не добавляя новых runtime-обращений.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.
- [x] Проверить mojibake/BOM по изменённым файлам.

## Инварианты

- [x] Не менять `ABClient/`.
- [x] Не добавлять новых обращений к `AppVars.VCode`.
- [x] Не создавать параллельный server-probe контур; использовать существующий `requestAutoTurnFromServerProbe(...)`.
- [x] Для критичных цепочек использовать `AppLog`/файловое логирование, не прямой `android.util.Log`.
- [x] Ручные HTML-действия не должны конкурировать с auto-turn probe.

## Результат 2026-04-24

- [x] Добавлен `app/src/main/java/ru/neverlands/abclient/handlers/FightContextChoiceHandler.java`.
- [x] `MainActivity.requestAutoTurnInternal(...)` стал координатором: получает HTML, вызывает handler, применяет решение.
- [x] Pending finish-навигация вынесена в `handlePendingFightFinishNavigation()` без изменения retry-limit логики.
- [x] Сборка `./gradlew --no-daemon :app:compileDebugJavaWithJavac` успешна.
- [x] BOM отсутствует в изменённых файлах.
- [x] Mojibake по изменённым файлам не найден; совпадения возможны только в checklist-примерах паттернов.
