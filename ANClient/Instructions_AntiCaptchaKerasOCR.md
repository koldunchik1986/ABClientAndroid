# ANClient: Keras OCR вместо Neuro для капчи

Дата фиксации: 2026-06-07.

## Текущий статус

Keras OCR pipeline находится в `IBClient2/scripts` и работает offline, без запуска клиента и без сетевых запросов.

Текущая модель:

| Артефакт | Назначение |
| --- | --- |
| `IBClient2/runtime_dll/anticaptcha_keras_ocr/train_20260607_040621_283325/model.keras` | Основная модель, обученная на всех 257 PNG из `IBClient2/Captcha` |
| `IBClient2/runtime_dll/anticaptcha_keras_ocr/train_20260607_040621_283325/best_model.keras` | Копия финальной модели для совместимости с predict flow |
| `IBClient2/runtime_dll/anticaptcha_keras_ocr/train_20260607_040621_283325/metadata.json` | Параметры preprocessing/model/split и метрики |
| `IBClient2/runtime_dll/anticaptcha_keras_ocr/predict_20260607_042125_345407/predict_report.md` | Проверка загрузки сохраненной модели: `257/257` |

Важное ограничение: модель уверенно распознает текущий известный набор `IBClient2/Captcha`, но holdout на unseen PNG пока слабый. Для боевой замены `Neuro` нужен добор данных и проверка на отложенном наборе.

## Быстрое использование текущего OCR

Эти команды нужны только для разработки/дообучения. Runtime в `ANClient` должен использовать portable `AntiCaptchaKerasOcr.exe`, а не `.venv_anticaptcha_keras` на целевом ПК.

Проверить один PNG:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_predict_keras_ocr.py" --model "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_20260607_040621_283325\model.keras" --input-path "IBClient2\Captcha\12217.png" --preview-count 0
```

Проверить папку PNG:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_predict_keras_ocr.py" --model "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_20260607_040621_283325\model.keras" --input-dir "IBClient2\Captcha" --preview-count 0
```

Результаты появляются в новой папке:

```text
IBClient2/runtime_dll/anticaptcha_keras_ocr/predict_YYYYMMDD_HHMMSS_microseconds/
```

Основные файлы результата:

| Файл | Что смотреть |
| --- | --- |
| `predict_report.md` | `Full accuracy`, `Char accuracy`, если имя файла содержит правильный ответ |
| `predictions.csv` | `input`, `expected`, `predicted`, `fullMatch`, `minConfidence`, `meanConfidence` |
| `predictions.jsonl` | То же в формате JSONL для парсинга из .NET/скриптов |

Для runtime fallback использовать `minConfidence`. На текущем известном наборе значения обычно около `0.99+`, но на unseen капчах порог нужно калибровать только после добора данных.

## Как добавлять новые samples

1. Сохранять PNG в `IBClient2/Captcha`.
2. Имя файла должно содержать правильный 5-значный ответ: `12345.png`, `sample_12345.png`.
3. Не сохранять рядом cookies, session id, `clientKey`, raw JSON, raw base64 body.
4. Если ответ неизвестен, не класть файл в обучающую папку до разметки.
5. Для боевого качества целевой объем: `1000+` размеченных PNG.

В `ANClient` уже есть сохранение текущей картинки в `Logs/Captcha` внутри `MyGuamod/Recognizer.cs`. Для dataset эти файлы нужно переименовывать по правильному ответу и переносить в `IBClient2/Captcha`.

## Как дообучать

Проверить venv и зависимости:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" --version
".venv_anticaptcha_keras\Scripts\python.exe" -c "import tensorflow as tf, numpy as np, PIL; print(tf.__version__); print(np.__version__); print(PIL.__version__)"
```

Если venv отсутствует:

```powershell
py -3.12 -m venv ".venv_anticaptcha_keras"
".venv_anticaptcha_keras\Scripts\python.exe" -m pip install -r "IBClient2\scripts\requirements_anticaptcha_keras.txt"
```

Сначала всегда делать holdout-обучение, чтобы проверить переносимость:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_train_keras_ocr.py" --train-dir "IBClient2\Captcha" --epochs 80 --batch-size 32 --validation-ratio 0.2 --preview-count 24
```

