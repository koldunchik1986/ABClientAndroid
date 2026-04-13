# Критичные пробелы портирования — Детальный план

**Обновлено**: 2026-04-12
**Приоритет**: P0 — блокируют gameplay

---

## 1. MainPhpDrink — Авто-питьё зелий

### C# реализация (MainPhpDrink.cs)

**Назначение**: Автоматическое употребление зелий/напитков из инвентаря по таймеру или при низких HP/MA.

**Ключевые переменные**:
- `AppVars.NeverTimer` — cooldown перед выполнением действия (DateTime)
- `AppVars.TimerPauseNonCombatAutoFunctions` — пауза авто-функций перед срабатыванием таймера
- `AppVars.CurHP/MaxHP/CurMA/MaxMA` — текущие/максимальные HP/MA
- `UserConfig.LezDoDrinkHp/LezDrinkHp` — настройки авто-питья HP
- `UserConfig.LezDoDrinkMa/LezDrinkMa` — настройки авто-питья MA

**Логика C#**:
1. Проверка: сейчас бой? Если да — не пить (бой имеет свою логику питья)
2. Проверка: NeverTimer истёк? Если нет — подождать
3. Проверка: HP < LezDrinkHp%? → найти зелье HP в инвентаре, отправить `main.php?get_id=...&use_id=...`
4. Проверка: MA < LezDrinkMa%? → найти зелье MA в инвентаре, отправить
5. Обновить NeverTimer (cooldown после питья)

### План портирования на Android

1. Добавить метод `mainPhpDrink(String html)` в `MainPhp.java`
2. Использовать `SessionManager.getValidVCodeForAction("drink")` для VCode
3. Формировать URL: `main.php?get_id=60&use_id=ITEM_ID&vcode=VCODE`
4. Вызывать через `MainActivity.evaluateJavascript()` или `WebView.loadUrl()`
5. Логировать через `FileLogger.trace("drink", ...)`

### Зависимости (уже есть в Android)
- ✅ `AppVars.NeverTimer` — портирован
- ✅ `AppVars.TimerPauseNonCombatAutoFunctions` — портирован
- ✅ `AppVars.CurHP/MaxHP/CurMA/MaxMA` — портированы
- ✅ `SessionManager` — портирован
- ✅ `InventoryParser` — портирован
- ❌ Логика поиска зелья в инвентаре — НЕТ (нужно написать)

---

## 2. MainPhpDrinkHpMa — Питьё HP/MA зелий в бою

### C# реализация (MainPhpDrinkHpMa.cs)

**Назначение**: Автоматическое употребление зелий HP/MA во время боя, когда HP/MA падают ниже порога.

**Логика C#**:
1. Проверка: мы в бою? (AutoboiState == AutoboiOn)
2. Проверка: текущая группа ботов имеет `DoRestoreHp`/`DoRestoreMa`?
3. Проверка: HP < RestoreHp%? → выпить зелье HP
4. Проверка: MA < RestoreMa%? → выпить зелье MA
5. Отправить ход боя с питьём (LezFight интеграция)

### План портирования

1. Добавить метод `mainPhpDrinkHpMa(int curHp, int maxHp, int curMa, int maxMa)` в `MainPhp.java`
2. Проверять `AppVars.Autoboi` состояние и настройки группы
3. Интегрировать с `LezFight.buildFrame()` — добавить питьё в ход боя
4. Использовать `SessionManager.getValidVCodeForAction("fight_drink")`

---

## 3. MainPhpFish — Триггер авторыбалки из main.php

### C# реализация (MainPhpFish.cs)

**Назначение**: Инициирование цикла авторыбалки при обнаружении озера на странице main.php.

**Ключевые переменные**:
- `AppVars.ContentLakeHtml` — HTML озера (уже портирован!)
- `AppVars.AutoFishDrink` — нужно ли пить перед рыбалкой
- `AppVars.AutoFishDrinkOnce` — одноразовый флаг питья
- `AppVars.Tied` — текущая усталость

**Логика C#**:
1. Обнаружить форму озера на странице (`get_id=55`)
2. Сохранить HTML в `ContentLakeHtml`
3. Если `AutoFishDrink` и усталость > порога → сначала пить
4. Иначе → вызвать `FishAjaxPhp.mainPhpAutoFishPrepareFromLakeAndroid()`

### План портирования

1. Добавить метод `mainPhpFish(String html)` в `MainPhp.java`
2. Использовать уже портированный `FishAjaxPhp.java`
3. Проверять `AppVars.Tied` (уже парсится в mainPhpInsHp)
4. Формировать цикл: питьё → рыбалка → ожидание → повтор

