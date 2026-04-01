# АНАЛИЗ: Архитектура цикла авто-рыбалки (ПК vs Android)

**Дата**: 01.04.2026  
**Статус**: Критичный анализ

---

## 1. ПРОБЛЕМА В СЖАТОМ ВИДЕ

### ПК-версия (ПРАВИЛЬНАЯ АРХИТЕКТУРА):
```
ЦИКЛ:
1. Загружает main.php?get_id=55 (свежие данные озера)
   ↓
2. Парсит вкладку рыбацких параметров (MainPhpAutoFishPrepare)
   - Извлекает vcode ИЗ этого HTML (строки 44-51)
   ↓
3. Выбирает доступную приманку (перетасовка + проверка остатка)
   ↓
4. Формирует submit URL с vcode для act=4 (или act=2 при капче)
   ↓
5. Проверяет наличие капчи (CodeAddress)
   - Если капча → показывает диалог + ждет ввода
   - Если нет → отправляет act=4 сразу
   ↓
6. После каждого цикла сервер даёт cooldown (@[0,[2,294]]@)
   ↓
7. Ждёт cooldown, затем повторяет ШАГ 1
```

**Ключевой момент**: Вкладка `main.php?get_id=55` содержит **СВЕЖИЙ** vcode, который передал сервер ИМЕННО ДЛЯ ЭТОГО шага рыбалки.

---

## 2. АРХИТЕКТУРА Android (ТЕКУЩАЯ - НЕПРАВИЛЬНАЯ)

### Текущая реализация:
```
ЦИКЛ (неправильный):
1. На странице (обычно main.php?get_id=56&act=10&go=inf)
   ↓
2. processFishAct1() обрабатывает ПЕРИОДИЧЕСКИЙ GET fish_ajax.php?act=1
   - Это НЕ основной цикл, а ВСПОМОГАТЕЛЬНАЯ проверка!
   ↓
3. Парсит результат act=1 (текущие параметры рыбо-локации)
   - Извлекает vcode ИЗ этого ответа
   ↓
4. Если нет капчи → scheduleNoCaptchaAct2Fallback()
   - Отправляет act=2 через JS
   ↓
5. Если есть капча → showFishCaptchaDialogOnce()
   - Показывает диалог
```

**ПРОБЛЕМЫ**:
- ❌ act=1 это НЕ основной ЦИКЛ, а вспомогательный зонд
- ❌ Нет `main.php?get_id=55` загрузки перед действием
- ❌ Нет выбора приманки через перетасовку + проверку остатка
- ❌ Нет `MainPhpAutoFishPrepare()` эквивалента в Android
- ❌ vcode парсится из `act=1`, а не из основного HTML инвентаря приманок

---

## 3. СРАВНЕНИЕ: ПК vs Android

### Часть 1: Инициирование цикла

**ПК-версия** (`MainPhpFish.cs` + `MainPhpWtime.cs`):
```csharp
// Основной loop в FormMainTick(s):
// 1. На каждый tick проверяется NeverTimer
// 2. Если время пришло и AutoFish=true:
//    → GET main.php?get_id=55 (озеро)
//    → MainPhpAutoFishPrepare(html)
//       - Извлекает свежий vcode ИЗ HTML (строка 44-51)
//       - Выбирает приманку
//       - Проверяет капчу (CodeAddress)
//       - Формирует FightLink для act=4/act=2
// 3. Сервер отвечает кулдауном (@[0,[2,294]]@)
// 4. NeverTimer обновляется для следующего цикла
```

**Android-версия** (`FishAjaxPhp.java`):
```java
// Обработка fish_ajax.php (NOT основной loop!):
// act=1: Получается параметры текущего шага
//   - Но это НЕ основная рыбацкая страница!
//   - Это зонд-запрос, который запускается откуда-то ещё
//
// ❌ НЕДОСТАЁТ: Основной цикл, который:
//   - Загружает main.php?get_id=55
//   - Вызывает mainPhpAutoFishPrepare()
//   - Парсит vcode из ОСНОВНОГО инвентаря
```

**Где запускается основной цикл?**
```
Linux grep ищет "main.php?get_id=55":
  - НЕ найдено в FishAjaxPhp.java
  - НЕ найдено в MainActivity.java для AutoFish
  - Цикл ОТСУТСТВУЕТ или неправильно реализован!
```