Критерий готовности для замены `Neuro`:

| Метрика | Минимум для эксперимента | Желательно для runtime |
| --- | --- | --- |
| `validation.fullAccuracy` | `0.85+` | `0.95+` |
| `validation.charAccuracy` | `0.97+` | `0.99+` |
| `minConfidence` на правильных ответах | стабильно выше выбранного порога | `0.90+` после калибровки |

Если holdout хороший, обучить финальную модель на всех samples:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_train_keras_ocr.py" --train-dir "IBClient2\Captcha" --epochs 80 --batch-size 32 --validation-ratio 0 --preview-count 24
```

После обучения проверить сохраненную модель отдельным predict run:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_predict_keras_ocr.py" --model "IBClient2\runtime_dll\anticaptcha_keras_ocr\train_YYYYMMDD_HHMMSS_microseconds\model.keras" --input-dir "IBClient2\Captcha" --preview-count 0
```

## Что проверять после дообучения

1. `train_report.md`: число samples, train metrics, validation metrics.
2. `predict_report.md`: сохраненная модель грузится и дает ожидаемые ответы.
3. `predictions.csv`: нет массовых ошибок на конкретной позиции цифры.
4. `previews/*_01_preprocessed.png`: цифры видны после `color-outline`, сетка не забивает glyph.
5. `metadata.json`: `scriptVersion`, `sampleCount`, `preprocess`, `imageWidth`, `imageHeight` соответствуют ожиданиям.

Команды быстрой проверки scripts:

```powershell
".venv_anticaptcha_keras\Scripts\python.exe" -m py_compile "IBClient2\scripts\anticaptcha_keras_ocr_common.py" "IBClient2\scripts\anticaptcha_train_keras_ocr.py" "IBClient2\scripts\anticaptcha_predict_keras_ocr.py"
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_train_keras_ocr.py" --help
".venv_anticaptcha_keras\Scripts\python.exe" "IBClient2\scripts\anticaptcha_predict_keras_ocr.py" --help
```

## Как привязать OCR в ANClient вместо Neuro

Цель: не создавать новый параллельный captcha-flow. Нужно встроиться в уже существующий контур `ANClient/MyGuamod/AntiCaptchaManager.cs`, потому что он уже отвечает за `Busy`, ожидание `AppVars.CodePng`, проверку `CodeAddress`, замену `????`, отправку готового `FightLink`, fallback на `Recognizer.Perform()` и ручной ввод.

Не нужно первым шагом править `NeuroBase`. `Neuro` должен остаться fallback до подтверждения качества Keras OCR.

### Текущий captcha-flow в ANClient

| Файл | Роль |
| --- | --- |
| `ANClient/ANForms/FormMainTicks.cs` | В состоянии `AutoboiState.Guamod` первым вызывает `AntiCaptchaManager.TrySolveCurrentCaptcha()`, потом fallback на `Recognizer.Perform()` или ручной ввод |
| `ANClient/MyGuamod/AntiCaptchaManager.cs` | Сейчас отправляет `AppVars.CodePng` на `anti-captcha.com`, получает текст, заменяет `????` в `AppVars.FightLink`, отправляет готовую ссылку |
| `ANClient/MyGuamod/Recognizer.cs` | Старый локальный `Neuro` solver, вызывает `NeuroBase.Calculate()` и `NeuroBase.Gyp()` |
| `ANClient/AppVars.cs` | Хранит `CodeAddress`, `CodePng`, `CodePngAddress`, `GuamodCode`, `FightLink` |
| `ANClient/ANProxy/Session.cs` | Перехватывает `code.php` и кладет bytes в `AppVars.AssignCodePng(...)` |

### Рекомендуемая архитектура интеграции

Добавить новый модуль:

```text
ANClient/MyGuamod/LocalCaptchaSolver.cs
```

Назначение модуля:

```csharp
internal static class LocalCaptchaSolver
{
    internal static bool TrySolve(byte[] imageBytes, out string code, out double minConfidence, out string diagnostic)
}
```

