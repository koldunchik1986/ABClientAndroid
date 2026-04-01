# ПОШАГОВОЕ РУКОВОДСТВО - Вставка кода авто-рыбалки

**Дата:** 1 апреля 2026  
**Статус:** ПОШАГОВАЯ ИНСТРУКЦИЯ  
**Время на реализацию:** 30-45 минут  

---

## 📋 ПРЕДВАРИТЕЛЬНАЯ ПРОВЕРКА

### ✅ ШАГ 0: Убедиться что ContentLakeHtml есть

```bash
# КОМАНДА: Поиск в AppVars.java
grep ContentLakeHtml app/src/main/java/ru/neverlands/abclient/utils/AppVars.java
```

**Ожидаемый результат:**
```java
public static String ContentLakeHtml = "";
```

Если НЕ найдено, добавить в AppVars.java (около строки 150):
```java
public static String ContentLakeHtml = "";  // Кэш HTML озера для парсинга приманок
```

---

## 🔴 ШАГ 1: MainPhp.java - Кэширование озера

### 1.1 ОТКРЫТЬ файл
```
File: app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java
Найти: mainPhpFindFish() [строка ~2123]
```

### 1.2 НАЙТИ этот блок кода:

**ТЕКУЩИЙ КОД (строки 2123-2143):**
```java
private static String mainPhpFindFish(String html) {
    if (html == null || html.isEmpty()) {
        return null;
    }
    String pattern = "[\"fis\",\"Рыбалка\",\"";
    int posPattern = html.indexOf(pattern);
    if (posPattern == -1) {
        return null;
    }
    posPattern += pattern.length();
    int posEnd = html.indexOf('"', posPattern);
    if (posEnd == -1) {
        return null;
    }
    String vcode = html.substring(posPattern, posEnd);
    if (vcode.isEmpty()) {
        return null;
    }
    String callFish = "Fish('" + vcode + "');";
    String patternViewMap = "view_map();";
    int posScript = html.indexOf(patternViewMap);
    // ... ДАЛЬШЕ ...
    posScript += patternViewMap.length();
    return html.substring(0, posScript) + callFish + html.substring(posScript);  // ← ПЕРЕД ЭТИМ
}
```

### 1.3 ВСТАВИТЬ ДО финальной `return`:

**НАЙТИ СТРОКУ:**
```java
    posScript += patternViewMap.length();
    return html.substring(0, posScript) + callFish + html.substring(posScript);
```

**ЗАМЕНИТЬ НА:**
```java
    posScript += patternViewMap.length();
    
    // ===== ДОБАВИТЬ ЭТИ 5 СТРОК =====
    if (AppVars.ContentLakeHtml == null || AppVars.ContentLakeHtml.isEmpty()) {
        AppVars.ContentLakeHtml = extractLakeFishFormHtml(html);
        android.util.Log.d("MainPhp", "AUTO_FISH_TRACE cached ContentLakeHtml, length=" + 
            (AppVars.ContentLakeHtml != null ? AppVars.ContentLakeHtml.length() : 0));
    }
    // ===== КОНЕЦ ДОБАВЛЕНИЯ =====
    
    return html.substring(0, posScript) + callFish + html.substring(posScript);
```

### 1.4 ДОБАВИТЬ вспомогательный метод

**МЕСТО:** В конец класса MainPhp (перед закрывающей }), добавить:

```java
/**
 * Извлекает HTML форму рыбалки (id="FISHF") из полной страницы озера.
 */
private static String extractLakeFishFormHtml(String html) {
    if (html == null || html.isEmpty()) return "";
    
    String lowerHtml = html.toLowerCase(Locale.ROOT);
    int posForm = lowerHtml.indexOf("id=\"fishf\"");
    if (posForm == -1) {
        posForm = lowerHtml.indexOf("id='fishf'");
    }
    if (posForm == -1) {
        return "";
    }
    
    int formStart = html.lastIndexOf("<form", posForm);
    if (formStart == -1) return "";
    
    int formEnd = html.indexOf("</form>", formStart);
    if (formEnd == -1) return "";
    
    formEnd += "</form>".length();
    return html.substring(formStart, formEnd);
}
```

### ✅ ПРОВЕРИТЬ:
```bash
# Должна скомпилироваться без ошибок
./gradlew.bat compileDebugJavaWithJavac 2>&1 | grep -i error
```

---

## 🟠 ШАГ 2: MainActivity.java - Удалить refreshFishVcodeInUrl

### 2.1 ПОИСК метода

```bash
# Поиск в MainActivity.java
grep -n "refreshFishVcodeInUrl" app/src/main/java/ru/neverlands/abclient/MainActivity.java
```

