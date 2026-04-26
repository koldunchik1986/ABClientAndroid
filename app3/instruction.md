# Инструкция по app3: выпуск и обновление лицензий ANClient

## Назначение app3

`app3` — локальный администраторский инструмент для работы с лицензиями `app2/ANClient`.

Основной класс:

`app3/src/main/java/ru/neverlands/anclient/license/AnLicenseTool.java`

Что делает инструмент:

- создаёт admin-ключи командой `init-keys`;
- расшифровывает и проверяет `ANREQ1 request.txt` от устройства;
- пишет проверенные поля `request.txt` в отчёт `Nick_devicePublicKeySha256.txt` командой `decode-request`;
- создаёт или обновляет общий `ANREG2 profile.reg`;
- сохраняет grants других пользователей и других устройств того же nick;
- хранит в `profile.reg` зашифрованный индекс ников для админского отчёта без постоянного хранения `request.txt` рядом;
- пишет `profile.reg` фиксированного размера `5 MiB` с noise-хвостом.

## Важные файлы

Private keys, хранить только локально:

`app3/keys/admin_sign_private.pkcs8`

`app3/keys/admin_request_private.pkcs8`

Public keys, встраиваются в app2:

`app3/keys/admin_sign_public.x509`

`app3/keys/admin_request_public.x509`

`app2/src/main/assets/license_public_keys.properties`

Файлы от устройства:

`Android/data/ru.neverlands.anclient/files/info/<filesystem-safe-profile>/request.txt`

Файл лицензии для установки на устройство:

`Android/data/ru.neverlands.anclient/files/info/profile.reg`

Допустим также профильный override:

`Android/data/ru.neverlands.anclient/files/info/<filesystem-safe-profile>/profile.reg`

## Первичная подготовка ключей

Запускать из корня проекта:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="init-keys"
```

Команда создаёт `app3/keys` и обновляет `app2/src/main/assets/license_public_keys.properties`.

Если нужно принудительно перевыпустить ключи:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="init-keys --force"
```

Важно: `--force` ломает совместимость со всеми ранее выданными `profile.reg`, потому что меняются admin public/private keys.

## CLI menu для Windows

В папке `app3` есть menu-обёртка:

```bat
app3\app3_menu.bat
```

Она запускает все функции `app3` без ручного набора Gradle-команд:

- `init-keys`;
- `init-keys --force` с подтверждением `FORCE`;
- расшифровка `app3/request/request.txt` в `Nick_devicePublicKeySha256.txt`;
- проверка текущего `profile.reg` с выводом общего доступа, ников, функций и оставшегося времени;
- выпуск `full` grant;
- выпуск `limited` grant;
- выпуск public-only bundle;
- ручной `issue` с вводом `expiresAtMillis`, `grantFeatures`, `publicFeatures`;
- сборка `app3`.

Каждый пункт menu перед выполнением показывает:

- точную команду;
- названия параметров;
- доступные значения;
- пример запуска.

По умолчанию menu использует:

```text
app3/request/request.txt
Nick_devicePublicKeySha256.txt рядом с request.txt
app3/request/profile.reg
```

Этот же `.bat` можно использовать как прямую CLI-обёртку:

```bat
app3\app3_menu.bat decode-request app3\request\request.txt
app3\app3_menu.bat check
app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m full limited
app3\app3_menu.bat build
```

Быстрый тест: выдать профилю из `app3/request/request.txt` полный доступ на 10 минут:

```bat
app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m full limited
```

То же через menu:

1. Запустить `app3\app3_menu.bat`.
2. Выбрать пункт `5`.
3. На пути `request.txt` нажать Enter, если используется `app3/request/request.txt`.
4. На пути `profile.reg` нажать Enter, если используется `app3/request/profile.reg`.
5. В поле срока доступа ввести `10m`.
6. В поле общего доступа ввести `limited` или нажать Enter.

## Получение request.txt

