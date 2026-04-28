package ru.neverlands.anclient.model;

import java.util.Locale;

/**
 * Модель пользовательского таймера (порт `ANClient/AppTimer.cs`).
 *
 * Зависимости:
 * - используется `AppTimerManager` для хранения/сортировки/исполнения таймеров;
 * - используется UI-слоем (`QuickButtonsPanel`) для отображения списка активных таймеров;
 * - строковое представление повторяет поведение C# `AppTimer.ToString()`.
 */
public class AppTimer {
    public long triggerTime = 0L;
    public String description = "";
    public String potion = "";
    public int drinkCount = 0;
    public boolean isRecur = false;
    public int everyMinutes = 0;
    public String destination = "";
    public String complect = "";
    public int id = 0;
    public boolean isHerb = false;
    public String enableAutoFunction = "";  // Включить авто-функцию (напр. "Авто-Бой")
    public String disableAutoFunction = ""; // Выключить авто-функцию

    public AppTimer() {
    }

    public AppTimer copy() {
        AppTimer result = new AppTimer();
        result.triggerTime = triggerTime;
        result.description = description;
        result.potion = potion;
        result.drinkCount = drinkCount;
        result.isRecur = isRecur;
        result.everyMinutes = everyMinutes;
        result.destination = destination;
        result.complect = complect;
        result.id = id;
        result.isHerb = isHerb;
        result.enableAutoFunction = enableAutoFunction;
        result.disableAutoFunction = disableAutoFunction;
        return result;
    }

    /**
     * Формат строки 1:1 по C#-логике:
     * `id[*]) Еще mm:ss - description [count]`.
     *
     * Для `isHerb` показываем фактическое время срабатывания: AutoCut сам ставит
     * запас `growth + 5 минут`, а маршрут должен идти только после due-time.
     */
    public String toDisplayString(long nowMs) {
        StringBuilder builder = new StringBuilder();
        builder.append(id);
        if (isRecur) {
            builder.append('*');
        }
        builder.append(") Еще ");

        if (triggerTime < nowMs) {
            builder.append("0:00");
        } else {
            long remainMs = triggerTime - nowMs;
            builder.append(formatRemain(remainMs));
        }

        builder.append(" - ");
        builder.append(description == null ? "" : description);
        if (drinkCount > 1) {
            builder.append(" [").append(drinkCount).append(']');
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return toDisplayString(System.currentTimeMillis());
    }

    private static String formatRemain(long remainMs) {
        long totalSeconds = Math.max(0L, remainMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
        return String.format(Locale.US, "0:%02d", seconds);
    }
}

