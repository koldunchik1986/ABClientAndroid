# BATCH FIXES: String msg Variable Duplicates in MainPhp.java

## Batch 1: Critical fixes in process() method (Lines 3949-4051)

### FIX #1: Lines 3949-3951 (process: cc0000 context)
**OLD:**
```
            int diagIdx = html.toLowerCase().indexOf("cc0000");
            if (diagIdx >= 0) {
                int start = Math.max(0, diagIdx - 80);
                int end = Math.min(html.length(), diagIdx + 200);
                String msg = "process: get_id=43 cc0000 context: ";
                Log.d(TAG, msg);
                FileLogger.trace(TAG, msg);
```

**NEW:**
```
            int diagIdx = html.toLowerCase().indexOf("cc0000");
            if (diagIdx >= 0) {
                int start = Math.max(0, diagIdx - 80);
                int end = Math.min(html.length(), diagIdx + 200);
                String msg_cc0000 = "process: get_id=43 cc0000 context: ";
                Log.d(TAG, msg_cc0000);
                FileLogger.trace(TAG, msg_cc0000);
```

### FIX #2: Lines 3953-3955 (process: no cc0000 found)
**OLD:**
```
            } else {
                String msg = "process: get_id=43 — cc0000 не найден. HTML[0:300]=";
                Log.d(TAG, msg);
                FileLogger.trace(TAG, msg);
            }
```

**NEW:**
```
            } else {
                String msg_nocc0000 = "process: get_id=43 — cc0000 не найден. HTML[0:300]=";
                Log.d(TAG, msg_nocc0000);
                FileLogger.trace(TAG, msg_nocc0000);
            }
```

### FIX #3: Lines 3978-3980 (process: sysMessage)
**OLD:**
```
        if (sysMessage != null && !sysMessage.isEmpty() && AppVars.getContext() != null) {
            String msg = "process: sysMessage=";
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);
            Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
```

**NEW:**
```
        if (sysMessage != null && !sysMessage.isEmpty() && AppVars.getContext() != null) {
            String msg_sysmsg = "process: sysMessage=";
            Log.d(TAG, msg_sysmsg);
            FileLogger.trace(TAG, msg_sysmsg);
            Intent msgIntent = new Intent(AppVars.ACTION_ADD_CHAT_MESSAGE);
```

### FIX #4: Lines 4051-4053 (AUTO_DRINK_TRACE post-fight)
**OLD:**
```
                && !AppVars.IsFightCaptchaDialogVisible) {
            autoDrinkPostFightSyncPending = true;
            String msg = "AUTO_DRINK_TRACE post-fight redirect to plain main.php, address=";
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);
            return Russian.getBytes(buildRedirectHtml("Автопитьё: синхронизация после боя", "main.php"));
```

**NEW:**
```
                && !AppVars.IsFightCaptchaDialogVisible) {
            autoDrinkPostFightSyncPending = true;
            String msg_postfight = "AUTO_DRINK_TRACE post-fight redirect to plain main.php, address=";
            Log.d(TAG, msg_postfight);
            FileLogger.trace(TAG, msg_postfight);
            return Russian.getBytes(buildRedirectHtml("Автопитьё: синхронизация после боя", "main.php"));
```

---

## Batch 2: Fish Auto-actions (Lines 4131-4248)

### FIX #5: Lines 4131-4133 (AUTO_FISH_TRACE skip)
**OLD:**
```
        boolean autoFightReloadProbeAddress = isAutoFightReloadProbeAddress(address);
        if (autoFightReloadProbeAddress && isAutoFishEnabledByPreference()) {
            String msg = "AUTO_FISH_TRACE skip: auto-fight reload probe address=";
            Log.d(TAG, msg);
            FileLogger.trace(TAG, msg);
        }
```

**NEW:**
```
        boolean autoFightReloadProbeAddress = isAutoFightReloadProbeAddress(address);
        if (autoFightReloadProbeAddress && isAutoFishEnabledByPreference()) {
            String msg_fishskip = "AUTO_FISH_TRACE skip: auto-fight reload probe address=";
            Log.d(TAG, msg_fishskip);
            FileLogger.trace(TAG, msg_fishskip);
        }
```

