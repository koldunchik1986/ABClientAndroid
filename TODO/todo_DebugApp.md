# Комплексная отладка и разработка

## Список задач

- [x] **Проблема:** Ошибки JavaScript при навигации между комнатами.
  - **Симптомы:**
    - `Uncaught TypeError: Cannot set properties of undefined (setting 'location')`
    - `Uncaught ReferenceError: cha_HP is not defined`
    - `Uncaught ReferenceError: ins_HP is not defined`
  - **Гипотеза:**
    1.  Отсутствие заглушек для JavaScript-функций `cha_HP` и `ins_HP`.
    2.  Некорректная эмуляция фреймовой структуры сайта. JavaScript-код пытается получить доступ к фреймам (`top.frames[...]`), которые не существуют в контексте WebView, что приводит к ошибке `setting 'location'`.
  - **План решения:**
    - [x] **Шаг 1:** Добавить заглушки для `cha_HP` и `ins_HP` в метод `injectJsFix` в `MainActivity.java`.
    - [x] **Шаг 2:** Создать в `injectJsFix` JavaScript-объект `top.frames`, который будет имитировать структуру фреймов.
    - [x] **Шаг 3:** Для каждого ожидаемого фрейма (`ch_buttons`, `ch_refr`, `ch_list`, `chmain`, `main_top`) создать объект-заглушку.
    - [x] **Шаг 4:** Реализовать для каждого объекта-заглушки сеттер для свойства `location`. Этот сеттер должен вызывать `AndroidBridge` для передачи URL и имени фрейма в Java-код.
    - [x] **Шаг 5:** Реализовать в `WebAppInterface` метод, который будет принимать URL и имя фрейма и загружать его в соответствующий `WebView`.

- [x] **Проблема:** Игровой фрейм не обновляется при навигации по комнатам.
  - **Симптомы:** При переходе по ссылкам в игре, основной фрейм (`main_top`) не обновляет свое содержимое, оставаясь на первоначальной комнате. В некоторых случаях вместо контента отображается белый экран.
  - **Гипотеза:** Проблема может быть связана с несколькими факторами: некорректная работа `shouldInterceptRequest`, проблемы с кешированием, ошибки в жизненном цикле `WebView` или отсутствие правильной обработки cookies.
  - **План решения:**
    - [x] **Шаг 1:** Проанализированы логи вызовов `WebAppInterface.loadFrame`, которые подтвердили, что URL для загрузки передаются корректно.
    - [x] **Шаг 2:** Временно удален метод `shouldInterceptRequest`, что привело к улучшению навигации, но отключило кеширование и модификацию контента.
    - [x] **Шаг 3:** Восстановлен `shouldInterceptRequest` с логикой перехвата только `.js` файлов. Это не решило проблему, так как HTML-страницы, содержащие ошибки, не обрабатывались.
    - [x] **Шаг 4:** Расширена логика `shouldInterceptRequest` для перехвата и обработки HTML-файлов (`.php`, `/`).
    - [x] **Шаг 5:** Добавлена проверка кода ответа сервера в `shouldInterceptRequest`. Если код не 200, запрос не обрабатывается, что предотвращает загрузку страниц с ошибками в `WebView`.
    - [x] **Шаг 6:** Добавлена передача cookies в `HttpURLConnection` в `shouldInterceptRequest` для правильной аутентификации.
    - [x] **Шаг 7:** В `injectJsFix` добавлен прокси для `window.external`, чтобы он указывал на `window.AndroidBridge`.
    - [x] **Шаг 8:** В `onPageFinished` добавлена инъекция `jsFix` для всех основных фреймов, чтобы гарантировать наличие необходимых заглушек и объектов до выполнения игровых скриптов.

- [ ] **Проблема:** При навигации по комнатам, где в ответе сервера есть тег `<script>`, верхний фрейм не отображает HTML-контент.
  - **Симптомы:** После клика на кнопку навигации, в логах видно, что `shouldInterceptRequest` перехватывает ответ, содержащий HTML и JavaScript, но `WebView` не отображает этот контент.
  - **Гипотеза:** Проблема может быть связана с тем, как `WebView` обрабатывает ответы, содержащие и HTML, и JavaScript. Возможно, `loadDataWithBaseURL` или другой метод загрузки контента в `WebView` не выполняет JavaScript в контексте загружаемой страницы.
  - **План решения:**
    - [ ] **Шаг 1:** Проанализировать, как именно `shouldInterceptRequest` возвращает данные в `WebView`. Убедиться, что используется правильный `mime-type` и кодировка.
    - [ ] **Шаг 2:** Попробовать заменить `loadUrl` на `loadDataWithBaseURL` в `WebAppInterface.loadFrame`, чтобы явно указать `baseUrl` и `mimeType`.
    - [ ] **Шаг 3:** Исследовать возможность выполнения JavaScript из ответа сервера в `onPageFinished` после загрузки основного HTML-контента.

