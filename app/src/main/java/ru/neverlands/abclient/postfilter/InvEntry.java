package ru.neverlands.abclient.postfilter;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;

public class InvEntry implements Comparable<InvEntry>, Cloneable {
    private String name;
    private String img;
    private String id;
    private String uid;
    private String dolg;
    private String text;
    private int count;
    private boolean isArt;
    private boolean isUniq;
    private boolean isGift;
    private boolean isSet;
    private boolean isQuest;
    private boolean isRepa;
    private boolean isPersonal;
    private boolean isSost;
    private boolean isCanDrop;
    private boolean isCanSell;
    private boolean isCanGive;
    private boolean isCanRepair;
    private boolean isCanModify;
    private boolean isCanSharpen;
    private boolean isCanCurse;
    private boolean isCanUncurse;
    private boolean isCanUnbind;
    private boolean isCanInsert;
    private boolean isCanExtract;
    private boolean isCanSplit;
    private boolean isCanMerge;
    private boolean isCanUpgrade;
    private boolean isCanEnchant;
    private boolean isCanDisenchant;
    private boolean isCanPaint;
    private boolean isCanUnpaint;
    private boolean isCanSocket;
    private boolean isCanUnsocket;
    private boolean isCanRune;
    private boolean isCanUnrune;
    private boolean isCanEngrave;
    private boolean isCanUnengrave;
    private boolean isCanSymbol;
    private boolean isCanUnsymbol;
    private boolean isCanBless;
    private boolean isCanUnbless;
    private boolean isCanCurse2;
    private boolean isCanUncurse2;
    private boolean isCanCurse3;
    private boolean isCanUncurse3;
    private boolean isCanCurse4;
    private boolean isCanUncurse4;
    private boolean isCanCurse5;
    private boolean isCanUncurse5;
    private boolean isCanCurse6;
    private boolean isCanUncurse6;
    private boolean isCanCurse7;
    private boolean isCanUncurse7;
    private boolean isCanCurse8;
    private boolean isCanUncurse8;
    private boolean isCanCurse9;
    private boolean isCanUncurse9;
    private boolean isCanCurse10;
    private boolean isCanUncurse10;
    private boolean isCanCurse11;
    private boolean isCanUncurse11;
    private boolean isCanCurse12;
    private boolean isCanUncurse12;
    private boolean isCanCurse13;
    private boolean isCanUncurse13;
    private boolean isCanCurse14;
    private boolean isCanUncurse14;
    private boolean isCanCurse15;
    private boolean isCanUncurse15;
    private boolean isCanCurse16;
    private boolean isCanUncurse16;
    private boolean isCanCurse17;
    private boolean isCanUncurse17;
    private boolean isCanCurse18;
    private boolean isCanUncurse18;
    private boolean isCanCurse19;
    private boolean isCanUncurse19;
    private boolean isCanCurse20;
    private boolean isCanUncurse20;
    private boolean isCanCurse21;
    private boolean isCanUncurse21;
    private boolean isCanCurse22;
    private boolean isCanUncurse22;
    private boolean isCanCurse23;
    private boolean isCanUncurse23;
    private boolean isCanCurse24;
    private boolean isCanUncurse24;
    private boolean isCanCurse25;
    private boolean isCanUncurse25;
    private boolean isCanCurse26;
    private boolean isCanUncurse26;
    private boolean isCanCurse27;
    private boolean isCanUncurse27;
    private boolean isCanCurse28;
    private boolean isCanUncurse28;
    private boolean isCanCurse29;
    private boolean isCanUncurse29;
    private boolean isCanCurse30;
    private boolean isCanUncurse30;
    private boolean isCanCurse31;
    private boolean isCanUncurse31;
    private boolean isCanCurse32;
    private boolean isCanUncurse32;
    private boolean isCanCurse33;
    private boolean isCanUncurse33;
    private boolean isCanCurse34;
    private boolean isCanUncurse34;
    private boolean isCanCurse35;
    private boolean isCanUncurse35;
    private boolean isCanCurse36;
    private boolean isCanUncurse36;
    private boolean isCanCurse37;
    private boolean isCanUncurse37;
    private boolean isCanCurse38;
    private boolean isCanUncurse38;
    private boolean isCanCurse39;
    private boolean isCanUncurse39;
    private boolean isCanCurse40;
    private boolean isCanUncurse40;
    private boolean isCanCurse41;
    private boolean isCanUncurse41;
    private boolean isCanCurse42;
    private boolean isCanUncurse42;
    private boolean isCanCurse43;
    private boolean isCanUncurse43;
    private boolean isCanCurse44;
    private boolean isCanUncurse44;
    private boolean isCanCurse45;
    private boolean isCanUncurse45;
    private boolean isCanCurse46;
    private boolean isCanUncurse46;
    private boolean isCanCurse47;
    private boolean isCanUncurse47;
    private boolean isCanCurse48;
    private boolean isCanUncurse48;
    private boolean isCanCurse49;
    private boolean isCanUncurse49;
    private boolean isCanCurse50;
    private boolean isCanUncurse50;

