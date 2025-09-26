# План портирования ErrorForm.cs

Файл `ErrorForm.cs` (содержащий класс `FormAutoTrap`) описывает простое диалоговое окно для отображения текстовой информации об ошибке.

## Функциональность в C#

*   **Назначение**: Показать пользователю полный текст исключения или сообщения об ошибке.
*   **Конструктор**: Принимает строку с текстом ошибки.
*   **UI**: Содержит многострочное текстовое поле (`textBox`), в которое помещается текст ошибки, и заголовок окна, в который выводится версия приложения.

## Решение для портирования на Android

Стандартным и наиболее подходящим способом отображения подобной информации в Android является `AlertDialog`.

## План реализации

Вместо создания отдельного `Activity` для такой простой задачи, рекомендуется создать утилитный метод, который можно будет вызывать из любого места в приложении.

1.  **Создать `UiUtils.java`** (если еще не существует) в пакете `ru.neverlands.abclient.utils`.
2.  **Добавить статический метод `showErrorDialog`**:
    ```java
    public static void showErrorDialog(Context context, String title, String errorText) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        
        // Создаем ScrollView, чтобы длинный текст ошибки можно было прокручивать
        final ScrollView scrollView = new ScrollView(context);
        final TextView textView = new TextView(context);
        textView.setText(errorText);
        textView.setPadding(40, 20, 40, 20); // Отступы для читаемости
        textView.setTextIsSelectable(true); // Позволить пользователю выделять и копировать текст
        scrollView.addView(textView);

        builder.setView(scrollView);

        builder.setPositiveButton("ОК", (dialog, which) -> dialog.dismiss());
        
        builder.setNeutralButton("Копировать", (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("error_report", errorText);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Текст ошибки скопирован", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    ```
3.  **Использование**:
    *   В любом месте, где в C#-коде вызывался `new FormAutoTrap(exception.ToString()).Show()`, в Java-коде нужно будет вызвать `UiUtils.showErrorDialog(context, "Ошибка", exception.toString());`.
    *   Необходимо будет обеспечить передачу `Context` в те места, где может понадобиться вызов этого диалога.
