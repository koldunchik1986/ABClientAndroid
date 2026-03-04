# Задача: 1:1 портирование `buttonAutoSkin` (Авто-Охота) с ПК C# на Android

## Цель
- [ ] Полностью повторить поведение `buttonAutoSkin` (ПК `ABClient`) в Android:
  - авто-надевание профессионального инструмента (ножа) при включенной авто-охоте;
  - авто-переход на действие `Разделать` после победы в бою;
  - парсинг результата разделки и вывод в чат/статистику;
  - корректная совместимость с текущим пайплайном автобоя и капчи завершения боя.

## Эталонные C# файлы (источник истины)
- [x] `ABClient/TInvUd.cs` — парсер экипировки `ParsedDressed` (`slots_inv`/`slots_pla`) и проверка ножа.
- [x] `ABClient/PostFilter/MainPhpWear.cs` — проверка/надевание ножа + парсинг ресурсов разделки.
- [x] `ABClient/PostFilter/MainPhpRaz.cs` — обнаружение кнопки `Разделать` по `fight_ty[9]` и редирект.
- [x] `ABClient/PostFilter/MainPhp.cs` — оркестрация флагов `AutoSkinCheck*` и переходов между страницами.
- [x] `ABClient/Lez/LezFight.cs` — фиксация результата разделки в логах боя (`ShowRazdMessage`).
- [x] `ABClient/ABForms/FormMain.cs`, `FormMainInit.cs`, `FormMainTicks.cs` — инициализация/тик/периодическая перепроверка ножа.

## Проверка текущей Android-реализации (до начала кода)
- [x] Есть только UI-тоггл `AUTO_SKIN` в `AutoFunctionsManager` + `QuickButtonsPanel`.
- [x] Нет аналога `ParsedDressed` (`TInvUd.cs`) в `app/src/main/java`.
- [x] Нет аналога `MainPhpWear*` (проверка/надевание ножа, парсинг ресурсов разделки).
- [x] Нет аналога `MainPhpRaz` (авто-нажатие `Разделать` по `fight_ty[9]`).
- [x] В `UserConfig` отсутствуют профильные поля `SkinAuto` и связанное состояние.
- [x] В `AppVars` отсутствуют C#-флаги `AutoSkinCheckUm/CheckKnife/CheckRes`, `AutoSkinArmedKnife`, `AutoSkinHand*`, `SkinUm`, `SkinRes`, `AutoSkinLastChecked`.
- [x] В `MainPhp.java` уже есть пригодные точки интеграции: `mainPhpFindInv(...)`, `mainPhpIsInv(...)`, `buildRedirectHtml(...)`, обработка `mainPhpFight(...)`.

## План портирования (этапы)

