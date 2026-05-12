package ru.neverlands.anclient.postfilter;

import android.content.Context;
import android.text.TextUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import ru.neverlands.anclient.manager.KaznaManager;
import ru.neverlands.anclient.model.KaznaCategory;
import ru.neverlands.anclient.model.KaznaItem;
import ru.neverlands.anclient.model.KaznaItemDetails;
import ru.neverlands.anclient.model.KaznaSet;
import ru.neverlands.anclient.model.KaznaSnapshot;
import ru.neverlands.anclient.parser.KaznaParser;
import ru.neverlands.anclient.repository.ApiRepository;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;

/**
 * HTML renderer для прямого открытия клановой казны внутри WebView.
 *
 * Почему отдельный renderer, а не новый экран/запрос:
 * - пользователь открывает серверную страницу напрямую: `main.php?useaction=clan-action&addid=3`;
 * - ответ уже пришёл через штатный WebView/proxy/cookie/User-Agent контур, поэтому второй
 *   `ApiRepository.downloadFile(...)` здесь не нужен и мог бы создать гонку с ручными кликами;
 * - `KaznaActivity` остаётся отдельным native-экраном, а этот класс только заменяет HTML
 *   в `MainPhp.process(...)`, как C# `KaznaHtmlRenderer` в `ANClient/PostFilter/MainPhp.cs`.
 *
 * Зависимости и источник данных:
 * - `KaznaParser` разбирает категории, предметы, uid и серверные action-link-и;
 * - `KaznaManager.acceptPostfilterHtml(...)` сохраняет snapshot в существующий кеш
 *   `info/<profile nick>/kazna/`, чтобы native-экран и HTML-инъекция видели один state;
 * - `KaznaItemDetailsCache` даёт картинки/свойства из уже просмотренного инвентаря;
 * - `KaznaSet`/`KaznaManager.loadSets()` переиспользуют локальные комплекты без второго хранилища;
 * - `AppLog` + `FileLogger` обязательны, потому цепочка находится в runtime postfilter-е.
 */
final class KaznaHtmlInjectionRenderer {
    private static final String TAG = "KaznaHtmlInjection";
    private static final String TRACE = "KAZNA_TRACE";

    private static final int VIEW_ALL = 0;
    private static final int VIEW_RARES = 1;
    private static final int VIEW_ARTS = 2;
    private static final int VIEW_ORDINARY = 3;
    private static final int VIEW_SETS = 4;

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern ARTIFACT_COEFFICIENT = Pattern.compile("(?<!\\d)[12]\\.\\d{2}(?!\\d)");

    private KaznaHtmlInjectionRenderer() {
    }

    static boolean isKaznaRequest(String address) {
        if (address == null) {
            return false;
        }
        String normalized = address.toLowerCase(Locale.ROOT).replace("&amp;", "&");
        return normalized.contains("useaction=clan-action")
                && normalized.contains("addid=3");
    }

    /**
     * Главная точка HTML-инъекции для `MainPhp.process(...)`.
     *
     * Порядок важен:
     * 1) сначала выполняем локальное действие `an_kazna_action`, если оно есть в URL;
     * 2) затем парсим именно текущий серверный HTML и сохраняем snapshot через `KaznaManager`;
     * 3) после этого строим HTML-документ из уже сохранённого snapshot/cache/details.
     *
     * Такой порядок сохраняет поведение C# renderer-а: локальные действия комплектов
     * меняют только профильный список UID, а `Взять из казны`/`Пожертвовать` остаются
     * прямыми серверными href из `KaznaItem.takeUrl`/`donateUrl`.
     */
    static String render(String address, String html) {
        Context context = AppVars.getContext();
        if (context == null) {
            AppLog.w(TAG, TRACE + " render skipped: AppVars context is null");
            return html;
        }

        try {
            KaznaManager manager = KaznaManager.getInstance(context);
            applyLocalAction(address, manager);
            KaznaSnapshot snapshot = manager.acceptPostfilterHtml(html, address);
            Map<String, KaznaItemDetails> detailsByUid = manager.loadItemDetailsByUid();
            List<KaznaSet> sets = manager.loadSets();
            ViewCounters counters = count(snapshot);
            int viewMode = parseInt(queryParam(address, "an_kazna_view"), VIEW_ALL);
            AppLog.i(TAG, TRACE + " render start: view=" + viewMode
                    + ", items=" + snapshot.items.size()
                    + ", sets=" + sets.size()
                    + ", address=" + address);
            FileLogger.trace(TRACE, "html injection render view=" + viewMode
                    + ", items=" + snapshot.items.size()
                    + ", sets=" + sets.size());
            return buildDocument(snapshot, detailsByUid, sets, counters, viewMode);
        } catch (Exception e) {
            AppLog.e(TAG, TRACE + " render failed", e);
            FileLogger.trace(TRACE, "html injection render failed: " + e.getMessage());
            return html;
        }
    }