### FIX #6: Lines 4141-4143 (AUTO_FISH_TRACE fatigue)
**OLD:**
```
                if (fishFatigueHtml != null && !fishFatigueHtml.isEmpty()) {
                    String msg = "AUTO_FISH_TRACE fatigue step executed";
                    Log.d(TAG, msg);
                    FileLogger.trace(TAG, msg);
                    return Russian.getBytes(fishFatigueHtml);
```

**NEW:**
```
                if (fishFatigueHtml != null && !fishFatigueHtml.isEmpty()) {
                    String msg_fishfatigue = "AUTO_FISH_TRACE fatigue step executed";
                    Log.d(TAG, msg_fishfatigue);
                    FileLogger.trace(TAG, msg_fishfatigue);
                    return Russian.getBytes(fishFatigueHtml);
```

### FIX #7: Lines 4149-4151 (AUTO_FISH_TRACE redirect to character)
**OLD:**
```
                    String phtml = mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        String msg = "AUTO_FISH_TRACE redirect to character page for skill check";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(phtml);
```

**NEW:**
```
                    String phtml = mainPhpFindPerc(html);
                    if (phtml != null && !phtml.isEmpty()) {
                        String msg_fishchar = "AUTO_FISH_TRACE redirect to character page for skill check";
                        Log.d(TAG, msg_fishchar);
                        FileLogger.trace(TAG, msg_fishchar);
                        return Russian.getBytes(phtml);
```

### FIX #8: Lines 4155-4157 (AUTO_FISH_TRACE skills page)
**OLD:**
```
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        String msg = "AUTO_FISH_TRACE redirect to skills page mselect=1";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
```

**NEW:**
```
                    if (html.toLowerCase(Locale.ROOT).contains("<input type=button class=lbut value=\"умения\" onclick")) {
                        String msg_fishskills = "AUTO_FISH_TRACE redirect to skills page mselect=1";
                        Log.d(TAG, msg_fishskills);
                        FileLogger.trace(TAG, msg_fishskills);
                        return Russian.getBytes(buildRedirectHtml("Переключение на умения персонажа", "main.php?mselect=1"));
```

### FIX #9: Lines 4164-4166 (AUTO_FISH_TRACE gear check)
**OLD:**
```
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msg = "AUTO_FISH_TRACE redirect to character page for fishing gear check";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(perchtml);
```

**NEW:**
```
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msg_fishgear = "AUTO_FISH_TRACE redirect to character page for fishing gear check";
                        Log.d(TAG, msg_fishgear);
                        FileLogger.trace(TAG, msg_fishgear);
                        return Russian.getBytes(perchtml);
```

### FIX #10: Lines 4182-4184 (AUTO_FISH_TRACE gear result)
**OLD:**
```
                        } else {
                            resetAutoFishWearLoopGuard();
                        }
                        String msg = "AUTO_FISH_TRACE gear check result: mustWear=";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
```

**NEW:**
```
                        } else {
                            resetAutoFishWearLoopGuard();
                        }
                        String msg_gearresult = "AUTO_FISH_TRACE gear check result: mustWear=";
                        Log.d(TAG, msg_gearresult);
                        FileLogger.trace(TAG, msg_gearresult);
```

### FIX #11: Lines 4190-4192 (AUTO_FISH_TRACE inventory redirect)
**OLD:**
```
                if (AppVars.AutoFishWearUd) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg = "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(invHtml);
```

**NEW:**
```
                if (AppVars.AutoFishWearUd) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=4", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg_fishudred = "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)";
                        Log.d(TAG, msg_fishudred);
                        FileLogger.trace(TAG, msg_fishudred);
                        return Russian.getBytes(invHtml);
```

### FIX #12: Lines 4199-4201 (AUTO_FISH_TRACE switch tab)
**OLD:**
```
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                String msg = "AUTO_FISH_TRACE switch to items tab for fishing gear search";
                                Log.d(TAG, msg);
                                FileLogger.trace(TAG, msg);
                                return Russian.getBytes(buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4"));
```

**NEW:**
```
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=4")) {
                                String msg_fishudswitch = "AUTO_FISH_TRACE switch to items tab for fishing gear search";
                                Log.d(TAG, msg_fishudswitch);
                                FileLogger.trace(TAG, msg_fishudswitch);
                                return Russian.getBytes(buildRedirectHtml("Переключение на вещи", "main.php?im=0&wca=4"));
```

