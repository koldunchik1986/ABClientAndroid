# Задача: Авто-Травник

## Цель

- [x] Найти существующий контур `AUTO_CUT` в `app2/ANClient`.
- [x] Сверить протокол `alchemy_ajax.php` по HAR с live-логикой `map.js`.
- [x] Сверить эталон ПК-версии `ABClient` без изменения файлов `ABClient/`.
- [x] Поставить иконку `Авто-Травник`: `http://image.neverlands.ru/achievement/20/a_20_3.gif`.
- [x] Реализовать базовые настройки клеток поиска, словарь трав, группы и чекбоксы выбранных трав.
- [x] Реализовать базовый runtime оглядывания и среза трав в существующем `AUTO_CUT` контуре.
- [x] Интегрировать captcha-срез с существующим popup/Anti-Captcha как fallback+auto-solve.
- [x] Проверить текущую сборку, UTF-8 без BOM, отсутствие mojibake и прямого `android.util.Log` для измененных файлов.

## Источники анализа

- `Ogl_srez_kartofel.har`: подтвержден реальный протокол `Оглядеться` и `Срезать`.
- `ogl.har`: пример `Оглядеться`, где часть трав недоступна для среза и `vcode` пустой.
- `Травник.html`: справочник по инструментам, сменам трав, умению, группам и времени роста.
- `ABClient/ABForms/FormMainHerbs.cs`: логика `HerbsList`, `TraceCut`, расчет смен и таймеров.
- `ABClient/ABForms/FormSettingsAutoCut.cs`: сохранение выбранных трав и флага вывода результата в чат.
- `ABClient/ScriptManager.cs`: JS-bridge имена `HerbsList`, `IsHerbAutoCut`, `HerbCut`, `DoHerbAutoCut`, `TraceCut`.
- `app2/src/main/assets/js/map.js`: актуальная Android JS-точка `Ogl(...)`, `ResoStart(...)`, `HerbsList(...)`, `TraceCut(...)`.
- `app2/src/main/java/ru/neverlands/anclient/bridge/WebAppInterface.java`: текущие Android bridge-заглушки `HerbsList` и `TraceCut`.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/Filter.java`: сейчас нет маршрута для `alchemy_ajax.php`.
- `app2/src/main/java/ru/neverlands/anclient/postfilter/FishAjaxPhp.java`: ближайший рабочий образец для ajax captcha, dedup и no-captcha fallback.

## Протокол Neverlands

`Оглядеться`:

```text
GET http://neverlands.ru/gameplay/ajax/alchemy_ajax.php?act=1&vcode=<lookVCode>&r=<random>
```

Ответ `act=1` начинается с `RESO@` и содержит:

```text
RESO@[messages]@[...]@[buttons]@[timer]@[resourceState]
```

Для трав `resourceState` имеет вид:

```text
[0, captchaToken, r_x, r_y,
 [res_id, res_name, l_time, r_time, uid, curs, mass, p, availableCount, cutVCode, r_type, totalCount], ...]
```

Срез травы:

```text
GET http://neverlands.ru/gameplay/ajax/alchemy_ajax.php?act=3&res_id=<id>&r_x=<x>&r_y=<y>&r_time=<r_time>&r_type=<r_type>&uid=<uid>&curs=<curs>&mass=<mass>&p=<p>&l_time=<l_time>&vcode=<cutVCode>&code=<captcha>&r=<random>
```

Важные выводы:

- `act=1` использует `lookVCode` из кнопки `Оглядеться` на карте.
- `act=3` использует не общий vcode страницы, а `cutVCode` из конкретной записи травы `ingr[i][9]`.
- Траву можно срезать только если `availableCount > 0` и `cutVCode` не пустой.
- Captcha image строится из `captchaToken`: `http://neverlands.ru/modules/code/code.php?<captchaToken>`.
- `Оглядеться` само по себе не должно запускать Anti-Captcha; captcha решается только если найденная трава выбрана пользователем и будет отправлен `act=3`.

## Текущее состояние app2

