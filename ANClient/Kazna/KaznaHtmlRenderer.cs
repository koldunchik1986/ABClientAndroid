using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using System.Web;

namespace ANClient.Kazna
{
    internal static class KaznaHtmlRenderer
    {
        internal const int ViewAll = 0;
        internal const int ViewRares = 1;
        internal const int ViewArts = 2;
        internal const int ViewOrdinary = 3;
        internal const int ViewSets = 4;

        internal static string Render(string html, string sourceUrl, int viewMode)
        {
            var snapshot = KaznaParser.Parse(html, sourceUrl);
            var details = KaznaItemDetailsCache.LoadAll();
            var sets = KaznaSetStore.LoadSets();
            var counters = Count(snapshot);
            return BuildDocument(snapshot, details, sets, counters, viewMode);
        }

        internal static bool IsViewMatch(KaznaItem item, int viewMode)
        {
            if (item == null)
                return false;

            switch (viewMode)
            {
                case ViewArts:
                    return item.HasArtifactCoefficient;
                case ViewRares:
                    return item.IsRare;
                case ViewOrdinary:
                    return item.IsOrdinary;
                case ViewSets:
                    return false;
                default:
                    return true;
            }
        }

        internal static string ViewModeName(int viewMode)
        {
            switch (viewMode)
            {
                case ViewSets:
                    return "Комплекты";
                case ViewRares:
                    return "Рары";
                case ViewArts:
                    return "Арты";
                case ViewOrdinary:
                    return "Обычные";
                default:
                    return "Все";
            }
        }

        private static string BuildDocument(KaznaSnapshot snapshot, Dictionary<string, KaznaItemDetails> detailsByUid, List<KaznaSet> sets, ViewCounters counters, int viewMode)
        {
            var sb = new StringBuilder();
            sb.Append("<!DOCTYPE html><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\"><title>ANClient Казна</title>");
            AppendStyles(sb);
            sb.Append("</head><body><div class=\"kazna-page\">");
            sb.Append("<div class=\"kazna-top\">");
            sb.Append("<a class=\"back-button\" href=\"http://www.neverlands.ru/main.php\">Вернуться к основному окну</a>");
            sb.Append("<div class=\"title\">Клановая казна</div>");
            sb.Append("<div class=\"subtitle\">ANClient renderer: ").Append(Html(ViewModeName(viewMode))).Append(", показано ").Append(CountForMode(counters, viewMode)).Append(" из ").Append(counters.All).Append("</div>");
            sb.Append("</div>");
            AppendModeLinks(sb, counters, viewMode);
            if (viewMode == ViewSets)
            {
                AppendSets(sb, sets, snapshot, detailsByUid);
            }
            else
            {
                AppendCategoryBar(sb, snapshot, viewMode);
                AppendItems(sb, snapshot, detailsByUid, sets, viewMode);
            }
            sb.Append("</div></body></html>");
            return sb.ToString();
        }