    private Element body;

    public InvEntry(Element body) {
        this.body = body;
        this.count = 1;
        parse();
    }

    private void parse() {
        Elements elements = body.select("a[href^=javascript:close_msg(']");
        if (elements.size() > 0) {
            String href = elements.first().attr("href");
            String[] parts = href.split("'");
            if (parts.length > 1) {
                this.id = parts[1];
            }
        }

        elements = body.select("img[src^=http://image.neverlands.ru/weapon/]");
        if (elements.size() > 0) {
            this.img = elements.first().attr("src");
        }

        elements = body.select("b");
        if (elements.size() > 0) {
            this.name = elements.first().text();
        }

        this.text = body.html();
    }

    public void inc() {
        this.count++;
    }

    public void addBulkSell() {
        UserConfig profile = AppVars.Profile;
        if (profile == null || !profile.DoButtonSell) {
            return;
        }

        // TODO: Implement logic to add bulk sell button
    }

    public void addBulkDelete() {
        UserConfig profile = AppVars.Profile;
        if (profile == null || !profile.DoButtonDrop) {
            return;
        }

        // TODO: Implement logic to add bulk delete button
    }

    public String build() {
        if (count > 1) {
            String newName = name + " (" + count + ")";
            body.select("b").first().text(newName);
        }
        return body.html();
    }

