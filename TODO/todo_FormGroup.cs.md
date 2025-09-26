# План портирования FormGroup.cs

Файл `FormGroup.cs` реализует простое диалоговое окно для ввода или редактирования названия группы (вероятно, группы контактов).

## Функциональность в C#

*   **Назначение**: Запросить у пользователя текстовую строку — название группы.
*   **UI**: Форма содержит одно текстовое поле (`textBox`) и кнопку "ОК".
*   **Логика**: Кнопка "ОК" активна только в том случае, если текстовое поле не является пустым (после удаления пробелов по краям).
*   **Результат**: Вызывающий код получает введенное название из свойства `GroupName`.

## План портирования на Android

Этот диалог должен быть реализован с помощью `AlertDialog` с кастомной разметкой.

1.  **XML-разметка (`dialog_enter_text.xml`)**: Можно создать универсальную разметку для ввода текста, которая будет использоваться в разных частях приложения.
    *   Разметка должна содержать `com.google.android.material.textfield.TextInputLayout` с вложенным `com.google.android.material.textfield.TextInputEditText`.

2.  **Создать утилитный метод для показа диалога**:
    ```java
    public interface EnterTextCallback {
        void onTextEntered(String text);
    }

    public static void showEnterTextDialog(Context context, String title, String hint, String defaultValue, EnterTextCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_enter_text, null);
        final TextInputLayout textInputLayout = view.findViewById(R.id.text_input_layout);
        final TextInputEditText editText = view.findViewById(R.id.edit_text);
        textInputLayout.setHint(hint);
        editText.setText(defaultValue);
        builder.setView(view);

        builder.setPositiveButton("ОК", (dialog, which) -> {
            // Обработчик будет переопределен
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Кнопка "ОК" изначально может быть неактивна, если defaultValue пуст
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(!defaultValue.trim().isEmpty());

        // Переопределяем обработчик, чтобы диалог не закрывался при ошибке
        positiveButton.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) {
                callback.onTextEntered(text);
                dialog.dismiss();
            } else {
                textInputLayout.setError("Название не может быть пустым");
            }
        });

        // Слушатель для активации кнопки и сброса ошибки
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textInputLayout.setError(null); // Убираем ошибку
                positiveButton.setEnabled(!s.toString().trim().isEmpty());
            }
            // ...
        });
    }
    ```
3.  **Использование**: В коде, где нужно запросить название группы, будет вызываться `showEnterTextDialog` с соответствующими параметрами и callback-ом.
