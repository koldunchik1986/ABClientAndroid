# Задача 2026-06-03: перенос выбора сервера из IBClient2 в ANClient/app2

## Контекст

- Пользователь нашёл предпоследнюю версию `IBClient2/IBClient2.exe`.
- Нужно перенести в текущий `ANClient/app2` выбор игрового сервера при выборе профиля, так как у Neverlands теперь два сервера.
- Лицензионный код в текущей итерации не трогается: текущий scope ограничен выбором сервера.

## Ограничения

- `IBClient2` используется как источник для анализа поведения, изменения в нём не выполняются.
- Android-изменения выполняются в `app2/`; по отдельному запросу пользователя аналогичные правки внесены в `ANClient/`.
- Не добавлять второй сетевой контур, а встроить выбор сервера в существующий профильный/логин-контур.
- Лицензионные изменения отложены и не входят в текущий scope.

## План

- [x] Найти в `app2` текущий контур выбора профиля и логина: `LoginActivity -> AuthManager -> NetworkClient`.
- [x] Извлечь доступное поведение выбора сервера из `IBClient2`: явных `.de/.kz` host не найдено, используются коды `DE`/`KZ`.
- [x] Добавить в `app2` UI/хранение редактируемого списка серверов с IP/ping и подключить к логину.
- [x] Добавить аналогичный proxy-safe редактируемый выбор сервера в ПК `ANClient`.
- [-] Лицензионные правки отложены: текущая итерация их не меняет.
- [x] Собрать `app2`: `./gradlew.bat --no-daemon :app2:assembleDebug`.
- [x] Проверить mojibake-паттерны и User-Agent/client-id регрессию.
- [x] Проверить падение сборки `ANClient/` и устранить найденную C# ошибку.

## Реализация 2026-06-03

- Добавлен `GameServerUrls` как единая точка выбора серверного кода и URL текущего игрового host.
- В `UserConfig` добавлено поле `GameServerCode`, сохраняется как `user@server`, старые профили получают default `DE`.
- В `activity_login.xml` и `LoginActivity` добавлен выбор `DE`/`KZ` на экране логина, значение сохраняется в профиль.
- В `AuthManager` выбранный сервер передаётся в штатный login/captcha form body как `server=de` или `server=KZ`; для `neverlands.ru`/custom-entry параметр `server` может быть пустым.
- В `MainActivity` первичная загрузка frame-URL, chat polling, room-list refresh, cookie sync и logout переведены на `GameServerUrls`.
- Выбранный server теперь является фактическим endpoint (`DE=136.243.18.79`, `KZ=213.148.10.84`, fallback `neverlands.ru=neverlands.ru`), proxy используется только как транспорт.
- В `LoginActivity` добавлено фоновое обновление ping каждые 10 секунд, selector строится из редактируемого списка и показывает `CODE (host) - N ms`.
- Исправлена нормализация выбора: отображаемая строка spinner не сохраняется как код, в профиль и login-flow попадает только `DE`/`KZ`.
- Исправлена потеря `PHPSESSID` на IP endpoint: `AuthManager` теперь дополнительно забирает `Set-Cookie` из auth/flash/main responses, а `CookiesManager.assign()` зеркалит cookies как host-safe `name=value; Path=/` без несовместимого `Domain=.neverlands.ru`.
- Проверка: сборка `:app2:assembleDebug` успешна после cookie-fixes.

## Дополнение для ПК `ANClient` 2026-06-03

- Добавлен `GameServerSelector` с редактируемым списком `anservers.txt`, default entries `DE`, `KZ`, `neverlands.ru` и TCP ping.
- В `UserConfig` добавлено поле `GameServerCode`, XML-атрибут `user@server`; старые профили default `DE`.
- В `FormProfile` добавлен ComboBox сервера с динамическим ping, кнопка редактирования списка и сохранение выбранного кода.
- В `PostFilter/IndexCgi.cs` добавлен optional hidden `<input name=server ...>` в существующую auto-submit форму входа, без обхода `AppVars.LocalProxy`.
- В `ANProxy/ServerChatter.cs` routing встроен в существующий `ConnectToHost`/`ResendRequest`: Neverlands-host переписывается на выбранный endpoint только на transport/request-forwarding этапе.
- В `ANProxy/CookiesManager.cs` cookies зеркалятся между selected endpoint, `www.neverlands.ru` и `neverlands.ru`, чтобы избежать потери сессии при alias-переходах.
- Для прямых `HttpWebRequest/WebClient` точек добавлен общий `GameServerSelector.RouteUrlToCurrentServer(...)`, чтобы активные background-запросы не обходили выбранный endpoint.
- `dotnet msbuild ANClient/ANClient.csproj` в текущем окружении заблокирован отсутствующим .NET Framework v2.0 targeting pack.
- Classic VS Build Tools MSBuild найден по пути `C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\MSBuild\Current\Bin\MSBuild.exe`; он доходит до компиляции и использует `C:\Windows\Microsoft.NET\Framework\v2.0.50727`.
- Исправлено падение компиляции `FormProfile.cs(259,28) CS0160`: `catch ObjectDisposedException` поставлен перед `catch InvalidOperationException`.
- Проверка: `ANClient/ANClient.csproj` успешно собран через VS Build Tools MSBuild в `Debug|AnyCPU`.
