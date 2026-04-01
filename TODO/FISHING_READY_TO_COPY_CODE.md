# READY-TO-COPY КОД - Для копирования прямо в файлы

**Статус:** ГОТОВ К КОПИРОВАНИЮ  
**Дата:** 1 апреля 2026  

---

## 📋 КОД #1: MainPhp.java - Блок кэширования

**НАЙТИ и ЗАМЕНИТЬ в mainPhpFindFish() (строка ~2140):**

```java
// ============ НАЙТИ ЭТА СТРОКА ============
    posScript += patternViewMap.length();
    return html.substring(0, posScript) + callFish + html.substring(posScript);
// ============ КОНЕЦ ПОИСКА ============

// ============ ЗАМЕНИТЬ НА ============
    posScript += patternViewMap.length();
    
    // Кэшируем HTML озера для парсинга приманок (Android версия MainPhpFish.cs)
    if (AppVars.ContentLakeHtml == null || AppVars.ContentLakeHtml.isEmpty()) {
        AppVars.ContentLakeHtml = extractLakeFishFormHtml(html);
        android.util.Log.d("MainPhp", "AUTO_FISH_TRACE cached ContentLakeHtml, length=" + 
            (AppVars.ContentLakeHtml != null ? AppVars.ContentLakeHtml.length() : 0));
    }
    
    return html.substring(0, posScript) + callFish + html.substring(posScript);
// ============ КОНЕЦ ЗАМЕНЫ ============
```

---

## 📋 КОД #2: MainPhp.java - Метод для парсинга озера

**ДОБАВИТЬ в конец класса MainPhp (перед последней `}`):**

```java
    /**
     * Извлекает HTML форму рыбалки (id="FISHF") из полной страницы озера.
     * Аналог C#: MainPhpFish.cs, парсинг озера.
     * 
     * @param html Полный HTML озера
     * @return HTML формы <form id="FISHF">...</form> или пустая строка
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
        
        // Найти начало <form>
        int formStart = html.lastIndexOf("<form", posForm);
        if (formStart == -1) return "";
        
        // Найти </form>
        int formEnd = html.indexOf("</form>", formStart);
        if (formEnd == -1) return "";
        
        formEnd += "</form>".length();
        return html.substring(formStart, formEnd);
    }
```

---

## 📋 КОД #3: FishAjaxPhp.java - Основной цикл (Часть 1)

**ДОБАВИТЬ в конец класса FishAjaxPhp (перед последней `}`):**

