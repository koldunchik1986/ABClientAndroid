package ru.neverlands.abclient.model;

/**
 * C#-совместимая битовая маска приманок рыбалки (`ABClient/Prims.cs`).
 *
 * Зависимости:
 * - `UserConfig.FishEnabledPrims` хранит значение этой маски в профиле;
 * - `MainPhpFish`/`FishAjaxPhp` используют маску для выбора разрешенных приманок;
 * - значения должны совпадать 1:1 с C# для корректной загрузки старых `.profile`.
 */
public final class Prims {
    private Prims() {}

    public static final int Bread = 0x01;
    public static final int Worm = 0x02;
    public static final int BigWorm = 0x04;
    public static final int Stink = 0x08;
    public static final int Fly = 0x10;
    public static final int Light = 0x20;
    public static final int Donka = 0x40;
    public static final int Morm = 0x80;
    public static final int HiFlight = 0x100;

    public static final int DEFAULT_ALL =
            Bread | Worm | BigWorm | Stink | Fly | Light | Donka | Morm | HiFlight;
}
