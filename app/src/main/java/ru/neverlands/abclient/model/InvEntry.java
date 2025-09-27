package ru.neverlands.abclient.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

public class InvEntry implements Comparable<InvEntry>, Cloneable {

    public String name = "";
    public String wearLink = "";
    public String dropThing = "";
    public String dropLink = "";
    public String dropPrice = "";
    public String pssThing = "";
    public String pssLink = "";
    public int pssPrice = 0;

    private String img = "";
    private int level = 0;
    private String dolg = "";
    private String properties = "";
    private int dolgOne = 0;
    private int dolgTwo = 0;
    private int countButton = 0;
    private boolean expired = false;
    private boolean expirible = false;
    private Element rawElement;
    private int count = 1;

    public InvEntry(Element element) {
        this.rawElement = element;
        this.count = 1;

        // Parse Wear Link
        Element wearButton = element.selectFirst("input[value=Надеть]");
        if (wearButton != null) {
            String onclick = wearButton.attr("onclick");
            this.wearLink = HelperStrings.subString(onclick, "location='", "'");
        }

        // Parse Sell Link
        Element sellButton = element.selectFirst("input[value^=Продать за]");
        if (sellButton != null) {
            String onclick = sellButton.attr("onclick");
            this.pssThing = HelperStrings.subString(onclick, "продать < ", " >");
            String priceStr = HelperStrings.subString(onclick, "> за ", " NV?");
            this.pssLink = HelperStrings.subString(onclick, "location='", "'");
            if (priceStr != null) {
                try {
                    this.pssPrice = Integer.parseInt(priceStr.trim());
                } catch (NumberFormatException e) { /* ignore */ }
            }
        }

        // Parse Drop Link
        Element dropButton = element.selectFirst("input[src*=del.gif]");
        if (dropButton != null) {
            String onclick = dropButton.attr("onclick");
            this.dropThing = HelperStrings.subString(onclick, "if(top.DeleteTrue('", "'))");
            this.dropLink = HelperStrings.subString(onclick, "{ location='", "' }");
        }

        // Parse Name
        Element nameElement = element.selectFirst("font.nickname > b");
        if (nameElement != null) {
            this.name = nameElement.text().trim();
        }

        // Parse Image
        Element imgElement = element.selectFirst("img[src*=weapon], img[src*=tools], img[src*=resources]");
        if (imgElement != null) {
            this.img = imgElement.attr("src");
        }

        // Parse Properties
        Elements propElements = element.select("font.weaponch");
        StringBuilder propsSb = new StringBuilder();
        for (Element prop : propElements) {
            String propHtml = prop.html();
            if (propHtml.contains("Цена:")) {
                if (this.dropPrice == null || this.dropPrice.isEmpty()) {
                    this.dropPrice = HelperStrings.subString(propHtml, "Цена: <b>", " NV</b>");
                }
                continue;
            }
            if (propHtml.contains("Материал:")) continue;

            if (propHtml.contains("Долговечность:")) {
                this.dolg = HelperStrings.subString(propHtml, "Долговечность: <b>", "</b>");
                if (this.dolg != null) {
                    String[] parts = this.dolg.split("/");
                    if (parts.length == 2) {
                        try {
                            this.dolgOne = Integer.parseInt(parts[0]);
                            this.dolgTwo = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) { /* ignore */ }
                    }
                }
            } else {
                if (propsSb.length() > 0) propsSb.append("|");
                propsSb.append(prop.text());
            }
        }
        this.properties = propsSb.toString();

        // Other fields
        this.countButton = element.select("input[type=button]").size();
    }

    public String build() {
        if (count > 1) {
            Element nameElement = rawElement.selectFirst("font.nickname > b");
            if (nameElement != null) {
                nameElement.append(String.format(Locale.US, " (%d шт.)", count));
            }
        }
        return rawElement.outerHtml();
    }

    public void addBulkSell() {
        if (count <= 1 || pssThing == null || pssThing.isEmpty()) return;
        Element sellButton = rawElement.selectFirst("input[value^=Продать за]");
        if (sellButton != null) {
            String script = String.format(Locale.US,
                    "javascript: if(confirm('Вы точно хотите продать все предметы < %s > по %d NV?')) { window.AndroidBridge.startBulkSell('%s', '%d', '%s'); }",
                    pssThing, pssPrice, pssThing, pssPrice, pssLink);
            Element newButton = new Element("input")
                    .attr("type", "button")
                    .attr("class", "invbut")
                    .attr("onclick", script)
                    .attr("value", String.format(Locale.US, "Продать пачку за %d NV", pssPrice * count));
            sellButton.after(newButton);
        }
    }

    public void addBulkDelete() {
        if (count <= 1 || dropThing == null || dropThing.isEmpty()) return;
        Element dropButton = rawElement.selectFirst("input[src*=del.gif]");
        if (dropButton != null) {
            String script = String.format(Locale.US,
                    "javascript: if(top.DeleteTrue('Пачку')) { window.AndroidBridge.startBulkDrop('%s', '%s'); }",
                    dropThing, dropPrice);
            Element newButton = new Element("input")
                    .attr("type", "image")
                    .attr("src", "http://image.neverlands.ru/del.gif")
                    .attr("width", "14").attr("height", "14").attr("border", "0")
                    .attr("title", "Выбросить всю пачку")
                    .attr("onclick", script);
            dropButton.before(newButton);
        }
    }

    public void inc() { this.count++; }
    public boolean isExpired() { return this.expirible && this.expired; }

    @Override
    public int compareTo(InvEntry other) {
        int result = this.name.compareTo(other.name);
        if (result != 0) return result;

        result = this.img.compareTo(other.img);
        if (result != 0) return result;

        result = Boolean.compare(this.expirible, other.expirible);
        if (result != 0) return result;

        result = Integer.compare(this.level, other.level);
        if (result != 0) return result;

        result = Integer.compare(this.countButton, other.countButton);
        if (result != 0) return result;

        return this.properties.compareTo(other.properties);
    }

    public int compareDolg(InvEntry other) {
        if (other == null) return 0;
        boolean isFull = dolgOne == dolgTwo;
        boolean isFullOther = other.dolgOne == other.dolgTwo;
        int result = Boolean.compare(isFull, isFullOther);
        if (result != 0) return result;

        result = Integer.compare(dolgOne, other.dolgOne);
        if (result != 0) return result;

        return Integer.compare(dolgTwo, other.dolgTwo);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