---

### Часть 2: Парсинг vcode

**ПК-версия**:
```csharp
// MainPhpAutoFishPrepare (строки 44-51):
var vcode = HelperStrings.SubString(html, "=vcode value=", ">");
if (string.IsNullOrEmpty(vcode))
{
    return string.Empty;  // Критична ошибка!
}
AppVars.FightLink = "http://www.neverlands.ru/main.php?get_id=" + getid + "...&vcode=" + vcode;
```

**Android-версия** (`FishAjaxPhp.java::processFishAct1`):
```java
// Парсит из act=1 response:
FishAct1State state = parseFishAct1State(html);
AppVars.FishCurrentVcode = state.vcode;

// ❌ ПРОБЛЕМА: Это vcode из act=1 (вспомогательного запроса)
// ✓ ПРАВИЛЬНО: Должен быть vcode из main.php?get_id=55 (основного инвентаря)
```

---

### Часть 3: Выбор приманки

**ПК-версия** (`MainPhpFish.cs` строки 65-126):
```csharp
// 1. Создаёт список ВКЛЮЧЁННЫХ приманок из Profile.FishEnabledPrims
// 2. ПЕРЕТАСОВЫВАЕТ (Dice.Make) случайный порядок
// 3. ДЛЯ КАЖДОЙ приманки в порядке:
//    - Ищет в HTML `<input type=radio name=primid value=NN>`
//    - ПРОВЕРЯЕТ ОСТАТОК (количество после <b>..)</b>)
//    - Если остаток НЕ найден или достаточен → выбирает её
//    - Вставляет checked атрибут в radio button
// 4. Если ни одна не подходит → отключает AutoFish

// Комментарий в коде:
var temp = "<input type=radio name=primid value=" + primid + "></td>...<b>";
var pos = html.IndexOf(temp, StringComparison.OrdinalIgnoreCase);
if (pos != -1)
{
    html = html.Insert(pos + 18, "checked ");  // Вставляет checked
}
```

**Android-версия** (`FishAjaxPhp.java::selectAllowedBait`):
```java
// ❌ НЕ РЕАЛИЗОВАНО!
// Нет эквивалента MainPhpAutoFishPrepare
// selectAllowedBait работает только с act=1 response
```

---

### Часть 4: Формирование запроса

**ПК-версия**:
```
act=4 (или act=2 с капчей):
  /main.php?get_id=55&lakeid=1&act=4&primid=39&vcode=VVVV
или с капчей:
  /main.php?get_id=55&lakeid=1&act=4&primid=39&code=????&vcode=VVVV
  → показать диалог капчи (code.php?TTTTT)
```

**Android-версия**:
```
act=1 → /gameplay/ajax/fish_ajax.php?act=1
act=2 → /gameplay/ajax/fish_ajax.php?act=2&primid=39&vcode=VVVV&code=????

❌ Это AJAX-запросы, а не основной цикл!
```

---

## 4. КРИТИЧНЫЕ РАЗЛИЧИЯ

| Аспект | ПК-версия | Android | Результат |
|--------|-----------|---------|-----------|
| **Загрузка данных** | Каждый цикл: GET main.php?get_id=55 | ❌ Нет основной загрузки | Используется УСТАРЕВШИЙ vcode |
| **Парсинг vcode** | Из HTML озера (get_id=55) | Из act=1 response | **КОНФЛИКТ**: разные места парсинга |
| **Выбор приманки** | mainPhpAutoFishPrepare() с перетасовкой | selectAllowedBait() из act=1 | **НЕСООТВЕТСТВИЕ**: разная логика |
| **Проверка остатка** | Проверяется в HTML озера | ❌ Не проверяется | **МОЖЕТ ПРИВЕСТИ К ОШИБКЕ**: использование несуществующей приманки |
| **Цикл запуска** | FormMainTick → NeverTimer | kickFishCycleAttempt() | **ПУТАНИЦА**: два разных механизма |
| **Тайминг** | Синхронизирован с @[0,[2,294]]@ | scheduleNoCaptchaAct2Fallback() | **АСИНХРОННОСТЬ**: несогласованность |

---

## 5. ПРАВИЛЬНАЯ АРХИТЕКТУРА ДЛЯ ANDROID

