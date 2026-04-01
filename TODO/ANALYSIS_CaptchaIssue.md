# Анализ: Проблема с капчей при авторыбалке

## Описание проблемы (из слов пользователя)
- **Ситуация:** Авторыбалка показывает капчу, хотя капча куплена
- **Ожидаемое поведение:** Сервер НЕ должен показывать капчу после покупки
- **Гипотеза пользователя:** "кукися отправляем не синхронизированно" - cookies не синхронизированы

## Статус ЭТАП 1 (Завершён)
- ✅ Все 5 логгеров унифицированы → FileLogger
- ✅ Проект компилируется без ошибок
- ✅ Stage 1 рефакторинг готов к работе

## Что нужно для диагностики капчи

### 1. Понимание механизма капчи на сервере
- Server-side flag в HTTP-response headers (например, `X-Captcha-Required: true/false`)?
- Или cookie вроде `captcha_purchased=1` который клиент должен отправлять?
- Проверить **auth sequence** в `AuthManager.java` - есть ли там логика для captcha detection?

### 2. Анализ логов в Critical
- **20260331_chat_poll.log** ✅ ПРОВЕРЕН
  - Cookies правильные (11 штук)
  - Cookies стабильные на протяжении полов минут
  - **Вывод:** Chat poll отправляет cookies корректно

- **20260331_auto_treasure.log** ✅ ПРОВЕРЕН
  - Только логи про усталость 100%, авто-клад stopped
  - **Вывод:** Это не авторыбалка, это другой модуль

- **20260331_logcat_recorder.log** - ТРЕБУЕТ АНАЛИЗА
  - Может содержать ошибку про captcha detection

- **20260331_auto_boss.log** - ТРЕБУЕТ АНАЛИЗА
  - Может быть там видна попытка авто-боя с ошибкой про капчу

### 3. Логи в /files/Logs/Logcat/
- Могут быть отдельные файлы для текущей сессии

## План диагностики

1. **Локализовать где именно capcha-error происходит:**
   - В WebView response? 
   - В HTTP response headers?
   - В HTML body?

2. **Найти в коде где обрабатывается captcha:**
   - Поиск по: `captcha`, `Captcha`, `CAPTCHA`
   - Проверить: `MainPhp.java`, `WebViewRequestInterceptor.java`, `AuthManager.java`
   - Проверить: как парсится HTML response для detection капчи

3. **Проверить cookie transmission:**
   - После auth в `AuthManager` - какие cookies сохраняются?
   - При autofishing запросе - все ли они передаются?
   - Есть ли фильтрация cookies где-то?

4. **Проверить HTTP-interceptor:**
   - `WebViewRequestInterceptor.java` - может там где-то cookies обрезаются/переписываются?

## Ключевые файлы для анализа
1. `AuthManager.java` - как обрабатывается capture purchase
2. `MainPhp.java` - как обрабатывается капча response
3. `WebViewRequestInterceptor.java` - как передаются cookies при перехвате
4. `CookieManager` - правильно ли сохраняются cookies после auth

## Ошибочное состояние

Вероятный сценарий:
1. Пользователь вводит капчу и оплачивает → Это устанавливает cookie на сервере `captcha_purchased=XYZ`
2. Сервер отправляет Set-Cookie в response
3. **ПРОБЛЕМА:** Либо:
   - WebView + OkHttp не синхронизируют cookies между собой
   - InterceptedRequest не передаёт обновлённые cookies
   - Есть временное окно "race condition" где новый cookie не успевает сохраниться

## Next Steps
- [ ] Прочитать `20260331_logcat_recorder.log` полностью
- [ ] Поиск в логе слов: "captcha", "cookie", "auth", "error"
- [ ] Если нашли cappcha error - получить полный stack trace
- [ ] Почитать код `AuthManager`, `MainPhp`, interceptor'ов для капчи
- [ ] Найти где cookies обновляются после покупки капчи
