package ru.neverlands.anclient.model;

import java.util.Objects;

/**
 * Модель противника (порт `ANClient/Foe.cs`).
 *
 * Назначение:
 * - хранить тип/уровень врага,
 * - валидировать связку "раса + уровень" по правилам ПК-клиента,
 * - давать единый Comparable/equals для сортировки и сравнения списков врагов.
 *
 * Зависимости:
 * - используется как строгая типизация в потоках автобоя/авто-нападения;
 * - правила валидации синхронизированы с C# `CheckValid()`.
 */
public class Foe implements Comparable<Foe>, Cloneable {
    private final String triba;
    private final int level;
    private final boolean nevid;
    private final String link;
    private boolean valid;

    public Foe() {
        this.triba = "";
        this.level = 0;
        this.link = "";
        this.nevid = true;
        this.valid = true;
    }

    public Foe(String triba, int level, String link) {
        this.triba = triba == null ? "" : triba;
        this.level = level;
        this.link = link == null ? "" : link;
        this.nevid = false;
        checkValid();
    }

    public Foe(String source) {
        String safe = source == null ? "" : source;
        int p1 = safe.indexOf('[');
        if (p1 < 0) {
            this.triba = "";
            this.level = 0;
            this.link = "";
            this.nevid = true;
            this.valid = true;
            return;
        }

        int p2 = safe.indexOf(']', p1 + 1);
        if (p2 < 0) {
            this.triba = "";
            this.level = 0;
            this.link = "";
            this.nevid = true;
            this.valid = true;
            return;
        }

        String parsedTriba = safe.substring(0, p1);
        String levelText = safe.substring(p1 + 1, p2);
        int parsedLevel;
        try {
            parsedLevel = Integer.parseInt(levelText);
        } catch (Exception ignored) {
            this.triba = "";
            this.level = 0;
            this.link = "";
            this.nevid = true;
            this.valid = true;
            return;
        }

        this.triba = parsedTriba;
        this.level = parsedLevel;
        this.link = "";
        this.nevid = false;
        checkValid();
    }

    public int getLevel() {
        return level;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Аналог C# `IsHuman()`: невидимка или раса "Человек".
     */
    public boolean isHuman() {
        return nevid || "Человек".equalsIgnoreCase(triba);
    }

    @Override
    public String toString() {
        return nevid ? "Невидимка" : triba + "[" + level + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Foe)) {
            return false;
        }
        Foe other = (Foe) obj;
        if (nevid && other.nevid) {
            return true;
        }
        if (!nevid && !other.nevid) {
            return Objects.equals(triba, other.triba)
                    && level == other.level
                    && Objects.equals(link, other.link);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(triba, level, nevid, link);
    }

    private void checkValid() {
        valid = false;
        switch (triba) {
            case "Невидимка":
                valid = true;
                break;
            case "Человек":
                valid = level == 33 || (level >= 0 && level <= 26);
                break;
            case "Огр":
                valid = level >= 16 && level <= 24;
                break;
            case "Орк":
                valid = level >= 1 && level <= 15;
                break;
            case "Гоблин":
                valid = level >= 1 && level <= 14;
                break;
            case "Кабан":
                valid = level >= 8 && level <= 12;
                break;
            case "Крыса":
                valid = level >= 0 && level <= 10;
                break;
            case "Паук":
                valid = level >= 1 && level <= 5;
                break;
            case "Ядовитый паук":
                valid = level >= 6 && level <= 10;
                break;
            case "Зомби":
                valid = level >= 10 && level <= 15;
                break;
            case "Скелет":
                valid = level >= 7 && level <= 10;
                break;
            case "Скелет-Воин":
                valid = level >= 11 && level <= 15;
                break;
            case "Разбойник":
                valid = level >= 5 && level <= 17;
                break;
            case "Грабитель":
                valid = level >= 6 && level <= 16;
                break;
            case "Сильф":
            case "Нетопырь":
                valid = true;
                break;
            case "Королева Змей":
            case "Хранитель Леса":
            case "Громлех Синезубый":
            case "Выползень":
                valid = level == 25;
                break;
            default:
                valid = false;
                break;
        }
    }

    @Override
    public int compareTo(Foe other) {
        if (other == null) {
            return 1;
        }
        if (nevid && other.nevid) {
            return 0;
        }
        if (!nevid && !other.nevid) {
            int cmp = triba.compareTo(other.triba);
            return cmp == 0 ? Integer.compare(level, other.level) : cmp;
        }
        if (!nevid) {
            return 1;
        }
        return -1;
    }

    @Override
    public Foe clone() {
        if (nevid) {
            return new Foe();
        }
        return new Foe(triba, level, link);
    }
}