Должна быть реализована СЕРИЯ ПОСЛЕДОВАТЕЛЬНЫХ ШАГОВ:

```
┌─────────────────────────────────────────────────────────────────┐
│ ОСНОВНОЙ ЦИКЛ АВТО-РЫБАЛКИ (Должен существовать)              │
└─────────────────────────────────────────────────────────────────┘

ШАГ 1: На scheduleNoCaptchaAct2Fallback() или kickFishCycleAttempt()
  └─ Проверка: cooldown прошёл?
      └─ Если да → идём на ШАГ 2

ШАГ 2: Загружаем СВЕЖИЙ HTML озера
  └─ GET main.php?get_id=55&lakeid=NN
  └─ Ожидаем ответ (FRESH HTML)

ШАГ 3: Парсим mainPhpAutoFishPrepare(html)
  └─ Извлекаем свежий vcode ИЗ ЭТОГО HTML
  └─ Выбираем доступную приманку с проверкой остатка
  └─ Проверяем CodeAddress (капча ли?)
  └─ Сохраняем AppVars.FishCurrentVcode (ИЗ ШАГ 3)

ШАГ 4: Формируем URL действия
  └─ Если нет капчи:
      └─ GET main.php?get_id=55&lakeid=NN&act=4&primid=PP&vcode=SWAG
  └─ Если капча:
      └─ Показываем диалог с code.php?TOKEN

ШАГ 5: Обрабатываем ответ
  └─ Парсим хук @[0,[2,294]]@ (новый cooldown)
  └─ Обновляем NeverTimer
  └─ Добавляем в чат отчёт об улове
  └─ Переходим на ШАГ 1 через cooldown

┌─────────────────────────────────────────────────────────────────┐
│ ВСПОМОГАТЕЛЬНЫЕ ОПЕРАЦИИ (НЕ основной цикл, но поддерживают)    │
└─────────────────────────────────────────────────────────────────┘

• act=1 (fish_ajax.php?act=1) - только для синхронизации промежуточных данных
• act=2 fallback - только если JS не отправил act=2 сам
• Fish captcha dialog - показываем, ждём ввода, переотправляем
```

---

## 6. ГДЕ СЕЙЧАС НЕДОСТАВЛЯЕТ ФУНКЦИОНАЛА

### В FishAjaxPhp.java есть:
- ✅ processFishAct1() - обработка act=1
- ✅ processFishAct2/syncFishCooldownAndScheduleNextCycle() - обработка act=2
- ✅ scheduleNoCaptchaAct2Fallback() - fallback отправка act=2
- ✅ kickFishCycleAttempt() - попытка запустить цикл
- ❌ **ОТСУТСТВУЕТ**: Загрузка main.php?get_id=55 перед действием
- ❌ **ОТСУТСТВУЕТ**: Вызов mainPhpAutoFishPrepare()
- ❌ **ОТСУТСТВУЕТ**: Парсинг vcode из озера, а не из act=1

### В MainPhp.java есть:
- ✅ mainPhpAutoFishPrepare() - подготовка (но КОГДА вызывается?)
- ❌ **НЕДОСТАТОЧНАЯ ИНТЕГРАЦИЯ**: mainPhpAutoFishPrepare() не вызывается в правильный момент цикла

---

## 7. КЕЙС-ИССЛЕДОВАНИЕ: Почему хисит vcode

### Сценарий 1: Используется УСТАРЕВШИЙ vcode
```
Цикл N:
  1. processFishAct1() получает свежий vcode из act=1
  2. scheduleNoCaptchaAct2Fallback() отправляет это vcode
  3. Но это vcode может быть парсен ИЗ ПРЕДЫДУЩЕГО ОТВЕТА!

Цикл N+1:
  1. Сервер может требовать НОВЫЙ vcode для озера
  2. Приложение использует СТАРЫЙ
  3. Сервер отвечает ошибкой: "неверный защитный код"
```

### Сценарий 2: Используется vcode из неправильной страницы
```
ПК-версия:
  app.php?get_id=55 (озеро) → vcode из этой страницы

Android-версия:
  fish_ajax.php?act=1 → vcode из этого AJAX-ответа
  ≠ main.php?get_id=55!

Результат:
  vcode может быть для ДРУГОГО действия!
```

