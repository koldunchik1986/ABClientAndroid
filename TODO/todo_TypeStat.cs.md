# План портирования TypeStat.cs и TypeItemDrop.cs

Файлы `TypeStat.cs` и `TypeItemDrop.cs` — это data-классы, предназначенные для хранения и управления статистикой текущей игровой сессии.

## Функциональность в C#

*   **`TypeItemDrop`**: Простая структура для хранения информации о выпавшем предмете (имя и количество).
*   **`TypeStat`**: Основной класс, который агрегирует всю статистику:
    *   Учет трафика (`Traffic`, `SavedTraffic`).
    *   Учет полученного опыта и NV (`XP`, `NV`, `FishNV`).
    *   Список всех выпавших предметов (`List<TypeItemDrop>`).
    *   Временные метки для периодического сброса статистики.

В C#-версии этот объект, вероятно, сериализуется как часть основного `UserConfig`.

## План портирования на Android

Эти классы необходимо портировать для сбора статистики. Для их сохранения удобнее всего использовать сериализацию в JSON.

1.  **Создать POJO-классы**:
    *   Создать `TypeItemDrop.java` с полями `String name` и `int count`.
    *   Создать `TypeStat.java` со всеми соответствующими полями из C#-версии (`long traffic`, `long xp`, `List<TypeItemDrop> itemDrop` и т.д.).

2.  **Создать `StatsManager.java`**:
    *   Создать класс-синглтон, который будет управлять объектом статистики.
    *   Он будет хранить в памяти текущий экземпляр `TypeStat`: `private TypeStat currentStats;`.
    *   Предоставлять геттер `getCurrentStats()` для доступа к статистике из других частей приложения.

3.  **Реализовать сохранение и загрузку**:
    *   В `StatsManager` реализовать два метода, которые будут работать с `SharedPreferences` и библиотекой `Gson`.
    ```java
    import com.google.gson.Gson;

    public class StatsManager {
        private static final String STATS_KEY = "session_stats";
        private TypeStat currentStats;
        private SharedPreferences prefs;
        private Gson gson = new Gson();

        // ... (реализация синглтона) ...

        public void loadStats(Context context) {
            // prefs инициализируется для конкретного профиля
            String statsJson = prefs.getString(STATS_KEY, null);
            if (statsJson != null) {
                currentStats = gson.fromJson(statsJson, TypeStat.class);
            } else {
                currentStats = new TypeStat(); // Создаем новую статистику
            }
        }

        public void saveStats() {
            if (currentStats != null) {
                String statsJson = gson.toJson(currentStats);
                prefs.edit().putString(STATS_KEY, statsJson).apply();
            }
        }

        public void resetStats() {
            currentStats = new TypeStat();
            saveStats();
        }
    }
    ```

4.  **Использование**: Другие части приложения (например, `MainPhpProcessor` при парсинге лога боя) будут вызывать `StatsManager.getInstance().getCurrentStats()` и изменять его поля (`.setXp(...)`, `.addItemDrop(...)`), а при завершении сессии или периодически будет вызываться `saveStats()`.