### Этап 0. Подготовка модели данных (profile/runtime)
- [x] Добавить в `UserConfig` профильный флаг `SkinAuto` с загрузкой/сохранением XML (1:1 с C#).
- [x] Добавить в `AppVars` runtime-поля AutoSkin (по аналогии с C# `AppVars.cs`).
- [x] Синхронизировать `AUTO_SKIN` (быстрая кнопка) с `Profile.SkinAuto` и начальной инициализацией флагов.

### Этап 1. Порт `TInvUd.cs` (первичный приоритет)
- [x] Создать Android-аналоги:
  - `ParsedDressed` (в `model` или `postfilter`),
  - методы `isWear1()`, `isWear2()`, `isWearKnife()`,
  - поля `valid/wid/vcod/empty1/empty2/inRightSlot/hand1/hand2/slist/dlist`.
- [x] Портировать парсинг `slots_inv(...)` и fallback `slots_pla(...)`.
- [x] Сохранить C#-семантику обновления глобального состояния (`AppVars.AutoFishHand*`, `AppVars.AutoSkinHand*`).
- [x] Вынести список ножей в константу и использовать единый список в AutoSkin-логике.

### Этап 2. Порт `MainPhpWear.cs` (первичный приоритет)
- [x] Реализовать `mainPhpArmedKnife(...)` (проверка надетого ножа через `ParsedDressed`).
- [x] Реализовать `mainPhpWearKnife(...)` (поиск ножа в инвентаре и редирект `Надеть`).
- [x] Реализовать `mainPhpGetSkinRes(...)` (парсинг охотничьих ресурсов, обновление статистики/чата).
- [x] Использовать существующие Android-хелперы `mainPhpFindInv(...)` и `buildRedirectHtml(...)` без дублирования.

### Этап 3. Порт `MainPhpRaz.cs` + интеграция в бой
- [x] Реализовать `mainPhpRaz(...)` (анализ `fight_ty[9]` и автопереход на `main.php?get_id=17...`).
- [x] Встроить вызов в `MainPhp.process(...)` в том же порядке, как в C# (до инвентарных преобразований).
- [x] Добавить фиксацию результата разделки в чат/статистику (через `mainPhpGetSkinRes` + `ChatStats` кг-агрегацию).

### Этап 4. Оркестрация флагов AutoSkin в `MainPhp`
- [x] Портировать последовательность C#:
  - `AutoSkinCheckUm` (читать умение охоты),
  - `AutoSkinCheckRes` (читать ресурсы),
  - `AutoSkinCheckKnife`/`AutoSkinArmedKnife` (проверка и надевание ножа).
- [x] Реализовать периодическую перепроверку ножа (аналог `FormMainTicks`: примерно раз в минуту).
- [x] Включать инициализацию флагов при старте/включении `AUTO_SKIN` (аналог `buttonAutoSkin_Click` и `FormMainInit`).

### Этап 5. Проверка и отладка
- [ ] Добавить подробный trace-лог префикс `AUTO_SKIN_TRACE` для всей цепочки:
  - состояние профиля/флагов,
  - найден ли нож/ссылка `Надеть`,
  - найдена ли разделка `fight_ty[9]`,
  - получен ли результат разделки.
- [ ] Проверить совместимость с текущей капчей завершения боя (`SkinBotCaptcha.har`).
- [ ] Подтвердить отсутствие регрессий авто-боя/авто-нападения.

## Критерии готовности (Definition of Done)
- [ ] При включенном `AUTO_SKIN` после поломки ножа автоматически надевается следующий нож.
- [ ] После победы по противнику с разделкой автоматически выполняется `Разделать`.
- [ ] Результат разделки попадает в чат и статистику ресурсов (кг) без ручных действий.
- [ ] В обычных боях (без разделки) авто-бой работает без изменений.
- [ ] Нет конфликтов с текущим завершением боя/капчей.

## Выполнено в этой итерации
- [x] Добавлен профильный флаг `SkinAuto` в `UserConfig` + чтение/запись XML-тега `<SkinAuto>`.
- [x] Добавлены runtime-поля AutoSkin в `AppVars` (`AutoSkinCheck*`, `SkinUm`, `AutoSkinHand*`, `SkinRes`, `AutoSkinLastChecked`).
- [x] `AutoFunctionsManager.setAutoSkinEnabled(...)` синхронизирует:
  - SharedPreferences (`auto_skin`),
  - `Profile.SkinAuto`,
  - runtime-флаги AutoSkin (инициализация как в C# `buttonAutoSkin`).
- [x] Добавлен `ParsedDressed.java` (порт `TInvUd.cs`) с C#-совместимыми методами:
  - `IsWear1()`, `IsWear2()`, `IsWearKnife()`.
- [x] Сборка проверена: `assembleDebug` — `BUILD SUCCESSFUL`.
- [x] Добавлены методы `mainPhpArmedKnife/mainPhpWearKnife/mainPhpGetSkinRes/mainPhpRaz` в `MainPhp`.
- [x] Встроена C#-последовательность AutoSkin в `MainPhp.process(...)` с `AUTO_SKIN_TRACE`.
- [x] Исправлен цикл `mselect=1`:
  - добавлен разбор умения охоты (`mainPhpProcessSkills`) и штатный сброс `AutoSkinCheckUm=false`,
  - добавлен защитный сброс при отсутствии skill-блока на странице умений (чтобы не зависать в redirect-loop).
- [x] При включении `AUTO_SKIN` добавлен форсированный переход на `main.php?get_id=56&act=10&go=inf`
  в `AutoFunctionsManager.triggerAutoSkinCharacterCheck()`, если `AUTO_FIGHT` уже был включен:
  это запускает проверку проф-инвентаря без ручного открытия раздела «Ваш персонаж».
- [x] Усилен `mainPhpRaz` по сравнению с HAR-референсом:
  - ссылка `get_id=17` теперь нормализуется к `http://neverlands.ru/...` (без `www`);
  - добавлен fallback-поиск прямой ссылки `main.php?get_id=17...` в HTML, если `fight_ty[9]` пустой;
  - добавлена повторная проверка разделки в `mainPhpFight(...)` перед авто-завершением боя.
