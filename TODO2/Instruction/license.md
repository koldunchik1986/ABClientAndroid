# Инструкция по лицензированию ANClient

## Назначение

`profile.reg` в актуальном формате `ANREG2` является общим fixed-size bundle-файлом. Один файл можно положить в `Android/data/ru.neverlands.anclient/files/info/profile.reg`, после чего он будет применяться ко всем профилям.

## Форматы

- `request.txt`: префикс `ANREQ1:`. Создается приложением для конкретного профиля/nick; пользователь может отправить администратору весь текст через кнопку `Копировать запрос`.
- `profile.reg`: префикс `ANREG2:`. Содержит подписанный payload и крипто-шум до фиксированного размера 5 MiB.
- Legacy `ANREG1:` остается переходным single-profile форматом, но новые выдачи должны идти через `ANREG2`.

## Логика доступа

- `publicFeatures` задает функции, доступные всем пользователям общего `profile.reg`.
- Nick-grant задает расширенные функции для конкретного ника и устройства.
- У одного ника может быть несколько nick-grants для разных устройств; ключ обновления — `nickHash + devicePublicKeySha256`.
- Если nick-grant активен, `expiresAt` не истек, совпали nick hash, device public key hash и fingerprint hash, итоговый доступ равен `publicFeatures + grantFeatures`.
- Если nick-grant истек, ника нет в списке grants или устройство не совпало, пользователь остается на `publicFeatures`.
- При runtime-downgrade `full -> publicFeatures` приложение перевалидирует bundle, обновляет UI и снимает persisted/runtime-флаги автофункций, которых больше нет в текущем allow-list.
- Nick в bundle не хранится открытым текстом: используется SHA-256 hash нормализованного ника.
- Для админского отчёта новые `profile.reg` содержат зашифрованный `profileNameIndex`, который app3 расшифровывает локальным `admin_request_private.pkcs8`.
- Нормализация nick для hash сохраняет игровые спецсимволы `!`, `*`, `(`, `)`, `$`, `~`, `^`, `_`, `-`, `@`, пробелы и кириллицу. Замена `\\/:*?"<>|` на `_` применяется только к имени папки профиля, а не к license identity.

## Размер файла

- `app3` всегда пишет `ANREG2 profile.reg` размером 5 MiB.
- В payload указан `slotCapacity=10000`, то есть файл рассчитан на хранение до 10000 nick-grants.
- Свободное место заполняется случайным base64url-шумом.
- Криптографический hash содержимого будет меняться при каждом patch, но размер файла остается постоянным.

## Chain-Patch

Каждый patch обновляет поля:

- `chainSeq`: номер версии bundle.
- `prevChainTip`: hash предыдущей версии.
- `chainTip`: hash текущего состояния grants/publicFeatures.

Новая подпись администратора покрывает весь текущий payload, включая grants всех пользователей. Поэтому patch одного пользователя подтверждает всю цепочку состояния до него и не сбрасывает timeout остальных.

`app2` дополнительно запоминает максимальный принятый `chainSeq`/`chainTip` в private app data. Если пользователь подменит файл на старый подписанный bundle с меньшим `chainSeq`, приложение откажет до очистки app data.

## Команды app3

Инициализация ключей:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="init-keys"
```

Проверка текущего `profile.reg` с расшифровкой доступных заявок:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="inspect-license C:\path\to\profile.reg C:\path\to\request_folder"
```

То же через Windows-меню:

```bat
app3\app3_menu.bat check
```

Отчёт показывает подпись/chain-state, общий доступ для всех профилей, каждый индивидуальный доступ по нику, набор функций и оставшееся время. В новых `profile.reg` ник берётся из зашифрованного `profileNameIndex` без хранения `request.txt` рядом. Для старых bundle без индекса ник отображается только если рядом есть соответствующий `request.txt` или отчёт `Nick_devicePublicKeySha256.txt`; иначе будет показан hash ника.

Расшифровать заявку и сохранить отчёт с автоматическим именем:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="decode-request C:\path\to\request.txt"
```

Имя отчёта по умолчанию: `Nick_devicePublicKeySha256.txt`.

`decode-request` принимает request как файл с полным текстом `ANREQ1:...`; переносы строк в теле заявки нормализуются перед расшифровкой. Целостность проверяется через `payloadSignature`, поэтому поврежденный или измененный текст будет отклонен.

Первичная выдача bundle по `request.txt`, public-функции по умолчанию `limited`, текущему nick дается `full`:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\path\to\request.txt C:\path\to\profile.reg 0 full"
```

Выдача/patch с конкретным timeout:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\path\to\request.txt C:\path\to\profile.reg 1770000000000 full"
```

Выдача/patch custom-grant для ника:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\path\to\request.txt C:\path\to\profile.reg 1770000000000 auto_fight,auto_fish,auto_skin"
```

Изменить public-функции для всех при очередном patch:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\path\to\request.txt C:\path\to\profile.reg 1770000000000 full limited"
```

Сделать public-only patch без grant для приславшего `request.txt`:

```powershell
.\gradlew.bat --no-daemon :app3:run --args="issue C:\path\to\request.txt C:\path\to\profile.reg 0 none limited"
```

## Feature Specs

- `full`: все быстрые/авто-функции и `clans`.
- `limited`, `free`, `basic`: базовый набор `auto_fight`, `auto_fish`, `auto_skin`, `auto_moving`, `auto_compass`, `quick_actions`, `timers`, `open_contacts`, `refresh_contacts`, `clans`, `open_stats`, `open_pinfo`.
- `none`, `off`, `empty`: пустой набор.
- CSV: конкретные feature keys через запятую, например `auto_fight,auto_fish`.

## Где лежат файлы

- Общий bundle: `Android/data/ru.neverlands.anclient/files/info/profile.reg`.
- Профильный fallback: `Android/data/ru.neverlands.anclient/files/info/<filesystem-safe-profile>/profile.reg`.
- Request конкретного пользователя: `Android/data/ru.neverlands.anclient/files/info/<filesystem-safe-profile>/request.txt`.
- В `request.txt` diagnostic-поля профиля зашифрованы внутри `ANREQ1`: для зашифрованного профиля `diagUserPassword` и `diagFlashPassword` пишутся уже после расшифровки пользовательским паролем на экране входа.
- `<filesystem-safe-profile>` нужен только для пути: например nick `*XuLIgaN*` попадет в папку `_XuLIgaN_`, но grant будет считаться по `*XuLIgaN*`.
- Internal fallback при недоступном external storage: `files/info/...` внутри app data.

## Проверка

- Собрать: `.\gradlew.bat --no-daemon :app2:compileDebugJavaWithJavac :app3:build`.
- Для limited/public пользователя убедиться, что full-функции не видны в quick-actions/timers/drawer.
- Для nick с active full-grant убедиться, что full-функции доступны до `expiresAt`.
- После истечения full-grant без перезапуска убедиться, что public-функции остаются, а недоступные автофункции выключены и не продолжают фоновые действия.
- После patch проверить, что `profile.reg` остался 5 MiB, `grantCount` не сбросился, `chainSeq` увеличился.
