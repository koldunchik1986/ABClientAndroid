# TODO: Порт «Авторыбалка» 1:1 из C# в Android (2026-03-07)

## Цель

Перенести `Авторыбалку` и её настройки в Android максимально близко к ПК-версии (`ABClient`) без изменения целевого поведения.

## Источники истины (C#)

- `ABClient/ABForms/FormMain.cs` (`ButtonAutoFish_Click`).
- `ABClient/PostFilter/MainPhp.cs` (ветки `AutoFishCheckUm`, `AutoFishCheckUd`, `AutoFishWearUd`).
- `ABClient/PostFilter/MainPhpFish.cs`.
- `ABClient/PostFilter/FishAjaxPhp.cs`.
- `ABClient/MyForms/FormSettingsGeneral.cs` (настройки приманок/рук).
- `ABClient/TInvUd.cs` (проверка слотов снастей).

## Входные артефакты трафика

- `DressFishing.har` — надевание рыболовного инвентаря.
- `FishingCaptcha.har` — flow рыбалки с капчей.

## Текущее состояние Android (snapshot)

- [x] Есть quick-кнопка `AUTO_FISH` с toggle.
- [x] Есть профильные поля `AutoFish`, `FishAutoWear`, `FishHandOne/Two`, `FishEnabledPrims`, `FishUm`.
- [x] Есть парсинг экипировки двух слотов в `ParsedDressed`.
- [x] Реализован `mainPhpAutoFishPrepare(...)` в `MainPhp`.
- [x] Реализован flow проверки/надевания снастей + переходов карты/рыбалки.
- [x] Реализована ветка fish captcha (показ диалога, submit по `finishUrl`, анти-дубль).
- [x] Реализован `FishAjaxPhp.java` (отчет, умелка, NV, масса, stop-ошибки).
- [ ] Финальная проверка всего потока на runtime-логах еще не закрыта.

## План реализации

### Этап 1. Профиль и настройки
- [x] Добавить `FishEnabledPrims` в `UserConfig`.
- [x] Добавить enum/битмаску `Prims` (C#-совместимые значения).
- [x] Добавить UI-настройки «Авто-Рыбалка»:
  - [x] выбор предмета в 1-й руке;
  - [x] выбор предмета во 2-й руке;
  - [x] выбор доступных приманок.
- [x] Подключить long-press на `AUTO_FISH` для открытия fish-настроек.

### Этап 2. Runtime и включение функции
- [x] В `AutoFunctionsManager.setAutoFishEnabled(true)` добавить C#-инициализацию:
  - [x] `AutoFishCheckUd = true`;
  - [x] `AutoFishWearUd = false`;
  - [x] `AutoFishCheckUm = (FishUm == 0)`;
  - [x] reset `AutoFishHand1/1D/2/2D`;
  - [x] reset `AutoFishMassa`;
  - [x] `AutoFishNV = 0`;
  - [x] `AutoFishDrink = false`.

### Этап 3. MainPhp flow (ключевой)
- [x] Встроить fish-ветку в `MainPhp.java`:
  - [x] проверка/чтение умения (`mselect=1`);
  - [x] проверка необходимости переодевания снастей;
  - [x] переход в инвентарь `im=0&wca=4` и надевание;
  - [x] подготовка рыболовного запроса (`mainPhpAutoFishPrepare`).
- [x] Реализовать выбор приманки только из разрешенных и реально доступных.

### Этап 4. Рыбацкая капча
- [x] Детектить fish-captcha до финального запроса.
- [x] Показывать captcha-диалог и отправлять submit с выбранной приманкой + `code`.
- [x] Добавить anti-duplicate guard для повторных challenge.
- [ ] Протестировать на нескольких runtime-логах с капчей и без.

### Этап 5. Fish AJAX report
- [x] Портировать `FishAjaxPhp.cs` -> `FishAjaxPhp.java`.
- [x] Обновлять `FishUm`, `AutoFishNV`, `AutoFishMassa`, cycle-флаги.
- [x] Выводить расширенный отчет в ответ и чат.
- [ ] Сверить расчеты 1:1 с C# по контрольным кейсам.

## Критерии готовности

- [ ] При включении `Авто-Рыбалка` снасти стабильно проверяются и надеваются в оба слота.
- [ ] Приманка выбирается строго по настройкам и доступности.
- [ ] При капче диалог появляется корректно, submit проходит, цикл продолжается.
- [ ] Отчет ловли стабильно попадает в UI/чат и корректно считает NV/массу.
- [ ] Нет циклических перезаходов и подвисаний в `main.php`.

## Следующий фокус

- [ ] Взять 2-3 свежих `logcat_runtime_*.txt` по рыбалке (с капчей/без) и закрыть пункты валидации Этапа 4 и 5.