    /**
     * Обрабатывает только локальные действия HTML-renderer-а.
     *
     * Что здесь намеренно НЕ делается:
     * - не синтезируются `get_id=29`/`get_id=18` URL для казны;
     * - не читается `AppVars.VCode`;
     * - не создаётся отдельный raw HTTP-контур.
     *
     * Зависимости:
     * - create/add/remove/delete вызывают существующие методы `KaznaManager`, которые пишут
     *   `kazna_sets.json` в профильную папку;
     * - collect запускает существующую асинхронную цепочку `KaznaManager.collectSet(...)`,
     *   где каждый take использует свежий серверный action-link и SessionManager/VCode handling;
     * - callback только логирует результат, потому WebView уже получил текущий HTML-ответ.
     */
    private static void applyLocalAction(String address, KaznaManager manager) {
        String action = queryParam(address, "an_kazna_action");
        if (TextUtils.isEmpty(action)) {
            return;
        }

        String setName = queryParam(address, "an_kazna_set");
        String uid = queryParam(address, "an_kazna_uid");
        if ("create".equalsIgnoreCase(action)) {
            manager.addSet(setName);
        } else if ("add".equalsIgnoreCase(action)) {
            manager.addItemToSet(setName, uid);
        } else if ("remove".equalsIgnoreCase(action)) {
            KaznaSet set = findSet(manager.loadSets(), setName);
            manager.removeItemFromSet(set, uid);
        } else if ("delete".equalsIgnoreCase(action)) {
            manager.deleteSet(findSet(manager.loadSets(), setName));
        } else if ("collect".equalsIgnoreCase(action)) {
            KaznaSet set = findSet(manager.loadSets(), setName);
            if (set != null) {
                manager.collectSet(set, new ApiRepository.ApiCallback<KaznaSnapshot>() {
                    @Override
                    public void onSuccess(KaznaSnapshot result) {
                        AppLog.i(TAG, TRACE + " collect from html injection finished: set=" + set.name);
                    }

                    @Override
                    public void onFailure(String message) {
                        AppLog.w(TAG, TRACE + " collect from html injection failed: set=" + set.name
                                + ", reason=" + message);
                    }
                });
            }
        }
        AppLog.i(TAG, TRACE + " local action applied: action=" + action
                + ", set=" + setName + ", uid=" + uid);
        FileLogger.trace(TRACE, "local action=" + action + ", set=" + setName + ", uid=" + uid);
    }

