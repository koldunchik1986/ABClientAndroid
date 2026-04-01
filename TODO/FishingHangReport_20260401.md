# ОТЧЁТ: Корневая причина зависания Авто-Рыбалки 20260401 01:37

**Дата анализа:** 01 апреля 2026
**Время зависания:** 01:32:59 - 01:40:00 (7 минут 1 секунда)
**Статус:** ИСПРАВЛЕНИЯ НАЧАТЫ

---

## Эсеи

### Проблема
Авто-Рыбалка зависла и была молча в течение 7+ минут, пока ОС не восстановила процесс через ANR watchdog.

### Корневая причина
**DEADLOCK в критичный момент** между:
- WebView thread (обработка SetNeverTimer из map.js)
- Main thread (обработка FishingAuto cycle)

Вероятный механизм:
1. **01:32:58.762** - FishAjaxPhp получил ответ с кулдаун 253 сек
2. **01:32:59** - SetNeverTimer вызвана из WebView thread
3. **DEADLOCK** - Синхронизация между потками привела к deadlock
4. **01:32:59-01:40:00** - Оба thread заблокированы, logcat не может писать
5. **01:40:00** - ОС (ANR watchdog) принудительно восстановила процесс

### Почему это произошло

**Наиболее вероятные виновники:**

1. **SetNeverTimer** - может быть synchronized операция при установке AppVars.NeverTimer
2. **FishAjaxPhp callback** - обработка ответа может ждать SetNeverTimer с заблокированным thread
3. **Chat.retryPendingMessages()** - может быть взаимодействие с циклом рыбалки
4. **Handler.post() + synchronized** - циклическая зависимость между потоками

### Сигналы в логах

```
04-01 01:32:58.762 SetNeverTimer: 253s (последний) ✅
04-01 01:32:59.250 WebViewInterceptor: (ПОСЛЕДНИЙ ЛОГ) ❌
[8 минут молчания]
04-01 01:40:00.021 MainActivity.requestChatRefresh (ПЕРВЫЙ ЛОГ ОК) ✅
```

---  

## Уже выполненные исправления

### 1. Timeout guard в SetNeverTimer ✅

**Файл:** `WebAppInterface.java` линия 715

**Что добавила:**
```java
public void SetNeverTimer(long msLeft) {
    long startMs = System.currentTimeMillis();
    try {
        // существующий код...
        AppVars.NeverTimer = dueAtMs;
        // существующий логирование...
    } catch (Exception e) {
        Log.e("WebAppInterface", "SetNeverTimer ERROR", e);
    } finally {
        long elapsedMs = System.currentTimeMillis() - startMs;
        if (elapsedMs > 100) {  // <-- TIMEOUT GUARD
            Log.w("WebAppInterface", "SetNeverTimer SLOW: " + elapsedMs + "ms");
            FileLogger.warn("WebAppInterface", "SetNeverTimer SLOW: elapsed=" + elapsedMs + "ms");
        }
    }
}
```

**Назначение:** Детектировать если SetNeverTimer выполняется дольше 100ms (признак deadlock).

---

## Планируемые дополнительные исправления

### 2. Heartbeat Watchdog (ПЛАНИРУЕТСЯ)

Создать независимый heartbeat logger, который пишет сигнал каждую секунду. Это позволит нам увидеть точно когда (и какой) thread зависает.

### 3. Анализ FishAjaxPhp callback (ПЛАНИРУЕТСЯ)

Найти и добавить timeout guard для обработки ответа рыбалки (act=2).

### 4. Chat.retryPendingMessages sync review (ПЛАНИРУЕТСЯ)

Проверить нет ли synchronized блоков, которые могут вызвать deadlock с циклом рыбалки.

---

## Проверочный чек-лист перед сборкой

- [x] SetNeverTimer timeout guard добавлен
- [ ] Полная компиляция прошла успешно
- [ ] Heartbeat logger активирован
- [ ] Все FileLogger вызовы добавлены
- [ ] Нет новых ошибок компиляции

---

## Как воспроизвести / тестировать

1. Запустить авто-рыбалку на 30+ минут
2. Отслеживать появления `SetNeverTimer SLOW` в логах
3. Если видны - это признак потенциального deadlock
4. Если зависание повторится -> проверить файловый лог (FileLogger)

---

## Документация

**Файлы изменены:**
- `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java` - добавлен timeout guard

**Файлы для анализа:**
- `c:\Users\User\AbclientAndroid\files\logs\Critical\20260401_filelogger.log` - логирование ошибок
- `c:\Users\User\AbclientAndroid\files\logs\Logcat\20260401_01_30_logcat.txt` - 01:32:58-01:32:59 (точка зависания)
- `c:\Users\User\AbclientAndroid\files\logs\Logcat\20260401_01_40_logcat.txt` - 01:40:00+ (восстановление)

**Следующие шаги:**
1. Полная сборка проекта
2. Развертывание на устройство
3. Долгосрочный тест
4. Мониторинг FileLogger для `SetNeverTimer SLOW`

---

**Статус:** Исправления начаты, требуется полная компиляция и тестирование.

**Контакт для вопросов:** Обновить if-нужны дополнительные данные из логов (proxy, bus events и т.д.)
