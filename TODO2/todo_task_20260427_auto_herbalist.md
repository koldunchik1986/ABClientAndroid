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
- `QuickButtonsPanel` уже умеет включать/выключать `AUTO_CUT`, но иконка сейчас `null`/`ic_add`.
- `FunctionListAdapter` уже показывает `AUTO_CUT`, но иконка сейчас `null`/`ic_add`.
- `MapJs.java` уже объявляет JS shim `window.external.DoHerbAutoCut()`, `HerbsList(...)`, `TraceCut(...)`.
- `app2/src/main/assets/js/map.js` уже вызывает `DoHerbAutoCut()` и автоматически делает `Ogl(...)` при наличии кнопки `Оглядеться`.
- `WebAppInterface.HerbsList(String list)` и `TraceCut(String herb)` сейчас только логируют, без состояния Авто-Травника.
- `WebAppInterface.DoHerbAutoCut()` отсутствует, поэтому текущий JS shim всегда получает default `false`.
- В `Filter.process(...)` нет обработчика `alchemy_ajax.php`, значит Java-side postfilter не видит структурно ответы `act=1/act=3`.
- Long-press меню настроек для `AUTO_CUT` отсутствует.

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

## План реализации

### Фаза 0: иконка функции

- [x] В `QuickButtonsPanel.getIconUrlForAction(AUTO_CUT)` вернуть `http://image.neverlands.ru/achievement/20/a_20_3.gif`.
- [x] В `FunctionListAdapter.getIconUrlForAction(AUTO_CUT)` вернуть тот же URL.
- [x] Оставить локальный fallback `R.drawable.ic_add`, если загрузка внешней иконки упала.

### Фаза 1: настройки Авто-Травника

- [x] Добавить long-press меню для `AUTO_CUT`: `Настройки авто-травника` и `Удалить кнопку`.
- [x] Добавить экран/диалог настроек с полем `Клетки для поиска`, CSV формата `xx-xxx, x-xxx`.
- [ ] Добавить отдельный групповой список трав `Травы 1`..`Травы 11` и `Не определено`.
- [x] Добавить чекбоксы выбора трав для автосреза.
- [x] Добавить long-press на траву: редактировать `Умение`, `Время среза`, `Группа`.
- [ ] Добавить editable смены трав: `00:50-06:50`, `06:50-12:50`, `12:50-18:50`, `18:50-00:50`.
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
- [x] Если масса инвентаря выросла больше чем на `10%`, запускать cleanup/выброс мусора по отдельной настройке.
- [x] Не ломать ручную работу инвентаря: все действия через существующие guard-ветки и suppression windows.

## Риски и проверки

- [ ] Проверить, что `DoHerbAutoCut()` не вызывает бесконечный `Ogl(...)` при каждом redraw карты.
- [ ] Проверить, что `act=3` использует именно `cutVCode`, а не stale `SessionManager` vcode.
- [ ] Проверить, что при ручном `Оглядеться` без выбранной травы Anti-Captcha не стартует.
- [ ] Проверить, что manual captcha popup работает при выключенной Anti-Captcha или пустом API key.
- [ ] Проверить, что `alchemy_ajax.php` response остается в `AjaxGet` flow и карта обновляет таймер/кнопки.
- [ ] Проверить, что фоновый сервис запускается/останавливается корректно, если runtime Авто-Травника потребует background tick.
- [x] Проверить `./gradlew.bat --no-daemon :app2:assembleDebug`.
- [x] Проверить UTF-8 без BOM измененных `.java`, `.js`, `.md` файлов.
- [x] Проверить стандартные mojibake-паттерны без self-hit в тексте TODO.
- [x] Проверить отсутствие новых `import android.util.Log;` и `Log.d/i/w/e` в app2 прикладном коде.

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
- [ ] Device smoke: открыть long-press `AUTO_CUT`, сохранить настройки, проверить `Оглядеться`/captcha на live-карте.
