# План портирования AskPassword.cs

Файл `AskPassword.cs` реализует диалоговое окно для запроса и проверки пароля у пользователя.

## Функциональность в C#

*   **Назначение**: Запросить у пользователя пароль и разблокировать кнопку "ОК" только после того, как будет введен правильный пароль.
*   **Проверка**: В конструктор формы передается хэш правильного пароля. При каждом вводе символа, форма вычисляет хэш от введенной строки (с помощью `Helpers.Crypts.Password2Hash`) и сравнивает его с эталонным. Кнопка "ОК" активна только при совпадении хэшей.
*   **Видимость**: Присутствует `CheckBox` для переключения видимости вводимых символов пароля.

## Зависимости

*   **`Helpers.Crypts.Password2Hash`**: Критически важно найти и корректно портировать этот метод хэширования, иначе проверка пароля работать не будет.

## План портирования на Android

Стандартным способом реализации такого диалога в Android является `AlertDialog` с кастомной разметкой.

1.  **Портировать хэширование**: Найти и портировать на Java метод `Helpers.Crypts.Password2Hash`.

2.  **Создать XML-разметку** (`dialog_ask_password.xml`):
    *   Разметка должна содержать компонент `com.google.android.material.textfield.TextInputLayout` с вложенным `com.google.android.material.textfield.TextInputEditText`.
    *   У `TextInputLayout` следует установить `app:passwordToggleEnabled="true"`, что автоматически добавит иконку для переключения видимости пароля. Это заменяет ручную реализацию с `CheckBox`.

3.  **Создать утилитный метод для показа диалога**:
    ```java
    public interface PasswordCallback {
        void onPasswordEntered(String password);
    }

    public static void showAskPasswordDialog(Context context, String correctHash, PasswordCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Введите пароль");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_ask_password, null);
        final TextInputEditText passwordInput = view.findViewById(R.id.password_input);
        builder.setView(view);

        builder.setPositiveButton("ОК", (dialog, which) -> {
            callback.onPasswordEntered(passwordInput.getText().toString());
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Кнопка "ОК" изначально неактивна
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        // Добавляем слушатель для проверки пароля в реальном времени
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                // Вызываем портированный метод хэширования
                String currentHash = Crypts.password2Hash(s.toString());
                if (currentHash.equals(correctHash)) {
                    positiveButton.setEnabled(true);
                } else {
                    positiveButton.setEnabled(false);
                }
            }
            // ... другие методы TextWatcher ...
        });
    }
    ```

4.  **Использование**: В коде, где нужно запросить пароль, вызывается `showAskPasswordDialog`, и в `callback` передается лямбда-функция для обработки введенного правильного пароля.
