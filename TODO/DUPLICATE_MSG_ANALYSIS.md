# Анализ: Дублирующиеся String msg Переменные в MainPhp.java

## Резюме проблемы

Ошибка сборки: **50+ дублирующихся "variable msg is already defined in method"**

Все ошибки находятся в методах:
1. `process()` — основной метод обработки (строки 3900-4650)
2. `mainPhpFightEnd()` — обработка конца боя (строки 5370-5440)
3. `showFightCaptchaDialogOnce()` — диалог captcha боя (строки 5250-5320)
4. `mainPhpInv()` — обработка инвентаря (строки 6120-6200)

## Причина проблемы

В одном методе/scope многократно объявляется переменная `String msg = "..."` без уникальных имён. Java требует уникальные имена переменных в одной области видимости.

**Неправильно (вызывает ошибку):**
```java
String msg = "first message";
Log.d(TAG, msg);
// ...
String msg = "second message";  // ОШИБКА: msg already defined!
Log.d(TAG, msg);
```

**Правильно:**
```java
String msg_1 = "first message";
Log.d(TAG, msg_1);
// ...
String msg_2 = "second message";  // OK: уникальное имя
Log.d(TAG, msg_2);
```

## Стратегия исправления

**Правило именования для дублирующихся переменных:**
- Использовать суффиксы, основанные на содержании или контексту
- Примеры: `msg_htmlLen`, `msg_diag`, `msg_fish`, `msg_skin`, `msg_inv`, `msg_err`, `msg_1`, `msg_2`, etc.

**Принцип:** Каждой новой переменной `String msg = ` в методе присваивается уникальное имя, и все последующие `Log.d()` вызовы обновляются.

---

## Полный список дублирующихся msg в process() методе

### Строка 3914 (ИСХОДНАЯ, СОХРАНИТЬ):
```java
String msg = "process() called for ";
Log.d(TAG, msg);
FileLogger.trace(TAG, msg);
```
✅ **Оставить без изменений** (первое объявление в методе)

### Строка 3949 (❌ ДУБЛИКАТ #1 - переименовать в msg_cc0000):
```java
String msg = "process: get_id=43 cc0000 context: ";
```
→ `String msg_cc0000 = "process: get_id=43 cc0000 context: ";`

### Строка 3953 (❌ ДУБЛИКАТ #2 - переименовать в msg_nocc0000):
```java
String msg = "process: get_id=43 — cc0000 не найден. HTML[0:300]=";
```
→ `String msg_nocc0000 = "process: get_id=43 — cc0000 не найден. HTML[0:300]=";`

### Строка 3978 (❌ ДУБЛИКАТ #3 - переименовать в msg_sysmsg):
```java
String msg = "process: sysMessage=";
```
→ `String msg_sysmsg = "process: sysMessage=";`

### Строка 4051 (❌ ДУБЛИКАТ #4 - переименовать в msg_postfight):
```java
String msg = "AUTO_DRINK_TRACE post-fight redirect to plain main.php, address=";
```
→ `String msg_postfight = "AUTO_DRINK_TRACE post-fight redirect to plain main.php, address=";`

### Строка 4101 (❌ ДУБЛИКАТ #5 - переименовать в msg_fastreturn):
```java
String msg = "FAST_ACTION_TRACE force return-to-map after fast action, address=";
```
→ `String msg_fastreturn = "FAST_ACTION_TRACE force return-to-map after fast action, address=";`

### Строка 4131 (❌ ДУБЛИКАТ #6 - переименовать в msg_fishskip):
```java
String msg = "AUTO_FISH_TRACE skip: auto-fight reload probe address=";
```
→ `String msg_fishskip = "AUTO_FISH_TRACE skip: auto-fight reload probe address=";`

### Строка 4141 (❌ ДУБЛИКАТ #7 - переименовать в msg_fishfatigue):
```java
String msg = "AUTO_FISH_TRACE fatigue step executed";
```
→ `String msg_fishfatigue = "AUTO_FISH_TRACE fatigue step executed";`

### Строка 4149 (❌ ДУБЛИКАТ #8 - переименовать в msg_fishchar):
```java
String msg = "AUTO_FISH_TRACE redirect to character page for skill check";
```
→ `String msg_fishchar = "AUTO_FISH_TRACE redirect to character page for skill check";`

