# План портирования MainPhpFish.cs (1:1 с ПК C#)

## Фактический статус на Android (на 2026-03-07)

- [ ] В `MainPhp.java` отсутствует ветка `MainPhpAutoFishPrepare` (аналог C#).
- [ ] В `MainPhp.java` отсутствует ветка `MainPhpFishReport` (аналог C#).
- [ ] Не реализована C#-цепочка флагов `AutoFishCheckUd -> AutoFishWearUd`.
- [ ] Не реализована C#-цепочка `AutoFishCheckUm` через `mselect=1` для актуализации `FishUm`.
- [~] `UserConfig`/`ParsedDressed` частично готовы: есть `FishAutoWear`, `FishHandOne`, `FishHandTwo`, проверка двух рук.
- [ ] Не встроена приоритетная логика выбора приманки по `FishEnabledPrims` в `MainPhp`-потоке.
- [ ] Не реализована рыбацкая captcha-ветка (отдельно от боевой), включая `FightLink` c `&code=...`.

## Что делает C# эталон (ABClient/PostFilter/MainPhpFish.cs)

1. `MainPhpAutoFishPrepare(html)`:
- проверяет страницу рыбалки;
- парсит `get_id`, `act`, `vcode`, `lakeid`, текущую массу инвентаря;
- выбирает доступную приманку из `Profile.FishEnabledPrims` (рандом в пределах разрешённых);
- отмечает выбранную приманку в HTML (`checked`);
- формирует `AppVars.FightLink` на следующий заброс;
- если найдена капча, заполняет `AppVars.CodeAddress` и включает режим ручного ввода кода.

2. `MainPhpFishReport(html)`:
- парсит результат улова (`nameFish`, `numFish`, `catchFish`, `fishUmUp`);
- обновляет `FishUm`, `AutoFishNV`, `AutoFishMassa`, остаток наживки;
- формирует подробный HTML-отчёт + чат/балун;
- после отчёта ставит флаги на следующий цикл (`AutoFishCheckUd=true`, `AutoFishWearUd=false`).

## Разрыв с Android-кодом

- В `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java` рыбалка не обрабатывается.
- В `app/src/main/java/ru/neverlands/abclient/manager/AutoFunctionsManager.java` включение `AUTO_FISH`
  не поднимает C#-инициализацию runtime-флагов (`CheckUd/CheckUm/...` и reset статистики).
- Нет связки с диалогом капчи по рыбалке.
- `FishEnabledPrims` отсутствует в текущем Android `UserConfig` как битовая маска C# `Prims`.

## План 1:1 реализации

- [ ] Перенести `MainPhpAutoFishPrepare` в `MainPhp.java` (с теми же именами runtime-полей `AppVars`).
- [ ] Перенести `MainPhpFishReport` в `MainPhp.java` (до внедрения отдельного менеджера).
- [ ] Добавить `FishEnabledPrims` в `UserConfig` + load/save профиля.
- [ ] Добавить enum/битмаску `Prims` (C#-совместимые значения).
- [ ] Добавить C#-инициализацию флагов в `AutoFunctionsManager.setAutoFishEnabled(true)`:
  `AutoFishCheckUd`, `AutoFishWearUd`, `AutoFishCheckUm`, reset `AutoFishHand*`, `AutoFishMassa`, `AutoFishNV`.
- [ ] Интегрировать рыбацкую captcha-ветку (UI/submit), не ломая существующую боевую капчу.
- [ ] Прогнать сценарии: без капчи, с капчей, без доступной приманки, без снастей.

## Связанный task-файл

Детализированный рабочий план и чеклист по всему AutoFish-потоку:
- `TODO/todo_task_20260307_autofish_1to1_port.md`
