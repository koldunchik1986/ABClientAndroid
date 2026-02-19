package ru.neverlands.abclient.lez;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import ru.neverlands.abclient.model.*;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Логика ведения боя.
 * Портировано из LezFight.cs.
 */
public class LezFight {
    public boolean IsValid;
    public boolean IsBoi;
    public boolean IsWaitingForNextTurn;
    public boolean DoStop;
    public boolean DoExit;
    public boolean IsLowHp;
    public boolean IsLowMa;
    public String LogBoi = "";
    public String FoeName = "";

    private String _html;
    private String[] _fightty;
    private String[] _fexp;
    private int _ftype;
    private int _currentHp, _maxHp;
    private int _currentMa, _maxMa;
    private int _percentHp, _percentMa;
    private String _foeImage, _foeName;
    private int _foeLevel, _foeGroupId;
    public LezBotsGroup FoeGroup;
    private int _magmax, _odmax, _hitval, _bs;
    private int[] _posod;
    private int[] _posma;
    private String[] _bspar;
    private boolean _hitByScroll;
    
    private final List<Integer> _hits = new ArrayList<>();
    private final List<Boolean> _ehits = new ArrayList<>();
    private final List<Integer> _magblocks = new ArrayList<>();
    private final List<List<Integer>> _blocks = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    private final List<List<Boolean>> _eblocks = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    private final List<Integer> _magics = new ArrayList<>();
    private final List<Boolean> _emagics = new ArrayList<>();

    private final List<LezNode> _lezHits = new ArrayList<>(Arrays.asList(new LezNode()));
    private final List<LezNode> _lezBlocks = new ArrayList<>(Arrays.asList(new LezNode()));
    private final List<LezNode> _lezMagics = new ArrayList<>(Arrays.asList(new LezNode()));

    public final List<LezNode> LezCombinations = new ArrayList<>();
    public LezNode LezCombination;
    public String Result;
    public String Frame;

    public LezFight(String html) {
        _html = html;
        IsValid = Parse(html);
    }

    private boolean Parse(String html) {
        AppVars.FightLink = "";

        _fightty = ParseString(html, "var fight_ty = [", 0);
        if (_fightty == null || _fightty.length <= 8) return false;

        LogBoi = Strip(_fightty[8]);
        try {
            _ftype = Integer.parseInt(Strip(_fightty[2]));
        } catch (NumberFormatException e) {
            _ftype = 0;
        }

        IsBoi = (_fightty[3].length() >= 1) && (_fightty[3].charAt(0) == '1');

        String[] paramow = ParseString(html, "var param_ow = [", 0);
        if (paramow == null) return false;

        try {
            _currentHp = (int) Double.parseDouble(Strip(paramow[1]));
            _maxHp = (int) Double.parseDouble(Strip(paramow[2]));
            _currentMa = (int) Double.parseDouble(Strip(paramow[3]));
            _maxMa = (int) Double.parseDouble(Strip(paramow[4]));
        } catch (NumberFormatException e) {
            return false;
        }

        _percentHp = _maxHp > 0 ? (_currentHp * 100) / _maxHp : 0;
        _percentMa = _maxMa > 0 ? (_currentMa * 100) / _maxMa : 0;

        if (!IsBoi) return ParseNonFight();

        String[] standin = ParseString(html, "var stand_in = [", 0);
        String[] magicin = ParseString(html, "var magic_in = [", 0);
        String[] paramen = ParseString(html, "var param_en = [", 0);
        String[] slotsen = ParseString(html, "var slots_en = [", 0);
        String[] fightpm = ParseString(html, "var fight_pm = [", 0);

        if (paramen == null || slotsen == null || fightpm == null) return false;

        FoeName = Strip(paramen[0]);
        _foeName = FoeName;
        try { _foeLevel = Integer.parseInt(Strip(paramen[5])); } catch (Exception e) { _foeLevel = 33; }
        _foeImage = Strip(slotsen[0]);

        if (!_foeImage.startsWith("bot") && !_foeImage.startsWith("_xneto") && !_foeImage.startsWith("_xsilf")) {
            _foeName = "Человек";
        }

        SelectFoeGroup();

        try {
            _magmax = Integer.parseInt(Strip(fightpm[0]));
            _odmax = Integer.parseInt(Strip(fightpm[1]));
            _hitval = Integer.parseInt(Strip(fightpm[2]));
        } catch (Exception e) { return false; }

        _posod = LezSpellCollection.Od.clone();
        _posod[0] = _hitval;
        _posod[1] = _hitval + 20;

        _posma = LezSpellCollection.PosMana.clone();
        _posma[2] = FoeGroup.MagHits;
        _posma[3] = FoeGroup.MagHits;

        List<Integer> lstandin = new ArrayList<>(Arrays.asList(0, 1));
        if (standin != null) {
            for (String s : standin) {
                try { lstandin.add(Integer.parseInt(Strip(s))); } catch (Exception ignored) {}
            }
        }
        Selpl(0, lstandin);

        List<Integer> lmagicin = new ArrayList<>();
        if (magicin != null) {
            for (String s : magicin) {
                try { lmagicin.add(Integer.parseInt(Strip(s))); } catch (Exception ignored) {}
            }
        }
        if (!lmagicin.isEmpty()) Selpl(1, lmagicin);

        _bs = 0;
        if (fightpm.length > 3) {
            String bsStr = Strip(fightpm[3]);
            if (bsStr.equals("40")) _bs = 1;
            else if (bsStr.equals("70")) _bs = 2;
            else if (bsStr.equals("90")) _bs = 3;
        }

        String[] tshowbl = { "4:5:6@7:8:9@10:11@12:13", "14:15@16:17@18:19@20:21", "22:23@24@25@26", "27@28" };
        _bspar = tshowbl[_bs].split("@");
        for (int ee = 0; ee < 4; ee++) {
            if (ee >= _bspar.length) break;
            String[] blks = _bspar[ee].split(":");
            for (String b : blks) {
                int val = Integer.parseInt(b);
                _blocks.get(ee).add(val);
            }
            _blocks.get(ee).addAll(_magblocks);
            for (int val : _blocks.get(ee)) {
                _eblocks.get(ee).add(IsBlockAllowed(val));
            }
        }

        for (int h : _hits) _ehits.add(IsHitAllowed(h));

        GenerateCombinations();

        DoStop = FoeGroup.DoStopNow;
        IsLowHp = FoeGroup.DoStopLowHp && (_percentHp <= FoeGroup.StopLowHp);
        IsLowMa = FoeGroup.DoStopLowMa && (_percentMa <= FoeGroup.StopLowMa);
        // DoExit logic...
        
        if (LezCombinations.size() > 0) {
            LezCombination = LezCombinations.get((int)(Math.random() * LezCombinations.size()));
            BuildResult();
        }

        return true;
    }

