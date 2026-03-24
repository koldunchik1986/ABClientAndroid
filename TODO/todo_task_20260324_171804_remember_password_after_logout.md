# Задача: пароль не подставляется после выхода и повторного входа

Дата: 2026-03-24  
Статус: `[x]` Выполнено

## План

- [x] Проверить `Logs/logcat_runtime_20260324_14.txt` по сценарию: вход -> выход -> вход.
- [x] Сверить логику `LoginActivity` (загрузка профиля, remember-checkbox, сохранение `UserPassword`).
- [x] Усилить сохранение `remember/password`, чтобы состояние не терялось между экранами.
- [x] Добавить точечные диагностические логи `LOGIN_UI` для верификации выбора профиля и длины сохраненного пароля.
- [x] Проверить компиляцию `:app:compileDebugJavaWithJavac`.

## Что изменено

- В `LoginActivity` добавлен метод `persistRememberPasswordSnapshot(...)`, который:
  - фиксирует `UserAutoLogon` + `UserPassword` синхронно в профиль;
  - сохраняет `last_profile_id`;
  - пишет диагностический лог `LOGIN_UI`.
- `persistRememberPasswordSnapshot(...)` вызывается:
  - при клике входа (`stage=login_click`);
  - после успешного входа (`stage=login_success`).
- Добавлены диагностические логи:
  - после `loadProfiles()` (какой профиль выбран и что в нем по remember/password);
  - в `applySelectedProfile(...)`;
  - на `loginClick`.

## Ожидаемая проверка в следующем логе

- На экране логина после выхода должны появиться строки:
  - `LOGIN_UI: profilesLoaded=...`
  - `LOGIN_UI: applySelectedProfile ... savedPasswordLen=...`
- При первом входе с галочкой:
  - `LOGIN_UI: persistRemember stage=login_click ... autoLogon=true ...`
  - `LOGIN_UI: persistRemember stage=login_success ... autoLogon=true ...`
- При повторном открытии логина `savedPasswordLen` для того же `profileId` должен быть `> 0`.
