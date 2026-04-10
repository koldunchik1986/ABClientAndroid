package ru.neverlands.abclient.postfilter;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;

/**
 * Специализированный парсер инвентаря (страница main.php?act=inv).
 * 
 * Модуль {@link InventoryParser} занимается парсингом данных из HTML инвентаря:
 * - Списком доступных комплектов (из вызовов compl_view())
 * - Сохранением комплектов в профиль для использования в диалогах (например, таймеры)
 * 
 * Зависимости:
 * - {@link AppVars#Profile} для сохранения комплектов;
 * - {@link FileLogger} для логирования.
 * 
 * Вызов из {@link MainPhp}:
 * - {@link #parseAndSaveComplectsFromInventory(String)} — при загрузке инвентаря.
 */
public final class InventoryParser {
    private static final String TAG = "InventoryParser";

    /**
     * Парсит список доступных комплектов из HTML инвентаря (compl_view вызовы).
     * 
     * Формат в HTML: compl_view("название", "uid", "vcode")
     * 
     * Сохраняет список в Profile.SavedComplectsList для использования в диалогах таймеров.
     * 
     * @param html HTML инвентаря (из main.php?act=inv)
     */
    public static void parseAndSaveComplectsFromInventory(String html) {
        if (html == null || html.isEmpty() || AppVars.Profile == null) {
            return;
        }
        
        List<String> complectNames = new ArrayList<>();
        
        // Парсим все вызовы compl_view("название", "uid", "vcode")
        int startIdx = 0;
        while (true) {
            final String marker = "compl_view(\"";
            startIdx = html.indexOf(marker, startIdx);
            if (startIdx == -1) break;
            
            startIdx += marker.length();
            int endNameIdx = html.indexOf("\"", startIdx);
            if (endNameIdx == -1) break;
            
            String complectName = html.substring(startIdx, endNameIdx);
            if (!complectName.isEmpty() && !complectNames.contains(complectName)) {
                complectNames.add(complectName);
                String msg_found = "COMPLECT_INV_PARSE_TRACE: found complect=\"" + complectName + "\"";
                AppLog.d(TAG, TAG, msg_found);
            }
            
            startIdx = endNameIdx;
        }
        
        // Сохраняем список в профилю
        if (!complectNames.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < complectNames.size(); i++) {
                if (i > 0) sb.append("|");
                sb.append(complectNames.get(i));
            }
            AppVars.Profile.SavedComplectsList = sb.toString();
            AppVars.Profile.save(AppVars.getContext());
            
            String msg = "COMPLECT_PARSE: saved " + complectNames.size() + " complects: " + AppVars.Profile.SavedComplectsList;
            AppLog.d(TAG, TAG, msg);
        }
    }
}
