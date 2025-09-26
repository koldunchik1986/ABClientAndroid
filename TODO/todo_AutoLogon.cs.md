# План портирования AutoLogon.cs

Файл `AutoLogon.cs` реализует диалоговое окно с обратным отсчетом перед автоматическим входом в игру.

## Функциональность в C#

*   **Назначение**: Дать пользователю несколько секунд, чтобы отменить автоматический вход в игру. Если пользователь ничего не делает, вход происходит автоматически.
*   **UI**: Окно отображает имя пользователя, для которого выполняется вход, и две кнопки: "Отмена" и кнопка подтверждения (например, "Войти (10)").
*   **Обратный отсчет**: При открытии окна запускается `System.Windows.Forms.Timer`. Каждую секунду он уменьшает счетчик и обновляет текст на кнопке подтверждения, показывая оставшееся время.
*   **Авто-подтверждение**: Когда счетчик доходит до нуля, форма автоматически закрывается с результатом `DialogResult.OK`, что инициирует вход в игру.
*   **Отмена**: Пользователь может нажать кнопку "Отмена", чтобы прервать таймер и отменить автологин.

## Решение для портирования на Android

Эта функциональность легко реализуется с помощью стандартного `AlertDialog` и системного класса `android.os.CountDownTimer`.

## План реализации

1.  **Создать утилитный метод** для показа диалога, который будет принимать `Context`, имя пользователя и `callback` для обработки результата.
    ```java
    public interface AutoLogonCallback {
        void onLoginConfirmed();
        void onLoginCancelled();
    }

    public static void showAutoLogonDialog(Context context, String userName, AutoLogonCallback callback) { ... }
    ```

2.  **Реализация `showAutoLogonDialog`**:
    *   Создать `AlertDialog.Builder` с заголовком "Автоматический вход" и сообщением, включающим `userName`.
    *   Установить кнопки:
        ```java
        builder.setNegativeButton("Отмена", (dialog, which) -> {
            // Таймер будет остановлен в обработчике onCancel
        });
        builder.setPositiveButton("Войти", (dialog, which) -> {
            // Этот обработчик сработает при нажатии или по окончании таймера
            callback.onLoginConfirmed();
        });
        ```
    *   Создать и показать диалог: `AlertDialog dialog = builder.create(); dialog.show();`

3.  **Настройка `CountDownTimer`**:
    *   После `dialog.show()`, получить ссылку на кнопку подтверждения: `final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);`
    *   Создать и запустить `CountDownTimer`:
        ```java
        final CountDownTimer timer = new CountDownTimer(10000, 1000) { // 10 секунд
            @Override
            public void onTick(long millisUntilFinished) {
                positiveButton.setText("Войти (" + (millisUntilFinished / 1000) + ")");
            }

            @Override
            public void onFinish() {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                    callback.onLoginConfirmed();
                }
            }
        }.start();
        ```

4.  **Обработка отмены**:
    *   Добавить слушатель отмены/закрытия диалога, в котором будет останавливаться таймер, чтобы избежать утечек и лишних срабатываний.
        ```java
        dialog.setOnCancelListener(dialogInterface -> {
            timer.cancel();
            callback.onLoginCancelled();
        });
        ```
        Обработчик `setNegativeButton` также вызовет `OnCancelListener`.
