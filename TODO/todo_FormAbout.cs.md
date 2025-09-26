# План портирования FormAbout.cs

Файл `FormAbout.cs` реализует стандартное окно "О программе".

## Функциональность в C#

*   **Назначение**: Показать пользователю базовую информацию о приложении.
*   **UI**: Форма содержит статический текст (имя программы, автор), логотип и динамически обновляемую метку `labelExpired`.
*   **Логика**: При загрузке формы она получает из `AppVars` дату истечения лицензии и отображает ее на метке `labelExpired`.

## План портирования на Android

Это простой информационный экран, который легко реализуется с помощью `Activity` или `DialogFragment`.

1.  **Создать `AboutActivity.java`** (или `AboutFragment.java`).

2.  **Создать XML-разметку (`activity_about.xml`)**:
    *   Использовать `LinearLayout` или `ConstraintLayout`.
    *   Добавить `ImageView` для логотипа приложения.
    *   Добавить несколько `TextView` для отображения:
        *   Названия приложения.
        *   Версии приложения. Ее следует получать программно: `BuildConfig.VERSION_NAME`.
        *   Информации об авторе.
        *   Информации о лицензии (если эта логика будет портирована).
        *   Ссылок на веб-сайт или форум. Для кликабельных ссылок использовать `android:autoLink="web"`.

3.  **Реализовать логику в `AboutActivity.java`**:
    ```java
    public class AboutActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_about);

            TextView versionText = findViewById(R.id.version_text);
            String versionName = BuildConfig.VERSION_NAME;
            int versionCode = BuildConfig.VERSION_CODE;
            versionText.setText("Версия: " + versionName + " (" + versionCode + ")");

            TextView licenseText = findViewById(R.id.license_text);
            // TODO: Получить дату истечения лицензии, если она будет реализована
            // Date licenseExpiredDate = ...;
            // SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Russian.CULTURE);
            // licenseText.setText("Лицензия до: " + sdf.format(licenseExpiredDate));
        }
    }
    ```

4.  **Добавить вызов**: В главном меню приложения (например, в `MaterialToolbar`) добавить пункт "О программе", который будет запускать `AboutActivity` через `Intent`.
