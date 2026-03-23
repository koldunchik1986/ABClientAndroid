package ru.neverlands.abclient.manager;

import android.util.Log;

import java.util.Locale;

import ru.neverlands.abclient.utils.AppVars;

/**
 * Единый runtime-реестр параметров персонажа (HP/MA/усталость/интервалы регена).
 *
 * Назначение:
 * - убрать дубли прямых записей в AppVars по разным модулям;
 * - гарантировать консистентное обновление параметров из разных источников;
 * - дать единый snapshot для логов/UI.
 *
 * Источники данных:
 * - hp.js bridge (`WebAppInterface.showHpMaTimers`)
 * - map.js bridge (`WebAppInterface.SetCurrentTied`)
 * - main.php (`ins_HP(...)`, блок "Усталость")
 * - pinfo.cgi (`NeverApi.PinfoVitals`)
 */
public final class CharacterVitalsManager {
    private static final String TAG = "CharacterVitalsManager";
    private static final Object LOCK = new Object();

    private static volatile long lastUpdatedAtMs = 0L;
    private static volatile String lastSource = "init";

    private CharacterVitalsManager() {
        // Utility class.
    }

    public static final class Snapshot {
        public final int curHp;
        public final int maxHp;
        public final int curMa;
        public final int maxMa;
        public final int tied;
        public final double intHp;
        public final double intMa;
        public final long updatedAtMs;
        public final String source;

        private Snapshot(int curHp, int maxHp, int curMa, int maxMa, int tied,
                         double intHp, double intMa, long updatedAtMs, String source) {
            this.curHp = curHp;
            this.maxHp = maxHp;
            this.curMa = curMa;
            this.maxMa = maxMa;
            this.tied = tied;
            this.intHp = intHp;
            this.intMa = intMa;
            this.updatedAtMs = updatedAtMs;
            this.source = source;
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return snapshotLocked();
        }
    }

    public static Snapshot updateTied(int tied, String source) {
        synchronized (LOCK) {
            int normalized = clampPercent(tied);
            if (AppVars.Tied != normalized) {
                Log.d(TAG, "VITALS_TRACE tied: " + AppVars.Tied + " -> " + normalized + ", source=" + source);
                AppVars.Tied = normalized;
            }
            touchLocked(source);
            return snapshotLocked();
        }
    }

    public static Snapshot increaseTied(int delta, String source) {
        synchronized (LOCK) {
            int normalized = clampPercent(AppVars.Tied + delta);
            if (AppVars.Tied != normalized) {
                Log.d(TAG, "VITALS_TRACE tied(step): " + AppVars.Tied + " -> " + normalized
                        + ", delta=" + delta + ", source=" + source);
                AppVars.Tied = normalized;
            }
            touchLocked(source);
            return snapshotLocked();
        }
    }

    public static Snapshot updateHpMa(int curHp, int maxHp, int curMa, int maxMa, String source) {
        synchronized (LOCK) {
            int normMaxHp = Math.max(0, maxHp);
            int normMaxMa = Math.max(0, maxMa);
            int normCurHp = Math.max(0, curHp);
            int normCurMa = Math.max(0, curMa);
            if (normMaxHp > 0 && normCurHp > normMaxHp) normCurHp = normMaxHp;
            if (normMaxMa > 0 && normCurMa > normMaxMa) normCurMa = normMaxMa;

            boolean changed = false;
            if (AppVars.CurHP != normCurHp) {
                AppVars.CurHP = normCurHp;
                changed = true;
            }
            if (AppVars.MaxHP != normMaxHp) {
                AppVars.MaxHP = normMaxHp;
                changed = true;
            }
            if (AppVars.CurMA != normCurMa) {
                AppVars.CurMA = normCurMa;
                changed = true;
            }
            if (AppVars.MaxMA != normMaxMa) {
                AppVars.MaxMA = normMaxMa;
                changed = true;
            }
            if (changed) {
                Log.d(TAG, "VITALS_TRACE hpma: hp=" + AppVars.CurHP + "/" + AppVars.MaxHP
                        + ", ma=" + AppVars.CurMA + "/" + AppVars.MaxMA + ", source=" + source);
            }
            touchLocked(source);
            return snapshotLocked();
        }
    }

    public static Snapshot updateRegenIntervals(double intHp, double intMa, String source) {
        synchronized (LOCK) {
            boolean changed = false;
            if (intHp > 0d && Double.compare(AppVars.PersIntHP, intHp) != 0) {
                AppVars.PersIntHP = intHp;
                changed = true;
            }
            if (intMa > 0d && Double.compare(AppVars.PersIntMA, intMa) != 0) {
                AppVars.PersIntMA = intMa;
                changed = true;
            }
            if (changed) {
                Log.d(TAG, "VITALS_TRACE regen: intHp=" + AppVars.PersIntHP
                        + ", intMa=" + AppVars.PersIntMA + ", source=" + source);
            }
            touchLocked(source);
            return snapshotLocked();
        }
    }

