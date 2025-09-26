
### 1. План портирования ClearExplorerCacheForm.cs

Файл `ClearExplorerCacheForm.cs` представляет собой простую форму, которая отображает процесс очистки кэша и позволяет пользователю отменить операцию.

### 2. Функциональность в C#

- **Назначение:** Предоставить пользователю обратную связь о длительной операции (очистка кэша) и дать возможность ее прервать.
- **Реализация:**
    - Показывает окно с текстовым сообщением.
    - Имеет кнопку "Отмена", которая устанавливает флаг `IsAllowed = false`.
    - Логика очистки, вероятно, находится в другом потоке и использует делегаты (`BeginInvoke`) для обновления текста на форме и ее закрытия.

### 3. Решение для портирования на Android

Эта функциональность в Android реализуется с помощью `DialogFragment` для отображения UI и `ViewModel` с корутинами для выполнения фоновой задачи.

- **UI:** Вместо модальной формы используется `DialogFragment`, который содержит `ProgressBar` и кнопку "Отмена".
- **Фоновая задача:** Логика очистки кэша выполняется в корутине, запущенной из `ViewModel`.
- **Взаимодействие:** `ViewModel` и `DialogFragment` общаются через `LiveData`.

### 4. План реализации

1.  **Создать `ClearCacheViewModel.kt`:**
    - `ViewModel` будет содержать логику очистки кэша.
    - Объявить `LiveData` для отслеживания состояния: `val isLoading = MutableLiveData<Boolean>()`, `val progressMessage = MutableLiveData<String>()`.
    - Создать публичный метод `startCacheClearing()`, который запускает корутину.
    - В корутине выполнить очистку кэша `WebView`:
      ```kotlin
      // context можно получить из getApplication() во ViewModel
      val webView = WebView(context)
      webView.clearCache(true) // true - включить удаление файлов с диска
      ```
    - Создать публичный метод `cancelCacheClearing()`, который будет устанавливать флаг для отмены корутины.
2.  **Создать `ClearCacheDialogFragment.kt`:**
    - Создать `DialogFragment` с макетом, содержащим `ProgressBar` и `Button` ("Отмена").
    - В `onViewCreated` подписаться на `LiveData` из `ClearCacheViewModel`.
    - При изменении `isLoading` показывать/скрывать диалог.
    - При изменении `progressMessage` обновлять `TextView` в диалоге.
    - По нажатию на кнопку "Отмена" вызывать `viewModel.cancelCacheClearing()` и закрывать диалог.

- [ ] Создать `ClearCacheViewModel` с логикой очистки кэша в корутине.
- [ ] Создать макет для `DialogFragment` с `ProgressBar`.
- [ ] Создать `ClearCacheDialogFragment` и связать его с `ViewModel` через `LiveData`.
