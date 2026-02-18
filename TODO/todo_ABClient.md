# Анализ проекта ABClient (согласно ABClient.csproj)

Этот файл отслеживает общий статус реализации (портирования) всех компонентов ПК-версии на Android.
**Источник истины**: `ABClient\ABClient.csproj` — только файлы, включённые в `<Compile>`, являются активными.

## Мёртвые файлы (есть на диске, но НЕ в .csproj — НЕ компилируются)

Следующие файлы **не включены** в .csproj и не должны портироваться:

| Файл | Папка | Примечание |
| ---- | ----- | ---------- |
| `Converters.cs` | Helpers | Заменён на `MyHelpers\HelperConverters.cs` |
| `HelperHttp.cs` | MyHelpers | Не используется |
| `HelperDice.cs` | MyHelpers | Заменён на `Helpers\Dice.cs` |
| `AskPassword.cs` | Forms | Заменён на `MyForms\FormAskPassword.cs` |
| `AutoLogon.cs` | Forms | Заменён на `MyForms\FormAutoLogon.cs` |
| `FormProfile.cs` | Forms | Заменён на `MyForms\FormProfile.cs` |
| `FormProfiles.cs` | Forms | Заменён на `MyForms\FormProfiles.cs` |
| `NewPassword.cs` | Forms | Заменён на `MyForms\FormNewPassword.cs` |
| `MapPath_0101.cs` | ExtMap | Старая версия, не используется |
| `MapPath_0103.cs` | ExtMap | Старая версия, не используется |

---

## Статус реализации по подпапкам

**Легенда:**
- `[+]` — Полностью реализована (все файлы портированы)
- `[~]` — Частично реализована
- `[-]` — Не требует портирования (Windows-специфика)
- `[ ]` — Не реализована

| Папка | Описание | Файлов в .csproj | Статус реализации |
| ----- | -------- | ---------------- | ----------------- |
| `PostFilter` | Фильтры ответов сервера | 59 .cs + json2.js | `[~]` Частично (3 полных, 4 частичных, 28 заглушек, 18 отсутствуют) |
| `ABProxy` | HTTP-прокси сервер | 18 | `[-]` Не требует (заменён WebView-перехватом) |
| `ABForms` | Главная форма (partial classes) | 36 | `[~]` Частично (MainActivity портирует часть) |
| `MyForms` | Диалоговые формы | 22 | `[~]` Частично (5 из 22 портированы: Profile, Profiles, Code, AskPassword, NewPassword) |
| `Forms` | Старые формы (только HerbNavigator) | 1 | `[ ]` Не реализована |
| `MyProfile` | Конфигурация профиля | 11 | `[~]` Частично (UserConfig портирован) |
| `ExtMap` | Карта и навигация | 13 | `[~]` Частично (Cell, AbcCell, MapPath, Position портированы) |
| `Lez` | ИИ боя (автобой) | 9 | `[ ]` Не реализована |
| `AppControls` | WinForms контролы | 11 | `[-]` Не требует (Windows-специфика) |
| `Helpers` | Утилиты (Crypts, Russian, etc.) | 8 | `[~]` Частично (Russian, Crypts портированы) |
| `MyHelpers` | Утилиты (Strings, Converters, etc.) | 5 | `[~]` Частично (HelperStrings портирован) |
| `Neuro` | Нейросеть для капчи | 2 | `[ ]` Не реализована |
| `MyGuamod` | Распознавание капчи | 1 | `[ ]` Не реализована |
| `MyChat` | Очередь сообщений чата | 1 | `[ ]` Не реализована |
| `MySounds` | Звуковые уведомления | 1 | `[ ]` Не реализована |
| `Tabs` | Мульти-вкладки браузера | 3 | `[ ]` Не реализована |
| `Things` | База предметов | 2 | `[~]` Частично (ThingsRepository портирован) |
| `Properties` | Ресурсы/настройки проекта | 3 | `[-]` Не требует |

---

## Статус реализации корневых файлов (согласно .csproj)

