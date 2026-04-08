package ru.neverlands.abclient.utils;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContactRenderHelper {

    public static final class EffectState {
        public final int id;
        public final int count;
        public final String timeout;

        public EffectState(int id, int count, String timeout) {
            this.id = id;
            this.count = count <= 0 ? 1 : count;
            this.timeout = timeout == null ? "" : timeout.trim();
        }
    }

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

    public static List<EffectState> parseEffectStatesCsv(String effectStatesCsv, String fallbackEffectIdsCsv) {
        LinkedHashMap<Integer, EffectState> byId = new LinkedHashMap<>();
        if (effectStatesCsv != null && !effectStatesCsv.trim().isEmpty()) {
            String[] entries = effectStatesCsv.split(",");
            for (String entry : entries) {
                if (entry == null) {
                    continue;
                }
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split(":", 3);
                int id = parseIntSafe(parts.length > 0 ? parts[0] : "", 0);
                if (id <= 0) {
                    continue;
                }
                int count = parseIntSafe(parts.length > 1 ? parts[1] : "", 1);
                String timeout = parts.length > 2 ? sanitizeEffectStatePart(parts[2]) : "";
                EffectState existing = byId.get(id);
                if (existing == null) {
                    byId.put(id, new EffectState(id, count, timeout));
                } else {
                    int mergedCount = Math.max(1, existing.count) + Math.max(1, count);
                    String mergedTimeout = existing.timeout.isEmpty() ? timeout : existing.timeout;
                    byId.put(id, new EffectState(id, mergedCount, mergedTimeout));
                }
            }
        }
        if (!byId.isEmpty()) {
            return new ArrayList<>(byId.values());
        }
        List<Integer> fallbackIds = parseEffectIdsCsv(fallbackEffectIdsCsv);
        for (Integer id : fallbackIds) {
            if (id != null && id > 0) {
                byId.put(id, new EffectState(id, 1, ""));
            }
        }
        return new ArrayList<>(byId.values());
    }

    public static List<Integer> extractEffectIds(List<EffectState> effectStates) {
        List<Integer> ids = new ArrayList<>();
        if (effectStates == null || effectStates.isEmpty()) {
            return ids;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        for (EffectState state : effectStates) {
            if (state == null || state.id <= 0) {
                continue;
            }
            unique.add(state.id);
        }
        ids.addAll(unique);
        return ids;
    }

    public static String toEffectStatesCsv(List<EffectState> effectStates) {
        if (effectStates == null || effectStates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<Integer, EffectState> byId = new LinkedHashMap<>();
        for (EffectState state : effectStates) {
            if (state == null || state.id <= 0) {
                continue;
            }
            EffectState existing = byId.get(state.id);
            if (existing == null) {
                byId.put(state.id, new EffectState(state.id, state.count, state.timeout));
            } else {
                int mergedCount = Math.max(1, existing.count) + Math.max(1, state.count);
                String mergedTimeout = existing.timeout.isEmpty() ? state.timeout : existing.timeout;
                byId.put(state.id, new EffectState(state.id, mergedCount, mergedTimeout));
            }
        }
        for (Map.Entry<Integer, EffectState> entry : byId.entrySet()) {
            EffectState state = entry.getValue();
            if (state == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(state.id)
                    .append(':')
                    .append(Math.max(1, state.count))
                    .append(':')
                    .append(sanitizeEffectStatePart(state.timeout));
        }
        return sb.toString();
    }

    public static String formatEffectCounter(EffectState state) {
        if (state == null) {
            return "";
        }
        String timeout = state.timeout == null ? "" : state.timeout.trim();
        if (timeout.isEmpty()) {
            timeout = "--:--:--";
        }
        return "[x" + Math.max(1, state.count) + "] (" + timeout + ")";
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

    private static int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String sanitizeEffectStatePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace(",", "").replace("\n", "").replace("\r", "");
    }
}
