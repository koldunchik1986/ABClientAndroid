package ru.neverlands.abclient.lez;

import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.neverlands.abclient.manager.UnderAttackManager;
import ru.neverlands.abclient.model.*;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Логика ведения боя.
 * Портировано из LezFight.cs.
 */
public class LezFight {
    // Парсер состава боя из var logs (имена и уровни участников).
    private static final Pattern LOG_MEMBER_PATTERN = Pattern.compile("\\[1,\\d+,\"([^\"]+)\",(\\d+)");
    // Дедуп только для обновления урона `LastBoiUron`.
    // Важно: это НЕ маркер завершения боя. Маркер завершения (`LastBoiEndLog`)
    // используется MainPhp/ChatFilter для счётчика поединков и не должен выставляться здесь.
    private static volatile String lastDamageLogId = "";
    public boolean IsValid;
    public boolean IsBoi;
    public boolean IsWaitingForNextTurn;
    public boolean DoStop;
    public boolean DoExit;
    public boolean IsLowHp;
    public boolean IsLowMa;
    public boolean IsFoeDead; // true если враг мёртв (HP <= 0)
    public int FoeCurrentHp; // HP врага для отладки
    public int FoeMaxHp;     // Макс HP врага
    public int FoeLevel;     // Уровень врага
    public String LogBoi = "";
    public String FoeName = "";

    private String _html;
    private String[] _fightty;
    private String[] _fexp;
    private int _ftype;
    private int _currentHp, _maxHp;
    private int _currentMa, _maxMa;
    private int _percentHp, _percentMa;
    private int _foeCurrentHp, _foeMaxHp;
    private int _foeCurrentMa, _foeMaxMa;
    private String _foeImage, _foeName;
    private int _foeLevel, _foeGroupId;
    public LezBotsGroup FoeGroup;
    private int _magmax, _odmax, _hitval, _bs;
    private int[] _posod;
    private int[] _posma;
    private String[] _bspar;
    private boolean _hitByScroll;
    
    // Random для анти-детекта (имитация поведения реального игрока)
    private static final Random _random = new Random();
    
    // Данные для генерации Frame
    private String[] _fightpm;
    private String _vcode;
    private String _levbot;
    private int[] _alchemy;
    
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

    // Конструктор: сразу парсит HTML боя и готовит состояние.
    public LezFight(String html) {
        _html = html;
        IsValid = Parse(html);
    }

    // Основной разбор HTML боя: fight_ty/param_ow/stand_in/magic_in и т.д.
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
        // Важно: ожидание хода противника не должно зависеть от IsBoi.
        // Если fight_ty[3]=='0', сервер ещё не принимает наш submit удара — нужно ждать/обновлять кадр.
        // Предыдущая версия с `IsBoi && ... == '0'` делала флаг всегда false и вызывала "пустые" авто-удары.
        IsWaitingForNextTurn = (_fightty[3].length() >= 1) && (_fightty[3].charAt(0) == '0');
        android.util.Log.d("LezFight", "fight_ty[3]=" + (_fightty[3].length() > 0 ? _fightty[3].charAt(0) : '?')
                + ", IsBoi=" + IsBoi + ", IsWaitingForNextTurn=" + IsWaitingForNextTurn);

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
        String[] fexp = ParseString(html, "var fexp = [", 0);

        if (paramen == null || slotsen == null || fightpm == null) return false;

        // Сохраняем данные для Frame
        _fightpm = fightpm;
        
        // VCode для submit-цепочки берём только из текущего fight_pm[4].
        // Зависимости:
        // - BuildResult() и BuildFrame() отправляют это значение в `vcode`.
        // - сервер валидирует vcode по текущему кадру боя, поэтому кэш из AppVars здесь недопустим.
        if (fightpm.length > 4) {
            _vcode = Strip(fightpm[4]);
        } else {
            _vcode = "";
        }
        
        _levbot = Strip(paramen[5]);

        android.util.Log.d("LezFight", "fight_pm: magmax=" + fightpm[0] + ", odmax=" + fightpm[1] + ", hitval=" + fightpm[2] + ", vcode=" + _vcode.substring(0, Math.min(8, _vcode.length())));

        // Парсим alchemy
        String[] alchemyArr = ParseString(html, "var alchemy = [", 0);
        if (alchemyArr != null && alchemyArr.length > 0) {
            _alchemy = new int[alchemyArr.length];
            for (int i = 0; i < alchemyArr.length; i++) {
                try { _alchemy[i] = Integer.parseInt(Strip(alchemyArr[i])); } catch (Exception e) { _alchemy[i] = 0; }
            }
        } else {
            _alchemy = new int[18];
        }

        FoeName = Strip(paramen[0]);
        _foeName = FoeName;
        try { _foeLevel = Integer.parseInt(Strip(paramen[5])); } catch (Exception e) { _foeLevel = 33; }
        
        // Парсим HP и MA противника
        try {
            _foeCurrentHp = (int) Double.parseDouble(Strip(paramen[1]));
            _foeMaxHp = (int) Double.parseDouble(Strip(paramen[2]));
            _foeCurrentMa = (int) Double.parseDouble(Strip(paramen[3]));
            _foeMaxMa = (int) Double.parseDouble(Strip(paramen[4]));
        } catch (Exception e) {
            _foeCurrentHp = 0;
            _foeMaxHp = 0;
            _foeCurrentMa = 0;
            _foeMaxMa = 0;
        }
        
