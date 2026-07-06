# TODO ANClient Anticaptcha

Дата обновления: 2026-06-09.

## Цель

Перевести OCR-распознавание капчи для `ANClient` на правильную архитектуру: клиент не содержит и не запускает Keras/TensorFlow runtime. Клиент отправляет обычные `createTask` и `getTaskResult` HTTP-запросы на локальный portable OCR-сервис, который слушает localhost-порт независимо от папки запуска.

## Источники анализа

| Источник | Вывод |
| --- | --- |
| `IBClient2/instructions/Anticaptcha.md` | Forest DLL не содержит OCR, а работает через HTTP-протокол `createTask`/`getTaskResult` с `ImageToTextTask.body=<base64>` |
| `ANClient/Instructions_AntiCaptchaKerasOCR.md` | Предыдущий план предлагал запуск portable exe из клиента; этот подход теперь признан неправильным для финальной архитектуры |
| `ANClient/MyGuamod/AntiCaptchaManager.cs` | Существующий decision point уже отвечает за `Busy`, получение `CodePng`, stale-check, замену `????`, submit и fallback |
| `ANClient/MyGuamod/LocalCaptchaSolver.cs` | Текущая незавершенная реализация запускала `AntiCaptchaKerasOcr.exe` и модель из `ANClient/OCR/AntiCaptcha`, что нужно заменить на localhost API |
| `IBClient2/scripts/anticaptcha_keras_ocr_runtime.py` | Уже есть CLI-runtime для модели; его нужно расширить server-mode без дублирования preprocessing/model-loading |

## Текущая проблема

- [x] В `ANClient/OCR/AntiCaptcha` лежит portable Keras/TensorFlow runtime как часть папки клиента.
- [x] `LocalCaptchaSolver` запускает subprocess и передает пути к `model.keras`/`metadata.json`.
- [x] UI профиля спрашивает `OCR exe` и `Модель`, то есть закрепляет runtime внутри клиента.
- [x] Это ломает требование переносимости: OCR должен быть отдельным сервисом, а `ANClient` должен знать только URL локального API.

## Целевая архитектура

| Компонент | Ответственность |
| --- | --- |
| `ANClient/MyGuamod/AntiCaptchaManager.cs` | Общий captcha-flow, fallback, применение ответа к `FightLink`, логирование и отправка готового action |
| `ANClient/MyGuamod/LocalCaptchaSolver.cs` | Только HTTP-клиент локального OCR API: `POST /createTask`, затем `POST /getTaskResult` |
| `IBClient2/scripts/anticaptcha_keras_ocr_runtime.py --serve` | Portable Keras OCR server: загружает модель один раз, слушает localhost, принимает AntiCaptcha-like JSON |
| `IBClient2/scripts/build_anticaptcha_ocr_portable.ps1` | Собирает portable OCR server artifact в `ANClient/OCR/AntiCaptcha` для запуска через оболочку `ANClient/OCR` |

## HTTP contract локального OCR

`POST http://127.0.0.1:8765/createTask`

```json
{
  "clientKey": "local",
  "task": {
    "type": "ImageToTextTask",
    "body": "<base64 captcha image>",
    "phrase": false,
    "case": false,
    "numeric": 1,
    "math": false,
    "minLength": 5,
    "maxLength": 5
  }
}
```

Успешный ответ:

```json
{"errorId":0,"taskId":1}
```

`POST http://127.0.0.1:8765/getTaskResult`

```json
{"clientKey":"local","taskId":1}
```

Готовый ответ:

```json
{
  "errorId": 0,
  "status": "ready",
  "solution": {
    "text": "12345",
    "minConfidence": 0.99,
    "meanConfidence": 0.99,
    "charConfidences": "0.99;0.99;0.99;0.99;0.99"
  }
}
```

## План реализации