    @Override
    public int compareTo(InvEntry other) {
        if (this.isArt != other.isArt) return this.isArt ? -1 : 1;
        if (this.isUniq != other.isUniq) return this.isUniq ? -1 : 1;
        if (this.isGift != other.isGift) return this.isGift ? -1 : 1;
        if (this.isSet != other.isSet) return this.isSet ? -1 : 1;
        if (this.isQuest != other.isQuest) return this.isQuest ? -1 : 1;
        if (this.isRepa != other.isRepa) return this.isRepa ? -1 : 1;
        if (this.isPersonal != other.isPersonal) return this.isPersonal ? -1 : 1;
        if (this.isSost != other.isSost) return this.isSost ? -1 : 1;
        if (this.isCanDrop != other.isCanDrop) return this.isCanDrop ? -1 : 1;
        if (this.isCanSell != other.isCanSell) return this.isCanSell ? -1 : 1;
        if (this.isCanGive != other.isCanGive) return this.isCanGive ? -1 : 1;
        if (this.isCanRepair != other.isCanRepair) return this.isCanRepair ? -1 : 1;
        if (this.isCanModify != other.isCanModify) return this.isCanModify ? -1 : 1;
        if (this.isCanSharpen != other.isCanSharpen) return this.isCanSharpen ? -1 : 1;
        if (this.isCanCurse != other.isCanCurse) return this.isCanCurse ? -1 : 1;
        if (this.isCanUncurse != other.isCanUncurse) return this.isCanUncurse ? -1 : 1;
        if (this.isCanUnbind != other.isCanUnbind) return this.isCanUnbind ? -1 : 1;
        if (this.isCanInsert != other.isCanInsert) return this.isCanInsert ? -1 : 1;
        if (this.isCanExtract != other.isCanExtract) return this.isCanExtract ? -1 : 1;
        if (this.isCanSplit != other.isCanSplit) return this.isCanSplit ? -1 : 1;
        if (this.isCanMerge != other.isCanMerge) return this.isCanMerge ? -1 : 1;
        if (this.isCanUpgrade != other.isCanUpgrade) return this.isCanUpgrade ? -1 : 1;
        if (this.isCanEnchant != other.isCanEnchant) return this.isCanEnchant ? -1 : 1;
        if (this.isCanDisenchant != other.isCanDisenchant) return this.isCanDisenchant ? -1 : 1;
        if (this.isCanPaint != other.isCanPaint) return this.isCanPaint ? -1 : 1;
        if (this.isCanUnpaint != other.isCanUnpaint) return this.isCanUnpaint ? -1 : 1;
        if (this.isCanSocket != other.isCanSocket) return this.isCanSocket ? -1 : 1;
        if (this.isCanUnsocket != other.isCanUnsocket) return this.isCanUnsocket ? -1 : 1;
        if (this.isCanRune != other.isCanRune) return this.isCanRune ? -1 : 1;
        if (this.isCanUnrune != other.isCanUnrune) return this.isCanUnrune ? -1 : 1;
        if (this.isCanEngrave != other.isCanEngrave) return this.isCanEngrave ? -1 : 1;
        if (this.isCanUnengrave != other.isCanUnengrave) return this.isCanUnengrave ? -1 : 1;
        if (this.isCanSymbol != other.isCanSymbol) return this.isCanSymbol ? -1 : 1;
        if (this.isCanUnsymbol != other.isCanUnsymbol) return this.isCanUnsymbol ? -1 : 1;
        if (this.isCanBless != other.isCanBless) return this.isCanBless ? -1 : 1;
        if (this.isCanUnbless != other.isCanUnbless) return this.isCanUnbless ? -1 : 1;
        if (this.isCanCurse2 != other.isCanCurse2) return this.isCanCurse2 ? -1 : 1;
        if (this.isCanUncurse2 != other.isCanUncurse2) return this.isCanUncurse2 ? -1 : 1;
        if (this.isCanCurse3 != other.isCanCurse3) return this.isCanCurse3 ? -1 : 1;
        if (this.isCanUncurse3 != other.isCanUncurse3) return this.isCanUncurse3 ? -1 : 1;
        if (this.isCanCurse4 != other.isCanCurse4) return this.isCanCurse4 ? -1 : 1;
        if (this.isCanUncurse4 != other.isCanUncurse4) return this.isCanUncurse4 ? -1 : 1;
        if (this.isCanCurse5 != other.isCanCurse5) return this.isCanCurse5 ? -1 : 1;
        if (this.isCanUncurse5 != other.isCanUncurse5) return this.isCanUncurse5 ? -1 : 1;
        if (this.isCanCurse6 != other.isCanCurse6) return this.isCanCurse6 ? -1 : 1;
        if (this.isCanUncurse6 != other.isCanUncurse6) return this.isCanUncurse6 ? -1 : 1;
        if (this.isCanCurse7 != other.isCanCurse7) return this.isCanCurse7 ? -1 : 1;
        if (this.isCanUncurse7 != other.isCanUncurse7) return this.isCanUncurse7 ? -1 : 1;
        if (this.isCanCurse8 != other.isCanCurse8) return this.isCanCurse8 ? -1 : 1;
        if (this.isCanUncurse8 != other.isCanUncurse8) return this.isCanUncurse8 ? -1 : 1;
        if (this.isCanCurse9 != other.isCanCurse9) return this.isCanCurse9 ? -1 : 1;
        if (this.isCanUncurse9 != other.isCanUncurse9) return this.isCanUncurse9 ? -1 : 1;
        if (this.isCanCurse10 != other.isCanCurse10) return this.isCanCurse10 ? -1 : 1;
        if (this.isCanUncurse10 != other.isCanUncurse10) return this.isCanUncurse10 ? -1 : 1;
        if (this.isCanCurse11 != other.isCanCurse11) return this.isCanCurse11 ? -1 : 1;
        if (this.isCanUncurse11 != other.isCanUncurse11) return this.isCanUncurse11 ? -1 : 1;
        if (this.isCanCurse12 != other.isCanCurse12) return this.isCanCurse12 ? -1 : 1;
        if (this.isCanUncurse12 != other.isCanUncurse12) return this.isCanUncurse12 ? -1 : 1;
        if (this.isCanCurse13 != other.isCanCurse13) return this.isCanCurse13 ? -1 : 1;
        if (this.isCanUncurse13 != other.isCanUncurse13) return this.isCanUncurse13 ? -1 : 1;
        if (this.isCanCurse14 != other.isCanCurse14) return this.isCanCurse14 ? -1 : 1;
        if (this.isCanUncurse14 != other.isCanUncurse14) return this.isCanUncurse14 ? -1 : 1;
        if (this.isCanCurse15 != other.isCanCurse15) return this.isCanCurse15 ? -1 : 1;
        if (this.isCanUncurse15 != other.isCanUncurse15) return this.isCanUncurse15 ? -1 : 1;
        if (this.isCanCurse16 != other.isCanCurse16) return this.isCanCurse16 ? -1 : 1;
        if (this.isCanUncurse16 != other.isCanUncurse16) return this.isCanUncurse16 ? -1 : 1;
        if (this.isCanCurse17 != other.isCanCurse17) return this.isCanCurse17 ? -1 : 1;
        if (this.isCanUncurse17 != other.isCanUncurse17) return this.isCanUncurse17 ? -1 : 1;
        if (this.isCanCurse18 != other.isCanCurse18) return this.isCanCurse18 ? -1 : 1;
        if (this.isCanUncurse18 != other.isCanUncurse18) return this.isCanUncurse18 ? -1 : 1;
        if (this.isCanCurse19 != other.isCanCurse19) return this.isCanCurse19 ? -1 : 1;
        if (this.isCanUncurse19 != other.isCanUncurse19) return this.isCanUncurse19 ? -1 : 1;
        if (this.isCanCurse20 != other.isCanCurse20) return this.isCanCurse20 ? -1 : 1;
        if (this.isCanUncurse20 != other.isCanUncurse20) return this.isCanUncurse20 ? -1 : 1;
        if (this.isCanCurse21 != other.isCanCurse21) return this.isCanCurse21 ? -1 : 1;
        if (this.isCanUncurse21 != other.isCanUncurse21) return this.isCanUncurse21 ? -1 : 1;
        if (this.isCanCurse22 != other.isCanCurse22) return this.isCanCurse22 ? -1 : 1;
        if (this.isCanUncurse22 != other.isCanUncurse22) return this.isCanUncurse22 ? -1 : 1;
        if (this.isCanCurse23 != other.isCanCurse23) return this.isCanCurse23 ? -1 : 1;
        if (this.isCanUncurse23 != other.isCanUncurse23) return this.isCanUncurse23 ? -1 : 1;
        if (this.isCanCurse24 != other.isCanCurse24) return this.isCanCurse24 ? -1 : 1;
        if (this.isCanUncurse24 != other.isCanUncurse24) return this.isCanUncurse24 ? -1 : 1;
        if (this.isCanCurse25 != other.isCanCurse25) return this.isCanCurse25 ? -1 : 1;
        if (this.isCanUncurse25 != other.isCanUncurse25) return this.isCanUncurse25 ? -1 : 1;
        if (this.isCanCurse26 != other.isCanCurse26) return this.isCanCurse26 ? -1 : 1;
        if (this.isCanUncurse26 != other.isCanUncurse26) return this.isCanUncurse26 ? -1 : 1;
        if (this.isCanCurse27 != other.isCanCurse27) return this.isCanCurse27 ? -1 : 1;
        if (this.isCanUncurse27 != other.isCanUncurse27) return this.isCanUncurse27 ? -1 : 1;
        if (this.isCanCurse28 != other.isCanCurse28) return this.isCanCurse28 ? -1 : 1;
        if (this.isCanUncurse28 != other.isCanUncurse28) return this.isCanUncurse28 ? -1 : 1;
        if (this.isCanCurse29 != other.isCanCurse29) return this.isCanCurse29 ? -1 : 1;
        if (this.isCanUncurse29 != other.isCanUncurse29) return this.isCanUncurse29 ? -1 : 1;
        if (this.isCanCurse30 != other.isCanCurse30) return this.isCanCurse30 ? -1 : 1;
        if (this.isCanUncurse30 != other.isCanUncurse30) return this.isCanUncurse30 ? -1 : 1;
        if (this.isCanCurse31 != other.isCanCurse31) return this.isCanCurse31 ? -1 : 1;
        if (this.isCanUncurse31 != other.isCanUncurse31) return this.isCanUncurse31 ? -1 : 1;
        if (this.isCanCurse32 != other.isCanCurse32) return this.isCanCurse32 ? -1 : 1;
        if (this.isCanUncurse32 != other.isCanUncurse32) return this.isCanUncurse32 ? -1 : 1;
        if (this.isCanCurse33 != other.isCanCurse33) return this.isCanCurse33 ? -1 : 1;
        if (this.isCanUncurse33 != other.isCanUncurse33) return this.isCanUncurse33 ? -1 : 1;
        if (this.isCanCurse34 != other.isCanCurse34) return this.isCanCurse34 ? -1 : 1;
        if (this.isCanUncurse34 != other.isCanUncurse34) return this.isCanUncurse34 ? -1 : 1;
        if (this.isCanCurse35 != other.isCanCurse35) return this.isCanCurse35 ? -1 : 1;
        if (this.isCanUncurse35 != other.isCanUncurse35) return this.isCanUncurse35 ? -1 : 1;
        if (this.isCanCurse36 != other.isCanCurse36) return this.isCanCurse36 ? -1 : 1;
        if (this.isCanUncurse36 != other.isCanUncurse36) return this.isCanUncurse36 ? -1 : 1;
        if (this.isCanCurse37 != other.isCanCurse37) return this.isCanCurse37 ? -1 : 1;
        if (this.isCanUncurse37 != other.isCanUncurse37) return this.isCanUncurse37 ? -1 : 1;
        if (this.isCanCurse38 != other.isCanCurse38) return this.isCanCurse38 ? -1 : 1;
        if (this.isCanUncurse38 != other.isCanUncurse38) return this.isCanUncurse38 ? -1 : 1;
        if (this.isCanCurse39 != other.isCanCurse39) return this.isCanCurse39 ? -1 : 1;
        if (this.isCanUncurse39 != other.isCanUncurse39) return this.isCanUncurse39 ? -1 : 1;
        if (this.isCanCurse40 != other.isCanCurse40) return this.isCanCurse40 ? -1 : 1;
        if (this.isCanUncurse40 != other.isCanUncurse40) return this.isCanUncurse40 ? -1 : 1;
        if (this.isCanCurse41 != other.isCanCurse41) return this.isCanCurse41 ? -1 : 1;
        if (this.isCanUncurse41 != other.isCanUncurse41) return this.isCanUncurse41 ? -1 : 1;
        if (this.isCanCurse42 != other.isCanCurse42) return this.isCanCurse42 ? -1 : 1;
        if (this.isCanUncurse42 != other.isCanUncurse42) return this.isCanUncurse42 ? -1 : 1;
        if (this.isCanCurse43 != other.isCanCurse43) return this.isCanCurse43 ? -1 : 1;
        if (this.isCanUncurse43 != other.isCanUncurse43) return this.isCanUncurse43 ? -1 : 1;
        if (this.isCanCurse44 != other.isCanCurse44) return this.isCanCurse44 ? -1 : 1;
        if (this.isCanUncurse44 != other.isCanUncurse44) return this.isCanUncurse44 ? -1 : 1;
        if (this.isCanCurse45 != other.isCanCurse45) return this.isCanCurse45 ? -1 : 1;
        if (this.isCanUncurse45 != other.isCanUncurse45) return this.isCanUncurse45 ? -1 : 1;
        if (this.isCanCurse46 != other.isCanCurse46) return this.isCanCurse46 ? -1 : 1;
        if (this.isCanUncurse46 != other.isCanUncurse46) return this.isCanUncurse46 ? -1 : 1;
        if (this.isCanCurse47 != other.isCanCurse47) return this.isCanCurse47 ? -1 : 1;
        if (this.isCanUncurse47 != other.isCanUncurse47) return this.isCanUncurse47 ? -1 : 1;
        if (this.isCanCurse48 != other.isCanCurse48) return this.isCanCurse48 ? -1 : 1;
        if (this.isCanUncurse48 != other.isCanUncurse48) return this.isCanUncurse48 ? -1 : 1;
        if (this.isCanCurse49 != other.isCanCurse49) return this.isCanCurse49 ? -1 : 1;
        if (this.isCanUncurse49 != other.isCanUncurse49) return this.isCanUncurse49 ? -1 : 1;
        if (this.isCanCurse50 != other.isCanCurse50) return this.isCanCurse50 ? -1 : 1;
        if (this.isCanUncurse50 != other.isCanUncurse50) return this.isCanUncurse50 ? -1 : 1;

        return this.name.compareTo(other.name);
    }

    public int compareDolg(InvEntry other) {
        if (this.dolg == null && other.dolg == null) return 0;
        if (this.dolg == null) return -1;
        if (other.dolg == null) return 1;
        return this.dolg.compareTo(other.dolg);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
