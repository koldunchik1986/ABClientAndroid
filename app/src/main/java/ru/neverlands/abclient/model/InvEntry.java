package ru.neverlands.abclient.model;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

import java.util.Locale;

public class InvEntry implements Cloneable, Comparable<InvEntry> {
    public String html;
    public String Dolg;
    public String Name;
    public int Count;
    public String Image;
    public String Id;
    public String Signature;
    public String PssThing;
    public int PssPrice;
    public String PssLink;
    public String DropThing;
    public String DropPrice;
    public String DropLink;
    public boolean isArt;
    public boolean isUniq;
    private String buttonsHtml = "";

    // Поля для реконструкции HTML
    private String rowClass;
    private String imageCellHtml;
    private String infoCellHtml;

    public InvEntry(Element table) {
        this.html = table.outerHtml();
        this.Count = 1;

        // Парсим основные ячейки, чтобы потом их пересобрать
        Elements cells = table.select("> tbody > tr > td");
        if (cells.size() >= 2) {
            this.imageCellHtml = cells.get(0).html();
            this.infoCellHtml = cells.get(1).html();
        }

        Element nameElement = table.selectFirst("b");
        if (nameElement != null) {
            this.Name = nameElement.text();
        }

        Elements imgElements = table.select("img[src^=http://image.neverlands.ru/weapon/]");
        if (!imgElements.isEmpty()) {
            this.Image = imgElements.attr("src");
            if (this.Name == null || this.Name.isEmpty()) {
                this.Name = imgElements.attr("alt");
            }
        }

        this.Dolg = HelperStrings.subString(this.html, "Долговечность: ", "/");

        Elements links = table.select("a[href^=main.php?]");
        for (Element link : links) {
            String href = link.attr("href");
            if (href.contains("act=3")) { // Pss - Sell
                this.PssLink = href;
                this.PssThing = HelperStrings.subString(href, "pss=", "&");
                String priceStr = HelperStrings.subString(href, "price=", "&");
                try {
                    this.PssPrice = Integer.parseInt(priceStr);
                } catch (NumberFormatException e) {
                    this.PssPrice = 0;
                }
            } else if (href.contains("act=2")) { // Drop
                this.DropLink = href;
                this.DropThing = HelperStrings.subString(href, "drop=", "&");
                this.DropPrice = HelperStrings.subString(href, "price=", "&");
            }
        }

        this.isArt = this.html.contains("artefact.gif");
        this.isUniq = this.html.contains("uniq.gif");
    }

    public void inc() {
        this.Count++;
    }

    public void addBulkSell() {
        if (PssPrice > 0 && AppVars.Profile != null && AppVars.Profile.DoButtonSell) {
            String script = String.format(Locale.US,
                "top.frames[\"main_frame\"].location='main.php?get_id=56&act=10&bulk_sell=1&pss=%s&price=%d&vcode=%s';",
                this.PssThing, this.PssPrice, AppVars.VCode);
            this.buttonsHtml += "<input type=button class=lbut style=\"width:100px\" value=\"Продать все\" onclick=\"" + script + "\">";
        }
    }

    public void addBulkDelete() {
        if (DropLink != null && !DropLink.isEmpty() && AppVars.Profile != null && AppVars.Profile.DoButtonDrop) {
            String script = String.format(Locale.US,
                "if(confirm('Вы уверены, что хотите выбросить все предметы &laquo;%s&raquo;? Восстановить их будет невозможно.')) { top.frames[\"main_frame\"].location='main.php?get_id=56&act=10&bulk_drop=1&drop=%s&price=%s&vcode=%s'; }",
                this.Name, this.DropThing, this.DropPrice, AppVars.VCode);
            this.buttonsHtml += "<input type=button class=lbut style=\"width:100px\" value=\"Выбросить все\" onclick=\"" + script + "\">";
        }
    }

    public String build() {
        if (imageCellHtml == null || infoCellHtml == null) {
            return this.html; // Возвращаем оригинал, если парсинг прошел неудачно
        }

        String finalInfoCellHtml = this.infoCellHtml;
        if (this.Count > 1) {
            finalInfoCellHtml = finalInfoCellHtml.replace("<b>" + this.Name + "</b>", "<b>" + this.Name + " (x" + this.Count + ")</b>");
        }

        String buttonsRow = "";
        if (!this.buttonsHtml.isEmpty()) {
            buttonsRow = "<tr><td colspan=2 align=right>" + this.buttonsHtml + "</td></tr>";
        }

        // Собираем HTML строки таблицы заново
        return "<tr><td bgcolor=#F5F5F5>" + imageCellHtml + "</td><td width=100% bgcolor=#FFFFFF valign=top><table cellpadding=0 cellspacing=0 border=0 width=100%>" + finalInfoCellHtml + buttonsRow + "</table></td></tr>";
    }

    @Override
    public int compareTo(InvEntry other) {
        if (this.Name == null || other.Name == null) return 1;
        if (!this.Name.equals(other.Name)) return this.Name.compareTo(other.Name);
        if (this.isArt != other.isArt) return 1;
        if (this.isUniq != other.isUniq) return 1;
        if (this.Dolg == null && other.Dolg == null) return 0;
        if (this.Dolg == null || other.Dolg == null) return 1;
        if (!this.Dolg.equals(other.Dolg)) return 1;
        return 0; // They are the same
    }

    public int compareDolg(InvEntry other) {
        if (this.Dolg == null || other.Dolg == null) return 0;
        try {
            int dolg1 = Integer.parseInt(this.Dolg);
            int dolg2 = Integer.parseInt(other.Dolg);
            return Integer.compare(dolg1, dolg2);
        } catch (NumberFormatException e) {
            return this.Dolg.compareTo(other.Dolg);
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
