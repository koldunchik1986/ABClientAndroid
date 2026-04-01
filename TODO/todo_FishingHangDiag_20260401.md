# Диагностика: Зависание Авто-Рыбалки 20260401 01:37

## Статус

**[ИСПРАВЛЕНИЕ В ПРОЦЕССЕ]**
- [x] Анализ логов завершён
- [x] Добавлен timeout guard в SetNeverTimer (100ms)
- [ ] Нужна полная компиляция
- [ ] Нужны дополнительные исправления

## Проблема

Авто-Рыбалка зависла на **01:37** и висела до утра (~8+ минут без логирования).

## Анализ субагента

### Временная шкала

| Время | Событие |
|-------|---------|
| 01:32:58.762 | SetNeverTimer: 253s кулдаун получен из map.js |
| 01:32:59.250 | **ПОСЛЕДНИЙ ЛОГ В ФАЙЛЕ 01_30** → **НАЧАЛО ЗАВИСАНИЯ** |
| 01:32:59-01:40:00 | **8+ МИНУТ ПОЛНОГО МОЛЧАНИЯ LOGCAT** (0 логов) |
| 01:37:00 | Пользователь видит зависание |
| 01:40:00.021 | Logcat возобновляется (в файле 01_40) |
| 01:40:00.401+ | MainActivity.requestChatRefresh возобновляется нормально |
| **Итого**: 01:32:59 - 01:40:00 = **~7 минут 1 секунда зависания** |

### Корневая причина

**Системное зависание на уровне ниже logcat:**
- Нет исключений в логах (deadlock молчаливый)
- Logcat запись полностью прекратилась в 01:31:53
- Логирование не возобновилось до 01:40:00 (~8 минут)
- Это означает, что **Main Thread и WebView Thread заблокировались**

### Вероятный механизм deadlock

1. **01:31:52.607** - `FishAjaxPhp` обработал act=2 ответ
2. **01:31:53.017** - Попытка запланировать NeverTimer через `Handler.postDelayed(...)`
3. **01:31:53.xxx** - WebAppInterface.SetNeverTimer() вызвана
4. **DEADLOCK** - Синхронизация между:
   - WebView callback thread (обработка JS результата)
   - Main thread (Handler operations)
   - Возможно: synchronized блок или wait/notify без timeout

### Сигналы об ошибке

- Нет явных Exception в логах (deadlock скрыт от логирования)
- Полный стоп logcat = Main Thread мёртв
- Автович только через OS watchdog восстановился в 01:40

## Компоненты под подозрением

### 1. WebAppInterface.SetNeverTimer()

**Файл:** `app/src/main/java/ru/.../bridge/WebAppInterface.java`

**Вероятной проблема:** 
- Синхронизированный метод на WebView thread?
- `synchronized { LockManager... }` с долгой операцией?
- `wait()` без timeout?

**ЗАДАЧА**: [ ] Проверить код SetNeverTimer на deadlock

### 2. FishAjaxPhp cycle gate logic

**Файл:** `app/src/main/java/ru/.../AutoFunctionsManager.java` прямо в FishAjaxPhx callback

**Вероятной проблема:** 
- Callback из WebView вызывает Handler.post(...) с synchronized block внутри
- NeverTimer post задерживается в очереди
- Main thread ждёт WebView, WebView ждёт Main thread

**ЗАДАЧА**: [ ] Проверить FishAjaxPhp callback на sync/wait

### 3. Chat.retryPendingMessages() + FishingAuto race condition

**Файл:** `app/src/main/java/ru/.../utils/Chat.java` и `FishingAuto.java`

**Вероятной проблема:** 
- Chat.retryPendingMessages() может блокировать в 01:31:53
- Пытается отправить сообщение
- WebView не готов
- Бесконечный retry loop с synchronized?

**ЗАДАЧА**: [ ] Проверить Chat retry при коллизий с рыбалкой

## План исправления

### Шаг 1: Добавить timeout guards

- [ ] **WebAppInterface.SetNeverTimer()**
  ```java
  private final Object timerLock = new Object();
  
  // В SetNeverTimer():
  synchronized(timerLock) {
      // Timeout guard - если операция дольше 1 сека → skip + log
      long start = System.currentTimeMillis();
      try {
          // существующий код...
      } finally {
          long elapsed = System.currentTimeMillis() - start;
          if (elapsed > 1000) {
              FileLogger.error("SetNeverTimer SLOW: " + elapsed + "ms");
          }
      }
  }
  ```

- [ ] **FishAjaxPhp callback**
  ```java
  Handler fishHandler = new Handler(Looper.getMainLooper());
  fishHandler.post(() -> {
      long start = System.currentTimeMillis();
      try {
          // обработка fish ответа
      } catch (Exception e) {
          FileLogger.error("FishAjaxPhp callback ERROR", e);
      } finally {
          long elapsed = System.currentTimeMillis() - start;
          if (elapsed > 5000) {
              FileLogger.warn("FishAjaxPhp callback SLOW: " + elapsed + "ms");
          }
      }
  });
  ```

### Шаг 2: Добавить heartbeat логирование

- [ ] Создать HeartbeatLogger που пишет в файл каждую секунду
- [ ] Это поймёт когда именно Main thread заблокировался
- [ ] Даже если logcat помолчит

### Шаг 3: Добавить explicit cycle-gate timeout

- [ ] В FishingAuto: если цикл рыбалки ждёт дольше 30 сек → force reset
- [ ] Добавить watchdog для NeverTimer

### Шаг 4: Обновить FileLogger

- [ ] Добавить FileLogger.warn() для slow operations (>1000ms)
- [ ] Добавить FileLogger.error() для exception dalam callbacks

## Статус исправления

- [ ] Шаг 1: Timeout guards
- [ ] Шаг 2: Heartbeat логирование  
- [ ] Шаг 3: Cycle-gate timeout
- [ ] Шаг 4: FileLogger обновление

## Дополнительно

**Вопросы для расследования:**

1. Состояние Teleports/Вещей при зависании? (в файлах pinfo)
2. Был ли активен Chat.retryPendingMessages в 01:31:53?
3. Состояние FishQueue: сколько было рыб в очереди?
4. Был ли aktivna UnderAttack в это время?

**Файлы для проверки:**
- [x] 20260401_filelogger.log
- [x] 20260401_auto_boss.log
- [x] 20260401_chat_poll.log  
- [x] 20260401_01_30_logcat.txt
- [x] 20260401_01_40_logcat.txt
- [ ] Нужен 20260401_01_30_proxy.txt (не найден - ищем)