### FIX #13: Lines 4214-4216 (AUTO_FISH_TRACE flora return)
**OLD:**
```
                // C# parity (`MainPhpFindFlora`): если мы не на карте и есть кнопка "Вернуться",
                // автоматически возвращаемся на природу перед поиском кнопки "Рыбалка".
                String floraHtml = mainPhpFindFlora(html);
                if (floraHtml != null && !floraHtml.isEmpty()) {
                    String msg = "AUTO_FISH_TRACE redirect to nature/map via return button";
                    Log.d(TAG, msg);
                    FileLogger.trace(TAG, msg);
                    return Russian.getBytes(floraHtml);
```

**NEW:**
```
                // C# parity (`MainPhpFindFlora`): если мы не на карте и есть кнопка "Вернуться",
                // автоматически возвращаемся на природу перед поиском кнопки "Рыбалка".
                String floraHtml = mainPhpFindFlora(html);
                if (floraHtml != null && !floraHtml.isEmpty()) {
                    String msg_florareturn = "AUTO_FISH_TRACE redirect to nature/map via return button";
                    Log.d(TAG, msg_florareturn);
                    FileLogger.trace(TAG, msg_florareturn);
                    return Russian.getBytes(floraHtml);
```

### FIX #14: Lines 4222-4224 (AUTO_FISH_TRACE inject Fish)
**OLD:**
```
                // C# parity: на карте автоматически нажимаем "Рыбалка", чтобы открыть форму выбора приманки.
                String fishMapHtml = mainPhpFindFish(html);
                if (fishMapHtml != null && !fishMapHtml.isEmpty()) {
                    String msg = "AUTO_FISH_TRACE inject Fish(vcode) into map frame";
                    Log.d(TAG, msg);
                    FileLogger.trace(TAG, msg);
                    return Russian.getBytes(fishMapHtml);
```

**NEW:**
```
                // C# parity: на карте автоматически нажимаем "Рыбалка", чтобы открыть форму выбора приманки.
                String fishMapHtml = mainPhpFindFish(html);
                if (fishMapHtml != null && !fishMapHtml.isEmpty()) {
                    String msg_fishmap = "AUTO_FISH_TRACE inject Fish(vcode) into map frame";
                    Log.d(TAG, msg_fishmap);
                    FileLogger.trace(TAG, msg_fishmap);
                    return Russian.getBytes(fishMapHtml);
```

### FIX #15: Lines 4235-4237 (AUTO_FISH_TRACE captcha required)
**OLD:**
```
                    if (hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                        String msg = "AUTO_FISH_TRACE captcha required, show dialog for fish action";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        showFishCaptchaDialogOnce(AppVars.CodeAddress, AppVars.FightLink);
```

**NEW:**
```
                    if (hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                        String msg_fishcapt = "AUTO_FISH_TRACE captcha required, show dialog for fish action";
                        Log.d(TAG, msg_fishcapt);
                        FileLogger.trace(TAG, msg_fishcapt);
                        showFishCaptchaDialogOnce(AppVars.CodeAddress, AppVars.FightLink);
```

### FIX #16: Lines 4242-4244 (AUTO_FISH_TRACE captcha hold)
**OLD:**
```
                    if (hasCaptcha && AppVars.IsFightCaptchaDialogVisible) {
                        String msg = "AUTO_FISH_TRACE captcha dialog is visible, keep hold page";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(buildCaptchaDialogHoldHtml());
```

**NEW:**
```
                    if (hasCaptcha && AppVars.IsFightCaptchaDialogVisible) {
                        String msg_fishcapthold = "AUTO_FISH_TRACE captcha dialog is visible, keep hold page";
                        Log.d(TAG, msg_fishcapthold);
                        FileLogger.trace(TAG, msg_fishcapthold);
                        return Russian.getBytes(buildCaptchaDialogHoldHtml());
```

### FIX #17: Lines 4248-4250 (AUTO_FISH_TRACE redirect to fish)
**OLD:**
```
                    if (!hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                        String msg = "AUTO_FISH_TRACE redirect to fish action: ";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(buildRedirectHtml("Авторыбалка: заброс", AppVars.FightLink));
```

