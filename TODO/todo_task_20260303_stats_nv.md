# Задача: статистика денежного дропа (NV)

## Входные данные
- Лог: `Logs/logcat_runtime_20260303_13.txt`.
- Наблюдение по логу: в чат приходит строка вида `Результат обыска бота: Денежные средства «22 NV»`.

## План
- [x] Проверить формат денежного дропа в логе.
- [x] Обновить сбор статистики: суммировать `NV` отдельно от предметов.
- [x] Обновить отображение окна статистики.
- [x] Проверить сборку debug.

## Реализация
- [x] `app/src/main/java/ru/neverlands/abclient/utils/ChatStats.java`
  - добавлено поле `totalNv` и метод `getTotalNv()`;
  - в `addLoot(...)` денежные записи (`NNN NV`) исключаются из `lootLog` и суммируются в `totalNv`;
  - добавлено сохранение/загрузка `NV=` в дневной файл `Logs/YYYYMMDD_stat.txt`;
  - `reset()` теперь сбрасывает и `totalNv`.
- [x] `app/src/main/java/ru/neverlands/abclient/ui/QuickButtonsPanel.java`
  - в тексте статистики строка заменена на `Денежные средства (NV): <сумма>`;
  - раздел `Последние находки` оставлен для обычного предметного дропа.

## Проверка
- [x] Сборка: `.\gradlew.bat assembleDebug`
- [x] Результат: `BUILD SUCCESSFUL`.

## Дополнение: ресурсы в килограммах

### Входные данные
- Лог: `Logs/logcat_runtime_20260303_14.txt`.
- Наблюдение: строка лута вида `Ресурс «Клык» (0.55 кг)` должна суммироваться отдельно как ресурсный дроп.

### Реализация
- [x] `app/src/main/java/ru/neverlands/abclient/utils/ChatFilter.java`
  - обновлён парсер лута: теперь сохраняет вес ресурса вместе с названием (`Название (x.xx кг)`).
- [x] `app/src/main/java/ru/neverlands/abclient/utils/ChatStats.java`
  - добавлены поля `totalResourceKg` и `resourceKgByType` (динамическая карта типов ресурсов);
  - добавлен парсер ресурсной записи `parseResourceKg(...)`;
  - ресурсный дроп `(x.xx кг)` исключается из `lootLog`, суммируется в общий вес и в разбивку по типам;
  - добавлено сохранение/загрузка в дневной файл: `KG_TOTAL=` и `KG_ITEM=<name>\t<kg>`.
- [x] `app/src/main/java/ru/neverlands/abclient/ui/QuickButtonsPanel.java`
  - в окне статистики добавлены строки `Ресурсы (кг)` и `Ресурсы по типам`.

### Проверка
- [x] Сборка: `.\gradlew.bat assembleDebug`
- [x] Результат: `BUILD SUCCESSFUL`.
