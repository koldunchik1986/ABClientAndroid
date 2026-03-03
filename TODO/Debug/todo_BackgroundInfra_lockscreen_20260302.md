# Детальный TODO: background infra (Manifest/Power/Service)

## Контекст
- [ ] Даже при отключенной оптимизации батареи Android может ограничивать таймеры/сеть в фоне.
- [ ] Нужна проверка инфраструктуры, которая должна поддерживать авто-контур при `screen off`.

## Области проверки

### `AndroidManifest.xml`
- [ ] Проверить текущие разрешения для фонового режима (`FOREGROUND_SERVICE`, типы service).
- [ ] Проверить, что отсутствуют обязательные для целевых API разрешения (если требуются для выбранной стратегии).

### Foreground service стратегия
- [ ] Определить точку запуска/остановки foreground-service при включении/выключении авто-режима.
- [ ] Зафиксировать контракт между service и текущими менеджерами (`AutoFunctionsManager`, `RoomManager`, `MainActivity`).

### Power management
- [ ] Проверить стратегию `PARTIAL_WAKE_LOCK` (включение только в активном авто-режиме).
- [ ] Проверить корректное освобождение lock при всех ветках stop/crash/restart.

### Сетевой контур в фоне
- [ ] Зафиксировать, какие запросы обязаны жить в фоне (room/chat/fight polling).
- [ ] Проверить, можно ли оставить WebView-контур или нужен отдельный HTTP-контур для устойчивости.

## Ожидаемый результат анализа
- [ ] Выбранный и документированный runtime-подход для стабильной работы 30+ минут при заблокированном экране.

## Промежуточные выводы (статический анализ, 2026-03-02)
- [x] В `AndroidManifest.xml` есть разрешения `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_DATA_SYNC`, но нет зарегистрированного `<service>` для автономного авто-цикла.
- [x] В текущем Android-коде не найдено использования `PowerManager.WakeLock` для удержания CPU при `screen off`.
- [x] Явной инфраструктуры устойчивого фонового scheduler (service + notification + wake control) сейчас нет.
- [ ] Нужно утвердить вариант реализации (foreground service + ограниченный wake-lock) до кодовых правок.

## Обновление реализации (2026-03-02)
- [x] Добавлен `AutoModeForegroundService` и регистрация `<service ... foregroundServiceType="dataSync" />`.
- [x] Реализованы `PARTIAL_WAKE_LOCK` + `WifiLock` с release в `onDestroy`.
- [x] Добавлен throttling сервисных тиков:
  - room refresh по `walkers_poll_interval_sec`,
  - auto-turn не чаще 1 раза в секунду.
- [x] Добавлен recovery-переход в `fight.frame` при рассинхроне верхнего фрейма (cooldown 5 сек).
- [x] Усилен детектор активного боя в сервисе: добавлен fallback по `MainWebView.getUrl()` в дополнение к `AppVars.ContentMainPhp/url_main_top`.