- [x] Создать текущий TODO и зафиксировать архитектурное решение.
- [x] Найти существующий контур `AntiCaptchaManager`/`LocalCaptchaSolver`, не создавать параллельный captcha-submit flow.
- [x] Заменить subprocess-реализацию `LocalCaptchaSolver` на HTTP-клиент локального service URL.
- [x] Изменить настройки профиля/UI: вместо `OCR exe` и `model.keras` хранить `LocalCaptchaOcrServiceUrl`.
- [x] Расширить Python runtime режимом `--serve`, который слушает `127.0.0.1:8765` и поддерживает `createTask/getTaskResult`.
- [x] Обновить build script portable runtime: output вне `ANClient/OCR` и manifest с server-mode командой запуска.
- [x] Проверить `py_compile` для Python runtime.
- [x] Проверить C# компиляцию доступным MSBuild или зафиксировать блокер среды.
- [x] Проверить, что в новых логах нет raw image/base64/clientKey, только длины/hash/confidence.
- [x] Пересобрать stale portable runtime в `ANClient/OCR/AntiCaptcha`, чтобы `AntiCaptchaKerasOcr.exe` поддерживал `--serve`.
- [x] Проверить, что `Авто-Травник` и `Авто-Лесоруб` идут через общий `AlchemyAjaxPhp -> AntiCaptchaManager -> LocalCaptchaSolver` контур.
- [x] Исправить client-side чтение `minConfidence` из JSON, чтобы `LocalCaptchaSolver` не превращал реальные `0.3407/0.3156` в `0.0000`.
- [x] Добавить режим `LocalCaptchaOcrMinConfidence=0.00` как диагностику/разрешение принимать любой 5-значный ответ без блокировки по confidence.
- [x] Добавить файловый лог OCR-сервера через `--log-dir`, не записывая raw base64/body/clientKey/cookies/session.
- [x] По логам `ANClient/bin/Debug/Logs/Critical/20260607_16_20_*` найти причину live-сбоя: запущенный `bin/Debug/ANClient.exe` был старее исходников и всё ещё логировал `min=0.0000, threshold=0.9000`.
- [x] Исправить OCR server logging: убрать `BaseHTTPRequestHandler.log_message` до отправки headers и писать подробные `createTask/getTaskResult` логи после HTTP-ответа, чтобы вывод Windows-консоли не блокировал клиента.
- [x] Добавить обработку оборванного соединения в `write_json_response`, чтобы `ConnectionReset/ConnectionAborted` не давали traceback в консоли сервера.
- [x] Для локальных OCR POST в `LocalCaptchaSolver` выставить `KeepAlive=false`.
- [x] Добавить выбираемый метод обучения `baseline`/`augmented` в `anticaptcha_train_keras_ocr.py` без удаления прежнего baseline.
- [x] Для `augmented` добавлять геометрические варианты только в train split, оставляя validation split без размножения.
- [x] Пробросить `training.method` и `training.augmentFactor` из `ANClient/OCR/settings.json` через `ocr.ps1 train`.

## Проверки

