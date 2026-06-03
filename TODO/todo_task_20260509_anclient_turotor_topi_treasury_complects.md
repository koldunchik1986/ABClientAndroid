# Задача 2026-05-09: перенос Туротор/Гиблая топь/казна/комплекты в ANClient

## Цель

Перенести в `ANClient` недостающую функциональность из восстановленного IBC runtime: режим `Остров Туротор / Гиблая Топь`, клановую казну с фильтрами `Все` / `Рары` / `Арты`, а также управление комплектами казны, не дублируя уже существующие fast-action, navigation, shop и profile контуры.

## Источники

- `TODO/todo_ANClient_vs_IBClient.md` - верхнеуровневая матрица фактов по IBC / ANClient / Android.
- `TODO/IBClient/todo_FormTurotor.cs.md` - разобранный контракт `FormTurotor`.
- `TODO/IBClient/todo_ScriptManager.cs.md` - разобранный item bridge и отличие shop/market от клан-казны.
- `TODO/todo_task_20260502_ibclient_runtime_diff.md` - подтвержденные anchors IBC runtime.
- `ABClient/PostFilter/*.cs` - read-only reference для совместимости старой PC-логики.
- `IBClient2/restored_project/` - восстановленный IBC source-of-truth по казне и комплектам.
- `ANClient/` - целевая C# кодовая база для внедрения.

## Инварианты

- [x] Не менять `ABClient/`; использовать только как справочник.
- [x] Не создавать параллельный teleport/treasury/shop-контур, если есть существующая точка расширения в `ANClient`.
- [x] Для клан-казны не хардкодить suffix take URL: брать готовую ссылку `main.php?get_id=29&uid=<itemId>...` из HTML страницы казны.
- [x] Не смешивать `FormTreasure` auto-digging с клан-казной: `d9PBFLSwqa[0..2]` относится к лопатам, не к фильтрам казны.
- [x] Сохранять существующие профильные поля `Complects` в `ANClient` и расширять их только при подтвержденной необходимости.

## План работ

- [x] Найти существующие документы анализа по IBC runtime и ANClient diff.
- [x] Досмотреть `ANClient/PostFilter/MainPhpFast.cs` и подтвердить полноту `Телепорт (Остров Туротор)`.
- [x] Найти точные текущие точки расширения `ANClient` для UI: main form toolbar/menu, profile save/load, script bridge, postfilter routing.
- [x] Сопоставить IBC казну: state режима `Все`/`Рары`/`Арты`, очередь item-id комплектов, вход в казну и take action.
- [x] Реализовать минимальные недостающие части в существующих `ANClient` файлах.
- [-] Обновить `ANClient.csproj` и `ANClient10.csproj`, если будут добавлены новые `.cs` файлы: новые файлы не добавлялись.
- [-] Проверить сборку или зафиксировать причину, если локальная сборка невозможна: `msbuild` не установлен, `dotnet build ANClient10.sln -c Release` остановился на отсутствии reference assemblies для `.NETFramework,Version=v2.0`.
- [x] Проверить touched `.cs`/`.md` на UTF-8 без BOM и mojibake-паттерны.
- [x] Обновить этот TODO по факту выполненных изменений.

## Текущие факты

- `ANClient` уже содержит быстрый action `Телепорт (Остров Туротор)` и route flag `MapPath.IsIslandRequired`.
- `ANClient` уже содержит профильное поле `Complects` и flow надевания комплектов через `MainPhpWearComplect`.
- Реализован runtime-пункт `Телепорт (Гиблая Топь)` поверх существующего fast-action `MainPhpFastIsland`: выбор спецтелепорта строится из `var telep` как `main.php?get_id=16&act=3&sp=...&vcode=...`.
- Реализована клановая казна поверх `MainPhp`: вход `main.php?wfo=1&useaction=clan-action&addid=3`, режимы `Все`/`Рары`/`Арты`, очередь id комплекта и direct take action из готовой HTML-ссылки `main.php?get_id=29&uid=...`.
- Для комплектов казны добавлено отдельное профильное поле `ClanKaznaComplects`; существующий `Complects` для надевания не изменён.
- После сборки VS 2022 исправлен запуск профилей: `ResGen` создаёт WinForms resources с `System.Drawing 4.0`, поэтому `ANClient/app.config` теперь предпочитает CLR v4 с fallback на v2, чтобы `bin/Release/ANClient.exe` не падал на загрузке профиля.