Runtime-версию делать через переносимый OCR executable, а не через `python.exe` из `.venv_anticaptcha_keras`.

Причины:

| Причина | Деталь |
| --- | --- |
| `ANClient` target | `ANClient.csproj` сейчас `TargetFrameworkVersion v2.0`; TensorFlow/Keras напрямую туда не ложится без большого апгрейда runtime |
| Переносимость | На другом ПК не должно требоваться ставить Python, venv, pip-пакеты или TensorFlow вручную |
| Стабильность | Ошибка TensorFlow не должна падать внутри WinForms процесса |
| Минимальная правка | Запуск локального `AntiCaptchaKerasOcr.exe` можно встроить в существующий `AntiCaptchaManager.SolveWorker()` и оставить внешний anti-captcha/Neuro fallback |

### Runtime deployment модели

Для dev можно использовать текущий путь модели из `IBClient2`, но для runtime лучше не завязывать `ANClient` на `IBClient2/runtime_dll`.

Рекомендуемая структура рядом с `ANClient.exe`:

```text
OCR/AntiCaptcha/AntiCaptchaKerasOcr/AntiCaptchaKerasOcr.exe
OCR/AntiCaptcha/model.keras
OCR/AntiCaptcha/metadata.json
OCR/AntiCaptcha/portable_runtime_manifest.json
```

`AntiCaptchaKerasOcr/` содержит переносимый runtime и DLL-зависимости, собранные PyInstaller. `model.keras` и `metadata.json` лежат отдельно и являются заменяемой базой обучения.

Сборка runtime из корня репозитория:

```powershell
IBClient2\scripts\build_anticaptcha_ocr_portable.ps1 -InstallBuildDeps
```

Важно: Python/venv нужны только на машине сборки portable runtime. На целевом ПК копируется готовая папка `OCR/AntiCaptcha`; Python, venv и pip-пакеты там не нужны.

Модель около `156 MB`. Перед добавлением в git отдельно решить, хранить ли ее в репозитории, в release-архиве или как локальный runtime artifact.

### LocalCaptchaSolver portable runtime contract

Минимальный contract:

1. Получить `byte[] imageBytes`.
2. Сохранить во временный PNG: `%TEMP%/anclient_keras_ocr_<guid>/captcha.png`.
3. Запустить переносимый OCR executable:

```text
<AntiCaptchaKerasOcr.exe> --input-path <temp.png> --model <model.keras> --metadata <metadata.json> --output-json <result.json> --preview-count 0
```

4. Прочитать `<result.json>`.
5. Проверить `predicted` по regex `^\d{5}$`.
6. Проверить `minConfidence >= threshold`.
7. Вернуть `true` только если код валиден и confidence проходит порог.
8. Не логировать raw image/base64, только размер, hash, predicted length, confidence.

Начальные настройки:

| Параметр | Dev default |
| --- | --- |
| `ocrExePath` | `OCR/AntiCaptcha/AntiCaptchaKerasOcr/AntiCaptchaKerasOcr.exe` |
| `modelPath` | `OCR/AntiCaptcha/model.keras` или `model.keras` в корне приложения |
| `minConfidenceThreshold` | `0.90` после калибровки, временно можно `0.80` только для диагностики |
| `timeoutMs` | `30000` для первого portable-process варианта |

### Где менять AntiCaptchaManager

Основная точка: `ANClient/MyGuamod/AntiCaptchaManager.cs`.

Сейчас `TrySolveCurrentCaptcha()` возвращает `false`, если нет `AntiCaptchaApiKey`:

```csharp
if (string.IsNullOrEmpty(AppVars.Profile.AntiCaptchaApiKey))
{
    return false;
}
```

После подключения local OCR это условие надо заменить:

```text
Если включен local OCR, API key не нужен.
Если local OCR выключен или недоступен, тогда API key нужен для external fallback.
```

Рекомендуемая логика:

```csharp
var localOcrEnabled = true; // сначала hardcoded для dev, потом вынести в Profile/UI
var externalFallbackEnabled = !string.IsNullOrEmpty(AppVars.Profile.AntiCaptchaApiKey);

if (!localOcrEnabled && !externalFallbackEnabled)
{
    return false;
}
```

