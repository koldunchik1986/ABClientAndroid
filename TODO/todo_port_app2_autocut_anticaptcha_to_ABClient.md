# Портирование app2 Auto-Травника и Anti-Captcha в ABClient C#

## Цель

- [x] Проанализировать текущую реализацию `ABClient/` для травника и капчи.
- [x] Проанализировать рабочую реализацию `app2/` для `AutoCut` и `AntiCaptcha`.
- [x] Перенести первый runtime-слой в ПК-версию C# без лицензирования.
- [ ] Сохранить текущий WinForms-дизайн и внедрять травник только в начатый модуль травника.
- [ ] Проверить сборку и кодировку.

## Анализ ABClient

### Текущие точки травника

- `ABClient/ABForms/FormMainHerbs.cs` уже содержит `HerbsList`, `IsHerbAutoCut`, `HerbCut`, `DoHerbAutoCut`, `TraceCut`, `TraceCutID`.
- `HerbsList(list)` работает: сохраняет список трав текущей клетки в `AppVars.Profile.HerbCells[MapLocation]`.
- `TraceCut/TraceCutID` работают: пишут сообщение и ставят herb timer `Вырастет <трава> на <клетка>`.
- `IsHerbAutoCut`, `HerbCut`, `DoHerbAutoCut` были закомментированы и возвращали/делали ничего.
- `AppVars.DoHerbAutoCut` есть только как закомментированное поле.
- `FormMainTicks.cs` содержит закомментированный tick-блок авто-`Оглядеться`.
- `FormMain.cs` содержит закомментированные обработчики включения автоспила и настроек.
- `FormSettingsAutoCut` уже имеет текущий дизайн выбора трав и флаг `Выводить в чат результат`.
- `map.js` уже содержит hooks `window.external.HerbsList(...)`, `DoHerbAutoCut()`, `TraceCut(...)`, `Ogl(...)`, `ResoStart(...)`, но ищет только legacy `ogl`.
- `Filter.Process(...)` не маршрутизирует `gameplay/ajax/alchemy_ajax.php` в отдельный обработчик.

### Текущие точки капчи

- `AppVars.CodePng` заполняется в `ABProxy/Session.cs` при загрузке `modules/code/code.php?...`.
- `AppVars.FightLink` хранит pending protected action; placeholder капчи — `????`.
- `FormMainTicks.cs` при `FightLink` с `????` запускает `MyGuamod.Recognizer.Perform()`.
- `Recognizer` локально распознаёт captcha через `NeuroBase`; fallback при ошибке очищает `FightLink` и reload-ит main.php.
- `FormCode` остаётся ручным fallback для ввода captcha.

## Анализ app2

### Anti-Captcha

- `AntiCaptchaManager.java` реализует внешний сервис `https://api.anti-captcha.com/createTask` и `getTaskResult`.
- DTO `Config`: `clientKey`, `phrase`, `caseSensitive`, `numeric`, `math`, `minLength`, `maxLength`, `languagePool`.
- `createTask` отправляет `ImageToTextTask` с `body=<base64 image>`.
- `getTaskResult` polling: до 24 попыток, пауза 3 секунды, берёт `solution.text` при `status=ready`.
- Runtime не отправляет captcha напрямую: возвращает код в общий captcha submit-контур.
- В C# портируем без лицензирования и без Android proxy/runtime checks.

### Auto-Травник

- `AlchemyAjaxPhp.java` обрабатывает `alchemy_ajax.php?act=1/act=3`.
- `act=1` парсит `RESO@`, регистрирует травы, выбирает первую доступную выбранную траву (`availableCount > 0` и `cutVcode` не пустой).
- `act=3` использует `cutVcode` из записи ресурса, не глобальный vcode.
- Captcha token пустой/`00000` означает no-captcha `code=1`; иначе нужен popup/Anti-Captcha.
- Success marker: `Всё прошло успешно.` / `Все прошло успешно.`.
- Wrong-code marker: `невер` + `код`, после него ставится retry текущей клетки.
- `AutoCutManager` держит выбранные травы, клетки, timers, checked-cells, retry, mass-sync, cleanup.
- `AutoCutHandler` готовит main.php контекст: серп, inventory, mass-sync, cleanup; сам `act=3` не отправляет.
- `map.js` app2 принимает `look` и legacy `ogl`, ставит guard 3 секунды и delayed `Ogl` 250 ms.

## План реализации в ABClient C#

