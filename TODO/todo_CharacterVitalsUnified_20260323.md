# TODO: Единая система учета параметров персонажа (HP/MA/Усталость) — 2026-03-23

## Контекст
- В кодовой базе были распределенные обновления `AppVars.CurHP/MaxHP/CurMA/MaxMA/Tied/PersIntHP/PersIntMA`.
- Источники данных приходят из разных каналов:
  - `main.php` (`ins_HP(...)`, блок "Усталость");
  - JS-bridge (`showHpMaTimers`, `SetCurrentTied`);
  - `pinfo.cgi` (`NeverApi.PinfoVitals`).
- Из-за дублирования логики появлялись гонки и рассинхрон между авто-функциями (Авто-Бой/Авто-Рыбалка/Авто-Клад/Навигатор).

## Цель
- Сделать единую точку записи параметров персонажа.
- Снизить дублирование и побочные эффекты.
- Оставить текущую бизнес-логику, но убрать разрозненные прямые записи в `AppVars`.

## Единый источник данных
- [x] Добавлен `CharacterVitalsManager`:
  - `app/src/main/java/ru/neverlands/abclient/manager/CharacterVitalsManager.java`
- [x] В менеджере реализованы:
  - `snapshot()`
  - `updateTied(...)`
  - `increaseTied(...)`
  - `updateFromInsHpSnapshot(...)`
  - `updateFromHpJs(...)`
  - `updateFromPinfo(...)`
  - `buildSyncMessage(...)`

## Матрица миграции (файл -> статус)
- [x] `WebAppInterface.java`
  - `SetCurrentTied(...)` переведен на `CharacterVitalsManager.updateTied(...)`
  - `showHpMaTimers(...)` переведен на `CharacterVitalsManager.updateFromHpJs(...)`
  - `MapText()` берет усталость из `CharacterVitalsManager.snapshot()`
- [x] `MainPhp.java`
  - `mainPhpInsHp(...)` переведен на `CharacterVitalsManager.updateFromInsHpSnapshot(...)`
  - `mainPhpUpdateTied(...)` переведен на `CharacterVitalsManager.updateTied(...)`
  - чтение усталости для ключевых UI-веток переключено на `snapshot().tied`
- [x] `MapAjax.java`
  - `too tired` ветка переведена на `updateTied(...)`
  - шаговая усталость переведена на `increaseTied(...)`
  - pinfo-синхронизация переведена на `updateFromPinfo(...)`
  - toast-сообщение синхронизации формируется через `buildSyncMessage(...)`
- [x] `AutoFunctionsManager.java`
  - `requestCharacterSyncAfterLogin()` переведен на `updateFromPinfo(...)`
  - `showCharacterSyncToast()` переведен на `snapshot()/buildSyncMessage(...)`

## Контроль дублирования
- [x] Поиск прямых присваиваний `AppVars.(CurHP|MaxHP|CurMA|MaxMA|Tied|PersIntHP|PersIntMA)=` за пределами `CharacterVitalsManager` — не найден.
- [ ] Проверить, что новые места записи не появились при последующих фичах (регулярная проверка `rg`).

## Наблюдаемость и отладка
- [x] В `CharacterVitalsManager` добавлены `VITALS_TRACE` логи с `source`.
- [ ] При необходимости добавить счетчики конфликтов источников (source A -> source B за короткое время).

## Критерии готовности
- [x] Все обновления vitals проходят через `CharacterVitalsManager`.
- [x] Авто-функции читают согласованные значения (через `snapshot` или через синхронизируемые `AppVars`).
- [ ] Пройти runtime-проверку логами на сценариях:
  - [ ] логин -> авто-синхронизация персонажа;
  - [ ] переходы навигатора + рост усталости;
  - [ ] подход к порогу блажа + fast action;
  - [ ] бой + восстановление HP/MA.

## Риски и примечания
- Исторически часть модулей читает `AppVars` напрямую. Это допустимо, если запись централизована в одном менеджере.
- Полный отказ от чтения `AppVars` в legacy-модулях может потребовать отдельного рефактора API (геттеры/наблюдатели), пока не является обязательным.

