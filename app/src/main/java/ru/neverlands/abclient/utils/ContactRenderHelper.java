package ru.neverlands.abclient.utils;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ContactRenderHelper {

    private ContactRenderHelper() {
    }

    public static String formatNickWithLevel(String nick, int level) {
        String safeNick = nick == null ? "" : nick;
        if (level > 0) {
            return safeNick + " [" + level + "]";
        }
        return safeNick;
    }

    public static int resolveNickColor(int classId) {
        if (classId == 1) {
            return Color.RED;
        }
        if (classId == 2) {
            return Color.parseColor("#008000");
        }
        return Color.WHITE;
    }

    /**
     * Определяет, относится ли контакт к нейтралам (без клана).
     */
    public static boolean isNeutralClanName(String clanName) {
        if (clanName == null) {
            return true;
        }
        String normalized = clanName.trim();
        return normalized.isEmpty() || "none".equalsIgnoreCase(normalized);
    }

    /**
     * Единая цветовая политика:
     * - нейтрал (без клана) всегда белый;
     * - остальное по classId (враг/друг/нейтрал).
     */
    public static int resolveNickColor(int classId, String clanName) {
        if (isNeutralClanName(clanName)) {
            return Color.WHITE;
        }
        return resolveNickColor(classId);
    }

    public static String buildEffectIconUrl(int effectId) {
        return "http://image.neverlands.ru/pinfo/eff_" + effectId + ".gif";
    }

    public static List<Integer> parseEffectIdsCsv(String effectIdsCsv) {
        if (effectIdsCsv == null || effectIdsCsv.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = effectIdsCsv.split(",");
        Set<Integer> unique = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(trimmed);
                if (value > 0) {
                    unique.add(value);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(unique);
    }

    public static String toEffectIdsCsv(List<Integer> effectIds) {
        if (effectIds == null || effectIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<Integer> unique = new LinkedHashSet<>(effectIds);
        for (Integer effectId : unique) {
            if (effectId == null || effectId <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(effectId);
        }
        return sb.toString();
    }
}
