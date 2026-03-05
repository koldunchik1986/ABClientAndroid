# Задача: очистка cookies перед входом (как в ПК-версии)

Дата: 2026-03-05
Статус: `[x]` Выполнено

## План

- [x] Найти текущий поток авторизации в Android (`LoginActivity`, `AuthManager`, `NetworkClient`, `CookiesManager`).
- [x] Проверить связанные TODO-анализы по cookies/логину перед изменениями.
- [x] Добавить очистку cookie-хранилища OkHttp (`java.net.CookieManager`) перед каждым новым входом.
- [x] Добавить безопасную очистку WebView cookies с завершением через callback перед стартом авторизации.
- [x] Встроить вызов очистки в `LoginActivity` до `AuthManager.authorize(...)`.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.
- [x] Обновить статус и зафиксировать результат.

## Результат

- Перед каждой новой попыткой входа выполняется очистка cookie в `NetworkClient` и `android.webkit.CookieManager`.
- Авторизация (`AuthManager.authorize`) стартует только после завершения очистки WebView cookies.
- Сборка `:app:compileDebugJavaWithJavac` проходит успешно.