        // Сохраняем HP врага в публичное поле для отладки
        FoeCurrentHp = _foeCurrentHp;
        FoeMaxHp = _foeMaxHp;
        FoeLevel = _foeLevel;
        
        // Проверяем, мёртв ли враг
        IsFoeDead = (_foeCurrentHp <= 0);
        
        _foeImage = Strip(slotsen[0]);

        if (!_foeImage.startsWith("bot") && !_foeImage.startsWith("_xneto") && !_foeImage.startsWith("_xsilf")) {
            _foeName = "Человек";
        }

        android.util.Log.d("LezFight", "Foe: name=" + _foeName + ", level=" + _foeLevel + ", image=" + _foeImage);

        SelectFoeGroup();

        android.util.Log.d("LezFight", "FoeGroup selected: Id=" + FoeGroup.Id + ", DoHits=" + FoeGroup.DoHits + ", DoBlocks=" + FoeGroup.DoBlocks + ", DoMiscAbils=" + FoeGroup.DoMiscAbils);

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
        android.util.Log.d("LezFight", "stand_in parsed: " + lstandin);

        Selpl(0, lstandin);

        List<Integer> lmagicin = new ArrayList<>();
        if (magicin != null) {
            for (String s : magicin) {
                try { lmagicin.add(Integer.parseInt(Strip(s))); } catch (Exception ignored) {}
            }
        }
        android.util.Log.d("LezFight", "magic_in parsed: " + lmagicin);

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

        android.util.Log.d("LezFight", "GenerateCombinations: combinations count = " + LezCombinations.size());

        DoStop = FoeGroup.DoStopNow;
        IsLowHp = FoeGroup.DoStopLowHp && (_percentHp <= FoeGroup.StopLowHp);
        IsLowMa = FoeGroup.DoStopLowMa && (_percentMa <= FoeGroup.StopLowMa);
        // C# parity: аварийный выход из рискованного PvP.
        // Условия совпадают с LezFight.cs: человек-цель + тип боя >= 80 + включённый DoExitRisky.
        DoExit = FoeGroup.DoExitRisky && _ftype >= 80 && "Человек".equals(_foeName);

        // Защита сценария "на нас напали": не применять stop/exit-ветки, если это не наша инициатива.
        // Зависимость: UnderAttackManager хранит контекст источника атаки между фильтрами боя.
        if ((DoStop || IsLowHp || IsLowMa || DoExit)
                && UnderAttackManager.isHumanFight()
                && !UnderAttackManager.isMeAttacker()) {
            DoStop = false;
            IsLowHp = false;
            IsLowMa = false;
            DoExit = false;
        }
        
        if (LezCombinations.size() > 0) {
            LezCombination = LezCombinations.get((int)(Math.random() * LezCombinations.size()));
            BuildResult();
            BuildFrame();
        }

