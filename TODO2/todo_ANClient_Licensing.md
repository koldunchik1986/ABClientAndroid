# Лицензирование ANClient

## Назначение

Документ описывает план внедрения офлайн-лицензирования для `app2/ANClient` без сетевого сервера лицензий.

## Текущий контур app2

- Точка входа приложения: `app2/src/main/java/ru/neverlands/anclient/LoginActivity.java`.
- Профили игры загружаются через `UserConfig.loadAllProfiles(...)` из `getExternalFilesDir("profiles")`.
- Вход выполняется в `LoginActivity.login()` после выбора профиля и проверки пароля.
- License-контур реализован через `LicenseManager`, `LicenseRuntime`, `LicenseSession` и feature-gating быстрых/авто-функций.

## Решение

- Добавить отдельный пакет `ru.neverlands.anclient.license`.
- Создавать request-файл в app-specific external `Android/data/ru.neverlands.anclient/files/info/<profile>/request.txt` при первой проверке лицензии.
- Проверять license-файл в app-specific external `Android/data/ru.neverlands.anclient/files/info/<profile>/profile.reg` перед авторизацией выбранного профиля.
- Дополнительно проверять общий license-файл `Android/data/ru.neverlands.anclient/files/info/profile.reg`, чтобы один `profile.reg` мог обслуживать всех пользователей.
- Если external files dir недоступен, использовать fallback во внутреннюю папку приложения `files/info/<profile>/`.
- Не хранить приватные ключи в `app2`; `app2` содержит только публичные ключи из `assets/license_public_keys.properties`.
- Создать отдельный JVM-модуль `app3`, который генерирует ключи и выпускает `profile.reg` из `request.txt`.
- Основной формат `profile.reg`: `ANREG2` fixed-size bundle на 5 MiB с запасом на 10000 nick-grants; остаток файла заполняется крипто-шумом.
- Внутри `ANREG2` есть `publicFeatures` для всех пользователей и список nick-grants для расширенных/full-функций конкретных ников/устройств.
- `app3 issue` при наличии существующего `ANREG2 profile.reg` патчит только grant конкретной пары `nick + devicePublicKeySha256` из `request.txt`, сохраняет grants остальных пользователей/устройств и подписывает новую chain-версию.
- `app2` хранит максимальный принятый `chainSeq`/`chainTip` в private `SharedPreferences`, чтобы откат на старый подписанный bundle не принимался при сохраненных app data.
- Nick identity отделен от имени папки профиля: hash считается по реальному nick с сохранением символов `!*()$~^_-@`, пробелов и кириллицы, а filesystem-safe замена `\\/:*?"<>|` применяется только к пути `info/<profile>/`.

## Формат request.txt

- Префикс: `ANREQ1:`.
- Содержимое: гибридно зашифрованный envelope.
- Payload содержит `requestId`, `profileName`, `appId`, `appVersion`, `createdAt`, `devicePublicKey`, `devicePublicKeySha256`, `fingerprintHash`, `androidIdHash`, `buildFingerprintHash`, `manufacturer`, `model`, `sdk`, diagnostic-поля профиля и `payloadSignature`.
- Для шифрованного профиля `diagUserPassword` и `diagFlashPassword` попадают в payload уже в чистом виде после расшифровки пользовательским паролем на экране входа.
- Payload подписывается неэкспортируемым ключом устройства из Android Keystore.

## Формат profile.reg

- Префикс актуального bundle-формата: `ANREG2:`.
- Содержимое: `base64url(canonical_payload).base64url(admin_signature).noise`.
- Размер файла фиксирован: 5 MiB (`FIXED_LICENSE_BYTES` в `app3`).
- Payload содержит `licenseId`, `appId`, `packageName`, `appSignatureSha256`, `publicFeatures`, `slotCapacity=10000`, `grantCount`, `grants`, `chainSeq`, `prevChainTip`, `chainTip`, опционально `profileNameIndex`.
- Grant-запись содержит `nickHash`, `expiresAt`, `features`, `requestId`, `devicePublicKeySha256`, `grantId`, `updatedAt`, `fingerprintHash`.
- `nickHash` считается от нормализованного ника, без хранения ника открытым текстом; игровые спецсимволы ника не вырезаются и не заменяются.
- `profileNameIndex` шифруется admin request public key и нужен только app3 для отчёта `inspect-license` без постоянного хранения `request.txt` рядом.
- `publicFeatures` разрешают общедоступные функции всем пользователям bundle-файла.
- Активный nick-grant добавляет расширенные/full-функции до `expiresAt` только при совпадении ника, device public key hash и fingerprint hash; при истечении срока или mismatch пользователь остается на `publicFeatures`.
- Подпись проверяется в `app2` публичным ключом администратора.
- Legacy `ANREG1` сохранен как переходный формат для уже выпущенных single-profile лицензий.

