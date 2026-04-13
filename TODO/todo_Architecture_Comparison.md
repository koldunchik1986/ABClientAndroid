"# Архитектурное сравнение: C# (ПК) → Android

**Обновлено**: 2026-04-12

---

## 1. Точка входа

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| Файл | `Program.cs` → `MainForm.cs` | `ABClientApplication.java` → `LoginActivity` → `MainActivity` |
| Инициализация | `DataManager.Init()` → выбор профиля → очистка кэша IE → запуск прокси → `Application.Run(MainForm)` | `AppVars.init(context)` → загрузка профиля → `LoginActivity` → авторизация → `MainActivity` |
| Профили | `ConfigSelector.Process()` → `FormProfiles` | `ProfilesActivity` / `ProfileActivity` (с шифрованием паролей) |
| Автологин | `AutoLogon.cs` (Form с таймером) | Автологин в `LoginActivity` если `UserAutoLogon=true` |

---

## 2. Сетевой стек

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| HTTP-клиент | `CookieAwareWebClient` (WebClient + CookieContainer) | `WebView` + `OkHttp` (для прямых запросов) |
| Прокси-сервер | `ABProxy/LocalHttpProxyServer` (порт 8052) — перехватывает ВСЕ запросы | `WebViewRequestInterceptor` — `shouldInterceptRequest()` + JS-инъекции |
| VCode | Глобальный `AppVars.VCode` — перезаписывается каждым ответом | `SessionManager` — синглтон с TTL, fight-fallback, версионированием |
| Cookie | Через прокси + `CookieContainer` | `WebViewCookieJar` + `CookiesManager` |
| User-Agent | Браузерный (из `AppConsts`) | `AppVars.BROWSER_USER_AGENT` = Chrome/124 (анти-детект) |

### Ключевое отличие: Прокси vs WebView

**C#**: Вся обработка идёт через локальный прокси-сервер:
1. Браузер (IE) настроен на прокси 127.0.0.1:8052
2. Прокси перехватывает запрос/ответ
3. `PostFilter.Filter` модифицирует HTML/JS
4. Прокси возвращает модифицированный контент

**Android**: Обработка через WebView + interceptor:
1. `WebViewClient.shouldInterceptRequest()` перехватывает запрос
2. `WebViewRequestInterceptor` загружает ответ, парсит VCode
3. `PostFilter.Filter.filter()` модифицирует HTML/JS
4. `HtmlUtils.injectJsFix()` добавляет JS-стубы (DOM-stubs, frames-эмуляция)
5. Модифицированный контент возвращается в WebView

---

## 3. Фреймовая модель

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| Браузер | IE с мульти-фреймами (`main_top`, `ch_buttons`, `ch_list`, `chmain`) | Одиночный `WebView` |
| Коммуникация фреймов | Прямой доступ `top.frames['chmain'].document` | JS-эмуляция через `AndroidBridge` |
| Чат | Отдельный фрейм `chmain` + `ch_list` + `ch_buttons` | Единый WebView + `AndroidBridge.chatAddMsg()` и др. |
| Бой | Фрейм `main_top` + JS `fight_v*.js` | Тот же WebView + `FightJs.java` замены + `WebAppInterface` |

### Эмуляция frames в Android (HtmlUtils.getJsFix)

```javascript
// Критичные заглушки DOM-элементов (ПРАВИЛО #5 — нельзя удалять):
window.__abEnsureNode('transfer', 'div');   // Форма передачи
window.__abEnsureNode('complect', 'div');   // Комплекты
window.__abEnsureNode('hbar', 'span');       // HP/MA бар

// Эмуляция top.frames:
top.frames['ch_buttons'] = { set location(url) { AndroidBridge.loadFrame(...) } };
top.frames['chmain'] = { set location(url) { ..., add_msg: function(t) { AndroidBridge.chatAddMsg(t); } } };
top.frames['main_top'] = { set location(url) { AndroidBridge.loadFrame(...) } };

// Переопределение document.getElementById для возврата dummy-элементов:
document.getElementById = function(id) { ... return dummy; }
```

---

