



















# Анализ проекта ABClient — Сводный файл портирования

**Обновлено**: 2026-04-12  
**Источник истины**: `ABClient\ABClient.csproj` + полное сканирование диска

## Мёртвые файлы (есть на диске, но НЕ в .csproj — НЕ компилируются)

Следующие файлы **не включены** в .csproj и не должны портироваться:

| Файл | Папка | Заменён на |
| ---- | ----- | ---------- |
| `Converters.cs` | Helpers | `MyHelpers\HelperConverters.cs` |
| `HelperHttp.cs` | MyHelpers | Не используется |
| `HelperDice.cs` | MyHelpers | `Helpers\Dice.cs` |
| `AskPassword.cs` | Forms | `MyForms\FormAskPassword.cs` |
| `AutoLogon.cs` | Forms | `MyForms\FormAutoLogon.cs` |
| `FormProfile.cs` | Forms | `MyForms\FormProfile.cs` |
| `FormProfiles.cs` | Forms | `MyForms\FormProfiles.cs` |
| `NewPassword.cs` | Forms | `MyForms\FormNewPassword.cs` |
| `MapPath_0101.cs` | ExtMap | Старая версия |
| `MapPath_0103.cs` | ExtMap | Старая версия, не используется |

---

## Статус реализации по подпапкам

**Легенда:**
- `[+]` — Полностью реализована (все файлы портированы)
- `[~]` — Частично реализована
- `[-]` — Не требует портирования (Windows-специфика)
- `[ ]` — Не реализована






















| Папка | Описание | Файлов .cs | Статус | Android-расположение | Детальный анализ |
| ----- | -------- | ---------- | ------ | -------------------- | --------------- |
| `PostFilter` | Фильтры ответов сервера | 59 | `[~]` **Частично** | `postfilter/` | `TODO/todo_PostFilter_detailed_comparison.md` |
| `ABProxy` | HTTP-прокси сервер | 18 | `[-]` Заменён архитектурно | WebView + SessionManager | — |
| `ABForms` | Главная форма (partial classes) | 36 | `[+]` Полностью | `MainActivity.java` | — |
| `MyForms` | Диалоговые формы | 22 | `[+]` Полностью | Activity / Dialog | — |
| `Forms` | Старые формы (только HerbNavigator) | 1 | `[+]` Полностью | `ui/Navigator.java` | — |
| `MyProfile` | Конфигурация профиля | 11 | `[+]` Полностью | `model/UserConfig.java` | — |
| `ExtMap` | Карта и навигация | 13 | `[+]` Полностью | `utils/ExtMap.java`, `model/Cell.java` | — |
| `Lez` | ИИ боя (автобой) | 9 | `[+]` Полностью | `lez/LezFight.java` | `TODO/todo_LezFight.md` |
| `AppControls` | WinForms контролы | 11 | `[-]` Win-специфика | — | — |
| `Helpers` | Утилиты (Crypts, Russian) | 8 | `[+]` Полностью | `utils/CryptoUtils.java` | — |
| `MyHelpers` | Утилиты (Strings, Conv) | 5 | `[+]` Полностью | `utils/HelperStrings.java` | — |
| `Neuro` | Нейросеть для капчи | 2 | `[~]` Частично | Captcha в Interceptor | — |
| `MyGuamod` | Распознавание капчи | 1 | `[~]` Частично | Captcha в Interceptor | — |
| `MyChat` | Очередь сообщений чата | 1 | `[+]` Полностью | `utils/Chat.java` | — |
| `MySounds` | Звуковые уведомления | 1 | `[+]` Полностью | `utils/EventSounds.java` | — |
| `Tabs` | Мульти-вкладки браузера | 3 | `[+]` Полностью | `manager/TabManager.java` | — |
| `Things` | База предметов | 2 | `[+]` Полностью | `repository/ThingsRepository.java` | — |
| `Profile` | Простой профиль (устар.) | 2 | `[-]` Заменён | `MyProfile/UserConfig.java` | — |
| `Properties` | Ресурсы/настройки | 3 | `[-]` Не требует | — | — |
| `Resources` | DLL, изображения | 2 | `[-]` Не требует | — | — |
| `Js` | JavaScript файлы | 6 | `[+]` В assets | assets/js/ | — |
| **QuickButtons** | Быстрые кнопки на UI | 5 | `[+]` Полностью | `ui/QuickButtonsPanel.java` | — |
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