        private static void AppendStyles(StringBuilder sb)
        {
            sb.Append("<style type=\"text/css\">");
            sb.Append("body{margin:0;background:#111827;font-family:Verdana,Arial,sans-serif;color:#172033;font-size:12px;} ");
            sb.Append(".kazna-page{padding:14px;background:linear-gradient(135deg,#111827 0%,#1f2937 34%,#f8fafc 34%,#eef2ff 100%);min-height:100%;} ");
            sb.Append(".kazna-top{border-radius:14px;background:#ffffff;padding:14px 16px;margin-bottom:12px;box-shadow:0 8px 24px rgba(15,23,42,.28);border:1px solid #dbe4ff;} ");
            sb.Append(".title{font-size:22px;font-weight:bold;color:#111827;margin-top:10px;} .subtitle{color:#64748b;margin-top:4px;} ");
            sb.Append(".back-button{display:inline-block;padding:9px 14px;border-radius:999px;background:#0f172a;color:#fff;text-decoration:none;font-weight:bold;box-shadow:0 3px 10px rgba(15,23,42,.35);} ");
            sb.Append(".modes,.categories{background:#ffffff;border-radius:12px;padding:10px;margin-bottom:12px;box-shadow:0 5px 18px rgba(15,23,42,.16);} ");
            sb.Append(".pill{display:inline-block;margin:3px;padding:8px 12px;border-radius:999px;text-decoration:none;font-weight:bold;border:1px solid #c7d2fe;color:#1d4ed8;background:#eef2ff;} ");
            sb.Append(".pill-current{background:#2563eb;color:#fff;border-color:#2563eb;box-shadow:0 4px 12px rgba(37,99,235,.35);} ");
            sb.Append(".cat{display:inline-block;margin:3px;padding:5px 7px;border-radius:9px;background:#f8fafc;border:1px solid #e2e8f0;text-decoration:none;color:#334155;} .cat img{border:0;vertical-align:middle;} .cat-current{background:#0f766e;color:#fff;border-color:#0f766e;box-shadow:0 4px 12px rgba(15,118,110,.35);} ");
            sb.Append(".item{display:table;width:100%;box-sizing:border-box;border-radius:12px;margin:8px 0;border:1px solid #dbe4ff;box-shadow:0 3px 12px rgba(15,23,42,.10);overflow:hidden;} ");
            sb.Append(".item-even{background:#ffffff;} .item-odd{background:#edf3ff;} .thumb{display:table-cell;width:58px;vertical-align:top;padding:12px;} .thumb img{max-width:50px;max-height:50px;border-radius:10px;background:#fff;border:1px solid #e2e8f0;} ");
            sb.Append(".body{display:table-cell;vertical-align:top;padding:11px 12px;} .name{font-size:14px;font-weight:bold;color:#0f172a;} .coef{display:inline-block;margin-left:6px;color:#b91c1c;background:#fee2e2;border-radius:6px;padding:1px 5px;} ");
            sb.Append(".meta{margin-top:5px;color:#475569;line-height:1.45;} .props{margin-top:7px;white-space:pre-line;color:#334155;background:rgba(255,255,255,.72);border-radius:8px;padding:7px;border:1px dashed #cbd5e1;} ");
            sb.Append(".actions{margin-top:9px;} .action{display:inline-block;margin:3px 6px 3px 0;padding:8px 13px;border-radius:9px;text-decoration:none;font-weight:bold;color:#fff;box-shadow:0 4px 12px rgba(15,23,42,.22);} ");
            sb.Append(".take{background:#16a34a;} .donate{background:#f97316;} .setadd{background:#7c3aed;} .collect{background:#0891b2;} .delete{background:#dc2626;} .remove{background:#be123c;} .disabled{display:inline-block;color:#94a3b8;margin-top:6px;} .empty{padding:18px;border-radius:12px;background:#fff;color:#b91c1c;} ");
            sb.Append(".set-card{background:#fff;border-radius:14px;margin:10px 0;padding:12px;border:1px solid #dbe4ff;box-shadow:0 5px 18px rgba(15,23,42,.14);} .set-title{font-size:15px;font-weight:bold;color:#0f172a;margin-bottom:7px;} .set-uids{margin-top:8px;} ");
            sb.Append("</style>");
        }

        private static void AppendModeLinks(StringBuilder sb, ViewCounters counters, int viewMode)
        {
            sb.Append("<div class=\"modes\">");
            AppendModeLink(sb, ViewAll, "Все", counters.All, viewMode);
            AppendModeLink(sb, ViewArts, "Арты", counters.Arts, viewMode);
            AppendModeLink(sb, ViewRares, "Рары", counters.Rares, viewMode);
            AppendModeLink(sb, ViewOrdinary, "Обычные", counters.Ordinary, viewMode);
            AppendModeLink(sb, ViewSets, "Комплекты", KaznaSetStore.LoadSets().Count, viewMode);
            sb.Append("<a class=\"pill\" href=\"javascript:var n=prompt('Название комплекта');if(n){location='")
                .Append(Html(KaznaParser.BaseKaznaUrl))
                .Append("&an_kazna_view=4&an_kazna_action=create&an_kazna_set='+encodeURIComponent(n);}\">+ Комплект</a>");
            sb.Append("</div>");
        }

