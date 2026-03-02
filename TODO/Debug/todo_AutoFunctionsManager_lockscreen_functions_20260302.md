# Детальный TODO: `AutoFunctionsManager.java` (lockscreen/background)

## Контекст
- [ ] Менеджер хранит переключатели авто-функций и параметры polling.
- [ ] Для lockscreen-кейса важен как «источник истины» состояния и триггер перезапуска polling.

## Функции и задачи

### `isAutoAttackEnabled()` / `getAutoAttackToolId()`
- [ ] Проверить, что состояние авто-нападения вычисляется только через `toolId != 0`.
- [ ] Подтвердить отсутствие рассинхронизации между `SharedPreferences` и `AppVars.AutoAttackToolId`.

### `setAutoAttackEnabled(boolean)` / `setAutoAttackToolId(int)`
- [ ] Проверить, что при включении корректно поднимается `LOCATION_TRACKING`.
- [ ] Проверить, что после lockscreen состояние не «сбрасывается» в `toolId=0`.

### `setLocationTrackingEnabled(boolean)`
- [ ] Проверить side-effects: reset walkers-state, `onWalkersPollingConfigChanged()`, immediate refresh.
- [ ] Проверить, вызывается ли метод непреднамеренно при блокировке экрана.

### `getWalkersPollIntervalSec()` / `setWalkersPollIntervalSec(int)`
- [ ] Проверить, что интервал не уходит в некорректное значение после восстановления процесса.
- [ ] Проверить, что рескейджулинг происходит корректно и не только в активном UI.

### `disableAll()`
- [ ] Проверить, кто вызывает массовое отключение функций.
- [ ] Исключить ложное срабатывание при переходах lifecycle/idle.

## Зависимости
- [ ] `MainActivity.onWalkersPollingConfigChanged()`.
- [ ] `AppVars` runtime-поля (DoShowWalkers, AutoAttackToolId).
- [ ] Быстрые кнопки/настройки (`QuickButtonsPanel`, настройки профиля).

## Ожидаемый результат анализа
- [ ] Явная карта: какие setter-методы могут гасить фоновые контуры и в каких условиях.

## Промежуточные выводы (статический анализ, 2026-03-02)
- [x] `isAutoAttackEnabled()` корректно derived от `getAutoAttackToolId() != 0`.
- [x] `setAutoAttackToolId(toolId>0)` автоматически включает `setLocationTrackingEnabled(true)`.
- [x] `setLocationTrackingEnabled(...)` триггерит `MainActivity.onWalkersPollingConfigChanged()` и `requestRoomUsersRefreshSoon()`, но через `runOnUiThread`.
- [x] Все критичные side-effect вызовы завязаны на наличие `AppVars.mainActivity.get()`.
- [ ] Нужна runtime-проверка, не теряется ли `mainActivity` reference/доступ к UI-thread после lockscreen.
