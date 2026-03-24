# Задача: Порт Автолечения из C# + синхронизация травм через pinfo `var eff`

## Контекст
- Требуется портировать Автолечение из ПК-версии C# в Android-приложение.
- Источник травм должен идти из единой модели параметров персонажа, включая синхронизацию через `pinfo.cgi`.
- В трафике `Pars_injured.har` подтвержден формат `var eff = [[code,'name'], ...]`:
  - `2` — тяжелая травма
  - `3` — средняя травма
  - `4` — легкая травма
  - `24` — яд
- В трафике `injuredHigh.har` подтвержден popup на карте:
  - `Вы не можете перемещаться! У Вас тяжёлая травма.`

## Анализ C#-эталона
- `ABClient/PostFilter/MainPhpAutoCure.cs`
  - `GetPoisonAndWounds(html)` парсит `cureff`.
  - `MainPhpRemovePoison(html)` формирует POST для `post_id=46` (magicreform) на себя.
- `ABClient/PostFilter/MainPhpCure.cs`
  - `MainPhpCure(html)` парсит `doctorform(...)` и формирует POST лечения травмы.
- `ABClient/PostFilter/MainPhp.cs`
  - Логика AutoCure в основном пайплайне:
    - приоритет лечения яда;
    - затем лечение небоевых травм;
    - переходы по категориям инвентаря (`wca=27`, `wca=85`);
    - отключение AutoCure при отсутствии средств лечения.
- `ABClient/ABForms/FormMainCheckInfo.cs`, `FormMainCheckTied.cs`
  - Поддерживают актуальность `PoisonAndWounds` по API/effects.

## Текущее состояние Android (до изменений)
- Переключатель `AUTO_CURE` в UI есть (`AutoFunctionsManager`), но логика выполнения в `MainPhp` отсутствует.
- `NeverApi.getPinfoVitalsFromPinfo(...)` парсит только HP/MA/усталость, без `var eff`.
- Единая модель параметров (`CharacterVitalsManager`) не содержит травмы/яд.

## План реализации
- [x] Провести анализ C#-эталона и текущей реализации Android.
- [x] Добавить в `NeverApi.PinfoVitals` парсинг и хранение статуса травм/яда из `var eff`.
- [x] Расширить `CharacterVitalsManager` поддержкой `PoisonAndWounds` как части единой runtime-модели.
- [x] Добавить runtime-поля в `AppVars` для хранения травм/яда.
- [x] Добавить детектор popup тяжелой травмы в `MainPhp` по сообщению из `injuredHigh.har`.
- [x] Портировать AutoCure-ветку в `MainPhp.process`:
  - [x] лечение яда (категория `wca=27`, `magicreform` -> POST);
  - [x] лечение травм (категория `wca=85`, `doctorform` -> POST);
  - [x] авто-отключение AutoCure при отсутствии нужных средств.
- [x] Добавить чат-сообщения AutoCure с серверным timestamp.
- [x] Проверить сборку `:app:compileDebugJavaWithJavac`.
- [x] Обновить этот TODO по факту выполненных пунктов.

## Файлы-кандидаты на изменение
- `app/src/main/java/ru/neverlands/abclient/manager/NeverApi.java`
- `app/src/main/java/ru/neverlands/abclient/manager/CharacterVitalsManager.java`
- `app/src/main/java/ru/neverlands/abclient/utils/AppVars.java`
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
