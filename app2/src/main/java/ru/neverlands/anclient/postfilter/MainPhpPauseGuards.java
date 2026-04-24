package ru.neverlands.anclient.postfilter;

import ru.neverlands.anclient.utils.AppVars;

/**
 * Единая точка pause-guards для non-combat automation в main.php.
 *
 * Источник выноса: маленькие методы MainPhp.isNonCombatAutoPausedByFastAction()
 * и MainPhp.isNonCombatAutoPausedByCureAction(). Бой/автобой не должен зависеть от этих guard-ов.
 */
final class MainPhpPauseGuards {

    private MainPhpPauseGuards() {
    }

    /**
     * Возвращает true, если non-combat pipeline должен ждать.
     * Зависимости: AppVars.FastNeed + FastPauseNonCombatAutoFunctions, TreasureDigPauseNonCombatAutoFunctions,
     * TimerPauseNonCombatAutoFunctions. Используется AutoFish/AutoSkin/AutoFury/AutoMoving до redirect-веток.
     */
    static boolean isNonCombatAutoPausedByFastAction() {
        return (AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions)
                || AppVars.TreasureDigPauseNonCombatAutoFunctions
                || AppVars.TimerPauseNonCombatAutoFunctions;
    }

    /**
     * Guard внешнего лечения: AppVars.CurePauseNonCombatAutoFunctions блокирует AutoSearch/AutoMoving,
     * чтобы doctorform/cure flow не конкурировал с навигацией.
     */
    static boolean isNonCombatAutoPausedByCureAction() {
        return AppVars.CurePauseNonCombatAutoFunctions;
    }
}