## 4. Система VCode (код защиты)

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| Хранение | `AppVars.VCode` (string) — глобальная переменная | `SessionManager` — синглтон с `SessionContext` |
| Парсинг | В каждом PostFilter-модуле вручную | `SessionManager.parseVCodeFromHtml()` — 7 паттернов |
| TTL | Нет (пока не перезапишется) | 5 минут (дефолт) / 2 минуты (бой) |
| Боевой контекст | Нет | `fightStartVCode` — кэш на время боя, `markFightInProgress()` |
| Thread-safety | Нет (WinForms — один поток) | `ReentrantReadWriteLock` |
| Обработка ошибок | Нет | `onInvalidProtectionCodeError()` — инвалидация контекста |

### Порядок инициализации боя (ПРАВИЛО #9)

```
1️⃣ SessionManager.markFightInProgress()    ← ПЕРВОЙ!
2️⃣ LezFight fight = new LezFight(html)      ← ВТОРОЙ
3️⃣ fight.buildFrame()                        ← ТРЕТИЙ
4️⃣ submitFightTurn(fight)                    ← ЧЕТВЁРТЫЙ
```

---

## 5. Авто-функции

| Функция | C# (ПК) | Android | Статус |
| ------- | ------- | ------- | ------ |
| Авто-бой | `AutoboiState` + `LezFight` + WinForms Timer | `AutoboiState` + `LezFight` + `AutoModeForegroundService` + `FightViewModel` | `[+]` Полностью |
| Авто-рыбалка | `FishHelper` + PostFilter | `FishAjaxPhp.java` + `AutoFunctionsManager` | `[+]` Полностью |
| Авто-охота | `AutoHelper` + `MainPhpWear` | `AutoFunctionsManager` + `MainPhp.mainPhpWearKnife()` | `[+]` Полностью |
| Авто-нападение | `FastActionManager` | `FastActionManager.java` | `[+]` Полностью |
| Авто-клад | `CompasAuto` | `CompasAuto.java` + `TreasureDig.java` | `[+]` Полностью |
| Авто-компас | `CompasAuto` | `CompasAuto.java` | `[+]` Полностью |
| Авто-питьё | `MainPhpDrink` + `MainPhpDrinkHpMa` | **НЕ портировано** | `[ ]` Критичный пробел |
| Авто-лечение | `MainPhpAutoCure` + `MainPhpCure` | **НЕ портировано** | `[ ]` Критичный пробел |
| Авто-надевание комплектов | `MainPhpWearComplect` | **НЕ портировано** | `[ ]` Средний приоритет |

### Event-driven бой (< 100ms)

**C#**: Polling через WinForms Timer (24+ сек между проверками)
**Android**: Event-driven через `FightAnnounceHandler` + `FightViewModel`:
1. `MainPhp.notifyNewFight()` → `AppVars.LastFightAnnounceAtMs`
2. `MainActivity.requestImmediateAutoTurnOnFightAnnounce()`
3. `FightAnnounceHandler.onFightAnnounced()` → проверка captcha/guard/VCode
4. Отправка хода в течение < 100ms

### Foreground Service (анти-убийство процесса)

**C#**: WinForms приложение — не убивается Windows
**Android**: `AutoModeForegroundService` — удерживает wake-lock, обеспечивает фоновую работу:
- `maybeForceFightFrameSync()` — синхронизация боя при заблокированном экране
- `maybeShowFightCaptchaDialog()` — показ капчи из фона
- `isFightSessionLikelyActive()` — проверка по `LastFightPulseAtMs`

---

