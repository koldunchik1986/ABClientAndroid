package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;

final class MainPhpPauseGuards {

    private MainPhpPauseGuards() {
    }

    static boolean isNonCombatAutoPausedByFastAction() {
        return (AppVars.FastNeed && AppVars.FastPauseNonCombatAutoFunctions)
                || AppVars.TreasureDigPauseNonCombatAutoFunctions
                || AppVars.TimerPauseNonCombatAutoFunctions;
    }

    static boolean isNonCombatAutoPausedByCureAction() {
        return AppVars.CurePauseNonCombatAutoFunctions;
    }
}
