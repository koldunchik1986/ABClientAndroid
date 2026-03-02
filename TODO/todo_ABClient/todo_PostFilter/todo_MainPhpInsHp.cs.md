
## План портирования `MainPhpInsHp.cs` (C# → Android)

### 1) Назначение исходника C#

`ABClient/PostFilter/MainPhpInsHp.cs` читает из HTML вызов:

`ins_HP(curh,maxh,curm,maxm,hp_int,ma_int)`

и сохраняет:
- `par[4]` → `AppVars.Profile.Pers.IntHP`
- `par[5]` → `AppVars.Profile.Pers.IntMA`

Это не влияет на рендер HTML, но влияет на расчёт таймеров восстановления после боя.

### 2) Что реализовано в Android

- [x] Портирован парсер `MainPhpInsHp` в `MainPhp.java`:
  - `mainPhpInsHp(String html)` ищет `ins_HP(`, извлекает 6 аргументов, парсит `hp_int/ma_int`.
  - `tryParseDoubleInvariant(String raw)` делает инвариант-парсинг (аналог `InvariantCulture`).
- [x] Интеграция в поток `main.php`:
  - вызов `mainPhpInsHp(html)` добавлен в `MainPhp.process(...)` сразу после `removeDoctype`.
- [x] Запись значений в runtime:
  - `AppVars.PersIntHP`
  - `AppVars.PersIntMA`

### 3) Зависимости и контракты

- **Источник данных:** HTML верхнего/основного фрейма `main.php` с `ins_HP(...)`.
- **Потребитель данных:** `LezFight.calcRestoreAfterBoiReadyAtMs()`:
  - состояние `Restoring` в `MainPhp.mainPhpFight(...)`.
- **Совместимость с C#:**
  - парсинг только по `ins_HP(...)`;
  - использование 5/6 параметров (индексы 4/5) как в эталоне.

### 4) Ограничения / принятые решения

- Ошибки парсинга не останавливают фильтр: только debug-лог и продолжение обработки.
- HTML не модифицируется.
- Формат `ins_HP(...)` обязателен; если его нет на странице, значения остаются предыдущими.

### 5) Файлы Android, затронутые портом

- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

### 6) Статус

- [x] Портирование `MainPhpInsHp.cs` в Android выполнено.
- [x] Интеграция в основной pipeline `MainPhp.process(...)` выполнена.
- [x] Комментарии по зависимостям и назначению добавлены в код.
