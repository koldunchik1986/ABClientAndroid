# План портирования NewPassword.cs

Файл `NewPassword.cs` реализует диалоговое окно для безопасного создания нового пароля.

## Функциональность в C#

*   **Назначение**: Позволить пользователю ввести новый пароль и подтвердить его, чтобы избежать опечаток. Кнопка "ОК" становится доступной только при полном совпадении паролей.
*   **UI**:
    *   `textPassword1`: Первое поле для ввода пароля.
    *   `textPassword2`: Второе поле для подтверждения пароля.
    *   `checkVisiblePassword`: Чекбокс для переключения видимости обоих полей.
    *   `buttonOk`: Кнопка подтверждения, изначально неактивна.
*   **Логика**: 
    *   При любом изменении текста в любом из полей (`TextPassword_TextChanged`) происходит проверка:
        *   `textPassword1.Text.Equals(textPassword2.Text, StringComparison.Ordinal)`
    *   Кнопка `buttonOk` становится активной (`Enabled = true`) только если строки в обоих полях полностью совпадают.
*   **Данные**: Введенный пароль возвращается через свойство `Password`.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В проекте нет диалога или `Activity` с двумя полями для ввода и подтверждения пароля.

## Решение для портирования на Android

Эту функциональность можно реализовать с помощью `AlertDialog` с кастомным layout'ом, содержащим два поля для ввода пароля.

## План реализации

- [ ] **Создать layout-файл `dialog_new_password.xml`**:
    - [ ] Добавить `TextInputLayout` с `TextInputEditText` для первого ввода пароля (`password_edit_text_1`).
    - [ ] Добавить второй `TextInputLayout` с `TextInputEditText` для подтверждения (`password_edit_text_2`).
    - [ ] Добавить `CheckBox` для переключения видимости паролей.

- [ ] **Создать утилитный метод для показа диалога**:
    - [ ] В классе `DialogHelper.java` (или создать, если его нет) добавить статический метод:
        ```java
        public interface NewPasswordCallback {
            void onPasswordCreated(String password);
        }

        public static void showNewPasswordDialog(Context context, NewPasswordCallback callback) { ... }
        ```

- [ ] **Реализация `showNewPasswordDialog`**:
    - [ ] Создать `AlertDialog.Builder`.
    - [ ] Установить кастомный layout: `builder.setView(R.layout.dialog_new_password)`.
    - [ ] Установить кнопки "ОК" и "Отмена".
    - [ ] Показать диалог: `AlertDialog dialog = builder.create(); dialog.show();`
    - [ ] Сразу после `dialog.show()`, деактивировать кнопку "ОК": `dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);`

- [ ] **Реализация логики проверки паролей**:
    - [ ] Найти оба `TextInputEditText` в кастомном layout'е.
    - [ ] Добавить `TextWatcher` к обоим полям.
    - [ ] В методе `onTextChanged` обоих `TextWatcher`'ов вызывать общую функцию проверки:
        ```java
        private void validatePasswords(AlertDialog dialog, EditText pass1, EditText pass2) {
            String p1 = pass1.getText().toString();
            String p2 = pass2.getText().toString();
            boolean areEqual = !p1.isEmpty() && p1.equals(p2);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(areEqual);
        }
        ```
    - [ ] В обработчике нажатия кнопки "ОК" вызывать `callback.onPasswordCreated(password);`.