### 2.2 ЕСЛИ НАЙДЕНО - УДАЛИТЬ весь метод:

**ЧТО ИЩЕМ:**
```java
private void refreshFishVcodeInUrl(...) {
    // весь метод
    ...
}
```

**ДЕЙСТВИЕ:** Удалить полностью и все вызовы вида:
- `refreshFishVcodeInUrl(...);`
- `this.refreshFishVcodeInUrl(...);`

### 2.3 УБЕДИТЬСЯ что submitCaptchaSolution() ПРАВИЛЬНЫЙ:

**ДОЛЖНО БЫТЬ (строка ~2025):**
```java
private void submitCaptchaSolution(String submitUrl, boolean isFishCaptcha) {
    if (binding == null || binding.appBarMain == null || binding.appBarMain.contentMain == null) {
        Log.w(TAG, "submitCaptchaSolution: skip, binding/content is null");
        return;
    }
    WebView mainWebView = binding.appBarMain.contentMain.webView;
    if (mainWebView == null) {
        Log.w(TAG, "submitCaptchaSolution: skip, mainWebView is null");
        return;
    }
    if (isFishCaptcha) {
        submitFishCaptchaViaAjaxOrFallback(mainWebView, submitUrl);
        return;
    }
    mainWebView.loadUrl(submitUrl);
}
```

**НЕ ДОЛЖНО БЫТЬ:** никаких вызовов `refreshFishVcodeInUrl`

### ✅ ПРОВЕРИТЬ:
```bash
grep "refreshFishVcodeInUrl" app/src/main/java/ru/neverlands/abclient/MainActivity.java
# Должно вывести: (no matches)
```

---

## 🔵 ШАГ 3: FishAjaxPhp.java - Добавить методы

### 3.1 ОТКРЫТЬ файл

```
File: app/src/main/java/ru/neverlands/abclient/postfilter/FishAjaxPhp.java
Найти: конец файла (перед последней })
Строка примерно: 1200-1250 (зависит от текущего размера)
```

### 3.2 НАЙТИ КОНЕЦ класса

**ИЩЕМ ПОСЛЕДНИЕ СТРОКИ:**
```java
        return null;
    }
}  // ← ЭТА СКОБКА - конец класса FishAjaxPhp
```

### 3.3 ВСТАВИТЬ перед закрывающей скобкой

**ВСТАВИТЬ ВСЕ 7 блоков кода (см. FISHING_AUTOFISH_COMPLETE_CODE.md):**

1. `executeFishingCycleCore()` + `scheduleNextFishingCycleAttempt()` (60 строк)
2. `mainPhpAutoFishPrepareFromLakeAndroid()` (70 строк)
3. `selectBaitFromLakeHtmlAndroid()` (90 строк)
4. `LakeParseResult` класс (20 строк)
5. `BaitSelectionResult` класс (20 строк)
6. `parseFishAct1State()` + `FishAct1State` класс (100 строк)
7. Вспомогательные методы (150 строк)

**ПРАВИЛО ВСТАВКИ:** Каждый блок вставляется ПЕРЕД `}` конца класса

### 3.4 ПРИМЕР вставки для блока #1:

**ТЕКУЩИЙ КОД (конец файла):**
```java
        return null;
    }
}
```

**НОВЫЙ КОД:**
```java
        return null;
    }
    
    /**
     * ===== ОСНОВНОЙ ЦИКЛ АВТО-РЫБАЛКИ (НОВЫЙ МЕТОД) =====
     * ...
     * [весь код executeFishingCycleCore и т.д.]
     */
    private static void executeFishingCycleCore(String reason) {
        // ... содержимое метода ...
    }
    
    /**
     * Планирует следующий цикл авто-рыбалки с задержкой.
     */
    private static void scheduleNextFishingCycleAttempt(...) {
        // ... содержимое метода ...
    }
    
    // ... остальные методы и классы ...
    
}  // ← КОНЕЦ КЛАССА
```

### 3.5 ИМПОРТЫ

**УБЕДИТЬСЯ что в начале FishAjaxPhp.java есть:**
```java
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.neverlands.abclient.utils.HelperStrings;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.model.UserConfig;
```

Если чего-то нет, добавить в начало файла

### ✅ ПРОВЕРИТЬ компиляцию:

```bash
# Компиляция
./gradlew.bat compileDebugJavaWithJavac 2>&1 | head -50

# Если есть ошибки - вывести их
./gradlew.bat compileDebugJavaWithJavac 2>&1 | grep -i error
```

---

## 🟢 ШАГ 4: Финальная сборка и проверка

### 4.1 СБОРКА APK