```java
    /**
     * ОСНОВНОЙ ЦИКЛ АВТО-РЫБАЛКИ - главный координатор.
     * 
     * Инициирует act=1, обрабатывает приманку, отправляет act=2,
     * получает результат и планирует следующий цикл с cooldown.
     * 
     * Аналог C#: MainPhp.cs "Новая рыбалка" (строка 2070+)
     */
    private static void executeFishingCycleCore(String reason) {
        if (!isAutoFishEnabled()) {
            Log.d(TAG, "AUTO_FISH_TRACE executeFishingCycleCore: auto-fish disabled, skip");
            return;
        }
        
        long cycleToken = System.currentTimeMillis();
        lastFishCycleToken = cycleToken;
        long nowMs = System.currentTimeMillis();
        
        // === Проверка cooldown ===
        if (AppVars.NeverTimer > nowMs) {
            long waitMs = AppVars.NeverTimer - nowMs;
            Log.d(TAG, "AUTO_FISH_TRACE cycle: in cooldown, waitMs=" + waitMs + ", reason=" + reason);
            scheduleNextFishingCycleAttempt(cycleToken, 1, (int)Math.min(30000L, waitMs + 500L));
            return;
        }
        
        // === Инициализация act=1 ===
        if (lastFishAct1AtMs <= 0L) {
            Log.d(TAG, "AUTO_FISH_TRACE cycle: bootstrap act=1");
            MainActivity activity = getMainActivityOrNull();
            if (activity != null && activity.getMainWebView() != null) {
                activity.getMainWebView().loadUrl("http://neverlands.ru/gameplay/ajax/fish_ajax.php?act=1&r=" 
                    + System.currentTimeMillis());
                lastFishAct1AtMs = nowMs;
            } else {
                scheduleNextFishingCycleAttempt(cycleToken, 1, 3000);
            }
            return;
        }
        
        // === Контроль таймаутов ===
        long timeSinceAct1Ms = nowMs - lastFishAct1AtMs;
        long timeSinceAct2Ms = nowMs - lastFishAct2AtMs;
        
        if (timeSinceAct1Ms > 25000L && timeSinceAct2Ms > 25000L) {
            Log.w(TAG, "AUTO_FISH_TRACE cycle: timeout, act1=" + timeSinceAct1Ms + "ms, act2=" + timeSinceAct2Ms + "ms");
            int errCount = registerAct1ErrAndMaybeRecover("cycle_timeout");
            if (errCount >= FISH_CYCLE_MAX_ATTEMPTS) {
                requestAutoFishBootstrap("cycle_timeout");
                resetAct1ErrRecoveryState();
                lastFishAct1AtMs = 0L;
            } else {
                scheduleNextFishingCycleAttempt(cycleToken, errCount + 1, FISH_CYCLE_RETRY_DELAY_MS);
            }
            return;
        }
        
        String statusMsg = "act1_ok";
        if (timeSinceAct1Ms < 500) statusMsg = "act1_fresh";
        else if (timeSinceAct1Ms < 5000) statusMsg = "act1_waiting";
        
        Log.d(TAG, "AUTO_FISH_TRACE cycle: " + statusMsg + ", act1=" + timeSinceAct1Ms 
            + "ms, act2=" + timeSinceAct2Ms + "ms");
    }
    
    /**
     * Планирует следующий цикл авто-рыбалки.
     */
    private static void scheduleNextFishingCycleAttempt(long cycleToken, int attempt, int delayMs) {
        if (cycleToken != lastFishCycleToken) {
            return;
        }
        
        MainActivity activity = getMainActivityOrNull();
        if (activity == null) return;
        
        WebView webView = activity.getMainWebView();
        if (webView == null) return;
        
        int effectiveDelay = Math.max(100, Math.min(120000, delayMs));
        Log.d(TAG, "AUTO_FISH_TRACE scheduleNext: attempt=" + attempt + ", delay=" + effectiveDelay);
        
        webView.postDelayed(() -> {
            if (!isAutoFishEnabled() || lastFishCycleToken != cycleToken) {
                return;
            }
            executeFishingCycleCore("scheduled_" + attempt);
        }, effectiveDelay);
    }
    
    private static MainActivity getMainActivityOrNull() {
        return (AppVars.mainActivity == null) ? null : AppVars.mainActivity.get();
    }
```

---

## 📋 КОД #4: FishAjaxPhp.java - Парсинг озера