    private static String buildDocument(
            KaznaSnapshot snapshot,
            Map<String, KaznaItemDetails> detailsByUid,
            List<KaznaSet> sets,
            ViewCounters counters,
            int viewMode) {
        // Строим полный документ, а не вставку в серверную таблицу. Это повторяет текущий
        // C# ANClient renderer и предотвращает смешивание старой табличной верстки сервера
        // с card-style UI, режимами и локальными комплектами.
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ANClient Казна</title>");
        appendStyles(sb);
        sb.append("</head><body><div class=\"kazna-page\">");
        sb.append("<div class=\"kazna-top\">");
        sb.append("<a class=\"back-button\" href=\"http://neverlands.ru/main.php\">Вернуться к основному окну</a>");
        sb.append("<div class=\"title\">Клановая казна</div>");
        sb.append("<div class=\"subtitle\">ANClient renderer: ").append(html(viewModeName(viewMode)))
                .append(", показано ").append(countForMode(counters, viewMode))
                .append(" из ").append(counters.all).append("</div>");
        sb.append("</div>");
        appendModeLinks(sb, counters, sets, viewMode);
        if (viewMode == VIEW_SETS) {
            appendSets(sb, sets, snapshot, detailsByUid);
        } else {
            appendCategoryBar(sb, snapshot, viewMode);
            appendItems(sb, snapshot, detailsByUid, sets, viewMode);
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static void appendStyles(StringBuilder sb) {
        sb.append("<style type=\"text/css\">");
        sb.append("body{margin:0;background:#111827;font-family:Verdana,Arial,sans-serif;color:#172033;font-size:12px;} ");
        sb.append(".kazna-page{padding:14px;background:linear-gradient(135deg,#111827 0%,#1f2937 34%,#f8fafc 34%,#eef2ff 100%);min-height:100%;} ");
        sb.append(".kazna-top{border-radius:14px;background:#ffffff;padding:14px 16px;margin-bottom:12px;box-shadow:0 8px 24px rgba(15,23,42,.28);border:1px solid #dbe4ff;} ");
        sb.append(".title{font-size:22px;font-weight:bold;color:#111827;margin-top:10px;} .subtitle{color:#64748b;margin-top:4px;} ");
        sb.append(".back-button{display:inline-block;padding:9px 14px;border-radius:999px;background:#0f172a;color:#fff;text-decoration:none;font-weight:bold;box-shadow:0 3px 10px rgba(15,23,42,.35);} ");
        sb.append(".modes,.categories{background:#ffffff;border-radius:12px;padding:10px;margin-bottom:12px;box-shadow:0 5px 18px rgba(15,23,42,.16);} ");
        sb.append(".pill{display:inline-block;margin:3px;padding:8px 12px;border-radius:999px;text-decoration:none;font-weight:bold;border:1px solid #c7d2fe;color:#1d4ed8;background:#eef2ff;} ");
        sb.append(".pill-current{background:#2563eb;color:#fff;border-color:#2563eb;box-shadow:0 4px 12px rgba(37,99,235,.35);} ");
        sb.append(".cat{display:inline-block;margin:3px;padding:5px 7px;border-radius:9px;background:#f8fafc;border:1px solid #e2e8f0;text-decoration:none;color:#334155;} .cat img{border:0;vertical-align:middle;max-width:24px;max-height:24px;} .cat-current{background:#0f766e;color:#fff;border-color:#0f766e;box-shadow:0 4px 12px rgba(15,118,110,.35);} ");
        sb.append(".item{display:table;width:100%;box-sizing:border-box;border-radius:12px;margin:8px 0;border:1px solid #dbe4ff;box-shadow:0 3px 12px rgba(15,23,42,.10);overflow:hidden;} ");
        sb.append(".item-even{background:#ffffff;} .item-odd{background:#edf3ff;} .thumb{display:table-cell;width:58px;vertical-align:top;padding:12px;} .thumb img{max-width:50px;max-height:50px;border-radius:10px;background:#fff;border:1px solid #e2e8f0;} ");
        sb.append(".body{display:table-cell;vertical-align:top;padding:11px 12px;} .name{font-size:14px;font-weight:bold;color:#0f172a;} .coef{display:inline-block;margin-left:6px;color:#b91c1c;background:#fee2e2;border-radius:6px;padding:1px 5px;} ");
        sb.append(".meta{margin-top:5px;color:#475569;line-height:1.45;} .props{margin-top:7px;white-space:pre-line;color:#334155;background:rgba(255,255,255,.72);border-radius:8px;padding:7px;border:1px dashed #cbd5e1;} ");
        sb.append(".actions{margin-top:9px;} .action{display:inline-block;margin:3px 6px 3px 0;padding:8px 13px;border-radius:9px;text-decoration:none;font-weight:bold;color:#fff;box-shadow:0 4px 12px rgba(15,23,42,.22);} ");
        sb.append(".take{background:#16a34a;} .donate{background:#f97316;} .setadd{background:#7c3aed;} .collect{background:#0891b2;} .delete{background:#dc2626;} .remove{background:#be123c;} .disabled{display:inline-block;color:#94a3b8;margin-top:6px;} .empty{padding:18px;border-radius:12px;background:#fff;color:#b91c1c;} ");
        sb.append(".set-card{background:#fff;border-radius:14px;margin:10px 0;padding:12px;border:1px solid #dbe4ff;box-shadow:0 5px 18px rgba(15,23,42,.14);} .set-title{font-size:15px;font-weight:bold;color:#0f172a;margin-bottom:7px;} .set-uids{margin-top:8px;} ");
        sb.append("</style>");
    }

    private static void appendModeLinks(StringBuilder sb, ViewCounters counters, List<KaznaSet> sets, int viewMode) {
        sb.append("<div class=\"modes\">");
        appendModeLink(sb, VIEW_ALL, "Все", counters.all, viewMode);
        appendModeLink(sb, VIEW_ARTS, "Арты", counters.arts, viewMode);
        appendModeLink(sb, VIEW_RARES, "Рары", counters.rares, viewMode);
        appendModeLink(sb, VIEW_ORDINARY, "Обычные", counters.ordinary, viewMode);
        appendModeLink(sb, VIEW_SETS, "Комплекты", sets == null ? 0 : sets.size(), viewMode);
        sb.append("<a class=\"pill\" href=\"javascript:var n=prompt('Название комплекта');if(n){location='")
                .append(html(KaznaParser.BASE_KAZNA_URL))
                .append("&an_kazna_view=4&an_kazna_action=create&an_kazna_set='+encodeURIComponent(n);}\">+ Комплект</a>");
        sb.append("</div>");
    }

    private static void appendModeLink(StringBuilder sb, int mode, String title, int count, int currentMode) {
        sb.append("<a class=\"").append(mode == currentMode ? "pill pill-current" : "pill").append("\" href=\"")
                .append(html(KaznaParser.BASE_KAZNA_URL))
                .append("&an_kazna_view=").append(mode).append("\">")
                .append(html(title)).append(' ').append(count).append("</a>");
    }

    private static void appendCategoryBar(StringBuilder sb, KaznaSnapshot snapshot, int viewMode) {
        if (snapshot == null || snapshot.categories.isEmpty()) {
            return;
        }
        sb.append("<div class=\"categories\"><a class=\"")
                .append(TextUtils.isEmpty(snapshot.currentWca) ? "cat cat-current" : "cat")
                .append("\" href=\"").append(html(withView(KaznaParser.BASE_KAZNA_URL, viewMode))).append("\">Все категории</a>");
        for (KaznaCategory category : snapshot.categories) {
            String css = snapshot.currentWca.equalsIgnoreCase(category.wca) ? "cat cat-current" : "cat";
            sb.append("<a class=\"").append(css).append("\" title=\"").append(html(category.title))
                    .append("\" href=\"").append(html(withView(category.href, viewMode))).append("\">");
            if (!TextUtils.isEmpty(category.iconUrl)) {
                sb.append("<img src=\"").append(html(category.iconUrl)).append("\"> ");
            }
            sb.append(html(TextUtils.isEmpty(category.title) ? category.wca : category.title)).append("</a>");
        }
        sb.append("</div>");
    }

    private static void appendItems(
            StringBuilder sb,
            KaznaSnapshot snapshot,
            Map<String, KaznaItemDetails> detailsByUid,
            List<KaznaSet> sets,
            int viewMode) {
        if (snapshot == null || snapshot.items.isEmpty()) {
            sb.append("<div class=\"empty\">Предметы казны не найдены в HTML-ответе.</div>");
            return;
        }
        int visibleIndex = 0;
        for (KaznaItem item : snapshot.items) {
            if (!isViewMatch(item, viewMode)) {
                continue;
            }
            // UID-details cache не является источником серверного действия. Он используется
            // только для картинки/свойств; action UID остаётся тем, что пришёл в строке казны.
            KaznaItemDetails details = findDetails(item, detailsByUid);
            sb.append("<div class=\"").append(visibleIndex % 2 == 0 ? "item item-even" : "item item-odd").append("\">");
            visibleIndex++;
            appendThumb(sb, details);
            sb.append("<div class=\"body\"><div class=\"name\">").append(html(item.displayName));
            if (!TextUtils.isEmpty(item.artifactCoefficient)) {
                sb.append("<span class=\"coef\">").append(html(item.artifactCoefficient)).append("</span>");
            }
            sb.append("</div>");
            appendMeta(sb, item, details);
            if (details != null && details.hasProperties()) {
                sb.append("<div class=\"props\">").append(html(details.propertiesText)).append("</div>");
            } else {
                sb.append("<div class=\"props\">Свойства: информация не известна. Откройте инвентарь/информацию предмета, чтобы ANClient пополнил кеш.</div>");
            }
            appendActions(sb, item, sets);
            sb.append("</div></div>");
        }
        if (visibleIndex == 0) {
            sb.append("<div class=\"empty\">В выбранном режиме предметов нет.</div>");
        }
    }

    private static void appendMeta(StringBuilder sb, KaznaItem item, KaznaItemDetails details) {
        sb.append("<div class=\"meta\">");
        if (!TextUtils.isEmpty(item.uid)) {
            sb.append("uid=").append(html(item.uid)).append(" | ");
        } else if (details != null && !TextUtils.isEmpty(details.uid)) {
            sb.append("uid=").append(html(details.uid)).append(" (из кеша) | ");
        }
        if (!TextUtils.isEmpty(item.owner)) {
            sb.append("В-инвентаре: ").append(html(item.owner)).append(" | ");
        }
        if (!TextUtils.isEmpty(item.durabilityText)) {
            sb.append("Долговечность: ").append(html(item.durabilityText)).append(" | ");
        }
        if (!TextUtils.isEmpty(item.status)) {
            sb.append(html(item.status));
        }
        sb.append("</div>");
    }

    private static void appendActions(StringBuilder sb, KaznaItem item, List<KaznaSet> sets) {
        sb.append("<div class=\"actions\">");
        boolean hasAction = false;
        if (!TextUtils.isEmpty(item.uid)) {
            hasAction = true;
            // Кнопки комплектов работают через локальные `an_kazna_*` параметры, потому они
            // не конкурируют с серверными Kazna action-link-ами и не требуют отдельного vcode.
            if (sets != null) {
                for (KaznaSet set : sets) {
                    sb.append("<a class=\"action setadd\" href=\"")
                            .append(html(buildSetActionUrl("add", set.name, item.uid)))
                            .append("\">+ ").append(html(set.name)).append("</a>");
                }
            }
            sb.append("<a class=\"action setadd\" href=\"javascript:var n=prompt('Название комплекта');if(n){location='")
                    .append(html(KaznaParser.BASE_KAZNA_URL))
                    .append("&an_kazna_action=add&an_kazna_uid=").append(html(url(item.uid)))
                    .append("&an_kazna_set='+encodeURIComponent(n);}\">+ Новый комплект</a>");
        }
        if (!TextUtils.isEmpty(item.takeUrl)) {
            hasAction = true;
            // Ручной HTML-клик должен уходить по серверному href без пересборки URL:
            // так сохраняются PHPSESSID/vcode и выполняется требование первого клика.
            sb.append("<a class=\"action take\" href=\"").append(html(item.takeUrl)).append("\">Взять из казны</a>");
        }
        if (!TextUtils.isEmpty(item.donateUrl)) {
            hasAction = true;
            sb.append("<a class=\"action donate\" href=\"").append(html(item.donateUrl)).append("\">Пожертвовать</a>");
        }
        if (!hasAction) {
            sb.append("<span class=\"disabled\">Нет доступных действий для этой строки</span>");
        }
        sb.append("</div>");
    }

    private static void appendSets(
            StringBuilder sb,
            List<KaznaSet> sets,
            KaznaSnapshot snapshot,
            Map<String, KaznaItemDetails> detailsByUid) {
        sb.append("<div class=\"categories\"><a class=\"action setadd\" href=\"javascript:var n=prompt('Название комплекта');if(n){location='")
                .append(html(KaznaParser.BASE_KAZNA_URL))
                .append("&an_kazna_view=4&an_kazna_action=create&an_kazna_set='+encodeURIComponent(n);}\">Создать комплект</a></div>");
        if (sets == null || sets.isEmpty()) {
            sb.append("<div class=\"empty\">Комплектов нет. Откройте вкладку предметов и нажмите `+ Новый комплект` у нужной вещи.</div>");
            return;
        }
        for (KaznaSet set : sets) {
            sb.append("<div class=\"set-card\"><div class=\"set-title\">").append(html(set.name)).append("</div>");
            sb.append("<div class=\"actions\">");
            sb.append("<a class=\"action collect\" href=\"").append(html(buildSetActionUrl("collect", set.name, ""))).append("\">Собрать</a>");
            sb.append("<a class=\"action delete\" href=\"").append(html(buildSetActionUrl("delete", set.name, ""))).append("\">Удалить комплект</a>");
            sb.append("</div><div class=\"set-uids\">");
            if (set.itemUids.isEmpty()) {
                sb.append("<div class=\"disabled\">Комплект пуст</div>");
            }
            int uidIndex = 0;
            for (String uid : set.itemUids) {
                // Для вкладки комплектов текущий snapshot нужен только для имени предмета
                // и доступности action-link при последующем collect; сохранённый UID остаётся
                // стабильным ключом комплекта между перезагрузками страницы.
                KaznaItem item = snapshot == null ? null : snapshot.findItemByUid(uid);
                KaznaItemDetails details = detailsByUid == null ? null : detailsByUid.get(uid);
                sb.append("<div class=\"").append(uidIndex % 2 == 0 ? "item item-even" : "item item-odd").append("\">");
                uidIndex++;
                appendThumb(sb, details);
                sb.append("<div class=\"body\"><div class=\"name\">").append(html(setItemTitle(item, details, uid))).append("</div>");
                if (details != null && details.hasProperties()) {
                    sb.append("<div class=\"props\">").append(html(details.propertiesText)).append("</div>");
                } else {
                    sb.append("<div class=\"meta\">uid=").append(html(uid)).append("</div>");
                }
                sb.append("<div class=\"actions\"><a class=\"action remove\" href=\"")
                        .append(html(buildSetActionUrl("remove", set.name, uid)))
                        .append("\">Убрать из комплекта</a></div>");
                sb.append("</div></div>");
            }
            sb.append("</div></div>");
        }
    }

    private static void appendThumb(StringBuilder sb, KaznaItemDetails details) {
        sb.append("<div class=\"thumb\">");
        if (details != null && details.hasImage()) {
            sb.append("<img src=\"").append(html(details.imageUrl)).append("\">");
        } else {
            sb.append("<div style=\"width:50px;height:50px;border-radius:10px;background:#e2e8f0;text-align:center;line-height:50px;color:#64748b\">?</div>");
        }
        sb.append("</div>");
    }

    private static boolean isViewMatch(KaznaItem item, int viewMode) {
        if (item == null) {
            return false;
        }
        if (viewMode == VIEW_ARTS) {
            return item.hasArtifactCoefficient();
        }
        if (viewMode == VIEW_RARES) {
            return item.isRare();
        }
        if (viewMode == VIEW_ORDINARY) {
            return item.isOrdinary();
        }
        return viewMode != VIEW_SETS;
    }

    private static KaznaItemDetails findDetails(KaznaItem item, Map<String, KaznaItemDetails> detailsByUid) {
        if (item == null || detailsByUid == null || detailsByUid.isEmpty()) {
            return null;
        }
        if (!TextUtils.isEmpty(item.uid) && detailsByUid.containsKey(item.uid)) {
            return detailsByUid.get(item.uid);
        }
        // Если серверная строка казны не содержит UID (например, предмет занят), используем
        // только безопасное совпадение по видимой сигнатуре. Порог score >= 100 защищает от
        // подстановки картинки/свойств похожего предмета с другим коэффициентом/долговечностью.
        String itemName = normalizeName(TextUtils.isEmpty(item.baseName) ? item.displayName : item.baseName);
        if (TextUtils.isEmpty(itemName)) {
            return null;
        }
        KaznaItemDetails best = null;
        int bestScore = 0;
        for (KaznaItemDetails details : detailsByUid.values()) {
            int score = scoreDetailsMatch(item, itemName, details);
            if (score > bestScore) {
                bestScore = score;
                best = details;
            }
        }
        return bestScore >= 100 ? best : null;
    }

    private static int scoreDetailsMatch(KaznaItem item, String itemName, KaznaItemDetails details) {
        if (details == null) {
            return 0;
        }
        String detailsName = normalizeName(details.name);
        if (TextUtils.isEmpty(detailsName) || !detailsName.equals(itemName)) {
            return 0;
        }
        String properties = normalizeSearchText(details.propertiesText);
        int score = 70;
        if (item.hasArtifactCoefficient()) {
            String coefficient = normalizeSearchText(item.artifactCoefficient);
            if (TextUtils.isEmpty(coefficient) || !properties.contains(coefficient)) {
                return 0;
            }
            score += 40;
        }
        if (!TextUtils.isEmpty(item.durabilityText)) {
            String durability = normalizeSearchText(item.durabilityText);
            if (!TextUtils.isEmpty(durability) && properties.contains(durability)) {
                score += 25;
            } else if (properties.contains("долговечность")) {
                return 0;
            }
        }
        if (details.hasProperties()) {
            score += 10;
        }
        if (details.hasImage()) {
            score += 5;
        }
        return score;
    }

    private static ViewCounters count(KaznaSnapshot snapshot) {
        ViewCounters counters = new ViewCounters();
        if (snapshot == null) {
            return counters;
        }
        for (KaznaItem item : snapshot.items) {
            counters.all++;
            if (item.hasArtifactCoefficient()) {
                counters.arts++;
            } else if (item.isRare()) {
                counters.rares++;
            } else if (item.isOrdinary()) {
                counters.ordinary++;
            }
        }
        return counters;
    }

    private static int countForMode(ViewCounters counters, int viewMode) {
        if (viewMode == VIEW_RARES) {
            return counters.rares;
        }
        if (viewMode == VIEW_ARTS) {
            return counters.arts;
        }
        if (viewMode == VIEW_ORDINARY) {
            return counters.ordinary;
        }
        return counters.all;
    }

    private static String viewModeName(int viewMode) {
        if (viewMode == VIEW_SETS) {
            return "Комплекты";
        }
        if (viewMode == VIEW_RARES) {
            return "Рары";
        }
        if (viewMode == VIEW_ARTS) {
            return "Арты";
        }
        if (viewMode == VIEW_ORDINARY) {
            return "Обычные";
        }
        return "Все";
    }

    private static KaznaSet findSet(List<KaznaSet> sets, String setName) {
        if (sets == null || TextUtils.isEmpty(setName)) {
            return null;
        }
        for (KaznaSet set : sets) {
            if (set.name.equalsIgnoreCase(setName.trim())) {
                return set;
            }
        }
        return null;
    }

    private static String setItemTitle(KaznaItem item, KaznaItemDetails details, String uid) {
        if (item != null && !TextUtils.isEmpty(item.displayName)) {
            return item.displayName;
        }
        if (details != null && !TextUtils.isEmpty(details.name)) {
            return details.name;
        }
        return "uid=" + safe(uid);
    }

    private static String buildSetActionUrl(String action, String setName, String uid) {
        String value = KaznaParser.BASE_KAZNA_URL + "&an_kazna_view=4&an_kazna_action=" + url(action)
                + "&an_kazna_set=" + url(setName);
        if (!TextUtils.isEmpty(uid)) {
            value += "&an_kazna_uid=" + url(uid);
        }
        return value;
    }

    private static String withView(String sourceUrl, int viewMode) {
        String value = TextUtils.isEmpty(sourceUrl) ? KaznaParser.BASE_KAZNA_URL : sourceUrl.replace("&amp;", "&");
        value = removeQueryParam(value, "an_kazna_view");
        return value + (value.contains("?") ? "&" : "?") + "an_kazna_view=" + viewMode;
    }

    private static String removeQueryParam(String url, String key) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(key)) {
            return safe(url);
        }
        String[] mainParts = url.split("\\?", 2);
        if (mainParts.length < 2) {
            return url;
        }
        StringBuilder sb = new StringBuilder(mainParts[0]);
        String delimiter = "?";
        for (String part : mainParts[1].split("&")) {
            int eq = part.indexOf('=');
            String name = eq < 0 ? part : part.substring(0, eq);
            if (key.equalsIgnoreCase(name)) {
                continue;
            }
            sb.append(delimiter).append(part);
            delimiter = "&";
        }
        return sb.toString();
    }

    private static String queryParam(String url, String key) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(key)) {
            return "";
        }
        for (String part : url.replace("&amp;", "&").split("[?&]")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (key.equalsIgnoreCase(part.substring(0, eq))) {
                return decode(part.substring(eq + 1));
            }
        }
        return "";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(safe(value), "UTF-8");
        } catch (Exception ignored) {
            return safe(value);
        }
    }

    private static String url(String value) {
        try {
            return URLEncoder.encode(safe(value), "UTF-8");
        } catch (Exception ignored) {
            return safe(value);
        }
    }

    private static String html(String value) {
        String safe = safe(value);
        return safe.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String normalizeSearchText(String value) {
        return SPACES.matcher(safe(value).replace('\u00A0', ' ')).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String value) {
        return ARTIFACT_COEFFICIENT.matcher(normalizeSearchText(value)).replaceAll("").trim();
    }

    private static int parseInt(String value, int fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ViewCounters {
        int all;
        int rares;
        int arts;
        int ordinary;
    }
}
