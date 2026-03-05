# Задача: синхронизация AutoSkin с боевым кадром `main.php`

## Проблема
- [x] В логах `logcat_runtime_20260304_06.txt` разделка не запускалась: в ответах `get_id=56&act=10&go=inf` массив `fight_ty[9]` приходил пустым (`[]`).
- [x] В эталонном потоке ПК-клиента (`SkinBotCaptcha.har`) перед `act=7` идут переходы на обычный `main.php`, где `fight_ty[9]` содержит параметры `get_id=17`.
- [x] В логе `logcat_runtime_20260304_07.txt` обнаружено, что POST-страница `main.php` после удара не проходит через `Filter.process`, поэтому AutoSkin не видит возможную кнопку `Разделать`.

## Реализация (Android)
- [x] `MainActivity`: для POST-ответов при включенном `SkinAuto` перезагрузка идет на полный `main.php` (`?r=...`), а не на `go=inf`.
- [x] `LezFight.BuildFrame()`: watchdog-возврат в AutoSkin-режиме переведен на `main.php` (`?r=...`) для получения серверного `fight_ty[9]`.
- [x] `MainPhp.mainPhpFight()`: добавлен одноразовый `raz probe` для текущего `LogBoi`, если бой завершился на `go=inf` и `mainPhpRaz()` не нашел ссылку.
- [x] `MainActivity.onPageFinished(main.php)`: добавлен JS-поиск ссылки разделки прямо в DOM POST-страницы (`fight_ty[9]`, `onclick`, `href`), и немедленный переход на `get_id=17` при наличии ссылки.

## Проверка
- [x] Сборка `assembleDebug` успешна.
- [ ] Runtime-проверка: в новом logcat должны появиться `onPageFinished: POST-ответ, найдена разделка -> ...get_id=17...` и фактические запросы `main.php?get_id=17...`.
