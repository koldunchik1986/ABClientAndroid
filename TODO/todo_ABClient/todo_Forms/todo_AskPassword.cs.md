# План портирования AskPassword.cs

Файл `AskPassword.cs` реализует простое диалоговое окно для запроса пароля и его проверки по хешу.

## Функциональность в C#

*   **Назначение**: Запросить у пользователя пароль и проверить его соответствие заранее известному хешу. Окно не закрывается, а кнопка "ОК" неактивна, пока не будет введен верный пароль.
*   **UI**: 
    *   `textPassword`: Поле для ввода пароля.
    *   `checkVisiblePassword`: Чекбокс для переключения видимости пароля.
    *   `buttonOk`: Кнопка подтверждения, которая изначально неактивна.
*   **Логика**: 
    *   Форма принимает в конструкторе хеш пароля (`hashPassword`).
    *   При каждом изменении текста в `textPassword` (`TextPassword_TextChanged` event) происходит следующее:
        1.  Вычисляется хеш введенного текста с помощью `Helpers.Crypts.Password2Hash`.
        2.  Полученный хеш сравнивается с `Hash`, переданным в конструкторе.
        3.  Кнопка `buttonOk` становится активной (`Enabled = true`) только в случае совпадения хешей.
*   **Данные**: Введенный пользователем пароль (в открытом виде) доступен через свойство `Password`.

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В проекте есть работа с паролями в `LoginActivity` и `ProfileActivity`, но нет диалога, который бы запрашивал пароль и проверял его по хешу в реальном времени для разблокировки действия.

## Решение для портирования на Android

Эту функциональность можно реализовать с помощью `AlertDialog` с кастомным layout'ом. Для проверки хеша в реальном времени будет использоваться `TextWatcher`.

## План реализации

- [ ] **Создать layout-файл `dialog_ask_password.xml`**:
    - [ ] Добавить `TextInputLayout` с `TextInputEditText` для ввода пароля.
    - [ ] Добавить `CheckBox` для переключения видимости пароля.

- [ ] **Создать утилитный метод для показа диалога**:
    - [ ] Создать статический метод, например, в новом классе `DialogHelper.java`:
        ```java
        public interface AskPasswordCallback {
            void onPasswordEntered(String password);
        }

        public static void showAskPasswordDialog(Context context, String hash, AskPasswordCallback callback) { ... }
        ```

- [ ] **Реализация `showAskPasswordDialog`**:
    - [ ] Создать `AlertDialog.Builder`.
    - [ ] Установить кастомный layout с помощью `builder.setView(R.layout.dialog_ask_password)`.
    - [ ] Установить кнопки "ОК" и "Отмена".
    - [ ] Показать диалог: `AlertDialog dialog = builder.create(); dialog.show();`
    - [ ] Сразу после `dialog.show()`, деактивировать кнопку "ОК": `dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);`

- [ ] **Реализация логики проверки пароля**:
    - [ ] Найти `TextInputEditText` в кастомном layout'е.
    - [ ] Добавить `TextWatcher` к `TextInputEditText`.
    - [ ] В методе `onTextChanged`:
        1.  Получить введенный текст.
        2.  Вызвать портированную функцию `Crypts.passwordToHash()` (которую нужно будет реализовать в `utils.Crypts.java`).
        3.  Сравнить хеши.
        4.  Активировать или деактивировать кнопку "ОК": `dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(hashesMatch);`
    - [ ] В обработчике нажатия кнопки "ОК" вызвать `callback.onPasswordEntered(enteredPassword);`.

- [ ] **Портирование `Crypts.Password2Hash`**:
    - [ ] В `utils` создать класс `Crypts.java`.
    - [ ] Перенести логику вычисления хеша из `ABClient\Helpers\Crypts.cs` в статический метод `passwordToHash(String password)`.