    private void SelectFoeGroup() {
        _foeGroupId = 0;
        if (AppVars.Profile == null) {
            FoeGroup = new LezBotsGroup(1, 0);
            return;
        }
        for (LezBotsGroup group : AppVars.Profile.LezGroups) {
            boolean match = false;
            switch (group.Id) {
                case 1: match = true; break;
                case 10: match = _foeName.equals("Человек") && _foeLevel >= group.MinimalLevel; break;
                case 20: match = !_foeName.equals("Человек") && _foeLevel >= group.MinimalLevel; break;
                default:
                    String className = LezBotsClassCollection.getClass(group.Id).name;
                    match = _foeName.equalsIgnoreCase(className) && _foeLevel >= group.MinimalLevel;
                    break;
            }
            if (match) {
                _foeGroupId = group.Id;
                FoeGroup = group.clone();
                break;
            }
        }
        if (FoeGroup == null) FoeGroup = new LezBotsGroup(1, 0);
    }

    private int ZMag(LezBotsGroup group, int code) {
        if (contains(group.SpellsBlocks, code)) return 4;
        if (contains(group.SpellsHits, code)) return 2;
        if (contains(group.SpellsMisc, code)) return 1;
        return 0;
    }

    private int ZRestore(LezBotsGroup group, int code) {
        if (code == 388) return 3;
        if (contains(group.SpellsRestoreHp, code)) return 2;
        if (contains(group.SpellsRestoreMa, code)) return 1;
        return 0;
    }

    private int ZScroll(int code) {
        if (code == 328) return 3;
        if (code == 338) return 2;
        if (code == 277) return 1;
        return 0;
    }