## 6. Логирование

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| Основной логгер | `Log.cs` (файл + Debug) | `FileLogger` (Critical/*.log) + `AppLog` (logcat) |
| Сегментация | Нет | 10-минутные сегменты для proxy-логов |
| Ротация | Нет | MAX_FILE_BYTES=8MB, MAX_ROTATIONS=2 |
| Logcat → файл | Нет | `LogcatFileRecorder` (вкл. из настроек) |
| Proxy-трейс | `AppVars.DoHttpLog` | `FileLogger.proxyPool()` + `ProxyLogDeduper` |

---

## 7. Профиль пользователя

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| Класс | `MyProfile.UserConfig` + `Profile.Config` | `model/UserConfig` |
| Формат хранения | XML файлы в AppData | XML файлы в `SharedPreferences` + файлы профиля |
| Шифрование | `Config.Encrypt()/Decrypt()` | `CryptoUtils.encrypt()/decrypt()` |
| Автологин | `Config.UserAutoLogon` | `UserConfig.UserAutoLogon` |
| Настройки боя | `LezGroups`, `LezDoAutoboi` и др. | `UserConfig.LezGroups`, `UserConfig.LezDoAutoboi` |
| Настройки рыбалки | `FishHandOne`, `FishAutoWear`, `FishEnabledPrims` | `UserConfig.FishHandOne/Two`, `FishAutoWear`, `FishEnabledPrims` |

---

## 8. Звуковая система

| Аспект | C# (ПК) | Android |
| ------ | ------- | ------- |
| Файлы | `MySounds/*.wav` (7 файлов) | **Не скопированы в assets** — нужно портировать |
| Класс | `EventSounds.cs` | `EventSounds.java` — портирован, но воспроизведение через SoundPool/MediaPlayer |
| События | attack, timer, refresh, sndmsg, alarm, bear, digits | Те же константы в EventSounds.java |

---

## 9. Менеджеры (Managers)

| C# класс | Android класс | Статус |
| -------- | ------------- | ------ |
| `RoomManager.cs` | `manager/RoomManager.java` | `[+]` Полностью |
| `ContactsManager.cs` | `manager/ContactsManager.java` | `[+]` Полностью |
| `ChatUsersManager.cs` | `manager/ChatUserList.java` | `[+]` Полностью |
| `IdleManager.cs` | `service/AutoModeForegroundService.java` | `[+]` (заменён архитектурно) |
| `AppTimerManager.cs` | `manager/AppTimerManager.java` | `[+]` Полностью |
| `ScriptManager.cs` | `bridge/WebAppInterface.java` | `[+]` Полностью |
| — | `manager/AutoFunctionsManager.java` | `[+]` Android-специфичный |
| — | `manager/FastActionManager.java` | `[+]` Android-специфичный |
| — | `manager/BossAuto.java` | `[+]` Полностью |
| — | `manager/CompasAuto.java` | `[+]` Полностью |
| — | `manager/CharacterVitalsManager.java` | `[+]` Android-специфичный |
| — | `manager/ClanWarsManager.java` | `[+]` Android-специфичный |
| — | `manager/UnderAttackManager.java` | `[+]` Полностью |
| — | `manager/TabManager.java` | `[+]` Полностью |
| — | `manager/QuickButtonsManager.java` | `[+]` Полностью |
| — | `manager/TorgList.java` + `TorgPair.java` | `[+]` Полностью |
| — | `manager/NeverApi.java` | `[+]` Полностью |

---

## 10. Отсутствующие в Android (критичные)

| Функциональность | C# файлы | Приоритет | Влияние |
| ---------------- | -------- | --------- | ------- |
| Авто-питьё зелий | `MainPhpDrink.cs`, `MainPhpDrinkHpMa.cs` | **P0** | Персонаж не пьёт зелья автоматически |
| Авто-лечение | `MainPhpAutoCure.cs`, `MainPhpCure.cs` | **P0** | Персонаж не лечится автоматически |
| Навигация по городу | `MainPhpCityNavigation.cs` | **P0** | Не работает проход через ворота |
| Триггер рыбалки из main | `MainPhpFish.cs` | **P0** | Рыбалка не запускается из main.php |
| Надевание комплектов | `MainPhpWearComplect.cs` | **P1** | Не надевает комплекты экипировки |
| Обработка усталости | `MainPhpTied.cs` | **P1** | Не обрабатывает усталость автоматически |
| Таймеры HP/MA | `HpJs.cs`, `HpmpJs.cs` | **P1** | Нет визуальных таймеров HP/MA |
| Парсинг карты | `MapAjax.cs`, `MapJs.cs` | **P1** | Нет обновления позиции на карте |
| Фильтр сообщений чата | `ChMsgJs.cs` | **P0** | Нет фильтрации/подсветки в чате |
| Звуковые файлы | `MySounds/*.wav` | **P1** | Нет звуковых уведомлений |
| Нейросеть капчи | `NeuroBase.cs`, `abneuro.dat` | **P2** | Распознавание капчи ручное |
| Заклинания | `spells.txt` | **P1** | LezSpell может не загрузить данные |