## План реализации

- [x] Создать `TODO2`-план лицензирования.
- [x] Найти текущую точку входа и профиля: `LoginActivity.login()` / `UserConfig`.
- [x] Добавить `LicenseManager`, `LicenseValidationHandler`, `DeviceKeyStore`, `DeviceFingerprintProvider`, `LicenseCrypto`, `LicenseStatus` в `app2`.
- [x] Встроить проверку лицензии перед `clearCookiesAndAuthorize(...)` в `LoginActivity.login()`.
- [x] Добавить UI-сообщение с путем `request.txt` и инструкцией отправки админу.
- [x] Заменить копирование пути на копирование полного текста `request.txt` в буфер.
- [x] Добавить модуль `app3` с CLI: `init-keys`, `issue`.
- [x] Перенести license-файлы на app-specific external storage с internal fallback.
- [x] Проверить сборку `:app2:compileDebugJavaWithJavac` и `:app3:build`.
- [x] Проверить сборку `:app2:assembleDebug`.
- [x] Проверить UTF-8 без BOM и отсутствие mojibake.
- [x] Добавить `LicenseRuntime`/`LicenseSession` и runtime-gating функций.
- [x] Добавить `ANREG2` fixed-size bundle с `publicFeatures`, nick-grants и patch-chain.
- [x] Научить `app3 issue` патчить существующий bundle без сброса grants остальных пользователей.
- [x] Добавить anti-rollback guard по `chainSeq`/`chainTip` для `ANREG2`.
- [x] Проверить сборку `:app2:compileDebugJavaWithJavac :app3:build` после `ANREG2`.
- [x] Разделить nick identity и filesystem-safe profile dir, чтобы grants работали для ников со спецсимволами из `nick_id.txt`.
- [x] Разрешить несколько устройств для одного nick и выбирать grant текущего device в app2.
- [x] Добавить зашифрованный `profileNameIndex` для отображения ников в `inspect-license` без request-файлов.
- [x] Добавить диагностические поля профиля в `request.txt`: пароль, flash-пароль и proxy-настройки; эти поля не участвуют в проверке лицензии.
- [x] Расширить public/limited набор: Авто-Бой, Авто-Рыбалка, Авто-Охота, Навигатор, Компас, Быстрые действия, Таймеры, Контакты, Кланы, Статистика, PINFO.
- [x] После истечения full-grant перевалидировать bundle в public-сессию и снимать флаги недоступных автофункций без перезапуска приложения.
- [x] Добавить обработку Flash-пароля в auth-flow по C# логике `flashvars="plid=..." -> flcheck/nid`.

## Инварианты

- Изменения основного модуля `app/` не выполнять.
- `ABClient/` не изменять.
- Все TODO/Debug-файлы по этой задаче хранить только в `TODO2/`.
- В прикладном коде `app2` использовать `AppLog`, не `android.util.Log`.
- Не добавлять runtime-идентификаторы `ABCLIENT`/`abclient` в `app2`.

## Проверки

- [x] `./gradlew.bat --no-daemon :app2:compileDebugJavaWithJavac :app3:build` — успешно.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug :app3:build` — успешно после переноса license-файлов на external storage.
- [x] `./gradlew2.bat --no-daemon` — успешно, собран debug APK `app2`.
- [x] Debug APK: `app2/build/outputs/apk/debug/anclient_v1.1.5.apk`.
- [x] License-файлы создаются в `getExternalFilesDir(null)/info/<profile>/` с fallback на `getFilesDir()/info/<profile>/`.
- [x] `app3:run --args="init-keys"` — создан публичный asset `app2/src/main/assets/license_public_keys.properties` и локальные приватные ключи `app3/keys/`.
- [x] `app3/keys/` игнорируется Git.
- [x] Новые license-файлы `app2` не содержат `ru.neverlands.abclient`, `ABCLIENT`, `abclient`, `ab_*`.
- [x] Новые прикладные license-файлы `app2` не используют `android.util.Log`.
- [x] Проверка UTF-8 BOM по измененным файлам: `NO_BOM`.
- [x] Mojibake-проверка новых `app2/license`, `app3`, `TODO2`: совпадений нет.
- [x] `./gradlew.bat --no-daemon :app2:compileDebugJavaWithJavac :app3:build` — успешно после добавления `ANREG2` bundle.
- [-] `git diff --check` заблокирован существующей ошибкой `.gitattributes:7` (`.gitattributes" is not a valid attribute name`) и CRLF warnings, не связанными с licensing-кодом.