        private static void AppendModeLink(StringBuilder sb, int mode, string title, int count, int currentMode)
        {
            var css = mode == currentMode ? "pill pill-current" : "pill";
            sb.Append("<a class=\"").Append(css).Append("\" href=\"")
                .Append(Html(KaznaParser.BaseKaznaUrl))
                .Append("&an_kazna_view=")
                .Append(mode.ToString(CultureInfo.InvariantCulture))
                .Append("\">")
                .Append(Html(title)).Append(" ").Append(count).Append("</a>");
        }

        private static void AppendCategoryBar(StringBuilder sb, KaznaSnapshot snapshot, int viewMode)
        {
            if (snapshot == null || snapshot.Categories.Count == 0)
                return;

            var allClass = string.IsNullOrEmpty(snapshot.CurrentWca) ? "cat cat-current" : "cat";
            sb.Append("<div class=\"categories\"><a class=\"").Append(allClass).Append("\" href=\"").Append(Html(WithView(KaznaParser.BaseKaznaUrl, viewMode))).Append("\">Все категории</a>");
            for (var i = 0; i < snapshot.Categories.Count; i++)
            {
                var category = snapshot.Categories[i];
                var css = string.Equals(snapshot.CurrentWca, category.Wca, StringComparison.OrdinalIgnoreCase) ? "cat cat-current" : "cat";
                sb.Append("<a class=\"").Append(css).Append("\" title=\"").Append(Html(category.Title)).Append("\" href=\"").Append(Html(WithView(category.Href, viewMode))).Append("\">");
                if (!string.IsNullOrEmpty(category.IconUrl))
                    sb.Append("<img src=\"").Append(Html(category.IconUrl)).Append("\"> ");
                sb.Append(Html(string.IsNullOrEmpty(category.Title) ? category.Wca : category.Title)).Append("</a>");
            }
            sb.Append("</div>");
        }

        private static string WithView(string url, int viewMode)
        {
            var value = string.IsNullOrEmpty(url) ? KaznaParser.BaseKaznaUrl : url;
            var cut = value.IndexOf("an_kazna_view=", StringComparison.OrdinalIgnoreCase);
            if (cut != -1)
            {
                var amp = value.IndexOf('&', cut + "an_kazna_view=".Length);
                value = amp == -1 ? value.Substring(0, cut).TrimEnd('&', '?') : value.Substring(0, cut) + value.Substring(amp + 1);
            }

            return value + (value.IndexOf('?') == -1 ? "?" : "&") + "an_kazna_view=" + viewMode.ToString(CultureInfo.InvariantCulture);
        }

