# Базовая инструкция по развертыванию авторизации

1. Убедитесь, что в проекте есть все файлы:
   - AuthManager.kt (с функциями и переменными из инструкций)
   - ProfileManager.kt (или аналогичная структура для профилей)
   - Все зависимости OkHttp, coroutines, и т.д. прописаны в build.gradle

2. Проверьте, что в AndroidManifest.xml есть разрешение на интернет:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```

3. Для логирования убедитесь, что log.txt создаётся во внутренней памяти приложения (context.filesDir).

4. Для авторизации вызывайте:
   ```kotlin
   AuthManager.authorizeAsync(login, password, callback, context)
   ```
   - login и password — из профиля
   - callback — функция для обработки результата
   - context — обычно это Activity или Application

5. Если что-то не работает:
   - Проверьте логи (log.txt)
   - Проверьте правильность cookies и заголовков
   - Проверьте кодировку windows-1251 для логина и пароля
   - Проверьте, что все запросы идут на http://neverlands.ru/

6. Если всё равно не работает:
   - Сравните логи с браузерным HAR (F12 → Network)
   - Проверьте, что не блокируется интернет или нет проблем с SSL
   - Проверьте, что не включён VPN/прокси, если не нужен

7. Если нужно восстановить код:
   - Используйте файлы инструкций (auth_func_*.md, auth_var_*.md, auth_struct_*.md)
   - Следуйте шагам из auth_instruct.md
