# Анализ папки PostFilter

В этой папке содержатся классы-фильтры, которые изменяют HTML/JS ответы от сервера перед их отображением в WebView.

## Список файлов и статус анализа / реализации

**Легенда статуса реализации:**
- `[+]` — Полностью портировано (FULL)
- `[~]` — Частично портировано (PARTIAL)
- `[s]` — Заглушка в Java (STUB) — файл существует, но возвращает данные без изменений
- `[=]` — Заглушка, но C# тоже no-op (логика закомментирована в C#, поведение совпадает)
- `[ ]` — Отсутствует в Java (MISSING)

| Файл                      | Описание                                               | Анализ | Реализация | Примечание |
| ------------------------- | ------------------------------------------------------ | ------ | ---------- | ---------- |
| `Filter.cs`               | Главный класс-маршрутизатор фильтров                   | `[x]`  | `[~]`      | Роутинг портирован. `preProcess` — заглушка (в C# тоже почти no-op) |
| `ChListJs.cs`             | Фильтр для `ch_list.js` (список пользователей)         | `[x]`  | `[+]`      | Полностью портирован для Android (через AndroidBridge) |
| `ShopEntry.cs`            | Класс-сущность предмета в магазине                     | `[x]`  | `[+]`      | Полностью портирован |
| `MainPhp.cs`              | Основной фильтр для `main.php`                         | `[x]`  | `[~]`      | Частично: removeDoctype, vcode, inv. Не портировано: fight, wear, cure, fish и др. |
| `CastleJs.cs`             | Фильтр для `castle.js`                                | `[x]`  | `[~]`      | Частично: замена document.location. C# также prepends json2 |
| `ArenaJs.cs`              | Фильтр для `arena.js`                                 | `[x]`  | `[s]`      | Java возвращает пустой массив. C# загружает ресурс и заменяет строки |
| `BuildingJs.cs`           | Фильтр для `building.js`                              | `[x]`  | `[s]`      | Заглушка. C# удаляет clr_chat() при ChatKeepMoving |
| `ButPhp.cs`               | Фильтр для `but.php` (кнопки чата)                    | `[x]`  | `[s]`      | Заглушка. C# парсит время, smile_open, кнопки (~50 строк) |
| `ChMsgJs.cs`              | Фильтр для `ch_msg_v01.js` (сообщения чата)           | `[x]`  | `[s]`      | Заглушка. C# инжектирует ChatFilter, ChatUpdated, SPAN alt |
| `ChRoomPhp.cs`            | Фильтр для `ch.php?lo=` (комнаты чата)                | `[x]`  | `[s]`      | Заглушка. Но /ch.php?lo=1 обрабатывается через RoomManager в Filter.java |
| `CounterJs.cs`            | Фильтр для удаления счетчиков                          | `[x]`  | `[s]`      | Java возвращает пустой массив. C# возвращает no-op функцию |
| `FightJs.cs`              | Фильтр для `fight_v*.js`                              | `[x]`  | `[s]`      | **КРИТИЧНО.** Заглушка. C# инжектирует autoselect/autoturn/autoboi (~100+ строк) |
| `FishAjaxPhp.cs`          | Фильтр для `fish_ajax.php`                            | `[x]`  | `[s]`      | Заглушка. C# парсит результаты рыбалки, управляет таймерами |
| `ForumTopicJs.cs`         | Фильтр для `forum_topic.js`                           | `[x]`  | `[s]`      | Заглушка. C# удаляет аватары при LightForum |
| `GameJs.cs`               | Фильтр для `game.js`                                  | `[x]`  | `[s]`      | **КРИТИЧНО.** Заглушка. C# модифицирует chat size, speed, фреймы |
| `GamePhp.cs`              | Фильтр для `game.php`                                 | `[x]`  | `[s]`      | Заглушка. C# авто-ввод пароля, удаление DOCTYPE |
| `HpJs.cs`                 | Фильтр для `hp.js`                                    | `[x]`  | `[s]`      | Заглушка. C# заменяет HP/MA на ShowHpMaTimers |
| `HpmpJs.cs`               | Фильтр для `hpmp.js`                                  | `[x]`  | `[s]`      | Java возвращает пустой массив. C# генерирует JS HP/MA таймер (~50 строк) |
| `IndexCgi.cs`             | Фильтр для `index.cgi` (страница логина)              | `[x]`  | `[s]`      | Заглушка. C# определяет форму логина, ошибки, автологин |
| `MapAjax.cs`              | Фильтр для `map_ajax`                                 | `[x]`  | `[s]`      | Заглушка. C# парсит координаты, регионы, обновляет позицию |
| `MapJs.cs`                | Фильтр для `map.js`                                   | `[x]`  | `[s]`      | Заглушка. C# возвращает кастомный map ресурс |
| `MsgPhp.cs`               | Фильтр для `msg.php`                                  | `[x]`  | `[s]`      | Заглушка. C# добавляет сохранённый чат при ChatKeepGame |
| `NlPinfo.cs`              | Фильтр для `nl_pinfo.cgi`                             | `[x]`  | `[s]`      | Заглушка. C# заменяет alt на InfoToolTip |
| `OutpostJs.cs`            | Фильтр для `outpost.js`                               | `[x]`  | `[s]`      | Заглушка. C# prepends json2 |
| `PvJs.cs`                 | Фильтр для `pv.js`                                    | `[x]`  | `[s]`      | Заглушка. C# заменяет '%clan% ' на '%clan%' |
| `RouletteAjaxPhp.cs`      | Фильтр для `roulette_ajax.php`                        | `[x]`  | `[s]`      | Заглушка. C# парсит результаты рулетки |
| `ShopAjaxPhp.cs`          | Фильтр для `shop_ajax.php`                            | `[x]`  | `[s]`      | Заглушка. C# парсит магазин, группирует, "Продать всё" |
| `ShopJs.cs`               | Фильтр для `shop.js`                                  | `[x]`  | `[s]`      | Заглушка. C# инжектирует BulkSellOldArg1/Arg2 |
| `SvitokJs.cs`             | Фильтр для `svitok.js`                                | `[x]`  | `[s]`      | Заглушка. C# инжектирует TraceDrinkPotion |
| `TarenaJs.cs`             | Фильтр для `tarena.js`                                | `[x]`  | `[s]`      | Заглушка. C# prepends json2 |
| `TopJs.cs`                | Фильтр для `top.js`                                   | `[x]`  | `[s]`      | Заглушка. C# обрезает функцию, возвращает no-op |
| `TowerJs.cs`              | Фильтр для `tower.js`                                 | `[x]`  | `[s]`      | Заглушка. C# prepends json2 |
| `TradePhp.cs`             | Фильтр для `trade.php` (подтверждение торга)          | `[x]`  | `[s]`      | Заглушка. C# парсит торговлю, считает стоимость |
| `ChZero.cs`               | Фильтр для `ch.php?0`                                 | `[x]`  | `[=]`      | Заглушка. C# тоже фактически no-op (логика закомментирована) |
| `LogsJs.cs`               | Фильтр для `logs.js`                                  | `[x]`  | `[=]`      | Заглушка. C# тоже no-op (замены закомментированы) |
| `MapActAjaxPhp.cs`        | Фильтр для `map_act_ajax.php`                         | `[x]`  | `[=]`      | Заглушка. C# тоже no-op |
| `Pinfo.cs`                | Фильтр для `pinfo.cgi`                                | `[x]`  | `[=]`      | Заглушка. C# активная логика — тоже примерно no-op |
| `PinfonewJs.cs`           | Фильтр для `pinfonew.js`                              | `[x]`  | `[=]`      | Заглушка. C# логика закомментирована |
| `SlotsJs.cs`              | Фильтр для `slots.js`                                 | `[x]`  | `[=]`      | Заглушка. C# тоже no-op (логика закомментирована) |
| `MainPhpAutoCure.cs`      | Часть `MainPhp`: логика автолечения                    | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpCityNavigation.cs`| Часть `MainPhp`: навигация по городу                   | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpCure.cs`          | Часть `MainPhp`: лечение                               | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpDarkFog.cs`       | Часть `MainPhp`: абилка тумана                         | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpDarkTeleport.cs`  | Часть `MainPhp`: абилка телепорта                      | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpDrink.cs`         | Часть `MainPhp`: питье                                 | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpDrinkHpMa.cs`     | Часть `MainPhp`: питье HP/MP                           | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpFast.cs`          | Часть `MainPhp`: быстрые действия                      | `[x]`  | `[~]`      | `FastActionManager.processMainPhp()` — основные парсеры. Нет: Elixir, Totem, AsyncPolling |
| `MainPhpFight.cs`         | Часть `MainPhp`: логика боя                            | `[x]`  | `[ ]`      | Нет Java-файла. Есть TODO-заглушка в MainPhp.java |
| `MainPhpFish.cs`          | Часть `MainPhp`: логика рыбалки                        | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpInsHp.cs`         | Часть `MainPhp`: парсинг HP                            | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpInv.cs`           | Часть `MainPhp`: инвентарь                             | `[x]`  | `[~]`      | Частично портировано в MainPhp.java (метод mainPhpInv) |
| `MainPhpRaz.cs`           | Часть `MainPhp`: разделка                              | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpRob.cs`           | Часть `MainPhp`: воровство                             | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpRobinHood.cs`     | Часть `MainPhp`: логика "Робин Гуда"                 | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpTied.cs`          | Часть `MainPhp`: усталость                             | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpWear.cs`          | Часть `MainPhp`: одевание                              | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpWearComplect.cs`  | Часть `MainPhp`: одевание комплектов                   | `[x]`  | `[ ]`      | Нет Java-файла |
| `MainPhpWtime.cs`         | Часть `MainPhp`: обработка wtime                       | `[x]`  | `[ ]`      | Нет Java-файла |
| `TeleportAjax.cs`         | Фильтр для `teleport_ajax`                             | `[x]`  | `[ ]`      | Нет Java-файла |
| `json2.js`                | Библиотека JSON2                                       | `[x]`  | `[+]`      | Присутствует в assets/js/json2.js |

## Сводная статистика

| Категория | Количество |
| --------- | ---------- |
| `[+]` Полностью портировано | 3 (ChListJs, ShopEntry, json2.js) |
| `[~]` Частично портировано | 4 (Filter, MainPhp, CastleJs, MainPhpInv) |
| `[=]` Заглушка = C# no-op | 6 (ChZero, LogsJs, MapActAjaxPhp, Pinfo, PinfonewJs, SlotsJs) |
| `[s]` Заглушка (нужно портировать) | 28 |
| `[ ]` Отсутствует в Java | 18 |
| **Итого требуют портирования** | **46** |

## Java-файлы без C# аналога

| Файл | Описание |
| ---- | -------- |
| `Date.java` | Утилитная обёртка для java.util.Date |
| `InvEntry.java` | Модель предмета инвентаря (используется в mainPhpInv) |
| `PinfoJs.java` | Заглушка (соответствует методу PinfoJs из PinfonewJs.cs) |
