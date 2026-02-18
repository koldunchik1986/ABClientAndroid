# План портирования FormMainFast.cs -> FastActionManager.java

## 1. Функциональность в C#

### Файл FormMainFast.cs (ABForms) — Управление быстрыми атаками
Partial class `FormMain`. Содержит:

1. **AttackInfo** — inner class: `TargetNick`, `Weapon`
2. **FastStartSafe(id, nick, count)** — устанавливает `AppVars.FastNeed/FastId/FastNick/FastCount`, thread-safe
3. **FastCancelSafe()** — сбрасывает все Fast-переменные, отменяет ожидание боя
4. **FastAttackAsync(stateInfo)** — фоновый поток:
   - Получает инфо о цели через `NeverApi.GetAll(nick)` → `UserInfo`
   - Если цель в бою (`FightLog != "0"`) — опрашивает `NeverApi.GetFlog(flog)` в цикле, ожидая `var off = 1`
   - Проверяет тип боя (открытый/закрытый) — если `WaitOpen` и бой открытый, ждёт
   - Каждые 100 запросов пишет статус в чат
   - После окончания боя вызывает `FastStartSafe(weapon, nick)` + `ReloadMainFrame()`
5. **20+ статических методов FastAttackXxx(nick)**:
   - `FastAttack` → weapon=`i_svi_001.gif`
   - `FastAttackBlood` → weapon=`i_svi_002.gif`
   - `FastAttackUltimate` → weapon=`i_w28_26.gif`
   - `FastAttackClosedUltimate` → weapon=`i_w28_26X.gif`
   - `FastAttackClosed` → weapon=`i_svi_205.gif`
   - `FastAttackFist` → weapon=`i_w28_24.gif`
   - `FastAttackClosedFist` → weapon=`i_w28_25.gif`
   - `FastAttackFog` → weapon=`i_svi_213.gif` (без ожидания боя — прямой `FastStartSafe`)
   - `FastAttackPoison` → weapon=`Яд`
   - `FastAttackStrong` → weapon=`Зелье Сильной Спины`
   - `FastAttackNevidPot` → weapon=`Зелье Невидимости`
   - И другие (Portal, Zas, Totem, Island, Blaz, Elixir, Bait)
6. **StripItalic(nick)** — убирает `<i>`, `</i>` из ника

### Файл MainPhpFast.cs (PostFilter) — HTML-обработка
Метод `MainPhpFast(html)` — switch по `AppVars.FastId`:
- Парсит HTML страницы `main.php`
- Ищет `w28_form(...)` или `magicreform(...)` или `abil_svitok(...)`
- Извлекает параметры: `vcode`, `wuid`, `wsubid`, `wsolid`, `wmcode`
- Генерирует HTML с формой + `document.ff.submit()` для авто-отправки

**Паттерн работы:**
1. Пользователь нажимает кнопку → `FormMainFast.FastAttackXxx(nick)`
2. Запуск фонового потока → ожидание окончания боя цели
3. `FastStartSafe(weapon, nick)` → `AppVars.FastNeed = true`
4. `ReloadMainFrame()` → WebView загружает `main.php`
5. `Filter.process()` → `MainPhp.process()` → проверяет `AppVars.FastNeed` → вызывает `MainPhpFast(html)`
6. `MainPhpFast` парсит HTML, генерирует форму с авто-submit → WebView отправляет POST → атака выполнена

## 2. Проверка существующей реализации в Android

### Уже есть:
- `AppVars.java` — все 8 Fast-переменных (добавлены при портировании FormQuick)
- `QuickActionsBottomSheet.java` — UI панель (заглушки)
- `WebAppInterface.java` — CheckFastAttack* методы
- `NeverApi.cs` → `ApiRepository.java` (частично)
- `HelperStrings.java` — метод `subString` (частично)
- `MainPhp.java` — есть `process()`, но без вызова `mainPhpFast`

### Чего нет:
- `FastActionManager.java` — основной класс
- Вызов `mainPhpFast` из `MainPhp.process()`
- Методы парсинга HTML (`mainPhpFastHit`, `mainPhpFastPotion` и др.)

## 3. Решение для портирования

### Архитектура на Android:
В C# — это два файла в разных папках (FormMainFast + PostFilter/MainPhpFast), оба partial class.
На Android объединим в один класс `FastActionManager`:
- **Часть 1 (из FormMainFast):** Методы `fastAttack*()`, `fastStart()`, `fastCancel()`, `fastAttackAsync()` — управление
- **Часть 2 (из MainPhpFast):** Метод `processMainPhp(html)` — парсинг HTML и генерация форм

Интеграция:
- `QuickActionsBottomSheet` вызывает `FastActionManager.fastAttack*(nick)`
- `MainPhp.process()` вызывает `FastActionManager.processMainPhp(html)` когда `AppVars.FastNeed == true`
- WebView загружает сгенерированный HTML с авто-submit формой

## 4. План реализации

### Шаг 1: Создать FastActionManager.java
- [x] Создать `app/src/main/java/ru/neverlands/abclient/manager/FastActionManager.java`
- [x] Реализовать `fastStart(id, nick, count)` и `fastCancel()`
- [x] Реализовать `stripItalic(nick)`
- [x] Реализовать все `fastAttack*(nick)` методы (13 штук)
- [ ] Реализовать `fastAttackAsync(targetNick, weapon)` — фоновый поток с polling (отложено: зависит от NeverApi)

### Шаг 2: Реализовать processMainPhp(html)
- [x] Метод-диспетчер по `AppVars.FastId` (processMainPhp)
- [x] `mainPhpFastHit(html, validSubIds, desc)` — универсальный для ВСЕХ нападалок (1-4, 5-8, 24, 25, 26, 29, 30)
- [x] `mainPhpFastFog(html)` — abil_svitok парсинг
- [x] `mainPhpFastPotion(html)` — magicreform парсинг (все 35+ зелий)
- [x] `mainPhpFastW28(html, subId, desc)` — универсальный для свитков/порталов (22, 27, 86)
- [ ] `mainPhpFastElixir(html)` — эликсиры (отложено)
- [ ] `mainPhpFastTotem(html)` — тотем (отложено)

### Шаг 3: Интеграция с MainPhp.java
- [x] В `MainPhp.process()` добавить проверку `AppVars.FastNeed`
- [x] Если true — вызвать `FastActionManager.processMainPhp(html)`
- [x] Если результат не null — вернуть его вместо обычного html

### Шаг 4: Интеграция с QuickActionsBottomSheet
- [x] Заменить Toast-заглушки на `FastActionManager.fastAttack*(nick)`
- [x] Добавить вызов reload WebView после fastStart (через broadcast)

### Шаг 5: Сборка и тестирование
- [x] Собрать приложение — BUILD SUCCESSFUL
- [ ] Проверить на устройстве