В `SolveWorker(...)` первым делом пробовать local OCR:

```csharp
string localCode;
double localMinConfidence;
string localDiagnostic;
if (LocalCaptchaSolver.TrySolve(imageBytes, out localCode, out localMinConfidence, out localDiagnostic))
{
    if (!IsSameCaptchaContext(challenge, codeAddress))
    {
        ClearCaptchaImageIfCurrent(codeAddress);
        AppLog.w(Tag, "LOCAL_OCR_TRACE stale solution ignored");
        return;
    }

    ApplySolvedCaptcha(challenge, codeAddress, localCode, "Local OCR", localMinConfidence);
    return;
}
```

Затем fallback на существующий `CreateTask/WaitForResult`:

```text
Если local OCR вернул false и есть AntiCaptchaApiKey, выполнять текущий external anti-captcha.com flow.
Если local OCR вернул false и API key нет, вызвать FailAndFallback(...), чтобы сработал Neuro/manual fallback.
```

### Вынести общий apply-код

В `AntiCaptchaManager.SolveWorker()` сейчас блок строк после успешного external solve делает одно и то же, что понадобится local OCR:

```csharp
AppVars.GuamodCode = text.Trim();
AppVars.ClearCodePng();
AppVars.FightLink = AppVars.FightLink.Replace("????", AppVars.GuamodCode);
lastFailedChallenge = string.Empty;
TrySubmitSolvedAlchemyLink();
TrySubmitSolvedAutoboiFightLink();
UpdateGuamodMessage(...);
UpdateTexLog(...);
PostAntiCaptchaCodeSubmittedToChat(...);
```

Нужно вынести в метод:

```csharp
private static void ApplySolvedCaptcha(string challenge, string codeAddress, string code, string source, double minConfidence)
```

Правила метода:

1. Проверить `IsSameCaptchaContext(challenge, codeAddress)` перед применением.
2. `code = (code ?? string.Empty).Trim()`.
3. Если `code` не `^\d{5}$`, не применять.
4. Заменить `????` в `AppVars.FightLink`.
5. Вызвать `TrySubmitSolvedAlchemyLink()` и `TrySubmitSolvedAutoboiFightLink()`.
6. Логировать source и confidence без raw image.
7. Чат-сообщение должно писать источник: `Local OCR` или `anti-captcha.com`.

### Как оставить Neuro fallback

`Recognizer.Perform()` не удалять.

Fallback порядок после внедрения:

```text
1. Local Keras OCR
2. anti-captcha.com, если задан API key и включен fallback
3. Neuro Recognizer.Perform(), если AppVars.Profile.DoGuamod == true
4. Manual captcha prompt
```

Это сохраняет старое поведение и позволяет тестировать Keras OCR без риска полной потери распознавания.

### Настройки UI/Profile

Минимальный dev-этап можно сделать с hardcoded paths в `LocalCaptchaSolver`, но финально лучше добавить поля в профиль:

```csharp
internal bool LocalCaptchaOcrEnabled { get; set; }
internal string LocalCaptchaOcrExePath { get; set; }
internal string LocalCaptchaOcrModelPath { get; set; }
internal double LocalCaptchaOcrMinConfidence { get; set; }
internal bool LocalCaptchaExternalFallbackEnabled { get; set; }
```

Файлы для профиля:

| Файл | Что добавить |
| --- | --- |
| `MyProfile/UserConfigVars.cs` | Поля настроек |
| `MyProfile/UserConfig.cs` | Значения по умолчанию |
| `MyProfile/UserConfigLoad.cs` | Загрузка XML |
| `MyProfile/UserConfigSave.cs` | Сохранение XML |
| `ANForms/FormSettingsAntiCaptcha.cs` | UI для local OCR |
| `ANForms/FormSettingsAutoCut.cs` | Если нужен тот же блок в настройках автосреза |

UI-текст лучше переименовать с `Использовать Anti-Captcha (anti-captcha.com)` на `Автораспознавание капчи`, а внешний сервис сделать отдельным fallback checkbox.

