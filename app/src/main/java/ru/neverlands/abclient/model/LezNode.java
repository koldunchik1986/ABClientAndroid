package ru.neverlands.abclient.model;

import java.io.Serializable;

/**
 * Узел комбинации.
 * Портировано из LezNode.cs.
 */
public class LezNode implements Serializable, Cloneable, Comparable<LezNode> {
    public final int[] HitOps = new int[4];
    public final int[] HitCodes = new int[4];
    public int BlockCombo;
    public int BlockOp;
    public int BlockCode;
    public final boolean[] MagicFlags = new boolean[18];
    public final int[] MagicCodes = new int[18];

    private int _zScroll;
    private int _zRestore;
    private int _zMag;
    private int _zHit;
    private int _zBlock;

    private int HitCounts() {
        int count = 0;
        for (int op : HitOps) if (op > 0) count++;
        return count;
    }

    private int BlockCounts() {
        return (BlockOp > 0) ? 1 : 0;
    }

    private int MagicCounts() {
        int count = 0;
        for (boolean flag : MagicFlags) if (flag) count++;
        return count;
    }

    public boolean IsValid() {
        int hc = HitCounts();
        int bc = BlockCounts();
        int mc = MagicCounts();
        return ((hc > 0 && mc > 0) || (bc > 0 && mc > 0) || (hc > 0 && bc > 0) || hc > 1);
    }

    public int Od(int[] posod) {
        int od = 0;
        int hc = HitCounts();
        if (hc > 0) {
            for (int i = 0; i < 4; i++) if (HitOps[i] > 0) od += posod[HitCodes[i]];
            int[] shtraud = {0, 0, 25, 75, 150, 250};
            if (hc < shtraud.length) od += shtraud[hc];
        }
        if (BlockCounts() > 0) od += posod[BlockCode];
        if (MagicCounts() > 0) {
            for (int i = 0; i < MagicFlags.length; i++) if (MagicFlags[i]) od += posod[MagicCodes[i]];
        }
        return od;
    }

    public int Mana(int[] posma) {
        int mana = 0;
        if (HitCounts() > 0) {
            for (int i = 0; i < 4; i++) if (HitOps[i] > 0) mana += posma[HitCodes[i]];
        }
        if (BlockCounts() > 0) mana += posma[BlockCode];
        if (MagicCounts() > 0) {
            for (int i = 0; i < MagicFlags.length; i++) if (MagicFlags[i]) mana += posma[MagicCodes[i]];
        }
        return mana;
    }

    private String Z() {
        return String.format(java.util.Locale.US, "%d.%d.%02d.%d.%02d", _zScroll, _zRestore, _zMag, _zBlock, _zHit);
    }

    public void AddHit(int combo, int op, int code) {
        HitOps[combo] = op;
        HitCodes[combo] = code;
        if (LezSpell.IsPhHit(code)) {
            _zHit += (code == 0 ? 3 : 4);
        } else {
            if (LezSpell.IsMagHit(code)) _zHit += (code == 2 ? 10 : 12);
            else _zHit += 25;
        }
    }

    public void AddBlock(int combo, int op, int code) {
        BlockCombo = combo;
        BlockOp = op;
        BlockCode = code;
        if (LezSpell.IsPhBlock(code)) {
            LezSpell s = LezSpellCollection.Spells.get(code);
            if (s != null) _zBlock = s.Name.split("\\+").length;
        } else {
            if (LezSpell.IsMagBlock(code)) {
                if (code == 29) _zBlock = 1;
                else if (code == 30) _zBlock = 2;
                else if (code == 31) _zBlock = 3;
            } else {
                _zMag += 4;
            }
        }
    }

    public void AddMagic(int op, int code, int zmag, int zrestore, int zscroll) {
        MagicFlags[op] = true;
        MagicCodes[op] = code;
        _zScroll += zscroll;
        _zRestore += zrestore;
        _zMag += zmag;
    }

    public void AddCombination(LezNode other) {
        for (int i = 0; i < 4; i++) {
            if (other.HitOps[i] > 0) {
                HitOps[i] = other.HitOps[i];
                HitCodes[i] = other.HitCodes[i];
            }
        }
        if (other.BlockOp > 0) {
            BlockCombo = other.BlockCombo;
            BlockOp = other.BlockOp;
            BlockCode = other.BlockCode;
        }
        for (int i = 0; i < other.MagicFlags.length; i++) {
            if (other.MagicFlags[i]) {
                MagicFlags[i] = other.MagicFlags[i];
                MagicCodes[i] = other.MagicCodes[i];
            }
        }
        _zScroll += other._zScroll;
        _zRestore += other._zRestore;
        _zMag += other._zMag;
        _zHit += other._zHit;
        _zBlock += other._zBlock;
    }

    public boolean HasNonPhBlock(LezBotsGroup foeGroup) {
        if (BlockOp > 0) {
            if (LezSpell.IsMagBlock(BlockCode)) return true;
            if (foeGroup.SpellsBlocks != null) {
                for (int b : foeGroup.SpellsBlocks) if (b == BlockCode) return true;
            }
        }
        for (int i = 0; i < MagicFlags.length; i++) {
            if (MagicFlags[i]) {
                if (foeGroup.SpellsBlocks != null) {
                    for (int b : foeGroup.SpellsBlocks) if (b == MagicCodes[i]) return true;
                }
            }
        }
        return false;
    }

    @Override
    public int compareTo(LezNode other) {
        if (other == null) return -1;
        return Z().compareTo(other.Z());
    }

    @Override
    public LezNode clone() {
        try {
            LezNode cloned = (LezNode) super.clone();
            System.arraycopy(this.HitOps, 0, cloned.HitOps, 0, 4);
            System.arraycopy(this.HitCodes, 0, cloned.HitCodes, 0, 4);
            System.arraycopy(this.MagicFlags, 0, cloned.MagicFlags, 0, 18);
            System.arraycopy(this.MagicCodes, 0, cloned.MagicCodes, 0, 18);
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
