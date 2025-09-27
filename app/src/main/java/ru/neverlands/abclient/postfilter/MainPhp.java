package ru.neverlands.abclient.postfilter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.neverlands.abclient.model.InvComparer;
import ru.neverlands.abclient.model.InvEntry;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class MainPhp {
    public static byte[] process(String address, byte[] array) {
        AppVars.IdleTimer = System.currentTimeMillis();
        AppVars.LastMainPhp = System.currentTimeMillis();
        AppVars.ContentMainPhp = null;

        String html = Russian.getString(array);
        html = Filter.removeDoctype(html);

        // TODO: Port the full logic from all MainPhp*.cs partial classes here.

        // Placeholder for fight logic
        if (html.contains("magic_slots();")) {
            html = mainPhpFight(html);
        }

        // Placeholder for inventory logic
        if (html.contains("/invent/0.gif")) {
            html = mainPhpInv(html);
        }

        if (html.contains("var map = [[")) {
            html = MapAjax.process(html);
        }

        // ... other placeholders ...

        AppVars.ContentMainPhp = html;
        return Russian.getBytes(html);
    }

    private static String mainPhpFight(String html) {
        // TODO: Port MainPhpFight.cs
        return html;
    }

    private static String mainPhpInv(String html) {
        Document doc = Jsoup.parse(html);
        Elements itemTables = doc.select("tr:has(td > table)"); // A bit fragile, but should work

        if (itemTables.isEmpty()) {
            return html;
        }

        List<InvEntry> invList = new ArrayList<>();
        Element inventoryContainer = null;

        for (Element table : itemTables) {
            if (table.html().contains("Срок годности")) { // Heuristic to find inventory items
                if (inventoryContainer == null) {
                    inventoryContainer = table.parent();
                }
                invList.add(new InvEntry(table));
            }
        }

        if (inventoryContainer == null) {
            return html;
        }

        // Packing logic
        if (AppVars.Profile != null && AppVars.Profile.DoInvPack) {
            for (int i = 0; i < invList.size() - 1; i++) {
                for (int j = i + 1; j < invList.size(); j++) {
                    if (invList.get(i).compareTo(invList.get(j)) == 0) {
                        if (invList.get(i).compareDolg(invList.get(j)) > 0) {
                            invList.set(i, invList.get(j));
                        }
                        invList.get(i).inc();
                        invList.remove(j);
                        j--;
                    }
                }
            }
        }

        // Add custom buttons
        for (InvEntry entry : invList) {
            entry.addBulkSell();
            entry.addBulkDelete();
        }

        // Sorting logic
        if (AppVars.Profile != null && AppVars.Profile.DoInvSort) {
            Collections.sort(invList, new InvComparer());
        }

        // Rebuild HTML
        StringBuilder newHtml = new StringBuilder();
        for (InvEntry entry : invList) {
            newHtml.append(entry.build());
        }

        inventoryContainer.html(newHtml.toString());
        return doc.outerHtml();
    }
}