### Зависимости (уже есть!)
- ✅ `FishAjaxPhp.java` — полностью портирован
- ✅ `AppVars.ContentLakeHtml` — портирован
- ✅ `AppVars.Tied` — портирован
- ❌ Метод `mainPhpFish()` — НЕТ (нужно написать)

---

## 4. MainPhpAutoCure + MainPhpCure — Авто-лечение

### C# реализация (MainPhpAutoCure.cs + MainPhpCure.cs)

**Назначение**: Автоматическое лечение травм через форму врача.

**Ключевые переменные**:
- `AppVars.CureNeed` — флаг запроса на лечение
- `AppVars.CureNick` — ник персонажа для лечения
- `AppVars.CureTravm` — тип травмы (1-4)
- `AppVars.CureNickDone` — ник уже леченного
- `AppVars.CurePauseNonCombatAutoFunctions` — пауза авто-функций

**Логика C#**:
1. `MainPhpAutoCure`: если `CureNeed=true`, найти форму врача на странице
2. Заполнить поля: ник, тип травмы
3. Отправить форму через AJAX
4. `MainPhpCure`: обработать ответ врача
5. Сбросить `CureNeed=false`, обновить `CureNickDone`

### План портирования

1. Добавить методы `mainPhpAutoCure(String html)` и `mainPhpCure(String html)` в `MainPhp.java`
2. Использовать `SessionManager.getValidVCodeForAction("cure")`
3. Парсить форму врача из HTML
4. Формировать AJAX-запрос: `doctorform` с параметрами
5. Логировать через `FileLogger.trace("cure", ...)`

---

## 5. MainPhpCityNavigation — Навигация по городу

### C# реализация (MainPhpCityNavigation.cs)

**Назначение**: Автоматическая навигация через ворота города при авто-перемещении.

**Ключевые переменные**:
- `AppVars.AutoMoving` — флаг автоматического перемещения
- `AppVars.AutoMovingCityGate` — тип ворот (CityGateType enum)
- `AppVars.AutoMovingDestination` — целевая локация
- `AppVars.AutoMovingMapPath` — маршрут

**Логика C#**:
1. При авто-перемещении обнаружить кнопку ворот на странице
2. Определить тип ворот (вход/выход из города)
3. Нажать кнопку ворот автоматически
4. Продолжить перемещение по маршруту

### План портирования

1. Добавить метод `mainPhpCityNavigation(String html)` в `MainPhp.java`
2. Использовать `AppVars.AutoMovingCityGate` (уже портирован)
3. Парсить кнопки навигации из HTML
4. Формировать URL клика: `main.php?go=...&city=...`
5. Интегрировать с `AutoFunctionsManager`

---

## 6. ChMsgJs — Фильтр сообщений чата

### C# реализация (ChMsgJs.cs)

**Назначение**: Фильтрация и подсветка сообщений в чате (приват, клан, системные).

**Логика C#**:
1. Инжектировать `ChatFilter` — фильтр по словам/никам
2. Инжектировать `ChatUpdated` — callback при обновлении чата
3. Добавить SPAN alt для приватных сообщений
4. Подсветка ключевых слов

### План портирования

1. Реализовать логику в `ChMsgJs.java` (сейчас заглушка)
2. Использовать уже портированные `ChatFilter.java` и `ChatStats.java`
3. Формировать JS-инъекцию для подсветки/фильтрации
4. Интегрировать с `AndroidBridge.chatAddMsg()`

---

## Порядок реализации (рекомендуемый)

1. ✅ **MainPhpDrink** — самое критичное, зелья не пьются автоматически
2. ✅ **MainPhpDrinkHpMa** — питьё в бою
3. ✅ **MainPhpFish** — триггер рыбалки (FishAjaxPhp уже готов!)
4. ✅ **MainPhpAutoCure + MainPhpCure** — авто-лечение
5. ✅ **MainPhpCityNavigation** — навигация
6. ✅ **ChMsgJs** — фильтр чата

---

## Чек-лист перед началом портирования каждого модуля

- [ ] Прочитать C# исходный файл полностью
- [ ] Найти все зависимости (переменные AppVars, UserConfig)
- [ ] Проверить что зависимости уже портированы в Android
- [ ] Определить точку вызова в MainPhp.java (какой URL/параметр триггерит)
- [ ] Использовать SessionManager для VCode (НЕ AppVars.VCode!)
- [ ] Добавить FileLogger.trace() для критичных точек
- [ ] Протестировать с реальным сервером