- `QuickActionType.AUTO_CUT("Авто-Травник", "auto_cut")` уже существует.
- `AutoFunctionsManager.isAutoCutEnabled/toggleAutoCut/setAutoCutEnabled` уже хранит флаг и выключает конфликтующие авто-функции.
- `AppTimerManager` уже сохраняет и временно отключает `AutoCut` при таймерах.
- `BossAuto` уже сохраняет/ставит на паузу/восстанавливает `AutoCut` в сценарии Авто-Босса.
- `QuickButtonsPanel` умеет включать/выключать `AUTO_CUT`, показывает внешнюю иконку травника и long-press меню настроек.
- `FunctionListAdapter` уже показывает `AUTO_CUT`, но иконка сейчас `null`/`ic_add`.
- `MapJs.java` объявляет JS shim `window.external.DoHerbAutoCut()`, `HerbsList(...)`, `TraceCut(...)`, `TraceAutoCutRuntime(...)`.
- `MapJs.process(...)` подменяет server `js/map.js` на `app2/src/main/assets/js/map.js`, поэтому runtime AutoCut JS-хук работает через текущий postfilter-контур.
- `app2/src/main/assets/js/map.js` вызывает `AnTryAutoCutOgl(...)` из существующих `ButtonGen/ReAddBut`, имеет guard от повторов, delayed `Ogl(...)` и JS->bridge трассировку.
- `WebAppInterface.HerbsList(String list)`, `TraceCut(String herb)`, `HerbCut(String name)`, `DoHerbAutoCut()` и `TraceAutoCutRuntime(String payload)` связаны с `AutoCutManager` и файловым логом `AUTO_CUT_TRACE`.
- `Filter.process(...)` подключает обработчик `alchemy_ajax.php`, поэтому Java-side postfilter видит структурно ответы `act=1/act=3`.
- Long-press меню для `AUTO_CUT` открывает `Настройки авто-травника`, `Список трав`, `Серпы`, `Смены трав` или удаление кнопки.

## Инварианты реализации

- Дорабатывать существующий контур `AUTO_CUT`, не создавать параллельный новый флаг.
- Не изменять `ABClient/`; использовать только как read-only эталон.
- Все изменения для ANClient делать в `app2/` и `TODO2/`.
- Новые Java-логи писать через `AppLog`, без прямого `android.util.Log`.
- Для защищенных новых запросов не читать и не писать `AppVars.VCode`; использовать `SessionManager` только там, где действительно нужен общий vcode.
- Для `act=3` приоритет имеет `cutVCode` из `RESO@`, потому что сервер выдает код защиты на конкретную траву.
- Manual captcha popup должен оставаться fallback; Anti-Captcha только автоматически заполняет и отправляет уже открытый popup.
- Ручные HTML-клики и ручное `Оглядеться` должны иметь приоритет над фоновыми auto-запросами.
- При runtime-логике добавлять файловую диагностику через `AppLog` chain, например `AUTO_CUT_TRACE`.
- `auto_cut` не входит в public/limited bundle лицензии: доступ только через individual `full` grant или custom grant `auto_cut`.
- При истечении временного individual grant `LicenseRuntime` пересобирает public-only session, а `AutoFunctionsManager.disableUnavailableFeatures(...)` сбрасывает persisted `auto_cut`.

## План реализации

### Фаза 0: иконка функции

- [x] В `QuickButtonsPanel.getIconUrlForAction(AUTO_CUT)` вернуть `http://image.neverlands.ru/achievement/20/a_20_3.gif`.
- [x] В `FunctionListAdapter.getIconUrlForAction(AUTO_CUT)` вернуть тот же URL.
- [x] Оставить локальный fallback `R.drawable.ic_add`, если загрузка внешней иконки упала.

### Фаза 1: настройки Авто-Травника