### Логи для интеграции

Добавить новые маркеры через `AppLog`:

| Маркер | Когда писать |
| --- | --- |
| `LOCAL_OCR_TRACE started` | Перед запуском subprocess |
| `LOCAL_OCR_TRACE solved` | Код принят, confidence прошел порог |
| `LOCAL_OCR_TRACE low_confidence` | Код есть, но confidence ниже порога |
| `LOCAL_OCR_TRACE invalid_code` | OCR вернул не 5 цифр |
| `LOCAL_OCR_TRACE timeout` | Portable OCR process не завершился за timeout |
| `LOCAL_OCR_TRACE failed` | Любая ошибка запуска/парсинга |
| `LOCAL_OCR_TRACE fallback_external` | Переход на anti-captcha.com |
| `LOCAL_OCR_TRACE fallback_neuro` | Переход на `Recognizer.Perform()` |

Секреты и raw image bytes в лог не писать.

### Проверка после интеграции без GUI

Сначала проверить portable runtime side:

```powershell
"ANClient\OCR\AntiCaptcha\AntiCaptchaKerasOcr\AntiCaptchaKerasOcr.exe" --model "ANClient\OCR\AntiCaptcha\model.keras" --metadata "ANClient\OCR\AntiCaptcha\metadata.json" --input-path "IBClient2\Captcha\12217.png" --output-json "$env:TEMP\ocr_result.json" --preview-count 0
```

Затем проверить C# side отдельным временным harness или debug вызовом `LocalCaptchaSolver.TrySolve(...)` на bytes из `IBClient2/Captcha/12217.png`.

Ожидаемый результат:

```text
code=12217
minConfidence >= threshold
return true
```

### GUI smoke после интеграции

1. Не делать частые логины: максимум 2-3 входа в минуту, лучше пауза 60-120 секунд.
2. Включить `AntiCaptchaEnabled` и local OCR.
3. API key можно оставить пустым, если проверяется local-only режим.
4. Дождаться captcha flow в рыбалке/автобое/алхимии.
5. В логах должны быть `LOCAL_OCR_TRACE started` и либо `solved`, либо понятный fallback.
6. При `solved` должен замениться `????` в `AppVars.FightLink` и выполниться submit через существующие `TrySubmitSolved...` методы.
7. При low confidence должен сработать внешний сервис, Neuro или ручной ввод, а не зависание в `Busy=true`.

### Чего не делать

1. Не удалять `NeuroBase` и `Recognizer.Perform()` до стабильной holdout-метрики на большом dataset.
2. Не запускать TensorFlow напрямую внутри `.NET 2.0` процесса как первый шаг.
3. Не создавать отдельный captcha submit flow рядом с `AntiCaptchaManager`.
4. Не обходить `IsSameCaptchaContext(...)`, иначе можно отправить код от старой капчи.
5. Не логировать base64 картинки, cookies, session id, API key.
6. Не завязывать release `ANClient` на временную папку `IBClient2/runtime_dll/...`; модель должна быть runtime artifact рядом с `ANClient` или настраиваемым путем.

## Короткий план реализации

1. Скопировать модель/scripts в `ANClient/OCR/AntiCaptcha` для dev runtime.
2. Создать `ANClient/MyGuamod/LocalCaptchaSolver.cs` с subprocess-вызовом portable `AntiCaptchaKerasOcr.exe`.
3. Вынести общий apply-код из `AntiCaptchaManager.SolveWorker()` в `ApplySolvedCaptcha(...)`.
4. В `SolveWorker()` сначала вызвать `LocalCaptchaSolver.TrySolve(...)`.
5. Оставить текущий `CreateTask/WaitForResult` как external fallback.
6. Изменить gate `AntiCaptchaApiKey`: local OCR должен работать без API key.
7. Добавить `LOCAL_OCR_TRACE` логи.
8. Проверить offline на PNG из `IBClient2/Captcha`.
9. Проверить GUI flow с одной captcha и cooldown.
10. Только после стабильного holdout качества отключать `Neuro` как обязательный fallback.
