# Рекомендации по портированию VCode Management на Android

## Сводка анализа ПК версии

### ✅ Что работает в C# версии

```
1. Распределённое хранилище VCode (нет единого хранилища)
   └─ Каждый модуль парсит VCode из своего контекста HTML

2. Парсинг "на лету"
   └─ VCode парсится непосредственно перед использованием

3. Единая точка обработки (Filter.Process())
   └─ Все ответы проходят обработку в одном месте

4. Немедленное использование
   └─ VCode используется в том же методе, где парсится

5. Минимальное кэширование
   └─ Только ParsedDressed хранит VCode в контексте одной обработки

6. Валидация перед использованием
   └─ Проверка наличия VCode перед использованием
```

---

## 🚫 Что НЕЛЬЗЯ делать на Android

### ❌ Ошибка 1: Использовать static хранилище VCode

```java
// ❌ НЕПРАВИЛЬНО:
public class AppState {
    public static String currentVCode;  // ← ОШИБКА!
    
    public static String getVCode() {
        return currentVCode;  // Может быть СТАРЫЙ VCode!
    }
}

// Применение:
AppState.currentVCode = vcode;  // Сохраняем в одном месте
// ... потом в другом месте:
String link = "main.php?vcode=" + AppState.getVCode();  // ← НЕАКТУАЛЬНЫЙ!
```

**Почему неправильно:**
- VCode может устаревать между вызовами
- "Другое место" может быть другой операцией с другим контекстом
- Возможны гонки между модулями

### ❌ Ошибка 2: Кэшировать VCode на долго

```java
// ❌ НЕПРАВИЛЬНО:
private String cachedVCode;
private long cachedVCodeTime;

void cacheVCode(String vcode) {
    this.cachedVCode = vcode;
    this.cachedVCodeTime = System.currentTimeMillis();
}

// ... позже, после других операций:
String link = "main.php?vcode=" + cachedVCode;  // ← МОЖЕТ БЫТЬ НЕАКТУАЛЕН!
```

**Почему неправильно:**
- VCode действительна только чтобы одного запроса
- Кэш может быть использован после контекстного переключения
- Результат = "Неверный код защиты"

### ❌ Ошибка 3: Не проверять наличие VCode

```java
// ❌ НЕПРАВИЛЬНО:
String vcode = extractVCode(html);  // Может быть null/пусто
String link = "main.php?vcode=" + vcode;  // ← CRASH или ОШИБКА!
```

**Почему неправильно:**
- Может быть null/пусто, если HTML некорректное
- Результат = ошибка на сервере
- Нет graceful degradation

---

## ✅ Правильный подход для Android

### Архитектурный паттерн

```
┌──────────────────────────────────────────┐
│        WebView с игровым HTML           │
└──────────┬───────────────────────────────┘
           │ onPageFinished()
           ▼
┌──────────────────────────────────────────┐
│  HTTPResponseInterceptor (или Bridge)    │  ← ЕДИНАЯ ТОЧКА
│  обрабатывает ВСЕ ответы                │
└──────────┬───────────────────────────────┘
           │ Определяет тип ответа
    ┌──────┼──────┬────────┬───────┐
    │      │      │        │       │
    ▼      ▼      ▼        ▼       ▼
 FishJs  BattleJs FishAjax FastAct MainPhp
    │      │      │        │       │
    └──────┴──────┴────────┴───────┘
           │
           ▼ Каждый тип:
        1. Парсит VCode из WebView DOM
        2. Валидирует наличие
        3. Использует немедленно
        4. НЕ сохраняет для потом
        5. Возвращает результат/ссылку с VCode
```

### Рекомендуемая структура кода

```java
// 1. Главная точка обработки
public class GameResponseHandler {
    public String handleResponse(String url, String html) {
        // Единая точка обработки всех ответов

        if (url.contains("main.php")) {
            return handleMainPhp(url, html);
        } else if (url.contains("fish_ajax.php")) {
            return handleFishAjax(html);
        } else if (url.contains("fight")) {
            return handleFight(html);
        }
        
        return html;
    }
}

// 2. Каждый обработчик парсит собственный VCode
public class FishingHandler {
    public String handleFishAjax(String html) {
        // Парсим VCode для рыбалки
        String vcode = extractVCode(html, "=vcode value=", ">");
        
        // Валидируем
        if (vcode == null || vcode.isEmpty()) {
            return "";  // Отмена, VCode нет
        }
        
        // Используем немедленно
        String lake = extractParam(html, "=lakeid value=", ">");
        String action = "4";
        String bait = "38";
        
        // Строим ссылку с VCode
        String link = String.format(
            "main.php?get_id=55&lakeid=%s&act=%s&primid=%s&vcode=%s",
            lake, action, bait, vcode
        );
        
        // ГЛАВНОЕ: vcode - локальная переменная, не сохраняем!
        return link;
    }
    
    // Утилита парсинга
    private String extractVCode(String html, String startMarker, String endMarker) {
        try {
            int start = html.indexOf(startMarker);
            if (start == -1) return null;
            
            start += startMarker.length();
            int end = html.indexOf(endMarker, start);
            if (end == -1) return null;
            
            return html.substring(start, end).trim();
        } catch (Exception e) {
            Log.e("VCodeParser", "Error parsing VCode", e);
            return null;
        }
    }
}

// 3. Использование в контроллере
public class GameActivity extends AppCompatActivity {
    private GameResponseHandler responseHandler = new GameResponseHandler();
    
    private void onGameActionRequired(String url, String html) {
        // Обработка ответа с приоритетом
        String result = responseHandler.handleResponse(url, html);
        
        if (result != null && !result.isEmpty()) {
            // Используем результат (может быть ссылка с VCode)
            navigateToUrl(result);
        }
    }
}
```

