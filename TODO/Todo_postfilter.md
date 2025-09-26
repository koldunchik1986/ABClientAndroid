# Анализ папки PostFilter

В этой папке содержатся классы, которые фильтруют и модифицируют HTML-ответы от сервера перед их отображением.

## Список файлов и статус анализа

| Файл                      | Описание                                      | Статус подробного анализа (по rules_MDcreate.md) |
| ------------------------- | --------------------------------------------- | --------------------------------------------------- |
| `ArenaJs.cs`              | Фильтр для `arena.js`.                        | `[+]` Реализован                                      |
| `BuildingJs.cs`           | Фильтр для `building.js`.                     | `[+]` Реализован                                      |
| `ButPhp.cs`               | Фильтр для `but.php`.                         | `[+]` Реализован                                      |
| `CastleJs.cs`             | Фильтр для `castle.js`.                       | `[+]` Реализован                                      |
| `ChListJs.cs`             | Фильтр для `ch_list.js`.                      | `[+]` Реализован                                      |
| `ChMsgJs.cs`              | Фильтр для `ch_msg.js`.                       | `[x]` Проанализирован                                 |
| `ChRoomPhp.cs`            | Фильтр для `ch_room.php`.                     | `[+]` Реализован                                      |
| `ChZero.cs`               | Фильтр для `ch_zero.php`.                     | `[+]` Реализован                                      |
| `CounterJs.cs`            | Фильтр для `counter.js`.                      | `[+]` Реализован                                      |
| `FightJs.cs`              | Фильтр для `fight.js`.                        | `[x]` Проанализирован                                 |
| `Filter.cs`               | Основной класс фильтра.                       | `[x]` Проанализирован                                 |
| `FishAjaxPhp.cs`          | Фильтр для `fish_ajax.php`.                   | `[x]` Проанализирован                                 |
| `ForumTopicJs.cs`         | Фильтр для `forum_topic.js`.                  | `[+]` Реализован                                      |
| `GameJs.cs`               | Фильтр для `game.js`.                         | `[+]` Реализован                                      |
| `GamePhp.cs`              | Фильтр для `game.php`.                        | `[+]` Реализован                                      |
| `HpJs.cs`                 | Фильтр для `hp.js`.                           | `[+]` Реализован                                      |
| `HpmpJs.cs`               | Фильтр для `hpmp.js`.                         | `[+]` Реализован                                      |
| `IndexCgi.cs`             | Фильтр для `index.cgi`.                       | `[+]` Реализован                                      |
| `json2.js`                | Библиотека JSON2 (полифил).                   | `[+]` Реализован                                      |
| `LogsJs.cs`               | Фильтр для `logs.js`.                         | `[+]` Реализован                                      |
| `MainPhp.cs`              | Фильтр для `main.php`.                        | `[x]` Проанализирован                                 |
| `MainPhpAutoCure.cs`      | Часть `MainPhp`: авто-лечение.                | `[x]` Проанализирован                                 |
| `MainPhpCityNavigation.cs`| Часть `MainPhp`: навигация по городу.         | `[x]` Проанализирован                                 |
| `MainPhpCure.cs`          | Часть `MainPhp`: лечение.                     | `[x]` Проанализирован                                 |
| `MainPhpDarkFog.cs`       | Часть `MainPhp`: темный туман.                | `[x]` Проанализирован                                 |
| `MainPhpDarkTeleport.cs`  | Часть `MainPhp`: темный телепорт.             | `[x]` Проанализирован                                 |
| `MainPhpDrink.cs`         | Часть `MainPhp`: питье.                       | `[x]` Проанализирован                                 |
| `MainPhpDrinkHpMa.cs`     | Часть `MainPhp`: питье HP/MA.                 | `[x]` Проанализирован                                 |
| `MainPhpFast.cs`          | Часть `MainPhp`: быстрые действия.            | `[x]` Проанализирован                                 |
| `MainPhpFight.cs`         | Часть `MainPhp`: бой.                         | `[x]` Проанализирован                                 |
| `MainPhpFish.cs`          | Часть `MainPhp`: рыбалка.                     | `[x]` Проанализирован                                 |
| `MainPhpInsHp.cs`         | Часть `MainPhp`: вставка HP.                  | `[x]` Проанализирован                                 |
| `MainPhpInv.cs`           | Часть `MainPhp`: инвентарь.                 | `[x]` Проанализирован                                 |
| `MainPhpRaz.cs`           | Часть `MainPhp`: раздевание.                  | `[x]` Проанализирован                                 |
| `MainPhpRob.cs`           | Часть `MainPhp`: ограбление.                | `[x]` Проанализирован                                 |
| `MainPhpRobinHood.cs`     | Часть `MainPhp`: Робин Гуд.                 | `[x]` Проанализирован                                 |
| `MainPhpTied.cs`          | Часть `MainPhp`: привязанные вещи.          | `[x]` Проанализирован                                 |
| `MainPhpWear.cs`          | Часть `MainPhp`: надевание вещей.             | `[x]` Проанализирован                                 |
| `MainPhpWearComplect.cs`  | Часть `MainPhp`: надевание комплектов.        | `[x]` Проанализирован                                 |
| `MainPhpWtime.cs`         | Часть `MainPhp`: время.                       | `[x]` Проанализирован                                 |
| `MapActAjaxPhp.cs`        | Фильтр для `map_act_ajax.php`.                | `[+]` Реализован                                      |
| `MapAjax.cs`              | Фильтр для `map_ajax.php`.                    | `[x]` Проанализирован                                 |
| `MapJs.cs`                | Фильтр для `map.js`.                          | `[+]` Реализован                                      |
| `MsgPhp.cs`               | Фильтр для `msg.php`.                         | `[x]` Проанализирован                                 |
| `NlPinfo.cs`              | Фильтр для `nl_pinfo.cgi`.                    | `[+]` Реализован                                      |
| `OutpostJs.cs`            | Фильтр для `outpost.js`.                      | `[x]` Проанализирован                                 |
| `Pinfo.cs`                | Фильтр для `pinfo.cgi`.                       | `[x]` Проанализирован                                 |
| `PinfonewJs.cs`           | Фильтр для `pinfonew.js`.                     | `[+]` Реализован                                      |
| `PvJs.cs`                 | Фильтр для `pv.js`.                           | `[+]` Реализован                                      |
| `RouletteAjaxPhp.cs`      | Фильтр для `roulette_ajax.php`.               | `[x]` Проанализирован                                 |
| `ShopAjaxPhp.cs`          | Фильтр для `shop_ajax.php`.                   | `[+]` Реализован                                      |
| `ShopEntry.cs`            | Запись в магазине.                            | `[+]` Реализован                                      |
| `ShopJs.cs`               | Фильтр для `shop.js`.                         | `[x]` Проанализирован                                 |
| `SlotsJs.cs`              | Фильтр для `slots.js`.                        | `[x]` Проанализирован                                 |
| `SvitokJs.cs`             | Фильтр для `svitok.js`.                       | `[x]` Проанализирован                                 |
| `TarenaJs.cs`             | Фильтр для `tarena.js`.                       | `[x]` Проанализирован                                 |
| `TeleportAjax.cs`         | Фильтр для `teleport_ajax.php`.               | `[x]` Проанализирован                                 |
| `TopJs.cs`                | Фильтр для `top.js`.                          | `[x]` Проанализирован                                 |
| `TowerJs.cs`              | Фильтр для `tower.js`.                        | `[x]` Проанализирован                                 |
| `TradePhp.cs`             | Фильтр для `trade.php`.                       | `[x]` Проанализирован                                 |
