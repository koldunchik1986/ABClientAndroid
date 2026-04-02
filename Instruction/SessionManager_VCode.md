# VCode Инструкция: SessionManager.java

## Назначение и роль VCode

`SessionManager` - единая система управления кодом защиты (VCode) приложения. Автоматически парсит VCode из каждого HTML ответа сервера и кеширует их для последующего использования в защищённых действиях (бой, быстрые действия, AJAX запросы).

## Как используется VCode в файле

### 1. Парсинг VCode из HTML

```java
// WebViewRequestInterceptor вызывает parseVCode() для каждого HTML ответа
parseVCode(html, source);

// 6 regex patterns для парсинга:
1. fight_pm array в боевых фреймах
2. main.php?...&vcode= в основных действиях  
3. go=inf при активном бое
4. go=ret при выходе из боя
5. get_id различные ID модули
6. AJAX с встроенным vcode
```

### 2. Кеширование VCode

```java
SessionContext context = new SessionContext(
    vcode,              // извлечённый VCode
    source,             // источник: "main", "fight", "general", "chat"
    version++,          // увеличивающийся номер версии
    ageMs = 0           // возраст в миллисекундах
);
```

### 3. Получение VCode перед действием

```java
String vcode = getValidVCodeForAction("fight");
if (vcode == null) {
    // Fallback: перезагрузить основную страницу для парсинга VCode
    redirectToMainPhp();
}
```

### 4. Механизм FIGHT_FALLBACK_MODE

```java
// При активном бое используется расширенный timeout:
if (fightInProgress) {
    timeout = 120_000ms;  // 2 минуты вместо стандартных ~60 сек
}
```

## Зависимости

- **WebViewRequestInterceptor** - вызывает `parseVCode()` при каждом HTTP ответе
- **AppVars.LastBoiLog, AppVars.Autoboi** - состояние текущего боя
- **ReentrantReadWriteLock** - потокобезопасный доступ к кешу
- **MainPhp, FightAuto, FastActionManager** - потребители VCode

## Обязательные вызовы

### При боевых действиях:

```java
// 1. В начале боя (FightAuto.processFight):
SessionManager.getInstance().markFightInProgress();
String vcode = SessionManager.getInstance().getValidVCodeForAction("fight_fallback");

// 2. При запросе хода:
String vcode = SessionManager.getInstance().getValidVCodeForAction("fight");

// 3. При завершении боя:
SessionManager.getInstance().clearFightContext();
```

### При быстрых действиях:

```java
// FastActionManager:
String vcode = SessionManager.getInstance().getValidVCodeForAction("fast_action");
if (vcode == null) {
    fastCancel("vcode_not_available");
    return;
}
```

### При AJAX запросах:

```java
// MainPhp:
String vcode = SessionManager.getInstance().getValidVCodeForAction("ajax");
if (vcode == null) {
    // Redirect to main.php to parse fresh VCode
    loadUrl("http://neverlands.ru/main.php");
}
```

## Типичные ошибки VCode

### ❌ Ошибка #1: Использование `AppVars.VCode` вместо SessionManager

```java
// НЕПРАВИЛЬНО:
String url = mainPhpUrl + "&vcode=" + AppVars.VCode;  // ❌ может быть null/старый

// ПРАВИЛЬНО:
String vcode = SessionManager.getInstance().getValidVCodeForAction("action");
if (vcode != null) {
    String url = mainPhpUrl + "&vcode=" + vcode;
}
```

### ❌ Ошибка #2: Отсутствие null check

```java
// НЕПРАВИЛЬНО:
String vcode = getValidVCodeForAction("fight");
submitRequest(vcode);  // ❌ может быть null, приведёт к ошибке

// ПРАВИЛЬНО:
String vcode = getValidVCodeForAction("fight");
if (vcode == null) {
    logError("VCode not available");
    return false;
}
submitRequest(vcode);
```

### ❌ Ошибка #3: markFightInProgress() вызвана ПОСЛЕ LezFight

