# План портирования MainPhpFish.cs (1:1 с ПК C#)

## 1. Назначение файла в C#

`ABClient/PostFilter/MainPhpFish.cs` закрывает две ключевые части авто-рыбалки:
- `MainPhpAutoFishPrepare(...)` — подготовка шага рыбалки (параметры, приманка, ссылка действия, капча);
- `MainPhpFishReport(...)` — итоговый отчет и обновление runtime-полей после ловли.

## 2. Что уже есть в Android (факт)

- [x] Профильные поля авто-рыбалки (`AutoFish`, `FishAutoWear`, `FishHandOne/Two`, `FishEnabledPrims`, `FishUm`) реализованы.
- [x] В `ParsedDressed` есть C#-совместимая проверка двух рыболовных слотов.
- [x] Есть quick-кнопка `AUTO_FISH` + long-press настройки.
- [x] В `MainPhp.java` реализован `mainPhpAutoFishPrepare(...)`.
- [x] В `MainPhp.java` встроен fish-flow:
  - [x] проверка умения (`AutoFishCheckUm`);
  - [x] проверка/надевание снастей (`AutoFishCheckUd`/`AutoFishWearUd`);
  - [x] переход в карту и вызов рыбалки;
  - [x] подготовка `FightLink` для рыболовного шага.
- [x] Добавлена fish captcha-ветка в `MainPhp` с показом диалога и anti-duplicate.
- [x] Рыболовный отчет вынесен в `FishAjaxPhp.java` (по месту обработки `fish_ajax.php`).

## 3. Поведение C# (что перенесено по смыслу)

### `MainPhpAutoFishPrepare(...)`
- читает `get_id/act/vcode/lakeid`;
- сохраняет массу инвентаря;
- выбирает допустимую приманку из `FishEnabledPrims`;
- заполняет `FightLink`;
- при капче формирует ссылку с `code=????` и запускает ручной ввод через диалог.

### `MainPhpFishReport(...)` (в Android — через `FishAjaxPhp`)
- парсит рыбу/клёв/улов/рост умения;
- обновляет `FishUm`, `AutoFishNV`, `AutoFishMassa`;
- формирует расширенный отчет;
- переводит флаги на следующий цикл (`AutoFishCheckUd=true`, `AutoFishWearUd=false`).

## 4. Gap и остаток работ

- [ ] Довести рыбацкую captcha-ветку до полного паритета по реальным сценариям из логов.
- [ ] Прогнать edge-кейсы (пустой ответ, нестандартный формат `fish_ajax`, быстрые повторные challenge).
- [ ] Финально сверить шаги с HAR (`DressFishing.har`, `FishingCaptcha.har`) на 1:1.

## 5. План реализации (остаток)

- [x] Добавить в Android профиль `FishEnabledPrims` + load/save.
- [x] Добавить битмаску `Prims` с C#-совместимыми значениями.
- [x] Портировать `MainPhpAutoFishPrepare(...)`.
- [x] Включить fish-flow в основной `main.php` pipeline.
- [x] Реализовать fish captcha-flow (показ диалога + submit).
- [x] Доработать `AutoFunctionsManager.setAutoFishEnabled(true)` под C# runtime-init.
- [x] Добавить long-press настройки `AUTO_FISH`.
- [ ] Закрыть финальную валидацию по runtime-логам.

## 6. Статус

- [x] Анализ C# и Android по `MainPhpFish` выполнен.
- [x] Основное портирование в Java выполнено.
- [ ] Финальная проверка 1:1 на боевых логах не завершена.

