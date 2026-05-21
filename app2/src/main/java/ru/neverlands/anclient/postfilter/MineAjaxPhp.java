package ru.neverlands.anclient.postfilter;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.manager.AutoFunctionsManager;
import ru.neverlands.anclient.manager.AutoMineManager;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.ChatStats;
import ru.neverlands.anclient.utils.Russian;

/** Post-filter for gameplay/ajax/mine_ajax.php Auto-Mine responses. */
public final class MineAjaxPhp {
    private static final String TAG = "MineAjaxPhp";
    private static final int AUTO_DIG_EXTRA_DELAY_SECONDS = 2;
    private static final Pattern DIGG_REPORT_PATTERN = Pattern.compile(
            "(?:^|\\^)DIGG@([^@\\r\\n]+)@(\\d+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern RESOURCE_ENTRY_PATTERN = Pattern.compile(
            "([^,.:]+?)\\s*\\((\\d+(?:[\\.,]\\d+)?)\\)",
            Pattern.UNICODE_CASE);

    private MineAjaxPhp() {
    }

    public static byte[] process(String address, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }
        String html = Russian.getString(array);
        if (TextUtils.isEmpty(html)) {
            return array;
        }
        if (AppVars.getContext() == null) {
            return array;
        }
        AutoMineManager manager = AutoMineManager.getInstance(AppVars.getContext());
        boolean autoMineEnabled = isAutoMineEnabled();
        boolean pendingRoute = manager.hasPendingMineRoute();
        boolean torchRequired = html.toLowerCase(Locale.ROOT).contains("вам нужен факел");
        boolean tooTired = MapAjax.containsTooTiredMessage(html);
        if (!autoMineEnabled && !pendingRoute && !torchRequired && !tooTired) {
            return array;
        }
        if (tooTired) {
            manager.onMineRouteTooTiredFromServer(address, html);
        } else {
            manager.onMineAjaxResponse(address, html);
        }
        MineDiggReport diggReport = parseMineDiggReport(address, html);
        boolean newDiggReport = true;
        if (diggReport != null) {
            newDiggReport = manager.onMineDiggReport(diggReport.eventKey, diggReport.serverDelaySeconds);
            if (newDiggReport && !diggReport.deltaByResourceKg.isEmpty()) {
                ChatStats.addResourceDeltaKg(diggReport.deltaByResourceKg);
            }
            AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                    "digg report parsed: resources=" + diggReport.resources.size()
                            + ", statsUpdated=" + (newDiggReport && !diggReport.deltaByResourceKg.isEmpty())
                            + ", serverDelaySec=" + diggReport.serverDelaySeconds
                            + ", effectiveDelaySec=" + diggReport.effectiveDelaySeconds);
        }
        if (autoMineEnabled) {
            publishMineChatReportIfNeeded(manager, address, html, diggReport, newDiggReport);
        }
        return array;
    }

    private static void publishMineChatReportIfNeeded(AutoMineManager manager,
                                                      String address,
                                                      String html,
                                                      MineDiggReport diggReport,
                                                      boolean newDiggReport) {
        if (manager == null || !manager.isChatReportEnabled()) {
            return;
        }
        String text = html == null ? "" : html;
        String lower = text.toLowerCase(Locale.ROOT);
        String message = "";
        if (diggReport != null) {
            if (!newDiggReport) {
                return;
            }
            message = buildDiggChatMessage(diggReport);
        } else if (lower.contains("обнаружены ресурсы")) {
            message = compactResourceText(text, lower, "обнаружены ресурсы");
        } else if (lower.contains("добыты ресурсы")) {
            message = compactResourceText(text, lower, "добыты ресурсы");
        } else if (lower.contains("вы не нашли ни одного ресурса")) {
            message = "Вы не нашли ни одного ресурса";
        }
        if (message.isEmpty()) {
            return;
        }
        String chatHtml = MainPhp.buildServerChatTimeHtmlExternal()
                + "<font color=#333399><b>[auto_mine/mine_ajax]</b></font> "
                + escapeHtml(message);
        MainPhp.sendInventoryChatMessage(chatHtml);
        AppLog.i(AutoMineManager.TRACE_CHAIN, TAG,
                "chat report posted: address=" + safe(address) + ", message=" + message);
    }

    private static MineDiggReport parseMineDiggReport(String address, String html) {
        if (TextUtils.isEmpty(html)) {
            return null;
        }
        Matcher matcher = DIGG_REPORT_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        String reportText = compactText(matcher.group(1));
        int serverDelaySeconds = parsePositiveInt(matcher.group(2));
        List<MineResourceEntry> resources = parseResources(reportText);
        Map<String, Double> deltaByResourceKg = new LinkedHashMap<>();
        for (MineResourceEntry entry : resources) {
            Double existing = deltaByResourceKg.get(entry.name);
            deltaByResourceKg.put(entry.name, (existing == null ? 0d : existing) + entry.kilograms);
        }
        return new MineDiggReport(
                safe(address) + "|" + reportText + "|" + serverDelaySeconds,
                reportText,
                serverDelaySeconds,
                resources,
                deltaByResourceKg);
    }

    private static List<MineResourceEntry> parseResources(String reportText) {
        List<MineResourceEntry> result = new ArrayList<>();
        if (TextUtils.isEmpty(reportText)) {
            return result;
        }
        String resourcePart = reportText;
        int colon = resourcePart.indexOf(':');
        if (colon >= 0 && colon + 1 < resourcePart.length()) {
            resourcePart = resourcePart.substring(colon + 1);
        }
        int digIndex = resourcePart.toLowerCase(Locale.ROOT).indexOf("добываем");
        if (digIndex >= 0) {
            resourcePart = resourcePart.substring(0, digIndex);
        }
        Matcher matcher = RESOURCE_ENTRY_PATTERN.matcher(resourcePart);
        while (matcher.find()) {
            String name = compactText(matcher.group(1));
            Double kilograms = parseDouble(matcher.group(2));
            if (!name.isEmpty() && kilograms != null && kilograms > 0d) {
                result.add(new MineResourceEntry(name, kilograms, formatKgForChat(kilograms)));
            }
        }
        return result;
    }

    private static String buildDiggChatMessage(MineDiggReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ресурсы: ");
        if (report.resources.isEmpty()) {
            builder.append("не распознаны");
        } else {
            for (int i = 0; i < report.resources.size(); i++) {
                MineResourceEntry entry = report.resources.get(i);
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(entry.name)
                        .append(" (")
                        .append(entry.amountText)
                        .append(" кг)");
            }
        }
        if (report.effectiveDelaySeconds > 0) {
            builder.append(". Задержка: ")
                    .append(report.effectiveDelaySeconds)
                    .append(" сек.");
        }
        return builder.toString();
    }

    private static String compactResourceText(String text, String lower, String marker) {
        int index = lower.indexOf(marker);
        if (index < 0) {
            return compactText(text);
        }
        return compactText(text.substring(index));
    }

    private static boolean isAutoMineEnabled() {
        try {
            return AppVars.getContext() != null
                    && AutoFunctionsManager.getInstance(AppVars.getContext()).isAutoMineEnabled();
        } catch (Exception e) {
            AppLog.w(AutoMineManager.TRACE_CHAIN, TAG, "AutoMine state read failed", e);
            return false;
        }
    }

    private static String compactText(String value) {
        if (value == null) {
            return "";
        }
        String result = value.replace('\u00A0', ' ')
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (result.length() > 400) {
            result = result.substring(0, 400) + "...";
        }
        return result;
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Double parseDouble(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formatKgForChat(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MineDiggReport {
        final String eventKey;
        final String reportText;
        final int serverDelaySeconds;
        final int effectiveDelaySeconds;
        final List<MineResourceEntry> resources;
        final Map<String, Double> deltaByResourceKg;

        MineDiggReport(String eventKey,
                       String reportText,
                       int serverDelaySeconds,
                       List<MineResourceEntry> resources,
                       Map<String, Double> deltaByResourceKg) {
            this.eventKey = eventKey;
            this.reportText = reportText;
            this.serverDelaySeconds = serverDelaySeconds;
            this.effectiveDelaySeconds = serverDelaySeconds > 0
                    ? serverDelaySeconds + AUTO_DIG_EXTRA_DELAY_SECONDS : 0;
            this.resources = resources;
            this.deltaByResourceKg = deltaByResourceKg;
        }
    }

    private static final class MineResourceEntry {
        final String name;
        final double kilograms;
        final String amountText;

        MineResourceEntry(String name, double kilograms, String amountText) {
            this.name = name;
            this.kilograms = kilograms;
            this.amountText = amountText;
        }
    }
}
