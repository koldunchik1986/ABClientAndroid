# Задача: лицензирование ANClient

## Цель

Реализовать офлайн-лицензирование для `app2/ANClient`: пользователь создает профиль, получает `request.txt`, отправляет его администратору, получает общий fixed-size `profile.reg`, который дает всем public-функции и расширяет/full-доступ только nick-grants из bundle.

## Подзадачи

- [x] Зафиксировать архитектуру и формат файлов в `TODO2/todo_ANClient_Licensing.md`.
- [x] Найти существующий login/profile decision point.
- [x] Реализовать генерацию `Android/data/ru.neverlands.anclient/files/info/<profile>/request.txt` с internal fallback.
- [x] Реализовать проверку `Android/data/ru.neverlands.anclient/files/info/<profile>/profile.reg` с internal fallback.
- [x] Заблокировать вход при отсутствии/ошибке лицензии до сетевой авторизации.
- [x] Создать `app3` как JVM CLI для генерации ключей и выпуска лицензий.
- [x] Проверить полную сборку `app2` и `app3`.
- [x] Проверить кодировку и финальные grep-инварианты.
- [x] Добавить runtime capability-сессию (`LicenseRuntime`/`LicenseSession`) вместо single UI-gate.
- [x] Добавить `ANREG2` fixed-size bundle: общий `profile.reg`, `publicFeatures`, nick-grants, chain-tip.
- [x] Сделать `app3 issue` patch-командой: обновляет timeout/features одного ника из `request.txt`, сохраняя grants остальных.
- [x] Закрыть лазейку nick-only grants: full/custom grant теперь требует совпадения device public key hash и fingerprint hash.
- [x] Добавить anti-rollback guard по `chainSeq`/`chainTip` для защиты от подмены на старый подписанный bundle.
- [x] Проверить сборку `:app2:compileDebugJavaWithJavac :app3:build` после bundle-формата.
- [x] Исправить license `nickHash` для ников со спецсимволами: `!*()$~^_-@`, пробелы и кириллица сохраняются в identity, filesystem-safe замена используется только для папки.
- [x] Добавить в `app3` проверку текущего `profile.reg`: подпись, общий доступ, nick-grants, функции и оставшееся время по каждому найденному request.
- [x] Разрешить несколько устройств для одного nick: patch обновляет grant по паре `nickHash + devicePublicKeySha256`, а app2 выбирает активный grant именно текущего устройства.
- [x] Добавить в `profile.reg` зашифрованный `profileNameIndex`, чтобы `inspect-license` показывал ники без постоянного хранения `request.txt` рядом.
- [x] Сохранять расшифровку заявки как `Nick_devicePublicKeySha256.txt` при `decode-request` без явного output path.
- [x] Заменить кнопку `Копировать путь` на `Копировать запрос`: в буфер попадает полный текст `request.txt`.
- [x] Добавить нормализацию переносов строк в `app3 decode-request`, чтобы request можно было переслать текстом через Telegram/email.
- [x] Передавать в diagnostic-поля `request.txt` чистые `diagUserPassword`/`diagFlashPassword` после расшифровки пользовательским паролем.
- [x] Переименовать UI-поле `Пароль от флешки` в `Flash-пароль`.
- [x] Добавить auth-flow Flash-пароля по логике ПК-версии: при `flashvars="plid=..."` отправлять `flcheck`/`nid` на `game.php`.
- [x] Исправить runtime downgrade после истечения full-grant: перевалидация в public-сессию, обновление UI и снятие флагов недоступных автофункций.

## Найденный контур

- `LoginActivity.login()` является минимальной точкой встраивания: до сетевой авторизации уже известен выбранный `UserConfig` и его `UserNick`.
- `ProfileActivity.saveProfile(...)` оставляем без блокировки, чтобы пользователь мог создать профиль и получить request-файл.
- `UserConfig.loadAllProfiles(...)` не меняем: лицензия лежит отдельно от `.profile` и не ломает существующие профили.

## Решение по безопасности

- `request.txt` шифруется публичным ключом администратора из `assets/license_public_keys.properties`.
- `profile.reg` подписывается приватным ключом администратора в `app3` и дополняется шумом до фиксированного размера 5 MiB.
- `app2` проверяет публичную подпись, APK signature, packageName и chain-tip.
- Raw-идентификаторы устройства в license-файл не пишутся; для grants используется SHA-256 hash ника и hash публичного ключа устройства из request.
- `publicFeatures` доступны всем, nick-grant только расширяет набор функций до своего `expiresAt`.
- Для старых grants с sanitized hash оставлен legacy fallback, но новые `app3 issue` записи всегда пишутся по реальному nick identity.
- Открытые nick-имена для админского отчёта не лежат в grants открытым текстом: `profileNameIndex` шифруется admin request public key и читается только app3 через `admin_request_private.pkcs8`.
