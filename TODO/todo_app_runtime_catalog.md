# Каталог runtime-контуров `app`

## Назначение

Карта владельцев background/lifecycle/network логики. `app_fable5` использовался только как справочник для узкого разделения ответственности, без массового перемещения классов.

| Контур | Владелец | Точка запуска | Lifecycle и cleanup | Ключевые guards |
| --- | --- | --- | --- | --- |
| Основной UI и WebView | `MainActivity` | `onCreate`, WebView callbacks | `onDestroy`: callbacks, popup/main/chat WebView, Activity reference | manual navigation suppression, server-probe throttle |
| Фоновый авто-режим | `AutoModeForegroundService` | `syncServiceState` | ticker, wake/Wi-Fi locks, no-Activity timeout stop | captcha, UI/manual context, fight markers |
| Авто-бой | `FightViewModel`, `FightAuto`, `FightAnnounceHandler` | fight HTML, announce, service tick | bounded announce retry, fight context clear | captcha, `ForcedActionGuard`, `SessionManager` |
| Fast-action | `FastActionManager` | quick action, auto-attack | `fastCancel`, wait-loop `finally` | `FastNeed`, cancellation, bounded retry |
| Chat | `Chat`, `WebAppInterface`, `MainActivity` | WebView bridge, polling | bounded queues, lifecycle-safe bridge lookup | WebView readiness, retry scheduler, POST timeouts |
| Room/auto-attack | `RoomManager` | `ch.php?lo=1` | TTL/capacity pruning of transient maps | active fight, `FastNeed`, blacklist |
| Local proxy | `ProxyRuntimeManager`, `LocalHttpProxyServer` | app bootstrap/profile change | bounded workers, tracked socket shutdown | strict proxy, socket timeout |
| Shared HTTP | `NetworkClient`, `ApiRepository` | API/download requests | pool retirement, response/body closure | proxy binding, session cookies |
| File diagnostics | `AppLog`, `FileLogger` | application modules | bounded daemon writer queue, rotation | dropped-write diagnostics |
| Contacts | `ContactsManager`, `ContactsActivity` | login and contacts screen | coalesced XML write, weak callback | application context, screen refresh guard |

## Результат сравнения с `app_fable5`

- Полезный принцип reference: `MainActivity` координирует, а специализированные классы владеют одной задачей.
- В текущем модуле это уже соблюдено для `FightAnnounceHandler`, `FastActionManager`, `SessionManager` и service. Для аудита исправлялись существующие decision points, а не создавались параллельные контуры.
- Безопасные кандидаты на будущую декомпозицию: server-probe и chat/room scheduler. Их владельцы и guards зафиксированы в таблице.
