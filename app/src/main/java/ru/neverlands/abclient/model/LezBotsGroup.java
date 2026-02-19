package ru.neverlands.abclient.model;

import java.io.Serializable;

/**
 * Группа ботов.
 * Портировано из LezBotsGroup.cs.
 */
public class LezBotsGroup implements Serializable, Cloneable, Comparable<LezBotsGroup> {
    public int Id;
    public int MinimalLevel;

    public boolean DoRestoreHp;
    public boolean DoRestoreMa;
    public int RestoreHp;
    public int RestoreMa;
    public boolean DoAbilBlocks;
    public boolean DoAbilHits;
    public boolean DoMagHits;
    public int MagHits;
    public boolean DoMagBlocks;
    public boolean DoHits;
    public boolean DoBlocks;
    public boolean DoMiscAbils;

    public boolean DoStopNow;
    public boolean DoStopLowHp;
    public boolean DoStopLowMa;
    public int StopLowHp;
    public int StopLowMa;
    public boolean DoExit;
    public boolean DoExitRisky;

    public int[] SpellsHits;
    public int[] SpellsBlocks;
    public int[] SpellsRestoreHp;
    public int[] SpellsRestoreMa;
    public int[] SpellsMisc;

    public LezBotsGroup(int id, int minimalLevel) {
        Change(id, minimalLevel);

        DoRestoreHp = true;
        DoRestoreMa = true;
        RestoreHp = 50;
        RestoreMa = 50;
        DoAbilBlocks = true;
        DoAbilHits = true;
        DoMagHits = true;
        MagHits = 5;
        DoMagBlocks = false;
        DoHits = true;
        DoBlocks = true;
        DoMiscAbils = true;

        DoStopNow = false;
        DoStopLowHp = false;
        DoStopLowMa = false;
        StopLowHp = 25;
        StopLowMa = 25;
        DoExit = false;
        DoExitRisky = true;

        SpellsHits = LezSpellCollection.Hits;
        SpellsBlocks = LezSpellCollection.Blocks;
        SpellsRestoreHp = LezSpellCollection.RestoreHp;
        SpellsRestoreMa = LezSpellCollection.RestoreMa;
        SpellsMisc = LezSpellCollection.Misc;
    }

    public void Change(int id, int minimalLevel) {
        Id = id;
        MinimalLevel = minimalLevel;
    }

    @Override
    public String toString() {
        String plural = LezBotsClassCollection.getClass(Id).plural;
        return String.format("%s %d+", plural, MinimalLevel);
    }

    @Override
    public int compareTo(LezBotsGroup other) {
        if (other == null) return -1;
        int result = Integer.compare(other.Id, Id);
        if (result != 0) return result;
        return Integer.compare(other.MinimalLevel, MinimalLevel);
    }

    @Override
    public LezBotsGroup clone() {
        try {
            LezBotsGroup cloned = (LezBotsGroup) super.clone();
            if (this.SpellsHits != null) cloned.SpellsHits = this.SpellsHits.clone();
            if (this.SpellsBlocks != null) cloned.SpellsBlocks = this.SpellsBlocks.clone();
            if (this.SpellsRestoreHp != null) cloned.SpellsRestoreHp = this.SpellsRestoreHp.clone();
            if (this.SpellsRestoreMa != null) cloned.SpellsRestoreMa = this.SpellsRestoreMa.clone();
            if (this.SpellsMisc != null) cloned.SpellsMisc = this.SpellsMisc.clone();
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
