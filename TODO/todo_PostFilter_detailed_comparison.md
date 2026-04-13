# Детальное сравнение PostFilter: C# → Android

**Обновлено**: 2026-04-12

---

## Легенда статусов

- `[+]` FULL — Полностью портировано, логика 1:1 с C#
- `[~]` PARTIAL — Частично портировано (основное работает, есть пробелы)
- `[s]` STUB — Заглушка (файл существует, но возвращает данные без обработки)
- `[=]` STUB=NOOP — Заглушка, но C# тоже no-op (поведение совпадает)
- `[ ]` MISSING — Отсутствует в Java

---

## Полностью портированные файлы `[+]`

| C# файл | Java файл | Ключевые переменные/методы | Комментарий |
| ------- | --------- | --------------------------- | ----------- |
| `ChListJs.cs` | `ChListJs.java` | `chListJsFilter()` | Полная замена document.location на AndroidBridge |
| `ShopEntry.cs` | `ShopEntry.java` | `name`, `price`, `count`, `image` | Модель предмета магазина |
| `FightJs.cs` | `FightJs.java` | Замены fight_v*.js строк | Строковые замены + WebAppInterface интеграция |
| `FishAjaxPhp.cs` | `FishAjaxPhp.java` | `processFishAct1()`, `processFishAct2()`, `mainPhpAutoFishPrepareFromLakeAndroid()` | Полный цикл: парсинг улова, капча, авто-цикл, проверка удочек |
| `GameJs.cs` | `GameJs.java` | Строковые замены game.js | Полностью портировано |
| `json2.js` | `assets/js/json2.js` | — | Библиотека без изменений |

---

## Частично портированные файлы `[~]`

### MainPhp.cs → MainPhp.java

**Портированные подсистемы:**
- `mainPhp()` — главный роутер (полностью)
- `mainPhpVCode()` — парсинг vcode (полностью, через SessionManager)
- `mainPhpInv()` — инвентарь с группировкой (полностью)
- `mainPhpFast()` — быстрые действия (почти полностью)
- `mainPhpFight()` / `mainPhpFightEnd()` — бой через LezFight (полностью)
- `mainPhpInsHp()` — парсинг HP/MA (полностью)
- `mainPhpRaz()` — разделка (полностью)
- `mainPhpWear()` — надевание ножа/свитка (частично)

**НЕ портированные подсистемы (отсутствуют в Java):**

| C# partial class | Функциональность | Ключевые переменные C# | Ключевые методы C# | Влияние |
| ---------------- | ---------------- | ---------------------- | ------------------ | ------- |
| `MainPhpAutoCure.cs` | Авто-лечение через врача | `AppVars.CureNeed`, `CureNick`, `CureTravm`, `CureNickDone`, `CurePauseNonCombatAutoFunctions` | `mainPhpAutoCure()` | **Высокое** — авто-лечение не работает |
| `MainPhpCure.cs` | Лечение из врача | `CureNick`, `CureTravm` | `mainPhpCure()` | **Высокое** — нет лечения |
| `MainPhpDrink.cs` | Питьё зелий/напитков | `AppVars.NeverTimer`, `TimerPauseNonCombatAutoFunctions` | `mainPhpDrink()` | **Высокое** — авто-питьё не работает |
| `MainPhpDrinkHpMa.cs` | Питьё HP/MA зелий | `AppVars.CurHP`, `CurMA`, `MaxHP`, `MaxMA` | `mainPhpDrinkHpMa()` | **Высокое** — авто-питьё HP/MA |
| `MainPhpFish.cs` | Авто-рыбалка из main.php | `AppVars.AutoFishDrink`, `AutoFishDrinkOnce`, `Tied`, `ContentLakeHtml` | `mainPhpFish()` | **Высокое** — нет триггера рыбалки из main |
| `MainPhpTied.cs` | Усталость персонажа | `AppVars.Tied`, `AutoFishDrink` | `mainPhpTied()` | **Среднее** — усталость парсится, но авто-действие нет |
| `MainPhpDarkFog.cs` | Абилка "Туман" | `AppVars.FastNeedAbilDarkFog` | `mainPhpDarkFog()` | **Низкое** — редкая абилка |
| `MainPhpDarkTeleport.cs` | Абилка "Телепорт" | `AppVars.FastNeedAbilDarkTeleport` | `mainPhpDarkTeleport()` | **Низкое** — редкая абилка |
| `MainPhpRob.cs` | Воровство | — | `mainPhpRob()` | **Низкое** |
| `MainPhpRobinHood.cs` | Логика "Робин Гуд" | — | `mainPhpRobinHood()` | **Низкое** |
| `MainPhpWearComplect.cs` | Одевание комплектов | `AppVars.WearComplect` | `mainPhpWearComplect()` | **Среднее** — надевание комплетов не работает |
| `MainPhpWtime.cs` | Обработка wtime | — | `mainPhpWtime()` | **Низкое** |