---

## 📋 Чек-лист портирования VCode Management

### Фаза 1: Анализ и планирование

- [ ] Изучить Filter.Process() в C# версии
- [ ] Понять, где парсируется каждый VCode
- [ ] Определить соответствующие точки обработки в Android
- [ ] Выбрать механизм перехвата ответов (Bridge, Interceptor, JS injection)
- [ ] Создать план синхронизации между модулями

### Фаза 2: Реализация основной архитектуры

- [ ] Создать GameResponseHandler (аналог Filter.Process())
- [ ] Реализовать распределение по типам (main.php, fish_ajax, fight, и т.д.)
- [ ] Создать базовый парсер VCode с HelperStrings.SubString() аналогом
- [ ] Реализовать валидацию VCode (null/empty check)

### Фаза 3: Модульная реализация

- [ ] **Рыбалка:** Парсинг VCode из fish_ajax ответа
  - [ ] Использовать pattern: `"=vcode value=" ... ">"`
  - [ ] Валидировать перед использованием
  - [ ] Не сохранять между рыбалками

- [ ] **Бой:** Парсинг VCode из боевого ответа
  - [ ] Разбор результата по "|"
  - [ ] VCode из ss[0]
  - [ ] Использование в form_main

- [ ] **Быстрые действия:** Парсинг VCode из onclick
  - [ ] Извлечение из onclick="w28_form('vcode', ...)"
  - [ ] Использование в hidden input
  - [ ] Auto-submit форма с VCode

- [ ] **Вещи:** Парсинг VCode из slots_inv()
  - [ ] Структура: pslots[4].split('@')[2]
  - [ ] Хранение в ParsedDressed.Vcod
  - [ ] Использование при одевании/снимании

### Фаза 4: Тестирование и отладка

- [ ] Рыбалка: VCode корректно парсится и используется
- [ ] Бой: Боевые ходы работают с новым VCode
- [ ] Телепорты: VCode в w28_form() работает
- [ ] Вещи: Одевание/снимание использует актуальный VCode
- [ ] Переходы: VCode обновляется при переключении между модулями
- [ ] Ошибки: "Неверный код защиты" не появляется

---

## 🔍 Диагностика проблем с VCode

### Если получается "Неверный код защиты"

1. **Проверить, парсится ли VCode из HTML:**
```java
String html = webView.getContentAsString();
String vcode = extractVCode(html, "=vcode value=", ">");
Log.d("VCode", "Parsed VCode: " + vcode);
// Если null/пусто → проблема в парсинге
```

2. **Проверить, используется ли правильный VCode:**
```java
// Перед использованием:
if (vcode == null || vcode.isEmpty()) {
    Log.w("VCode", "VCode is empty! Aborting action");
    return;  // Отмена действия
}
```

3. **Проверить, не устарел ли VCode:**
```java
// VCode должен использоваться НЕМЕДЛЕННО после парсинга
// НЕ сохранять для потом!

// ❌ ПЛОХО:
this.savedVCode = vcode;  // Сохраняем
// ... другие операции ...
String link = "main.php?vcode=" + this.savedVCode;  // Может быть старый!

// ✅ ХОРОШО:
String link = "main.php?vcode=" + vcode;  // Используем сразу же
```

4. **Проверить контекст переключения:**
```java
// Если операция переключает контекст (например, переходит с рыбалки на войну):
// 1. Переход на main.php (получить новый контекст)
// 2. Переход в боевой режим (боевой сервер дает свой VCode)
// 3. VCode должен быть разным!

// НЕ использовать VCode из рыбалки в войне!
```

---

## 💾 Где должна быть реализация VCode Management

### На уровне модуля `app`

```
app/src/main/java/ru/neverlands/abclient/
├── manager/
│   ├── GameResponseHandler.java  ← ГЛАВНЫЙ КЛАСС обработки ответов
│   ├── FishingResponseHandler.java
│   ├── BattleResponseHandler.java
│   ├── FastActionResponseHandler.java
│   └── InventoryResponseHandler.java
│
├── util/
│   ├── HtmlParser.java  ← Утилита парсинга (аналог HelperStrings)
│   └── VCodeValidator.java  ← Валидация VCode
│
└── integration/
    ├── GameJSBridge.java  ← Точка обработки ответов из WebView
    └── HTTPInterceptor.java  ← Перехват HTTP ответов
```

---

## 🎯 Ключевые моменты

**Помните:**
1. ✅ **VCode - одноразовая переменная**, не глобальное хранилище
2. ✅ **Парсить из текущего HTML** перед каждым использованием
3. ✅ **Валидировать перед использованием** (null/empty check)
4. ✅ **Использовать немедленно** в том же методе
5. ✅ **Не кэшировать** между разными операциями
6. ✅ **Обрабатывать в единной точке** (GameResponseHandler)
7. ✅ **Синхронизировать через** эту единую точку

**Результат:**
```
✓ Нет потерь VCode
✓ Нет конфликтов между модулями
✓ "Неверный код защиты" не случается
✓ Система остается стабильной при переключениях контекстов
```

---

## Дополнительные ресурсы

- [VCode_Management_Analysis.md](VCode_Management_Analysis.md) - Полный анализ архитектуры
- [VCode_Management_QUICK_REFERENCE.md](VCode_Management_QUICK_REFERENCE.md) - Краткие справочные таблицы
- [VCode_Management_CODE_EXAMPLES.md](VCode_Management_CODE_EXAMPLES.md) - Примеры кода из C#

