# План портирования T-структур

Этот документ описывает группу простых структур (`struct`), названия которых начинаются с `T` (`TAutoAdv`, `TNavigator`, `TPers`, `TSound`, `TSplitter`, `TWindow`).

## Функциональность в C#

Эти структуры не содержат логики. Они используются исключительно как data-контейнеры для логической группировки полей внутри основного класса `UserConfig`. Например, все настройки звука (`Enabled`, `DoPlayAlarm` и т.д.) объединены в одну структуру `TSound`, и в `UserConfig` есть одно поле `public TSound Sound;`.

## Решение для портирования на Android

**Портировать эти структуры как отдельные классы не нужно.**

В Android-архитектуре, основанной на `SharedPreferences`, нет необходимости в такой искусственной группировке. Каждая настройка будет храниться как отдельная пара "ключ-значение".

## План реализации (вместо портирования)

Поля из этих структур будут "сплющены" и станут обычными методами в классе `UserConfig.java`.

**Пример для `TSound`:**

Вместо создания класса `TSound.java`, в `UserConfig.java` будут добавлены следующие методы:

```java
// Вместо поля TSound.Enabled
public boolean isSoundGloballyEnabled() {
    return prefs.getBoolean("sound_globally_enabled", true);
}

public void setSoundGloballyEnabled(boolean enabled) {
    prefs.edit().putBoolean("sound_globally_enabled", enabled).apply();
}

// Вместо поля TSound.DoPlayAlarm
public boolean isAlarmSoundEnabled() {
    return prefs.getBoolean("sound_alarm_enabled", true);
}

public void setAlarmSoundEnabled(boolean enabled) {
    prefs.edit().putBoolean("sound_alarm_enabled", enabled).apply();
}

// ... и так далее для каждого звука ...
```

**Нерелевантные настройки:**

*   **`TSplitter`, `TWindow`**: Эти структуры хранят состояние UI (размеры и положение окон и панелей). Так как UI в Android будет полностью другим, эти настройки **не портируются**.
*   **Остальные**: Настройки из `TAutoAdv`, `TNavigator`, `TPers`, `TSound` переносятся в `UserConfig.java` как описано выше.
