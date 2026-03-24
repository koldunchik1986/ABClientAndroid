# Задача: post-fight синхронизация HP/MA для автопитья

## Контекст
- После завершения боя `AUTO_DRINK_TRACE` не срабатывал, хотя фактические HP/MA были ниже порога.
- В логе использовался снимок `ins_HP/page` со старыми значениями.

## План
- [x] Проверить текущий источник HP/MA для `tryTriggerAutoDrinkRestoreElixir`.
- [x] Добавить one-shot синхронизацию vitals через `pinfo` в post-fight ветке.
- [x] Сохранить fallback на существующий `ins_HP/page` и unified snapshot.
- [x] Исправить JS bridge-совместимость `ShowHpMaTimers`, чтобы убрать ошибки в консоли.
- [ ] Проверить на реальном сценарии в новом логе после боя.

## Изменённые файлы
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`
- `app/src/main/java/ru/neverlands/abclient/bridge/WebAppInterface.java`

## Ожидаемый результат
- После `act=7` автопитьё принимает решение по свежим HP/MA из `pinfo` (если доступно).
- При недоступности `pinfo` сохраняется прежняя логика fallback без поломки потока.
- Ошибка `window.external.ShowHpMaTimers is not a function` больше не появляется.