### Filter.cs → Filter.java

**Портировано:**
- Маршрутизация URL → фильтр (полностью)
- `preProcess()` — заглушка (в C# тоже почти no-op)
- Комнатная обработка `ch.php?lo=` через RoomManager

**НЕ портировано:**
- Некоторые C#-специфичные трансформации

### CastleJs.cs → CastleJs.java

**Портировано:** Частичная замена document.location
**НЕ портировано:** prepend json2 (в Android не нужно — WebView имеет нативный JSON)

### MainPhpWear.cs (частично в MainPhp.java)

**Портировано:** `mainPhpArmedKnife()`, `mainPhpWearKnife()`, `mainPhpGetSkinRes()`
**НЕ портировано:** Полная логика надевания комплектов (WearComplect)

---

## Заглушки `[s]` — файл существует, но НЕ обрабатывает данные

| C# файл | Java файл | Что делает C# | Что делает Java | Приоритет портирования |
| ------- | --------- | ------------- | -------------- | ---------------------- |
| `ArenaJs.cs` | `ArenaJs.java` | Загружает ресурс, заменяет строки арены | Возвращает пустой массив | P2 |
| `BuildingJs.cs` | `BuildingJs.java` | Удаляет `clr_chat()` при ChatKeepMoving | Заглушка | P2 |
| `ButPhp.cs` | `ButPhp.java` | Парсит время, smile_open, кнопки (~50 строк) | Заглушка | **P1** — кнопки чата |
| `ChMsgJs.cs` | `ChMsgJs.java` | Инжектирует ChatFilter, ChatUpdated, SPAN alt | Заглушка | **P0** — фильтр чата |
| `ChRoomPhp.cs` | `ChRoomPhp.java` | Обработка ch.php?lo= | Заглушка (RoomManager обрабатывает отдельно) | P2 |
| `CounterJs.cs` | `CounterJs.java` | Возвращает no-op функцию удаления счетчиков | Возвращает пустой массив | P2 |
| `ForumTopicJs.cs` | `ForumTopicJs.java` | Удаляет аватары при LightForum | Заглушка | P2 |
| `GamePhp.cs` | `GamePhp.java` | Авто-ввод пароля, удаление DOCTYPE | Заглушка | P2 |
| `HpJs.cs` | `HpJs.java` | Заменяет HP/MA на ShowHpMaTimers | Заглушка | **P1** — таймеры HP/MA |
| `HpmpJs.cs` | `HpmpJs.java` | Генерирует JS HP/MA таймер (~50 строк) | Возвращает пустой массив | **P1** — таймеры HP/MA |
| `IndexCgi.cs` | `IndexCgi.java` | Определяет форму логина, ошибки, автологин | Заглушка | P2 (LoginActivity заменяет) |
| `MapAjax.cs` | `MapAjax.java` | Парсит координаты, регионы, обновляет позицию | Заглушка | **P0** — навигация |
| `MapJs.cs` | `MapJs.java` | Возвращает кастомный map ресурс | Заглушка | **P1** — карта |
| `MsgPhp.cs` | `MsgPhp.java` | Добавляет сохранённый чат при ChatKeepGame | Заглушка | P2 |
| `NlPinfo.cs` | `NlPinfo.java` | Заменяет alt на InfoToolTip | Заглушка | P2 |
| `OutpostJs.cs` | `OutpostJs.java` | Prepends json2 | Заглушка | P2 |
| `PvJs.cs` | `PvJs.java` | Заменяет '%clan% ' на '%clan%' | Заглушка | P2 |
| `RouletteAjaxPhp.cs` | `RouletteAjaxPhp.java` | Парсит результаты рулетки | Заглушка | P2 |
| `ShopAjaxPhp.cs` | `ShopAjaxPhp.java` | Парсит магазин, группирует, "Продать всё" | Заглушка | **P1** — магазин |
| `ShopJs.cs` | `ShopJs.java` | Инжектирует BulkSellOldArg1/Arg2 | Заглушка | P2 |
| `SvitokJs.cs` | `SvitokJs.java` | Инжектирует TraceDrinkPotion | Заглушка | **P1** — зелья |
| `TarenaJs.cs` | `TarenaJs.java` | Prepends json2 | Заглушка | P2 |
| `TopJs.cs` | `TopJs.java` | Обрезает функцию, возвращает no-op | Заглушка | P2 |
| `TowerJs.cs` | `TowerJs.java` | Prepends json2 | Заглушка | P2 |
| `TradePhp.cs` | `TradePhp.java` | Парсит торговлю, считает стоимость | Заглушка | P2 |

---

## Заглушки = C# no-op `[=]` — не требуют портирования

| C# файл | Java файл | Комментарий |
| ------- | --------- | ----------- |
| `ChZero.cs` | — | C# логика закомментирована |
| `LogsJs.cs` | — | C# замены закомментированы |
| `MapActAjaxPhp.cs` | `MapActAjaxPhp.java` | C# тоже no-op |
| `Pinfo.cs` | — | C# активная логика ≈ no-op |
| `PinfonewJs.cs` | — | C# логика закомментирована |
| `SlotsJs.cs` | — | C# тоже no-op |

---

## Полностью отсутствующие в Java `[ ]`

| C# файл | Функциональность | Ключевые переменные C# | Приоритет |
| ------- | ---------------- | ---------------------- | --------- |
| `MainPhpAutoCure.cs` | Авто-лечение | `CureNeed`, `CureNick`, `CureTravm` | **P0** |
| `MainPhpCityNavigation.cs` | Навигация по городу | `AutoMovingCityGate` | **P0** |
| `MainPhpCure.cs` | Лечение у врача | `CureNick`, `CureTravm` | **P0** |
| `MainPhpDarkFog.cs` | Абилка тумана | `FastNeedAbilDarkFog` | P2 |
| `MainPhpDarkTeleport.cs` | Абилка телепорта | `FastNeedAbilDarkTeleport` | P2 |
| `MainPhpDrink.cs` | Питьё зелий | `NeverTimer`, `TimerPauseNonCombatAutoFunctions` | **P0** |
| `MainPhpDrinkHpMa.cs` | Питьё HP/MA | `CurHP`, `CurMA`, `MaxHP`, `MaxMA` | **P0** |
| `MainPhpFish.cs` | Авто-рыбалка из main | `AutoFishDrink`, `ContentLakeHtml` | **P0** |
| `MainPhpRob.cs` | Воровство | — | P2 |
| `MainPhpRobinHood.cs` | Робин Гуд | — | P2 |
| `MainPhpTied.cs` | Усталость | `Tied`, `AutoFishDrink` | **P1** |
| `MainPhpWearComplect.cs` | Надевание комплектов | `WearComplect` | **P1** |
| `MainPhpWtime.cs` | Обработка wtime | — | P2 |
| `TeleportAjax.cs` | Телепорт AJAX | — | P2 |

---

## Рекомендации по портированию (по приоритету)

### P0 — Критичные (блокируют gameplay)

1. **MainPhpDrink.cs** → добавить `mainPhpDrink()` в MainPhp.java
   - Зависимости: `AppVars.NeverTimer`, `TimerPauseNonCombatAutoFunctions`
   - C# логика: проверка NeverTimer, питьё зелья, обновление таймеров
   - Переменные: `AppVars.CurHP/MaxHP/CurMA/MaxMA`, `UserConfig.LezDoDrinkHp/LezDrinkHp`

2. **MainPhpDrinkHpMa.cs** → добавить `mainPhpDrinkHpMa()` в MainPhp.java
   - Зависимости: переменные HP/MA из `mainPhpInsHp()`
   - C# логика: автоматическое питьё зелий при низких HP/MA

3. **MainPhpFish.cs** → добавить `mainPhpFish()` в MainPhp.java
   - Зависимости: `FishAjaxPhp.java` (уже портирован!), `AppVars.ContentLakeHtml`
   - C# логика: триггер авторыбалки из main.php, авто-питьё при усталости
   - ВАЖНО: FishAjaxPhp.java уже портирован — нужно только вызвать его из MainPhp

4. **MainPhpAutoCure.cs + MainPhpCure.cs** → добавить `mainPhpAutoCure()` + `mainPhpCure()`
   - Зависимости: `AppVars.CureNeed/CureNick/CureTravm`, `RoomManager`
   - C# логика: запрос лечения, отправка формы врача

5. **MainPhpCityNavigation.cs** → добавить навигацию
   - Зависимости: `AppVars.AutoMovingCityGate`, `NavHelper`
   - C# логика: навигация через ворота города

6. **MapAjax.cs** → реализовать парсинг координат
   - Зависимости: `AppVars.myLocOld/myCoordOld`, `ExtMap`
   - C# логика: парсинг `map_ajax.php` ответа, обновление позиции игрока

7. **ChMsgJs.cs** → реализовать фильтр сообщений чата
   - Зависимости: `ChatFilter`, `ChatStats`
   - C# логика: инъекция ChatFilter, ChatUpdated, SPAN alt для приватных

### P1 — Важные (ухудшают UX)

8. **MainPhpWearComplect.cs** → добавить надевание комплектов
   - Зависимости: `AppVars.WearComplect`, `InventoryParser`
   - C# логика: поиск комплекта в инвентаре, отправка формы надевания

9. **MainPhpTied.cs** → добавить обработку усталости
   - Зависимости: `AppVars.Tied`, `AutoFishDrink`
   - C# логика: парсинг усталости, триггер авто-питья

10. **HpJs.cs + HpmpJs.cs** → таймеры HP/MA
    - Зависимости: `AppVars.PersIntHP/PersIntMA`
    - C# логика: замена HP/MA на ShowHpMaTimers JS

11. **ButPhp.cs** → кнопки чата
    - Зависимости: `AppVars.Chat`
    - C# логика: парсинг кнопок, smile_open, время

12. **ShopAjaxPhp.cs** → магазин
    - Зависимости: `ShopEntry`, `AppVars.ShopList`
    - C# логика: парсинг предметов, группировка, кнопка "Продать всё"

13. **MapJs.cs** → кастомная карта
    - Зависимости: `ExtMap`
    - C# логика: замена стандартного map.js на кастомный

14. **SvitokJs.cs** → зелья/свитки
    - Зависимости: `AppVars.NeverTimer`
    - C# логика: инъекция TraceDrinkPotion

### P2 — Желательные

15-25. Остальные заглушки: ArenaJs, BuildingJs, ForumTopicJs, GamePhp, NlPinfo, OutpostJs, PvJs, RouletteAjaxPhp, ShopJs, TarenaJs, TopJs, TowerJs, TradePhp, CounterJs

---

## Сводная статистика PostFilter

| Категория | Количество |
| --------- | ---------- |
| `[+]` Полностью портировано | 6 |
| `[~]` Частично портировано | 6 |
| `[=]` Заглушка = C# no-op | 6 |
| `[s]` Заглушка (нужно портировать) | 25 |
| `[ ]` Отсутствует в Java | 14 |
| **Итого требуют портирования** | **45** |