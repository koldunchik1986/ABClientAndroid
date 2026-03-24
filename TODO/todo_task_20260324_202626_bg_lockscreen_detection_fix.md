# Задача: диагностика фоновой работы при блокировке экрана (лог 20260324_17)

## Что обнаружено в логе
- [x] Нет событий `screenStateReceiver: action=android.intent.action.SCREEN_OFF` в этом файле.
- [x] Сервис держит wake/wifi lock: `ensureLocks: wakeLock acquired`, `ensureLocks: wifiLock acquired`.
- [x] При этом сервис часто уходит в ветку `skip autoTurn/probe in foreground-likely UI`.
- [x] В `onResume` зафиксировано `batteryOptimized=true`.

## Гипотеза причины
- [x] Детектор foreground/background не учитывал состояние keyguard (блокировка устройства), из-за чего при lockscreen UI мог ошибочно считаться foreground-likely.

## Изменения
- [x] Добавлен учёт `KeyguardManager` в `MainActivity`:
  - `isUiForegroundInteractive()`
  - `isUiForegroundLikely()`
  - `isAutoTurnServerProbeAllowedNow()`
- [x] На `ACTION_SCREEN_OFF` принудительно сбрасывается `isActivityResumedState=false` как защитный fallback.
- [x] В `logBackgroundState(...)` добавлено поле `deviceLocked`.

## Проверка после фикса
- [ ] В новом логе должны появиться события `screen_event_android.intent.action.SCREEN_OFF`.
- [ ] После блокировки `uiForegroundLikely`/`uiForegroundInteractive` должны стать `false`.
- [ ] В фоне не должно быть постоянного `skip autoTurn/probe in foreground-likely UI` в lockscreen-сценарии.
