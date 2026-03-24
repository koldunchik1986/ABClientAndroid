# Задача: Выход из сессии через боковое меню

## Контекст
- Пользователь записал реальный серверный трафик выхода в `exit.har`.
- Нужно реализовать в Android-клиенте кнопку `Выход` в drawer-меню:
  - отправка logout-запроса на сервер (`GET /exit.php`);
  - закрытие текущей сессии;
  - переход на начальный экран выбора профиля/входа.

## План реализации
- [x] Проверить `exit.har` и подтвердить серверный сценарий logout.
- [x] Добавить пункт `nav_logout` в `activity_main_drawer.xml`.
- [x] Добавить иконку двери для пункта выхода.
- [x] Добавить строковый ресурс `menu_logout`.
- [x] Реализовать обработчик `nav_logout` в `MainActivity`.
- [x] Реализовать best-effort запрос `GET http://neverlands.ru/exit.php` с текущими cookie.
- [x] Очистить локальную сессию (WebView cookies + OkHttp cookie jar).
- [x] Выполнить переход на `LoginActivity` с очисткой back stack.
- [x] Проверить компиляцией `:app:compileDebugJavaWithJavac`.

## Измененные файлы
- `app/src/main/java/ru/neverlands/abclient/MainActivity.java`
- `app/src/main/res/menu/activity_main_drawer.xml`
- `app/src/main/res/drawable/ic_door_exit.xml`
- `app/src/main/res/values/strings_logout.xml`
