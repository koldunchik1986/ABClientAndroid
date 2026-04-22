# Задача: стабилизация завершения боя и капчи (2026-04-21)

## Контекст
- Симптом 1: `PatternSyntaxException` в `FightAuto.publishFightResultFromLogsIfNeeded(...)` на ветке `act=7`.
- Симптом 2: при открытом popup капчи guard в `WebViewRequestInterceptor` блокирует foreign challenge без bytes, что может удерживать несинхронный captcha-контекст.

## План
- [x] Проверить логи `Logs/` и подтвердить точки отказа.
- [x] Исправить decision point парсинга winner в `FightAuto` без аварийного regex.
- [x] Исправить decision point captcha-guard в `WebViewRequestInterceptor` (resync вместо hard-block при `expected bytes missing`).
- [x] Проверить компиляцию (`:app:compileDebugJavaWithJavac`).
- [ ] Получить новый runtime-лог с устройства и подтвердить:
  - нет `Intercept failed` из `publishFightResultFromLogsIfNeeded`;
  - нет зависания в повторных `act=7`;
  - корректная капча принимается и бой закрывается без ручного выключения авто-боя.

## Изменённые файлы
- `app/src/main/java/ru/neverlands/abclient/postfilter/FightAuto.java`
- `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`
- `TODO/Debug/debug_log_20260421_152242.md`