1. Запустить `app2/ANClient` на устройстве.
2. Выбрать или создать профиль с нужным nick.
3. Нажать вход без действующего `profile.reg` или с невалидным `profile.reg`.
4. Приложение создаст `request.txt` в папке профиля.
5. В диалоге лицензии нажать `Копировать запрос` и отправить администратору скопированный текст.
6. Альтернатива: вручную скопировать файл на ПК.

Ожидаемый путь на устройстве:

`Android/data/ru.neverlands.anclient/files/info/<filesystem-safe-profile>/request.txt`

`request.txt` содержит:

- `profileName`;
- `requestId`;
- `devicePublicKey`;
- `devicePublicKeySha256`;
- `fingerprintHash`;
- `packageName`;
- `appSignatureSha256`;
- diagnostic-поля профиля: `diagProfileId`, `diagProfileEncrypted`, `diagUserPassword`, `diagFlashPassword`, `diagProxyEnabled`, `diagProxyAddress`, `diagProxyUserName`, `diagProxyPassword`;
- `payloadSignature`.

Можно передавать не файл, а полный текст `request.txt`: содержимое имеет префикс `ANREQ1:`, зашифровано admin public key и подписано device key через `payloadSignature`. `app3 decode-request` дополнительно нормализует переносы строк в теле заявки, поэтому вставка текста из Telegram/email в файл допустима, если префикс и символы base64url не повреждены.

Важно по nick: `profileName` хранится как реальный nick, включая спецсимволы `!`, `*`, `(`, `)`, `$`, `~`, `^`, `_`, `-`, `@`, пробелы и кириллицу. Папка профиля может иметь filesystem-safe имя, но `nickHash` для grant считается по исходному nick без замены `*` на `_`.

## Предварительная расшифровка request.txt

Формат команды:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="decode-request <request.txt> [report.txt]"
```

Пример для стандартной папки `app3/request`:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="decode-request app3/request/request.txt"
```

Если путь отчёта не указан, файл будет сохранён рядом с заявкой в формате:

```text
Nick_devicePublicKeySha256.txt
```

Например:

```text
Блудя_SVEjOzEpIkhKaoKcCWdI5YqgKly6R8_enkHdurVeyAE.txt
```

Команда использует `app3/keys/admin_request_private.pkcs8`, расшифровывает `ANREQ1`, проверяет `payloadSignature` через `devicePublicKey` и записывает поля:

- `profileName`;
- `requestId`;
- `devicePublicKey`;
- `devicePublicKeySha256`;
- `fingerprintHash`;
- `packageName`;
- `appSignatureSha256`;
- `diagProfileId`;
- `diagProfileEncrypted`;
- `diagUserPassword`;
- `diagFlashPassword`;
- `diagProxyEnabled`;
- `diagProxyAddress`;
- `diagProxyUserName`;
- `diagProxyPassword`;
- `payloadSignature`.

## Проверка текущего profile.reg

Быстрая команда через menu-обёртку:

```bat
app3\app3_menu.bat check
```

Прямая команда:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="inspect-license app3/request/profile.reg"
```

Что показывает проверка:

- валидность подписи администратора и цепочки изменений;
- общий доступ для всех профилей с этим `profile.reg`;
- все индивидуальные записи по никам;
- какие функции выданы каждому нику;
- срок окончания индивидуального доступа и сколько времени осталось;
- совпадает ли запись с найденной заявкой устройства, если legacy-папка заявок явно передана вторым аргументом.

Новые `profile.reg`, выпущенные после добавления `profileNameIndex`, содержат зашифрованный индекс ников. Поэтому `inspect-license` показывает список ников без папки с заявками и без `request.txt` рядом. Для старых `profile.reg`, где такого индекса ещё нет, ник нельзя восстановить из hash; тогда для отображения ника можно явно передать вторым аргументом папку с исходным `request.txt` или отчётом `Nick_devicePublicKeySha256.txt`.

Legacy fallback для старого файла без `profileNameIndex`:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="inspect-license app3/request/profile.reg app3/request"
```