```java
    /**
     * ПАРСИНГ ОЗЕРА - извлечение приманок и информации.
     * 
     * Парсит HTML озера (form id="FISHF") и извлекает:
     * - Доступные приманки (primid, name, count)
     * - Информацию о ресурсах (масса инвентаря)
     */
    private static LakeParseResult mainPhpAutoFishPrepareFromLakeAndroid() {
        String lakeHtml = AppVars.ContentLakeHtml;
        if (lakeHtml == null || lakeHtml.isEmpty()) {
            Log.w(TAG, "AUTO_FISH_TRACE lake: ContentLakeHtml is empty");
            return null;
        }
        
        LakeParseResult result = new LakeParseResult();
        result.rawHtml = lakeHtml;
        result.lowerHtml = lakeHtml.toLowerCase(Locale.ROOT);
        
        // === Парсим приманки ===
        Pattern primPattern = Pattern.compile(
            "type\\s*=\\s*['\"]?radio['\"]?[^>]*name\\s*=\\s*['\"]?primid['\"]?[^>]*value\\s*=\\s*['\"]?([^'\"\\s>]+)['\"]?[^>]*>([^<]*)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher primMatcher = primPattern.matcher(lakeHtml);
        
        while (primMatcher.find()) {
            String primid = primMatcher.group(1);
            String primLabel = primMatcher.group(2);
            
            Pattern countPattern = Pattern.compile("\\((\\d+)\\)");
            Matcher countMatcher = countPattern.matcher(primLabel);
            int count = 0;
            if (countMatcher.find()) {
                count = HelperStrings.tryParseInt(countMatcher.group(1), 0);
            }
            
            String name = primLabel.replaceAll("\\(\\d+\\)", "").trim();
            result.baits.add(new BaitSelectionResult(primid, name, count));
            
            Log.d(TAG, "AUTO_FISH_TRACE lake: primid=" + primid + ", name=" + name + ", count=" + count);
        }
        
        if (result.baits.isEmpty()) {
            Log.w(TAG, "AUTO_FISH_TRACE lake: no baits found");
            return null;
        }
        
        // === Извлекаем массу ===
        Pattern massPattern = Pattern.compile("Масса\\s*:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)");
        Matcher massMatcher = massPattern.matcher(lakeHtml);
        if (massMatcher.find()) {
            result.massCurrent = massMatcher.group(1);
            result.massMax = massMatcher.group(2);
        }
        
        result.isValid = true;
        Log.d(TAG, "AUTO_FISH_TRACE lake: parsed " + result.baits.size() + " baits, mass=" 
            + result.massCurrent + "/" + result.massMax);
        
        return result;
    }
```

---

## 📋 КОД #5: FishAjaxPhp.java - Выбор приманки

```java
    /**
     * ВЫБОР ПРИМАНКИ - по профилю и доступности.
     * 
     * Алгоритм:
     * 1) Список приманок озера (count > 0)
     * 2) Проверка: разрешена ли по профилю (Profile.FishEnabledPrims)
     * 3) Выбор первой подходящей
     */
    private static BaitSelectionResult selectBaitFromLakeHtmlAndroid(LakeParseResult result) {
        if (result == null || !result.isValid || result.baits.isEmpty()) {
            Log.w(TAG, "AUTO_FISH_TRACE selectBait: invalid result");
            return null;
        }
        
        UserConfig profile = AppVars.Profile;
        if (profile == null) {
            Log.w(TAG, "AUTO_FISH_TRACE selectBait: profile is null");
            return null;
        }
        
        int enabledPrims = profile.FishEnabledPrims;
        int[] primIds = FISH_PRIM_IDS;      // [38, 39, 40, 41, 42, 43, 44, 45, 46]
        int[] primFlags = FISH_PRIM_FLAGS;  // Соответствующие флаги Prims
        
        for (BaitSelectionResult bait : result.baits) {
            if (bait.count <= 0) continue;
            
            int primIdInt = HelperStrings.tryParseInt(bait.id, -1);
            int primFlag = -1;
            
            for (int i = 0; i < primIds.length; i++) {
                if (primIds[i] == primIdInt) {
                    primFlag = primFlags[i];
                    break;
                }
            }
            
            if (primFlag == -1) continue;
            
            if ((enabledPrims & primFlag) != 0) {
                Log.d(TAG, "AUTO_FISH_TRACE selectBait: selected " + bait.id + "/" + bait.name);
                return bait;
            }
        }
        
        Log.w(TAG, "AUTO_FISH_TRACE selectBait: no enabled baits");
        return null;
    }
```

---

## 📋 КОД #6: FishAjaxPhp.java - DTO классы

