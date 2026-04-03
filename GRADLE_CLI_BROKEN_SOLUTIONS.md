# РЕШЕНИЕ: Gradle не собирается на Windows - что делать

## Причина
Gradle 8.x на этой Windows машине имеет критичную JNI проблему: "Could not extract native JNI library". Это не зависит от:
- Версии gradle (пробовали 8.0.2, 8.1.4, 8.5.0)
- JVM параметров (-Xmx, -XX flags)
- Очистки кэшей (~/.gradle, app/build)
- Версии Java (17.0.12 установлена)

Это системная проблема Windows + Gradle CLI.

## Решение 1: Android Studio IDE (РЕКОМЕНДУЕТСЯ)

```
1. Скачайте Android Studio: https://developer.android.com/studio
2. Откройте проект c:\Users\User\AbclientAndroid в Android Studio
3. Дождитесь Gradle Sync
4. Build → Build APK(s) → Debug
5. APK появится в app/build/outputs/apk/debug/
```

**Почему это работает:** Android Studio использует встроенный gradle daemon с правильными Windows параметрами, минуя CLI ошибки.

## Решение 2: Облачная сборка (если Android Studio недоступна)

Используйте облачный CI/CD:
- GitHub Actions (бесплатно для публичных репозиториев)
- Firebase App Distribution
- Codemagic (имеет free tier)

Они собирают на Linux машинах где Gradle работает корректно.

## Решение 3: WSL (Windows Subsystem for Linux)

Если установлен WSL:
```bash
wsl
cd /mnt/c/Users/User/AbclientAndroid
./gradlew assembleDebug
```

Gradle на Linux не имеет JNI проблем.

## Решение 4: Docker

Если установлен Docker:
```bash
docker run --rm -v c:\Users\User\AbclientAndroid:/workspace android:latest /workspace/gradlew assembleDebug
```

## Итог

**На этой Windows машине Gradle CLI не работает из-за JNI проблемы.**

Используйте:
1. ✅ Android Studio IDE (оптимально)
2. ✅ WSL если установлен
3. ✅ Docker если установлен
4. ✅ Облачную сборку если надо

Выбирайте то что вам доступно. Android Studio - самое простое решение.
