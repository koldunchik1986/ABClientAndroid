package ru.neverlands.abclient.utils;

import ru.neverlands.abclient.utils.AppLog;

/**
 * Единый модуль для управления принудительными действиями авто-функций.
 * 
 * Назначение:
 * - Игнорировать блокировки UI (uiForegroundLikely) когда нужно ПРИНУДИТЕЛЬНО запустить действие
 * - Применяется ко всем авто-функциям: рыбалка, бой, автоход, и т.д.
 * - Централизованное управление флагами и логикой защиты
 * 
 * Сценарии использования:
 * 1. После питья эликсира - нужно ПРИНУДИТЕЛЬНО запустить рыбалку, несмотря на UI флаги
 * 2. После других действий (бой, pinfo, питьё) - нужно восстановить авто-функции
 * 3. Общий механизм блокировки/разблокировки для всех авто-процессов
 */
public final class ForcedActionGuard {
    private static final String TAG = "ForcedActionGuard";

    private ForcedActionGuard() {}

    /**
     * Проверить нужна ли ПРИНУДИТЕЛЬНОЕ действие и игнорировать блокировки UI.
     * 
     * Это метод используется перед любой блокировкой по uiForegroundLikely.
     * Если флаг установлен - действие разрешается, несмотря на то что пользователь смотрит на экран.
     * 
     * @param actionName название действия для логирования (например "autofish", "autofight", "autoturn")
     * @param uiForegroundLikely текущее состояние флага что пользователь смотрит на экран
     * @return true если действие ДОЛЖНО БЫТЬ ВЫПОЛНЕНО (игнорируя uiForegroundLikely), false если заблокировано UI
     * 
     * Пример:
     *   if (ForcedActionGuard.shouldForceAction("autofish", uiForegroundLikely)) {
     *       // Запустить рыбалку даже если UI видимый
     *   }
     */
    public static boolean shouldForceAction(String actionName, boolean uiForegroundLikely) {
        // Если UI видимый И флаг принудительных действий НЕ установлен - действие заблокировано
        if (uiForegroundLikely && !AppVars.ProbeForceNeedAutofish && !AppVars.ProbeForceNeedAutoboi) {
            return false;
        }
        
        // Если установлен флаг для рыбалки - принудительное действие
        if (AppVars.ProbeForceNeedAutofish) {
            AppLog.d(TAG, "FORCED_ACTION: " + actionName + " - forcing past UI foreground block (elixir cooldown)");
            AppVars.ProbeForceNeedAutofish = false;
            return true;
        }
        
        // Если установлен флаг для авто-боя (холодный старт) - принудительное действие
        if (AppVars.ProbeForceNeedAutoboi) {
            AppLog.d(TAG, "FORCED_ACTION: " + actionName + " - forcing past UI foreground block (cold start autoboi)");
            AppVars.ProbeForceNeedAutoboi = false;
            return true;
        }
        
        // Действие разрешено (UI не видимый)
        return true;
    }

    /**
     * Установить флаг для ПРИНУДИТЕЛЬНОГО действия авто-функции.
     * 
     * Используется после операций которые требуют восстановления авто-функций:
     * - Питье эликсира (нужна рыбалка)
     * - Завершение боя (нужна рыбалка или авто-ход)
     * - Закрытие диалогов (возврат в нормальный авто-режим)
     * 
     * @param actionName название действия
     * @param reason причина установки флага (для логирования)
     */
    public static void setForceAction(String actionName, String reason) {
        AppVars.ProbeForceNeedAutofish = true;
        AppLog.d(TAG, "FORCED_ACTION_SET: " + actionName + " - reason: " + reason);
    }

    /**
     * Очистить флаг принудительных действий.
     * 
     * Используется когда:
     * - Действие уже было выполнено
     * - Нужно вернуться в нормальный режим
     */
    public static void clearForceAction(String actionName) {
        if (AppVars.ProbeForceNeedAutofish) {
            AppLog.d(TAG, "FORCED_ACTION_CLEAR: " + actionName);
            AppVars.ProbeForceNeedAutofish = false;
        }
    }

    /**
     * Проверить установлен ли флаг принудительных действий.
     * 
     * @return true если флаг установлен
     */
    public static boolean isForceActionEnabled() {
        return AppVars.ProbeForceNeedAutofish;
    }

    /**
     * Альтернативная проверка с учетом дополнительных условий.
     * 
     * Используется для решения: запускать ли авто-действие при фоновом/заблокированном экране.
     * 
     * Логика:
     * 1) Если бой вероятно активен — ВСЕГДА разрешаем (приоритет над блокерами).
     * 2) Если UI в foreground и нет force-флагов — блокируем (чтобы не мешать ручным действиям).
     * 3) Иначе (фон/lockscreen) — разрешаем.
     * 
     * @param actionName название действия
     * @param uiForegroundLikely флаг что пользователь смотрит
     * @param fightLikelyActive флаг что идет бой
     * @param hasFightMarkers есть ли маркеры боя в HTML (информационный, НЕ блокер)
     * @param hasPendingFinishLink есть ли pendingFightFinishLink (информационный, НЕ блокер)
     * @return true если действие разрешено
     */
    public static boolean shouldForceActionAdvanced(
            String actionName,
            boolean uiForegroundLikely,
            boolean fightLikelyActive,
            boolean hasFightMarkers,
            boolean hasPendingFinishLink) {
        
        // Если идет бой (или есть маркеры/finish-link) — ОБЯЗАТЕЛЬНО разрешаем autoTurn.
        // Это критично для фонового авто-боя при заблокированном экране.
        if (fightLikelyActive || hasFightMarkers || hasPendingFinishLink) {
            return true;
        }
        
        // Нет боя — стандартная проверка с учётом UI foreground
        return shouldForceAction(actionName, uiForegroundLikely);
    }
}