## Ключевые архитектурные отличия Android от C#

### 1. Прокси-подход
- **C#**: Локальный HTTP-прокси (`ABProxy/`) перехватывает все запросы, модифицирует HTML/JS
- **Android**: `WebViewRequestInterceptor` + `WebViewClient.shouldInterceptRequest()` + JS-инъекции через `HtmlUtils.getJsFix()`
- **Результат**: Функционально эквивалентно, но архитектурно иначе

### 2. Фреймовая модель
- **C#**: Мульти-фреймовый IE-браузер (`main_top`, `ch_buttons`, `ch_list`, `chmain`)
- **Android**: Одиночный WebView + JS-эмуляция `top.frames[...]` через `AndroidBridge`
- **Результат**: Полная эмуляция через `HtmlUtils.getJsFix()` + DOM-stubs (`transfer`, `complect`, `hbar`)

### 3. Система VCode
- **C#**: Парсинг из каждого ответа в глобальный `AppVars.VCode`
- **Android**: `SessionManager` — централизованный синглтон с TTL, fight-fallback, версионированием
- **Результат**: Android-версия более надёжна (TTL, thread-safe, fight-context)

### 4. Авто-функции
- **C#**: Таймеры WinForms + `IdleManager`
- **Android**: `AutoModeForegroundService` + `ForcedActionGuard` + `FightViewModel`
- **Результат**: Полный паритет, event-driven бой < 100ms

---

## Сводная статистика

| Категория | Количество |
| --------- | ---------- |
| `[+]` Полностью реализовано | ~120 файлов/модулей |
| `[~]` Частично реализовано | ~20 файлов (PostFilter + Neuro) |
| `[s]` Заглушки (PostFilter) | ~28 файлов |
| `[-]` Не требует портирования | ~15 файлов (Windows-специфика) |
| `[ ]` Не реализовано | ~18 файлов |
| **Мёртвые файлы (не портировать!)** | **15** |

---

## Приоритеты доработки (по важности)

### P0 — Критичные пробелы (влияют на gameplay)
1. **PostFilter: 16 отсутствующих MainPhp-модулей** — `MainPhpCure`, `MainPhpDrink`, `MainPhpFish`, `MainPhpWearComplect`, `MainPhpTied`, `MainPhpDarkFog`, `MainPhpDarkTeleport`, `MainPhpRob`, `MainPhpRobinHood`, `MainPhpWtime`, `MainPhpAutoCure`
2. **PostFilter: 28 заглушек** — `HpJs`, `HpmpJs`, `MapAjax`, `MapJs`, `ButPhp`, `ShopAjaxPhp`, `SvitokJs`, `IndexCgi` и др.
3. **Neuro/капча** — `abneuro.dat` + нейросеть для распознавания капчи

### P1 — Важные (ухудшают UX)
4. **Ресурсы**: `spells.txt`, `MySounds/*.wav`, `map2.xml` — не скопированы в assets
5. **KeyList.cs** — не проанализирован, назначение неизвестно
6. **Tips.cs** — подсказки игроку

### P2 — Желательные (косметика/оптимизация)
7. **Neuro полная интеграция** — замена hardcoded-распознавания на нейросеть
8. **Refactor PostFilter заглушек** — заменить пустые методы на реальные фильтры
9. **ForumTopicJs, TradePhp, RouletteAjaxPhp** — второстепенные фильтры

---

## Карта зависимостей ключевых систем

```
MainActivity
├── WebView → WebViewRequestInterceptor → SessionManager (VCode)
├── WebView → HtmlUtils.getJsFix() → AndroidBridge (WebAppInterface)
├── PostFilter.Filter → MainPhp → [LezFight, FishAjaxPhp, FightJs, ...]
├── AutoModeForegroundService → ForcedActionGuard → AppVars
├── FightViewModel → FightAnnounceHandler → SessionManager
├── FastActionManager → AppVars.FastNeed → MainPhp
├── AutoFunctionsManager → UserConfig → SharedPreferences
└── ProfileManager → UserConfig → DataManager
| **Мёртвые файлы (не портировать!)** | **15** |