```bash
# Полная сборка debug APK
./gradlew.bat assembleDebug 2>&1 | tail -20
```

**ОЖИДАЕМЫЙ РЕЗУЛЬТАТ:**
```
BUILD SUCCESSFUL in Xs
```

### 4.2 ЕСЛИ ОШИБКИ компиляции

**ТИПИЧНЫЕ ОШИБКИ:**

1. `Cannot find symbol: class LakeParseResult`
   - РЕШЕНИЕ: Убедиться что все 4 класса вставлены (DTO + вспомогательные)

2. `Method extractLakeFishFormHtml is undefined`
   - РЕШЕНИЕ: Убедиться что метод добавлен в MainPhp.java

3. `Package ru.neverlands... cannot be resolved`
   - РЕШЕНИЕ: Проверить импорты в начале файла

### 4.3 ПРОВЕРКА ЛОГИРОВАНИЯ

После установки APK, запустите в Android Studio:
```bash
adb logcat | grep AUTO_FISH_TRACE
```

**ДОЛЖНЫ ВИДЕТЬ ЛОГИ:**
```
AUTO_FISH_TRACE cached ContentLakeHtml, length=5432
AUTO_FISH_TRACE cycle: bootstrap act=1
AUTO_FISH_TRACE act1: captcha not required, primid=40
```

---

## 📊 ЧЕКЛИСТ ВСТАВКИ

| № | Файл | Действие | Статус |
|---|------|---------|--------|
| 1 | MainPhp.java | Добавить 5 строк кэширования | [ ] |
| 1b | MainPhp.java | Добавить extractLakeFishFormHtml() | [ ] |
| 2 | MainActivity.java | Удалить refreshFishVcodeInUrl | [ ] |
| 3 | FishAjaxPhp.java | Добавить executeFishingCycleCore() | [ ] |
| 4 | FishAjaxPhp.java | Добавить mainPhpAutoFishPrepareFromLakeAndroid() | [ ] |
| 5 | FishAjaxPhp.java | Добавить selectBaitFromLakeHtmlAndroid() | [ ] |
| 6 | FishAjaxPhp.java | Добавить LakeParseResult класс | [ ] |
| 7 | FishAjaxPhp.java | Добавить BaitSelectionResult класс | [ ] |
| 8 | FishAjaxPhp.java | Добавить parseFishAct1State() | [ ] |
| 9 | FishAjaxPhp.java | Добавить вспомогательные методы | [ ] |
| 10 | Все файлы | Компиляция без ошибок | [ ] |
| 11 | Все файлы | Сборка APK успешна | [ ] |

---

## 🚀 ЗАПУСК ТЕСТИРОВАНИЯ

### После успешной сборки:

```bash
# 1. Установить APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Открыть приложение на телефоне
# 3. Включить авто-рыбалку в настройках
# 4. Посмотреть логи
adb logcat ru.neverlands.abclient | grep AUTO_FISH_TRACE

# 5. Проверить озеро загрузилось:
adb logcat ru.neverlands.abclient | grep "ContentLakeHtml"
```

---

## 🆘 ЕСЛИ ВОЗНИКАЮТ ОШИБКИ

### Ошибка 1: "Переменная ContentLakeHtml не инициализирована"

```
РЕШЕНИЕ:
1. Открыть AppVars.java
2. Найти: public static String ContentLakeHtml
3. Убедиться что initialization: = "";
```

### Ошибка 2: "Cannot resolve symbol: LakeParseResult"

```
РЕШЕНИЕ:
1. Открыть FishAjaxPhp.java
2. ВСТАВИТЬ класс LakeParseResult полностью
3. Убедиться что на строке выше написано: private static final class LakeParseResult
```

### Ошибка 3: "Method is not defined in class FishAjaxPhp"

```
РЕШЕНИЕ:
1. Открыть FishAjaxPhp.java
2. Перелистать на конец файла
3. Убедиться что ВСЕ методы вставлены ДО закрывающей }
4. Пересчитать скобки - должна быть одна } на конец
```

### Ошибка 4: "Cannot resolve symbol HelperStrings"

```
РЕШЕНИЕ:
1. В начале FishAjaxPhp.java добавить:
   import ru.neverlands.abclient.utils.HelperStrings;
```

---

## 📝 ИТОГОВАЯ СТАТИСТИКА

```
Всего файлов изменено:       3
Строк добавлено:           ~650
Строк удалено:              ~3
Методов добавлено:           7
Классов добавлено:           4

Время реализации:      30-45 минут
Сложность:             Средняя
Риск регрессии:        НИЗКИЙ

Статус:               ✅ ГОТОВО К РЕАЛИЗАЦИИ
```