- [x] Добавить long-press меню для `AUTO_CUT`: `Настройки авто-травника`, `Список трав`, `Серпы`, `Смены трав` и `Удалить кнопку`.
- [x] Добавить экран/диалог настроек с полем `Клетки для поиска`, CSV формата `xx-xxx, x-xxx`.
- [x] Добавить отдельный групповой список трав `Травы 1`..`Травы 11` и `Не определено`.
- [x] Добавить чекбоксы выбора трав для автосреза.
- [x] Добавить long-press на траву: редактировать `Умение`, `Время среза`, `Группа`.
- [x] Добавить editable смены трав: `00:50-06:50`, `06:50-12:50`, `12:50-18:50`, `18:50-00:50`.
- [x] Добавить флаг `Выводить в чат результат`, аналог `DoAutoCutWriteChat` из ПК-версии.

### Фаза 2: модель данных

- [x] Создать базовую модель `AutoCutHerb` с полями `id`, `name`, `skill`, `growthMinutes`, `group`, `selected`.
- [ ] Создать модель `AutoCutCell` с полями `regNum`, `herbsSnapshot`, `updatedAtServerMs`.
- [ ] Seed-словарь минимум:
- [x] `437` = `Петрушка кровавобережная`, группа `11`, рост `60`.
- [x] `442` = `Чеснок`, группа `11`, рост `60`.
- [x] `443` = `Картофель`, группа `11`, рост `60`.
- [x] `450` = `Томат`, группа `11`, рост `60`.
- [x] `451` = `Сахарный тростник`, группа `11`, рост `60`.
- [x] При `RESO@` с неизвестной травой автоматически добавлять ее в словарь с группой `Не определено`.
- [x] Persist хранить в SharedPreferences/JSON внутри `AutoFunctionsManager` или выделенного `AutoCutManager`.

### Фаза 3: bridge и JS-интеграция

- [x] Добавить `WebAppInterface.DoHerbAutoCut()` и вернуть `AutoFunctionsManager.isAutoCutEnabled()` с guard-условиями.
- [x] Добавить `WebAppInterface.IsHerbAutoCut(String herb)` для parity с `ABClient.ScriptManager`.
- [x] Расширить `WebAppInterface.HerbsList(String list)`: обновлять словарь трав из JS-снимка.
- [x] Расширить `WebAppInterface.TraceCut(String herb)`: сохранять trace для последующего подтверждения успешного среза.
- [x] Использовать существующий `alchemy_ajax.php` payload `RESO@`/`ingr[i]` в postfilter без второго `Ogl`-контура.
- [x] Не добавлять второй `Ogl`-контур рядом с существующим `ButtonGen/ReAddBut`; исправлять текущую ветку.

### Фаза 4: обработчик `alchemy_ajax.php`

- [x] Создать `AlchemyAjaxPhp` по образцу `FishAjaxPhp`.
- [x] Подключить `Filter.process(...)` для `http://neverlands.ru/gameplay/ajax/alchemy_ajax.php`.
- [x] В `act=1` распарсить `RESO@`, выбрать первую доступную выбранную траву.
- [x] Если captcha не требуется или сервер допустит пустой `code`, отправить `act=3` через JS `AjaxGet(...)` в текущем map-flow.
- [x] Если captcha требуется, открыть существующий popup через `AppVars.ACTION_SHOW_CAPTCHA` с `captchaUrl` и `finishUrl` для `act=3&code=????`.
- [x] Добавить dedup по ключу `captchaToken|res_id|cutVCode`, чтобы не показывать один popup несколько раз.
- [x] В `act=3` обрабатывать успех `Всё прошло успешно.`, ставить таймер роста и писать результат в чат.

### Фаза 5: captcha submit для травника

- [x] В `MainActivity.showCaptchaDialog(...)` добавить тип `isAlchemyCaptcha` для `alchemy_ajax.php?act=3`.
- [x] Заголовок popup: `Введите капчу для травника`.
- [x] Обобщить `submitFishCaptchaViaAjaxOrFallback(...)` в ajax-submit для `fish_ajax.php` и `alchemy_ajax.php`.
- [x] Для `alchemy_ajax.php` отправлять через `AjaxGet(...)`, а не через top-frame `loadUrl`, чтобы `RESO@` остался в игровом JS/postfilter flow.
- [x] Anti-Captcha использовать без отдельного нового solver: тот же `maybeStartAntiCaptchaForActiveChallenge(...)` после открытия popup.

### Фаза 6: движение по клеткам