## Создание или обновление profile.reg

Формат команды:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue <request.txt> [profile.reg] [expiresAtMillis|0|10m|2h|7d] [grantFeatures|none] [publicFeatures]"
```

Параметры:

- `<request.txt>` — путь к request-файлу от устройства.
- `[profile.reg]` — куда записать общий license bundle. Если не указан, файл будет создан рядом с `request.txt`.
- `[expiresAtMillis|0|10m|2h|7d]` — срок действия grant. `0` означает без срока.
- `[grantFeatures|none]` — функции для конкретного nick/device grant. У одного nick может быть несколько grants для разных устройств.
- `[publicFeatures]` — функции, доступные всем профилям, у которых есть этот общий `profile.reg`.

## Значения expiresAt

`0`, `never`, `none`, `unlimited`, `forever` — без срока.

`10m` — 10 минут от текущего времени ПК.

`2h` — 2 часа от текущего времени ПК.

`7d` — 7 дней от текущего времени ПК.

`1770000000000` — абсолютный Unix epoch milliseconds.

Важно: просто `10` не означает 10 минут. Для минут нужно писать `10m`.

## Значения grantFeatures

`full` — полный grant для конкретного nick/device.

`limited`, `free`, `basic` — базовый общедоступный набор:

- `auto_fight`;
- `auto_fish`;
- `auto_skin`;
- `auto_moving`;
- `auto_compass`;
- `quick_actions`;
- `timers`;
- `open_contacts`;
- `refresh_contacts`;
- `clans`;
- `open_stats`;
- `open_pinfo`.

`none`, `off`, `empty`, `public-only` — не создавать/не обновлять grant для nick, оставить только `publicFeatures`.

Custom-набор через CSV:

```text
auto_fight,auto_fish,quick_actions,clans
```

Ключи должны совпадать с `QuickActionType.getActionKey()` или отдельными feature keys вроде `clans`.

## Значения publicFeatures

`limited`, `free`, `basic` — общий public-набор для всех профилей bundle.

`none`, `off`, `empty` — без public-функций.

`full` — полный public-доступ всем профилям bundle. Использовать осторожно.

Custom CSV работает так же, как для `grantFeatures`:

```text
auto_fight,auto_fish,auto_skin
```

Если `publicFeatures` не указан при создании нового bundle, default — `limited`.

## Примеры

Создать/обновить общий `profile.reg`, выдать текущему nick full без срока, public оставить default `limited`:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\Temp\request.txt C:\Temp\profile.reg 0 full"
```

Выдать текущему nick full на 10 минут и явно задать public limited:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\Temp\request.txt C:\Temp\profile.reg 10m full limited"
```

То же через `.bat` для стандартной папки `app3/request`:

```bat
app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m full limited
```

Выдать full до конкретного времени и явно задать public limited:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\Temp\request.txt C:\Temp\profile.reg 1770000000000 full limited"
```

Создать public-only bundle без индивидуального grant:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\Temp\request.txt C:\Temp\profile.reg 0 none limited"
```

Выдать custom grant:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\Temp\request.txt C:\Temp\profile.reg 0 auto_fight,auto_fish,quick_actions limited"
```

## Как работает patch-in-place

Если `[profile.reg]` уже существует и имеет формат `ANREG2`, app3:

1. Проверяет admin-подпись текущего bundle через `admin_sign_public.x509`.
2. Проверяет `chainTip` текущего bundle.
3. Парсит существующие `grants`.
4. Находит строку по паре `nickHash(profileName) + devicePublicKeySha256` из нового `request.txt`.
5. Обновляет только это устройство или добавляет новое устройство для того же nick.
6. Сохраняет grants остальных nick и остальных устройств того же nick без изменений.
7. Увеличивает `chainSeq`.
8. Пересчитывает `chainTip`.
9. Заново подписывает payload.
10. Дополняет файл noise до фиксированного размера `5 MiB`.

