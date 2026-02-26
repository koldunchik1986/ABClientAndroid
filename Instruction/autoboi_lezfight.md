# Инструкция: отладка и портирование AutoBoi + LezFight (Android)

## 1. Назначение
Документ описывает, как диагностировать и исправлять проблемы автобоя в Android-порте по эталону C#:
- "автобой не бьет";
- "белый верхний фрейм после остановки автобоя".

Эталонные файлы C# (только для сравнения, не изменять):
- `ABClient/PostFilter/FightJs.cs`
- `ABClient/PostFilter/MainPhpFight.cs`
- `ABClient/Lez/LezFight.cs`
- `ABClient/ABForms/FormMainAutoBoi.cs`
- `ABClient/ScriptManager.cs`

Рабочие файлы Android:
- `app/src/main/java/ru/neverlands/abclient/postfilter/FightJs.java`
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
- `app/src/main/java/ru/neverlands/abclient/lez/LezFight.java`
- `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java`
- `app/src/main/java/ru/neverlands/abclient/utils/AppVars.java`
- `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`

## 2. Ключевая цепочка работы автобоя
1. `MainPhp.mainPhpFight()` парсит HTML боя через `LezFight`.
2. Если безопасно и автобой включен, возвращается `fight.Frame`.
3. `fight.Frame` в Android содержит `setTimeout(... window.location='main.php?post_id=7...')`.
4. `WebViewInterceptor` перехватывает `main.php?post_id=7...`, прогоняет через `Filter.process()`.
5. Бой обновляется, цикл повторяется.

## 3. Что уже исправлено
### 3.1 Белый фрейм и JS-краш
- В `FightJs.java` исправлено экранирование submit-кнопки завершения боя:
  - проблема: `Unexpected identifier 'FEND'` в `fight_v10.js`;
  - фикс: корректное экранирование `document.forms[\\\'FEND\\\'].submit()` в Java-строке.

### 3.2 Неполные параметры автоудара
- В `LezFight.BuildFrame()` добавлен параметр `inf_zb` (аналог C# `fightpm[10]`).
- Без него сервер мог принимать запрос `post_id=7`, но не применять удар корректно.

## 4. Что обязательно проверять в логах
Файл логов: `Logs/logcat.txt`.

Фильтры:
- `MainPhp`, `LezFight`, `WebViewInterceptor`, `WebAppInterface`, `JS_CONSOLE`.

Команда поиска:
`rg -n "MainPhp|LezFight|WebViewInterceptor|WebAppInterface|JS_CONSOLE|post_id=7|Autoboi|fight.Frame" Logs/logcat.txt -S`

Критические признаки:
- Хорошо:
  - `mainPhpFight: SAFE - returning fight.Frame for auto-attack`
  - `main.php?post_id=7...`
- Плохо:
  - `JS_CONSOLE: Uncaught SyntaxError...`
  - `JS_CONSOLE: Uncaught ReferenceError...`
  - нет `post_id=7` при активном автобое
  - `Autoboi state=AutoboiOff` при `LezDoAutoboi enabled`

## 5. Сверка Android с C# (чеклист)
1. `FightJs`:
   - инъекции кнопок и функций (`AutoSelect`, `AutoTurn`, `AutoBoi`, `ResetCure`) должны быть синтаксически валидны.
2. `MainPhpFight`:
   - ветки `IsWaitingForNextTurn`, `AutoboiOn/Off`, `LezDoAutoboi` должны повторять C#-логику.
3. `LezFight.BuildFrame`:
   - параметры должны быть эквивалентны C# форме: `post_id`, `vcode`, `enemy`, `group`, `inf_bot`, `inf_zb`, `lev_bot`, `ftr`, `inu`, `inb`, `ina`.
4. `WebAppInterface.AutoBoi`:
   - синхронизация `AppVars.Autoboi` и `AppVars.Profile.LezDoAutoboi`.

## 6. Что ещё доделать (текущий статус)
- [ ] Подтвердить в runtime, что после фикса `inf_zb` HP противника уменьшается в каждом цикле автобоя.
- [ ] Если урон не проходит — сравнить формат `inu/inb/ina` с C# по фактическим примерам из логов.
- [ ] При необходимости добавить диагностический лог полного URL автоудара в `LezFight.BuildFrame()` (маскируя чувствительные данные).

## 7. Минимальный протокол повторной отладки
1. Очистить логи: `adb logcat -c`.
2. Запустить сценарий: включить автобой, дождаться 2–3 циклов, остановить автобой.
3. Снять логи:
   - `adb logcat -v time --pid $(adb shell pidof ru.neverlands.abclient) > Logs/logcat_runtime.txt`
4. Проверить фильтром из раздела 4.
5. Зафиксировать выводы в:
   - `TODO/todo_DebugApp.md`
   - `TODO/Debug/debug_log_{timestamp}.md`