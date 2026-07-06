# ANClient Portable OCR Service

Дата обновления: 2026-06-07.

## Что это

Portable OCR теперь работает как отдельный локальный HTTP-сервис, а не как Keras/TensorFlow runtime внутри `ANClient`.

`ANClient` делает только AntiCaptcha-like запросы:

```text
POST http://127.0.0.1:8765/createTask
POST http://127.0.0.1:8765/getTaskResult
```

OCR-сервис можно запускать из любой папки. Главное, чтобы он слушал тот же локальный URL, который указан в настройках `ANClient`.

## Архитектура

| Компонент | Назначение |
| --- | --- |
| `ANClient/MyGuamod/LocalCaptchaSolver.cs` | HTTP-клиент локального OCR API |
| `ANClient/MyGuamod/AntiCaptchaManager.cs` | Общий captcha-flow, fallback и отправка готового кода |
| `IBClient2/scripts/anticaptcha_keras_ocr_runtime.py` | Python runtime, умеет CLI predict и `--serve` HTTP server |
| `IBClient2/scripts/build_anticaptcha_ocr_portable.ps1` | Сборка переносимого OCR-сервиса через PyInstaller |
| `IBClient2/runtime_dll/anticaptcha_keras_ocr/portable_service` | Рекомендуемая папка готового portable OCR-сервиса |

## Быстрый запуск готового portable service

Если portable service уже собран, запустить его можно так:

```powershell
& "IBClient2\runtime_dll\anticaptcha_keras_ocr\portable_service\AntiCaptchaKerasOcr\AntiCaptchaKerasOcr.exe" --serve --host 127.0.0.1 --port 8765
```

Ожидаемый лог старта:

```text
LOCAL_OCR_SERVER listening http://127.0.0.1:8765/ model=... metadata=...
```

Пока это окно открыто, `ANClient` может отправлять капчи на локальный OCR.

## Настройка ANClient

Открыть настройки Anti-Captcha в клиенте:

```text
Инструменты -> Anti-Captcha...
```

Установить параметры:

| Поле | Значение |
| --- | --- |
| `Автораспознавание капчи` | включено |
| `Локальный Keras OCR через localhost API` | включено |
| `Service URL` | `http://127.0.0.1:8765/` |
| `Мин. confidence` | `0.00` для диагностики/принятия любого 5-значного ответа или рабочий порог после калибровки модели |
| `Если локальный OCR не сработал, использовать anti-captcha.com` | по желанию |
| `API key` | нужен только для fallback на `anti-captcha.com` |
| `numeric` | `1 - только цифры` |
| `minLength` | `5` |
| `maxLength` | `5` |

Если локальный OCR включен, `API key` не обязателен. Если OCR-сервис недоступен и внешний fallback выключен, клиент перейдет к старому `Recognizer.Perform()` при включенном `DoGuamod` или к ручному вводу.

## Проверка health endpoint

После запуска OCR-сервиса проверить, что порт слушается:

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8765/health"
```

Ожидаемый ответ:

```json
{
  "status": "ok",
  "scriptVersion": 3,
  "modelFile": "model.keras",
  "metadataFile": "metadata.json"
}
```

## Проверка createTask/getTaskResult вручную

Подготовить тестовую картинку из dataset:

```powershell
$imagePath = "IBClient2\Captcha\12217.png"
$body = [Convert]::ToBase64String([IO.File]::ReadAllBytes($imagePath))
$request = @{
    clientKey = "local"
    task = @{
        type = "ImageToTextTask"
        body = $body
        phrase = $false
        case = $false
        numeric = 1
        math = $false
        minLength = 5
        maxLength = 5
    }
} | ConvertTo-Json -Depth 5 -Compress

$create = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8765/createTask" -ContentType "application/json" -Body $request
$create
```

Ожидаемый `createTask`:

```json
{"errorId":0,"taskId":1}
```

Получить результат:

```powershell
$resultRequest = @{ clientKey = "local"; taskId = $create.taskId } | ConvertTo-Json -Compress
$result = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8765/getTaskResult" -ContentType "application/json" -Body $resultRequest
$result
$result.solution.text
```

Ожидаемый `getTaskResult`:

```json
{
  "errorId": 0,
  "status": "ready",
  "solution": {
    "text": "12217",
    "minConfidence": 0.99,
    "meanConfidence": 0.99,
    "charConfidences": "..."
  }
}
```

## Сборка portable service

Сборка выполняется из корня репозитория:

```powershell
IBClient2\scripts\build_anticaptcha_ocr_portable.ps1 -InstallBuildDeps
```

По умолчанию скрипт берет модель:

```text
IBClient2\runtime_dll\anticaptcha_keras_ocr\train_20260607_040621_283325\model.keras
IBClient2\runtime_dll\anticaptcha_keras_ocr\train_20260607_040621_283325\metadata.json
```

И собирает portable service сюда:

```text
IBClient2\runtime_dll\anticaptcha_keras_ocr\portable_service
```

Внутри должны быть файлы:

```text
portable_service\AntiCaptchaKerasOcr\AntiCaptchaKerasOcr.exe
portable_service\model.keras
portable_service\metadata.json
portable_service\portable_runtime_manifest.json
```

Если нужно собрать с другой моделью:

```powershell
IBClient2\scripts\build_anticaptcha_ocr_portable.ps1 `
    -ModelPath "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_YYYYMMDD_HHMMSS\model.keras" `
    -MetadataPath "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_YYYYMMDD_HHMMSS\metadata.json"