        private static void AppendItems(StringBuilder sb, KaznaSnapshot snapshot, Dictionary<string, KaznaItemDetails> detailsByUid, List<KaznaSet> sets, int viewMode)
        {
            if (snapshot == null || snapshot.Items.Count == 0)
            {
                sb.Append("<div class=\"empty\">Предметы казны не найдены в HTML-ответе.</div>");
                return;
            }

            var visibleIndex = 0;
            for (var i = 0; i < snapshot.Items.Count; i++)
            {
                var item = snapshot.Items[i];
                if (!IsViewMatch(item, viewMode))
                    continue;

                var details = FindDetails(item, detailsByUid);
                var rowClass = (visibleIndex % 2) == 0 ? "item item-even" : "item item-odd";
                visibleIndex++;
                sb.Append("<div class=\"").Append(rowClass).Append("\">");
                sb.Append("<div class=\"thumb\">");
                if (details != null && details.HasImage)
                    sb.Append("<img src=\"").Append(Html(details.ImageUrl)).Append("\">");
                else
                    sb.Append("<div style=\"width:50px;height:50px;border-radius:10px;background:#e2e8f0;text-align:center;line-height:50px;color:#64748b\">?</div>");
                sb.Append("</div><div class=\"body\">");
                sb.Append("<div class=\"name\">").Append(Html(item.DisplayName));
                if (!string.IsNullOrEmpty(item.ArtifactCoefficient))
                    sb.Append("<span class=\"coef\">").Append(Html(item.ArtifactCoefficient)).Append("</span>");
                sb.Append("</div>");
                AppendMeta(sb, item, details);
                if (details != null && details.HasProperties)
                    sb.Append("<div class=\"props\">").Append(Html(details.PropertiesText)).Append("</div>");
                else
                    sb.Append("<div class=\"props\">Свойства: информация не известна. Откройте инвентарь/информацию предмета, чтобы ANClient пополнил кеш.</div>");
                AppendActions(sb, item, sets);
                sb.Append("</div></div>");
            }

            if (visibleIndex == 0)
                sb.Append("<div class=\"empty\">В выбранном режиме предметов нет.</div>");
        }

        private static void AppendMeta(StringBuilder sb, KaznaItem item, KaznaItemDetails details)
        {
            sb.Append("<div class=\"meta\">");
            if (!string.IsNullOrEmpty(item.Uid))
                sb.Append("uid=").Append(Html(item.Uid)).Append(" | ");
            else if (details != null && !string.IsNullOrEmpty(details.Uid))
                sb.Append("uid=").Append(Html(details.Uid)).Append(" (из кеша) | ");
            if (!string.IsNullOrEmpty(item.Owner))
                sb.Append("В-инвентаре: ").Append(Html(item.Owner)).Append(" | ");
            if (!string.IsNullOrEmpty(item.DurabilityText))
                sb.Append("Долговечность: ").Append(Html(item.DurabilityText)).Append(" | ");
            if (!string.IsNullOrEmpty(item.Status))
                sb.Append(Html(item.Status));
            sb.Append("</div>");
        }

        private static void AppendActions(StringBuilder sb, KaznaItem item, List<KaznaSet> sets)
        {
            sb.Append("<div class=\"actions\">");
            var hasAction = false;
            var actionUid = ResolveActionUid(item);
            if (!string.IsNullOrEmpty(actionUid))
            {
                hasAction = true;
                if (sets != null && sets.Count > 0)
                {
                    for (var i = 0; i < sets.Count; i++)
                    {
                        sb.Append("<a class=\"action setadd\" href=\"")
                            .Append(Html(BuildSetActionUrl("add", sets[i].Name, actionUid)))
                            .Append("\">+ ")
                            .Append(Html(sets[i].Name))
                            .Append("</a>");
                    }
                }

                sb.Append("<a class=\"action setadd\" href=\"javascript:var n=prompt('Название комплекта');if(n){location='")
                    .Append(Html(KaznaParser.BaseKaznaUrl))
                    .Append("&an_kazna_action=add&an_kazna_uid=")
                    .Append(Html(Url(actionUid)))
                    .Append("&an_kazna_set='+encodeURIComponent(n);}\">+ Новый комплект</a>");
            }
            if (!string.IsNullOrEmpty(item.TakeUrl))
            {
                hasAction = true;
                sb.Append("<a class=\"action take\" href=\"").Append(Html(item.TakeUrl)).Append("\">Взять из казны</a>");
            }
            if (!string.IsNullOrEmpty(item.DonateUrl))
            {
                hasAction = true;
                sb.Append("<a class=\"action donate\" href=\"").Append(Html(item.DonateUrl)).Append("\">Пожертвовать</a>");
            }
            if (!hasAction)
                sb.Append("<span class=\"disabled\">Нет доступных действий для этой строки</span>");
            sb.Append("</div>");
        }

