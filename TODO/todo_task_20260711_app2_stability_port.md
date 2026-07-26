# Перенос исправлений стабильности `app` -> `app2`

## Цель

Перенести подтверждённые исправления lifecycle, сети, proxy, логирования и авто-функций из `app` в более новую Android-ветку `app2`, не заменяя её реализацию клан-казны, лицензии, strict-proxy, маршрутизации и captcha.

## Статус

- [x] Сопоставлены 20 изменённых классов `app` с аналогами `app2`.
- [x] Перенесены foundation/network/proxy исправления.
- [x] Перенесены fight/background automation исправления.
- [x] Перенесены UI lifecycle и contacts исправления.
- [x] Выполнена компиляция `:app2:compileDebugJavaWithJavac`.
- [x] Собран `:app2:assembleDebug`.
- [-] `:app2:lintDebug` блокируется пятью существующими XML lint-ошибками вне перенесённых классов; компиляция и APK-сборка успешны.

## Сохранённая функциональность `app2`

- Клан-казна: `Kazna` parser/renderer/cache и UI не заменялись.
- Лицензирование, browser User-Agent, routing и strict-proxy fail-closed сохранены.
- `LocalHttpProxyServer.waitProxyQueueTurn(...)` сохранён при переносе cleanup сетевых ресурсов.
- Event-driven captcha-aware автоход, карта/room render, auto-cure и `ProfessionRatingMonitor` сохранены.

## Результат

APK: `app2/build/outputs/apk/debug/app2-debug.apk`.
