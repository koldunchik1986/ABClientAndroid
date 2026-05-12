# Задача 2026-05-12: cold-start guard авто-функций по NeverTimer

## Проблема

- [x] При закрытии приложения во время активного server cooldown `NeverTimer` хранится только в runtime-поле `AppVars.NeverTimer`.
- [x] После холодного старта процесс теряет это значение, а `restorePersistentAutoModesAfterLogin()` сразу поднимает сохранённые авто-функции.
- [x] `setAutoFishEnabled(true)` дополнительно сбрасывал `AppVars.NeverTimer = 0L`, поэтому Авто-Рыбалка могла стартовать раньше окончания серверного таймера.

## Найденный существующий контур

- [x] Источник server cooldown: `WebAppInterface.SetNeverTimer(msLeft)`, вызывается из `assets/js/map.js` при `TimerStart/timerst`.
- [x] Общий dispatcher ожидания: `MainActivity.checkServerTimerDrivenActions()` уже ориентируется на `AppVars.NeverTimer`.
- [x] Единая точка cold-start восстановления авто-режимов: `AutoFunctionsManager.restorePersistentAutoModesAfterLogin()`.

## Реализация

- [x] В `AppVars` добавлено сохранение absolute due-time `NeverTimer` в `runtime_timers_prefs`.
- [x] `AppVars.init(...)` восстанавливает future `NeverTimer` после cold start и очищает истёкший persisted timer.
- [x] `WebAppInterface.SetNeverTimer(...)` теперь пишет через `AppVars.setNeverTimerDueAt(...)`, чтобы серверный таймер переживал перезапуск процесса.
- [x] `restorePersistentAutoModesAfterLogin()` перед запуском bootstraps проверяет активный `NeverTimer` и откладывает восстановление авто-функций до его истечения.
- [x] `setAutoFishEnabled(true)` больше не сбрасывает будущий `NeverTimer`; он очищает timer только если активного ожидания уже нет.
- [x] Прямые сбросы `AppVars.NeverTimer = 0L` в затронутых runtime-ветках заменены на `AppVars.clearNeverTimer(...)`, чтобы persisted due-time не восстанавливался после фактического сброса.

## Проверки

- [x] Проверка mojibake по изменённым `.java` и этому TODO не нашла новых повреждений; единственный `????` в `AppVars.java` относится к старому captcha-placeholder `code=????`.
- [x] `./gradlew.bat :app2:compileDebugJavaWithJavac --rerun-tasks` выполнен успешно; остался существующий deprecation warning в `DeviceKeyStore.java`.
- [x] `./gradlew.bat :app2:assembleDebug` выполнен успешно.
- [x] `./gradlew.bat :app2:installDebug` выполнен успешно; APK `anclient_v1.1.5.apk` установлен на `Mi Note 3 - 9`.
- [ ] На устройстве проверить сценарий: запустить server cooldown, закрыть приложение, открыть снова, убедиться что в логах есть `cold-start auto restore delayed until NeverTimer`, а bootstrap авто-функций начинается после истечения таймера.