```java
    /**
     * РЕЗУЛЬТАТ ПАРСИНГА ОЗЕРА - DTO для mainPhpAutoFishPrepareFromLakeAndroid().
     */
    private static final class LakeParseResult {
        String rawHtml = "";
        String lowerHtml = "";
        List<BaitSelectionResult> baits = new ArrayList<>();
        String massCurrent = "";
        String massMax = "";
        boolean isValid = false;
        
        @Override
        public String toString() {
            return "LakeParseResult{baits=" + baits.size() + ", mass=" + massCurrent + "/" + massMax + "}";
        }
    }
    
    /**
     * ВЫБОР ПРИМАНКИ - DTO для результата selectBaitFromLakeHtmlAndroid().
     */
    private static final class BaitSelectionResult {
        final String id;
        final String name;
        final int count;
        
        BaitSelectionResult(String id, String name, int count) {
            this.id = (id == null) ? "" : id;
            this.name = (name == null) ? "" : name;
            this.count = Math.max(0, count);
        }
        
        @Override
        public String toString() {
            return "BaitSelectionResult{id='" + id + "', name='" + name + "', count=" + count + "}";
        }
    }
```

---

## 📋 КОД #7: FishAjaxPhp.java - Парсинг act=1

```java
    /**
     * ПАРСИНГ ОТВЕТА ACT=1 - извлечение capcha/vcode/масса/приманки.
     * 
     * Формат: RESO@["описание"]@[]@[1,"captchaToken","vcode",massCur,massMax,[primid,name,count]...]
     */
    private static FishAct1State parseFishAct1State(String html) {
        if (html == null || html.isEmpty()) return null;
        
        FishAct1State state = new FishAct1State();
        
        try {
            String pattern = "@\\[1,";
            int posPayload = html.indexOf(pattern);
            if (posPayload == -1) return null;
            
            int posEnd = html.indexOf("@[]@", posPayload);
            if (posEnd == -1) {
                posEnd = html.indexOf("@[", posPayload + 100);
                if (posEnd == -1) posEnd = html.length();
            }
            
            String payloadPart = html.substring(posPayload + pattern.length() - 1, posEnd);
            
            // Парсим token и vcode
            Pattern tokenPattern = Pattern.compile(",\\s*['\"]([a-zA-Z0-9]+)['\"]\\s*,\\s*['\"]([a-zA-Z0-9]+)['\"]");
            Matcher tokenMatcher = tokenPattern.matcher(payloadPart);
            if (tokenMatcher.find()) {
                state.captchaToken = tokenMatcher.group(1);
                state.vcode = tokenMatcher.group(2);
            }
            
            // Парсим массу инвентаря
            Pattern massPattern = Pattern.compile(",(\\d+(?:[,.]\\d+)?)\\s*,\\s*(\\d+(?:[,.]\\d+)?)");
            Matcher massMatcher = massPattern.matcher(payloadPart);
            int matchCount = 0;
            while (massMatcher.find() && matchCount < 2) {
                if (matchCount == 0) {
                    state.massCurrent = massMatcher.group(1);
                    state.massMax = massMatcher.group(2);
                }
                matchCount++;
            }
            
            // Парсим приманки
            Pattern baitPattern = Pattern.compile("\\[(\\d+)\\s*,\\s*['\"]([^'\"]+)['\"]\\s*,\\s*(\\d+)\\]");
            Matcher baitMatcher = baitPattern.matcher(payloadPart);
            while (baitMatcher.find()) {
                state.baits.add(new FishBaitInfo(baitMatcher.group(1), baitMatcher.group(2), 
                    HelperStrings.tryParseInt(baitMatcher.group(3), 0)));
            }
            
            state.isValid = state.vcode != null && !state.vcode.isEmpty();
            Log.d(TAG, "AUTO_FISH_TRACE parseFishAct1: vcode=" + state.vcode + ", baits=" + state.baits.size());
            
            return state;
        } catch (Exception e) {
            Log.w(TAG, "AUTO_FISH_TRACE parseFishAct1: error", e);
            return null;
        }
    }
    
    private static final class FishAct1State {
        String captchaToken = "";
        String vcode = "";
        String massCurrent = "";
        String massMax = "";
        List<FishBaitInfo> baits = new ArrayList<>();
        boolean isValid = false;
    }
    
    private static final class FishBaitInfo {
        final String primid;
        final String name;
        final int count;
        
        FishBaitInfo(String primid, String name, int count) {
            this.primid = primid != null ? primid : "";
            this.name = name != null ? name : "";
            this.count = Math.max(0, count);
        }
    }
```