- [x] `py -3.12 -m py_compile "IBClient2\scripts\anticaptcha_keras_ocr_runtime.py"` выполнен без ошибок.
- [x] `py -3.12 "IBClient2\scripts\anticaptcha_keras_ocr_runtime.py" --help` показывает `--serve`, `--host`, `--port`.
- [x] VS 2022 MSBuild: `"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe" "ANClient\ANClient.csproj" /t:Build /p:Configuration=Debug /p:Platform=AnyCPU /p:OutDir="bin\VerifyLocalOcrVS\ANClient\"` завершился успешно: 0 warnings, 0 errors.
- [-] `ANClient\ANClient10.csproj` не проходит сборку из-за старого unrelated blocker: `MyForms\FormContact.resx` зависит от отсутствующего `MyForms\FormContact.cs`.
- [x] Статически проверено: новый клиентский лог `LOCAL_OCR_TRACE` не пишет raw base64/body/clientKey; серверный лог пишет только `taskId`, `activeTasks`, размер `bytes`, распознанный 5-значный `text` и confidence.
- [x] `powershell -NoProfile -ExecutionPolicy Bypass -File "ANClient\OCR\ocr.ps1" help` выполнен успешно.
- [x] `powershell -NoProfile -ExecutionPolicy Bypass -File "ANClient\OCR\ocr.ps1" stats` выполнен успешно: dataset `IBClient2/Captcha`, 500 PNG, 500 размеченных labels.
- [x] После `ANClient\OCR\ocr.ps1 build` packaged exe `ANClient/OCR/AntiCaptcha/AntiCaptchaKerasOcr/AntiCaptchaKerasOcr.exe --help` показывает `--serve`, `--host`, `--port`, `--log-dir`.
- [x] End-to-end local OCR API test: временно запущен сервер `http://127.0.0.1:8765/`, `ocr.ps1 test` отправил `createTask/getTaskResult` и получил `12217` с `minConfidence=0.9995`.
- [x] Проверен `ANClient/OCR/AntiCaptcha/portable_runtime_manifest.json`: `runCommand` содержит `--log-dir ..\Logs`.
- [x] Проверен файловый лог сервера `ANClient/OCR/Logs/OCR_20260607_141026.log`: есть `createTask solved ... text=12217 minConfidence=0.9995` и `getTaskResult ... status=ready text=12217`.
- [x] 2026-06-07 16:43: `ANClient\ANClient.csproj` собран прямо в штатный `ANClient\bin\Debug` без `OutDir`: 0 warnings, 0 errors.
- [x] 2026-06-07 16:44: пересобран portable OCR runtime после server logging fix; `ocr.ps1 test` получил `12217`, `minConfidence=0.9995`.
- [x] Проверен новый файловый лог `ANClient/OCR/Logs/OCR_20260607_164441.log`: только `createTask solved` и `getTaskResult`, без pre-response `http ... POST ...` access-log.
- [x] `py -3.12 -m py_compile "IBClient2\scripts\anticaptcha_keras_ocr_common.py" "IBClient2\scripts\anticaptcha_train_keras_ocr.py"` выполнен без ошибок.
- [x] `py -3.12 "IBClient2\scripts\anticaptcha_train_keras_ocr.py" --help` показывает `--train-method {baseline,augmented}`, `--augment-factor`, `--augment-seed`.
- [x] `powershell -NoProfile -ExecutionPolicy Bypass -File "ANClient\OCR\ocr.ps1" help` показывает описание `training.method` для `baseline`/`augmented`.
- [x] Проверка типовых mojibake-паттернов по измененным OCR/TODO/Python файлам не нашла совпадений.
- [x] 2026-06-07 22:15: проверен активный runtime `ANClient/OCR/AntiCaptcha/metadata.json`: модель установлена из `train_20260607_184919`, `trainMethod=augmented`, `augmentFactor=2`, `sampleCount=500`, `effectiveTrainCount=1200`.
- [x] Проверен `train_20260607_184919/train_report.md`: `Train full accuracy=1.0000`, `Validation full accuracy=0.0000`, `Validation char accuracy=0.2140`; новый метод лучше baseline `0.1780`, но full-match всё ещё нулевой.
- [x] Проверен `train_20260607_184919/training_log.csv`: лучший `val_char_accuracy=0.2400` был на эпохе 11, финальная эпоха дала `0.2140`; `best_model.keras` потенциально лучше установленного `model.keras`.
- [x] Проверен live log `OCR_20260607_220956.log`: 3 успешных `createTask/getTaskResult`, без ошибок, minConfidence `0.3852..0.6124`, meanConfidence avg около `0.7737`.
- [x] 2026-06-07: без нового обучения проверены `model.keras` и `best_model.keras` из `train_20260607_184919` через `anticaptcha_predict_keras_ocr.py` на текущей папке `IBClient2/Captcha`; во время сравнения найдено 540 PNG, позднее `ocr.ps1 stats` показал уже 556 PNG/labels.
- [x] Сравнение по исходному split `train_20260607_184919`: установленный final `model.keras` = validation full `0.0000`, char `0.2140`, new/unsplit char `0.2150`; `best_model.keras` = validation full `0.0000`, char `0.2400`, new/unsplit char `0.1950`. `best_model.keras` не установлен, потому что full-match не улучшился, новые PNG хуже, confidence ниже.
- [x] 2026-06-07: временно запущен OCR server с активным `ANClient/OCR/AntiCaptcha/model.keras`; `/health` вернул `status=ok`, `ocr.ps1 test` для `12217.png` вернул `12217`, `minConfidence=0.999947`, `meanConfidence=0.999976`.
- [x] Добавлены сравнимые `modelType`: `fixed5-cnn-multihead` (baseline), `fixed5-cnn-regularized`, `fixed5-slice-cnn`, `fixed5-crnn-ctc`; runtime/predict умеют читать `metadata.modelType`, multi-head и CTC decoder.
- [x] Добавлен `anticaptcha_experiment_matrix.py` и команда `ocr.ps1 experiments`: формирует стабильные checkpoint-manifest выборки для `500/700/1000/1500` и пишет `experiment_summary.csv`/`experiment_report.md`, не устанавливая модель в `AntiCaptcha/`.
- [x] Проанализированы пары одинаковых labels `44043.png`/`44043_001.png` и `38945.png`/`38945_001.png`: искажение не похоже на единый глобальный affine; символы независимо смещаются/наклоняются, поэтому точное обратное восстановление без знания параметров генератора маловероятно.
- [x] По итогам анализа пар добавлены preprocessing-варианты `color-outline-crop` и `color-outline-dewave`; наиболее безопасный кандидат для проверки — `color-outline-crop`, потому что нормализует bbox текста без сильного разрушения формы цифр.
- [x] Проверки без обучения: `py_compile` новых/изменённых OCR scripts, `train --help`, `ocr.ps1 help`, dry-run experiment matrix на текущих `724` labels; доступны checkpoint `500` и `700`, `1000/1500` будут skipped до накопления dataset.
- [x] 2026-06-08: полная matrix дошла до всех 12 вариантов checkpoint `500`; лучший результат дал `fixed5-crnn-ctc + color-outline`: validation full `0.6000`, validation char `0.8340`, single test `12217/12217`. Остальные варианты checkpoint `500` имеют validation full `0.0000`.
- [x] 2026-06-08: focused run `ANClient/OCR/Output/focus_cp700_ctc_color_outline_20260608` для `fixed5-crnn-ctc + color-outline` на checkpoint `700` завершён успешно: validation full `0.7071`, validation char `0.8843`, train full `0.9714`, train char `0.9889`, single test `12217/12217`, minConfidence `0.5912`, meanConfidence `0.9118`.
- [x] 2026-06-08: исправлен `ocr.ps1 build`, чтобы build script получал текущие active `runtime.model`/`runtime.metadata` из `settings.json` и не перезаписывал модель старым default artifact из `IBClient2/runtime_dll/...`.
- [x] 2026-06-08: исправлен `build_anticaptcha_ocr_portable.ps1`, чтобы при совпадении source/target не выполнять `Copy-Item` файла самого в себя.
- [x] 2026-06-08: portable runtime пересобран с сохранением active `model.keras`/`metadata.json`; `/health` вернул `scriptVersion=4`, `modelType=fixed5-cnn-multihead`.
- [x] 2026-06-08: end-to-end `ocr.ps1 test` на rebuilt runtime вернул `12217`, `minConfidence=0.999947`, `modelType=fixed5-cnn-multihead`, `scriptVersion=4`.
- [x] 2026-06-08: offline evaluation focused CTC на всей текущей папке `IBClient2/Captcha` (`725` labeled из `726` inputs) дала full `0.9048`, char `0.9641`; отчёт `ANClient/OCR/Output/focus_cp700_ctc_color_outline_20260608/eval_all_current/predict_report.md`.
- [x] 2026-06-08: holdout вне checkpoint `700` для focused CTC: `25` labeled, full `0.5200`, char `0.8560`, `12` full errors. Для active CNN на том же списке: full `0.7600`, char `0.8000`, `6` full errors; этот holdout не является полностью честным для active CNN, но показывает, что CTC ещё нужно проверять на новых независимых капчах перед установкой.
- [ ] CTC-модель `focus_cp700_ctc_color_outline_20260608` пока не установлена в `ANClient/OCR/AntiCaptcha`; перед установкой нужно явно заменить active `model.keras`/`metadata.json` и проверить `/health`/`ocr.ps1 test` уже с `modelType=fixed5-crnn-ctc`.
- [x] 2026-06-08: перед новым обучением `ocr.ps1 stats` показывал `1000` PNG, но только `999` labeled; после исправления датасета текущая статистика стала `1000` PNG / `1000` labeled / `996` unique labels.
- [x] 2026-06-08: focused run `ANClient/OCR/Output/focus_cp999_ctc_color_outline_20260608` для `fixed5-crnn-ctc + color-outline` на checkpoint `999` завершён успешно: train full `0.9562`, train char `0.9857`, validation full `0.7350`, validation char `0.9230`, `restoreBestWeights=true`.
- [x] 2026-06-08: offline evaluation на одинаковой текущей папке `IBClient2/Captcha` (`1000` inputs, `1000` labeled): `cp999 CTC` full `0.9120`, char `0.9732`; `cp700 CTC` full `0.8350`, char `0.9420`; active `fixed5-cnn-multihead` full `0.4000`, char `0.5176`.
- [x] 2026-06-08: confidence calibration для `cp999 CTC` на текущем eval: `minConfidence>=0.999` даёт `163/1000` accepted и `0` wrong; `>=0.995` даёт `372` accepted и `2` wrong; `>=0.990` даёт `446` accepted и `4` wrong. На validation split уже при `>=0.995` есть `1` wrong из `42`, поэтому confidence не считается гарантией.
- [x] 2026-06-08: `cp999` обучался на manifest из `999` файлов; текущий eval содержит один новый файл `84445.png`, он распознан правильно как `84445` с `minConfidence=0.749849`.
- [x] 2026-06-08: independent folder `Captcha_test` в workspace не найден (`**/Captcha_test`, `**/Captcha_test/*.png`, `**/*Captcha*test*` без результатов). Без независимого holdout модель `cp999 CTC` не устанавливать в active runtime, несмотря на сильное улучшение над active CNN на текущей папке.
- [ ] CTC-модель `focus_cp999_ctc_color_outline_20260608` пока не установлена в `ANClient/OCR/AntiCaptcha`; перед установкой нужен independent holdout или явное решение принять риск.
- [x] 2026-06-08: после исправления датасета выполнен true checkpoint `1000`: `ANClient/OCR/Output/focus_cp1000_ctc_color_outline_20260608`, `fixed5-crnn-ctc + color-outline`, train full `0.9888`, train char `0.9960`, validation full `0.7950`, validation char `0.9320`, best epoch `19`, single test `12217/12217`.
- [x] 2026-06-08: offline evaluation `cp1000 CTC` на текущей папке `IBClient2/Captcha` (`1000` inputs, `1000` labeled) дала full `0.9500`, char `0.9832`; это лучше `cp999 CTC` (`0.9120`/`0.9732`) и active CNN (`0.4000`/`0.5176`), но почти весь eval совпадает с training set.
- [x] 2026-06-08: confidence calibration для `cp1000 CTC` на текущем eval: `minConfidence>=0.999` даёт `154/1000` accepted и `0` wrong; `>=0.995` даёт `347` accepted и `1` wrong; `>=0.990` даёт `423` accepted и `1` wrong; `>=0.980` даёт `498` accepted и `2` wrong. На validation split при `>=0.995` есть `1` wrong из `42`.
- [x] 2026-06-08: high-confidence wrong у `cp1000 CTC` сохраняются: `50649 -> 50646` с `minConfidence=0.998404`, `62906 -> 62606` с `minConfidence=0.983805`, `87136 -> 80136` с `minConfidence=0.973236`.
- [ ] CTC-модель `focus_cp1000_ctc_color_outline_20260608` пока не установлена в `ANClient/OCR/AntiCaptcha`; для цели `99%` нужен independent holdout `500-1000` новых капч или явное решение использовать модель как best-current с риском ошибок.
- [x] 2026-06-08: создан автономный OCR dev-контур внутри `ANClient/OCR`: scripts теперь в `ANClient/OCR/Scripts`, train dataset в `ANClient/OCR/Data/Captcha` (`1000` PNG / `1000` labeled), mini-test в `ANClient/OCR/Data/Captcha_test` (`21` PNG). `IBClient2/scripts` оставлен как успешный backup и не используется как текущая рабочая копия.
- [x] 2026-06-08: `ANClient/OCR/settings.json` переведён на локальные `Scripts`, `Data/Captcha`, `Data/Captcha_test`, `fixed5-crnn-ctc`, `dropout=0.5`, `installAfterTrain=false`, checkpoint `1000`; старые output-папки `400/500/700` удалены, оставлены контрольные `cp999` и `cp1000`.
- [x] 2026-06-08: dev scripts в `ANClient/OCR/Scripts` обновлены до `SCRIPT_VERSION=5`: локальные default paths, усиленная train-only augmentation, `ReduceLROnPlateau`, команда `ocr.ps1 analyze`, отдельный `anticaptcha_analyze_errors.py`, portable build script с default output в `ANClient/OCR/AntiCaptcha`.
- [x] 2026-06-08: strong-augmentation run `focus_cp1000_ctc_color_outline_augstrong_20260608` остановлен по timeout `7200000 ms`; финальных `model.keras`/`metadata.json` нет, для оценки создан временный `metadata_eval.json` и использован `best_model.keras`.
- [x] 2026-06-08: evaluation strong-augmentation best на том же текущем `1000` dataset дала full `0.9370`, char `0.9738`; это хуже baseline `cp1000 CTC` (`0.9500`/`0.9832`). Ошибок больше: `63` против `50`.
- [x] 2026-06-08: mini-test `ANClient/OCR/Data/Captcha_test` (`21` PNG) тоже не подтвердил улучшение: baseline `cp1000 CTC` full `0.6190`, char `0.8857`; strong-augmentation best full `0.5714`, char `0.8286`. Размер mini-test слишком мал для production-решения, но strong augmentation хуже на одинаковом наборе.
- [x] 2026-06-08: error analysis создан для baseline и strong-augmentation: `eval_all_current/error_analysis`, `eval_captcha_test/error_analysis`, `eval_all_current_best/error_analysis`, `eval_captcha_test_best/error_analysis`. У strong-augmentation на текущем `1000` есть wrong при `minConfidence>=0.999` (`86827 -> 86627`, `0.999345`), поэтому confidence остаётся не гарантийным.
- [ ] Strong-augmentation модель не устанавливать в `ANClient/OCR/AntiCaptcha`; текущий best-current кандидат остаётся `focus_cp1000_ctc_color_outline_20260608`, но для цели `99%` всё ещё нужен independent holdout `500-1000` новых капч или явное принятие риска.
- [x] 2026-06-09: проведены manual full-train запуски без shell timeout: `manual_cp1000_strong_augmented_full_20260608_195406`, `manual_cp1000_backup_augmented_full_20260608_212318`, `manual_cp1000_backup_augmented_full_20260608_231417`. Все обучались на одном `ANClient/OCR/Data/Captcha` (`1000` labels), active runtime не менялся.
- [x] 2026-06-09: strong manual run проиграл backup runs: validation full/char `0.6200`/`0.8520`, eval current `0.9190`/`0.9692`, mini-test `0.4762`/`0.8095`; не рассматривать как кандидат для установки.
- [x] 2026-06-09: backup manual run `20260608_212318` показал лучший общий результат: validation `0.8300`/`0.9370`, eval current `0.9600`/`0.9840`, high-confidence профиль на current лучше (`minConfidence>=0.995`: `435` accepted, `0` wrong; `>=0.999`: `318`, `0` wrong). На mini-test `21` PNG результат `0.6190`/`0.8095`.
- [x] 2026-06-09: backup manual run `20260608_231417` показал лучший mini-test (`0.7143`/`0.8762`, `15/21`), но хуже validation/current (`0.7600`/`0.9360`, current `0.9410`/`0.9848`) и имеет high-confidence wrong на current (`33913 -> 33313` с `0.999543`, `77937 -> 77337` с `0.999079`). Из-за маленького mini-test (`21`) это не перевешивает общий профиль `20260608_212318`.
- [x] Текущий recommended candidate для ручного live-теста `manual_cp1000_backup_augmented_full_20260608_212318` установлен пользователем в active runtime. Перед production-решением всё ещё нужен independent holdout `500-1000` новых капч; mini-test `21` использовать только как слабый сигнал.
- [x] 2026-06-09: live-log после установки `manual_cp1000_backup_augmented_full_20260608_212318` показал high-confidence wrong (`50225`, `minConfidence=0.9962`) и два low-confidence rejected (`47134`, `0.7687`; `60150`, `0.5710`). Вывод: confidence не является гарантией правильности, а порог `LocalCaptchaOcrMinConfidence=0.8000` блокирует ввод даже визуально верных ответов.
- [x] 2026-06-09: исправлен fallback auto-cut alchemy при `local OCR failed`: вместо перехода в ручное окно и зависания pending cut теперь вызывается существующий `Filter.CancelPendingAlchemyCut("anti_captcha_failed:local_ocr", true)`, который очищает captcha-state и планирует повторный `Оглядеться` через `AutoCutRuntime.ScheduleLookRetry` с учётом `NeverTimer`.
- [x] 2026-06-09: свежие логи `01:50-02:00` подтвердили, что после low-confidence retry планируется через `NeverTimer`, но фактическое потребление зависит от ближайшего UI timer tick и текущего auto-cut/cleanup состояния. Добавлен расширенный лог `look retry scheduled/consumed` с `neverTimerApplied`, `neverTimerAt`, `scheduledAt`, `now`, `dueLagMs`.
- [x] 2026-06-09: при ответе Neverlands `Неверный код защиты`/wrong captcha теперь сохраняется последняя отправленная OCR-картинка в `ANClient/bin/Debug/Logs/Captcha`.
- [x] 2026-06-09: по логам `02:10` подтверждено, что новый лаг после successful OCR не был OCR-зависанием: после `act=3` cleanup запускался во время активного `NeverTimer`, а ветка `before_auto_drink` повторно кликала inventory link `main.php?...go=inv&im=0` до готовности сервера. Исправлен существующий `MainPhpAutoCutCleanupRedirect`: cleanup теперь ждёт `NeverTimer`, пишет `cleanup waits NeverTimer before inventory redirect` и планирует существующий retry/reload через `AutoCutRuntime.ScheduleLookRetry("cleanup_wait:...")` вместо цикла redirect-запросов.
- [x] 2026-06-09: по логам `02:30` подтверждена fight completion captcha с low-confidence (`22398`, `minConfidence=0.0000`, threshold `0.6000`). Для `main.php?code=????&get_id=61&act=7...` локальный OCR low-confidence теперь не уходит во внешний/manual fallback, а очищает текущую картинку, пишет `fight captcha refresh scheduled after local OCR failure` и делает `ReloadMainPhpInvoke`, чтобы fightframe получил новую капчу (аналог нажатия refresh/`4-3.gif`). Ручное окно ввода капчи отключено: auto-trigger `PromptManualCaptcha()` больше не создаёт `FormCode`, tray double-click больше не открывает `FormCode`, а fight/fish manual-сигналы `Ввод цифр` заменены логированием `manual captcha prompt disabled`.
- [x] 2026-06-11: wrong-captcha для автоспила теперь сохраняется по отправленному OCR-тексту: `ANClient/bin/Debug/Logs/Captcha/12345.png`, при конфликте `12345_001.png`, `12345_002.png`, `12345_003.png` и далее.
- [x] 2026-06-11: после `Эликсира Восстановления` общий auto-cut контур возвращается к `Флора` перед следующим `Оглядеться`: `MainPhpDrinkHpMa` выставляет `AppVars.SwitchToFlora = true`, как уже делалось для `Эликсира Блаженства`.