| Файл | Описание | Статус реализации |
| ---- | -------- | ----------------- |
| `Program.cs` | Точка входа | `[+]` ABClientApplication.java |
| `AppConsts.cs` | Константы | `[~]` Частично (AppConsts.java) |
| `AppVars.cs` | Глобальное состояние | `[~]` Частично (AppVars.java) |
| `AppTimer.cs` | Кастомный таймер | `[ ]` Не реализован |
| `AppTimerManager.cs` | Менеджер таймеров | `[ ]` Не реализован |
| `AutoAnswerMachine.cs` | Автоответчик | `[ ]` Не реализован |
| `AutoboiState.cs` | Enum состояний автобоя | `[ ]` Не реализован |
| `Bookmark.cs` | Закладки | `[ ]` Не реализован |
| `BossContact.cs` | Контакты боссов | `[ ]` Не реализован |
| `BossMap.cs` | Карта боссов | `[ ]` Не реализован |
| `ChatUser.cs` | Пользователь чата | `[+]` ChatUser.java |
| `ChatUsersManager.cs` | Менеджер пользователей чата | `[~]` Частично (ChatUserList.java) |
| `Contact.cs` | Модель контакта | `[+]` Contact.java |
| `ContactsManager.cs` | Менеджер контактов | `[+]` ContactsManager.java |
| `CookieAwareWebClient.cs` | WebClient с cookies | `[-]` Не требует (OkHttp) |
| `DataManager.cs` | Менеджер файлов/путей | `[+]` DataManager.java |
| `ExplorerHelper.cs` | Очистка кеша IE | `[-]` Не требует (Windows-специфика) |
| `Favorites.cs` | Избранное | `[ ]` Не реализован |
| `FeatureBrowserEmulation.cs` | Эмуляция IE | `[-]` Не требует (Windows-специфика) |
| `FishTip.cs` | Подсказка рыбалки | `[ ]` Не реализован |
| `Foe.cs` | Враг | `[ ]` Не реализован |
| `HerbCell.cs` | Ячейка с травой | `[ ]` Не реализован |
| `IdleManager.cs` | Менеджер простоя | `[ ]` Не реализован |
| `InvEntry.cs` | Запись инвентаря | `[+]` InvEntry.java (в postfilter) |
| `KeyList.cs` | Список ключей | `[ ]` Не реализован |
| `ListItemBotLevelEx.cs` | Элемент списка бота | `[ ]` Не реализован |
| `LoadingUrlList.cs` | Список загружаемых URL | `[ ]` Не реализован |
| `Log.cs` | Логирование | `[+]` AppLogger.java / DebugLogger.java |
| `NativeMethods.cs` | P/Invoke для WinINet | `[-]` Не требует (Windows-специфика) |
| `NeverApi.cs` | API Neverlands | `[~]` Частично (ApiRepository.java) |
| `Prims.cs` | Примитивы | `[ ]` Не реализован |
| `RoomManager.cs` | Менеджер комнат/чата | `[+]` RoomManager.java |
| `ScriptManager.cs` | Менеджер JS-инъекций | `[~]` Частично (WebAppInterface.java) |
| `TInvUd.cs` | Обновление инвентаря | `[ ]` Не реализован |
| `Tips.cs` | Подсказки | `[ ]` Не реализован |
| `TorgList.cs` | Список торговли | `[+]` TorgList.java |
| `TorgPair.cs` | Пара торговли | `[+]` TorgPair.java |
| `UnderAttack.cs` | Состояние "под атакой" | `[ ]` Не реализован |
| `UnhandledExceptionManager.cs` | Обработчик исключений | `[-]` Не требует (Android crashlytics) |
| `UserForBo.cs` | Пользователь для бота | `[ ]` Не реализован |
| `UserInfo.cs` | Информация о пользователе | `[ ]` Не реализован |
| `VersionClass.cs` | Версия | `[+]` VersionClass.java |

---

## Контентные файлы (Content/None в .csproj)

| Файл | Тип | Описание | Статус |
| ---- | --- | -------- | ------ |
| `abcells.xml` | Content | Данные карты | `[+]` В assets |
| `abthings.xml` | Content | База предметов | `[+]` В assets |
| `abfavorites.xml` | Content | Избранное | `[+]` В assets |
| `abteleports.xml` | Content | Телепорты | `[+]` В assets |
| `bossusers.xml` | Content | Боссы | `[+]` В assets |
| `chatusers.xml` | Content | Пользователи чата | `[+]` В assets |
| `map.xml` | Content | Основная карта | `[+]` В assets |
| `mapnav.js` | Content | JS навигации | `[+]` В assets |
| `PostFilter\json2.js` | Content | JSON2 библиотека | `[+]` В assets/js |
| `arena_v04.js` | None | JS арены | `[+]` В assets |
| `ch_list.js` | None | JS списка чата | `[+]` В assets |
| `map.js` | None | JS карты | `[+]` В assets |
| `Resources\map2.xml` | None | Вторичная карта | `[ ]` Не скопирован |
| `abneuro.dat` | None | Данные нейросети | `[ ]` Не скопирован |
| `spells.txt` | None | Заклинания для Lez | `[ ]` Не скопирован |
| `MySounds\*.wav` | None | Звуки (7 файлов) | `[ ]` Не скопированы |

---

## Сводная статистика

| Категория | Количество |
| --------- | ---------- |
| `[+]` Полностью реализовано | ~15 файлов |
| `[~]` Частично реализовано | ~12 файлов |
| `[-]` Не требует портирования | ~8 файлов (Windows-специфика) |
| `[ ]` Не реализовано | ~25 корневых + подпапки |
| **Мёртвые файлы (не портировать!)** | **15** |
