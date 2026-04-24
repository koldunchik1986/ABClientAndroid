package ru.neverlands.anclient.postfilter;

import java.util.Collections;
import java.util.Locale;

import ru.neverlands.anclient.model.CityGateType;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.HelperStrings;
import ru.neverlands.anclient.utils.MapPath;

/**
 * Общая non-combat навигация main.php.
 *
 * Источник выноса: helpers `mainPhpFindPerc`, `mainPhpFindFlora`, `mainPhpFindMapReturnForAutoMoving`,
 * `startAutoSearchBoxMoving` из MainPhp.java.
 * Зависимости: MainPhp.buildRedirectHtml(...) как единый redirect-шаблон, FightAuto URL/link helpers,
 * InventoryParser.containsIgnoreCase(...), AppVars.AutoMoving* и AppVars.Profile.MapLocation.
 */
final class MainPhpNavigationHandler {

    private static final String TAG = "MainPhp";

    private MainPhpNavigationHandler() {
    }

    /**
     * Инициализирует состояние авто-движения к destination для SearchBox/клада.
     *
     * Важные переменные:
     * - destination: целевая клетка/локация.
     * - AppVars.AutoMoving/AutoMovingDestinaton/AutoMovingMapPath/AutoMovingNextJump/AutoMovingJumps/AutoMovingCityGate.
     * - AppVars.Profile.MapLocation: стартовая точка для MapPath, если уже известна.
     */
    static void startAutoSearchBoxMoving(String destination) {
        if (destination == null || destination.isEmpty()) {
            return;
        }
        AppVars.AutoMoving = true;
        AppVars.AutoMovingDestinaton = destination;
        AppVars.AutoMovingMapPath = null;
        AppVars.AutoMovingNextJump = null;
        AppVars.AutoMovingJumps = 0;
        AppVars.AutoMovingCityGate = CityGateType.None;

        String mapLocation = (AppVars.Profile != null) ? AppVars.Profile.MapLocation : null;
        if (mapLocation != null && !mapLocation.isEmpty()) {
            MapPath path = new MapPath(mapLocation, Collections.singletonList(destination));
            AppVars.AutoMovingMapPath = path;
            AppVars.AutoMovingNextJump = path.nextJump;
            AppVars.AutoMovingJumps = path.jumps;
            AppVars.AutoMovingCityGate = path.cityGate;
        }
    }

