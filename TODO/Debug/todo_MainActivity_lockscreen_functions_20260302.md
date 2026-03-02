# Детальный TODO: `MainActivity.java` (lockscreen/background)

## Контекст
- [ ] Компонент содержит lifecycle Activity и запуск/остановку polling-циклов (`chat`, `room users`).
- [ ] Это первичный кандидат на причину остановки авто-режима при `screen off`.

## Функции и задачи

### `onCreate(Bundle)` (`MainActivity.java`)
- [ ] Проверить инициализацию фоновых контуров (`startTimer()`, `startChatRefresh()`) и зависимости от UI.
- [ ] Зафиксировать, можно ли безопасно вынести часть цикла из Activity в сервис.

### `onResume()` (`MainActivity.java`)
- [ ] Проверить, что именно запускается только при возвращении UI (`startRoomUsersPolling()`, broadcast receiver).
- [ ] Подтвердить, что без `onResume()` фоновые тики не реанимируются.

### `onPause()` (`MainActivity.java`)
- [ ] Проверить прямую остановку room-polling (`stopRoomUsersPolling()`).
- [ ] Сверить, нет ли здесь фактической «смерти» авто-цепочки при блокировке экрана.

### `onDestroy()` (`MainActivity.java`)
- [ ] Проверить условия, когда Activity уничтожается при фоне/блокировке на целевом устройстве.
- [ ] Проверить, какие циклы/подписки теряются без восстановления.

### `startRoomUsersPolling()` / `stopRoomUsersPolling()` / `restartRoomUsersPolling()`
- [ ] Проверить, кто и когда их вызывает (UI-only или есть фоновые источники).
- [ ] Проверить, что polling продолжает планировать следующий тик при `screen off`.
- [ ] Проверить guard `isFinishing()/isDestroyed()` и влияние на фоновую работу.

### `requestRoomUsersRefreshSoon()`
- [ ] Проверить, не блокируется ли вызов интервалом `ROOM_USERS_REFRESH_MIN_INTERVAL_MS` после сна устройства.
- [ ] Проверить фактические `loadUrl(ch.php?lo=1)` в lockscreen-сценарии.

### `startChatRefresh()` / `stopChatRefresh()` / `requestChatRefresh()`
- [ ] Проверить, жив ли `chatRefreshHandler` при погашенном экране.
- [ ] Проверить, идет ли связанный `requestRoomUsersRefreshSoon()` при активном `LOCATION_TRACKING`.

## Зависимости
- [ ] `AutoFunctionsManager` (флаг `LOCATION_TRACKING`, интервал walkers).
- [ ] `WebView`-контур (`chatRefrWebView`, `chatUsersWebview`).
- [ ] `RoomManager` (обработка `ch.php?lo=1` и старт авто-нападения).

## Ожидаемый результат анализа
- [ ] Явный вывод: какие функции в `MainActivity` должны остаться UI-only, а какие должны быть продублированы/перенесены в фон.

## Промежуточные выводы (статический анализ, 2026-03-02)
- [x] `onPause()` явно останавливает `roomUsersPolling` через `stopRoomUsersPolling()`.
- [x] `onResume()` поднимает `roomUsersPolling` только при возврате активной Activity.
- [x] `startRoomUsersPolling()` полностью завязан на `Handler` + UI lifecycle Activity.
- [x] `requestRoomUsersRefreshSoon()` выполняет `WebView.loadUrl(...)`; при паузе/заморозке WebView тики могут фактически не исполняться.
- [x] `startChatRefresh()` стартует в `onCreate()`, но критичный боевой/room контур всё равно зависит от UI/WebView.
- [ ] Нужна runtime-проверка по логам, что именно останавливается первым: room polling, chat polling или submit бой-цепочки.
