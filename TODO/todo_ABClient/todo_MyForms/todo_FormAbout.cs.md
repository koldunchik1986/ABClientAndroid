
### 1. План портирования FormAbout.cs

Файл `FormAbout.cs` реализует простое окно "О программе".

### 2. Функциональность в C#

- **UI:** Окно, содержащее информацию о программе.
- **Логика:**
    - При загрузке формы, в метку `labelExpired` записывается текст с датой окончания лицензии (`AppVars.LicenceExpired`).

### 3. Решение для портирования на Android

Эту функциональность следует реализовать с помощью `Activity` или, что более предпочтительно для такого простого окна, `DialogFragment`.

### 4. План реализации

1.  **Создать XML-разметку:**
    - Создать `layout/dialog_about.xml`.
    - В разметке разместить `TextView` для версии программы, `TextView` для информации о лицензии (с `id`, например, `tv_license_expired`) и кнопку "OK".
2.  **Создать класс `AboutDialogFragment`:**
    - Унаследовать его от `androidx.fragment.app.DialogFragment`.
    - В методе `onCreateDialog` или `onCreateView` "надуть" разметку `dialog_about.xml`.
3.  **Реализовать логику:**
    - В `onViewCreated` найти `TextView` по id (`tv_license_expired`).
    - Получить дату окончания лицензии из соответствующего класса-хранилища в Android (аналога `AppVars`).
    - Отформатировать и установить текст в `TextView`.
4.  **Показ диалога:**
    - В главном меню или настройках добавить обработчик, который будет создавать и показывать этот диалог:
      ```kotlin
      val aboutDialog = AboutDialogFragment()
      aboutDialog.show(supportFragmentManager, "AboutDialogFragment")
      ```

- [ ] Создать XML-разметку `dialog_about.xml`.
- [ ] Создать класс `AboutDialogFragment`.
- [ ] Реализовать установку текста с датой лицензии.
- [ ] Добавить вызов диалога из меню.