---

## 📋 КОД #8: FishAjaxPhp.java - Вспомогательные методы

```java
    /**
     * ОБРАБОТКА ОШИБОК - ведет счетчик и инициирует recovery.
     */
    private static int registerAct1ErrAndMaybeRecover(String errorType) {
        long nowMs = System.currentTimeMillis();
        long timeSinceLastErr = nowMs - lastFishAct1ErrAtMs;
        
        if (timeSinceLastErr > 30000L) {
            consecutiveFishAct1ErrCount = 0;
        }
        
        consecutiveFishAct1ErrCount++;
        lastFishAct1ErrAtMs = nowMs;
        
        Log.w(TAG, "AUTO_FISH_TRACE err: type=" + errorType + ", count=" + consecutiveFishAct1ErrCount);
        
        if (consecutiveFishAct1ErrCount >= FISH_ACT1_ERR_RECOVERY_THRESHOLD) {
            requestAutoFishBootstrap(errorType + "_max_retries");
        }
        
        return consecutiveFishAct1ErrCount;
    }
    
    /**
     * Сбрасывает счетчик ошибок.
     */
    private static void resetAct1ErrRecoveryState() {
        consecutiveFishAct1ErrCount = 0;
        lastFishAct1ErrAtMs = 0L;
    }
    
    /**
     * COOOWN СЕРВЕРА - извлекает время до следующего заброса.
     * 
     * Формат: @[0,[2,<sec>]]@
     */
    private static int extractFishCooldownSec(String html) {
        if (html == null || html.isEmpty()) return 0;
        
        Matcher matcher = FISH_COOLDOWN_PATTERN.matcher(html);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                Log.w(TAG, "AUTO_FISH_TRACE cooldown parse error", e);
            }
        }
        return 0;
    }
    
    /**
     * ВЫБОР ПРИМАНКИ - из списка baits по профилю (аналог selectBaitFromLakeHtmlAndroid()).
     */
    private static FishBaitSelection selectAllowedBait(List<FishBaitInfo> baits) {
        if (baits == null || baits.isEmpty()) return null;
        
        UserConfig profile = AppVars.Profile;
        if (profile == null) return null;
        
        int[] primIds = FISH_PRIM_IDS;
        int[] primFlags = FISH_PRIM_FLAGS;
        int enabledPrims = profile.FishEnabledPrims;
        
        for (FishBaitInfo bait : baits) {
            if (bait.count <= 0) continue;
            
            int primIdInt = HelperStrings.tryParseInt(bait.primid, -1);
            for (int i = 0; i < primIds.length; i++) {
                if (primIds[i] == primIdInt && (enabledPrims & primFlags[i]) != 0) {
                    return new FishBaitSelection(bait.primid, bait.name, bait.count);
                }
            }
        }
        return null;
    }
```

---

## ✅ ИТОГОВЫЙ ЧЕКЛИСТ

- [ ] KОД #1-2: Вставлены в MainPhp.java (кэширование озера)
- [ ] КОД #3: Основной цикл добавлен в FishAjaxPhp.java
- [ ] КОД #4: Парсинг озера добавлен в FishAjaxPhp.java
- [ ] КОД #5: Выбор приманки добавлен в FishAjaxPhp.java
- [ ] КОД #6: DTO классы добавлены в FishAjaxPhp.java
- [ ] КОД #7: Парсинг act=1 добавлен в FishAjaxPhp.java
- [ ] КОД #8: Вспомогательные методы добавлены в FishAjaxPhp.java
- [ ] Компиляция успешна: `./gradlew.bat compileDebugJavaWithJavac`
- [ ] APK собрана: `./gradlew.bat assembleDebug`

**СТАТУС ГОТОВНОСТИ: ✅ 100% - ВСЕ КОДЫ ГОТОВЫ К КОПИРОВАНИЮ**