## Дополнение 2026-05-09: post-battle anti-captcha в `D:\IBC_OLD`

- [x] Отказались от dnlib-rewrite `D:\IBC_OLD\IBClient_BD_patched.exe`: rewritten protected exe падал с CLR `0xE0434352`; рабочий exe восстановлен из backup.
- [x] Проверили safe target: `D:\IBC_OLD\Cache\www.neverlands.ru\js\fight_v10.js` содержит ветку post-battle captcha формы `name=FEND` в `fight_ty[4] == 2`.
- [x] Создан backup `fight_v10.js.bak_postfight_anticaptcha_20260509`.
- [x] В cached `fight_v10.js` byte-level способом добавлен `id=CAPInput` и ASCII-only fallback `__ibcPostFightCaptchaAuto`: `AntiCaptchaStart()` -> polling `GetCaptchaCode()` -> заполнение `CAPInput` -> `ClearCaptchaCode()` -> submit формы `FEND`.
- [x] Синтаксис patched JS проверен через `node --check`.
- [x] `D:\IBC_OLD\IBClient_BD_patched.exe` smoke-test: окно запускается, `CloseMainWindow=True`, принудительное завершение не потребовалось.
- [x] `Newtonsoft.Json.dll` в `D:\IBC_OLD` проверен как `13.0.0.0`; exe совпадает с рабочим backup `IBClient_BD_patched.exe.bak_postfight_20260509`.
- [x] Top-level `D:\IBC_OLD\*.profile` проверены как XML OK, UTF-8 BOM сохранён, `anticaptchakey` заполнен во всех текущих top-level профилях без вывода значения ключа.

## Дополнение 2026-05-10: уточнение диагностики post-battle anti-captcha

- [x] Сопоставлен runtime crash `NullReferenceException` в `FormMain`: стек `bWEmQipJ9Q()` -> `CwV4i1LDFy()` -> `O9FPYHXhSP()` соответствует `OnFormMainResize()` -> `MainFormResize()` -> `TrayShow()`.
- [x] Точное место NRE: `O9FPYHXhSP()` вызывает `Ise786hEx8hMh1IGbq60(this.ksJXJBcQB2)`, wrapper возвращает `arg0.Images`; значит при падении `ksJXJBcQB2` (`trayImages`) равен `null`.
- [x] Сопоставление с read-only `ABClient`: `trayImages` создаётся в `InitializeComponent()`, `TrayShow()` вызывается только при `WindowState == Minimized && AppVars.Profile.DoTray`.
- [x] Проверены top-level профили `D:\IBC_OLD\*.profile`: `dotray=true`, `showtraybaloons=true`; window state у `z3k4fi41.profile` = `Normal`, у `3o5rw0fw.profile` = `Maximized`.
- [x] Важное уточнение по cached JS: `ABClient.ABProxy.Cache.GetDisk()` и runtime `Cache.cs` явно возвращают `null` для `.js`, поэтому byte-level правка `D:\IBC_OLD\Cache\www.neverlands.ru\js\fight_v10.js` не является надёжным fix-path после перезапуска процесса.
- [x] Найден существующий live-контур: runtime `ABClient.PostFilter.Filter.FightJs()` уже содержит замену `var tkeys = '';` -> JS с `AntiCaptchaStart()`/`GetCaptchaCode()`/`ClearCaptchaCode()` и замену `name=code` -> `name=code id=CAPInput`.
- [x] Проверены markers в patched cached `fight_v10.js`: `id=CAPInput`, `__ibcPostFightCaptchaAuto`, `AntiCaptchaStart`, `GetCaptchaCode`, `ClearCaptchaCode`, `document.forms["FEND"]`, `f.submit()` присутствуют; но из-за `.js` cache bypass это только вспомогательный артефакт, не подтверждённый runtime fix.
- [ ] Следующий шаг: подтвердить на реальном runtime, что ответ `/js/fight_v*.js` проходит через `Filter.FightJs()` и что в фактически исполняемом WebBrowser JS есть `CAPInput` и `AntiCaptchaStart()`.
- [ ] Если live-фильтр не применяется, исправлять существующий `Filter.FightJs`/маршрутизацию `/js/fight_v`, а не добавлять параллельный workaround в disk cache.
- [ ] Если live-фильтр применяется, но API не вызывается, проверять `window.external`/`ScriptManager.AntiCaptchaStart()` и наличие загруженного `CodePng` от `/modules/code/code.php?...`.