### Строка 4155 (❌ ДУБЛИКАТ #9 - переименовать в msg_fishskills):
```java
String msg = "AUTO_FISH_TRACE redirect to skills page mselect=1";
```
→ `String msg_fishskills = "AUTO_FISH_TRACE redirect to skills page mselect=1";`

### Строка 4164 (❌ ДУБЛИКАТ #10 - переименовать в msg_fishgear):
```java
String msg = "AUTO_FISH_TRACE redirect to character page for fishing gear check";
```
→ `String msg_fishgear = "AUTO_FISH_TRACE redirect to character page for fishing gear check";`

### Строка 4182 (❌ ДУБЛИКАТ #11 - переименовать в msg_gearresult):
```java
String msg = "AUTO_FISH_TRACE gear check result: mustWear=";
```
→ `String msg_gearresult = "AUTO_FISH_TRACE gear check result: mustWear=";`

### Строка 4190 (❌ ДУБЛИКАТ #12 - переименовать в msg_fishudred):
```java
String msg = "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)";
```
→ `String msg_fishudred = "AUTO_FISH_TRACE redirect to inventory for fishing gear (&im=0&wca=4)";`

### Строка 4199 (❌ ДУБЛИКАТ #13 - переименовать в msg_fishudswitch):
```java
String msg = "AUTO_FISH_TRACE switch to items tab for fishing gear search";
```
→ `String msg_fishudswitch = "AUTO_FISH_TRACE switch to items tab for fishing gear search";`

### Строка 4214 (❌ ДУБЛИКАТ #14 - переименовать в msg_florareturn):
```java
String msg = "AUTO_FISH_TRACE redirect to nature/map via return button";
```
→ `String msg_florareturn = "AUTO_FISH_TRACE redirect to nature/map via return button";`

### Строка 4222 (❌ ДУБЛИКАТ #15 - переименовать в msg_fishmap):
```java
String msg = "AUTO_FISH_TRACE inject Fish(vcode) into map frame";
```
→ `String msg_fishmap = "AUTO_FISH_TRACE inject Fish(vcode) into map frame";`

### Строка 4235 (❌ ДУБЛИКАТ #16 - переименовать в msg_fishcapt):
```java
String msg = "AUTO_FISH_TRACE captcha required, show dialog for fish action";
```
→ `String msg_fishcapt = "AUTO_FISH_TRACE captcha required, show dialog for fish action";`

### Строка 4242 (❌ ДУБЛИКАТ #17 - переименовать в msg_fishcapthold):
```java
String msg = "AUTO_FISH_TRACE captcha dialog is visible, keep hold page";
```
→ `String msg_fishcapthold = "AUTO_FISH_TRACE captcha dialog is visible, keep hold page";`

### Строка 4248 (❌ ДУБЛИКАТ #18 - переименовать в msg_fishaction):
```java
String msg = "AUTO_FISH_TRACE redirect to fish action: ";
```
→ `String msg_fishaction = "AUTO_FISH_TRACE redirect to fish action: ";`

### Строка 4266 (❌ ДУБЛИКАТ #19 - переименовать в msg_furychar):
```java
String msg = "AUTO_FURY_TRACE redirect to character page for scroll check";
```
→ `String msg_furychar = "AUTO_FURY_TRACE redirect to character page for scroll check";`

### Строка 4282 (❌ ДУБЛИКАТ #20 - переименовать в msg_furyinv):
```java
String msg = "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)";
```
→ `String msg_furyinv = "AUTO_FURY_TRACE redirect to scroll inventory (&im=0&wca=28)";`

### Строка 4291 (❌ ДУБЛИКАТ #21 - переименовать в msg_furityab):
```java
String msg = "AUTO_FURY_TRACE switch to scroll category (wca=28)";
```
→ `String msg_furityab = "AUTO_FURY_TRACE switch to scroll category (wca=28)";`

---

## Полный список дублирующихся msg в mainPhpFightEnd() методе

### Строка 5370 (ИСХОДНАЯ, СОХРАНИТЬ):
```java
String msg = "mainPhpFightEnd: processing fight end page";
```
✅ **Оставить без изменений** (первое объявление)

### Строка 5376 (❌ ДУБЛИКАТ #22 - переименовать в msg_fexp):
```java
String msg = "mainPhpFightEnd: has fexp, building redirect";
```
→ `String msg_fexp = "mainPhpFightEnd: has fexp, building redirect";`