        return true;
    }

    // Выбор группы противника (настройки авто‑боя по уровням/типам).
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
                case 21: match = IsBoss(); break;
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

    /**
     * Генерация доступных комбинаций удара/блока/магии по правилам группы.
     *
     * Пайплайн 1:1 с C# `LezFight.GenerateCombinations()`:
     * 1) собрать валидные hit-узлы (1/2/3 удара),
     * 2) собрать block-узлы,
     * 3) собрать magic-узлы (1/2/3 каста с anti-conflict ограничениями),
     * 4) склеить и отобрать лучший набор через `compareTo`.
     *
     * Ключевые зависимости:
     * - `LezNode` (расчёт OD/Mana/валидности и приоритет комбинации),
     * - `FoeGroup` (правила и доступные спеллы),
     * - `_posod/_posma/_odmax/_currentMa` (лимиты ресурсов текущего хода),
     * - `LezSpellCollection` + `LezSpell` (классификация кода действия).
     */
    private void GenerateCombinations() {
        LezCombinations.clear();
        _lezHits.clear();
        _lezHits.add(new LezNode());

        _lezBlocks.clear();
        _lezBlocks.add(new LezNode());

        _lezMagics.clear();
        _lezMagics.add(new LezNode());

        // 1) Одиночные удары (4 боевые зоны).
        for (int combo = 0; combo < 4; combo++) {
            for (int op = 1; op <= _hits.size(); op++) {
                if (!_ehits.get(op - 1)) continue;

                LezNode hit = new LezNode();
                int code = _hits.get(op - 1);
                hit.AddHit(combo, op, code);
                if (hit.Od(_posod) > _odmax || hit.Mana(_posma) > _currentMa) continue;

                _lezHits.add(hit);
            }
        }

        // 2) Двойные удары (с ограничением на недопустимые пары зон).
        for (int combo1 = 0; combo1 < 3; combo1++) {
            for (int op1 = 1; op1 <= _hits.size(); op1++) {
                if (!_ehits.get(op1 - 1)) continue;

                LezNode hit = new LezNode();
                int code1 = _hits.get(op1 - 1);
                hit.AddHit(combo1, op1, code1);
                if (hit.Od(_posod) > _odmax || hit.Mana(_posma) > _currentMa) continue;

                for (int combo2 = combo1 + 1; combo2 < 4; combo2++) {
                    if (combo2 - combo1 == 3) continue;

                    for (int op2 = 1; op2 <= _hits.size(); op2++) {
                        if (!_ehits.get(op2 - 1)) continue;

                        LezNode hit2 = hit.clone();
                        int code2 = _hits.get(op2 - 1);
                        hit2.AddHit(combo2, op2, code2);
                        if (hit2.Od(_posod) > _odmax || hit2.Mana(_posma) > _currentMa) continue;

                        _lezHits.add(hit2);
                    }
                }
            }
        }

        // 3) Тройные удары (последовательные зоны).
        for (int combo1 = 0; combo1 < 2; combo1++) {
            for (int op1 = 1; op1 <= _hits.size(); op1++) {
                if (!_ehits.get(op1 - 1)) continue;

                LezNode hit = new LezNode();
                int code1 = _hits.get(op1 - 1);
                hit.AddHit(combo1, op1, code1);
                if (hit.Od(_posod) > _odmax || hit.Mana(_posma) > _currentMa) continue;

                int combo2 = combo1 + 1;
                for (int op2 = 1; op2 <= _hits.size(); op2++) {
                    if (!_ehits.get(op2 - 1)) continue;

                    LezNode hit2 = hit.clone();
                    int code2 = _hits.get(op2 - 1);
                    hit2.AddHit(combo2, op2, code2);
                    if (hit2.Od(_posod) > _odmax || hit2.Mana(_posma) > _currentMa) continue;

                    int combo3 = combo2 + 1;
                    for (int op3 = 1; op3 <= _hits.size(); op3++) {
                        if (!_ehits.get(op3 - 1)) continue;

                        LezNode hit3 = hit2.clone();
                        int code3 = _hits.get(op3 - 1);
                        hit3.AddHit(combo3, op3, code3);
                        if (hit3.Od(_posod) > _odmax || hit3.Mana(_posma) > _currentMa) continue;

                        _lezHits.add(hit3);
                    }
                }
            }
        }

        // 4) Блоки: первый слот допускает всё, остальные только физблок.
        for (int combo = 0; combo < 4; combo++) {
            for (int op = 1; op <= _blocks.get(combo).size(); op++) {
                if (!_eblocks.get(combo).get(op - 1)) continue;

                LezNode block = new LezNode();
                int code = _blocks.get(combo).get(op - 1);
                if (combo > 0 && !LezSpell.IsPhBlock(code)) continue;

                block.AddBlock(combo, op, code);
                if (block.Od(_posod) > _odmax || block.Mana(_posma) > _currentMa) continue;

                _lezBlocks.add(block);
            }
        }

        // 5) Магия: считаем реально кликабельные кнопки текущего кадра.
        int magicClickablesCount = MagicClickablesCount();
        if (magicClickablesCount > 0) {
            for (int flag = 0; flag < _magics.size(); flag++) {
                if (_emagics.get(flag)) {
                    int code = _magics.get(flag);
                    LezNode magic = new LezNode();
                    magic.AddMagic(flag, code, ZMag(FoeGroup, code), ZRestore(FoeGroup, code), ZScroll(code));
                    if (magic.Od(_posod) > _odmax || magic.Mana(_posma) > _currentMa) continue;

                    _lezMagics.add(magic);
                }
            }
        }

        // 6) Пары магий с фильтрацией конфликтов (двойной блок, 388 + restoreHp и т.п.).
        if (magicClickablesCount > 1) {
            for (int flag1 = 0; flag1 < _magics.size() - 1; flag1++) {
                if (_emagics.get(flag1)) {
                    int code1 = _magics.get(flag1);
                    LezNode magic = new LezNode();
                    magic.AddMagic(flag1, code1, ZMag(FoeGroup, code1), ZRestore(FoeGroup, code1), ZScroll(code1));
                    if (magic.Od(_posod) > _odmax || magic.Mana(_posma) > _currentMa) continue;

                    for (int flag2 = flag1 + 1; flag2 < _magics.size(); flag2++) {
                        if (_emagics.get(flag2)) {
                            int code2 = _magics.get(flag2);
                            if ((code1 == 388 && contains(FoeGroup.SpellsRestoreHp, code2))
                                    || (code2 == 388 && contains(FoeGroup.SpellsRestoreHp, code1))) {
                                continue;
                            }

                            if (contains(FoeGroup.SpellsBlocks, code1) && contains(FoeGroup.SpellsBlocks, code2)) {
                                continue;
                            }

                            LezNode magic2 = magic.clone();
                            magic2.AddMagic(flag2, code2, ZMag(FoeGroup, code2), ZRestore(FoeGroup, code2), ZScroll(code2));
                            if (magic2.Od(_posod) > _odmax || magic2.Mana(_posma) > _currentMa) continue;

                            _lezMagics.add(magic2);
                        }
                    }
                }
            }
        }

        // 7) Тройки магий с теми же конфликтными ограничениями.
        if (magicClickablesCount > 2) {
            for (int flag1 = 0; flag1 < _magics.size() - 2; flag1++) {
                if (_emagics.get(flag1)) {
                    int code1 = _magics.get(flag1);
                    LezNode magic = new LezNode();
                    magic.AddMagic(flag1, code1, ZMag(FoeGroup, code1), ZRestore(FoeGroup, code1), ZScroll(code1));
                    if (magic.Od(_posod) > _odmax || magic.Mana(_posma) > _currentMa) continue;

                    for (int flag2 = flag1 + 1; flag2 < _magics.size() - 1; flag2++) {
                        if (_emagics.get(flag2)) {
                            int code2 = _magics.get(flag2);
                            if ((code1 == 388 && contains(FoeGroup.SpellsRestoreHp, code2))
                                    || (code2 == 388 && contains(FoeGroup.SpellsRestoreHp, code1))) {
                                continue;
                            }

                            if (contains(FoeGroup.SpellsBlocks, code1) && contains(FoeGroup.SpellsBlocks, code2)) {
                                continue;
                            }

                            LezNode magic2 = magic.clone();
                            magic2.AddMagic(flag2, code2, ZMag(FoeGroup, code2), ZRestore(FoeGroup, code2), ZScroll(code2));
                            if (magic2.Od(_posod) > _odmax || magic2.Mana(_posma) > _currentMa) continue;

                            for (int flag3 = flag2 + 1; flag3 < _magics.size(); flag3++) {
                                if (_emagics.get(flag3)) {
                                    int code3 = _magics.get(flag3);
                                    if ((code1 == 388 && contains(FoeGroup.SpellsRestoreHp, code3))
                                            || (code2 == 388 && contains(FoeGroup.SpellsRestoreHp, code3))
                                            || (code3 == 388 && contains(FoeGroup.SpellsRestoreHp, code1))
                                            || (code3 == 388 && contains(FoeGroup.SpellsRestoreHp, code2))) {
                                        continue;
                                    }

                                    if ((contains(FoeGroup.SpellsBlocks, code1) && contains(FoeGroup.SpellsBlocks, code3))
                                            || (contains(FoeGroup.SpellsBlocks, code2) && contains(FoeGroup.SpellsBlocks, code3))) {
                                        continue;
                                    }

                                    LezNode magic3 = magic2.clone();
                                    magic3.AddMagic(flag3, code3, ZMag(FoeGroup, code3), ZRestore(FoeGroup, code3), ZScroll(code3));
                                    if (magic3.Od(_posod) > _odmax || magic3.Mana(_posma) > _currentMa) continue;

                                    _lezMagics.add(magic3);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8) Финальная декартова сборка hit+block+magic и отбор лучшего(их) варианта.
        for (int ihits = 0; ihits < _lezHits.size(); ihits++) {
            LezNode combination = new LezNode();
            combination.AddCombination(_lezHits.get(ihits));
            for (int iblocks = 0; iblocks < _lezBlocks.size(); iblocks++) {
                boolean hasNonPhBlock2 = _lezBlocks.get(iblocks).HasNonPhBlock(FoeGroup);

                LezNode combination2 = combination.clone();
                combination2.AddCombination(_lezBlocks.get(iblocks));
                if (combination2.Od(_posod) > _odmax || combination2.Mana(_posma) > _currentMa) continue;

                for (int imagic = 0; imagic < _lezMagics.size(); imagic++) {
                    if (hasNonPhBlock2) {
                        boolean hasNonPhBlock3 = _lezMagics.get(imagic).HasNonPhBlock(FoeGroup);
                        if (hasNonPhBlock3) continue;
                    }

                    LezNode combination3 = combination2.clone();
                    combination3.AddCombination(_lezMagics.get(imagic));
                    if (combination3.Od(_posod) > _odmax || combination3.Mana(_posma) > _currentMa) continue;
                    if (!combination3.IsValid()) continue;

                    if (LezCombinations.isEmpty()) {
                        LezCombinations.add(combination3);
                    } else {
                        int compare = combination3.compareTo(LezCombinations.get(0));
                        if (compare < 0) continue;
                        if (compare > 0) LezCombinations.clear();
                        LezCombinations.add(combination3);
                    }
                }
            }
        }
    }

    // Сбор строки Result для AutoSubmit (VCODE|ENEMY|GROUP|...).
    // Должен полностью соответствовать C# LezFight.cs:
    // vcode|enemy|group|inf_bot|lev_bot|ftr|inu|inb|ina
    // Dependencies for BuildResult():
    // - WebAppInterface.processFightHtml(...) forwards Result to JS AutoSubmit.
    // - FightJs.AutoSubmit(...) parses exact "vcode|enemy|group|inf_bot|lev_bot|ftr|inu|inb|ina" format.
    // - Any delimiter/order mismatch stops server-side hit processing.
    private void BuildResult() {
        String vcode = _vcode != null ? _vcode : "";
        // C# parity: enemy/group/inf_bot берутся ИСХОДНЫМИ значениями из fight_pm (без Strip).
        String enemy = _fightpm.length > 5 ? _fightpm[5] : "";
        String group = _fightpm.length > 6 ? _fightpm[6] : "";
        String infbot = _fightpm.length > 7 ? _fightpm[7] : "";
        String ftrRaw = (_fightty != null && _fightty.length > 2) ? _fightty[2] : String.valueOf(_ftype);

        StringBuilder inputu = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (LezCombination.HitOps[i] > 0) {
                int code = LezCombination.HitCodes[i];
                inputu.append(i).append("_").append(code).append("_").append(_posma[code]).append("@");
            }
        }

        StringBuilder inputb = new StringBuilder();
        if (LezCombination.BlockOp > 0) {
            // В C# второй компонент — именно BlockCode, а не BlockOp.
            inputb.append(LezCombination.BlockCombo)
                    .append("_")
                    .append(LezCombination.BlockCode)
                    .append("_")
                    .append(_posma[LezCombination.BlockCode])
                    .append("@");
        }

        StringBuilder inputa = new StringBuilder();
        for (int i = 0; i < LezCombination.MagicFlags.length; i++) {
            if (!LezCombination.MagicFlags[i]) continue;
            int code = LezCombination.MagicCodes[i];
            int posType = LezSpellCollection.PosType[code];
            if (posType <= 2) continue;

            inputa.append(code);
            if (posType > 3 && _alchemy != null && i < _alchemy.length) {
                inputa.append("_").append(_alchemy[i]);
            }
            inputa.append("@");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(vcode).append("|")
                .append(enemy).append("|")
                .append(group).append("|")
                .append(infbot).append("|")
                .append(_levbot).append("|")
                .append(ftrRaw).append("|")
                .append(inputu).append("|")
                .append(inputb).append("|")
                .append(inputa);
        Result = sb.toString();
    }

    // Формирование минимального fight.Frame для автобоя (инфо + auto-submit).
    // Dependencies for BuildFrame():
    // - MainPhp.mainPhpFight(...) returns this lightweight frame in AutoboiOn mode.
    // - Server expects post_id=7 and exact inu/inb/ina field formats.
    private void BuildFrame() {
        if (_fightpm == null || _fightpm.length < 11) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ABClient</title></head><body><!--ABCLIENT_GENERATED-->");
        
        // Заголовок с информацией о противнике
        sb.append("<b>").append(FoeName).append("</b> [").append(_foeLevel).append("] [<font color=#bb0000><b>");
        sb.append(_foeCurrentHp).append("</b>/<b>").append(_foeMaxHp);
        sb.append("</b></font> | <font color=#336699><b>");
        sb.append(_foeCurrentMa).append("</b>/<b>").append(_foeMaxMa).append("</b></font>]<br>");
        
        // Собираем hidden-форму удара (аналог ПК версии) и шлём её через submit().
        int delay = 1000 + _random.nextInt(501); // 1.0–1.5s
        sb.append("<form action=\"main.php\" method=POST name=ff>");
        
        sb.append("<input name=post_id type=hidden value=\"7\">");
        
        // C# parity: поля enemy/group/inf_bot/inf_zb отправляются raw из fight_pm.
        String enemy = _fightpm.length > 5 ? _fightpm[5] : "";
        String group = _fightpm.length > 6 ? _fightpm[6] : "";
        String infbot = _fightpm.length > 7 ? _fightpm[7] : "";
        String infzb = _fightpm.length > 10 ? _fightpm[10] : "";
        String ftrRaw = (_fightty != null && _fightty.length > 2) ? _fightty[2] : String.valueOf(_ftype);
        
        sb.append("<input name=vcode type=hidden value=\"").append(_vcode).append("\">");
        sb.append("<input name=enemy type=hidden value=\"").append(enemy).append("\">");
        sb.append("<input name=group type=hidden value=\"").append(group).append("\">");
        sb.append("<input name=inf_bot type=hidden value=\"").append(infbot).append("\">");
        sb.append("<input name=inf_zb type=hidden value=\"").append(infzb).append("\">");
        sb.append("<input name=lev_bot type=hidden value=\"").append(_levbot).append("\">");
        sb.append("<input name=ftr type=hidden value=\"").append(ftrRaw).append("\">");
        
        StringBuilder inu = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (LezCombination.HitOps[i] > 0) {
                int code = LezCombination.HitCodes[i];
                inu.append(i).append("_").append(code).append("_").append(_posma[code]).append("@");
            }
        }
        sb.append("<input name=inu type=hidden value=\"").append(inu.toString()).append("\">");
        
        // Keep C#-compatible inb format: BlockCombo_BlockCode_posma@.
        String inb = LezCombination.BlockOp > 0 ?
            LezCombination.BlockCombo + "_" + LezCombination.BlockCode + "_" + _posma[LezCombination.BlockCode] + "@" : "";
        sb.append("<input name=inb type=hidden value=\"").append(inb).append("\">");
        
        StringBuilder ina = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            if (LezCombination.MagicFlags[i]) {
                int code = LezCombination.MagicCodes[i];
                int posType = LezSpellCollection.PosType[code];
                if (posType > 2) {
                    ina.append(code);
                    if (posType > 3 && _alchemy != null && i < _alchemy.length) {
                        ina.append("_").append(_alchemy[i]);
                    }
                    ina.append("@");
                }
            }
        }
        sb.append("<input name=ina type=hidden value=\"").append(ina.toString()).append("\">");

        sb.append("</form>");
        android.util.Log.d("LezFight", "BuildFrame payload: enemy=" + enemy
                + ", group=" + group
                + ", inf_bot=" + infbot
                + ", inf_zb=" + infzb
                + ", lev_bot=" + _levbot
                + ", ftr=" + ftrRaw
                + ", inu=" + inu
                + ", inb=" + inb
                + ", ina=" + ina);
        sb.append("<script language=\"JavaScript\">");
        sb.append("setTimeout(function(){ console.log('ABCLIENT_AUTOBATTLE_SUBMIT'); document.ff.submit(); }, ").append(delay).append(");");
        sb.append("setTimeout(function(){ window.location.href='main.php?get_id=56&act=10&go=inf'; }, ").append(delay + 5000).append(");");
        sb.append("</script></body></html>");

        Frame = sb.toString();
        android.util.Log.d("LezFight", "BuildFrame: Frame generated, length=" + Frame.length() + ", delay=" + delay + "ms");
    }

    /**
     * Раскладывает коды действий по внутренним коллекциям `_hits/_magblocks/_magics`.
     *
     * @param type источник списка: `0` = stand_in, `1` = magic_in
     * @param list список action-кодов из JS-массивов текущего кадра
     *
     * Зависимости:
     * - `LezSpellCollection.PosType` определяет класс действия (hit/block/magic),
     * - `IsMagicAllowed()` определяет флаг кликабельности магии (`_emagics`).
     */
    private void Selpl(int type, List<Integer> list) {
        for (int i : list) {
            if (i < 0 || i >= LezSpellCollection.PosType.length) continue;
            int pos = LezSpellCollection.PosType[i];
            if (pos == 1) {
                _hits.add(i);
                if (type == 1) {
                    _magics.add(i);
                    _emagics.add(false);
                }
            } else if (pos == 2) {
                _magblocks.add(i);
                if (type == 1) {
                    _magics.add(i);
                    _emagics.add(false);
                }
            } else {
                if (pos == 3 || pos == 4) {
                    _magics.add(i);
                    _emagics.add(IsMagicAllowed(i));
                }
            }
        }
    }

    /**
     * Возвращает число маг-кнопок, которые разрешены к использованию на этом ходу.
     * Зависимости: `_emagics`, который заполняется в `Selpl()` через `IsMagicAllowed()`.
     */
    private int MagicClickablesCount() {
        int count = 0;
        for (boolean c : _emagics) {
            if (c) count++;
        }
        return count;
    }

    /**
     * Проверка разрешения удара по настройкам группы противника.
     * Зависимости: `LezSpell` (тип удара), `FoeGroup.DoHits/DoMagHits/DoAbilHits`, `FoeGroup.SpellsHits`.
     */
    private boolean IsHitAllowed(int code) {
        if (LezSpell.IsPhHit(code) && FoeGroup.DoHits) return true;
        if (LezSpell.IsMagHit(code) && FoeGroup.DoMagHits) return true;
        if (contains(FoeGroup.SpellsHits, code) && FoeGroup.DoAbilHits) return true;
        return false;
    }

    /**
     * Проверка разрешения блока по настройкам группы противника.
     * Зависимости: `LezSpell` (тип блока), `FoeGroup.DoBlocks/DoMagBlocks/DoAbilBlocks`, `FoeGroup.SpellsBlocks`.
     */
    private boolean IsBlockAllowed(int code) {
        if (LezSpell.IsPhBlock(code) && FoeGroup.DoBlocks) return true;
        if (LezSpell.IsMagBlock(code) && FoeGroup.DoMagBlocks) return true;
        if (contains(FoeGroup.SpellsBlocks, code) && FoeGroup.DoAbilBlocks) return true;
        return false;
    }

    /**
     * Проверка разрешения магии/абилки.
     *
     * Логика совпадает с C#:
     * - restore HP/MA разрешаются только при включённом флаге и достижении порога,
     * - блок/удар/прочие абилки проверяются через списки группы,
     * - scroll hit и код 328 исключаются из авто-каста.
     *
     * Зависимости: `FoeGroup` (флаги + пороги + списки спеллов), `_currentHp/_maxHp/_currentMa/_maxMa`.
     */
    private boolean IsMagicAllowed(int code) {
        if (contains(FoeGroup.SpellsRestoreHp, code)) {
            if (FoeGroup.DoRestoreHp && _maxHp > 0) {
                int php = (int) (_currentHp * 100.0 / _maxHp);
                if (php <= FoeGroup.RestoreHp) return true;
            }
            return false;
        }

        if (contains(FoeGroup.SpellsRestoreMa, code)) {
            if (FoeGroup.DoRestoreMa && _maxMa > 0) {
                int pma = (int) (_currentMa * 100.0 / _maxMa);
                if (pma <= FoeGroup.RestoreMa) return true;
            }
            return false;
        }

        if (contains(FoeGroup.SpellsBlocks, code)) return FoeGroup.DoAbilBlocks;
        if (contains(FoeGroup.SpellsHits, code)) return FoeGroup.DoAbilHits;
        if (contains(FoeGroup.SpellsMisc, code)) return FoeGroup.DoMiscAbils;
        if (LezSpell.IsScrollHit(code)) return false;
        if (code == 328) return false;

        return false;
    }

    /**
     * Null-safe проверка вхождения кода в C#-подобных int[] списках настроек группы.
     */
    private boolean contains(int[] arr, int val) {
        if (arr == null) return false;
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

    // Обработка небоевого состояния (ожидание хода/завершение боя).
    private boolean ParseNonFight() {
        // Состояния вне активного хода (ожидание, окончание боя и т.п.).
        IsWaitingForNextTurn = false;
        AppVars.CodeAddress = "";
        try {
            String state = (_fightty != null && _fightty.length > 4) ? Strip(_fightty[4]) : "";
            switch (state) {
                case "2": {
                    _fexp = ParseString(_html, "var fexp = [", 0);
                    if (_fexp == null || _fexp.length < 14) {
                        return false;
                    }
                    String captchaToken = Strip(_fexp[4]);
                    String captchaFlag = (_fexp.length > 6) ? Strip(_fexp[6]) : "";
                    boolean needManualCaptcha = captchaToken.length() > 2 && "0".equals(captchaFlag);
                    if (needManualCaptcha) {
                        AppVars.CodeAddress = "http://neverlands.ru/modules/code/code.php?" + captchaToken;
                        BuildFightLink(true);
                    } else {
                        BuildFightLink(false);
                    }
                    break;
                }
                case "3": {
                    String vcode = (_fightty != null && _fightty.length > 6) ? Strip(_fightty[6]) : "";
                    if (vcode.length() > 2) {
                        String group = (_fightty != null && _fightty.length > 7) ? Strip(_fightty[7]) : "";
                        String mode = (AppVars.Profile != null && AppVars.Profile.LezDoWinTimeout) ? "1" : "0";
                        AppVars.FightLink = "main.php?get_id=61&act=6&mode=" + mode
                                + "&gr=" + group
                                + "&vcode=" + vcode;
                    } else {
                        // Ожидание хода противника (аналог C# ParseNonFight() case "3").
                        IsWaitingForNextTurn = true;
                    }
                    break;
                }
                case "4":
                    // Ждём окончания боя (в C# тут только уведомление в Tray).
                    break;
                case "5": {
                    String vcode = (_fightty != null && _fightty.length > 5) ? Strip(_fightty[5]) : "";
                    AppVars.FightLink = "main.php?get_id=61&act=5&vcode=" + vcode;
                    break;
                }
                case "7": {
                    String st = (_fightty != null && _fightty.length > 4) ? Strip(_fightty[4]) : "";
                    String vcode = (_fightty != null && _fightty.length > 5) ? Strip(_fightty[5]) : "";
                    AppVars.FightLink = "main.php?get_id=61&act=5&st=" + st + "&vcode=" + vcode;
                    break;
                }
                default:
                    break;
            }

            updateLastBoiDamageIfNeeded();
        } catch (Exception ignored) {
        }

        return true;
    }

    /**
     * Аналог блока в конце C# ParseNonFight():
     * считает суммарный урон из `var list = [[...]]` и запоминает его для статистики.
     */
    private void updateLastBoiDamageIfNeeded() {
        if (LogBoi == null || LogBoi.isEmpty() || LogBoi.equals(lastDamageLogId)) {
            return;
        }
        String[] list = ParseString(_html, "var list = [[", 0);
        if (list == null || list.length <= 10) {
            return;
        }
        int damage = 0;
        for (int idx = 6; idx <= 10; idx++) {
            damage += parseIntSafe(list[idx], 0);
        }
        AppVars.LastBoiUron = String.valueOf(damage);
        lastDamageLogId = LogBoi;
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(Strip(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
    
    // Сбор ссылки "Завершить бой" из fexp (аналог C#).
    private void BuildFightLink(boolean withCaptchaPlaceholder) {
        if (_fexp == null || _fexp.length < 14) return;
        
        try {
            String fexp0 = Strip(_fexp[0]);
            String fexp1 = Strip(_fexp[1]);
            String fexp3 = Strip(_fexp[3]);
            String fexp5 = Strip(_fexp[5]);
            String fexp8 = Strip(_fexp[8]);
            String fexp9 = Strip(_fexp[9]);
            String fexp10 = Strip(_fexp[10]);
            String fexp11 = Strip(_fexp[11]);
            String fexp12 = Strip(_fexp[12]);
            String fexp13 = Strip(_fexp[13]);
            
            String fightLink = (withCaptchaPlaceholder ? "main.php?code=????&get_id=61&act=7&fexp=" : "main.php?get_id=61&act=7&fexp=") + fexp0 +
                "&fres=" + fexp1 +
                "&vcode=" + fexp3 +
                "&min1=" + fexp8 +
                "&max1=" + fexp9 +
                "&min2=" + fexp10 +
                "&max2=" + fexp11 +
                "&sum1=" + fexp12 +
                "&sum2=" + fexp13 +
                "&ftype=" + fexp5;
            
            AppVars.FightLink = fightLink;
            android.util.Log.d("LezFight", "BuildFightLink(" + (withCaptchaPlaceholder ? "captcha" : "normal")
                    + "): " + fightLink + ", codeAddress=" + AppVars.CodeAddress);
        } catch (Exception e) {
            android.util.Log.e("LezFight", "BuildFightLink error: " + e.getMessage());
        }
    }

    /**
     * Аналог C# CalcRestoreAfterBoi().
     * Возвращает время готовности в миллисекундах или 0, если восстановление не требуется.
     */
    public long calcRestoreAfterBoiReadyAtMs() {
        if (AppVars.Profile == null) {
            return 0L;
        }

        double sec = 0.0;
        double intHp = AppVars.PersIntHP > 0 ? AppVars.PersIntHP : 2000.0;
        double intMa = AppVars.PersIntMA > 0 ? AppVars.PersIntMA : 9000.0;

        if (AppVars.Profile.LezDoWaitHp && _maxHp > 0 && _percentHp < AppVars.Profile.LezWaitHp) {
            int goalHp = (int) (AppVars.Profile.LezWaitHp * _maxHp / 100.0);
            sec = ((goalHp - _currentHp) * intHp) / _maxHp;
        }

        if (AppVars.Profile.LezDoWaitMa && _maxMa > 0 && _percentMa < AppVars.Profile.LezWaitMa) {
            int goalMa = (int) (AppVars.Profile.LezWaitMa * _maxMa / 100.0);
            double secMa = ((goalMa - _currentMa) * intMa) / _maxMa;
            if (secMa > sec) {
                sec = secMa;
            }
        }

        if (sec < 1.0) {
            return 0L;
        }
        long delayMs = (long) Math.ceil(sec * 1000.0);
        return System.currentTimeMillis() + Math.max(0L, delayMs);
    }

    /**
     * Текущее HP игрока, распарсенное из `param_my`.
     * Зависимости: используется в MainPhp для строки статуса лечения.
     */
    public int getCurrentHp() {
        return _currentHp;
    }

    /** Максимальное HP игрока. */
    public int getMaxHp() {
        return _maxHp;
    }

    /** Текущее MA игрока. */
    public int getCurrentMa() {
        return _currentMa;
    }

    /** Максимальное MA игрока. */
    public int getMaxMa() {
        return _maxMa;
    }

    /** Текущий процент HP (0..100). */
    public int getPercentHp() {
        return _percentHp;
    }

    /** Текущий процент MA (0..100). */
    public int getPercentMa() {
        return _percentMa;
    }

    /**
     * Аналог C# проверки ftype >= 80 — опасный противник (человек или высокий ftype).
     */
    /**
     * Обновляет AppVars.LastBoiSostav/LastBoiTravm на основе логов боя.
     * Аналог ParseFightLog в C#.
     */
    // Парсит var logs и обновляет состав/травм. (LastBoiSostav/LastBoiTravm).
    public void updateLastBoiFromLogs() {
        try {
            String fightty2 = (_fightty != null && _fightty.length > 2) ? Strip(_fightty[2]) : "";
            AppVars.LastBoiSostav = "";
            AppVars.LastBoiTravm = "";
            AppVars.LastBoiTimer = new Date();

            String ftmppic = "";
            String ftmp = "";
            switch (fightty2) {
                case "10":
                    ftmppic = "4";
                    ftmp = "низкий";
                    break;
                case "30":
                    ftmppic = "3";
                    ftmp = "средний";
                    break;
                case "50":
                    ftmppic = "2";
                    ftmp = "высокий";
                    break;
                case "80":
                case "100":
                    ftmppic = "1";
                    ftmp = "оч. высокий";
                    break;
                case "110":
                    ftmppic = "0";
                    ftmp = "травма";
                    break;
            }

            if (!ftmppic.isEmpty()) {
                AppVars.LastBoiTravm =
                        "<img src=http://image.neverlands.ru/gameplay/injury" +
                                ftmppic +
                                ".gif alt=\"% травматичности: " +
                                ftmp +
                                "\" width=17 height=17 align=absmiddle>";
            }

            String log1 = HelperStrings.subString(_html, "var logs = ", ";");
            if (log1 == null) return;

            int start = log1.indexOf("\"Бой между\"");
            if (start < 0) return;
            start += "\"Бой между\"".length();
            int end = log1.indexOf("\" начался", start);
            if (end < 0) return;

            String between = log1.substring(start, end);
            int splitIdx = between.indexOf("\" и\",");
            int splitLen = 0;
            if (splitIdx >= 0) {
                splitLen = 5;
            } else {
                splitIdx = between.indexOf("\" и\"");
                if (splitIdx >= 0) {
                    splitLen = 4;
                }
            }

            List<String> opponents;
            String myNick = (AppVars.Profile != null && AppVars.Profile.UserNick != null)
                    ? AppVars.Profile.UserNick
                    : "";

            if (splitIdx >= 0) {
                String left = between.substring(0, splitIdx);
                String right = between.substring(splitIdx + splitLen);

                List<String> leftMembers = extractMembers(left);
                List<String> rightMembers = extractMembers(right);

                boolean leftHasMe = containsNick(leftMembers, myNick);
                boolean rightHasMe = containsNick(rightMembers, myNick);
                opponents = (leftHasMe && !rightHasMe) ? rightMembers
                        : (rightHasMe && !leftHasMe) ? leftMembers
                        : leftMembers;
            } else {
                opponents = extractMembers(between);
                if (!myNick.isEmpty()) {
                    opponents.removeIf(m -> m != null && m.startsWith(myNick + "["));
                }
            }

            if (opponents != null && !opponents.isEmpty()) {
                AppVars.LastBoiSostav = joinMembers(opponents);
            }
        } catch (Exception e) {
            Log.e("LezFight", "updateLastBoiFromLogs error: " + e.getMessage());
        }
    }

    private List<String> extractMembers(String source) {
        List<String> result = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;
        Matcher matcher = LOG_MEMBER_PATTERN.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            String level = matcher.group(2);
            if (name != null && !name.isEmpty() && level != null && !level.isEmpty()) {
                result.add(name + "[" + level + "]");
            }
        }
        return result;
    }

    private boolean containsNick(List<String> members, String nick) {
        if (members == null || members.isEmpty() || nick == null || nick.isEmpty()) return false;
        for (String member : members) {
            if (member != null && member.startsWith(nick + "[")) {
                return true;
            }
        }
        return false;
    }

    private String joinMembers(List<String> members) {
        StringBuilder sb = new StringBuilder();
        for (String member : members) {
            if (member == null || member.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(member);
        }
        return sb.toString();
    }

    public boolean IsDangerousFoe() {
        return _ftype >= 80;
    }

    /**
     * Аналог C# IsBossName() — проверяет, является ли противник боссом.
     */
    public boolean IsBoss() {
        return _foeName != null && (
            _foeName.equals("Королева Змей") ||
            _foeName.equals("Хранитель Леса") ||
            _foeName.equals("Громлех Синезубый") ||
            _foeName.equals("Выползень")
        );
    }
}
