# Детальный TODO: `WebAppInterface.java` (lockscreen/background)

## Контекст
- [ ] JS-мост соединяет игровые скрипты и Android-код.
- [ ] Если bridge-триггеры не приходят в фоне, авто-цепочка может останавливаться даже при включенном флаге.

## Функции и задачи

### `AutoBoi()`
- [ ] Проверить, что переключение `AppVars.Autoboi` не зависит от видимого UI.
- [ ] Проверить, что при `ON` корректно запускается `requestAutoTurn()` без необходимости ручного обновления кадра.

### `AutoTurn()` / `AutoUd()` / `AutoSelect()`
- [ ] Проверить, доходят ли вызовы до `MainActivity` в lockscreen-сценарии.
- [ ] Проверить, что `runOnUiThread`-маршрутизация не теряет задачи при паузе Activity.

### `processFightHtml(String)`
- [ ] Проверить, вызывается ли обработчик после блокировки экрана.
- [ ] Проверить связку `WebAppInterface -> FightViewModel.processFightHtml(...)`.

### `loadFrame(String, String)` / `redirectToUrl(String)`
- [ ] Проверить, не залипает ли fight frame/load pipeline после screen off.
- [ ] Проверить корректность absolute URL и фактических `loadUrl(...)` вызовов в фоне.

### `chatRefreshN()` / `chatRefreshNow()`
- [ ] Проверить, используются ли эти триггеры как fallback для поддержания room/chat циклов.

## Зависимости
- [ ] `MainActivity` (`requestAutoTurn`, `requestAutoSelect`, `loadChatRefrUrl`, WebView references).
- [ ] `FightViewModel`.
- [ ] `AppVars` (`Autoboi`, `Profile`, URLs).

## Ожидаемый результат анализа
- [ ] Подтверждение, какие bridge-методы критично зависят от активного UI и требуют фонового дубля.

## Промежуточные выводы (статический анализ, 2026-03-02)
- [x] `AutoBoi()`, `AutoTurn()`, `AutoUd()`, `AutoSelect()` маршрутизируют действия в `MainActivity` через `runOnUiThread`.
- [x] `loadFrame(...)` и смежные методы также завязаны на живые ссылки `MainActivity` + `WebView`.
- [x] При паузе/заморозке UI bridge не гарантирует устойчивое выполнение боевого pipeline.
- [ ] Нужна runtime-проверка, продолжают ли приходить bridge-вызовы при `screen off`.