```

## Dev-запуск без PyInstaller

Для разработки можно запускать server-mode напрямую через venv:

```powershell
& ".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_keras_ocr_runtime.py" `
    --serve `
    --host 127.0.0.1 `
    --port 8765 `
    --model "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_20260607_040621_283325\model.keras" `
    --metadata "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_20260607_040621_283325\metadata.json"
```

Этот режим требует установленный Python venv и зависимости TensorFlow/Pillow/NumPy. Для обычного запуска рядом с клиентом нужен portable exe, а не venv.

## Что писать в Service URL

Обычный вариант:

```text
http://127.0.0.1:8765/
```

Если порт занят, можно запустить OCR на другом порту:

```powershell
& "IBClient2\runtime_dll\anticaptcha_keras_ocr\portable_service\AntiCaptchaKerasOcr\AntiCaptchaKerasOcr.exe" --serve --host 127.0.0.1 --port 8766
```

Тогда в `ANClient` указать:

```text
http://127.0.0.1:8766/
```

Не нужно указывать `/createTask` в настройке. Клиент сам добавляет endpoint к базовому URL.

## Логи и диагностика

В логах `ANClient` искать:

```text
LOCAL_OCR_TRACE createTask
LOCAL_OCR_TRACE processing
LOCAL_OCR_TRACE ready
LOCAL_OCR_TRACE solved
LOCAL_OCR_TRACE low_confidence rejected
LOCAL_OCR_TRACE failed
LOCAL_OCR_TRACE fallback_external
LOCAL_OCR_TRACE fallback_neuro
```

В консоли OCR-сервиса искать:

```text
LOCAL_OCR_SERVER listening
LOCAL_OCR_SERVER createTask solved taskId=... text=... minConfidence=...
LOCAL_OCR_SERVER getTaskResult taskId=... status=ready text=... minConfidence=...
LOCAL_OCR_SERVER createTask failed ...
```

При запуске через `ANClient/OCR/ocr.ps1 start` те же строки пишутся в `ANClient/OCR/Logs/OCR_YYYYMMDD_HHMMSS.log`.

Логи не должны содержать raw base64 картинки, cookies, session id или реальный `clientKey`. Допустимы только размер картинки, `taskId`, распознанный 5-значный ответ и confidence.

## Частые ошибки

| Симптом | Причина | Что сделать |
| --- | --- | --- |
| `LOCAL_OCR_TRACE failed` сразу после `createTask` | OCR-сервис не запущен или URL неверный | Проверить `/health`, порт и `Service URL` |
| `ERROR_BAD_ENDPOINT` | В `Service URL` указали endpoint вместо base URL | Указать `http://127.0.0.1:8765/` |
| `ERROR_IMAGE_BODY_ABSENT` | Некорректный JSON createTask | Проверить, что `task.body` содержит base64 картинки |
| `ERROR_OCR_FAILED` | Модель не смогла открыть/распознать картинку | Проверить формат PNG и модель/metadata |
| `low_confidence rejected` | Confidence ниже порога в настройках | Поставить `Мин. confidence=0.00` для диагностики/автоввода или дообучить модель |
| `taskId is empty` | Сервис вернул неуспешный `createTask` | Смотреть `errorCode`/`errorDescription` в логах |

## Важные правила

- `ANClient` не должен запускать `AntiCaptchaKerasOcr.exe` сам.
- `ANClient` не должен знать путь к `model.keras` или `metadata.json`.
- `ANClient/OCR/AntiCaptcha` содержит portable runtime OCR-сервера; клиентский код не использует эту папку напрямую.
- Перед запуском клиента OCR-сервис должен уже слушать локальный порт.
- Fallback порядок сохраняется: local OCR service -> `anti-captcha.com` -> старый Neuro recognizer -> ручной ввод.