## Обучение цифр

- [x] 2026-06-09: добавлен режим `Система -> Обучение цифр`, который в локации `Посёлок Лазурный` использует существующий browser/proxy-flow: DOM-клик по HTML-кнопке `Ресурсы`, перехват `modules/code/code.php?...`, локальный OCR, сохранение PNG и следующий клик после результата.
- [x] 2026-06-09: первая реализация с прямым чтением `vcode` из текущего `main.php` заменена на event-driven proxy-hook, потому что ручной клик `Ресурсы` реально идёт через `store_ajax.php?vcode=...` внутри браузерного контекста.
- [x] 2026-06-09: после OCR-result следующий автоклик `Ресурсы` планируется через `2000 ms`, чтобы не дергать сервер сразу после получения ответа.
- [x] 2026-06-09: accepted captcha сохраняется в `ANClient/bin/Debug/Logs/Captcha/True/12345.png`; low-confidence captcha с валидно распознанными 5 цифрами сохраняется в `ANClient/bin/Debug/Logs/Captcha/Train/12345.png`; при конфликте имён используется `12345_001.png`, `12345_002.png`, `12345_003.png` и далее. Если OCR не вернул валидные 5 цифр, fallback-имя для `Train` остаётся `HHmmss.png`.

## Проверка AutoCut captcha