- [ ] **Проблема:** После последних правок автобой не выполняет удары, а при остановке автобоя верхний фрейм остается белым.
  - **Симптомы:**
    - Кнопка/цикл автобоя активен, но реальный удар по противнику не отправляется.
    - После остановки автобоя верхний фрейм (main_top) показывает белый экран.
  - **Гипотезы:**
    1. Ошибка в ветках MainPhp.mainPhpFight() (возврат null/некорректный HTML в критическом состоянии).
    2. Некорректный цикл AutoSubmit/FightViewModel/WebAppInterface.AutoTurn.
    3. Проблема с перехватом/пробросом HTML через WebViewRequestInterceptor и postfilter.
  - **План решения:**
    - [ ] **Шаг 1:** Снять целевые логи `adb logcat` с фильтрами по MainPhp, LezFight, WebAppInterface, WebViewInterceptor, chromium.
    - [ ] **Шаг 2:** Сопоставить лог-цепочку с C# эталоном (MainPhpFight.cs, FightJs.cs, FormMainAutoBoi.cs).
    - [ ] **Шаг 3:** Внести точечный фикс в `app` без изменений в ABClient.
    - [ ] **Шаг 4:** Проверить компиляцию и повторно подтвердить логами.
## Отладка AutoBoi/LezFight (2026-02-26)

### Что сделано
- [x] Проанализирован `Logs/logcat.txt` по ключевым маркерам (`MainPhp`, `LezFight`, `WebViewInterceptor`, `JS_CONSOLE`).
- [x] Найдена и исправлена JS-строка в `app/src/main/java/ru/neverlands/abclient/postfilter/FightJs.java`:
  - было некорректное экранирование `document.forms[''FEND''].submit()`;
  - исправлено на корректное экранирование в Java-строке (`document.forms[\\\'FEND\\\'].submit()`).