    /**
     * Строит переход на страницу персонажа `go=inf`.
     *
     * Порядок источников vcode:
     * - прямой onclick `main.php?get_id=56&act=10&go=inf&vcode=...`;
     * - JSON menu entry `["inf","Ваш персонаж","..."]`;
     * - fallback FightAuto.findMainPhpLinkByQueryParts(...).
     */
    static String mainPhpFindPerc(String html) {
        String vcode = HelperStrings.subString(html, "'main.php?get_id=56&act=10&go=inf&vcode=", "'");
        if (vcode != null && !vcode.isEmpty()) {
            String link = "main.php?get_id=56&act=10&go=inf&vcode=" + vcode;
            return MainPhp.buildRedirectHtml("Переключение на персонаж", link);
        }
        String patternEnter = "[\"inf\",\"Ваш персонаж\",\"";
        int posPatternEnter = html.indexOf(patternEnter);
        if (posPatternEnter == -1) {
            String fallbackLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> ";
                AppLog.d(TAG, msg);
                return MainPhp.buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        posPatternEnter += patternEnter.length();
        int posEnd = html.indexOf('"', posPatternEnter);
        if (posEnd == -1) {
            String fallbackLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> ";
                AppLog.d(TAG, msg);
                return MainPhp.buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        String jsonVcode = html.substring(posPatternEnter, posEnd);
        if (jsonVcode == null || jsonVcode.isEmpty()) {
            String fallbackLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (fallbackLink != null) {
                String msg = "AUTO_FALLBACK_TRACE mainPhpFindPerc: regex fallback -> ";
                AppLog.d(TAG, msg);
                return MainPhp.buildRedirectHtml("Переключение на персонаж", fallbackLink);
            }
            return null;
        }
        String link = "main.php?get_id=56&act=10&go=inf&vcode=" + jsonVcode;
        return MainPhp.buildRedirectHtml("Переключение на персонаж", link);
    }

    /**
     * Возвращает redirect на природу/карту через кнопку "Вернуться".
     * Используется AutoFish/AutoMoving после инвентаря или страницы персонажа.
     * Если обнаружен disabled "Причал", возвращает null, чтобы не кликать недоступный переход.
     */
    static String mainPhpFindFlora(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        if (InventoryParser.containsIgnoreCase(html, "<input type=button class=lbutdis value=\"Причал\" disabled>")) {
            return null;
        }
        final String returnMarker = "value=\"Вернуться\">";
        int posReturn = html.toLowerCase(Locale.ROOT).indexOf(returnMarker.toLowerCase(Locale.ROOT));
        if (posReturn == -1) {
            return null;
        }
        final String onClickPrefix = "onclick=\"location='";
        int posOnClick = html.toLowerCase(Locale.ROOT).lastIndexOf(
                onClickPrefix.toLowerCase(Locale.ROOT),
                posReturn
        );
        if (posOnClick == -1) {
            return null;
        }
        int linkStart = posOnClick + onClickPrefix.length();
        int linkEnd = html.indexOf('\'', linkStart);
        if (linkEnd == -1 || linkEnd <= linkStart) {
            return null;
        }
        String link = html.substring(linkStart, linkEnd);
        if (link.isEmpty()) {
            return null;
        }
        return MainPhp.buildRedirectHtml("Переключение на природу", link);
    }

    /**
     * Надёжный поиск ссылки возврата на карту `go=ret` для AutoMoving/SearchBox.
     *
     * Важные переменные:
     * - retVcode: vcode из menu item `ret`.
     * - mapLink: кандидат ссылки, приоритетно с `go=ret`, fallback через `go=inf -> go=ret`.
     * - returnMarker/onClickPrefix/linkStart/linkEnd: fallback по кнопке `Вернуться`.
     */
    static String mainPhpFindMapReturnForAutoMoving(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        String retVcode = mainPhpExtractMenuVcode(html, "ret");

        String mapLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=ret", "vcode=");
        if (mapLink == null) {
            mapLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=ret");
        }
        if ((mapLink == null || mapLink.isEmpty()) && retVcode != null && !retVcode.isEmpty()) {
            mapLink = FightAuto.normalizeNeverlandsMainLink("main.php?get_id=56&act=10&go=ret&vcode=" + retVcode);
        }
        if (mapLink == null) {
            String infLink = FightAuto.findMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
            if (infLink != null && !infLink.isEmpty()) {
                mapLink = FightAuto.normalizeNeverlandsMainLink(infLink.replace("go=inf", "go=ret"));
            }
        }

        if (mapLink == null) {
            final String returnMarker = "value=\"Вернуться\">";
            int posReturn = html.toLowerCase(Locale.ROOT).indexOf(returnMarker.toLowerCase(Locale.ROOT));
            if (posReturn != -1) {
                final String onClickPrefix = "onclick=\"location='";
                int posOnClick = html.toLowerCase(Locale.ROOT).lastIndexOf(
                        onClickPrefix.toLowerCase(Locale.ROOT),
                        posReturn
                );
                if (posOnClick != -1) {
                    int linkStart = posOnClick + onClickPrefix.length();
                    int linkEnd = html.indexOf('\'', linkStart);
                    if (linkEnd > linkStart) {
                        mapLink = FightAuto.normalizeNeverlandsMainLink(html.substring(linkStart, linkEnd));
                        if (retVcode != null && !retVcode.isEmpty()) {
                            mapLink = FightAuto.normalizeNeverlandsMainLink("main.php?get_id=56&act=10&go=ret&vcode=" + retVcode);
                        } else if (mapLink.contains("go=inf")) {
                            mapLink = FightAuto.normalizeNeverlandsMainLink(mapLink.replace("go=inf", "go=ret"));
                        } else if (mapLink.endsWith("/main.php") || mapLink.endsWith("/main.php?")) {
                            mapLink = FightAuto.normalizeNeverlandsMainLink("main.php?get_id=56&act=10&go=ret");
                        }
                    }
                }
            }
        }

        if (mapLink == null || mapLink.isEmpty()) {
            return null;
        }
        return MainPhp.buildRedirectHtml("Навигатор: переход на карту", mapLink);
    }

    /**
     * Извлекает vcode из JS/menu entry по ключу menuKey (`ret`, `inf` и т.п.).
     * Возвращает null, если menu entry отсутствует или формат отличается.
     */
    private static String mainPhpExtractMenuVcode(String html, String menuKey) {
        if (html == null || html.isEmpty() || menuKey == null || menuKey.isEmpty()) {
            return null;
        }
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\\[\\s*\\\"" + java.util.regex.Pattern.quote(menuKey) + "\\\"\\s*,\\s*\\\"[^\\\"]*\\\"\\s*,\\s*\\\"([^\\\"]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                String vcode = m.group(1);
                if (vcode != null && !vcode.isEmpty()) {
                    return vcode;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
