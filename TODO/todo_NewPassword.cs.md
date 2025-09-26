# План портирования NewPassword.cs

Файл `NewPassword.cs` реализует простое диалоговое окно для создания и подтверждения нового пароля.

## Функциональность в C#

*   **Назначение**: Позволить пользователю безопасно ввести новый пароль, убедившись, что он не сделал опечатку.
*   **UI**: Форма содержит два поля для ввода пароля (`textPassword1`, `textPassword2`) и `CheckBox` для управления видимостью вводимых символов.
*   **Проверка**: При каждом изменении текста в любом из полей, их содержимое сравнивается. Кнопка "ОК" становится активной только тогда, когда строки в обоих полях полностью совпадают.
*   **Результат**: После нажатия "ОК" вызывающий код получает введенный пароль для дальнейшего использования (например, для шифрования профиля).

## План портирования на Android

Этот диалог легко воссоздается с помощью `AlertDialog` с кастомной разметкой.

1.  **XML-разметка (`dialog_new_password.xml`)**:
    *   Создать разметку, содержащую два компонента `com.google.android.material.textfield.TextInputLayout`.
    *   В каждый `TextInputLayout` вложить `com.google.android.material.textfield.TextInputEditText`.
    *   Для `TextInputLayout` установить `app:passwordToggleEnabled="true"`, чтобы автоматически получить иконку для переключения видимости пароля.
    *   Одному полю дать `hint` "Новый пароль", второму — "Подтвердите пароль".

2.  **Создать утилитный метод для показа диалога**:
    ```java
    public interface NewPasswordCallback {
        void onNewPassword(String password);
    }

    public static void showNewPasswordDialog(Context context, NewPasswordCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Создание пароля");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_new_password, null);
        final TextInputEditText pass1 = view.findViewById(R.id.password_input_1);
        final TextInputEditText pass2 = view.findViewById(R.id.password_input_2);
        builder.setView(view);

        builder.setPositiveButton("ОК", (dialog, which) -> {
            callback.onNewPassword(pass1.getText().toString());
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Кнопка "ОК" изначально неактивна
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        // Слушатель для проверки совпадения паролей
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String p1 = pass1.getText().toString();
                String p2 = pass2.getText().toString();
                // Кнопка активна только если пароли не пустые и совпадают
                positiveButton.setEnabled(!p1.isEmpty() && p1.equals(p2));
            }
            // ... другие методы TextWatcher ...
        };

        pass1.addTextChangedListener(textWatcher);
        pass2.addTextChangedListener(textWatcher);
    }
    ```