- [x] `AlchemyAjaxPhp.ProcessAlchemyAct1()` выбирает ресурс для активного `AutoCutMode` (`Herb` или `Tree`) через `FormMain.IsResourceAutoCut(...)`.
- [x] `DispatchPendingAlchemyCut(...)` формирует `AppVars.FightLink` с `code=????` и вызывает `TryStartAlchemyCaptchaSolve(...)`, если `CaptchaRequired=true`.
- [x] `EnsureAlchemyCaptchaState(...)` сохраняет `AppVars.CodeAddress` для `modules/code/code.php?...`, после чего `AntiCaptchaManager.TrySolveCurrentCaptcha()` получает/ждет PNG и передает байты в `LocalCaptchaSolver`.
- [x] `LocalCaptchaSolver.TrySolve(...)` первым делает локальные HTTP `createTask/getTaskResult` на `LocalCaptchaOcrServiceUrl`, поэтому `Авто-Травник` и `Авто-Лесоруб` используют локальный OCR при включенных `AntiCaptchaEnabled` и `LocalCaptchaOcrEnabled`.
- [x] После распознавания `AntiCaptchaManager.ApplySolvedCaptcha(...)` заменяет `????`, а `TrySubmitReadyAlchemyFightLink(...)` отправляет готовый `alchemy_ajax.php?act=3...` через `AjaxGet`; это общий submit для трав и деревьев.

