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
    private static final int POISON_INDEX = 0;
    private static final int LIGHT_WOUND_INDEX = 1;
    private static final int MEDIUM_WOUND_INDEX = 2;
    private static final int HEAVY_WOUND_INDEX = 3;

    private static volatile long lastUpdatedAtMs = 0L;
    private static volatile String lastSource = "init";

    private CharacterVitalsManager() {
        // Utility class.
    }

    public static final class Snapshot {
        /** Текущее HP персонажа. */
        public final int curHp;
        /** Максимальное HP персонажа. */
        public final int maxHp;
        /** Текущее MA персонажа. */
        public final int curMa;
        /** Максимальное MA персонажа. */
        public final int maxMa;
        /** Текущая усталость (0..100). */
        public final int tied;
        /** Интервал восстановления HP (мс) из ins_HP/hp.js. */
        public final double intHp;
        /** Интервал восстановления MA (мс) из ins_HP/hp.js. */
        public final double intMa;
        /** Количество эффектов "Яд". */
        public final int poisonCount;
        /** Количество легких травм. */
        public final int lightWoundCount;
        /** Количество средних травм. */
        public final int mediumWoundCount;
        /** Количество тяжелых травм. */
        public final int heavyWoundCount;
        /** Момент последнего обновления snapshot (System.currentTimeMillis). */
        public final long updatedAtMs;
        /** Источник последнего обновления (тег метода/модуля). */
        public final String source;

        private Snapshot(int curHp, int maxHp, int curMa, int maxMa, int tied,
                         double intHp, double intMa,
                         int poisonCount, int lightWoundCount, int mediumWoundCount, int heavyWoundCount,
                         long updatedAtMs, String source) {
            this.curHp = curHp;
            this.maxHp = maxHp;
            this.curMa = curMa;
            this.maxMa = maxMa;
            this.tied = tied;
            this.intHp = intHp;
            this.intMa = intMa;
            this.poisonCount = poisonCount;
            this.lightWoundCount = lightWoundCount;
            this.mediumWoundCount = mediumWoundCount;
            this.heavyWoundCount = heavyWoundCount;
            this.updatedAtMs = updatedAtMs;
            this.source = source;
        }
    }

    /**
     * Возвращает атомарный снимок параметров персонажа.
     *
     * Зависимости:
     * - runtime-хранилище AppVars (CurHP/MaxHP/CurMA/MaxMA/Tied/PersIntHP/PersIntMA);
     * - внутренний lock {@link #LOCK} для консистентного чтения.
     *
     * Использование:
     * - UI-слой (toast/статусы/подписи);
     * - принятие решений в авто-функциях без повторного парсинга.
     */
    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return snapshotLocked();
        }
    }

    /**
     * Централизованно обновляет усталость персонажа.
     *
     * Что делает:
     * - нормализует входное значение в диапазон 0..100;
     * - пишет новое значение в AppVars.Tied;
     * - фиксирует источник обновления и timestamp.
     *
     * Зависимости:
     * - map.js bridge (`WebAppInterface.SetCurrentTied`);
     * - парсер main.php (`MainPhp.mainPhpUpdateTied`);
     * - map_ajax too-tired ветка (`MapAjax.process`).
     */
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

    /**
     * Увеличивает усталость на шаг (обычно +2 за переход по карте) с нормализацией 0..100.
     *
     * Зависимости:
     * - авто-переходы карты/навигации (`MapAjax.onAutoMovingCellObserved`);
     * - пороговые проверки автопитья блажа.
     */
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

    /**
     * Обновляет текущие/максимальные HP и MA одной транзакцией.
     *
     * Что важно:
     * - значения приводятся к неотрицательным;
     * - текущие HP/MA не могут превышать max;
     * - после обновления возвращается единый snapshot.
     *
     * Зависимости:
     * - pinfo-синхронизация;
     * - bridge hp.js;
     * - postfilter main.php.
     */
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

    /**
     * Обновляет интервалы регенерации HP/MA из JS/ins_HP.
     *
     * Правило:
     * - записываются только положительные интервалы;
     * - нулевые/отрицательные значения игнорируются как невалидные.
     *
     * Зависимости:
     * - `LezFight` (использует PersIntHP/PersIntMA для расчета таймингов восстановления);
     * - `HpJs` / `MainPhp`.
     */
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

    /**
     * Централизованно обновляет runtime-снимок отравления/травм.
     *
     * Формат массива (C# parity `PoisonAndWounds`):
     * - `[0]` яд, `[1]` легкие, `[2]` средние, `[3]` тяжелые.
     */
    public static Snapshot updatePoisonAndWounds(int[] poisonAndWounds, String source) {
        synchronized (LOCK) {
            applyPoisonAndWoundsLocked(poisonAndWounds);
            touchLocked(source);
            return snapshotLocked();
        }
    }

    /**
     * Уменьшает счётчик одного типа эффекта (яд/травма) на 1 с защитой от отрицательных значений.
     */
    public static Snapshot decrementPoisonOrWound(int index, String source) {
        synchronized (LOCK) {
            if (index < POISON_INDEX || index > HEAVY_WOUND_INDEX) {
                touchLocked(source);
                return snapshotLocked();
            }
            int[] current = normalizePoisonAndWounds(AppVars.PoisonAndWounds);
            if (current[index] > 0) {
                current[index]--;
                applyPoisonAndWoundsLocked(current);
            }
            touchLocked(source);
            return snapshotLocked();
        }
    }

    /**
     * Гарантирует наличие хотя бы одной тяжелой травмы в runtime-состоянии.
     * Используется как fallback при server popup "У Вас тяжёлая травма".
     */
    public static Snapshot ensureHeavyWoundPresent(String source) {
        synchronized (LOCK) {
            int[] current = normalizePoisonAndWounds(AppVars.PoisonAndWounds);
            if (current[HEAVY_WOUND_INDEX] <= 0) {
                current[HEAVY_WOUND_INDEX] = 1;
                applyPoisonAndWoundsLocked(current);
            }
            touchLocked(source);
            return snapshotLocked();
        }
    }

    /**
     * Обновление из полного снимка `ins_HP(cur,max,cur,max,intHp,intMa)`.
     *
     * Зависимости:
     * - `MainPhp.parseInsHpSnapshot(...)`;
     * - бизнес-логика восстановления в бою и после боя (через PersInt* и HP/MA).
     */
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

    /**
     * Обновление из JS-bridge `showHpMaTimers(...)`.
     *
     * Особенность:
     * - curHp/curMa приходят float и нормализуются через округление до int.
     *
     * Зависимости:
     * - `WebAppInterface.showHpMaTimers(...)`;
     * - JS-код hp.js в верхнем фрейме.
     */
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

    /**
     * Обновляет vitals из ответа `pinfo.cgi`.
     *
     * Что делает:
     * - применяет усталость, если `curTire` присутствует;
     * - частично обновляет HP/MA (если сервер вернул хотя бы одно из полей);
     * - не сбрасывает существующие значения отсутствующими полями.
     *
     * Зависимости:
     * - `NeverApi.getPinfoVitalsFromPinfo(...)`;
     * - авто-синхронизация персонажа после логина;
     * - near-threshold синхронизация усталости в навигаторе/авто-кладе.
     */
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
            if (vitals.poisonAndWounds != null) {
                applyPoisonAndWoundsLocked(vitals.poisonAndWounds);
            }
            touchLocked(source);
            Log.d(TAG, "VITALS_TRACE from pinfo: hp=" + AppVars.CurHP + "/" + AppVars.MaxHP
                    + ", ma=" + AppVars.CurMA + "/" + AppVars.MaxMA
                    + ", tied=" + AppVars.Tied
                    + ", pw=" + poisonAndWoundsToLog(AppVars.PoisonAndWounds)
                    + ", source=" + source);
            return snapshotLocked();
        }
    }

    /**
     * Формирует единый человекочитаемый текст состояния персонажа.
     *
     * Формат:
     * - `prefix: HP cur/max; MA cur/max; Усталость: tied`
     *
     * Использование:
     * - toast-уведомления синхронизации;
     * - унифицированные сообщения в модулях, где раньше была ручная склейка строки.
     */
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

    private static void applyPoisonAndWoundsLocked(int[] poisonAndWounds) {
        int[] normalized = normalizePoisonAndWounds(poisonAndWounds);
        if (normalized == null) {
            return;
        }
        AppVars.PoisonAndWounds = normalized;
        Log.d(TAG, "VITALS_TRACE poisonAndWounds: " + poisonAndWoundsToLog(normalized));
    }

    private static int[] normalizePoisonAndWounds(int[] poisonAndWounds) {
        if (poisonAndWounds == null || poisonAndWounds.length < 4) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {
                Math.max(0, poisonAndWounds[POISON_INDEX]),
                Math.max(0, poisonAndWounds[LIGHT_WOUND_INDEX]),
                Math.max(0, poisonAndWounds[MEDIUM_WOUND_INDEX]),
                Math.max(0, poisonAndWounds[HEAVY_WOUND_INDEX])
        };
    }

    private static String poisonAndWoundsToLog(int[] poisonAndWounds) {
        int[] normalized = normalizePoisonAndWounds(poisonAndWounds);
        return "[" + normalized[POISON_INDEX]
                + "," + normalized[LIGHT_WOUND_INDEX]
                + "," + normalized[MEDIUM_WOUND_INDEX]
                + "," + normalized[HEAVY_WOUND_INDEX] + "]";
    }

    private static void touchLocked(String source) {
        lastUpdatedAtMs = System.currentTimeMillis();
        if (source != null && !source.isEmpty()) {
            lastSource = source;
        }
    }

    private static Snapshot snapshotLocked() {
        int[] poisonAndWounds = normalizePoisonAndWounds(AppVars.PoisonAndWounds);
        return new Snapshot(
                AppVars.CurHP,
                AppVars.MaxHP,
                AppVars.CurMA,
                AppVars.MaxMA,
                clampPercent(AppVars.Tied),
                AppVars.PersIntHP,
                AppVars.PersIntMA,
                poisonAndWounds[POISON_INDEX],
                poisonAndWounds[LIGHT_WOUND_INDEX],
                poisonAndWounds[MEDIUM_WOUND_INDEX],
                poisonAndWounds[HEAVY_WOUND_INDEX],
                lastUpdatedAtMs,
                lastSource
        );
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