    private void GenerateCombinations() {
        _lezHits.clear();
        _lezHits.add(new LezNode());
        
        _lezBlocks.clear();
        _lezBlocks.add(new LezNode());
        
        _lezMagics.clear();
        _lezMagics.add(new LezNode());

        // 1. Удары (Hits)
        for (int combo = 0; combo < 4; combo++) {
            for (int op = 1; op <= _hits.size(); op++) {
                if (!_ehits.get(op - 1)) continue;
                LezNode node = new LezNode();
                int code = _hits.get(op - 1);
                node.AddHit(combo, op, code);
                if (node.Od(_posod) > _odmax || node.Mana(_posma) > _currentMa) continue;
                _lezHits.add(node);
            }
        }

        // Двойные удары
        for (int c1 = 0; c1 < 3; c1++) {
            for (int op1 = 1; op1 <= _hits.size(); op1++) {
                if (!_ehits.get(op1 - 1)) continue;
                LezNode node1 = new LezNode();
                node1.AddHit(c1, op1, _hits.get(op1 - 1));
                if (node1.Od(_posod) > _odmax || node1.Mana(_posma) > _currentMa) continue;

                for (int c2 = c1 + 1; c2 < 4; c2++) {
                    if (c2 - c1 == 3) continue;
                    for (int op2 = 1; op2 <= _hits.size(); op2++) {
                        if (!_ehits.get(op2 - 1)) continue;
                        LezNode node2 = node1.clone();
                        node2.AddHit(c2, op2, _hits.get(op2 - 1));
                        if (node2.Od(_posod) > _odmax || node2.Mana(_posma) > _currentMa) continue;
                        _lezHits.add(node2);
                    }
                }
            }
        }

        // 2. Блоки (Blocks)
        for (int combo = 0; combo < 4; combo++) {
            for (int op = 1; op <= _blocks.get(combo).size(); op++) {
                if (!_eblocks.get(combo).get(op - 1)) continue;
                LezNode node = new LezNode();
                int code = _blocks.get(combo).get(op - 1);
                if (combo > 0 && !LezSpell.IsPhBlock(code)) continue;
                node.AddBlock(combo, op, code);
                if (node.Od(_posod) > _odmax || node.Mana(_posma) > _currentMa) continue;
                _lezBlocks.add(node);
            }
        }

        // 3. Магия (Magics)
        int magicCount = 0;
        for (boolean e : _emagics) if (e) magicCount++;

        if (magicCount > 0) {
            for (int flag = 0; flag < _magics.size(); flag++) {
                if (_emagics.get(flag)) {
                    int code = _magics.get(flag);
                    LezNode node = new LezNode();
                    node.AddMagic(flag, code, ZMag(FoeGroup, code), ZRestore(FoeGroup, code), ZScroll(code));
                    if (node.Od(_posod) <= _odmax && node.Mana(_posma) <= _currentMa) _lezMagics.add(node);
                }
            }
        }

        // Финальная сборка и выбор лучшей
        for (LezNode hNode : _lezHits) {
            for (LezNode bNode : _lezBlocks) {
                boolean hasNonPhBlock2 = bNode.HasNonPhBlock(FoeGroup);
                for (LezNode mNode : _lezMagics) {
                    if (hasNonPhBlock2 && mNode.HasNonPhBlock(FoeGroup)) continue;
                    
                    LezNode comb = hNode.clone();
                    comb.AddCombination(bNode);
                    comb.AddCombination(mNode);
                    
                    if (comb.Od(_posod) > _odmax || comb.Mana(_posma) > _currentMa) continue;
                    if (!comb.IsValid()) continue;

                    if (LezCombinations.isEmpty()) {
                        LezCombinations.add(comb);
                    } else {
                        int comp = comb.compareTo(LezCombinations.get(0));
                        if (comp < 0) continue;
                        if (comp > 0) LezCombinations.clear();
                        LezCombinations.add(comb);
                    }
                }
            }
        }
    }

    private void BuildResult() {
        StringBuilder sb = new StringBuilder();
        // Формирование строки VCODE|ENEMY|GROUP|...
        sb.append(AppVars.VCode).append("|");
        sb.append(FoeName).append("|");
        sb.append(_foeGroupId).append("|0|0|");
        
        StringBuilder ftr = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (LezCombination.HitOps[i] > 0) {
                int code = LezCombination.HitCodes[i];
                ftr.append(i).append("_").append(code).append("_").append(_posma[code]).append("|");
            }
        }
        sb.append(ftr).append("|");
        sb.append(LezCombination.BlockCombo).append("_").append(LezCombination.BlockOp).append("_").append(LezCombination.BlockCode).append("|");
        
        for (int i = 0; i < 18; i++) if (LezCombination.MagicFlags[i]) sb.append(i).append("|");
        sb.append("|");
        for (int i = 0; i < 18; i++) if (LezCombination.MagicFlags[i]) sb.append(LezCombination.MagicCodes[i]).append("|");
        
        Result = sb.toString();
    }

    private void Selpl(int type, List<Integer> list) {
        for (int i : list) {
            int pos = LezSpellCollection.PosType[i];
            if (type == 0) {
                if (pos == 1) _hits.add(i);
                else if (pos == 2) _magblocks.add(i);
            } else {
                if (pos == 3 || pos == 4 || pos == 5 || pos == 6) {
                    _magics.add(i);
                    _emagics.add(IsMagicAllowed(i));
                }
            }
        }
    }

    private boolean IsHitAllowed(int code) { return FoeGroup.DoHits; }
    private boolean IsBlockAllowed(int code) { return FoeGroup.DoBlocks; }
    private boolean IsMagicAllowed(int code) {
        if (code == 388 || contains(FoeGroup.SpellsRestoreHp, code)) return FoeGroup.DoRestoreHp;
        if (contains(FoeGroup.SpellsRestoreMa, code)) return FoeGroup.DoRestoreMa;
        return FoeGroup.DoMiscAbils;
    }

    private boolean contains(int[] arr, int val) {
        for (int a : arr) if (a == val) return true;
        return false;
    }

    private String[] ParseString(String html, String sarg, int mina) {
        int pos = html.indexOf(sarg);
        if (pos == -1) return null;
        String args = HelperStrings.subString(html, sarg, "]");
        if (args == null) return null;
        String[] pars = args.split(",");
        return pars.length < mina ? null : pars;
    }

    private String Strip(String arg) { return arg.replace("\"", "").trim(); }

    private boolean ParseNonFight() {
        // Логика завершения боя (case "2", case "7" и т.д.)
        return true;
    }
}
