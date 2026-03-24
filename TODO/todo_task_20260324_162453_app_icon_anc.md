# Задача: заменить значок приложения на ANC.png

Дата: 2026-03-24  
Статус: `[x]` Выполнено

## План

- [x] Проверить текущий источник иконки приложения и логотипа на экране логина.
- [x] Добавить `ANC.png` в Android-ресурсы.
- [x] Переключить `drawable/ic_launcher.xml` на новый PNG.
- [x] Проверить сборку проекта.

## Результат

- Добавлен ресурс `app/src/main/res/drawable/anc.png`.
- `app/src/main/res/drawable/ic_launcher.xml` теперь указывает на `@drawable/anc` через `bitmap`.
- Иконка приложения (Manifest: `android:icon`/`android:roundIcon`) и логотип на стартовом экране (`activity_login.xml`, `@drawable/ic_launcher`) показывают новый ANC-значок.
- `:app:compileDebugJavaWithJavac` — успешно.