- [x] Создать TODO-анализ портирования.
- [x] Включить `AppVars.DoHerbAutoCut` как runtime-флаг.
- [x] Реанимировать существующие `FormMainHerbs.IsHerbAutoCut/HerbCut/DoHerbAutoCut` без платежных/license checks.
- [x] Добавить минимальный `AlchemyAjaxPhp` C# postfilter: parse `act=1/act=3`, pending cut, no-captcha/captcha action через текущий `FightLink` placeholder.
- [x] Подключить `AlchemyAjaxPhp` в `Filter.Process(...)`.
- [x] Обновить `map.js` в существующем контуре: принимать `look` и `ogl`, не запускать параллельный native HTTP-контур.
- [x] Добавить `AntiCaptchaManager` C# как внешний HTTP-клиент anti-captcha.com.
- [x] Добавить настройки Anti-Captcha в `UserConfig` без изменения текущего дизайна: defaults + XML save/load.
- [x] Встроить Anti-Captcha перед `Recognizer.Perform()`; при выключении/ошибке fallback на старый `Recognizer`.
- [x] Проверить сборку `ABClient.csproj` / доступный build tool.
- [-] Проверить `ABClient10.csproj`: сборка блокируется до компиляции текущим старым отсутствующим файлом `MyForms/FormContact.cs`, не связанным с этой правкой.
- [x] Проверить mojibake для изменённых `.cs` и TODO — совпадений `РЎР`/`РџС`/`Ð`/`Ñ` нет.

## Первый этап реализации

- Реализуется минимальный безопасный слой:
  - включение runtime-флага травника;
  - выбор травы через существующий список `HerbsAutoCut`;
  - postfilter `alchemy_ajax.php` для `act=1/act=3`;
  - pending `FightLink` для captcha/no-captcha через существующий tick-контур;
  - Anti-Captcha сервис с fallback на старый `Recognizer`.
- Серпы, mass-sync, cleanup и route по timer-ам будут дорабатываться следующим проходом, чтобы не ломать текущий WinForms flow большим изменением за раз.

## Выполнено 2026-04-28

- [x] `AppVars.DoHerbAutoCut` восстановлен как runtime-флаг.
- [x] На toolbar добавлена кнопка `Автотравник` в текущем стиле текстовых авто-кнопок; при пустом списке трав открываются существующие настройки автоспила.
- [x] `FormSettingsAutoCut` после сохранения выбранных трав включает runtime-флаг, если список не пустой.
- [x] `FormMainHerbs.IsHerbAutoCut/HerbCut/DoHerbAutoCut` снова выполняют реальную работу без старой pay/license проверки.
- [x] `PostFilter/AlchemyAjaxPhp.cs` добавлен как C# аналог app2 `AlchemyAjaxPhp`: парсит `RESO@`, выбирает первую доступную выбранную траву, ставит pending `FightLink` для no-captcha/captcha, обрабатывает success/wrong-code.
- [x] `Filter.Process(...)` маршрутизирует `gameplay/ajax/alchemy_ajax.php` в новый postfilter.
- [x] `FormMainTicks` отправляет `alchemy_ajax.php?act=3` через `AjaxGet` текущего `main_top` frame, если `FightLink` уже без `????`.
- [x] `FormMainTicks` перед локальным `Recognizer.Perform()` пробует `AntiCaptchaManager.TrySolveCurrentCaptchaWithFallback()`.
- [x] `MyGuamod/AntiCaptchaManager.cs` добавлен: `createTask`, polling `getTaskResult`, browser User-Agent, stale-check challenge, fallback на локальный recognizer.
- [x] `UserConfig` получил XML-настройки Anti-Captcha: `enabled`, `apikey`, `phrase`, `case`, `numeric`, `math`, `minlength`, `maxlength`, `languagepool`.
- [x] `map.js` обновлён в существующем контуре: поддерживает `look` и `ogl`; no-captcha `ResoStart` ставит `code=1`, если `CAPCODE` отсутствует.
- [x] `ABClient.csproj` собран через Visual Studio MSBuild 2022: `Build succeeded`, 0 warnings, 0 errors.
- [-] `dotnet build ABClient.csproj` невозможен в этой среде без reference assemblies `.NETFramework v2.0`; рабочий build tool — VS MSBuild.
- [-] `ABClient10.csproj` не собран из-за pre-existing ошибки `MyForms/FormContact.cs` not found.
- [-] `git diff --check -- ABClient TODO` показывает только известное `.gitattributes:7` и LF/CRLF warnings.

## Инварианты

- Дизайн WinForms не менять, существующие формы и кнопки сохранять.
- Травник внедрять в `FormMainHerbs`, `FormSettingsAutoCut`, `map.js`, `Filter`, а не создавать отдельный параллельный UI.
- Не добавлять лицензирование.
- Для логов использовать `AppLog`/`FileLogger`.
- Не добавлять User-Agent с маркерами клиента для внешнего Anti-Captcha API; использовать браузерный UA.
- При сбое Anti-Captcha сохранять fallback на локальный `Recognizer` или ручной ввод.
