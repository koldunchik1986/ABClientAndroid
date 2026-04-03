# Как собрать APK через Android Studio (вместо Gradle CLI)

## Почему Android Studio?

Gradle 8.x на Windows имеет критичную JNI ошибку при запуске из CLI. Android Studio IDE обходит эту проблему потому что использует встроенный gradle daemon с правильными JVM параметрами для Windows.

## Пошаговая инструкция

### Шаг 1: Откройте Android Studio

```
1. Запустите Android Studio
2. Welcome screen → Open Project (или File → Open)
3. Выберите папку: c:\Users\User\AbclientAndroid
4. Нажмите OK
```

### Шаг 2: Дождитесь синхронизации

```
IDE автоматически запустит:
- Gradle Sync
- Indexing
- Dependency resolution

Дождитесь завершения (может занять 2-3 минуты)
```

### Шаг 3: Соберите APK

```
1. Нажмите: Build → Build Bundle(s) / APK(s) → Build APK(s)
2. Выберите app (если есть выбор)
3. Выберите Debug variant
4. Нажмите Build
```

### Шаг 4: Проверьте результат

```
✅ УСПЕШНО если видите:
   - "Build Successful" внизу экрана
   - Notification с ссылкой на APK

APK будет в:
app/build/outputs/apk/debug/app-debug.apk
```

## Если Android Studio не установлена

Скачайте с: https://developer.android.com/studio

Минимальные требования:
- Windows 7+ (рекомендуется Windows 10/11)
- RAM: 8GB+
- Disk: 5GB+

## Алтернатива: Использовать встроенный gradle wrapper через IDE

Даже без Android Studio откройте проект в Android Studio Standalone (нет IDE, только tools):
```
1. gradle.bat --version  (проверьте что gradle доступен)
2. Используйте: gradlew.bat assembleDebug (вместо gradle.bat)
3. Добавьте параметры:
   gradlew.bat assembleDebug -Dorg.gradle.jvmargs="-Xmx4096m" --no-daemon
```