## Инварианты

- `ANClient` не запускает TensorFlow/Keras и не хранит путь к модели.
- `ANClient` сохраняет общий fallback порядок: local OCR service -> `anti-captcha.com` при включенном fallback -> `Recognizer.Perform()` при `DoGuamod` -> ручной ввод; для auto-cut alchemy отказ local OCR без внешнего fallback не открывает ручной ввод, а отменяет pending cut и ждёт повторный `Оглядеться` через `NeverTimer`.
- Существующие проверки `IsSameCaptchaContext(...)` и замена `????` остаются только в `AntiCaptchaManager`.
- Сырой base64/body, cookies, session id и API key не пишутся в обычные логи.
- `ANClient.exe` не использует файлы `ANClient/OCR/AntiCaptcha` напрямую; эта папка является portable runtime для внешнего OCR-сервера, которым управляют `ANClient/OCR/ocr.ps1` и батники.
- Обучение имеет два режима: `baseline` сохраняет прежний pipeline, `augmented` добавляет train-only варианты без изменения runtime preprocessing.

## Portable shell в `ANClient/OCR`

- [x] Добавить `settings.json` с host/port, runtime paths, dataset paths и training params.
- [x] Добавить `ocr.ps1` как единый CLI для `menu/start/train/stats/test/settings/build/health/help`.
- [x] Добавить батники `Запуск`, `Обучение`, `Статистика`, `Тест`, `Настройки`, `Меню` и ASCII `OCR.bat`.
- [x] Добавить подробный `ANClient/OCR/README.md` по каждому пункту меню.
- [x] Оставить интеграцию `ANClient` через localhost API; папка `OCR` управляет сервером, но клиент не запускает exe напрямую.

## Оставшиеся проверки вручную

- [ ] В живом клиенте включить local OCR service и для диагностики поставить `Мин. confidence=0.00` либо порог ниже фактического `minConfidence` из server log.
- [ ] На реальной капче `Авто-Травник`/`Авто-Лесоруб` проверить клиентский лог `LOCAL_OCR_TRACE ready ... code=... minConfidence=...` и последующий submit без `????`.