### Строка 5396 (❌ ДУБЛИКАТ #23 - переименовать в msg_error):
```java
String msg = "mainPhpFightEnd: server returned error page, returning original HTML";
```
→ `String msg_error = "mainPhpFightEnd: server returned error page, returning original HTML";`

### Строка 5405 (❌ ДУБЛИКАТ #24 - переименовать в msg_form):
```java
String msg = "mainPhpFightEnd: found form in HTML, auto-submitting";
```
→ `String msg_form = "mainPhpFightEnd: found form in HTML, auto-submitting";`

### Строка 5414 (❌ ДУБЛИКАТ #25 - переименовать в msg_redirect):
```java
String msg = "mainPhpFightEnd: building redirect for fight end";
```
→ `String msg_redirect = "mainPhpFightEnd: building redirect for fight end";`

### Строка 5436 (❌ ДУБЛИКАТ #26 - переименовать в msg_nofexp):
```java
String msg = "mainPhpFightEnd: no fexp in URL, returning original HTML";
```
→ `String msg_nofexp = "mainPhpFightEnd: no fexp in URL, returning original HTML";`

---

## Полный список дублирующихся msg в mainPhpInv() методе

### Строка 6173 (❌ ДУБЛИКАТ #27 - переименовать в msg_pack):
```java
String msg = "INV_GROUP_TRACE afterPack=" + parsedCount
        + ", packed=" + Math.max(0, parsedCount - invList.size());
```
→ `String msg_pack = "INV_GROUP_TRACE afterPack=" + ...`

### Строка 6184 (❌ ДУБЛИКАТ #28 - переименовать в msg_sort):
```java
String msg = "INV_GROUP_TRACE afterSort=";
```
→ `String msg_sort = "INV_GROUP_TRACE afterSort=";`

### Строка 6191 (❌ ДУБЛИКАТ #29 - переименовать в msg_cache):
```java
String msg = "INV_GROUP_TRACE cache-only sync done: entries=";
```
→ `String msg_cache = "INV_GROUP_TRACE cache-only sync done: entries=";`

---

## Итоговая статистика

| Параметр | Значение |
|----------|----------|
| **Всего дублирующихся msg** | **29** |
| **В process()** | 21 |
| **В mainPhpFightEnd()** | 6 |
| **В mainPhpInv()** | 3 |
| **Всего операций замены** | **58** (каждой переменной + её Log.d) |

---

## Критические замены для немедленного исправления (Top Priority)

Эти 15 замен будут устранять большую часть ошибок сборки:

1. **Строка 3949** → msg_cc0000
2. **Строка 3953** → msg_nocc0000
3. **Строка 3978** → msg_sysmsg
4. **Строка 4051** → msg_postfight
5. **Строка 4101** → msg_fastreturn
6. **Строка 4131** → msg_fishskip
7. **Строка 4141** → msg_fishfatigue
8. **Строка 4214** → msg_florareturn
9. **Строка 4222** → msg_fishmap
10. **Строка 4235** → msg_fishcapt
11. **Строка 5370** → СОХРАНИТЬ БЕЗ ИЗМЕНЕНИЙ (исходная)
12. **Строка 5376** → msg_fexp
13. **Строка 5396** → msg_error
14. **Строка 5405** → msg_form
15. **Строка 6173** → msg_pack
16. **Строка 6184** → msg_sort

Этой группы замен достаточно протестировать изменения и убедиться, что механизм работает корректно.

---

## Инструкции по применению fixes

Для каждой замены необходимо:
1. Найти `String msg = "...";` на указанной строке
2. Переименовать в новое уникальное имя (например, `msg_cc0000`)
3. Найти все `Log.d(TAG, msg)` и `FileLogger.trace(TAG, msg)` сразу после объявления
4. Обновить все вызовы на новое имя переменной (например, `Log.d(TAG, msg_cc0000)`)
5. Сохранить файл в UTF-8

## Проверка после применения fixes

1. **Синтаксис:** Все переменные должны быть уникальными в методе
2. **Логирование:** Все Log.d/FileLogger.trace вызовы должны ссылаться на корректные переменные
3. **Сборка:** `./gradlew assembleDebug` должна завершиться успешно без ошибок компиляции
