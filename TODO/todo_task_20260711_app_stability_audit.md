# Аудит стабильности Android-модуля `app`

## Цель

Системно проверить Android-код `app/` на подтверждаемые риски утечек памяти и потери сетевых соединений, устранить найденные дефекты в существующих контурах и зафиксировать архитектурную карту для дальнейшего сопровождения.

## Ограничения

- Изменения только в `app/` и документации `TODO/`; `ABClient/` и `app_fable5/` используются только как справочник.
- Не удалять автоматизацию, proxy, WebView, бой и другие рабочие подсистемы: исправлять жизненный цикл и cleanup в их текущей точке принятия решения.
- Не добавлять новый HTTP-контур при наличии существующих interceptor/request queue/retry механизмов.
- Для критичных runtime-цепочек сохранять двойное логирование через `AppLog`/`FileLogger`.
- Не обещать абсолютное отсутствие утечек без runtime-профилирования; после статического аудита нужны воспроизводимые device-тесты и анализ heap/network traces.

## План

- [ ] Сопоставить структуру `app/` с `app_fable5/`, выделить полезную каталогизацию без косметического массового перемещения файлов.
- [ ] Инвентаризировать точки жизненного цикла: `Activity`, `Service`, `BroadcastReceiver`, `WebView`, `Handler`, `Runnable`, executors, coroutines и listeners.
- [ ] Проверить регистрацию/отмену callback'ов, receiver'ов, задач и WebView cleanup.
- [ ] Инвентаризировать HTTP/proxy/polling/retry: закрытие response/body/stream, timeouts, отмена запросов и восстановление сессии.
- [ ] Проверить существующие логи и guards от конкурентных background/manual запросов.
- [ ] Внести минимальные подтверждённые исправления в существующие decision points.
- [ ] Собрать модуль и выполнить focused проверки кодировки, mojibake и diff.

## Проверяемые классы рисков

- Утечки `Activity`/`View` через static singleton, non-static inner class, delayed `Runnable`, `Handler`, listener или WebView bridge.
- Незавершённые foreground/background services, receiver registrations и executors.
- `WebView` с удерживаемым `Context`, неочищенными clients/bridges или незакрытым destroyed lifecycle.
- Незакрытые `Response`, `ResponseBody`, `InputStream`, socket и connection pool exhaustion.
- Бесконечные retry/polling задачи без cancellation, backoff или lifecycle/network guard.
- Потеря сессии/VCode/cookie после ошибки соединения и конкуренция auto/manual запросов.

## Ход аудита

- Статический аудит и минимальные исправления завершены; device-профилирование остаётся заключительным этапом.

### Исправленные подтверждённые риски

- `MainActivity`: lifecycle-safe UI ticker, cleanup popup WebView/asset stream/Activity reference и защита от stale server-probe callback.
- `AutoModeForegroundService`, `FastActionManager`, `FightAnnounceHandler`: остановка на destroyed UI, bounded polling/retry и гарантированный cleanup runtime-флагов.
- `FightAuto`, `FightViewModel`, `MainActivity`: `markFightInProgress()` выполняется перед каждым `new LezFight(...)`.
- `WebAppInterface`, `Chat`, `UnderAttackManager`: application context bridge, POST timeouts, bounded chat queues и coalesced HTML parsing.
- `Cache`, `RoomManager`, `FileLogger`, `LocalHttpProxyServer`: bounded memory/executor queues, TTL pruning и socket cleanup.
- `ContactsManager`, `ContactsActivity`, `LoginActivity`, `ApiRepository`: coalesced XML persistence, lifecycle-safe callbacks и закрытие failed HTTP response.

### Проверки

- `:app:compileDebugJavaWithJavac --rerun-tasks` — успешно.
- `:app:lintDebug` — успешно.
- `git diff --check` — без ошибок whitespace.

### Следующий этап

- [ ] Device-тесты: поворот/сворачивание Activity, lockscreen, смена сети/proxy, длинный бой и heap/network profiler. Статический анализ не может доказать отсутствие runtime-утечек на устройстве.
