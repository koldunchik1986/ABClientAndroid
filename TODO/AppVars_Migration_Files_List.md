# Список файлов для миграции AppVars.VCode на SessionManager

## 🔴 КРИТИЧНЫЕ файлы (Stage 1 - Немедленно)

### 1. TreasureDig.java - FAST ACTION (Откапывание клада)

**Расположение:** [app/src/main/java/ru/neverlands/abclient/postfilter/TreasureDig.java](TreasureDig.java)

**Строки:** 404-405

**БЫЛО:**
```java
if (AppVars.VCode != null && !AppVars.VCode.trim().isEmpty()) {
    link += "&vcode=" + AppVars.VCode.trim();
}
```

**СТАЛО:**
```java
import ru.neverlands.abclient.utils.SessionManager;
import ru.neverlands.abclient.utils.FileLogger;

String vcode = SessionManager.getInstance().getValidVCodeForAction("treasure_dig");
if (vcode != null && !vcode.trim().isEmpty()) {
    link += "&vcode=" + vcode.trim();
    FileLogger.trace("TreasureDig: VCode obtained, ageMs=" + 
        SessionManager.getInstance().getVCodeAgeMs());
} else {
    Log.w(TAG, "❌ VCODE_MISSING: treasure_dig, reloading main.php");
    FileLogger.trace("TreasureDig: VCode expired, fallback - reload main.php");
    MainActivity.getInstance().loadUrl("main.php");
    return;
}
```

**Содержание кода:** Получение VCode для fast action - копание клада

**Приоритет:** 🔴 **CRITICAL**

---

### 2. FastActionManager.java - FAST ACTIONS (Быстрые удары, умения)

**Расположение:** [app/src/main/java/ru/neverlands/abclient/manager/FastActionManager.java](FastActionManager.java)

**Строки:** 2217-2218

**БЫЛО:**
```java
if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
    url += "&vcode=" + AppVars.VCode;
}
```

**СТАЛО:**
```java
import ru.neverlands.abclient.utils.SessionManager;

String vcode = SessionManager.getInstance().getValidVCodeForAction("fast_action");
if (vcode != null && !vcode.isEmpty()) {
    url += "&vcode=" + vcode;
    FileLogger.trace("FastActionManager: VCode obtained for sendFastAttackMulti");
} else {
    Log.w(TAG, "❌ VCODE_MISSING: fast_action, abort sendFastAttackMulti");
    FileLogger.trace("FastActionManager: VCode expired, skipping fast_action");
    return; // skip отправку удара
}
```

**Содержание кода:** Отправка быстрого удара с несколькими целями

**Приоритет:** 🔴 **CRITICAL**

---

### 3. AutoFunctionsManager.java - RECOVERY & LOGOFF (2 МЕСТА)

**Расположение:** [app/src/main/java/ru/neverlands/abclient/manager/AutoFunctionsManager.java](AutoFunctionsManager.java)

#### 3.1 Строки 759-760 - Recovery reload after error

**БЫЛО:**
```java
if (AppVars.VCode != null && !AppVars.VCode.isEmpty()) {
    reloadUrl += "&vcode=" + AppVars.VCode;
}
```

**СТАЛО:**
```java
import ru.neverlands.abclient.utils.SessionManager;

String vcode = SessionManager.getInstance().getValidVCodeForAction("main_php_reload");
if (vcode != null && !vcode.isEmpty()) {
    reloadUrl += "&vcode=" + vcode;
    FileLogger.trace("AutoFunctionsManager: VCode obtained for recovery reload");
} else {
    Log.w(TAG, "⚠️ VCODE_EXPIRED: main_php_reload, reload without vcode");
    FileLogger.trace("AutoFunctionsManager: VCode expired for reload, continuing without it");
    // Continuе без VCode (сервер может принять)
}
```

**Содержание:** Восстановление после ошибки - перезагрузка main.php

**Приоритет:** 🔴 **CRITICAL**

---

#### 3.2 Строка 1909 - LogOff (выход из игры)

**БЫЛО:**
```java
String vcode = AppVars.VCode != null ? AppVars.VCode.trim() : "";
```

**СТАЛО:**
```java
import ru.neverlands.abclient.utils.SessionManager;

String vcode = "";
String sessionManagerVcode = SessionManager.getInstance().getValidVCodeForAction("logoff");
if (sessionManagerVcode != null && !sessionManagerVcode.isEmpty()) {
    vcode = sessionManagerVcode.trim();
    FileLogger.trace("AutoFunctionsManager: VCode obtained for logoff");
} else {
    Log.w(TAG, "⚠️ VCODE_EXPIRED: logoff, continuing without vcode");
    FileLogger.trace("AutoFunctionsManager: VCode expired for logoff");
}
```

**Содержание:** Выход из игры (logoff)

**Приоритет:** 🔴 **CRITICAL**

---

### 4. MainActivity.java - SYNC из auto-hit payload ⭐ ОСТАВИТЬ

**Расположение:** [app/src/main/java/ru/neverlands/abclient/MainActivity.java](MainActivity.java)

**Строки:** 1526-1527

**ТЕКУЩЕЕ:**
```java
if (!vcode.equals(AppVars.VCode)) {
    AppVars.VCode = vcode;
    FileLogger.trace("MainActivity: VCode synced from auto-hit payload, new=" + 
        vcode.substring(0, Math.min(4, vcode.length())) + "...");
}
```

**ДОЛЖНО ОСТАТЬСЯ** ✅ (это единственное легитимное место для обновления AppVars.VCode)

**Содержание:** Синхронизация VCode из payload'а авто-удара

**Приоритет:** 🟢 **DO NOT CHANGE** (это правильно!)

---

## 🟡 ВСПОМОГАТЕЛЬНЫЕ файлы (Stage 2 - После верификации Stage 1)