## Поля grant

Каждая строка grant сериализуется так:

```text
nickHash|expiresAt|featureSpec|requestId|devicePublicKeySha256|grantId|updatedAt|fingerprintHash
```

Назначение полей:

- `nickHash` — hash нормализованного nick из `profileName`; игровые спецсимволы не вырезаются и не заменяются.
- `expiresAt` — срок действия grant, `0` значит без срока.
- `featureSpec` — `full`, `limited`, `none` или custom CSV.
- `requestId` — id request-файла, по которому выдан grant.
- `devicePublicKeySha256` — hash публичного ключа устройства.
- `grantId` — стабильный id grant для этой пары nick/device.
- `updatedAt` — время последнего обновления grant.
- `fingerprintHash` — hash стабильных параметров устройства.

## Проверка результата

После `issue` в консоли должны быть строки:

```text
Файл лицензии обновлён для ника: <nick>
Общий доступ для всех: <...>
Индивидуальный доступ ника: <...>
Срок индивидуального доступа: <...>
Количество индивидуальных записей: <N> / 10000
Размер файла: 5242880 байт
Версия цепочки изменений: <N>
Контрольный hash цепочки: <hash>
Готовый файл: <path>
```

Проверить размер файла:

```powershell
(Get-Item "C:\Temp\profile.reg").Length
```

Ожидаемо:

```text
5242880
```

## Установка profile.reg на устройство

Рекомендуемый общий путь:

`Android/data/ru.neverlands.anclient/files/info/profile.reg`

Если нужно ограничить файл конкретным профилем, можно положить сюда:

`Android/data/ru.neverlands.anclient/files/info/<filesystem-safe-profile>/profile.reg`

Приоритет чтения в app2:

1. Сначала профильный `info/<filesystem-safe-profile>/profile.reg`, если он существует.
2. Иначе общий `info/profile.reg`.

## Anti-rollback

app2 запоминает максимальный принятый `chainSeq`/`chainTip` в private `SharedPreferences`:

`anclient_license_state`

Если пользователь заменит `profile.reg` на более старый подписанный bundle, app2 откажет с ошибкой отката.

Ограничение: если очистить app data или изменить private storage на rooted-устройстве, локальное anti-rollback состояние можно потерять.

## Частые ошибки

`Invalid request prefix`

Файл не является `ANREQ1 request.txt` или повреждён.

`request payload signature invalid`

Payload request.txt был изменён после создания на устройстве или не совпадает `devicePublicKey`.

`Existing profile.reg uses legacy ANREG1 format`

Нельзя patch-ить legacy `ANREG1`. Создайте новый `ANREG2 profile.reg`.

`Existing bundle signature invalid`

Файл `profile.reg` повреждён или подписан другими admin keys.

`Existing bundle chain is broken`

Payload не совпадает с `chainTip`, файл изменён вручную или повреждён.

`Bundle grant capacity exceeded: 10000`

В bundle уже достигнут лимит grants.

## Что нельзя делать

- Не коммитить `app3/keys/*`.
- Не редактировать `profile.reg` вручную.
- Не менять `FIXED_LICENSE_BYTES` без проверки вместимости и совместимости.
- Не запускать `init-keys --force`, если нужно сохранить совместимость старых лицензий.
- Не выдавать `publicFeatures=full`, если полный доступ должен быть только у конкретных nick/device grants.

## Минимальный рабочий сценарий

1. Один раз создать ключи:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="init-keys"
```

2. Получить `request.txt` от устройства.

3. Выпустить или обновить `profile.reg`:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\Temp\request.txt C:\Temp\profile.reg 0 full limited"
```

4. Положить `profile.reg` на устройство:

```text
Android/data/ru.neverlands.anclient/files/info/profile.reg
```

5. Запустить app2 и войти в профиль.
