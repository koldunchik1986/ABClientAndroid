# Auth + Proxy (предварительный разбор, 2026-03-08)

## Цель
Зафиксировать рабочий вариант входа через прокси, причины последних падений и безопасный путь для повторной проверки гипотезы `www` vs `non-www`.

## Симптомы до фикса
- Через прокси авторизация в Android падала на шаге `POST /game.php`.
- Типовой паттерн в логах:
  - `GET /` -> `200`
  - `POST /game.php` -> `405`
  - fallback/retry -> `403` или `400` (squid/nginx).

## Что было изменено
1. `AuthManager`:
- В proxy-режиме auth-base переключён на `http://www.neverlands.ru`.
- Для auth-запросов (`/`, `/game.php`, `/main.php`) используются URL от этого base.

2. `ProxyRuntimeManager`:
- Кодировка `Proxy-Authorization` приведена к `windows-1251` (как в ПК C#).

3. `LocalHttpProxyServer`:
- Добавлен дополнительный retry для `POST /game.php` в origin-form (`/game.php`) после `405`.

4. `NetworkClient`:
- Исправлен выбор timeout в login-phase: если proxy runtime уже поднят, режим timeout считается proxy.
- В proxy-режиме увеличены timeout до `60s`.

## Что показали логи
### `Logs/logcat_runtime_20260308_30.txt` (до final-фикса)
- `POST http://neverlands.ru/game.php` -> `405`
- `POST /game.php` -> `400`

### `Logs/logcat_runtime_20260308_31.txt` (после final-фиксов)
- `GET http://www.neverlands.ru/` -> `200`
- `POST http://www.neverlands.ru/game.php` -> `200`
- `GET http://www.neverlands.ru/main.php` -> `200`
- Авторизация успешна.

## Важный вывод (текущий, предварительный)
Пока нельзя на 100% доказать, что сработал только один фактор.

На текущих данных:
- Вклад `www` очень вероятен (именно с `www` POST перестал возвращать `405`).
- Вклад `cp1251` в `Proxy-Authorization` тоже вероятен (полная 1:1 совместимость с ПК-профилем/прокси).

Итого: рабочий production-вариант сейчас = `www + cp1251`.

## План A/B проверки (чтобы точно отделить причину)
### Вариант A (текущий baseline, рабочий)
- `www` в `AuthManager.resolveAuthBaseUrl()` при proxy.
- `cp1251` в `ProxyRuntimeManager` для BasicAuth.

Ожидаемый маркер:
- `POST http://www.neverlands.ru/game.php` -> `200`.

### Вариант B (тест гипотезы пользователя)
- Оставить `cp1251`.
- Временно вернуть auth-base на `http://neverlands.ru` даже в proxy-режиме.

Ожидаемый маркер:
- Если снова `405/400` на `POST /game.php`, значит ключевой фактор — host `www`.
- Если вход успешный, тогда ключевой фактор — кодировка BasicAuth (`cp1251`), а `www` не обязателен.

## Быстрый откат/переключение
Точка переключения только одна:
- `app/src/main/java/ru/neverlands/abclient/AuthManager.java`
- Метод: `resolveAuthBaseUrl()`.

### Вернуть non-www для теста B
```java
private String resolveAuthBaseUrl() {
    return "http://neverlands.ru";
}
```

### Вернуть рабочий baseline A
```java
private String resolveAuthBaseUrl() {
    final boolean proxyActive = ProxyRuntimeManager.isRunning();
    return proxyActive ? "http://www.neverlands.ru" : "http://neverlands.ru";
}
```

## Что держим в коде сейчас
- Оставляем все фиксы (они не конфликтуют и дают лучший шанс совместимости с нестабильными proxy).
- Следующий шаг только диагностический: разовый A/B тест варианта B с новым логом.

## Чеклист
- [x] Зафиксирован рабочий вариант (`www + cp1251`).
- [x] Описан минимальный путь переключения на тест B.
- [ ] Выполнить тест B (`non-www + cp1251`) и приложить лог.
- [ ] Принять финальное решение по `resolveAuthBaseUrl()`.