### Файлы со ВСПОМОГАТЕЛЬНЫМИ переменными (не требуют миграции):

#### [MainActivity.java](MainActivity.java)
- LastSubmittedFightCaptchaFinishKey (3-4 обращения)
- ResumeAutoboiAfterCaptcha (7-8 обращений)
- ResumeSearchBoxAfterCaptcha (4-5 обращений)
- ContentLakeHtml (4 обращения - управление кэшем)
- ✅ **СТАТУС:** Оставить в AppVars (это состояние, не VCode)

#### [FightAuto.java](FightAuto.java)
- LastSubmittedFightCaptchaFinishKey (2 обращения)
- ResumeAutoboiAfterCaptcha (2 обращения)
- ResumeSearchBoxAfterCaptcha (2 обращения)
- ✅ **СТАТУС:** Оставить в AppVars (логика боя)

#### [AutoModeForegroundService.java](AutoModeForegroundService.java)
- LastSubmittedFightCaptchaFinishKey (3 обращения)
- ResumeAutoboiAfterCaptcha (1 обращение)
- ✅ **СТАТУС:** Оставить в AppVars (состояние сервиса)

#### [FishAjaxPhp.java](FishAjaxPhp.java)
- ContentLakeHtml (7 обращений)
- ContentLakeHtmlLastUpdateAtMs (4 обращения)
- ✅ **СТАТУС:** Оставить в AppVars (кэш озера, управление TTL)

#### [MainPhp.java](MainPhp.java)
- ContentLakeHtml (4 обращения)
- ContentLakeHtmlLastUpdateAtMs (2 обращения)
- ResumeAutoboiAfterCaptcha (1 обращение)
- ✅ **СТАТУС:** Оставить в AppVars (логика фильтра)

---

## 📋 Deprecated переменные (Stage 2 - Удаление)

### AppVars.FishCurrentVcode - ⚠️ DEPRECATED

**Расположение:** AppVars.java строки 322-331

**СТАТУС:** ❌ **DEPRECATED** - НЕ ИСПОЛЬЗУЕТСЯ

**Причина:** WebViewRequestInterceptor содержит комментарий "ЗАПРЕЩЕНО: AppVars.FishCurrentVcode = newVcode (RULE 5 VIOLATION)"

**Действие:** Удалить переменную и все комментарии о ней (проверить grep на наличие использований перед удалением)

```bash
grep -r "FishCurrentVcode" c:\Users\User\AbclientAndroid\app\src\main\java\
# Должно быть 0 использований (кроме самого AppVars.java)
```

---

## 🎯 Последовательность миграции

### ЭТАП 1 (День 1):

```
1. Фиксация TreasureDig.java (строки 404-405)
2. Фиксация FastActionManager.java (строки 2217-2218)
3. Фиксация AutoFunctionsManager.java место 1 (строки 759-760)
4. Фиксация AutoFunctionsManager.java место 2 (строка 1909)
5. Проверка сборки: gradlew clean assembleDebug
6. Проверка логов: все VCODE_OBTAINED/VCODE_EXPIRED логируются
```

### ЭТАП 2 (День 2):

```
1. Удаление AppVars.FishCurrentVcode (check grep исп-ия перед удалением)
2. Оптимизация ContentLakeHtml (оставить в AppVars или мигрировать в SessionManager)
3. Обогащение SessionManager методами:
   - getValidVCodeForAction("treasure_dig")
   - getValidVCodeForAction("fast_action")
   - getValidVCodeForAction("main_php_reload")
   - getValidVCodeForAction("logoff")
4. Обнка документации SessionManager
5. Проверка сборки: gradlew clean assembleDebug
```

### ЭТАП 3 (День 3 - Verification):

```
1. Финальная проверка grep: AppVars.VCode должен быть только в:
   - AppVars.java (определение)
   - MainActivity.java:1526-1527 (sync из payload)
   - SessionManager.java (объяснения)
2. Логирование проверка: все VCODE_* логи в FileLogger
3. QA тестирование: все fast actions, treasure dig, logoff - работают
4. Комитт: фикс AppVars VCode migration complete
```

---

## ✓ Чек-лист фиксации

### TreasureDig.java

- [ ] Добавить импорт: `import ru.neverlands.abclient.utils.SessionManager;`
- [ ] Добавить импорт: `import ru.neverlands.abclient.utils.FileLogger;`
- [ ] Заменить строки 404-405 на новый код
- [ ] Добавить логирование при успехе и при fallback
- [ ] Проверить возможные обращения к null

### FastActionManager.java

- [ ] Добавить импорт: `import ru.neverlands.abclient.utils.SessionManager;`
- [ ] Заменить строки 2217-2218 на новый код
- [ ] Добавить логирование при успехе и при fallback
- [ ] Добавить return if null check

### AutoFunctionsManager.java (место 1)

- [ ] Добавить импорт: `import ru.neverlands.abclient.utils.SessionManager;`
- [ ] Заменить строки 759-760 на новый код
- [ ] Добавить логирование при успехе и при fallback
- [ ] Проверить continue-logic (reload может выполниться без VCode)

### AutoFunctionsManager.java (место 2)

- [ ] Заменить строку 1909 на новый код с SessionManager
- [ ] Добавить логирование при успехе и при fallback
- [ ] Убедиться что fallback-пустая строка обрабатывается корректно

### Вся миграция

- [ ] grep -r "AppVars\.VCode" app/src/main/java/ возвращает <= 5 строк
- [ ] Сборка успешна: gradlew clean assembleDebug
- [ ] Все VCODE_OBTAINED/VCODE_EXPIRED логируются в FileLogger
- [ ] UTF-8 кодирование всех файлов
- [ ] Тестирование Treasure dig, fast actions, logoff - работают

