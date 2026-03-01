# Debug log — 2026-03-01 — AutoBoi finish captcha

## Контекст
- В логах `Logs/logcat_runtime_20260301_03.txt` авто-завершение боя уходило в частый цикл `act=7`.
- При этом капча завершения боя (`/modules/code/code.php`) загружалась, но popup ввода не открывался.

## Действия
1. Проанализирован поток `MainPhp.mainPhpFight` на ветке `fightEnded + AutoboiOn`.
2. Выявлено: редирект завершения боя отправлялся слишком часто (без минимального интервала).
3. Добавлен fallback-канал для капчи:
   - в `WebViewRequestInterceptor` сохраняется последняя URL картинки `/modules/code/code.php?...`;
   - в `AppVars` добавлены поля `LastFightCaptchaImageUrl`, `LastFightCaptchaImageAtMs`.
4. В `MainPhp` добавлено:
   - `resolveFightCaptchaUrl(...)` (HTML + fallback из перехватчика),
   - `buildDelayedRedirectHtml(...)` (отложенный redirect),
   - троттлинг завершения боя (минимум 1 сек между запросами),
   - очистка fallback капчи после запуска popup.
5. Выполнена проверка сборки:
   - `./gradlew.bat :app:compileDebugJavaWithJavac` — успешно.
   - `./gradlew.bat :app:assembleDebug` — успешно.

## Изменённые файлы
- `app/src/main/java/ru/neverlands/abclient/utils/AppVars.java`
- `app/src/main/java/ru/neverlands/abclient/webview/WebViewRequestInterceptor.java`
- `app/src/main/java/ru/neverlands/abclient/postfilter/MainPhp.java`

## Что проверить на устройстве
- Включить быстрый `Авто-Бой` в момент, когда на завершении нужна капча.
- Проверить появление popup капчи и отправку `finishUrl` с `code=<digits>`.
- Проверить, что нет спама `act=7` чаще ~1 запроса в секунду.