        private static void AppendSets(StringBuilder sb, List<KaznaSet> sets, KaznaSnapshot snapshot, Dictionary<string, KaznaItemDetails> detailsByUid)
        {
            sb.Append("<div class=\"categories\"><a class=\"action setadd\" href=\"javascript:var n=prompt('Название комплекта');if(n){location='")
                .Append(Html(KaznaParser.BaseKaznaUrl))
                .Append("&an_kazna_view=4&an_kazna_action=create&an_kazna_set='+encodeURIComponent(n);}\">Создать комплект</a></div>");

            if (sets == null || sets.Count == 0)
            {
                sb.Append("<div class=\"empty\">Комплектов нет. Откройте вкладку предметов и нажмите `+ Новый комплект` у нужной вещи.</div>");
                return;
            }

            for (var i = 0; i < sets.Count; i++)
            {
                var set = sets[i];
                sb.Append("<div class=\"set-card\"><div class=\"set-title\">").Append(Html(set.Name)).Append("</div>");
                sb.Append("<div class=\"actions\">");
                sb.Append("<a class=\"action collect\" href=\"").Append(Html(BuildSetActionUrl("collect", set.Name, string.Empty))).Append("\">Собрать</a>");
                sb.Append("<a class=\"action delete\" href=\"").Append(Html(BuildSetActionUrl("delete", set.Name, string.Empty))).Append("\">Удалить комплект</a>");
                sb.Append("</div><div class=\"set-uids\">");

                if (set.ItemUids.Count == 0)
                    sb.Append("<div class=\"disabled\">Комплект пуст</div>");

                for (var uidIndex = 0; uidIndex < set.ItemUids.Count; uidIndex++)
                {
                    var uid = set.ItemUids[uidIndex];
                    var item = snapshot == null ? null : snapshot.FindItemByUid(uid);
                    KaznaItemDetails details = null;
                    if (detailsByUid != null)
                        detailsByUid.TryGetValue(uid, out details);

                    sb.Append("<div class=\"").Append((uidIndex % 2) == 0 ? "item item-even" : "item item-odd").Append("\">");
                    sb.Append("<div class=\"thumb\">");
                    if (details != null && details.HasImage)
                        sb.Append("<img src=\"").Append(Html(details.ImageUrl)).Append("\">");
                    else
                        sb.Append("<div style=\"width:50px;height:50px;border-radius:10px;background:#e2e8f0;text-align:center;line-height:50px;color:#64748b\">?</div>");
                    sb.Append("</div><div class=\"body\"><div class=\"name\">").Append(Html(SetItemTitle(item, details, uid))).Append("</div>");
                    if (details != null && details.HasProperties)
                        sb.Append("<div class=\"props\">").Append(Html(details.PropertiesText)).Append("</div>");
                    else
                        sb.Append("<div class=\"meta\">uid=").Append(Html(uid)).Append("</div>");
                    sb.Append("<div class=\"actions\"><a class=\"action remove\" href=\"").Append(Html(BuildSetActionUrl("remove", set.Name, uid))).Append("\">Убрать из комплекта</a></div>");
                    sb.Append("</div></div>");
                }

                sb.Append("</div></div>");
            }
        }

        private static string ResolveActionUid(KaznaItem item)
        {
            if (item == null)
                return string.Empty;
            return string.IsNullOrEmpty(item.Uid) ? string.Empty : item.Uid;
        }

        private static string BuildSetActionUrl(string action, string setName, string uid)
        {
            var url = KaznaParser.BaseKaznaUrl + "&an_kazna_view=4&an_kazna_action=" + Url(action) + "&an_kazna_set=" + Url(setName);
            if (!string.IsNullOrEmpty(uid))
                url += "&an_kazna_uid=" + Url(uid);
            return url;
        }

        private static string SetItemTitle(KaznaItem item, KaznaItemDetails details, string uid)
        {
            if (item != null && !string.IsNullOrEmpty(item.DisplayName))
                return item.DisplayName;
            if (details != null && !string.IsNullOrEmpty(details.Name))
                return details.Name;
            return "uid=" + (uid ?? string.Empty);
        }

