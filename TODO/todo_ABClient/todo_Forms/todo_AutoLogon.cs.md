# План портирования AutoLogon.cs

Файл `AutoLogon.cs` реализует диалоговое окно с обратным отсчетом перед автоматическим входом в игру.

## Функциональность в C#

*   **Назначение**: Дать пользователю несколько секунд, чтобы отменить автоматический вход в игру. Если пользователь ничего не делает, вход происходит автоматически.
*   **UI**: Окно отображает имя пользователя (`labelUsername`), для которого выполняется вход, и кнопку подтверждения (`buttonOk`), на которой отображается обратный отсчет.
*   **Логика**: 
    *   При создании формы запускается `System.Windows.Forms.Timer` (`timerCountDown`).
    *   Каждую секунду таймер уменьшает счетчик `CountDown`.
    *   Текст на кнопке `buttonOk` обновляется, показывая оставшееся время (например, "Войти (9)").
    *   Когда счетчик доходит до нуля, форма автоматически закрывается с результатом `DialogResult.OK`, что инициирует вход в игру.
    *   Пользователь может прервать процесс, закрыв окно или нажав кнопку "Отмена".

## Проверка на существующую реализацию в Android

- **Результат:** Функциональность не реализована. В проекте используется `AlertDialog`, но нет логики для обратного отсчета и автоматического подтверждения.

## Решение для портирования на Android

Эта функциональность легко реализуется с помощью стандартного `AlertDialog` и системного класса `android.os.CountDownTimer`.

## План реализации

- [ ] **Создать утилитный метод для показа диалога**:
    - [ ] Создать статический метод, например, в новом классе `DialogHelper.java`:
        ```java
        public interface AutoLogonCallback {
            void onLoginConfirmed();
            void onLoginCancelled();
        }

        public static void showAutoLogonDialog(Context context, String userName, AutoLogonCallback callback) { ... }
        ```

- [ ] **Реализация `showAutoLogonDialog`**:
    - [ ] Создать `AlertDialog.Builder` с заголовком "Автоматический вход" и сообщением, включающим `userName`.
    - [ ] Установить кнопки:
        ```java
        builder.setNegativeButton("Отмена", (dialog, which) -> {
            // Таймер будет остановлен в обработчике onCancel
        });
        builder.setPositiveButton("Войти", (dialog, which) -> {
            // Этот обработчик сработает при нажатии или по окончании таймера
            callback.onLoginConfirmed();
        });
        ```
    - [ ] Создать и показать диалог: `AlertDialog dialog = builder.create(); dialog.show();`

- [ ] **Настройка `CountDownTimer`**:
    - [ ] После `dialog.show()`, получить ссылку на кнопку подтверждения: `final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);`
    - [ ] Создать и запустить `CountDownTimer`:
        ```java
        final CountDownTimer timer = new CountDownTimer(10000, 1000) { // 10 секунд, значение взять из AppConsts
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

- [ ] **Обработка отмены**:
    - [ ] Добавить слушатель отмены/закрытия диалога, в котором будет останавливаться таймер, чтобы избежать утечек и лишних срабатываний.
        ```java
        dialog.setOnCancelListener(dialogInterface -> {
            timer.cancel();
            callback.onLoginCancelled();
        });
        ```
        Обработчик `setNegativeButton` также вызовет `OnCancelListener`.
