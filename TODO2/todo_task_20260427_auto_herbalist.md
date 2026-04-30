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
- [x] В `AutoCutManager` добавлен вывод массы в chat-report Авто-Травника по формату Авто-Рыбалки: `Масса: <b>current/max</b>` с delta `+mass` после среза.
- [x] В `AutoCutHandler` добавлена синхронизация `Масса Вашего инвентаря: current/max` из main.php/inventory HTML перед проверкой серпа/cleanup, чтобы AutoCut не зависел только от рыболовного `SetAutoFishMassa(...)`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после добавления snapshot-а клетки и массы — успешно.
- [x] BOM-проверка измененных `AutoCutManager.java`, `AutoCutHandler.java`, `AlchemyAjaxPhp.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main` и `TODO2` после добавления массы — совпадений нет.
- [x] Проверка прямого `Log.*` после добавления массы: совпадения только в разрешенных `FileLogger.java`/`LogcatFileRecorder.java`.
- [ ] Проверить live-лог после обновления chat-report: сообщение должно содержать полный список трав клетки, например `Клетка '12-307' содержит: "Картофель" 2/2, "Томат" 1/2.`
- [ ] Проверить live-лог после обновления массы: сообщение Авто-Травника должно содержать `Масса: <b>.../...</b> (+...)`.
- [x] По логам `20260428_02_50`/`03_00` выявлены регрессии runtime: wrong captcha не ставила повторный `Оглядеться`, AutoCut captcha считалась боевой и блокировала event-driven автобой, после одного среза клетка помечалась checked при наличии ещё выбранной доступной травы.
- [x] В `AlchemyAjaxPhp.processAlchemyAct3(...)` добавлена обработка wrong captcha только для актуального `pendingCut`: pending сбрасывается, captcha dedup-key очищается, `AutoCutManager.onCutCaptchaRejected(...)` планирует retry текущей клетки после `NeverTimer` без отметки checked.
- [x] В `AutoCutManager` добавлен one-shot retry `Оглядеться` через существующий `NeverTimer` и `MainActivity.checkServerTimerDrivenActions()`: по due tick загружается `go=ret&an_auto_cut_tick=1`, после реального `act=1` retry очищается.
- [x] В `AlchemyAjaxPhp` вычисляется `retrySameCellAfterCut`: если после успешного среза на клетке остаются выбранные доступные травы, `AutoCutManager.markHerbCut(...)` не помечает клетку checked и не запускает route next, а планирует повторный `Оглядеться`.
- [x] В `MainActivity` и `FightViewModel` AutoCut captcha (`alchemy_ajax.php?act=3`) отделена от боевой captcha: при объявлении боя stale popup Авто-Травника закрывается и не блокирует `requestImmediateAutoTurnOnFightAnnounce()`/`autoTurnOnce(...)`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после retry/captcha/multi-cut фиксов — успешно.
- [x] BOM-проверка измененных `AutoCutManager.java`, `AlchemyAjaxPhp.java`, `MainActivity.java`, `FightViewModel.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` после retry/captcha/multi-cut фиксов — совпадений нет.
- [x] Проверка прямого `Log.*`: совпадения только в разрешенных `AppLog.java`, `FileLogger.java`, `LogcatFileRecorder.java`.
- [-] `git diff --stat` показал известное предупреждение `.gitattributes" is not a valid attribute name: .gitattributes:7`; diff-stat при этом вывел измененные файлы.
- [x] Реализован круговой обход после последней CSV-клетки: `AutoCutManager.routeNextCellWithManager(...)` больше не останавливается на `all cells checked`, а очищает checked-set текущей смены и запускает следующий round-robin круг.
- [x] Due herb timers текущей смены теперь имеют приоритет маршрута: AutoCut извлекает `regNum` из `AppTimer.description` вида `Вырастет <трава> на <cell>`, игнорирует/удаляет stale timer-ы других смен и ведёт на клетку, когда ожидаемое время роста наступило.
- [x] Фактический `alchemy_ajax.php?act=1` очищает due herb timer-ы текущей клетки через `AutoCutManager.clearDueHerbTimersForCurrentCell(...)`, потому один scan проверяет и выросшую траву, и новое содержимое клетки.
- [x] `shouldAutoLookOnCurrentCell()` разрешает `Оглядеться` на checked-клетке, если для неё есть due herb timer текущей смены.
- [x] В `ChatStats` добавлена отдельная persisted-статистика `HERB_CUT=<Название>\t<count>` и UI-раздел `Травы (шт.)` в окне `Статистика` по формату `Название - N шт.`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после кругового обхода/timer/statistics — успешно.
- [x] BOM-проверка изменённых `AutoCutManager.java`, `AlchemyAjaxPhp.java`, `ChatStats.java`, `QuickButtonsPanel.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` после кругового обхода/timer/statistics — совпадений нет.
- [x] Проверка прямого `Log.*`: совпадения только в разрешённых `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] Добавлены подробные Javadoc/inline-комментарии с зависимостями к текущим и ранее добавленным веткам Авто-Травника: retry через `NeverTimer`, due herb timers, круговой route, mass sync, statistics, alchemy captcha/fight captcha separation.
- [x] Проверено лицензирование `Авто-Травник` через `app3`: `AnLicenseTool.normalizePublicFeatureSpec(...)` и `removeNonPublicFeatureTokens(...)` удаляют `auto_cut` из public/custom public-наборов, а `app2 LicenseFeature.expandPublicFeatureSpec(...)` повторно вырезает `auto_cut` при чтении `ANREG2.publicFeatures`.
- [x] Подтверждён ожидаемый режим выдачи: `auto_cut` доступен только через individual `full` grant или custom grant `auto_cut`; public `full`/`limited`/custom public не должны открывать `Авто-Травник`.
- [x] В `app3 AnLicenseTool` добавлены комментарии и usage-строка, фиксирующие non-public контракт для `anti_captcha`/`auto_cut` и зависимость от app2 verifier/runtime disable.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после комментариев/licensing-check — успешно.
- [x] `./gradlew.bat --no-daemon :app3:classes` после комментариев/licensing-check — успешно.
- [x] BOM-проверка изменённых app2/app3/TODO2 файлов после комментариев/licensing-check — OK.
- [x] Mojibake-проверка `app2/src/main/java`, `app3/src/main/java`, `TODO2` после комментариев/licensing-check — совпадений нет.
- [x] Проверка прямого `Log.*`: совпадения только в разрешённых `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] Проверка diff изменённых app2-файлов на старые AutoCut runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_auto_cut`/`ab_cut` — совпадений нет; `git diff` продолжает показывать известное предупреждение `.gitattributes:7`.
- [x] По логам `20260428_04_10` подтверждено: успешный `act=3` может вернуть `Случайно найдено: Предмет: Бесполезный хлам (1 шт).`, но прежний AutoCut продолжал route next без inventory cleanup.
- [x] В `AlchemyAjaxPhp.processAlchemyAct3(...)` добавлен детект `Бесполезный хлам` после success и запуск cleanup через существующий `AutoCutManager`, без нового native HTTP-контура.
- [x] В `AutoCutManager` cleanup хлама больше не зависит от галочки cleanup по массе: выставляется `BulkDropThing=Бесполезный хлам`, открывается inventory, route AutoCut удерживается до завершения cleanup.
- [x] На время AutoCut cleanup добавлена пауза небоевых авто-функций с snapshot/restore; `Авто-Бой` и `Авто-Лечение` не выключаются, восстанавливаются только реально активные функции.
- [x] В `InventoryParser.mainPhpInv(...)` bulk-drop получил wildcard по цене для AutoCut garbage-cleanup и продолжает использовать существующий `InvEntry.DropLink` (`del.gif`) для удаления всех найденных предметов.
- [x] Chat-report AutoCut теперь показывает fallback массы `+mass`, если `current/max` ещё не синхронизирован из main.php/inventory.
- [x] Исправлено залипание self `Авто-Лечение` тяжёлой травмы: очередь из heavy injury popup теперь сама запрашивает reload main.php, а stale `CurePauseNonCombatAutoFunctions` имеет timeout/fail-safe clear.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после cleanup хлама/mass fallback/Auto-Cure fixes — успешно; Kotlin daemon не подключился, Gradle отработал fallback compile и завершил `BUILD SUCCESSFUL`.
- [x] BOM-проверка изменённых `AutoCutManager.java`, `AlchemyAjaxPhp.java`, `AutoCureHandler.java`, `InventoryParser.java`, `MapAjax.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` после cleanup хлама/mass fallback/Auto-Cure fixes — совпадений нет.
- [x] Проверка прямого `Log.*` в изменённых manager/postfilter файлах — совпадений нет; общая проверка показывает только разрешённые `AppLog.java`, `FileLogger.java`, `LogcatFileRecorder.java`.
- [x] Проверка новых AutoCut/Auto-Cure runtime-маркеров: добавлен только `an_auto_cure`; новые `ab_*` не добавлялись. В `MapAjax/MainPhp/FightAuto` остаются старые pre-existing `ab_nav`/`ab_bg_probe`/HTML id, не относящиеся к этой правке.
- [x] По логам `20260428_13_00` подтверждено: chat-report Авто-Травника пишет `Масса: +5`, потому перед `act=3` нет валидного `AppVars.AutoFishMassa=current/max`; в логах отсутствуют `SetAutoFishMassa`/`mass snapshot updated`.
- [x] Найдена существующая точка исправления без нового HTTP-контура: `AlchemyAjaxPhp.processAlchemyAct1(...)` перед no-captcha/captcha `act=3` и `AutoCutHandler.processMainPhpAutoCutStep(...)`, который уже умеет парсить `Масса Вашего инвентаря` из main.php/inventory HTML.
- [x] В `AutoCutManager` добавлен one-shot mass-sync guard: при пустом `current/max` перед срезом Авто-Травник ставит `AutoCutCheckSickle`, запрашивает штатный main.php/inventory проход и не даёт map.js отправить `act=3` параллельно.
- [x] В `AutoCutHandler` добавлен mass-sync pass через существующий `MainPhp.mainPhpFindInvWithFallback(...)`: если go=inf уже содержит массу, sync завершается сразу; если нет — открывается inventory; при пустом inventory срабатывает fail-safe и остаётся прежний fallback `+mass`.
- [ ] Проверить live-лог после mass-sync фикса: перед успешным `act=3` должны появиться `mass snapshot requested before cut` и `mass snapshot updated`, а chat-report должен содержать `Масса: <b>current/max</b> (+...)`.
- [x] По логам `20260428_13_20` выявлена причина остановки после 13:11/13:20: `DoHerbAutoCut=true` запускал `Ogl(...)` при активном `NeverTimer` (`SetNeverTimer: 15s`), сервер отвечал на `alchemy_ajax.php?act=1` коротким `ERR`, а `AlchemyAjaxPhp` только писал `act1: no resource state` без retry/route.
- [x] В `AutoCutManager` добавлен общий guard `deferLookUntilServerTimerIfActive(...)`: он переиспользует existing one-shot retry через `NeverTimer`/`MainActivity.checkServerTimerDrivenActions()` и не создаёт новый HTTP-контур.
- [x] `WebAppInterface.DoHerbAutoCut()` теперь после проверки готовности клетки блокирует преждевременный JS `Ogl(...)`, если server cooldown ещё активен, и ставит retry текущей клетки.
- [x] `AlchemyAjaxPhp` теперь обрабатывает `ERR` от `act=1` как recoverable: не помечает клетку checked, а планирует повторное `Оглядеться` через существующий retry/fallback.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после mass-sync фикса — успешно.
- [x] BOM-проверка изменённых `AutoCutManager.java`, `AlchemyAjaxPhp.java`, `AutoCutHandler.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` — совпадений `РЎР`/`РџС`/`Ð`/`Ñ` нет; совпадения `????` относятся к ожидаемым captcha placeholder-ам.
- [x] Проверка прямого `Log.*`: совпадения только в разрешённых `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] Проверка app2 Java на старые runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_auto_cut`/`ab_cut` — совпадений нет.
- [x] По логам `20260428_13_30` выявлен новый false-stop: серп был надет (`NeverApi` raw: `Серп Мастера-травника`, ранее `sickle armed: ... 967/1000`), но после one-shot mass-sync inventory не содержал строку массы и `AutoCutHandler` провалился в ветку auto-wear, где надетого серпа уже нет в списке `Надеть`.
- [x] Найдена существующая точка исправления без нового HTTP-контура: `AutoCutManager.requestMassSnapshotBeforeCut(...)` и `AutoCutHandler.processMassSnapshotSync(...)`; причина была в смешении mass-sync guard с реальной проверкой серпа через `AutoCutCheckSickle`/`AutoCutArmedSickle`.
- [x] `requestMassSnapshotBeforeCut(...)` больше не сбрасывает `AutoCutArmedSickle`, потому mass-sync вызывается только после успешной проверки серпа; это сохраняет подтверждение надетого инструмента.
- [x] `processMassSnapshotSync(...)` теперь завершает one-shot mass-sync самостоятельным возвратом на карту и снимает временный `AutoCutCheckSickle` guard, чтобы inventory fail-safe не попадал в ошибочную остановку `Серп ... не найден`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса false-stop с надетым серпом — успешно.
- [x] BOM-проверка изменённых `AutoCutManager.java`, `AutoCutHandler.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка `app2/src/main/java` и `TODO2` после фикса false-stop — совпадений нет, кроме self-hit в этой TODO-строке с перечислением старых проверок.
- [x] Проверка прямого `Log.*`: совпадения только в разрешённых `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] Проверка app2 Java на старые AutoCut runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_auto_cut`/`ab_cut` — совпадений нет.
- [-] `git diff --check` для изменённых файлов показал только известное предупреждение `.gitattributes:7` и LF/CRLF warnings, без whitespace-error строк.
- [x] По логам `20260428_13_50`/`14_00` выявлен новый retry-loop: `SERVER_TIMER_TICK` после `an_auto_cut_tick=1` сам ставил локальный `NeverTimer = now + 1500`, затем `DoHerbAutoCut()` видел этот локальный cooldown и заново планировал `server_timer:bridge_do_herb_auto_cut` вместо запуска `Ogl(...)`.
- [x] Найдена существующая точка исправления без нового HTTP-контура: `MainActivity.checkServerTimerDrivenActions()`; AutoCut retry теперь очищает истёкший `NeverTimer` и не продлевает локальный anti-loop guard, поэтому штатный `map.js` может сразу выполнить `Оглядеться` после reload-а.
- [ ] Проверить live-лог после retry-loop фикса: после `look retry consumed` должен появиться `DoHerbAutoCut=true`/`AUTO_CUT_JS start Ogl`, а не новая пара `look retry scheduled: source=server_timer:bridge_do_herb_auto_cut` + `DoHerbAutoCut=false, server timer active`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после retry-loop фикса — успешно.
- [x] BOM-проверка изменённых `MainActivity.java` и `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка изменённого Java-кода — совпадений нет; в TODO остаётся ожидаемый self-hit строки с перечислением паттернов `РЎР`/`РџС`/`Ð`/`Ñ`.
- [x] Проверка прямого `Log.*`: совпадения только в разрешённых `FileLogger.java`/`LogcatFileRecorder.java`.
- [x] Проверка изменённого Java-кода на старые AutoCut runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_auto_cut`/`ab_cut` — совпадений нет.
- [-] `git diff --check` для изменённых файлов показал только известное предупреждение `.gitattributes:7` и LF/CRLF warnings.
- [x] По свежим логам `20260428_14_10` подтверждено: AutoCut пошёл по `herb_timer` (`12-342`, затем `12-311`), `Оглядеться` находил выбранный `Лён`, но после `act1: selected herb waits for mass snapshot` выполнялся inventory mass-sync и возврат на карту без отправки сохранённого `act=3`.
- [x] Найдена существующая точка исправления без нового HTTP-контура: `AlchemyAjaxPhp.pendingCut` + `AutoCutHandler.processMassSnapshotSync(...)`; после mass/sickle preparation теперь планируется delayed resume pending-среза через тот же `AjaxGet(...)` flow.
- [x] Добавлена настройка `Срезать по таймерам` в диалог `Настройки Авто-Травника`; она управляет уже существующим route по herb timer-ам.
- [x] Herb timer после успешного среза теперь ставится на `growthMinutes + 5` минут, а route считает timer готовым только по фактическому `triggerTime`, без раннего `triggerTime - 28min` обхода.
- [x] `AppTimerManager` при включённом `Авто-Травник` + `Срезать по таймерам` не удаляет due herb timer как обычный сработавший timer; timer удаляется после реального `alchemy_ajax.php?act=1` на его клетке.
- [x] После среза/проверки timer-клетки AutoCut запоминает клетку, с которой ушёл на timer-route, возвращается на неё и затем продолжает обычный CSV-обход дальше.
- [x] Проверен edge-case resume после проверки/надевания серпа: если `pendingCut` ещё не имеет `current/max` snapshot, `AlchemyAjaxPhp.resumePendingCutAfterPreparation(...)` сначала запускает existing mass-sync, а не отправляет `act=3` раньше времени.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после настройки `Срезать по таймерам`, timer-route return и pending-cut resume — успешно.
- [x] BOM-проверка изменённых app2/TODO2 файлов — OK.
- [x] Mojibake-проверка `app2/src/main` — совпадений `РЎР`/`РџС`/`Ð`/`Ñ` нет; совпадения `????` относятся к ожидаемым captcha placeholder-ам `code=????`.
- [x] Проверка прямого `Log.*`: совпадения только в разрешённых `AppLog.java`, `FileLogger.java`, `LogcatFileRecorder.java`.
- [x] Проверка `app2/src/main` на старые AutoCut runtime-префиксы `ABCLIENT`/`abclient`/`ru.neverlands.abclient`/`ab_auto_cut`/`ab_cut` — совпадений нет.
- [-] `git diff --check -- app2 TODO2` показал только известное предупреждение `.gitattributes:7` и LF/CRLF warnings, без whitespace-error строк.
- [ ] Проверить live-лог после этих правок: после `mass snapshot sync finished` должен появиться `pending cut resume scheduled` и затем `act3 no-captcha submit via AjaxGet` или captcha popup; после timer-route должен быть `timer-route return` и `timer-route returned to source cell`.
- [x] После C# фикса `Лён`/toolbar/start сверена app2-точка `AutoCutHandler.processSickleCheck(...)`: возврат после проверки серпа уже идёт через существующий `buildReturnToMapHtml(...)` и лог `return to map using parsed link`.
- [x] Найдена и исправлена app2-аналогичная гонка без нового HTTP-контура: `AutoCutManager.routeNextCellIfCurrentIsNotReady(...)` теперь не запускает маршрут, пока активна подготовка серпа, mass-sync, cleanup или pending `AlchemyAjaxPhp` cut; ожидаемый лог `route skip: preparation pending`.
- [ ] Проверить live-лог после preparation-route guard: при включении Авто-Травника на ready-клетке должны идти `route skip: preparation pending` -> `return to map using parsed link` -> `AUTO_CUT_JS schedule/start Ogl`, без преждевременного `route next`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после preparation-route guard — успешно.
- [x] BOM-проверка изменённых `AutoCutManager.java`, `AlchemyAjaxPhp.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка diff изменённых файлов после preparation-route guard — совпадений нет.
- [x] Проверка прямого `Log.*` и старых AutoCut runtime-префиксов в изменённых Java-папках — совпадений нет.
- [-] `git diff --check` для изменённых файлов показал только известное предупреждение `.gitattributes:7` и LF/CRLF warnings, без whitespace-error строк.
- [x] После C# loop `12-494 -> 12-428` проверен app2-аналог: `app2/src/main/java/ru/neverlands/anclient/utils/MapPath.java` уже выставляет `CityGateType.OktalRightToLeftGate` для `12-494 -> 12-428`, а `MainPhpCityNavigation.java` ведёт такой gate через Западные Ворота; code-fix в app2 не нужен.
- [x] После C# фикса задержки `Anti-Captcha -> act=3` проверен app2-аналог: `MainActivity.handleAntiCaptchaSolved(...)` уже в UI thread сразу вызывает общий `submitCaptchaCodeFromDialog(...)`, а `submitCaptchaSolution(..., useAjaxSubmit=true)` немедленно отправляет URL через `AjaxGet(...)`; ожидания periodic/timer между solved и срезом в app2 нет, code-fix не нужен.
- [x] После C# фикса задержки `Anti-Captcha -> AutoBoi act=7` проверен app2-аналог: боевой popup использует тот же `MainActivity.handleAntiCaptchaSolved(...) -> submitCaptchaCodeFromDialog(...)`, а при `useAjaxSubmit=false` метод `submitCaptchaSolution(...)` сразу делает `mainWebView.loadUrl(submitUrl)`; ожидания periodic/timer между solved и завершением боя в app2 нет, code-fix не нужен.
- [x] По свежим логам `20260429_12_30` перенесена app2-диагностика: `act1: no resource state` не должен стопорить AutoCut на клетке, а известные по snapshot-у клетки без выбранных трав не должны становиться следующей целью маршрута.
- [x] Найдена существующая точка исправления без нового HTTP-контура: `AutoCutManager.routeNextCellWithManager(...)`/`findNextUncheckedCell(...)` и `AlchemyAjaxPhp.processAlchemyAct1(...)`; `map.js`/`AjaxGet(...)` submit-flow не меняется.
- [x] В `AutoCutManager` добавлен persisted snapshot клетки из `HerbsList(...)`/`RESO@` по текущей смене; route теперь пропускает cached-клетки с `no_selected_herbs_in_cell_cache` или `selected_herbs_empty_current_shift`, unknown/stale snapshot не блокирует первичную проверку.
- [x] `WebAppInterface.DoHerbAutoCut()` теперь при отказе `shouldAutoLookOnCurrentCell()` может запустить существующий route-next, если текущая unchecked-клетка по cache не содержит выбранных доступных трав.
- [x] `AlchemyAjaxPhp.handleMissingResourceState(...)` для non-`ERR` ответа помечает текущую клетку как проверенную и переводит AutoCut к следующей route-клетке вместо тихой остановки на `act1: no resource state`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после route-snapshot/no-resource-state фикса — успешно; первый запуск был прерван timeout инструмента на 120 сек, повтор с большим timeout завершился `BUILD SUCCESSFUL`.
- [x] BOM-проверка изменённых `AutoCutManager.java`, `WebAppInterface.java`, `AlchemyAjaxPhp.java`, `TODO2/todo_task_20260427_auto_herbalist.md` — OK.
- [x] Mojibake-проверка diff изменённых файлов — совпадений стандартных mojibake-паттернов нет; Java diff также без question-mark mojibake.
- [x] Проверка прямого `Log.*` и старых AutoCut runtime-префиксов в изменённых Java-файлах — совпадений нет; общая проверка по app2 показывает только разрешённые logger-файлы и старые pre-existing `abcells.xml`/`abmove` комментарии вне этой правки.
- [-] `git diff --check` для изменённых файлов показал только известное предупреждение `.gitattributes:7` и LF/CRLF warnings, без whitespace-error строк.
- [x] После C# stale-cache фикса проверен app2-аналог: `AutoCutManager.getUncheckedCellSkipReason(...)` уже хранил `updatedAtMs`, но route-skip сравнивал только номер смены 1..4, поэтому snapshot вчерашней такой же смены мог блокировать первичный обход.
- [x] Исправлена существующая route-snapshot ветка без нового HTTP-контура: cache-skip `no_selected_herbs_in_cell_cache`/`selected_herbs_empty_current_shift` применяется только если snapshot обновлён внутри текущего server-shift window; unknown/stale snapshot снова идёт в обычный `alchemy_ajax.php?act=1`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после server-shift-window фикса snapshot-а — успешно.
- [x] Проверки после server-shift-window фикса: релевантные файлы без BOM; mojibake в `AutoCutManager.java` не найден; прямого `Log.*` и старых AutoCut runtime-префиксов в изменённом `AutoCutManager.java` нет.
- [x] По логам `Logs/Critical/20260429_15_40_auto_cut_trace.log` найдено отличие app2 от ПК-версии: после `act=1` с captcha app2 запускал `mass snapshot` и уходил в `main.php`/inventory/return, затем отправлял captcha `act=3`, что давало `wrong protection/captcha code response`.
- [x] Сверено с `ABClient/PostFilter/AlchemyAjaxPhp.cs`: C# делает mass snapshot только для no-captcha среза (`!CaptchaRequired`), а captcha-срез отправляет сразу из текущего `RESO@` контекста.
- [x] Исправление внесено в существующий `app2` decision point `AlchemyAjaxPhp`: `needsMassSnapshotBeforeCut()` теперь применяется только если `PendingCut.isCaptchaRequired() == false`; captcha-срез больше не уводится в `main.php` между `Оглядеться` и popup/`act=3`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после fix captcha/mass-snapshot — успешно.
- [x] Финальные проверки: релевантные файлы без BOM; в `AlchemyAjaxPhp.java` mojibake-паттерны не найдены; прямых `Log.*`, `AppVars.VCode` и старых AB runtime-префиксов в изменённом Java-файле нет; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [x] По свежему старту `Logs/Critical/20260429_15_50_auto_cut_trace.log` подтверждён выбор `12-248` через существующий `AutoCutManager.findNextUncheckedCell(...)`: route шёл как `reason=unchecked`, а cache-skip логов не было, значит первые CSV-клетки могли быть пропущены именно как checked.
- [x] Найдена причина риска stale checked: persisted `checked_shift_*` хранил только номер смены `1..4`, поэтому checked прошлого дня в такой же смене мог переиспользоваться и пропускать первые клетки после нового старта.
- [x] Исправлена существующая checked-route ветка без нового HTTP-контура: `checked_shift_*` теперь хранит `shiftStartServerMs|shift|cells`, старый формат `shift|cells` считается stale, а новый день/новое server-window смены автоматически даёт пустой checked-set.
- [x] Добавлена файловая диагностика route: при пропуске checked-клеток логируется `route skipped checked cells` с count/sample/current/next/shift/shiftStartServerMs, чтобы следующий live-лог сразу показал, почему выбрана не первая CSV-клетка.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после checked server-window фикса — успешно.
- [x] По логам `20260429_16_20` подтверждено: после `garbage cleanup requested` AutoCut переходил в inventory `&im=0`, где `Бесполезный хлам` отсутствовал, поэтому `InventoryParser` завершал bulk-drop без `garbage bulk-drop redirect` и возвращал маршрут дальше.
- [x] По ручной проверке `20260429_16_50` подтверждено: категория `main.php?wca=60` содержит `Бесполезный хлам`, но клики по `del.gif` падали с `Uncaught TypeError: top.DeleteTrue is not a function`.
- [x] Исправлена существующая cleanup-ветка `AutoCutHandler.processCleanupOpenInventory(...)`: для `BulkDropThing=Бесполезный хлам` используется filter `&wca=60` и fallback `main.php?wca=60`, без нового raw HTTP/drop-контура.
- [x] В `HtmlUtils.getJsFix()` добавлен WebView-stub `top.DeleteTrue` и alias `top.deletetrue`, чтобы штатный HTML `del.gif` выполнял existing `location='main.php?get_id=50...'` после confirm.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса категории хлама и `top.DeleteTrue` — успешно.
- [x] Проверки после фикса: изменённые файлы без BOM; в изменённых Java-файлах mojibake, прямой `Log.*` и старые AutoCut runtime-префиксы не найдены; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [x] По логам `20260429_18_10`/`18_20` проверена жалоба на остановку после спила около `18:18:29`: AutoCut не остановился, а в `18:18:34.763` запустил существующий `startAutoMoving` на `12-314` (`reason=unchecked`).
- [x] Причина визуальной паузы: после captcha-среза серверный cooldown удерживал `NeverTimer` до `18:19:57`, затем навигатор пошёл по маршруту `12-221 -> 12-192 -> 12-193 -> 12-224 -> 12-254 -> 12-285 -> 12-314`; около `18:20:30` маршрут дополнительно был прерван боем с `Крыса` и продолжился после боя.
- [x] Подтверждение кругового обхода: после достижения `12-314` в `18:21:41` AutoCut сделал `act=1`, отметил клетку checked, очистил checked-set нового круга и стартовал следующий маршрут `12-342`, затем `12-311`, `12-310`, `12-309`, `12-374`.
- [x] После допроверки логов `20260429_18_30` найден реальный стоп: в `18:33:50` AutoCut очистил checked-set нового круга, но затем cache-skip снова отбросил все клетки как `selected_herbs_empty_current_shift`/`no_selected_herbs_in_cell_cache` и завершился `route next skipped: no routable cells after circle reset`; после этого до `18:40:56` шли только chat-refresh без AutoCut/route/server-timer действий.
- [x] Сверен эталон `ABClient/HerbCell.cs`: ПК-версия в такой ситуации вызывает `ScheduleNextRouteRound(...)`, очищает checked-set, инвалидирует selected-empty snapshots и ставит retry нового круга, а не оставляет runtime без pending action.
- [x] Исправлена существующая ветка `AutoCutManager.routeNextCellWithManager(...)` без нового route/HTTP-контура: при пустом `findNextUncheckedCell(...)` теперь вызывается `scheduleNextRouteRound(...)`, который очищает checked-set, сбрасывает `updatedAtMs` у snapshot-ов `selected_herbs_empty_current_shift` и ставит one-shot retry через уже существующий `NeverTimer`/`MainActivity.checkServerTimerDrivenActions()`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса стопа нового круга — успешно.
- [x] Проверки после фикса стопа нового круга: изменённые файлы без BOM; mojibake в diff не найден; в `AutoCutManager.java` нет прямого `Log.*`, `AppVars.VCode` и старых AutoCut runtime-префиксов.
- [-] `git diff --check` для изменённых файлов показал только известный шум `.gitattributes:7` и LF/CRLF warnings, без whitespace-error строк.
- [x] По свежим логам `20260429_20_30` найден новый стоп не в route/cache: в `20:32:28` `AppTimerManager` написал `[TIMER_PAUSE] Auto-Herb paused` и вызвал `setAutoCutEnabled(false)` за 4.5 сек до herb timer `timerId=14`; в `20:32:34` навигатор достиг `12-371`, но AutoCut уже был disabled, поэтому `DoHerbAutoCut=false, disabled` и `Оглядеться` не запускалось.
- [x] Найдена существующая точка исправления без нового HTTP/route-контура: `AppTimerManager.processDueTimers()`. Буферная пауза таймеров выполнялась раньше проверки `timer.isHerb && shouldKeepDueHerbTimerForAutoCut()`, из-за чего herb timer режима `Срезать по таймерам` сам выключал Авто-Травник.
- [x] Исправлено: herb timer, который принадлежит включённому AutoCut режиму `Срезать по таймерам`, теперь пропускает `[TIMER_PAUSE]` и логирует `[TIMER_PAUSE_SKIP] AutoCut owns herb timer`, сохраняя AutoCut включённым до прибытия на due-клетку и штатного `alchemy_ajax.php?act=1`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса timer-pause для herb timer — успешно.
- [x] Проверки после фикса timer-pause: изменённые файлы без BOM; в `AppTimerManager.java` не найдено mojibake, прямого `Log.*` и новых `AppVars.VCode`; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [x] По live logcat `23:01`/`23:02` найден новый стоп: после включения AutoCut маршрут стартовал на `12-307`, но `MapAjax.process.tooTired` поднял усталость до `100`, `DoAutoDrinkBlaz=false`, общий AutoMoving был остановлен и дальше шёл только `AUTO_CUT_JS skip no look/ogl button` без нового route/pending action.
- [x] Найдена существующая точка исправления без нового HTTP/route-контура: ветка `containsTooTiredMessage(...)` в `MapAjax.process(...)` и существующий `AutoFunctionsManager.startAutoMoving(...)`, которым уже пользуется AutoCut.
- [x] Исправлено: если `too tired` остановил AutoMoving к CSV-клетке AutoCut, `MapAjax` больше не пишет Auto-Клад stop как финальный исход, а `AutoCutManager.scheduleRouteRetryAfterTiredness(...)` ставит one-shot retry маршрута через 60 сек и заново выбирает актуальную due/unchecked клетку.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса tired-route retry — успешно.
- [x] По live logcat после reinstall (`00:04`, PID `27139`) найден post-login стоп: persisted `auto_function_auto_cut=true` восстанавливался в UI, но `restorePersistentAutoModesAfterLogin()` не запускал runtime bootstrap Авто-Травника.
- [x] Исправлено в существующем restore-контуре `AutoFunctionsManager.restorePersistentAutoModesAfterLogin()`: при `autoCut=true` вызывается штатный `setAutoCutEnabled(true)` и логируется `restore after login: auto-cut owns cold start bootstrap`, без нового login/route контура.
- [x] По live logcat после следующего reinstall (`00:15`, PID `29297`) найден второй post-login стоп: после `sickle armed` и `return to map using parsed link` не было `route next`, потому route был ранее подавлен `route skip: preparation pending` и не возобновлялся после завершения проверки серпа без pending cut.
- [x] Исправлено в существующем контуре `AutoCutHandler -> AutoCutManager`: после `sickle_checked`/`sickle_already_ready`/mass-sync без pending cut вызывается `continueRouteAfterPreparationIfIdle(...)`, который повторно применяет `routeNextCellIfCurrentIsNotReady(...)` и стартует маршрут через тот же `AutoFunctionsManager.startAutoMoving(...)`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после post-login preparation route bootstrap — успешно.
- [x] Live smoke после установки `app2/build/outputs/apk/debug/anclient_v1.1.5.apk`: подтверждены markers `restore after login: auto-cut owns cold start bootstrap`, `sickle armed`, `preparation completed: continue route bootstrap`, `route next: destination=12-371`, `startAutoMoving: destination=12-371`.
- [x] Live smoke tired-route после `NeverTimer`: подтверждены markers `auto-moving stopped by tiredness, route retry scheduled`, `autoCutRetryScheduled=true`, `tired route retry fired`, повторный `route next` и `startAutoMoving`; повторный серверный `too tired` ожидаем при усталости 100 и не является прежним зависанием runtime.
- [x] По live logcat `00:18-00:25` найден уточняющий риск tired-retry: фиксированный retry 60 сек поддерживал runtime живым, но при `DoAutoDrinkBlaz=false` каждую минуту снова попадал в `too tired`, пока `pinfo` снижался только `99 -> 98`; это не dead-stop, но создаёт лишние серверные попытки до выхода ниже порога.
- [x] Исправлено в существующей ветке `AutoCutManager.scheduleRouteRetryAfterTiredness(...)`: задержка retry теперь рассчитывается по `tiedNow` и `tiedThreshold` до выхода ниже порога, с минимумом 60 сек; новый route по-прежнему стартует через штатный `routeNextCellWithManager(...)`/`AutoFunctionsManager.startAutoMoving(...)`, без нового HTTP/route-контура.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после расчётной задержки tired-retry — успешно.
- [x] Live smoke после установки свежего APK и ручного login: подтверждены markers `restore after login: auto-cut owns cold start bootstrap`, `sickle armed`, `preparation completed: continue route bootstrap`, `route next: destination=12-371`, `startAutoMoving`, затем `auto-moving stopped by tiredness, route retry scheduled: destination=12-371, tied=100, threshold=84, delayMs=1020000`; прежнего минутного `delayMs=60000` больше нет.
- [-] Фактический срез после tired-retry отложен до серверного cooldown: при `tied=100`, `threshold=84` новый безопасный retry ожидается примерно через 17 минут или после ручного/профильного снижения усталости.
- [x] По логам `_device_logs_0906` найдено, почему cleanup `Бесполезный хлам` снова завершался без `del.gif`: после `garbage cleanup requested` app2 переходил на `go=inv&...&wca=60`, сервер возвращал main.php HTML без inventory rows (`Raw bytes=1545`), а `AutoCutHandler.afterMainPhpInventoryStep(...)` завершал cleanup только по inventory-адресу.
- [x] Исправлена существующая cleanup/inventory ветка без нового raw HTTP/drop-контура: для `BulkDropThing=Бесполезный хлам` `AutoCutHandler.processCleanupOpenInventory(...)` теперь открывает реальную категорию `main.php?wca=60`, а `afterMainPhpInventoryStep(...)` завершает cleanup только когда HTML действительно похож на inventory (`mainPhpIsInv`/rows), не по одному `go=inv` в URL.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса `go=inv&wca=60` false-complete — успешно.
- [x] Проверки после фикса: изменённые файлы без BOM; в diff mojibake-паттерны не найдены; в `AutoCutHandler.java` нет прямого `Log.*` и новых обращений к `AppVars.VCode`; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [x] По свежим логам `20260430_10_20`/`10_30` найден фоновой root cause AutoCut `act1: ERR response`: при `uiForegroundInteractive=false` и `fightLikelyActive=false` сервис каждые ~2 сек запускал `autoTurn idle probe` (`main.php?ab_bg_probe=1`/`go=inf&ab_bg_probe=1`), пока карта пыталась выполнить `alchemy_ajax.php?act=1`; после `onResume` probe прекращался и тот же AutoCut flow сразу доходил до `act1: selected herb`.
- [x] Найдена существующая точка исправления без нового HTTP/proxy/route-контура: `AutoModeForegroundService.runBackgroundTick()` guard авто-хода. Исправление внесено именно туда, чтобы убрать конкурирующие background probe-запросы до отправки `Ogl(...)`, а не добавлять отдельный AutoCut scheduler.
- [x] Исправлено: `AutoModeForegroundService` больше не запускает no-fight `autoTurn idle probe`, если нет fight-маркеров, pending finish-link, cold-start `ProbeForceNeedAutoboi` или свежего `LastFightAnnounceAtMs`. Cold-start probe и event-driven fight announce сохранены.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса no-fight idle probe — успешно.
- [x] Проверки после фикса: изменённые файлы без BOM; в `AutoModeForegroundService.java` не найдено mojibake, прямого `Log.*`, `AppVars.VCode` и старых AutoCut runtime-префиксов; в TODO остаются только ожидаемые self-hit строки с перечислением mojibake-паттернов; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [ ] Проверить live-лог после фонового фикса: при фоне без боя должен появляться `skip autoTurn idle probe, no fight context`, не должно быть серии `requestAutoTurn: app backgrounded`/`ab_bg_probe` рядом с `AUTO_CUT_JS start Ogl`, а `alchemy_ajax.php?act=1` должен перейти из `ERR` к `RESO@`/`act1: selected herb`.
- [x] По свежим логам `20260430_13_00` найдено, почему `Бесполезный хлам` не удалялся после `13:03:21`: AutoCut переводил cleanup на `main.php?wca=60` без `go=inv&vcode`, сервер отдавал HTML без inventory rows, поэтому `InventoryParser.mainPhpInv(...)` не доходил до `del.gif`/`get_id=50`.
- [x] `Inventory_hlam_del.har` подтвердил ручной рабочий flow: сначала реальный inventory URL `main.php?get_id=56&act=10&go=inv&vcode=...`, затем штатная delete-ссылка `main.php?get_id=50&uid=...&wpr=1&wmas=750&wcs=1&wms=1&vcode=...&wn=...` из `del.gif`.
- [x] Исправлена существующая cleanup/inventory ветка без raw-drop контура: `AutoCutHandler.processCleanupOpenInventory(...)` снова открывает inventory через `MainPhp.mainPhpFindInvWithFallback(..., "&im=0", address)`, чтобы сохранить `go=inv&vcode`; `main.php?wca=60` больше не используется как отдельный обход. Если текущий кадр уже застрял на inventory-like URL без rows, handler возвращает `go=inf` и даёт штатному helper заново найти реальную ссылку инвентаря.
- [x] `Бесполезный хлам` подключён к той же ветке `InventoryParser.mainPhpInv(...)`, где выбрасываются просроченные вещи: при `AutoCutCleanupPending` и наличии `DropLink` parser строит штатный redirect на `invEntry.DropLink`, логируя `garbage bulk-drop redirect: link=...`.
- [ ] Проверить live-лог после фикса хлама: после `garbage cleanup requested` должен быть `cleanup redirect to inventory, reason=garbage:alchemy_act3, filter=&im=0`, затем `garbage bulk-drop redirect: link=main.php?get_id=50...`, затем `garbage bulk-drop completed` и `cleanup completed, source=inventory_pass`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса хлама через inventory parser — успешно.
- [x] Проверки после фикса хлама: изменённые файлы без BOM; в `AutoCutHandler.java`/`InventoryParser.java` не найдено mojibake, прямого `Log.*`, новых `AppVars.VCode` и старых AutoCut runtime-префиксов; `git diff --check` по изменённым файлам без вывода.
- [x] По жалобе игрока на `Навигатор -> бой -> proxy fail -> бой закончился -> навигатор не идёт` учтён реальный сетевой путь: локальный proxy приложения передаёт запросы в удалённый upstream proxy, а затем на игровой сервер.
- [x] Найдена существующая точка исправления без нового route/proxy контура: `MainPhp` продолжал рассматривать `AppVars.AutoMoving` даже на `isFightFrame/isFightTopFrame`; теперь ветки `mainPhpWtime`, city/teleport/map-return подавляются в бою и логируют `AUTO_MOVING_TRACE: pause navigator while fight frame is active`.
- [x] Найдена существующая точка proxy recovery: `WebViewRequestInterceptor` уже оборачивает `HttpURLConnection` через локальный proxy; теперь main-frame transient `502/503/504`/strict intercept failure возвращает HTML auto-retry на тот же URL через proxy, с маркером `PROXY_RETRY`, не отдавая пользователю plain `Proxy forwarding error` как финальную страницу.
- [ ] Проверить live на upstream proxy: во время боя навигатор не кликает карту; при proxy-сбое есть `PROXY_RETRY`; после завершения боя текущий `AutoMovingDestinaton` сохраняется и маршрут продолжается через обычный `MapAjax.process(...)`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после nav/proxy-fail фикса — успешно.
- [x] Проверки после nav/proxy-fail фикса: изменённые файлы без BOM; в `MainPhp.java`/`WebViewRequestInterceptor.java` не найдено mojibake и прямого `Log.*`; новых обращений к `AppVars.VCode` нет, найдено только старое TODO-комментарное упоминание в `MainPhp.java`; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
- [x] По свежим логам `20260430_14_40` подтверждено, что `Бесполезный хлам` всё-таки выбрасывается штатным `DropLink`: `garbage bulk-drop redirect: link=main.php?get_id=50...`, затем `garbage bulk-drop completed`, `cleanup completed, source=inventory_pass`, `route next: destination=12-374`.
- [x] Найден новый риск в той же cleanup/inventory ветке: между `14:41:41` и `14:43:01` handler гонял `go=inf -> go=inv` каждые ~250-500ms, потому `go=inv&im=0` во время серверного cooldown возвращал HTML без inventory rows, а код сразу делал `cleanup inventory address has no inventory html, reload main frame`.
- [x] Исправлена существующая cleanup/resume ветка без нового HTTP/drop/scheduler-контура: если inventory URL ещё не содержит реальный inventory HTML и активен `NeverTimer`, `AutoCutHandler` больше не перезагружает `go=inf`, а через `AutoCutManager.deferCleanupInventoryUntilServerTimer(...)` переиспользует штатный one-shot `NeverTimer` dispatcher и повторяет inventory pass после cooldown.
- [ ] Проверить live-маркеры после фикса cooldown-loop: вместо серии `cleanup inventory address has no inventory html` должен быть `cleanup inventory wait deferred until server timer` и `cleanup waits server timer before reopening inventory`, затем после server-timer — `cleanup redirect to inventory`, `garbage bulk-drop completed`, `cleanup completed` и `route next`.
- [x] `./gradlew.bat --no-daemon :app2:assembleDebug` после фикса cleanup cooldown-loop — успешно.
- [x] Проверки после фикса cleanup cooldown-loop: изменённые файлы без BOM; в `AutoCutManager.java`/`AutoCutHandler.java` не найдено mojibake, прямого `Log.*` и новых `AppVars.VCode`; в TODO остаются только старые self-hit строки с перечислением mojibake-паттернов; `git diff --check` показал только известный шум `.gitattributes:7` и LF/CRLF warnings.
