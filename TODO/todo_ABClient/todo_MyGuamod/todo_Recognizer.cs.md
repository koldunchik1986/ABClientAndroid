
### 1. План портирования Recognizer.cs

Файл `Recognizer.cs` — это статический класс-менеджер, который управляет процессом распознавания CAPTCHA, выступая в роли связующего звена между основной логикой приложения и OCR-движком `NeuroBase`.

### 2. Функциональность в C#

- **Назначение:** Оркестрация процесса распознавания CAPTCHA.
- **`Perform()` / `Hop()`:** Основная логика. `Perform` запускает `Hop` в фоновом потоке. `Hop` ожидает изображение капчи, передает его в `NeuroBase` для анализа, получает результат и, в случае успеха, подставляет его в ссылку для продолжения боя.
- **`PrepareData()`:** Отвечает за однократную загрузку обученной нейросети (файла `abneuro.dat`) в `NeuroBase` при первом обращении.
- **Управление состоянием:** Использует статические флаги (`Busy`, `Ready`) для контроля над процессом распознавания.

### 3. Решение для портирования на Android

Поскольку сам движок `NeuroBase` заменяется на Google ML Kit, этот класс-оркестратор также должен быть заменен. Его обязанности будут распределены между `ViewModel` и `Repository`.

- **Архитектура:** Вместо статического менеджера будет использоваться `GuamodViewModel` для управления состоянием и `CaptchaRepository` для выполнения самой операции распознавания.
- **Замена `NeuroBase`:** Все вызовы к `NeuroBase` будут заменены на вызовы к `TextRecognizer` из Google ML Kit.
- **Замена `PrepareData`:** Этот метод становится ненужным, так как модели ML Kit управляются сервисами Google Play.

### 4. План реализации

1.  **Создать `CaptchaRepository.kt`:**
    - Создать `suspend fun recognize(bitmap: Bitmap): Result<String>`.
    - Внутри этой функции будет находиться логика работы с `TextRecognizer` из ML Kit (как описано в анализе `NeuroBase.cs`).
    - Функция будет возвращать `Result` (успех с текстом или ошибка).
2.  **Создать `GuamodViewModel.kt`:**
    - Будет содержать `LiveData` для состояний: `val isRecognizing = MutableLiveData<Boolean>()`, `val recognitionResult = MutableLiveData<Event<Result<String>>>()`.
    - Создать метод `startRecognition(bitmap: Bitmap)`, который:
        - Устанавливает `isRecognizing.value = true`.
        - Запускает корутину, которая вызывает `captchaRepository.recognize(bitmap)`.
        - Помещает результат в `recognitionResult.value`.
        - Устанавливает `isRecognizing.value = false`.
3.  **Интеграция:**
    - Тот компонент, который инициирует распознавание (например, `FightViewModel`), будет получать `Bitmap` капчи из `WebView`.
    - Он будет вызывать `guamodViewModel.startRecognition(bitmap)`.
    - `FightViewModel` будет наблюдать за `guamodViewModel.recognitionResult`. При получении успешного результата, он будет использовать распознанный код для отправки следующего запроса на сервер.

- [ ] Создать `CaptchaRepository` с методом `recognize`, использующим ML Kit.
- [ ] Создать `GuamodViewModel` для управления процессом и состоянием.
- [ ] Интегрировать `GuamodViewModel` с другими частями приложения (например, `FightViewModel`), которые требуют решения капчи.
- [x] Удалить необходимость в `PrepareData` и `abneuro.dat`.