- [x] Использовать существующий навигатор `AutoFunctionsManager.startAutoMoving(destination)`.
- [x] CSV клеток нормализовать как список `regNum`.
- [x] На текущей клетке сначала выполнять `Оглядеться`, затем срез, затем переход к следующей клетке.
- [x] Если на клетке нет выбранных доступных трав, пометить клетку проверенной на текущую смену и идти дальше.
- [x] Если `AppVars.AutoMoving` уже ведет к нужной клетке, не перезапускать маршрут.
- [x] Если включен `AutoTreasure` или другая конфликтующая навигация, выключать через существующие setter-ы `AutoFunctionsManager`.

### Фаза 7: серпы, масса и мусор

- [x] По аналогии с авто-рыбалкой проверять руки/инвентарь перед срезом.
- [x] Поддержать названия серпов:
- [x] `Серп Мастера-травника`.
- [x] `Серп собирателя`.
- [x] `Серп мастера-травника`.
- [x] `Серп эксперта-травника`.
- [x] `Серп Триады`.
- [x] Добавить UI-настройку разрешенных серпов для авто-надевания.
- [x] Если масса инвентаря выросла больше чем на `10%`, запускать cleanup/выброс мусора по отдельной настройке.
- [x] Не ломать ручную работу инвентаря: все действия через существующие guard-ветки и suppression windows.

### Фаза 8: лицензирование и документация кода

- [x] Добавить подробные Javadoc-комментарии к новым AutoCut runtime-переменным, DTO, handler-ам и bridge-методам.
- [x] В `app2` исключить `auto_cut` из public feature expansion рядом с `anti_captcha`.
- [x] В `app2` оставить `auto_cut` доступным через individual `full` или custom grant.
- [x] В `app2` добавить `Авто-Травник` в timer auto-function mapping с license filter.
- [x] В `app2` проверить, что `disableUnavailableFeatures(...)` отключает AutoCut при downgrade/expiry.
- [x] В `app3` исключить `auto_cut` из public `full`/custom public features.
- [x] В `app3` обновить описания full/custom grants и инструкцию выдачи `auto_cut`.

## Риски и проверки

- [x] Проверить, что `DoHerbAutoCut()` не вызывает бесконечный `Ogl(...)` при каждом redraw карты.
- [x] Проверить, что `act=3` использует именно `cutVCode`, а не stale `SessionManager` vcode.
- [x] Проверить, что при ручном `Оглядеться` без выбранной травы Anti-Captcha не стартует.
- [ ] Проверить, что manual captcha popup работает при выключенной Anti-Captcha или пустом API key.
- [ ] Проверить, что `alchemy_ajax.php` response остается в `AjaxGet` flow и карта обновляет таймер/кнопки.
- [ ] Проверить, что фоновый сервис запускается/останавливается корректно, если runtime Авто-Травника потребует background tick.
- [x] Проверить `./gradlew.bat --no-daemon :app2:assembleDebug`.
- [x] Проверить UTF-8 без BOM измененных `.java`, `.js`, `.md` файлов.
- [x] Проверить стандартные mojibake-паттерны без self-hit в тексте TODO.
- [x] Проверить отсутствие новых `import android.util.Log;` и `Log.d/i/w/e` в app2 прикладном коде.
- [ ] Device smoke после фикса возврата на карту: после проверки серпа должен появиться лог `return to map using parsed link`, затем загрузка карты, `AUTO_CUT_JS schedule/start` или диагностический `AUTO_CUT_JS skip no ogl button`, далее `DoHerbAutoCut` и `alchemy_ajax.php?act=1`.

## Проверки 2026-04-27

- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` — успешно.
- [x] BOM-проверка измененных файлов — OK.
- [x] Mojibake-проверка `TODO2` и измененных Java-файлов — совпадений нет.
- [x] Проверка измененных Java-файлов на прямой `android.util.Log`/`Log.*` — совпадений нет.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после runtime-слоя Авто-Травника — успешно.
- [x] BOM-проверка измененных Java/MD файлов после runtime-слоя — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` — совпадений нет.
- [x] Проверка `Log.*`: совпадения только в разрешенных `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фаз 6/7 — успешно.
- [x] BOM-проверка измененных Java/MD файлов после фаз 6/7 — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` после фаз 6/7 — совпадений нет.
- [x] Проверка `Log.*` после фаз 6/7: совпадения только в разрешенных `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после лицензирования/comment pass — успешно.
- [x] `./gradlew.bat --no-daemon :app3:classes` после лицензирования — успешно.
- [x] BOM-проверка измененных Java/MD файлов после лицензирования — OK.
- [x] Mojibake-проверка `app2/src/main/java`, `app3` и `TODO2` после лицензирования — совпадений нет.
- [x] Проверка `Log.*` после лицензирования: совпадения только в разрешенных `FileLogger.java`/`LogcatFileRecorder.java`.
- [ ] Device smoke: открыть long-press `AUTO_CUT`, сохранить настройки, проверить `Оглядеться`/captcha на live-карте.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса возврата Авто-Травника на карту — успешно.
- [x] BOM-проверка `AutoCutHandler.java` и этого task-файла после фикса возврата — OK.
- [x] Mojibake-проверка `AutoCutHandler.java` после фикса возврата — совпадений нет.
- [x] Проверка `AutoCutHandler.java` на прямой `android.util.Log`/`Log.*` после фикса возврата — совпадений нет.

## Проверки 2026-04-28

- [x] Проверено, что `MapJs.process(...)` берёт `app2/src/main/assets/js/map.js`, поэтому `AnTryAutoCutOgl(...)` находится в рабочем runtime-контуре server `js/map.js`.
- [x] Добавлена JS-трассировка `TraceAutoCutRuntime(...)`: в логах должен появляться `AUTO_CUT_JS schedule/start/cancel/skip ...`.
- [x] Добавлены UI-разделы `Список трав`, `Серпы`, `Смены трав` в long-press меню `AUTO_CUT` и основной dialog настроек.
- [x] Добавлена persisted настройка списка серпов через `AutoCutManager.getEnabledSickleNames()` и использование её в `AutoCutHandler`.
- [x] Добавлено persisted расписание смен трав через `AutoCutManager.getShiftScheduleText()/setShiftScheduleText(...)`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после UI/runtime доработок — успешно.
- [x] BOM-проверка измененных app2/TODO2 файлов после UI/runtime доработок — OK.
- [x] Mojibake-проверка `app2/src/main/java`, `app2/src/main/assets/js`, `TODO2` — совпадений нет.
- [x] Проверка прямого `Log.*`: совпадения только в разрешенных `AppLog.java`, `FileLogger.java`, `LogcatFileRecorder.java`.
- [x] Проверка нового AutoCut runtime на старые `ab_auto_cut`/`ab_cut`/`ABCLIENT`/`abclient`/`ru.neverlands.abclient` — совпадений нет.
- [x] По логам `20260428_01_20` выявлено: после `return to map using parsed link` карта и `assets/js/map.js` загружались, но `DoHerbAutoCut`/`AUTO_CUT_JS`/`alchemy_ajax.php?act=1` не появлялись, значит отказ был до AJAX `Оглядеться`.
- [x] В `MapJs.java` добавлен runtime-patch `ANCLIENT_MAP_RUNTIME_PATCH_AUTO_CUT`: wrapper `view_map()` после построения карты повторно ищет `mapbt['ogl']` и вызывает существующий `AnTryAutoCutOgl(...)` с тем же guard, без второго native HTTP-контура.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после runtime fallback `view_map()` — успешно.
- [x] BOM-проверка измененных `MapJs.java`, `app2/src/main/assets/js/map.js`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main/java`, `app2/src/main/assets/js`, `TODO2` — без mojibake; совпадения `????` относятся к ожидаемому captcha placeholder `code=????`.
- [x] Проверка прямого `Log.*`: совпадения только в разрешенных `AppLog.java`, `FileLogger.java`, `LogcatFileRecorder.java`.
- [x] Проверка `MapJs.java` на старые runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_*` — совпадений нет.
- [x] По логам `20260428_01_40` подтверждено: ручная попытка не формирует `alchemy_ajax.php?act=1`; вместо этого идут `main.php?get_id=56&act=10&go=inf/go=ret`, а AutoCut пишет `AUTO_CUT_JS skip no ogl button`.
- [x] Найден фикc-в-фиксе в `app2/src/main/assets/js/map.js`: `timerst(...)` после окончания таймера движения принудительно делал `location = ...go=inf`, чего нет в `map_orig.js`; из-за этого карта уходила с текущего `mapbt` и `Оглядеться` пропадало до клика.
- [x] Убран forced reload на `go=inf` из `timerst(...)`; вместо него добавлена файловая runtime-диагностика `TraceMapRuntime('timerst complete, stay on current map, lp=...')`.
- [x] Сверен `ogl.har`: ручной `Оглядеться` должен идти через `AjaxGet -> Look -> ButClick` в `alchemy_ajax.php?act=1&vcode=<lookVCode>&r=<random>`, без перехода на `main.php?...go=inf`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса `timerst(...)` — успешно.
- [x] BOM-проверка измененных app2/TODO2/app3 файлов после фикса `timerst(...)` — OK.
- [x] Mojibake-проверка `app2/src/main/java`, `app2/src/main/assets/js`, `TODO2`, `app3` — совпадений нет.
- [x] Проверка прямого `Log.*`: совпадения только в разрешенных `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] По логам `20260428_01_50` и `ogl.har` выявлено актуальное имя кнопки `Оглядеться`: сервер возвращает `mapbt[i][0] == "look"`, а рабочий runtime искал только legacy `ogl`.
- [x] Исправлен существующий JS-контур `ButtonGen/ReAddBut/ButClick` в `app2/src/main/assets/js/map.js`: теперь ручной `look` вызывает `Ogl(...)`, а Авто-Травник принимает `look` и legacy `ogl` без второго native HTTP-контура.
- [x] Исправлен runtime fallback `ANCLIENT_MAP_RUNTIME_PATCH_AUTO_CUT` в `MapJs.java`: `__anFindOglCode()` сначала ищет `look`, затем fallback `ogl`; диагностика теперь пишет `skip no look/ogl button`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса `look` — успешно; Kotlin daemon не подключился, но Gradle отработал fallback compile и завершил `BUILD SUCCESSFUL`.
- [x] BOM-проверка измененных `MapJs.java`, `app2/src/main/assets/js/map.js`, `TODO2/todo_task_20260427_auto_herbalist.md` после фикса `look` — OK.
- [x] Mojibake-проверка `app2/src/main` и `TODO2` после фикса `look` — совпадений нет.
- [x] Проверка прямого `Log.*` после фикса `look`: совпадения только в разрешенных `AppLog.java`, `FileLogger.java`, `LogcatFileRecorder.java`.
- [x] Проверка измененных `MapJs.java` и `assets/js/map.js` на старые runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_auto_cut`/`ab_cut` — совпадений нет.
- [x] Device smoke после фикса `look`: ручной клик `Оглядеться` дал `alchemy_ajax.php?act=1`, Авто-Травник дал `RESO@`/`HerbsList` и успешный `act=3`.
- [x] По логам `20260428_02_10` подтвержден успешный runtime-цикл: `act1: selected herb=Томат`, `HerbsList observed count=6`, `cut success: herb=Томат`, chat-report создан.
- [x] Проверено, что `AlchemyAjaxPhp.processAlchemyAct1(...)` перебирает все `state.resources` из `RESO@` и выбирает первую доступную выбранную траву, а не только первый элемент массива.
- [x] В `AlchemyAjaxPhp` добавлен snapshot всех трав клетки из `RESO@` (`buildCellResourcesSummary(...)`) и сохранение его в `PendingCut` до ответа `act=3`.
- [x] В `AutoCutManager.postCutResultToChat(...)` chat-report расширен до формата `Клетка '<regNum>' содержит: "Трава" available/total, ...` с server timestamp и source label.
- [ ] Проверить live-лог после обновления chat-report: сообщение должно содержать полный список трав клетки, например `Клетка '12-307' содержит: "Картофель" 2/2, "Томат" 1/2.`