- [x] Проверена сборка после правки: `./gradlew :app:compileDebugJavaWithJavac` завершилась успешно.
- [x] По логам подтверждено, что запрос отправляется (`main.php?post_id=7...`), но серверная сторона или UI еще требуют проверки HP.
- [x] Сопоставлен Android `LezFight.BuildFrame()` с C# эталоном `ABClient/Lez/LezFight.cs`.
- [x] Добавлена поддержка параметра `inf_zb` в `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java` (аналог C# `fightpm[10]`).
- [x] Сборка после добавления `inf_zb` прошла успешно.

### Что еще нужно проверить
- [ ] Проверить на устройстве фактическую отправку удара и изменение HP после добавления `inf_zb`.
- [ ] Снять новый `Logs/logcat.txt` после сценария: начать бой > 2-3 раунда > остановить автобой.
- [ ] Проверить отсутствие новых ошибок `JS_CONSOLE` (`SyntaxError`, `ReferenceError`) в боевом фрейме.
- [ ] Если удар все еще не проходит, сравнить с C# формированием всех параметров (`inu/inb/ina` массивы, позиции и модификаторы).

- [ ] **Проблема IBClient2:** На природе верхний фрейм падает в `javascript:clickRefresh()` с сообщением `Переход на веб-страницу отменен`, при этом в городе верхний фрейм работает.
  - **Симптомы:** Ошибка появляется именно на JavaScript refresh/navigation верхнего фрейма после уже выполненного license/feature patch. Wrapper-патч `Cancel=false` не изменил пользовательский симптом.
  - **Гипотеза:** Нужно отделить license-state patch от дополнительных side effects: широкого `xabgjOJtDg=-1`, global cancel-wrapper patch и отдельных веток `LC7guda13N`/`Dvag1nmnel`. `FeatureFlags=0` не включает plugin gates через `lLFgWeDiUF(...)`, но сами `LC7guda13N=True` и `Dvag1nmnel=True` могут менять HTML/JS обработку верхнего фрейма.
  - **План решения:**
    - [x] Найти текущую точку decision point: `kpyHm0ZTH0nEvVYK9jd.JVpZDkQMFy(...)`.
    - [x] Проверить, что raw runtime IL метода не лежит verbatim в original exe.
    - [x] Проверить runtime/devirtualized route: `JVpZDkQMFy` raw IL не лежит verbatim в original exe, а dnlib-output ломает protector init из-за metadata layout.
    - [x] Проверить wrapper-патч `DSoBfBSP0AvdomJc065M.VX2SSB6FTJv(...)`: пользователь подтвердил, что симптом не ушёл, поэтому wrapper не считать финальным решением.
    - [x] Добавить параметры patch script: `-FeatureFlags` и диагностический `-PatchCancelWrapper`.
    - [x] Перевести default на `FeatureFlags=0`, `PatchCancelWrapper=False`.
    - [x] Регенерировать `IBClient2_patched.exe` и проверить reflection-поля: `LC=True`, `Dv=True`, `Flags=0`.
    - [x] Smoke-запуск: процесс `IBClient2_patched.exe` жив через 6 секунд, immediate crash нет.
    - [x] Добавить диагностические параметры `-DisableLcFlag` и `-DisableDvFlag` в существующий `New-LicenseTypeInitializerIl` без отдельного hex-обхода.
    - [x] Статически проверить LC/Dv decision points: `ScriptManager.ShouldHideImages()` и `QD1SfMcOLd0OeVU5Lgm.Process(...)` меняют HTML/JS/content path при `LC7guda13N=True`; `Dvag1nmnel` в основном включает complect/drink/timer/UI flows.
    - [x] Проверить, что `QD1SfMcOLd0OeVU5Lgm.Process(...)` в original exe является VM-stub, а runtime/table IL `0x06000970_hit1_0x25728773B9C.ilbin` не лежит verbatim в original exe (`hits=0`), поэтому прямой IL-патч этой ветки не использовать как быстрый фикс.
    - [x] Собрать `IBClient2_patched_lc_only.exe`: `LC=True`, `Dv=False`, `FeatureFlags=0`, `PatchCancelWrapper=False`, SHA256 `1C0BC7B629593B37C890ED99679E10DFCCBC1B16465B02F470B018F535169CD0`, smoke 6 секунд OK.
    - [x] Собрать `IBClient2_patched_dv_only.exe`: `LC=False`, `Dv=True`, `FeatureFlags=0`, `PatchCancelWrapper=False`, SHA256 `403561077301C6411E340D6B9856ABEA0996AA1A874B3186DD4974DD182479A8`, smoke 6 секунд OK.
    - [x] Проверить оба variants через отдельные reflection/JIT процессы, чтобы не смешивать одинаковую assembly identity.
    - [x] Добавить диагностические параметры дат `-LicenseSlot1ExpiresAt`, `-LicenseSlot2ExpiresAt`, `-LicenseSlot3ExpiresAt` для раздельной проверки `uL3gFdhZHW`/`pyEgmOUHDj`/`wTVg4RtDZs` без отключения `LC`/`Dv`.
    - [x] Собрать slot-variants с `LC=True`, `Dv=True`, `FeatureFlags=0`, `PatchCancelWrapper=False`, reflection и smoke 6 секунд OK:
      - `IBClient2_patched_slot1_expired.exe`: `uL3gFdhZHW=2000-01-01`, SHA256 `E089812C6BA3E10F76CB6D66F38D5313EEB2998F6B7A16524EF5578FA201BACF`.
      - `IBClient2_patched_slot2_expired.exe`: `pyEgmOUHDj=2000-01-01`, SHA256 `65855D3DEF1D62AE329DE468D2277C42A112F82A6E2F1CB60E851A4808AD47F2`.
      - `IBClient2_patched_slot3_expired.exe`: `wTVg4RtDZs=2000-01-01`, SHA256 `C613C630E8949DB8618FB38BF7694320B3DFE442D3C1CBE860F27A57FB043871`.
    - [x] Регенерировать основной `IBClient2_patched.exe` текущим patcher: `LC=True`, `Dv=True`, все slots `2027-05-09 23:59:59 +03:00`, SHA256 `EFFF8EA99472615495559C24DDBF5F9D4B54C92038B2C05329BC5DAD49344B8C`, smoke 6 секунд OK.
    - [x] Добавить точечный diagnostic switch `-ForceShouldHideImagesFalse`: он сохраняет `LC=True`, `Dv=True`, `FeatureFlags=0`, но меняет opcode `ScriptManager.ShouldHideImages()` на `false` (`0x1214A: 0x17 -> 0x16`).
    - [x] Собрать `IBClient2_patched_no_hide_images.exe`: `LC=True`, `Dv=True`, `FeatureFlags=0`, `ShouldHideImagesIL=00-00-16-2A`, SHA256 `7FC5EEA7AB4C6986785A523A6207C3C3DC648E77AF81CA6054F88A4BC4077A04`, smoke 6 секунд OK.
    - [ ] Проверить вручную в игре сценарий природы: original/current patched/no-hide-images/lc-only/dv-only/slot1/slot2/slot3, верхний фрейм и `javascript:clickRefresh()`.

### Команды для проверки боя
- `adb logcat -c`
- `adb logcat -v time --pid $(adb shell pidof ru.neverlands.abclient) > Logs/logcat_runtime.txt`
- `rg -n "MainPhp|LezFight|WebViewInterceptor|JS_CONSOLE|post_id=7|Autoboi" Logs/logcat_runtime.txt -S`

- [x] **Проблема:** При обновлении названия клетки карты подпись иногда применялась к предыдущей клетке (цепочка `normalizeCellLabel` / `syncCellLabelFromServer` / `syncCellNameFromRoomHtml`).
  - **Симптомы:** В движении (`AutoMoving`) `CellDivText` мог показывать имя не той клетки, т.к. рендер берёт приоритетно `cell.Tooltip`, а sync работал с отложенным именем без жёсткой привязки к `regNum`.
  - **Гипотеза:** Deferred-синхронизация в `RoomManager` сохраняла только имя (`pendingRoomLocationName`) и могла применяться к любому подтверждённому `regNum` в `onMapLocationConfirmed(...)`; дополнительно fallback на `currentReg` при `AutoMoving` повышал риск записи в «предыдущую» клетку.
  - **План решения:**
    - [x] Привязать deferred-значение имени к целевому `regNum`.
    - [x] Применять deferred-обновление только при совпадении `confirmedReg == pendingTargetReg`.
    - [x] Убрать fallback на `currentReg` в `resolveCellRegNumForRoomName(...)`, когда `AutoMoving=true` и нет надёжного совпадения.
    - [x] Проверить сборку `:app:compileDebugJavaWithJavac`.

- [x] **Проблема app2:** Во время `Авто-Лесоруба` cleanup после набора массы спамит переходом в inventory до истечения `NeverTimer`.
  - **Симптомы по логам `logs/Critical/20260511_10_50_*`:**
    - `10:55:03` после `alchemy_act3` включается cleanup: `cleanup requested`, затем `cleanup redirect to inventory`.
    - Серверный `NeverTimer` активен примерно на 383 секунды: `globalDueInMs=382864`.
    - Несмотря на это, каждые ~2 секунды повторяется `shouldOverrideUrlLoading ... go=inv`, а `SERVER_TIMER_TICK` пишет `autoCutRetrySource=cleanup_inventory:inventory_without_rows`.
  - **Причина:** существующий контур `AutoCutManager.deferCleanupInventoryUntilServerTimer(...)` планировал cleanup retry через bounded fallback `1500 ms`, затем `MainActivity.checkServerTimerDrivenActions()` выпускал retry до истечения общего `NeverTimer`; после этого `AutoCutHandler.processCleanupOpenInventory(...)` снова открывал inventory.
  - **План решения:**
    - [x] Найти текущий decision point: `AutoCutHandler.processCleanupOpenInventory(...)` -> `AutoCutManager.deferCleanupInventoryUntilServerTimer(...)` -> `MainActivity.checkServerTimerDrivenActions()`.
    - [x] Изменить `deferCleanupInventoryUntilServerTimer(...)`, чтобы cleanup inventory ждал реальный `AppVars.NeverTimer`, а не bounded retry.
    - [x] Проверить сборку `app2` и отсутствие mojibake/прямого `android.util.Log` в изменённых прикладных файлах.

- [ ] **Проблема ANClient:** В новой вкладке `Постройки` раздел `Здания` показывает fallback `API форпоста недоступен`, хотя ожидается ответ `getcity.cgi?city1`.
  - **Симптомы:** Пользователь видит сообщение `API форпоста недоступен. Нажмите обновить; при HTTP 535 клиент пробует скрытый WebBrowser с текущими cookie.`.
  - **Гипотезы:**
    1. Запрос `getcity.cgi?city1` отправляется слишком рано после `cityhall_*.txt`, нужен короткий delay 500 ms.
    2. Прямой `HttpWebRequest` получает `HTTP 535`, а fallback через скрытый `WebBrowser` не читает тело из-за тайминга/ошибки документа.
    3. Недостаточно cookie/header diagnostics для понимания, какой ответ реально возвращает сервер.
  - **План решения:**
    - [x] Изучить `ANClient/bin/Debug/Logs` по маркерам `ForpostInfoController`, `GETCITY`, `HTTP 535`, `WebBrowser`.
    - [x] Проверить текущую цепочку загрузки в `Info/ForpostInfoController.cs`.
    - [x] Внести минимальный фикс в существующий контур запроса/fallback, без отдельного параллельного загрузчика.
    - [x] Добавить файловую диагностику через `AppLog` для каждого этапа загрузки зданий.
    - [-] Проверить сборку/доступные статические проверки и mojibake/BOM: `dotnet msbuild ANClient10.sln` заблокирован отсутствующим targeting pack `.NETFramework,Version=v2.0`; mojibake/BOM проверяется отдельно.
  - **Результат 2026-05-14:**
    - [x] В `ForpostInfoController.LoadAllDataAsync()` добавлена задержка `500 ms` перед `getcity.cgi?city1` после загрузки `cityhall_*.txt`.
    - [x] `HTTP 535` и `HTTP 536` теперь считаются fallback-case и запускают существующий скрытый `WebBrowser`.
    - [x] Добавлены `AppLog`-маркеры `GETCITY_DELAY_BEFORE_REQUEST`, `GETCITY_DIRECT_START`, `FETCH_START`, `FETCH_OK`, `FETCH_HTTP_FAILED`, `GETCITY_WEBBROWSER_START`, `GETCITY_WEBBROWSER_DOCUMENT_COMPLETED`, `GETCITY_WEBBROWSER_BODY`, `GETCITY_WEBBROWSER_PARSED`.
    - [x] Проверены `ForpostInfoController.cs`, `todo_DebugApp.md`, `todo_task_20260513_tables_recipes.md`: BOM отсутствует, mojibake-паттерны не найдены.
    - [-] Полная сборка недоступна в текущем окружении: `dotnet msbuild ANClient10.sln` падает на отсутствующем `.NETFramework,Version=v2.0` targeting pack.
  - **Результат 2026-05-14 после повторных логов:**
    - [x] По `20260514_08_30_forpostinfocontroller.log` подтверждено: прямой `getcity.cgi?city1` получает `HTTP 535/536`, скрытый `WebBrowser` стартует, но не получает `DocumentCompleted` и уходит в `GETCITY_WEBBROWSER_TIMEOUT`.
    - [x] По `20260514_08_30_proxysession.log` подтверждено: запрос скрытого `WebBrowser` доходит до локального proxy как `neverlands.ru`, но общий proxy-контур затирал переданный `Cookie` и подставлял только host-cookie из `CookiesManager.Obtain(Host)`.
    - [x] В `ANProxy/Session.cs` добавлено исключение для `neverlands.ru/modules/api/getcity.cgi`: proxy сохраняет переданный браузерный `Cookie` заголовок и логирует `preserve request Cookie for getcity` без значений cookie.
    - [x] Проверены `ForpostInfoController.cs`, `Session.cs`, `todo_DebugApp.md`, `todo_task_20260513_tables_recipes.md`: BOM отсутствует, mojibake-паттерны не найдены.
    - [-] Повторная сборка `dotnet msbuild ANClient10.sln` всё ещё заблокирована отсутствующим `.NETFramework,Version=v2.0` targeting pack.
    - [x] По рабочей `/app2` реализации сверено: прямой запрос использует `OkHttp` + объединенные cookie, fallback использует встроенный `WebView.loadUrl(url)`; в `ANClient` аналогичный путь должен идти через локальный proxy и скрытый `WebBrowser`, поэтому критично не затирать переданный `Cookie`.
    - [x] Найдена рабочая команда сборки ANClient через VS BuildTools и выполнена успешно: `Stop-Process -Name ANClient -Force -ErrorAction SilentlyContinue; & "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe" ANClient.csproj /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir=bin\Debug\`.
  - **Результат 2026-05-14 после логов `08_50`:**
    - [x] Подтверждено, что `ForpostInfoController` собирал `Cookie` для hidden `WebBrowser` (`cookieLength=367`), но в `ProxySession` не было маркера `preserve request Cookie for getcity`, то есть WinForms `WebBrowser.Navigate(..., headers)` не передал `Cookie` через локальный proxy.
    - [x] В `ANProxy/Session.cs` getcity-ветка изменена по образцу `/app2`: proxy сам объединяет cookie из `CookiesManager.Obtain("neverlands.ru")`, `CookiesManager.Obtain("www.neverlands.ru")` и пришедшего request-cookie, затем ставит единый `Cookie` перед отправкой на сервер.
    - [x] Добавлены proxy-маркеры `getcity Cookie merged` и `getcity response` для проверки фактического cookie length и HTTP-кода без логирования значений cookie.
    - [x] ANClient пересобран рабочей командой VS BuildTools, `CoreCompile` выполнен, `bin\Debug\ANClient.exe` обновлен.
  - **Проверка 2026-05-14 по замечанию про браузер:**
    - [x] Прямой `HttpWebRequest` без proxy/cookie с браузерным `User-Agent` из текущей машины получает `HTTP 535`.
    - [x] `curl` без cookie и с browser-like headers тоже получает `HTTP 535` и `Set-Cookie: watermark=...`.
    - [x] Следовательно, браузерный успех не означает полное отсутствие cookie/state: обычный браузер, вероятно, уже имеет нужное состояние, а ANClient должен брать его из текущей игровой cookie-сессии или проксировать запрос через игровой контур.
    - [ ] Проверить вручную в ANClient, что вкладка `Постройки -> Здания` получает здания после fallback/прямого запроса.
  - **Результат 2026-05-14 по частым retry `getcity`:**
    - [x] По `20260514_09_10_forpostinfocontroller.log` подтверждено: после раннего `535` запросы с cookieLength=369 несколько раз возвращали `200/buildings=6`, а затем частые повторные обновления начали получать `536`.
    - [x] В существующем `ForpostInfoController` добавлен линейный backoff: базовые `500 ms` + `500 ms` за каждый последовательный сбой `getcity`, максимум `5000 ms`.
    - [x] Добавлена защита последней успешной загрузки: `535/536`, пустой ответ, timeout или parse-fail скрытого `WebBrowser` больше не затирают уже полученные `_forpostData.Buildings`.
    - [x] Если здания уже есть, повторный `535/536` не запускает новый скрытый `WebBrowser`, чтобы не плодить зависающие fallback-запросы и не усиливать rate-limit.
    - [ ] Проверить вручную, что после первого успешного `buildings=6` UI вкладки `Здания` больше не возвращается к fallback-сообщению при последующих `536`.
  - **Результат 2026-05-14 по причине `535/536` и очереди proxy:**
    - [x] По замечанию пользователя причина уточнена: `getcity.cgi?city1` конфликтует не только с cookie, а с параллельными игровыми запросами других функций (контакты/чат/прочие фоновые контуры), из-за чего сервер отвечает `535/536` как на частые запросы.
    - [x] Из `ANProxy.Session` удалены специальные `getcity`-исключения: нет `preserve request Cookie`, нет `BuildGetcityCookieHeader`, нет WinINET/IE cookie-read, нет `getcity Cookie merged`/`getcity response` логов.
    - [x] Cookie-обработка proxy возвращена к ABClient-style: перед отправкой удаляется входящий `Cookie`, затем ставится только `CookiesManager.Obtain(Host)`.
    - [x] Добавлен отдельный `ANProxy.ProxyRequestQueue`: все динамические игровые запросы через локальный proxy получают слот старта с интервалом `1000 ms`; cache/static-ответы не задерживаются.
    - [ ] Проверить по новым логам наличие `ProxyRequestQueue: queued game request` рядом с контактами/`getcity` и отсутствие `535/536` при одновременных фоновых обновлениях.
  - **Результат 2026-05-14 по direct Neverlands-запросам:**
    - [x] Найден существующий контур proxy: `AppVars.LocalProxy` задается из профильного внешнего proxy, а `ProxyRequestQueue` уже сериализует трафик локального `ANProxy.Session`.
    - [x] Добавлен `ANProxy/DirectGameRequestGuard.cs`: для direct `HttpWebRequest`/`WebClient` на `neverlands.ru` он применяет `AppVars.LocalProxy`, блокирует запрос при включенном внешнем proxy и отсутствующем `LocalProxy`, логирует `DIRECT_GAME_REQUEST_BLOCKED` без cookie-значений и вызывает существующий `ProxyRequestQueue`.
    - [x] Guard подключен к активным direct игровым контурам: `ForpostInfoController.FetchText`, `AntiCaptchaManager.TryLoadCaptchaImageFromCodeAddress`, `FormMain.WaitForTurnAsync`, `FormMain.ScanMap`, `RoomManager.RoomAsync`, `NeverApi.GetInfo`, `Contact.FightLog`, `FormMain.CrossAuth`.
    - [x] `AntiCaptchaManager` больше не загружает captcha image прямым `HttpWebRequest` без proxy guard.
    - [x] ANClient пересобран рабочей командой VS BuildTools, ошибок и предупреждений нет.
    - [ ] Проверить вручную в логах `ProxyRequestQueue: queued game request` для direct контуров и отсутствие `DIRECT_GAME_REQUEST_BLOCKED` при нормальной proxy-конфигурации.
  - **Результат 2026-05-14 по браузерному HAR `Getcity.har`:**
    - [x] HAR показывает успешный top-level document request `http://neverlands.ru/modules/api/getcity.cgi?city1` без request cookies и без `Referer`; ответ `200 OK`, `Content-Type: text/plain; charset=windows-1251`, тело содержит 6 зданий.
    - [x] Браузерный профиль запроса из HAR: Chrome-style `User-Agent`, полный document `Accept`, `Accept-Language: ru-RU...`, `Cache-Control/Pragma: no-cache`, `DNT: 1`, `Upgrade-Insecure-Requests: 1`, `Accept-Encoding: gzip, deflate`.
    - [x] В существующем `ForpostInfoController.FetchText()` для `ForpostCityApiUrl` применен HAR-профиль: обновлен browser UA, включен document `Accept`, no-cache/DNT/upgrade headers, gzip/deflate decompression, `harProfile=true` в логе.
    - [x] Для `getcity.cgi?city1` убрана принудительная подстановка `Cookie` и `Referer`; для остальных `FetchText`-запросов прежняя best-effort cookie/referer ветка сохранена.
    - [x] Hidden WebBrowser fallback больше не передает принудительные `Cookie`/`Referer` headers для `getcity`, чтобы не отличаться от HAR-запроса; proxy safety остается через локальный proxy/WinINET.
    - [x] ANClient пересобран рабочей командой VS BuildTools, ошибок и предупреждений нет.
    - [ ] Проверить вручную вкладку `Постройки -> Здания`: в `forpostinfocontroller.log` ожидаются `FETCH_START ... harProfile=True, cookies=False, cookieLength=0` и `GETCITY_DIRECT_PARSED: buildings=6` без fallback.
  - **Результат 2026-05-14 по сужению `ProxyRequestQueue`:**
    - [x] По `20260514_13_00_proxyrequestqueue.log` подтверждено, что широкий `ShouldQueue()` создавал backlog из read-only/misc запросов: `getid.cgi`, `info.cgi`, `pinfo.cgi`, `ch/*.html`, `ch.php?0...`, `game.php`, из-за чего `getcity.cgi?city1` ждал до `14220 ms` еще до `FETCH_START`.
    - [x] Найдена дополнительная причина задержки direct-запросов: при `AppVars.LocalProxy` они попадали в очередь дважды, сначала в `DirectGameRequestGuard`, затем повторно в `ANProxy.Session` локального proxy.
    - [x] `ProxyRequestQueue.ShouldQueue()` сужен до конфликтных динамических действий: `main.php`, `gameplay/ajax/*`, `ch.php` room/chat dynamic и неизвестные `.php/.cgi/.fcg`; read-only lookup/static больше не резервируют слот.
    - [x] Для `getcity.cgi?city1`, `getid.cgi`, `info.cgi`, `pinfo.cgi`, `pbots.cgi`, `logs.fcg`, `.txt` и read-only chat frames добавлен bypass очереди с throttled diagnostic `skipped game request queue: reason=...`.
    - [x] `DirectGameRequestGuard` больше не вызывает `ProxyRequestQueue.WaitTurn()` для запросов, которые уже направлены через `AppVars.LocalProxy`; их один раз сериализует `ANProxy.Session`.
    - [x] В логах очереди `vcode` маскируется как `<redacted>`, чтобы diagnostic URL не раскрывал код защиты.
    - [x] ANClient пересобран рабочей командой VS BuildTools, ошибок и предупреждений нет.
    - [ ] Проверить вручную новый запуск: `getcity.cgi?city1` больше не должен иметь `queued game action`/многосекундный `waitMs`; в логах допустим `skipped game request queue: reason=safe_lookup`.

- [x] **Проблема:** Anti-Captcha в бою не завершает ввод каптчи после правок `currentFinishUrl(retryFinishUrl, retryChallengeKey, currentChallengeKey)`.
  - **Симптомы:** В бою около `2026-05-19 19:53:50` каптча висит, Anti-Captcha не завершает бой; раньше срабатывало только со второго раза и тратило кредиты дважды.
  - **Гипотезы:**
    1. [-] В retry-контуре подставляется не тот `finishUrl` или challenge key: первичная причина не подтвердилась.
    2. [x] Автоматическая отправка сверена с ручным запросом из `BoiBot_captcha.har`: метод и URL совпадают (`GET main.php?code=...&get_id=61&act=7...`).
    3. [x] Ответ Anti-Captcha получен и submit отправлен, но успешный finish-html повторно распознан как новая каптча из `fexp[4]`.
  - **План диагностики:**
    - [x] Найти в `logs/` цепочку около `19:53:50` по маркерам `captcha`, `AntiCaptcha`, `finishUrl`, `currentFinishUrl`, `challenge`.
    - [x] Сопоставить запросы с `BoiBot_captcha.har` и определить эталонный URL/method/body для ручного ввода.
    - [x] Найти единственный current decision point в Android-коде и не добавлять параллельный retry-контур.
    - [x] Исправить распознавание каптчи в существующем `FightAuto.extractCaptchaUrlFromFexp(...)`: `fexp[4]` считается каптчей только при `fexp[6] == 0`, как в `LezFight.ParseNonFight()`.
    - [x] Проверить сборку `:app:compileDebugJavaWithJavac` и grep на `AppVars.VCode`, прямой `android.util.Log`, mojibake.
  - **Результат 2026-05-19:**
    - [x] По `20260519_19_50_mainactivity.log` подтверждено: Anti-Captcha вернул код, submit ушел на `http://neverlands.ru/main.php?code=41821&get_id=61&act=7&fexp=8&fres=1&vcode=d77b204a...`.
    - [x] По `BoiBot_captcha.har` подтвержден эталон: ручной ввод использует `GET http://neverlands.ru/main.php?code=38690&get_id=61&act=7&fexp=2451&fres=1&vcode=...`, после чего сервер возвращает карту.
    - [x] По `20260519_19_50_fightauto.log` причина зависания: после успешного submit был опубликован результат боя, но `resolveFightCaptchaUrl()` взял новый token из `fexp[4]` при флаге `fexp[6]=30` и снова открыл popup.
    - [x] В `FightAuto.extractCaptchaUrlFromFexp(...)` добавлена проверка `fexp[6] == 0`; это переиспользует существующий контур `CAPTCHA_REQUIRED` и не создаёт новый retry/submit-flow.
    - [x] `./gradlew.bat --no-daemon :app:compileDebugJavaWithJavac` завершился успешно; новые `AppVars.VCode`/прямой `android.util.Log`/mojibake в изменённых файлах не найдены.

- [x] **Проблема ANClient:** `Авто-Травник` после нескольких спилов иногда зависает на фрейме `Инвентарь` и не идёт дальше по клеткам.
  - **Симптомы по логам `ANClient/bin/Debug/Logs/Critical/20260611_10_40_*`, `20260611_10_50_*`, `20260611_11_00_*`:** после `cleanup requested` выполняется `cleanup inventory redirect`, затем `cleanup completed, source=inventory_pass`, но маршрут дальше не строится; следующий `ReloadMainPhpInvoke` обрабатывает HTML инвентаря (`inventory page detected`) без `map data found in HTML`.
  - **Причина:** `AutoCutRuntime.OnCleanupCompleted()` видел старый pending `lookRetry` от `cleanup_wait:before_auto_drink` и выходил по ветке `keep current cell for pending retry`; этот retry был нужен только для ожидания `NeverTimer` перед заходом в инвентарь, но после `inventory_pass` уже устаревал и блокировал `RouteNextCell(...)`.
  - **Решение 2026-06-11:** в существующем `AutoCutRuntime.OnCleanupCompleted()` cleanup-wait retry очищается как отработанный (`cleanup completed: clear consumed cleanup wait retry`), после чего штатно вызывается `RouteBackToTimerReturnIfNeeded(...)` или `RouteNextCell("cleanup_completed:inventory_pass")`.
  - **Проверить вручную:** после cleanup в логах ожидается `cleanup completed: clear consumed cleanup wait retry` и затем `route next: destination=...`; фрейм не должен оставаться на `Инвентарь` без новых `alchemy_ajax.php?act=1`.

- [x] **Проблема ANClient:** после `Эликсира Восстановления` автоспил мог продолжить `Оглядеться` из неверного контекста вместо возврата к `Флора`.
  - **Симптомы:** после `Используем Эликсир Восстановления...` следующий auto-look мог идти до возврата в `Флора`, из-за чего общий контур `Авто-Травник`/`Авто-Лесоруб` терял карту/ресурсный контекст.
  - **Причина:** ветка восстановления HP/MA в `MainPhpDrinkHpMa` отправляла elixir-action, но не выставляла тот же флаг возврата, который уже используется для `Эликсира Блаженства`.
  - **Решение 2026-06-11:** в существующем контуре `MainPhpDrinkHpMa` после успешной отправки recovery elixir выставляется `AppVars.SwitchToFlora = true`; добавлены маркеры `recovery elixir submitted, return to flora scheduled` и `recovery elixir submitted: return to flora before auto look`.
  - **Проверить вручную:** после recovery/bliss elixir в логах должен быть возврат к `Флора`, затем `auto cut pre-processing`, `map data found in HTML` и следующий `alchemy_ajax.php?act=1`.
