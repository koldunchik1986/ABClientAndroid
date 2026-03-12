# TODO: FormSettingsGeneral (вкладка "Общие") — 1:1 порт (2026-03-12)

## Контекст
- Фокус только на пунктах, которые запросил пользователь из ПК-версии C#:
  - `checkboxRazdChatReport`
  - `groupBoxDoAutoDrinkBlaz`
  - `comboBoxDoAutoDrinkBlaz`
  - `checkBoxDoAutoDrinkBlaz`
  - `groupBox24` (`checkDoInvPackDolg`, `checkDoInvPack`, `checkDoInvSort`)
- Настройки авто-рыбалки и прочие авто-функции, вынесенные в отдельные экраны, здесь не дублируем.

## Эталон C# (что именно должно быть)
- `checkboxRazdChatReport`:
  - UI-текст: "Выводить результаты разделки в чат".
  - Профиль: `AppVars.Profile.RazdChatReport`.
  - Использование: `FormMainTicks.cs` публикует результат разделки в чат только при `true`.
- `groupBoxDoAutoDrinkBlaz`:
  - `checkBoxDoAutoDrinkBlaz` — включение авто-питья блажа.
  - `textBoxAutoDrinkBlazTied` — порог усталости.
  - `comboBoxDoAutoDrinkBlaz` — порядок поиска:
    - `0`: "ищем зелье, потом эликсир"
    - `1`: "ищем эликсир, потом зелье"
  - Профиль: `DoAutoDrinkBlaz`, `AutoDrinkBlazTied`, `AutoDrinkBlazOrder`.
  - Runtime: `PostFilter/MainPhp.cs` (ветка `DoAutoDrinkBlaz` + `AppVars.Tied` + `NeverTimer`).
- `groupBox24` (Инвентарь):
  - `DoInvPack`, `DoInvPackDolg`, `DoInvSort`.
  - Уже частично есть в Android runtime, нужно довести UI и связку 1:1.

## Текущее состояние Android (после этапа A)
- `[x]` `DoInvPack`, `DoInvPackDolg`, `DoInvSort` есть в `UserConfig` и реально используются в `MainPhp/InvEntry`.
- `[x]` UI-настройки для `DoInvPack*` и `DoInvSort` добавлены в "Общие настройки клиента".
- `[x]` `RazdChatReport` добавлен в `UserConfig` + UI.
- `[x]` `DoAutoDrinkBlaz`, `AutoDrinkBlazTied`, `AutoDrinkBlazOrder` добавлены в `UserConfig` + UI.
- `[ ]` Runtime-ветка `DoAutoDrinkBlaz` в Android `MainPhp` отсутствует.

## План реализации
1. `[x]` Добавить профильные поля в `UserConfig`:
   - `RazdChatReport` (default `false`)
   - `DoAutoDrinkBlaz` (default `false`)
   - `AutoDrinkBlazTied` (default `84`)
   - `AutoDrinkBlazOrder` (default `0`, диапазон `0..1`)
2. `[x]` Добавить сериализацию/десериализацию:
   - C#-совместимые узлы `autodrinkblaz` + `autodrinkblazorder`
   - хранение/чтение `razdchatreport` (в текущей Android-схеме профиля)
3. `[x]` Расширить `root_preferences.xml` (раздел "Основные настройки"):
   - switch `show_razd_chat_report`
   - группа авто-блажа:
     - switch `do_auto_drink_blaz`
     - edit `auto_drink_blaz_tied`
     - list `auto_drink_blaz_order` (2 варианта как в C#)
   - блок инвентаря:
     - switch `do_inv_pack`
     - switch `do_inv_pack_dolg`
     - switch `do_inv_sort`
4. `[x]` Подключить обработчики в `SettingsActivity`:
   - загрузка значений из `AppVars.Profile`
   - запись в профиль + `save(requireContext())`
5. `[x]` Подключить `RazdChatReport` в runtime:
   - вывод "Результат разделки" в чат только при включенном флаге.
6. `[ ]` Отдельным этапом портировать runtime-ветку `DoAutoDrinkBlaz` из C# `MainPhp.cs` (без упрощений).

## Что делаем в этой итерации
- `[x]` Этап A: Профиль + UI + `RazdChatReport` gating.
- `[-]` Этап B: Полный runtime `DoAutoDrinkBlaz` (следующим шагом после проверки этапа A).
