# План портирования FormCode.cs

Файл `FormCode.cs` реализует диалоговое окно для ввода капчи (кода с картинки).

## Функциональность в C#

*   **Назначение**: Показать пользователю изображение с кодом и предоставить поле для его ввода. Это требуется для защиты от ботов на сервере.
*   **UI**: Форма содержит `PictureBox` для отображения картинки, `TextBox` для ввода кода, кнопку "Обновить" и кнопку "Ввод".
*   **Логика**:
    *   При открытии, форма берет массив байт изображения капчи из глобальной переменной `AppVars.CodePng` и отображает его.
    *   Кнопка "Ввод" активна только если в поле ввода введено число в определенном диапазоне.
    *   Кнопка "Обновить" инициирует перезапрос картинки с кодом.

## План портирования на Android

Этот диалог должен быть реализован с помощью `AlertDialog` с кастомной разметкой.

1.  **XML-разметка (`dialog_captcha.xml`)**:
    *   Создать разметку, содержащую `ImageView` для картинки и `com.google.android.material.textfield.TextInputEditText` для ввода.
    *   Для поля ввода установить `android:inputType="number"`.

2.  **Создать утилитный метод для показа диалога**:
    ```java
    public interface CaptchaCallback {
        void onCodeEntered(String code);
        void onRefresh();
    }

    public static void showCaptchaDialog(Context context, byte[] captchaBytes, CaptchaCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Введите код с картинки");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_captcha, null);
        final ImageView captchaImage = view.findViewById(R.id.captcha_image);
        final TextInputEditText codeInput = view.findViewById(R.id.code_input);
        builder.setView(view);

        // Отображаем капчу
        if (captchaBytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.length);
            captchaImage.setImageBitmap(bitmap);
        }

        builder.setPositiveButton("Ввод", (dialog, which) -> {
            callback.onCodeEntered(codeInput.getText().toString());
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        builder.setNeutralButton("Обновить", (dialog, which) -> {
            // Этот обработчик нужно будет переопределить, чтобы диалог не закрывался
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Переопределяем клик на "Обновить", чтобы диалог не закрывался
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            callback.onRefresh();
            // dialog.dismiss(); // не закрываем диалог
        });

        // Валидация ввода
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);
        codeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                // Проверяем, что введено 5 цифр
                positiveButton.setEnabled(s.toString().length() == 5);
            }
            // ...
        });
    }
    ```

3.  **Интеграция**: Код, который в C# сохранял капчу в `AppVars.CodePng` и ждал результата от `FormCode`, в Android должен будет вызвать `showCaptchaDialog`. В `callback.onRefresh()` он должен будет инициировать перезапрос капчи с сервера и затем снова вызвать `showCaptchaDialog` с новым изображением.