    public static Snapshot updateFromInsHpSnapshot(int curHp, int maxHp, int curMa, int maxMa,
                                                   double intHp, double intMa, String source) {
        synchronized (LOCK) {
            updateHpMaLocked(curHp, maxHp, curMa, maxMa);
            updateRegenLocked(intHp, intMa);
            touchLocked(source);
            Log.d(TAG, "VITALS_TRACE from ins_HP: hp=" + AppVars.CurHP + "/" + AppVars.MaxHP
                    + ", ma=" + AppVars.CurMA + "/" + AppVars.MaxMA
                    + ", intHp=" + AppVars.PersIntHP + ", intMa=" + AppVars.PersIntMA
                    + ", source=" + source);
            return snapshotLocked();
        }
    }

    public static Snapshot updateFromHpJs(float curHp, int maxHp, float curMa, int maxMa,
                                          float intHp, float intMa, String source) {
        synchronized (LOCK) {
            updateHpMaLocked(Math.round(curHp), maxHp, Math.round(curMa), maxMa);
            updateRegenLocked(intHp, intMa);
            touchLocked(source);
            Log.d(TAG, "VITALS_TRACE from hp.js: hp=" + AppVars.CurHP + "/" + AppVars.MaxHP
                    + ", ma=" + AppVars.CurMA + "/" + AppVars.MaxMA
                    + ", intHp=" + AppVars.PersIntHP + ", intMa=" + AppVars.PersIntMA
                    + ", source=" + source);
            return snapshotLocked();
        }
    }

    public static Snapshot updateFromPinfo(NeverApi.PinfoVitals vitals, String source) {
        synchronized (LOCK) {
            if (vitals == null) {
                touchLocked(source);
                return snapshotLocked();
            }
            if (vitals.curTire != null) {
                AppVars.Tied = clampPercent(vitals.curTire);
            }
            if (vitals.curHp != null || vitals.maxHp != null || vitals.curMa != null || vitals.maxMa != null) {
                int curHp = vitals.curHp != null ? vitals.curHp : AppVars.CurHP;
                int maxHp = vitals.maxHp != null ? vitals.maxHp : AppVars.MaxHP;
                int curMa = vitals.curMa != null ? vitals.curMa : AppVars.CurMA;
                int maxMa = vitals.maxMa != null ? vitals.maxMa : AppVars.MaxMA;
                updateHpMaLocked(curHp, maxHp, curMa, maxMa);
            }
            touchLocked(source);
            Log.d(TAG, "VITALS_TRACE from pinfo: hp=" + AppVars.CurHP + "/" + AppVars.MaxHP
                    + ", ma=" + AppVars.CurMA + "/" + AppVars.MaxMA
                    + ", tied=" + AppVars.Tied + ", source=" + source);
            return snapshotLocked();
        }
    }

    public static String buildSyncMessage(String prefix, Snapshot snapshot) {
        String label = (prefix == null || prefix.isEmpty()) ? "Синхронизация Персонажа" : prefix;
        Snapshot s = snapshot != null ? snapshot : snapshot();
        return label
                + ": HP: " + s.curHp + "/" + s.maxHp
                + "; MA: " + s.curMa + "/" + s.maxMa
                + "; Усталость: " + s.tied;
    }

    private static void updateHpMaLocked(int curHp, int maxHp, int curMa, int maxMa) {
        int normMaxHp = Math.max(0, maxHp);
        int normMaxMa = Math.max(0, maxMa);
        int normCurHp = Math.max(0, curHp);
        int normCurMa = Math.max(0, curMa);
        if (normMaxHp > 0 && normCurHp > normMaxHp) normCurHp = normMaxHp;
        if (normMaxMa > 0 && normCurMa > normMaxMa) normCurMa = normMaxMa;
        AppVars.CurHP = normCurHp;
        AppVars.MaxHP = normMaxHp;
        AppVars.CurMA = normCurMa;
        AppVars.MaxMA = normMaxMa;
    }

    private static void updateRegenLocked(double intHp, double intMa) {
        if (intHp > 0d) {
            AppVars.PersIntHP = intHp;
        }
        if (intMa > 0d) {
            AppVars.PersIntMA = intMa;
        }
    }

    private static void touchLocked(String source) {
        lastUpdatedAtMs = System.currentTimeMillis();
        if (source != null && !source.isEmpty()) {
            lastSource = source;
        }
    }

    private static Snapshot snapshotLocked() {
        return new Snapshot(
                AppVars.CurHP,
                AppVars.MaxHP,
                AppVars.CurMA,
                AppVars.MaxMA,
                clampPercent(AppVars.Tied),
                AppVars.PersIntHP,
                AppVars.PersIntMA,
                lastUpdatedAtMs,
                lastSource
        );
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}

