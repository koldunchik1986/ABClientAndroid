# План портирования FormEnterInt.cs

Файл `FormEnterInt.cs` реализует универсальное диалоговое окно для ввода целого числа в заданном диапазоне с валидацией.

## Функциональность в C#

*   **Назначение**: Запросить у пользователя целое число, убедившись, что оно соответствует заданным критериям.
*   **Параметры**: В конструктор передается заголовок окна, значение по умолчанию, а также минимальное и максимальное допустимые значения.
*   **Валидация**: При попытке уйти с поля ввода, происходит проверка:
    *   Поле не должно быть пустым.
    *   Введенное значение должно быть целым числом.
    *   Число должно входить в заданный диапазон (min/max).
    *   В случае ошибки, рядом с полем отображается иконка с текстом ошибки, и фокус остается на поле ввода.

## План портирования на Android

Этот диалог должен быть реализован с помощью `AlertDialog` с кастомной разметкой и логикой валидации.

1.  **XML-разметка (`dialog_enter_int.xml`)**:
    *   Создать разметку, содержащую `TextView` для описания (например, "Введите число от 1 до 100") и `com.google.android.material.textfield.TextInputLayout`.
    *   В `TextInputLayout` вложить `com.google.android.material.textfield.TextInputEditText`.
    *   Для поля ввода установить `android:inputType="number"`.
    *   `TextInputLayout` имеет встроенную поддержку отображения ошибок, которую мы будем использовать.

2.  **Создать утилитный метод для показа диалога**:
    ```java
    public interface EnterIntCallback {
        void onIntEntered(int value);
    }

    public static void showEnterIntDialog(Context context, String title, int defaultValue, int min, int max, EnterIntCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_enter_int, null);
        final TextInputLayout textInputLayout = view.findViewById(R.id.text_input_layout);
        final TextInputEditText editText = view.findViewById(R.id.edit_text);
        textInputLayout.setHint("Введите число от " + min + " до " + max);
        editText.setText(String.valueOf(defaultValue));
        builder.setView(view);

        builder.setPositiveButton("ОК", null); // Устанавливаем null, чтобы переопределить позже
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Переопределяем обработчик кнопки "ОК", чтобы диалог не закрывался при ошибке валидации
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = editText.getText().toString();
            try {
                int value = Integer.parseInt(text);
                if (value >= min && value <= max) {
                    // Валидация пройдена
                    callback.onIntEntered(value);
                    dialog.dismiss();
                } else {
                    // Ошибка диапазона
                    textInputLayout.setError("Значение должно быть от " + min + " до " + max);
                }
            } catch (NumberFormatException e) {
                // Ошибка формата числа
                textInputLayout.setError("Введите корректное число");
            }
        });

        // Сбрасываем ошибку при изменении текста
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textInputLayout.setError(null); // Убираем сообщение об ошибке
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }
    ```
