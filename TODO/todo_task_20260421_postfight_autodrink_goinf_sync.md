# Задача: post-fight автопитьё без plain main.php (fix Bad req)

## Контекст
- В свежих логах после `get_id=61&act=7` фиксировался переход `AUTO_DRINK_TRACE post-fight redirect to plain main.php`.
- Далее `WebAppInterface.loadFrame(main_top, main.php)` принимал серверный plain `main.php` (`allow_server_plain_main`).
- По симптомам пользователя это могло проявляться как `bad req` в верхнем фрейме.

## План
- [x] Проверить существующий контур post-fight автопитья в `MainPhp`.
- [x] Подтвердить, что follow-up автопитья уже поддерживает `go=inf/go=inv/im=*`.
- [x] Убрать redirect на plain `main.php` и направлять post-fight sync сразу на `go=inf`.
- [x] Сохранить существующий one-shot marker `autoDrinkPostFightSyncPending` без изменения контракта.
- [ ] Проверить на новом runtime-логе, что после `act=7` больше нет `loadFrame ... to main.php` для этого сценария.

## Изменённые файлы
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

## Ожидаемый результат
- После завершения боя post-fight sync автопитья идёт через `main.php?get_id=56&act=10&go=inf`.
- Топ-фрейм больше не получает лишний plain `main.php` из этой ветки.
- Решение о восстановлении HP/MA по порогам остаётся прежним.
