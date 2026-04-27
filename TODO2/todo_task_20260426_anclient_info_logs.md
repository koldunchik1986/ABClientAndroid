# Перенос игровых логов ANClient из Logs в info

## Цель

Не удалять игровые данные пользователя при нажатии `Настройки -> Очистить логи`.

## Проверка текущего состояния

- [x] Проверить реализацию `clear_logs` в `SettingsActivity`.
- [x] Проверить `FileLogger.clearAllLogs()`.
- [x] Проверить текущие пути `Chat` и `ChatStats`.

## Реализация

- [x] Оставить `FileLogger.clearAllLogs()` очисткой всей папки `files/Logs`.
- [x] Перенести новые chat-логи в `files/info/<nick>/<date>_chat.html`.
- [x] Перенести новые stat-логи в `files/info/<nick>/<date>_stat.txt`.
- [-] Миграция legacy-файлов `files/Logs/<nick>/*_chat.html` и `*_stat.txt` перед очисткой логов не нужна: старые файлы очищаются вместе с `files/Logs`.
- [x] Сохранить чтение legacy-статистики из старых корневых путей для обратной совместимости до очистки логов.

## Проверка

- [x] Собрать `:app2:compileDebugJavaWithJavac`.
- [x] Проверить отсутствие mojibake/BOM в изменённых файлах.