        private static string Url(string value)
        {
            return HttpUtility.UrlEncode(value ?? string.Empty);
        }

        private static KaznaItemDetails FindDetails(KaznaItem item, Dictionary<string, KaznaItemDetails> detailsByUid)
        {
            if (item == null || detailsByUid == null || detailsByUid.Count == 0)
                return null;

            KaznaItemDetails direct;
            if (!string.IsNullOrEmpty(item.Uid) && detailsByUid.TryGetValue(item.Uid, out direct))
                return direct;

            var itemName = NormalizeName(string.IsNullOrEmpty(item.BaseName) ? item.DisplayName : item.BaseName);
            if (string.IsNullOrEmpty(itemName))
                return null;

            KaznaItemDetails best = null;
            var bestScore = 0;
            foreach (var details in detailsByUid.Values)
            {
                var score = ScoreDetailsMatch(item, itemName, details);
                if (score > bestScore)
                {
                    bestScore = score;
                    best = details;
                }
            }

            return bestScore >= 100 ? best : null;
        }

        private static int ScoreDetailsMatch(KaznaItem item, string itemName, KaznaItemDetails details)
        {
            if (details == null)
                return 0;

            var detailsName = NormalizeName(details.Name);
            if (string.IsNullOrEmpty(detailsName) || !detailsName.Equals(itemName, StringComparison.Ordinal))
                return 0;

            var properties = NormalizeSearchText(details.PropertiesText);
            var score = 70;
            if (item.HasArtifactCoefficient)
            {
                var coefficient = NormalizeSearchText(item.ArtifactCoefficient);
                if (string.IsNullOrEmpty(coefficient) || properties.IndexOf(coefficient, StringComparison.Ordinal) == -1)
                    return 0;
                score += 40;
            }

            if (!string.IsNullOrEmpty(item.DurabilityText))
            {
                var durability = NormalizeSearchText(item.DurabilityText);
                if (!string.IsNullOrEmpty(durability) && properties.IndexOf(durability, StringComparison.Ordinal) != -1)
                    score += 25;
                else if (properties.IndexOf("долговечность", StringComparison.Ordinal) != -1)
                    return 0;
            }

            if (details.HasProperties)
                score += 10;
            if (details.HasImage)
                score += 5;
            return score;
        }

        private static string NormalizeSearchText(string value)
        {
            return Regex.Replace(value ?? string.Empty, @"\s+", " ").Trim().ToLowerInvariant();
        }

        private static string NormalizeName(string value)
        {
            return Regex.Replace(NormalizeSearchText(value), @"(?<!\d)[12]\.\d{2}(?!\d)", string.Empty).Trim();
        }

        private static ViewCounters Count(KaznaSnapshot snapshot)
        {
            var counters = new ViewCounters();
            if (snapshot == null)
                return counters;

            for (var i = 0; i < snapshot.Items.Count; i++)
            {
                var item = snapshot.Items[i];
                counters.All++;
                if (item.HasArtifactCoefficient)
                    counters.Arts++;
                else if (item.IsRare)
                    counters.Rares++;
                else if (item.IsOrdinary)
                    counters.Ordinary++;
            }

            return counters;
        }

        private static int CountForMode(ViewCounters counters, int viewMode)
        {
            switch (viewMode)
            {
                case ViewRares:
                    return counters.Rares;
                case ViewArts:
                    return counters.Arts;
                case ViewOrdinary:
                    return counters.Ordinary;
                default:
                    return counters.All;
            }
        }

        private static string Html(string value)
        {
            return HttpUtility.HtmlEncode(value ?? string.Empty);
        }

        private sealed class ViewCounters
        {
            internal int All;
            internal int Rares;
            internal int Arts;
            internal int Ordinary;
        }
    }
}