```java
// НЕПРАВИЛЬНО (9ms gap):
new LezFight(html);                              // ❌ запросит VCode
SessionManager.getInstance().markFightInProgress();  // слишком поздно

// ПРАВИЛЬНО (нет gap):
SessionManager.getInstance().markFightInProgress();  // ① пометить бой
new LezFight(html);                              // ② получить VCode fallback
```

### ❌ Ошибка #4: Не очистить кеш при смене PHPSESSID

```java
// При изменении сессии:
String newPhpSessionId = extractPhpsessidFromCookies();
if (!newPhpSessionId.equals(currentPhpSessionId)) {
    SessionManager.getInstance().clearFightContext();  // ✅ очистить старый VCode
    currentPhpSessionId = newPhpSessionId;
}
```

## Жизненный цикл VCode

```
1. HTML ответ от сервера
   ↓
2. WebViewRequestInterceptor ловит ответ
   ↓
3. parseVCode() извлекает VCode из HTML (6 regex patterns)
   ↓
4. SessionContext создан с версией и временем
   ↓
5. Сохранен в currentContext (или fightStartVCode если бой)
   ↓
6. getValidVCodeForAction() проверяет age и возвращает VCode
   ↓
7. Используется в защищённом запросе (go=inf, fast-action, AJAX)
   ↓
8. При изменении PHPSESSID → clearFightContext()
   ↓
9. При завершении боя → clearFightContext()
```

## Интеграция с другими модулями

### SessionManager → FightAuto

```java
// FightAuto.processFight() → LezFight constructor:
SessionManager.getInstance().markFightInProgress();  // ① mark first
SessionContext ctx = SessionManager.getInstance().getSessionContext();
LezFight fight = new LezFight(html);  // ② uses fallback mode
String vcode = SessionManager.getInstance().getValidVCodeForAction("fight");
```

### SessionManager → MainPhp

```java
// MainPhp при быстрых действиях:
String vcode = SessionManager.getInstance().getValidVCodeForAction("fast_action");
if (vcode != null) {
    String phpUrl = MAIN_PHP + "?get_id=43&act=" + actId + "&vcode=" + vcode;
} else {
    // Fallback: перезагрузить main.php
    loadMainPhpForVCParse();
}
```

### SessionManager → FastActionManager

```java
// FastActionManager.fastStart():
String vcode = SessionManager.getInstance().getValidVCodeForAction("fast_action");
if (vcode == null) {
    FileLogger.trace("FastAction", "VCode not available, cancel");
    fastCancel("vcode_missing");
    return false;
}
submitFastAction(vcode);
```

## FIGHT_FALLBACK_MODE детали

Когда `fightInProgress = true`:
- Используется расширение timeout до 120 секунд
- VCode может быть переиспользован из `fightStartVCode` кеша
- Если свежий VCode недоступен, используется старый (с ageMs проверкой)
- При смене PHPSESSID кеш автоматически инвалидируется

```java
if (fightInProgress && ageMs < 120_000) {
    return fightStartVCode;  // fallback при активном бое
}
```

## Потокобезопасность

Все операции защищены `ReentrantReadWriteLock`:

```java
readLock.lock();
try {
    SessionContext current = currentContext;
    if (current != null && (System.currentTimeMillis() - current.createdAtMs) < timeout) {
        return current.vcode;
    }
} finally {
    readLock.unlock();
}
```

## Проверка перед коммитом

- [ ] Все вызовы `getValidVCodeForAction()` имеют null check
- [ ] markFightInProgress() вызвана ДО LezFight constructor
- [ ] clearFightContext() вызвана при завершении боя
- [ ] parseVCode() логирует ошибки парсинга
- [ ] PHPSESSID изменение вызывает clearFightContext()
- [ ] Нет использования `AppVars.VCode` в новом коде
- [ ] Все потоки используют getValidVCodeForAction(), не локальные переменные