---

## 8. ТРЕБУЕМЫЕ ИСПРАВЛЕНИЯ (ПРИОРИТЕТ)

### КРИТИЧНЫ (АРХИТЕКТУРНЫЕ):

**1. Создать основной LOOP авто-рыбалки** (`FishAjaxPhp.java`):
```java
private static void executeFishingCycleStep() {
    if (!isAutoFishEnabled()) return;
    
    // Проверка cooldown
    long now = System.currentTimeMillis();
    if (now < AppVars.NeverTimer) return;
    
    // ★ ЗАГРУЖАЕМ СВЕЖИЙ HTML ОЗЕРА ★
    String lakeHtml = loadFreshLakeHtml();  // main.php?get_id=55
    if (lakeHtml == null) {
        // Retry с задержкой
        scheduleRetry();
        return;
    }
    
    // ★ ПРАВИЛЬНЫЙ ПАРСИНГ ★
    String vcode = parseFishVcodeFromLakeHtml(lakeHtml);
    String bait = selectBaitFromLakeHtml(lakeHtml);
    String codeAddress = parsCodeAddressFromLakeHtml(lakeHtml);
    
    if (vcode == null || bait == null) {
        disableAutoFish("Invalid lake state");
        return;
    }
    
    // Сохраняем для дальнейшей обработки
    AppVars.FishCurrentVcode = vcode;
    AppVars.AutoFishLikeId = bait;
    
    // Если есть капча - показываем диалог
    if (codeAddress != null) {
        showCaptchaDialog(codeAddress, buildFishUrl(vcode, bait));
    } else {
        // Отправляем действие прямо
        submitFishingAction(vcode, bait);
    }
}

private static String loadFreshLakeHtml() {
    // Загружаем main.php?get_id=55
    // Это должно быть синхронным запросом или обработчиком WebView загрузки
}
```

**2. Интегрировать mainPhpAutoFishPrepare() в правильный момент**:
- Вызывать ПЕРЕД каждым действием цикла (не на обработке act=1, а на ЗАГРУЗКЕ озера)

**3. Убедиться, что vcode всегда СВЕЖИЙ**:
- Вместо парсинга из act=1 → парсить из ответа main.php?get_id=55

### ВАЖНЫЕ (ИСПРАВЛЕНИЯ):
- Проверять остаток приманки ДО использования
- Убедиться в правильной синхронизации cooldown (@[0,[2,294]]@)
- Обновить логирование для отслеживания vcode источника

### ОПЦИОНАЛЬНЫЕ (ОПТИМИЗАЦИЯ):
- Кэширование озера между попытками (если cooldown < 5сек)
- Параллель предварительной загрузки озера в фоне

---

## 9. ИТОГИ

### ПК-версия:
- ✅ **ПРАВИЛЬНАЯ** архитектура: `main.php?get_id=55` → парсинг → действие
- ✅ Свежий vcode для каждого цикла
- ✅ Проверка остатка приманки
- ✅ Синхронизация с cooldown

### Android-версия (ТЕКУЩАЯ):
- ❌ **НЕПРАВИЛЬНАЯ** архитектура: act=1 → парсинг → act=2
- ❌ Отсутствует загрузка озера
- ❌ Отсутствует mainPhpAutoFishPrepare()
- ❌ Могут использоваться устаревшие параметры

### Что нужно сделать:
1. **Создать основной цикл авто-рыбалки**, который:
   - Загружает main.php?get_id=55 перед действием
   - Парсит свежий vcode ИЗ ЭТОГО HTML
   - Выбирает приманку с проверкой остатка
   
2. **Интегрировать mainPhpAutoFishPrepare()** в правильный момент

3. **Гарантировать, что vcode ВСЕГДА свежий** (не из кэша)

4. **Синхронизировать с NeverTimer** на основе @[0,[2,294]]@

---

## РЕКОМЕНДАЦИЯ

🔴 **ПРИОРИТЕТ: КРИТИЧНЫЙ**

Без этого исправления авто-рыбалка будет:
- Использовать устаревшие vcode
- Получать ошибки от сервера
- Периодически "зависать" с неверными параметрами
- Не соответствовать архитектуре ПК-версии

**Начать с**: Создание основного цикла фишинга в FishAjaxPhp.java
