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
| `PostFilter` | Фильтры ответов сервера | 59 .cs + json2.js | `[+]` Полностью (все основные фильтры портированы в `ru.neverlands.abclient.postfilter`) |
| `ABProxy` | HTTP-прокси сервер | 18 | `[-]` Не требует (заменён WebView-перехватом и SessionManager) |
| `ABForms` | Главная форма (partial classes) | 36 | `[+]` Полностью (MainActivity реализует всю основную логику) |
| `MyForms` | Диалоговые формы | 22 | `[+]` Полностью (реализованы как Activity или Dialog в Android) |
| `Forms` | Старые формы (только HerbNavigator) | 1 | `[+]` Полностью (Navigator.java) |
| `MyProfile` | Конфигурация профиля | 11 | `[+]` Полностью (UserConfig.java, AuthManager.java) |
| `ExtMap` | Карта и навигация | 13 | `[+]` Полностью (ExtMap.java, Cell, AbcCell, MapPath, Position) |
| `Lez` | ИИ боя (автобой) | 9 | `[+]` Полностью (LezFight, LezBotsGroup, LezBotsClassCollection, LezSpell, LezSpellCollection, LezNode) |
| `AppControls` | WinForms контролы | 11 | `[-]` Не требует (Windows-специфика) |
| `Helpers` | Утилиты (Crypts, Russian, etc.) | 8 | `[+]` Полностью (Russian, CryptoUtils, ConverterUtils) |
| `MyHelpers` | Утилиты (Strings, Converters, etc.) | 5 | `[+]` Полностью (HelperStrings, ConverterUtils) |
| `Neuro` | Нейросеть для капчи | 2 | `[~]` Частично (Captcha logic в Interceptor/MainActivity) |
| `MyGuamod` | Распознавание капчи | 1 | `[~]` Частично (Captcha logic в Interceptor/MainActivity) |
| `MyChat` | Очередь сообщений чата | 1 | `[+]` Полностью (Chat.java, ChatFilter.java, ChatStats.java) |
| `MySounds` | Звуковые уведомления | 1 | `[+]` Полностью (EventSounds.java) |
| `Tabs` | Мульти-вкладки браузера | 3 | `[+]` Полностью (TabManager.java, TabClass.java) |
| `Things` | База предметов | 2 | `[+]` Полностью (ThingsRepository.java, Thing.java) |
| `Properties` | Ресурсы/настройки проекта | 3 | `[-]` Не требует |
| **QuickButtons** | Быстрые кнопки на UI | 5 | `[+]` Полностью (QuickButtonsPanel.java, QuickButtonsManager.java) |
| **Авто-Функции** | Автобой, авторыбалка, автоохота и т.д. | 10+ | `[+]` Полностью (AutoFunctionsManager.java, FastActionManager.java, BossAuto.java, CompasAuto.java) |

---

## Статус реализации корневых файлов (согласно .csproj)

| Файл | Описание | Статус реализации |
| ---- | -------- | ----------------- |
| `Program.cs` | Точка входа | `[+]` ABClientApplication.java |
| `AppConsts.cs` | Константы | `[+]` AppConsts.java |
| `AppVars.cs` | Глобальное состояние | `[+]` AppVars.java |
| `AppTimer.cs` | Кастомный таймер | `[+]` AppTimer.java |
| `AppTimerManager.cs` | Менеджер таймеров | `[+]` AppTimerManager.java |
| `AutoAnswerMachine.cs` | Автоответчик | `[+]` AutoAnswerMachine.java |
| `AutoboiState.cs` | Enum состояний автобоя | `[+]` AutoboiState.java |
| **Авто-Функции (FastAction)** | LezFight, FastActionManager, AutoFunctionsManager | `[+]` Полностью реализованы |
| `Bookmark.cs` | Закладки | `[-]` Не требуется (TabManager) |
| `BossContact.cs` | Контакты боссов | `[+]` BossAuto.java |
| `BossMap.cs` | Карта боссов | `[+]` BossAuto.java |
| `ChatUser.cs` | Пользователь чата | `[+]` ChatUser.java |
| `ChatUsersManager.cs` | Менеджер пользователей чата | `[+]` ChatUserList.java |
| `Contact.cs` | Модель контакта | `[+]` Contact.java |
| `ContactsManager.cs` | Менеджер контактов | `[+]` ContactsManager.java |
| `CookieAwareWebClient.cs` | WebClient с cookies | `[-]` Не требует (OkHttp) |
| `DataManager.cs` | Менеджер файлов/путей | `[+]` DataManager.java |
| `ExplorerHelper.cs` | Очистка кеша IE | `[-]` Не требует (Windows-специфика) |
| `Favorites.cs` | Избранное | `[+]` Favorites.java (или TabManager) |
| `FeatureBrowserEmulation.cs` | Эмуляция IE | `[-]` Не требует (Windows-специфика) |
| `FishTip.cs` | Подсказка рыбалки | `[+]` FishAjaxPhp.java |
| `Foe.cs` | Враг | `[+]` Foe.java |
| `HerbCell.cs` | Ячейка с травой | `[+]` AbcCell.java |
| `IdleManager.cs` | Менеджер простоя | `[+]` MainActivity.java / ForegroundService |
| `InvEntry.cs` | Запись инвентаря | `[+]` InvEntry.java / InventoryParser.java |
| `KeyList.cs` | Список ключей | `[ ]` Не проанализирован |
| `ListItemBotLevelEx.cs` | Элемент списка бота | `[-]` Не требуется |
| `LoadingUrlList.cs` | Список загружаемых URL | `[+]` WebViewRequestInterceptor.java |
| `Log.cs` | Логирование | `[+]` FileLogger.java / DebugLogger.java |
| `NativeMethods.cs` | P/Invoke для WinINet | `[-]` Не требует (Windows-специфика) |
| `NeverApi.cs` | API Neverlands | `[+]` NeverApi.java |
| `Prims.cs` | Примитивы | `[+]` Prims.java |
| `RoomManager.cs` | Менеджер комнат/чата | `[+]` RoomManager.java |
| `ScriptManager.cs` | Менеджер JS-инъекций | `[+]` WebAppInterface.java |
| `TInvUd.cs` | Обновление инвентаря | `[+]` InventoryParser.java / ParsedDressed.java |
| `Tips.cs` | Подсказки | `[ ]` Не реализован |
| `TorgList.cs` | Список торговли | `[+]` TorgList.java |
| `TorgPair.cs` | Пара торговли | `[+]` TorgPair.java |
| `UnderAttack.cs` | Состояние "под атакой" | `[+]` UnderAttackManager.java |
| `UnhandledExceptionManager.cs` | Обработчик исключений | `[-]` Не требует (Android crashlytics) |
| `UserForBo.cs` | Пользователь для бота | `[+]` LezBotsGroup.java |
| `UserInfo.cs` | Информация о пользователе | `[+]` NeverApi.java / PinfoActivity |
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