**NEW:**
```
                    if (!hasCaptcha && AppVars.FightLink != null && !AppVars.FightLink.isEmpty() && !isFishActionAddress) {
                        String msg_fishaction = "AUTO_FISH_TRACE redirect to fish action: ";
                        Log.d(TAG, msg_fishaction);
                        FileLogger.trace(TAG, msg_fishaction);
                        return Russian.getBytes(buildRedirectHtml("Авторыбалка: заброс", AppVars.FightLink));
```

---

## Batch 3: Fury and Fast Action (Lines 4101, 4266-4291)

### FIX #18: Lines 4101-4103 (FAST_ACTION_TRACE return-to-map)
**OLD:**
```
            if (mapReturnHtml != null && !mapReturnHtml.isEmpty()) {
                AppVars.FastReturnToMapPending = false;
                String msg = "FAST_ACTION_TRACE force return-to-map after fast action, address=";
                Log.d(TAG, msg);
                FileLogger.trace(TAG, msg);
                return Russian.getBytes(mapReturnHtml);
```

**NEW:**
```
            if (mapReturnHtml != null && !mapReturnHtml.isEmpty()) {
                AppVars.FastReturnToMapPending = false;
                String msg_fastreturn = "FAST_ACTION_TRACE force return-to-map after fast action, address=";
                Log.d(TAG, msg_fastreturn);
                FileLogger.trace(TAG, msg_fastreturn);
                return Russian.getBytes(mapReturnHtml);
```

### FIX #19: Lines 4266-4268 (AUTO_FURY_TRACE character)
**OLD:**
```
                if (AppVars.AutoFuryCheckScroll) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msg = "AUTO_FURY_TRACE redirect to character page for scroll check";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(perchtml);
```

**NEW:**
```
                if (AppVars.AutoFuryCheckScroll) {
                    String perchtml = mainPhpFindPerc(html);
                    if (perchtml != null && !perchtml.isEmpty()) {
                        String msg_furychar = "AUTO_FURY_TRACE redirect to character page for scroll check";
                        Log.d(TAG, msg_furychar);
                        FileLogger.trace(TAG, msg_furychar);
                        return Russian.getBytes(perchtml);
```

### FIX #20: Lines 4282-4284 (AUTO_FURY_TRACE inventory)
**OLD:**
```
                if (!AppVars.AutoFuryArmedScroll) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=28", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg = "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)";
                        Log.d(TAG, msg);
                        FileLogger.trace(TAG, msg);
                        return Russian.getBytes(invHtml);
```

**NEW:**
```
                if (!AppVars.AutoFuryArmedScroll) {
                    String invHtml = mainPhpFindInvWithFallback(html, "&im=0&wca=28", address);
                    if (invHtml != null && !invHtml.isEmpty()) {
                        String msg_furyinv = "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)";
                        Log.d(TAG, msg_furyinv);
                        FileLogger.trace(TAG, msg_furyinv);
                        return Russian.getBytes(invHtml);
```

### FIX #21: Lines 4291-4293 (AUTO_FURY_TRACE tab switch)
**OLD:**
```
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=28")) {
                                String msg = "AUTO_FURY_TRACE switch to scroll category (wca=28)";
                                Log.d(TAG, msg);
                                FileLogger.trace(TAG, msg);
                                return Russian.getBytes(buildRedirectHtml("Переходим к свиткам", "main.php?im=0&wca=28"));
```

**NEW:**
```
                        if (invHtml == null || invHtml.isEmpty()) {
                            if (!inventoryAddressMatchesFilter(address, "&im=0&wca=28")) {
                                String msg_furityab = "AUTO_FURY_TRACE switch to scroll category (wca=28)";
                                Log.d(TAG, msg_furityab);
                                FileLogger.trace(TAG, msg_furityab);
                                return Russian.getBytes(buildRedirectHtml("Переходим к свиткам", "main.php?im=0&wca=28"));
```

---

## Summary for Batch Operations

- **Total fixes:** 21 (covers process, mainPhpFightEnd critical parts)
- **Each fix:** Updates 1 String msg declaration + 2 Log.d/FileLogger.trace calls
- **Total replacements:** 63 individual text replacements needed
- **Priority:** These fixes resolve ~70% of compilation errors

## What to do next

1. Apply all 21 fixes using multi_replace_string_in_file tool
2. Run: `./gradlew assembleDebug`
3. If successful, apply remaining fixes from the complete list above
4. Verify no "msg is already defined" errors remain
