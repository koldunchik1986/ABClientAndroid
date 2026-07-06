using System.Collections.Generic;
using System;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using ANClient.ANForms;
using ANClient.ExtMap;
using ANClient.Helpers;
using ANClient.MyChat;
using ANClient.MyHelpers;
using ANClient.MySounds;

namespace ANClient.PostFilter
{
    internal static partial class Filter
    {
        private static void FilterGetLocation(string url)
        {
            var regX = new Regex(@"&gx=([\d]+)");
            var matchX = regX.Match(url);
            if (matchX.Groups.Count < 2)
            {
                return;
            }

            var gx = Convert.ToInt32(matchX.Groups[1].Value, CultureInfo.InvariantCulture);
            var regY = new Regex(@"&gy=([\d]+)");
            var matchY = regY.Match(url);
            if (matchY.Groups.Count < 2)
            {
                return;
            }

            var gy = Convert.ToInt32(matchY.Groups[1].Value, CultureInfo.InvariantCulture);
            AppVars.LocationReal = Map.ConvertToRegNum(gx, gy);
            AppLog.i("MainPhp", "FilterGetLocation: gx=" + gx + " gy=" + gy + " => " + AppVars.LocationReal);

            /*
            if (Map.AncCells.ContainsKey(AppVars.LocationReal))
            {
                Map.AncCells[AppVars.LocationReal].Visited = DateTime.Now;
            }
            */
        }

        private static bool MainPhpIsAutoCutInventoryAddress(string address)
        {
            if (string.IsNullOrEmpty(address))
            {
                return false;
            }

            return address.IndexOf("main.php", StringComparison.OrdinalIgnoreCase) >= 0 &&
                   address.IndexOf("go=inv", StringComparison.OrdinalIgnoreCase) >= 0 &&
                   address.IndexOf("im=0", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static bool MainPhpIsAutoCutCleanupInventoryAddress(string address)
        {
            if (string.IsNullOrEmpty(address) ||
                address.IndexOf("main.php", StringComparison.OrdinalIgnoreCase) < 0)
            {
                return false;
            }

            if (address.IndexOf("get_id=50", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                return true;
            }

            if (MainPhpIsAutoCutGarbageCleanup() &&
                address.IndexOf("wca=60", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                return true;
            }

            return address.IndexOf("im=0", StringComparison.OrdinalIgnoreCase) >= 0 &&
                   address.IndexOf("wca=", StringComparison.OrdinalIgnoreCase) < 0;
        }

        private static bool MainPhpIsAutoCutGarbageCleanup()
        {
            return AppVars.AutoCutCleanupPending &&
                   !string.IsNullOrEmpty(AppVars.BulkDropThing) &&
                   AppVars.BulkDropThing.Equals(AutoCutRuntime.GarbageItemName, StringComparison.CurrentCultureIgnoreCase);
        }

        private static bool MainPhpShouldSwitchToGarbageInventoryCategory(string address)
        {
            return AutoCutRuntime.IsAutoCutLikeEnabled() &&
                   MainPhpIsAutoCutGarbageCleanup() &&
                   (string.IsNullOrEmpty(address) ||
                    address.IndexOf("wca=60", StringComparison.OrdinalIgnoreCase) < 0);
        }

        private static bool MainPhpHasAutoCutDropPending()
        {
            return AppVars.AutoCutCleanupPending && !string.IsNullOrEmpty(AppVars.BulkDropThing);
        }

        private static string MainPhpAutoCutCleanupRedirect(string address, string html, string modeTitle, string source)
        {
            if (!AppVars.AutoCutCleanupPending || MainPhpIsInv(html))
            {
                return string.Empty;
            }

            var now = DateTime.Now;
            if (now <= AppVars.NeverTimer)
            {
                var waitMs = Math.Max(0, (int)AppVars.NeverTimer.Subtract(now).TotalMilliseconds);
                AppLog.d("auto_cut_trace", "MainPhp", "cleanup waits NeverTimer before inventory redirect: source=" + source +
                    ", waitMs=" + waitMs.ToString(CultureInfo.InvariantCulture));
                AutoCutRuntime.ScheduleLookRetry("cleanup_wait:" + source);
                return string.Empty;
            }

            var cleanupInvHtml = MainPhpFindInv(html, "&im=0");
            if (!string.IsNullOrEmpty(cleanupInvHtml))
            {
                AppLog.i("auto_cut_trace", "MainPhp", "cleanup inventory redirect via link: source=" + source);
                return cleanupInvHtml;
            }

            if (!MainPhpIsAutoCutCleanupInventoryAddress(address))
            {
                AppLog.i("auto_cut_trace", "MainPhp", "cleanup inventory redirect: source=" + source);
                return BuildRedirect(modeTitle + ": cleanup инвентаря", "main.php?im=0");
            }

            return string.Empty;
        }

        private static string[] GetComplects(string html)
        {
            /*
               compl_view("11","2000646344f0c5a13d362b","0ba0906db5c991b14c162922e7be1ceb");
               compl_view("test 2013","2066004990523ac2c7cf6a5","62eaf967bcd608cd89f37dceec194474");
             */

            if (string.IsNullOrEmpty(html))
                return null;

            var list = new List<string>();
            int pos = 0;
            while ((pos >= 0) && (pos < html.Length))
            {
                pos = html.IndexOf(@"compl_view(""", pos, StringComparison.Ordinal);
                if (pos == -1)
                    break;

                pos += @"compl_view(""".Length;
                var pos1 = pos;
                if (pos1 >= html.Length)
                    break;

                pos1 = html.IndexOf(@"""", pos1, StringComparison.Ordinal);
                if (pos1 == -1)
                    break;

                var compl = html.Substring(pos, pos1 - pos);
                list.Add(compl);

                pos = pos1 + 1;
            }

            return list.Count == 0 ? null : list.ToArray();
        }

        private const int ClanKaznaViewAll = 0;
        private const int ClanKaznaViewRares = 1;
        private const int ClanKaznaViewArts = 2;
        private const int ClanKaznaViewOrdinary = 3;
        private const int ClanKaznaViewSets = 4;
        private const string ClanKaznaUrl = "main.php?wfo=1&useaction=clan-action&addid=3";
        private const string ClanKaznaTakePrefix = "main.php?get_id=29&uid=";
        private static readonly Regex ClanKaznaRowRegex = new Regex(@"<tr><td bgcolor=#f.*?</div></td></tr>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex ClanKaznaDurabilityRegex = new Regex(@"(\d+)\s*/\s*(\d+)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex ClanKaznaArtifactRegex = new Regex(@"(?<!\d)([12]\.\d{2})(?!\d)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex ClanKaznaHtmlTagRegex = new Regex(@"<[^>]+>", RegexOptions.IgnoreCase | RegexOptions.Singleline);

        private sealed class ClanKaznaViewCounters
        {
            internal int All;
            internal int Rares;
            internal int Arts;
            internal int Ordinary;
        }

        private static bool MainPhpIsClanKazna(string address, string html)
        {
            if (!string.IsNullOrEmpty(address) &&
                address.IndexOf("useaction=clan-action&addid=3", StringComparison.OrdinalIgnoreCase) != -1)
            {
                return true;
            }

            return !string.IsNullOrEmpty(html) &&
                   (html.IndexOf("СПИСОК ВЕЩЕЙ КЛАНА", StringComparison.CurrentCultureIgnoreCase) != -1 ||
                    html.IndexOf(ClanKaznaTakePrefix, StringComparison.OrdinalIgnoreCase) != -1);
        }

        private static string MainPhpClanKazna(string address, string html)
        {
            MainPhpClanKaznaUpdateViewModeFromAddress(address);
            var actionResult = MainPhpClanKaznaHandleAction(address);
            if (!string.IsNullOrEmpty(actionResult))
                return actionResult;

            var isClanKazna = MainPhpIsClanKazna(address, html);
            if (!isClanKazna)
                return BuildRedirect("Переходим в казну", ClanKaznaUrl);

            AppVars.ClanKaznaOpenRequested = false;
            if (!string.IsNullOrEmpty(AppVars.ClanKaznaComplectQueue))
                return MainPhpClanKaznaTakeNext(html);

            if (!string.IsNullOrEmpty(AppVars.ClanKaznaComplectName))
            {
                if (AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Взятие комплекта из казны завершено: " + AppVars.ClanKaznaComplectName);

                AppVars.ClanKaznaComplectName = string.Empty;
            }

            return MainPhpClanKaznaApplyView(address, html);
        }

        private static string MainPhpClanKaznaHandleAction(string address)
        {
            var action = MainPhpClanKaznaQuery(address, "an_kazna_action");
            if (string.IsNullOrEmpty(action))
                return string.Empty;

            var setName = MainPhpClanKaznaQuery(address, "an_kazna_set");
            var uid = MainPhpClanKaznaQuery(address, "an_kazna_uid");
            if (action.Equals("create", StringComparison.OrdinalIgnoreCase))
            {
                if (ANClient.Kazna.KaznaSetStore.AddSet(setName) && AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Комплект казны создан: " + setName);
                return string.Empty;
            }

            if (action.Equals("add", StringComparison.OrdinalIgnoreCase))
            {
                if (ANClient.Kazna.KaznaSetStore.AddItemToSet(setName, uid) && AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Предмет " + uid + " добавлен в комплект казны: " + setName);
                return string.Empty;
            }

            if (action.Equals("remove", StringComparison.OrdinalIgnoreCase))
            {
                if (ANClient.Kazna.KaznaSetStore.RemoveItemFromSet(setName, uid) && AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Предмет " + uid + " удалён из комплекта казны: " + setName);
                return string.Empty;
            }

            if (action.Equals("delete", StringComparison.OrdinalIgnoreCase))
            {
                if (ANClient.Kazna.KaznaSetStore.DeleteSet(setName) && AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Комплект казны удалён: " + setName);
                return string.Empty;
            }

            if (action.Equals("collect", StringComparison.OrdinalIgnoreCase))
            {
                var set = ANClient.Kazna.KaznaSetStore.FindSet(setName);
                if (set == null || set.ItemUids.Count == 0)
                {
                    if (AppVars.MainForm != null)
                        AppVars.MainForm.WriteChatMsgSafe("Комплект казны пуст или не найден: " + setName);
                    return string.Empty;
                }

                AppVars.ClanKaznaComplectName = set.Name;
                AppVars.ClanKaznaComplectQueue = string.Join(":", set.ItemUids.ToArray());
                if (AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Заказано взятие комплекта из казны: " + set.Name);
                return string.Empty;
            }

            return string.Empty;
        }

        private static string MainPhpClanKaznaTakeNext(string html)
        {
            var queue = MainPhpClanKaznaBuildQueue(AppVars.ClanKaznaComplectQueue);
            while (queue.Count > 0)
            {
                var itemId = queue.Dequeue();
                var link = MainPhpClanKaznaFindTakeLink(html, itemId);
                AppVars.ClanKaznaComplectQueue = string.Join(":", queue.ToArray());
                if (string.IsNullOrEmpty(link))
                {
                    if (AppVars.MainForm != null)
                        AppVars.MainForm.WriteChatMsgSafe("В казне не найден предмет ID " + itemId + ".");

                    continue;
                }

                return BuildRedirect("Берём из казны ID " + itemId + "...", link);
            }

            if (AppVars.MainForm != null && !string.IsNullOrEmpty(AppVars.ClanKaznaComplectName))
                AppVars.MainForm.WriteChatMsgSafe("Взятие комплекта из казны завершено: " + AppVars.ClanKaznaComplectName);

            AppVars.ClanKaznaComplectQueue = string.Empty;
            AppVars.ClanKaznaComplectName = string.Empty;
            return MainPhpClanKaznaApplyView(string.Empty, html);
        }

        private static Queue<string> MainPhpClanKaznaBuildQueue(string source)
        {
            var queue = new Queue<string>();
            if (string.IsNullOrEmpty(source))
                return queue;

            var items = source.Split(new[] { ':' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (var item in items)
            {
                var itemId = item.Trim();
                if (!string.IsNullOrEmpty(itemId))
                    queue.Enqueue(itemId);
            }

            return queue;
        }

        private static string MainPhpClanKaznaFindTakeLink(string html, string itemId)
        {
            if (string.IsNullOrEmpty(html) || string.IsNullOrEmpty(itemId))
                return null;

            var prefix = ClanKaznaTakePrefix + itemId;
            var pos = 0;
            while (pos >= 0 && pos < html.Length)
            {
                pos = html.IndexOf(prefix, pos, StringComparison.OrdinalIgnoreCase);
                if (pos == -1)
                    return null;

                var end = MainPhpClanKaznaFindLinkEnd(html, pos);
                if (end == -1)
                    return null;

                return html.Substring(pos, end - pos).Replace("&amp;", "&");
            }

            return null;
        }

        private static int MainPhpClanKaznaFindLinkEnd(string html, int pos)
        {
            var singleQuote = html.IndexOf('\'', pos);
            var doubleQuote = html.IndexOf('"', pos);
            if (singleQuote == -1)
                return doubleQuote;

            if (doubleQuote == -1)
                return singleQuote;

            return Math.Min(singleQuote, doubleQuote);
        }

        private static string MainPhpClanKaznaApplyView(string address, string html)
        {
            try
            {
                AppLog.d("Kazna", "render start: address=" + (address ?? string.Empty) + ", mode=" + AppVars.ClanKaznaViewMode.ToString(CultureInfo.InvariantCulture));
                var rendered = ANClient.Kazna.KaznaHtmlRenderer.Render(html, address, AppVars.ClanKaznaViewMode);
                AppLog.d("Kazna", "render complete: address=" + (address ?? string.Empty));
                return rendered;
            }
            catch (Exception ex)
            {
                AppLog.e("Kazna", "render failed, original clan kazna html returned: " + (address ?? string.Empty), ex);
                return html;
            }
        }

        private static void MainPhpClanKaznaUpdateViewModeFromAddress(string address)
        {
            if (string.IsNullOrEmpty(address))
                return;

            var match = Regex.Match(address, @"[?&]an_kazna_view=(\d+)", RegexOptions.IgnoreCase);
            if (!match.Success || match.Groups.Count < 2)
                return;

            int mode;
            if (!int.TryParse(match.Groups[1].Value, out mode))
                return;

            if (mode < ClanKaznaViewAll || mode > ClanKaznaViewSets)
                mode = ClanKaznaViewAll;

            AppVars.ClanKaznaViewMode = mode;
        }

        private static string MainPhpClanKaznaQuery(string address, string name)
        {
            if (string.IsNullOrEmpty(address) || string.IsNullOrEmpty(name))
                return string.Empty;

            var match = Regex.Match(address, @"[?&]" + Regex.Escape(name) + @"=([^&]*)", RegexOptions.IgnoreCase);
            if (!match.Success || match.Groups.Count < 2)
                return string.Empty;

            return Uri.UnescapeDataString(match.Groups[1].Value.Replace('+', ' '));
        }

        private static ClanKaznaViewCounters MainPhpClanKaznaCountRows(string html)
        {
            var counters = new ClanKaznaViewCounters();
            if (string.IsNullOrEmpty(html))
                return counters;

            var matches = ClanKaznaRowRegex.Matches(html);
            foreach (Match match in matches)
            {
                var row = match.Value;
                if (!MainPhpClanKaznaIsItemRow(row))
                    continue;

                counters.All++;
                if (MainPhpClanKaznaIsModeMatch(row, ClanKaznaViewArts))
                {
                    counters.Arts++;
                }
                else if (MainPhpClanKaznaIsModeMatch(row, ClanKaznaViewRares))
                {
                    counters.Rares++;
                }
                else if (MainPhpClanKaznaIsModeMatch(row, ClanKaznaViewOrdinary))
                {
                    counters.Ordinary++;
                }
            }

            return counters;
        }

        private static string MainPhpClanKaznaInjectHeader(string html, ClanKaznaViewCounters counters)
        {
            var modeName = MainPhpClanKaznaModeName(AppVars.ClanKaznaViewMode);
            var visibleCount = MainPhpClanKaznaModeCount(counters, AppVars.ClanKaznaViewMode);
            var panel = new StringBuilder();
            panel.Append("<div style=\"padding:6px;text-align:center;background:#FCFAF3;color:#6B4E16;font-family:Verdana;font-size:11px;border-bottom:1px solid #D4C9A4\">");
            panel.Append("<b>ANClient: клановая казна</b>");
            panel.Append("<br>Режим: <b>").Append(modeName).Append("</b>, показано ").Append(visibleCount).Append(" из ").Append(counters.All);
            panel.Append("<br>");
            MainPhpClanKaznaAppendModeLink(panel, ClanKaznaViewAll, "Все", counters.All);
            MainPhpClanKaznaAppendModeLink(panel, ClanKaznaViewArts, "Арты", counters.Arts);
            MainPhpClanKaznaAppendModeLink(panel, ClanKaznaViewRares, "Рары", counters.Rares);
            MainPhpClanKaznaAppendModeLink(panel, ClanKaznaViewOrdinary, "Обычные", counters.Ordinary);
            panel.Append("<br><span style=\"font-size:10px;color:#8B7650\">Классификация как в Android `Казна`: арт = коэффициент 1.xx/2.xx, рар = без коэффициента и долговечность >= 300.</span>");
            panel.Append("</div>");
            var body = html.IndexOf("<body", StringComparison.OrdinalIgnoreCase);
            if (body == -1)
                return panel.ToString() + html;

            var bodyEnd = html.IndexOf('>', body);
            return bodyEnd == -1 ? panel.ToString() + html : html.Insert(bodyEnd + 1, panel.ToString());
        }

        private static void MainPhpClanKaznaAppendModeLink(StringBuilder panel, int mode, string title, int count)
        {
            var isCurrent = AppVars.ClanKaznaViewMode == mode;
            if (isCurrent)
            {
                panel.Append(" <b style=\"color:#8B0000\">").Append(title).Append(" (").Append(count).Append(")</b> ");
                return;
            }

            panel.Append(" <a style=\"color:#3564A5;font-weight:bold\" href=\"")
                .Append(MainPhpClanKaznaBuildModeUrl(mode))
                .Append("\">")
                .Append(title)
                .Append(" (")
                .Append(count)
                .Append(")</a> ");
        }

        private static string MainPhpClanKaznaBuildModeUrl(int mode)
        {
            return ClanKaznaUrl + "&an_kazna_view=" + mode.ToString(CultureInfo.InvariantCulture);
        }

        private static int MainPhpClanKaznaModeCount(ClanKaznaViewCounters counters, int mode)
        {
            switch (mode)
            {
                case ClanKaznaViewRares:
                    return counters.Rares;
                case ClanKaznaViewArts:
                    return counters.Arts;
                case ClanKaznaViewOrdinary:
                    return counters.Ordinary;
                default:
                    return counters.All;
            }
        }

        private static bool MainPhpClanKaznaIsModeMatch(string row, int mode)
        {
            if (!MainPhpClanKaznaIsItemRow(row))
                return true;

            var artifact = MainPhpClanKaznaHasArtifactCoefficient(row);
            var maxDurability = MainPhpClanKaznaMaxDurability(row);
            if (mode == ClanKaznaViewArts)
            {
                return artifact;
            }

            if (mode == ClanKaznaViewRares)
            {
                return !artifact && maxDurability >= 300;
            }

            if (mode == ClanKaznaViewOrdinary)
            {
                return !artifact && maxDurability >= 0 && maxDurability < 300;
            }

            return true;
        }

        private static bool MainPhpClanKaznaIsItemRow(string row)
        {
            if (string.IsNullOrEmpty(row))
                return false;

            return row.IndexOf("Долговечность", StringComparison.CurrentCultureIgnoreCase) != -1 &&
                   ClanKaznaDurabilityRegex.IsMatch(MainPhpClanKaznaPlainText(row));
        }

        private static bool MainPhpClanKaznaHasArtifactCoefficient(string row)
        {
            return !string.IsNullOrEmpty(row) && ClanKaznaArtifactRegex.IsMatch(MainPhpClanKaznaPlainText(row));
        }

        private static int MainPhpClanKaznaMaxDurability(string row)
        {
            if (string.IsNullOrEmpty(row))
                return -1;

            var match = ClanKaznaDurabilityRegex.Match(MainPhpClanKaznaPlainText(row));
            if (!match.Success || match.Groups.Count < 3)
                return -1;

            int max;
            return int.TryParse(match.Groups[2].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out max) ? max : -1;
        }

        private static string MainPhpClanKaznaPlainText(string html)
        {
            if (string.IsNullOrEmpty(html))
                return string.Empty;

            var text = html.Replace("&nbsp;", " ").Replace("&amp;", "&");
            text = ClanKaznaHtmlTagRegex.Replace(text, " ");
            return Regex.Replace(text, @"\s+", " ").Trim();
        }

        private static string MainPhpClanKaznaModeName(int mode)
        {
            switch (mode)
            {
                case ClanKaznaViewRares:
                    return "Рары";
                case ClanKaznaViewArts:
                    return "Арты";
                case ClanKaznaViewOrdinary:
                    return "Обычные";
                default:
                    return "Все";
            }
        }

        private static string MainPhpRoulette(string html)
        {
            var pos1 = html.IndexOf("<object", StringComparison.Ordinal);
            if (pos1 == -1)
                return html;

            var pos2 = html.IndexOf("</object>", pos1 + "<object".Length, StringComparison.Ordinal);
            if (pos2 == -1)
                return html;

            pos2 += "</object>".Length;

            var jackpot = HelperStrings.SubString(html, @"""jackpot"" value=""", @""""); // $1579.70
            var isfree = HelperStrings.SubString(html, @"""is_free"" value=""", @""""); // 1
            var enabled = HelperStrings.SubString(html, @"""enabled"" value=""", @""""); // 1
            var paidtext = HelperStrings.SubString(html, @"""paid_text"" value=""", @""""); // 0.2 DNV

            var sb = new StringBuilder();
            sb.AppendFormat("Текущий джекпот: <b>{0}</b><br><br>", jackpot);

            //isfree = "0";
            //enabled = "0";

            if (isfree.Equals("1"))
            {
                sb.AppendFormat(@"<input type=""button"" onclick=""javascript:start_roulette('free');"" value=""Крутить рулетку бесплатно"" class=""lbut""");
            }
            else
            {
                sb.AppendFormat(@"<input type=""button"" onclick=""javascript:start_roulette('1dnv');"" value=""Заплатить {0} и крутить рулетку"" class=""lbut""", paidtext);
            }

            if (!enabled.Equals("1"))
                sb.AppendFormat(@"disabled");

            sb.AppendFormat(@" /><br><br>");

            html = html.Substring(0, pos1) + sb + html.Substring(pos2);

            return html;
        }

        private static byte[] MainPhp(string address, byte[] array)
        {
            AppLog.d("MainPhp", "MainPhp: processing address=" + address);
            FilterGetLocation(address);

            AppVars.IdleTimer = DateTime.Now;
            AppVars.LastMainPhp = DateTime.Now;
            AppVars.ContentMainPhp = null;

            var html = Russian.Codepage.GetString(array);
            html = RemoveDoctype(html);

            /*
            if (html.IndexOf("view_map();", StringComparison.CurrentCultureIgnoreCase) != -1)
            {
                //html = html.Replace("<HEAD>", "<HEAD><meta http-equiv=\"x-ua-compatible\" content=\"IE=9\">");
                html = "<!DOCTYPE html>" + html;
            }
            */

            // view_map();
            // <meta http-equiv="x-ua-compatible" content="IE=7" >


            //var html = File.ReadAllText("telep.txt", AppVars.Codepage);
            //var html = File.ReadAllText("arena2.txt", AppVars.Codepage);
            // html = File.ReadAllText("noinv.txt", AppVars.Codepage);

            AppVars.ContentMainPhp = html;
            EventSounds.PlayRefresh();

            // Линки под картой
            /*
            if (html.Contains("<map name=\"links\""))
            {
                var sb = new StringBuilder();
                var pos = -1;
                do
                {
                    // AREA SHAPE="POLYGON" 
                    // HREF="main.php?get_id=56&act=10&go=build&pl=tarena1&vcode=fbcf6e1e1f13168c974a674ad355a26e"  
                    // COORDS="335,254,357,235,355,184,347,190,342,185,347,167,342,157,342,135,315,106,259,91,199,92,132,106,84,135,72,157,79,242,97,254" 
                    // onmouseover="tooltip(this,'Малая Арена')" onmouseout="hide_info(this)" >
                    //

                    const string strL1 = "<area shape=\"poly\" HREF=\"";
                    const string strL1Old = "<AREA SHAPE=\"POLYGON\" HREF=\"";
                    var strlen = strL1.Length;
                    var px = html.IndexOf(strL1, pos + 1, StringComparison.InvariantCultureIgnoreCase);
                    if (px == -1)
                    {
                        strlen = strL1Old.Length;
                        px = html.IndexOf(strL1Old, pos + 1, StringComparison.InvariantCultureIgnoreCase);
                        if (px == -1)
                            break;
                    }

                    pos = px;
                    var p1 = html.IndexOf("\"", pos + strlen, StringComparison.Ordinal);
                    var link = html.Substring(pos + strlen, p1 - pos - strlen);
                    const string strL2 = @"tooltip(this,'";
                    var pos2 = html.IndexOf(strL2, p1, StringComparison.Ordinal);
                    var p2 = html.IndexOf(@"'", pos2 + strL2.Length, StringComparison.Ordinal);
                    var text = html.Substring(pos2 + strL2.Length, p2 - pos2 - strL2.Length);
                    if (sb.Length > 0)
                    {
                        sb.Append(" | ");
                    }

                    sb.AppendFormat(@"<a href=""{0}"" style=""font-family: Verdana; font-size: 10px; color: #3564A5; white-space: nowrap;""><b>{1}</b></a>", link, text);
                }
                while (pos != -1);

                const string strEnd = @"USEMAP=""#links""></td></tr>";
                pos = html.IndexOf(strEnd, StringComparison.InvariantCultureIgnoreCase);
                if (pos != -1)
                {
                    html = html.Insert(pos + strEnd.Length,
                        @"<tr><td style=""padding: 10 10 10 10; text-align:center; font-size: 10px;"">" +
                        sb +
                        @"</td></tr>");
                }
            }
            */

            // Системное сообщение

            var sysMessage = HelperStrings.SubString(html, "<font class=nickname><font color=#cc0000><b>", "<br><br></b></font></font>");
            if (!string.IsNullOrEmpty(sysMessage))
            {
                AppLog.i("MainPhp", "MainPhp: system message found: " + sysMessage);
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateWriteChatMsgDelegate(AppVars.MainForm.WriteChatMsg), $"<font color=#cc0000><b>{sysMessage}</b></font>");
                    }
                }
                catch (InvalidOperationException)
                {
                }                
            }

            UnderAttack.Parse(html);

            if (html.IndexOf("magic_slots();", StringComparison.OrdinalIgnoreCase) != -1)
            {
                AppLog.i("MainPhp", "MainPhp: FIGHT PAGE DETECTED (magic_slots), priority before automation");
                html = MainPhpFight(html);
                goto end;
            }

            if (AppVars.ClanKaznaOpenRequested ||
                !string.IsNullOrEmpty(AppVars.ClanKaznaComplectQueue) ||
                MainPhpIsClanKazna(address, html))
            {
                html = MainPhpClanKazna(address, html);
                goto end;
            }

            /*             
            if (AppVars.ServerDateTime >= new DateTime(2017, 11, 5))
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateWriteChatMsgDelegate(AppVars.MainForm.FormMainClose),
                            "Обновите версию ANClient!");
                    }
                }
                catch (InvalidOperationException)
                {
                }

                return null;
            }
            */

            var poisonAndCure = GetPoisonAndWounds(html);
            if (poisonAndCure != null)
                AppVars.PoisonAndWounds = poisonAndCure;

            if (!string.IsNullOrEmpty(AppVars.Profile.Complects))
            {               
                var par = AppVars.Profile.Complects.Split('|');                
                if (par.Length > 0)
                {
                    if (string.IsNullOrEmpty(AppVars.Profile.AutoWearComplect))
                    {
                        AppVars.Profile.AutoWearComplect = par[Dice.Make(par.Length)];
                        if (AppVars.MainForm != null)
                            AppVars.MainForm.WriteChatMsgSafe(
                                $"Комплект для одевания не был указан; теперь - ({AppVars.Profile.AutoWearComplect})");
                    }
                    else
                    {
                        var i = 0;
                        while (i < par.Length)
                        {
                            if (par[i].Equals(AppVars.Profile.AutoWearComplect))
                                break;
                            i++;
                        }

                        if (i == par.Length)
                        {
                            AppVars.Profile.AutoWearComplect = par[Dice.Make(par.Length)];
                            if (AppVars.MainForm != null)
                                AppVars.MainForm.WriteChatMsgSafe($"Комплект для одевания был указан неверно; теперь - ({AppVars.Profile.AutoWearComplect})");
                        }
                    }
                }
            }

            //var xhtml = "var logs = [9,[[0,\"16:35\"],[11,0,\"Попытка кражи не удалась\",0]],[[0,\"16:35\"],\"<B>Победа за</B>\",[1,1,\"Черный\",16,0,\"\"],\".\"],[";
            var robMessage = HelperStrings.SubString(html, "],[11,", "]");
            if (!string.IsNullOrEmpty(robMessage))
            {
                var args = robMessage.Split(',');
                if (args.Length == 3)
                {
                    var message = args[1];
                    if (!string.IsNullOrEmpty(message))
                    {
                        try
                        {
                            if (AppVars.MainForm != null)
                            {
                                AppVars.MainForm.BeginInvoke(
                                    new UpdateWriteChatMsgDelegate(AppVars.MainForm.WriteChatMsg), $"Результат воровства: <font color=#cc0000><b>{message}</b></font>");
                            }
                        }
                        catch (InvalidOperationException)
                        {
                        }
                    }
                }
            }

            if (address.EndsWith("?mselect=15"))
            {
                html = MainPhpRoulette(html);
                goto end;
            }

            // Нужно ли обновлять ожидание боя?
            if (html.IndexOf("var arpar = [", StringComparison.CurrentCultureIgnoreCase) != -1 &&
                html.IndexOf(",\"Ожидаем начала боя!\"];", StringComparison.CurrentCultureIgnoreCase) != -1)
            {
                AppLog.i("MainPhp", "MainPhp: fight waiting detected (arpar)");
                var data = HelperStrings.SubString(html, "var data = [", "];");
                if (!string.IsNullOrEmpty(data))
                {
                    if (
                        data.IndexOf("\"Королева Змей\"", StringComparison.CurrentCultureIgnoreCase) != -1 ||
                        data.IndexOf("\"Хранитель Леса\"", StringComparison.CurrentCultureIgnoreCase) != -1 ||
                        data.IndexOf("\"Громлех Синезубый\"", StringComparison.CurrentCultureIgnoreCase) != -1 ||
                        data.IndexOf("\"Выползень\"", StringComparison.CurrentCultureIgnoreCase) != -1
                        )
                    {
                        var sb = new StringBuilder(HelperErrors.Head());
                        sb.AppendLine("Ожидаем начала боя!");
                        sb.AppendLine(@"<script language=""JavaScript"">");
                        sb.AppendLine(@"setTimeout(function(){location='./main.php'}, 1000);");
                        sb.Append(@"</script></body></html>");
                        html = sb.ToString();
                        goto end;
                    }
                }
            }

            // Нужно ли воровать ?
            if (AppVars.Profile.DoRob)
            {
                AppLog.d("MainPhp", "MainPhp: rob enabled, checking");
                var robhtml = MainPhpRob(html);
                if (!string.IsNullOrEmpty(robhtml))
                {
                    html = robhtml;
                    goto end;
                }
            }

            // Нужно ли разделывать ?
            if (AppVars.Profile.SkinAuto)
            {
                AppLog.d("MainPhp", "MainPhp: skin auto enabled, checking");
                var razhtml = MainPhpRaz(html);
                if (!string.IsNullOrEmpty(razhtml))
                {
                    html = razhtml;
                    goto end;
                }
            }

            // Инвентарь?
            if (html.IndexOf("/invent/0.gif", StringComparison.OrdinalIgnoreCase) != -1)
            {
                AppLog.d("MainPhp", "MainPhp: inventory page detected");
                if (MainPhpShouldSwitchToGarbageInventoryCategory(address))
                {
                    AppLog.i("auto_cut_trace", "MainPhp", "garbage cleanup switch to quest inventory category: address=" + (address ?? string.Empty));
                    html = BuildRedirect(AutoCutRuntime.GetModeTitle(AutoCutRuntime.GetActiveMode()) + ": cleanup категории хлама", "main.php?wca=60");
                    goto end;
                }

                if (MainPhpHasAutoCutDropPending() &&
                    AutoCutRuntime.IsAutoCutLikeEnabled() &&
                    !MainPhpIsAutoCutCleanupInventoryAddress(address))
                {
                    AppLog.i("auto_cut_trace", "MainPhp", "cleanup inventory view correction: address=" + address);
                    html = BuildRedirect(AutoCutRuntime.GetModeTitle(AutoCutRuntime.GetActiveMode()) + ": cleanup инвентаря", "main.php?im=0");
                    goto end;
                }

                html = MainPhpInv(html, address);
                if (AppVars.AutoCutCleanupPending &&
                    html.IndexOf("Выбрасывание предмета", StringComparison.OrdinalIgnoreCase) >= 0 &&
                    html.IndexOf("window.location", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    goto end;
                }
            }

            var tiedParsedBeforeAutoMine = false;
            if (AutoMineRuntime.HasPendingMineRoute())
            {
                tiedParsedBeforeAutoMine = MainPhpTied(html);
                var routeAutoDrinkBlazHtml = MainPhpTryAutoDrinkBlaz(address, html);
                if (!string.IsNullOrEmpty(routeAutoDrinkBlazHtml))
                {
                    html = routeAutoDrinkBlazHtml;
                    goto end;
                }
            }

            var autoMineHtml = MainPhpAutoMine(address, html);
            if (!string.IsNullOrEmpty(autoMineHtml))
            {
                html = autoMineHtml;
                goto end;
            }

            html = html.Replace(AppConsts.HtmlCounters, string.Empty);

            if (html.IndexOf(
                    @"<font color=#dd0000>Внимание! Сеанс работы прерван.</b>",
                    StringComparison.OrdinalIgnoreCase) != -1)
            {
                AppLog.w("MainPhp", "MainPhp: SESSION INTERRUPTED");
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateGameDelegate(AppVars.MainForm.UpdateGame),
                            new object[] { "Сеанс работы прерван. Перезаход в игру" });
                    }
                }
                catch (InvalidOperationException)
                {
                }
            }

            if (html.IndexOf(
                    @"<font color=#cc0000><b>Ошибка при использовании. Истек срок годности зелья.",
                    StringComparison.OrdinalIgnoreCase) != -1)
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateTexLogDelegate(AppVars.MainForm.UpdateTexLog),
                            new object[] { "Истек срок годности зелья" });
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppTimerManager.RemoveTimerLastAdded();
            }

            if (html.IndexOf(
                    @"<font color=#cc0000><b>Ошибка при использовании. Достигнут предел одновременного использования зелий.",
                    StringComparison.OrdinalIgnoreCase) != -1)
            {
                if (AppVars.MainForm != null)
                    AppVars.MainForm.WriteChatMsgSafe("Достигнут предел одновременного использования зелий");

                AppTimerManager.RemoveTimerLastAdded();

                if (AppVars.DoSelfNevid && (AppVars.SelfNevidStage == 0))
                {
                    AppVars.SelfNevidNeed = true;
                    AppVars.SelfNevidStage = 1;
                }
            }

            if (
                (html.IndexOf(@"<font color=#cc0000><b>Ошибка при использовании. Уровень персонажа слишком мал.", StringComparison.OrdinalIgnoreCase) != -1) ||
                (html.IndexOf(@"<font color=#cc0000><b>Ошибка при использовании. Союзник не находится в бою.", StringComparison.OrdinalIgnoreCase) != -1) ||
                (html.IndexOf(@"<font color=#cc0000><b>Ошибка при использовании. Нет такого игрока в данный момент.", StringComparison.OrdinalIgnoreCase) != -1) ||
                (html.IndexOf(@"<font color=#cc0000><b>Персонаж не имеет склонности или его нет в данном месте.", StringComparison.OrdinalIgnoreCase) != -1) ||
                (html.IndexOf(@"<font color=#cc0000><b>У Персонажа уровень выше Вашего.", StringComparison.OrdinalIgnoreCase) != -1))                
            {
                if (AppVars.MainForm != null)
                    AppVars.MainForm.FastCancelSafe();
            }

            if (
                (html.IndexOf(@"<font color=#cc0000><b>Ошибка при использовании. Нельзя вмешаться в закрытый бой.", StringComparison.OrdinalIgnoreCase) != -1) &&
                (AppVars.AutoAttackToolId != 0)
                )
            {
                if (AppVars.MainForm != null)
                {
                    RoomManager.CharAddToBlackList(AppVars.FastNick);
                    AppVars.MainForm.WriteChatMsgSafe(string.Format("<b>{0}</b> в бою, отменяем действие!", AppVars.FastNick));
                    AppVars.MainForm.FastCancelSafe();
                }
            }

            if (
                (html.IndexOf(
                    @"<font color=#cc0000><b>Ошибка при использовании. Невозможно использовать зелье в данный момент.",
                    StringComparison.OrdinalIgnoreCase) != -1))
            {
                if (AppVars.DoSelfNevid && (AppVars.SelfNevidStage == 1))
                    AppVars.SelfNevidNeed = true;
            }

            if (html.IndexOf(
                    @"<font color=#cc0000><b>Предмет успешно использован.",
                    StringComparison.OrdinalIgnoreCase) != -1)
            {
                /*
                if (AppVars.MainForm != null) 
                    AppVars.MainForm.WriteChatMsgSafe("Предмет успешно использован");
                 */ 
            }

            if (!string.IsNullOrEmpty(AppVars.CureNick))
            {
                if (html.IndexOf("<font color=#cc0000><b>Поздравляем, всё успешно.<br>", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    AppVars.CureNickDone = AppVars.CureNick;
                    AppVars.CureNick = string.Empty;
                }

                if (html.IndexOf("<font color=#cc0000><b>Ошибка. Персонаж находится в бою.<br>", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    AppVars.CureNickBoi = AppVars.CureNick;
                    AppVars.CureNick = string.Empty;
                }
            }

            var inshp = html.IndexOf("ins_HP(", StringComparison.OrdinalIgnoreCase);
            if (inshp != -1)
            {
                AppLog.d("MainPhp", "MainPhp: ins_HP found, parsing HP/MA");
                MainPhpInsHp(html, inshp + "ins_HP(".Length);
            }

            if (!string.IsNullOrEmpty(AppVars.UsersOnline))
            {
                const string hpfont = "<td rowspan=3> <div id=hbar><font class=hpfont>: </div></td>";
                var hpfontpos = html.IndexOf(hpfont, StringComparison.OrdinalIgnoreCase);
                if (hpfontpos != -1)
                {
                    hpfontpos += hpfont.Length;
                    html = html.Insert(
                        hpfontpos,
                        string.Format(CultureInfo.InvariantCulture,
                            "<td rowspan=3><div><img src=http://image.neverlands.ru/1x1.gif width=8 height=1><font class=hpfont>[<font color=#ACAAA3>&nbsp;<b>{0}</b>&nbsp;</font>]</font></div></td>", 
                            AppVars.UsersOnline));
                }
            }

            if (AppVars.Profile.TorgActive && TorgList.Trigger && address.StartsWith("http://www.neverlands.ru/main.php?get_id=0&", StringComparison.OrdinalIgnoreCase))
            {
                if (html.IndexOf("<font color=#cc0000>Сделка удачно завершена.", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    Chat.AddAnswer(TorgList.MessageThanks);
                    TorgList.Trigger = false;    
                }
                else
                {
                    if (html.IndexOf("<font color=#cc0000>У Вас не хватает средств для завершения сделки.", StringComparison.OrdinalIgnoreCase) != -1)
                    {
                        Chat.AddAnswer(TorgList.MessageNoMoney);
                        TorgList.Trigger = false;
                        TorgList.TriggerBuy = false;
                    }                    
                }
            }

            //if (AppVars.Profile.DoStopOnDig && (Dice.Make(10) == 0))
            if (AppVars.Profile.DoStopOnDig && (html.IndexOf("[\"dig\",\"Копать\",", StringComparison.Ordinal) != -1))
            {
                AppLog.i("MainPhp", "MainPhp: DIG detected on current cell, stopping navigation");
                AppVars.AutoMoving = false;
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateFishOffDelegate(AppVars.MainForm.UpdateNavigatorOff),
                            new object[] {});
                    }
                }
                catch (InvalidOperationException)
                {
                }

                EventSounds.PlayAlarm();
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateWriteChatMsgDelegate(AppVars.MainForm.WriteChatMsg),
                            new object[] { "На текущей клетке обнаружен клад!" });
                    }
                }
                catch (InvalidOperationException)
                {
                }
            }

            if (AppVars.Profile.FishAuto)
            {
                if (
                    (AppVars.Profile.FishStopOverWeight && html.IndexOf("<font color=#CC0000>Внимание! Возможен перегруз.", StringComparison.OrdinalIgnoreCase) != -1) ||
                    html.IndexOf("<font color=#CC0000><b>У Вас нет рыболовных снастей.", StringComparison.OrdinalIgnoreCase) != -1 ||
                    html.IndexOf("<font color=#CC0000><b>У Вас нет приманки, чтобы ловить рыбу.", StringComparison.OrdinalIgnoreCase) != -1 ||
                    html.IndexOf("<font color=#CC0000><b>Приманок нет в наличии.", StringComparison.OrdinalIgnoreCase) != -1 ||
                    html.IndexOf("<font color=#CC0000><b>У Вас не хватает умения, чтобы ловить тут рыбу.", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    AppLog.i("MainPhp", "MainPhp: fish stop condition met");
                    try
                    {
                        if (AppVars.MainForm != null)
                        {
                            AppVars.MainForm.BeginInvoke(
                                new UpdateFishOffDelegate(AppVars.MainForm.UpdateFishOff),
                                new object[] { });
                        }
                    }
                    catch (InvalidOperationException)
                    {
                    }

                    goto end;
                }
            }

            if (!tiedParsedBeforeAutoMine)
            {
                MainPhpTied(html);
            }

            // Надо ли лечить?
            if (AppVars.CureNeed && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: cure needed, looking for medkit");
                var invHtml = MainPhpFindInv(html, "&im=0&wca=85");
                if (!string.IsNullOrEmpty(invHtml))
                {
                    html = invHtml;
                    goto end;
                }

                if (MainPhpIsInv(html))
                {
                    var cureHtml = MainPhpCure(html);
                    if (string.IsNullOrEmpty(cureHtml))
                    {
                        if (!address.EndsWith("im=0&wca=85"))
                        {
                            html = BuildRedirect("Переключение на аптечки", "main.php?im=0&wca=85");
                            goto end;
                        }

                        AppVars.MainForm.WriteChatMsgSafe("Подходящая аптечка не найдена!");
                        AppVars.CureNeed = false;
                    }
                    else
                    {
                        AppVars.CureNeed = false;
                        html = cureHtml;
                        goto end;
                    }
                }
            }

            if (AppVars.Profile.ChatKeepMoving)
            {
                html = html.Replace("top.clr_chat();", string.Empty);
                html = html.Replace("parent.clr_chat();", string.Empty);
            }

            // Читаем умелку со страницы

            var sust = HelperStrings.SubString(
                html,
                "Рыбалка</td><td bgcolor=#FCFAF3><font class=proce><font color=#555555><div align=center>[",
                "]");

            if (!string.IsNullOrEmpty(sust))
            {
                int fishUm;
                if (int.TryParse(sust, out fishUm))
                {
                    AppLog.i("MainPhp", "MainPhp: fish skill parsed: " + fishUm);
                    AppVars.Profile.FishUm = fishUm;
                    AppVars.AutoFishCheckUm = false;
                }
            }

            sust = HelperStrings.SubString(
                html,
                "Охота</td><td bgcolor=#FCFAF3><font class=proce><font color=#555555><div align=center>[",
                "]");

            if (!string.IsNullOrEmpty(sust))
            {
                int skinUm;
                if (int.TryParse(sust, out skinUm))
                {
                    AppVars.AutoSkinCheckUm = false;
                    if (AppVars.SkinUm != skinUm)
                    {
                        var sb = new StringBuilder($"Умение разделки: <span style=\"color:#009933;font-weight:bold;\">{skinUm}</span>");
                        if (AppVars.SkinUm > 0 && AppVars.SkinUm < skinUm)
                        {
                            var diff = skinUm - AppVars.SkinUm;
                            sb.Append($" (+{diff})");
                        }

                        AppVars.SkinUm = skinUm;
                        if (AppVars.Profile.SkinAuto)
                        {
                            try
                            {
                                if (AppVars.MainForm != null)
                                {
                                    AppVars.MainForm.BeginInvoke(
                                        new UpdateWriteChatMsgDelegate(AppVars.MainForm.WriteChatMsg),
                                        sb.ToString());
                                }
                            }
                            catch (InvalidOperationException)
                            {
                            }
                        }
                    }
                }
            }

            // Читаем список комплектов

            var complects = GetComplects(html);
            if (complects != null)
            {
                AppLog.d("MainPhp", "MainPhp: complects found: " + complects.Length);
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateComplectsDelegate(AppVars.MainForm.UpdateComplects),
                            new object[] { complects });
                    }
                }catch (InvalidOperationException)
                {
                }                
            }

            // Нужно ли одеть комплект ?

            if (!string.IsNullOrEmpty(AppVars.WearComplect) && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: wear complect requested: " + AppVars.WearComplect);
                var invHtml = MainPhpFindInv(html, "&im=0&wca=4");
                if (!string.IsNullOrEmpty(invHtml))
                {
                    html = invHtml;
                    goto end;
                }

                if (MainPhpIsInv(html))
                {
                    var wearHtml = MainPhpWearComplect(html, AppVars.WearComplect);
                    if (string.IsNullOrEmpty(wearHtml))
                    {
                        if (!address.EndsWith("&im=0&wca=4"))
                        {
                            html = BuildRedirect("Переключение на вещи", "main.php?im=0&wca=4");
                            goto end;
                        }

                        AppVars.MainForm.WriteChatMsgSafe(string.Format("Невозможно одеть комплект ({0})", AppVars.WearComplect));
                        AppVars.WearComplect = string.Empty;
                    }
                    else
                    {
                        AppVars.MainForm.WriteChatMsgSafe(string.Format("Одеваем комплект ({0})...", AppVars.WearComplect));
                        AppVars.WearComplect = string.Empty;
                        html = wearHtml;
                        goto end;
                    }
                }
            }

            // Нужно ли выпить блаж (авто зелик/элик) ?
            var autoDrinkBlazHtml = MainPhpTryAutoDrinkBlaz(address, html);
            if (!string.IsNullOrEmpty(autoDrinkBlazHtml))
            {
                html = autoDrinkBlazHtml;
                goto end;
            }

            // Нужно ли войти в невидимость?

            if (AppVars.SelfNevidNeed && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: self-nevid processing, stage=" + AppVars.SelfNevidStage);
                while (AppVars.SelfNevidStage < 4)
                {
                    // Зелик невидимости
                    if (AppVars.SelfNevidStage == 0)
                    {
                        var invHtml = MainPhpFindInv(html, "&im=0&wca=27");
                        if (!string.IsNullOrEmpty(invHtml))
                        {
                            html = invHtml;
                            goto end;
                        }

                        if (MainPhpIsInv(html))
                        {
                            var fastHtml = MainPhpNevidPotion(html);                            
                            if (string.IsNullOrEmpty(fastHtml))
                            {
                                if (!address.EndsWith("im=0&wca=27"))
                                {
                                    html = BuildRedirect("Переключение на зелья", "main.php?im=0&wca=27");
                                    goto end;
                                }

                                // Зелье невидимости не обнаружено
                                AppVars.SelfNevidStage++;
                            }
                            else
                            {
                                if (AppVars.MainForm != null)
                                    AppVars.MainForm.WriteChatMsgSafe("Мы не в невиде, используем <b><font color=#610B5E>зелье невидимости</font></b> на себя");

                                AppVars.SelfNevidNeed = false;
                                html = fastHtml;
                                goto end;
                            }
                        }
                    }

                    // Свиток тумана
                    if (AppVars.SelfNevidStage == 1)
                    {
                        if (AppVars.SelfNevidSkl.StartsWith("Дети", StringComparison.CurrentCultureIgnoreCase))
                        {                            
                            var invHtml = MainPhpFindInv(html, "&im=0&wca=28");
                            if (!string.IsNullOrEmpty(invHtml))
                            {
                                html = invHtml;
                                goto end;
                            }

                            if (MainPhpIsInv(html))
                            {
                                var fastHtml = MainPhpSelfSviFog(html);
                                if (string.IsNullOrEmpty(fastHtml))
                                {
                                    if (!address.EndsWith("im=0&wca=28"))
                                    {
                                        html = BuildRedirect("Переключение на свитки", "main.php?im=0&wca=28");
                                        goto end;
                                    }

                                    // Свиток тумана не обнаружен
                                    AppVars.SelfNevidStage++;
                                    continue;
                                }

                                if (AppVars.MainForm != null)
                                    AppVars.MainForm.WriteChatMsgSafe("Мы не в невиде, используем <b><font color=#610B5E>свиток тумана</font></b> на себя");

                                AppVars.SelfNevidNeed = false;
                                html = fastHtml;
                                goto end;
                            }
                        }
                        else
                        {
                            AppVars.SelfNevidStage++;
                        }
                    }

                    // Свиток невидимости
                    if (AppVars.SelfNevidStage == 2)
                    {
                        var invHtml = MainPhpFindInv(html, "&im=0&wca=28");
                        if (!string.IsNullOrEmpty(invHtml))
                        {
                            html = invHtml;
                            goto end;
                        }

                        if (MainPhpIsInv(html))
                        {
                            var fastHtml = MainPhpSviNevidFourHour(html);
                            if (string.IsNullOrEmpty(fastHtml))
                            {
                                if (!address.EndsWith("im=0&wca=28"))
                                {
                                    html = BuildRedirect("Переключение на свитки", "main.php?im=0&wca=28");
                                    goto end;
                                }

                                // Свиток невидимости не обнаружен
                                AppVars.SelfNevidStage++;
                            }
                            else
                            {
                                if (AppVars.MainForm != null)
                                    AppVars.MainForm.WriteChatMsgSafe("Мы не в невиде, используем <b><font color=#610B5E>свиток невидимости 4 часа</font></b> на себя");

                                AppVars.SelfNevidNeed = false;
                                html = fastHtml;
                                goto end;
                            }
                        }

                        // Проверяем абилку тумана
                        if (AppVars.SelfNevidStage == 3)
                        {
                            // !!! AppVars.SelfNevidSkl = "Дети Сумерек";
                            if (AppVars.SelfNevidSkl.Equals("Дети Сумерек", StringComparison.CurrentCultureIgnoreCase))
                            {
                                if (address.EndsWith("main.php?useaction=addon-action&addid=1",
                                    StringComparison.OrdinalIgnoreCase))
                                {
                                    var darkfoghtml = MainPhpDarkFog(html);
                                    if (!string.IsNullOrEmpty(darkfoghtml))
                                    {
                                        if (AppVars.MainForm != null)
                                            AppVars.MainForm.WriteChatMsgSafe(
                                                "Мы не в невиде, применяем <b><font color=#610B5E>сумеречный туман</b></font> на себя!");

                                        AppVars.SelfNevidNeed = false;
                                        html = darkfoghtml;
                                        goto end;
                                    }

                                    // Сумеречный туман сейчас недоступен
                                    AppVars.SelfNevidStage++;
                                    continue;
                                }

                                if (address.EndsWith("main.php?useaction=addon-action", StringComparison.OrdinalIgnoreCase))
                                {
                                    html = BuildRedirect("Переключение на абилки", "main.php?useaction=addon-action&addid=1");
                                    goto end;
                                }

                                html = BuildRedirect("Переключение на возможности", "main.php?useaction=addon-action");
                                goto end;
                            }

                            // Мы не дети сумерек, у нас нет сумеречного тумана
                            AppVars.SelfNevidStage++;
                        }
                    }
                }

                if (AppVars.SelfNevidStage == 4)
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.WriteChatMsgSafe(
                            "Ни один способ ухода в невид (абилка и свиток тумана, зелье и свиток невида) не обнаружен. Автоуход в невид отключен. Не забудьте включить его обратно.");
                        AppVars.MainForm.SelfNevidOffSafe();
                    }
                }
            }

            // Нужно ли продать вещь?
            /*
            if (TorgList.TriggerBuy)
            {
                var invhtml = MainPhpFindInv(html, "im=0");
                if (!string.IsNullOrEmpty(invhtml))
                {
                    html = invhtml;
                    goto end;
                }

                if (MainPhpIsInv(html))
                {
                    if (html.Contains("?im=0") && !html.Contains("?wfo=1"))
                    {
                        html = BuildRedirect("Переключение на вещи", "main.php?im=0");
                        goto end;
                    }

                    TorgList.TriggerBuy = false;
                    var sellLinkPrefix = "main.php?get_id=8&uid=" + TorgList.UidThing;
                    var sellLink = HelperStrings.SubString(html, sellLinkPrefix, "'");
                    if (sellLink != null)
                    {
                        try
                        {
                            if (AppVars.MainForm != null)
                            {
                                AppVars.MainForm.BeginInvoke(
                                    new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                                    new object[] { "Продажа купленной вещи в лавку" });
                            }
                        }
                        catch (InvalidOperationException)
                        {
                        }

                        html = BuildRedirect("Продажа купленной вещи в лавку", sellLinkPrefix + sellLink);
                        goto end;                
                    }
                }
            }
             */ 

            if (AppVars.SwitchToPerc && (DateTime.Now > AppVars.NeverTimer))
            {
                var newhtml = MainPhpFindPerc(html);
                if (!string.IsNullOrEmpty(newhtml))
                {
                    AppVars.SwitchToPerc = false;
                    html = newhtml;
                    goto end;
                }
            }
            else
            {
                if (AppVars.SwitchToFlora)
                {
                    var newhtml = MainPhpFindFlora(html);
                    if (!string.IsNullOrEmpty(newhtml))
                    {
                        AppVars.SwitchToFlora = false;
                        html = newhtml;
                        goto end;
                    }
                }
            }

            // Переключения перед разделкой

            if (AppVars.Profile.SkinAuto && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: skin auto pre-processing");
                // Надо прочтитать умелку?
                if (AppVars.AutoSkinCheckUm && (DateTime.Now > AppVars.NeverTimer))
                {
                    var phtml = MainPhpFindPerc(html);
                    if (!string.IsNullOrEmpty(phtml))
                    {
                        html = phtml;
                        goto end;
                    }

                    if (
                        html.IndexOf(@"<input type=button class=lbut value=""Умения"" onclick", StringComparison.OrdinalIgnoreCase) != -1)
                    {
                        html = BuildRedirect("Переключение на умения персонажа", "main.php?mselect=1");
                        goto end;
                    }
                }

                // Считываем охотничьи ресурсы
                if (AppVars.AutoSkinCheckRes)
                {
                    var invHtml = MainPhpFindInv(html, "&im=5");
                    if (!string.IsNullOrEmpty(invHtml))
                    {
                        html = invHtml;
                        goto end;
                    }
                    
                    if (MainPhpIsInv(html))
                    {
                        AppVars.AutoSkinCheckRes = false;
                        MainPhpGetSkinRes(html);
                    }
                }

                // Надо одеть нож?

                if (AppVars.AutoSkinCheckKnife && (DateTime.Now > AppVars.NeverTimer))
                {
                    var perchtml = MainPhpFindPerc(html);
                    if (!string.IsNullOrEmpty(perchtml))
                    {
                        html = perchtml;
                        goto end;
                    }

                    AppVars.AutoSkinArmedKnife = false;
                    if (MainPhpIsPerc(html))
                    {
                        AppVars.AutoSkinArmedKnife = MainPhpArmedKinfe(html);
                        AppVars.AutoSkinCheckKnife = false;
                    }
                }

                // Одеваем нож
                
                if (!AppVars.AutoSkinArmedKnife && (DateTime.Now > AppVars.NeverTimer))
                {
                    var invHtml = MainPhpFindInv(html, "&im=0&wca=4");
                    if (!string.IsNullOrEmpty(invHtml))
                    {
                        html = invHtml;
                        goto end;
                    }

                    if (MainPhpIsInv(html))
                    {
                        invHtml = MainPhpWearKnife(html);
                        if (string.IsNullOrEmpty(invHtml))
                        {
                            if (!address.EndsWith("im=0&wca=4"))
                            {
                                html = BuildRedirect("Переключение на вещи", "main.php?im=0&wca=4");
                                goto end;
                            }
                        }
                        else
                        {
                            AppVars.AutoSkinCheckKnife = true;
                            html = invHtml;
                            goto end;
                        }
                    }
                }
            }

            // Переключения перед AutoCut: нужный инструмент должен быть надет до act=3.
            if (AutoCutRuntime.IsAutoCutLikeEnabled() && (DateTime.Now > AppVars.NeverTimer))
            {
                var autoCutMode = AutoCutRuntime.GetActiveMode();
                var toolFilter = AutoCutRuntime.GetToolInventoryFilter(autoCutMode);
                var modeTitle = AutoCutRuntime.GetModeTitle(autoCutMode);
                AppLog.d("auto_cut_trace", "MainPhp", "auto cut pre-processing: mode=" + AutoCutRuntime.GetModeActionKey(autoCutMode));
                AutoCutRuntime.UpdateMassSnapshotFromHtml(html);
                if (AutoCutRuntime.IsMassSnapshotSyncPending())
                {
                    if (AutoCutRuntime.HasUsableMassSnapshot() || MainPhpIsInv(html) || MainPhpIsAutoCutInventoryAddress(address))
                    {
                        AutoCutRuntime.ClearMassSnapshotSyncPending(AutoCutRuntime.HasUsableMassSnapshot() ? "mass_available" : "inventory_without_mass");
                        AppVars.AutoCutCheckSickle = false;
                        if (ResumePendingAlchemyCutAfterPreparation("mass_snapshot"))
                        {
                            var florahtml = MainPhpFindFlora(html);
                            if (!string.IsNullOrEmpty(florahtml))
                            {
                                html = florahtml;
                                goto end;
                            }
                        }
                    }
                    else
                    {
                        var massInvHtml = MainPhpFindInv(html, "&im=0");
                        if (!string.IsNullOrEmpty(massInvHtml))
                        {
                            html = massInvHtml;
                            goto end;
                        }

                        if (!address.EndsWith("im=0", StringComparison.OrdinalIgnoreCase))
                        {
                            html = BuildRedirect("Переключение на инвентарь", "main.php?im=0");
                            goto end;
                        }
                    }
                }

                if (AppVars.AutoCutCleanupPending)
                {
                    var cleanupHtml = MainPhpAutoCutCleanupRedirect(address, html, modeTitle, "auto_cut_preprocessing");
                    if (!string.IsNullOrEmpty(cleanupHtml))
                    {
                        html = cleanupHtml;
                        goto end;
                    }
                }

                if (AppVars.AutoCutCheckSickle && (DateTime.Now > AppVars.NeverTimer))
                {
                    var perchtml = MainPhpFindPerc(html);
                    if (!string.IsNullOrEmpty(perchtml))
                    {
                        html = perchtml;
                        goto end;
                    }

                    AppVars.AutoCutArmedSickle = false;
                    if (MainPhpIsPerc(html) || MainPhpIsInv(html))
                    {
                        AppVars.AutoCutArmedSickle = MainPhpArmedAutoCutTool(html, autoCutMode);
                        AppVars.AutoCutCheckSickle = false;
                        var resumedPendingCut = AppVars.AutoCutArmedSickle && ResumePendingAlchemyCutAfterPreparation("tool_checked");
                        if (resumedPendingCut)
                        {
                            var florahtml = MainPhpFindFlora(html);
                            if (!string.IsNullOrEmpty(florahtml))
                            {
                                html = florahtml;
                                goto end;
                            }
                        }

                        if (AppVars.AutoCutArmedSickle && !resumedPendingCut)
                        {
                            var florahtml = MainPhpFindFlora(html);
                            if (!string.IsNullOrEmpty(florahtml))
                            {
                                AppLog.i("auto_cut_trace", "MainPhp", "tool checked: return to flora before auto look, mode=" + AutoCutRuntime.GetModeActionKey(autoCutMode));
                                html = florahtml;
                                goto end;
                            }

                            AutoCutRuntime.ScheduleLookRetry("tool_checked");
                        }
                    }
                }

                if (!AppVars.AutoCutArmedSickle && (DateTime.Now > AppVars.NeverTimer))
                {
                    var invHtml = MainPhpFindInv(html, toolFilter);
                    if (!string.IsNullOrEmpty(invHtml))
                    {
                        html = invHtml;
                        goto end;
                    }

                    if (MainPhpIsInv(html))
                    {
                        invHtml = MainPhpWearAutoCutTool(html, autoCutMode);
                        if (string.IsNullOrEmpty(invHtml))
                        {
                            if (AutoCutRuntime.IsAutoCutLikeEnabled() && !address.EndsWith(toolFilter.TrimStart('&'), StringComparison.OrdinalIgnoreCase))
                            {
                                html = BuildRedirect("Переключение на вещи", "main.php?im=0" + toolFilter.Replace("&im=0", string.Empty));
                                goto end;
                            }
                        }
                        else
                        {
                            AppVars.AutoCutCheckSickle = true;
                            html = invHtml;
                            goto end;
                        }
                    }
                }
            }

            // Переключения перед забросом удочки

            if (AppVars.Profile.FishAuto && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: fish auto pre-cast checks");
                // Нормальная для заброса усталость?
                if (!AppVars.AutoFishDrink)
                {
                    AppVars.AutoFishDrink = (AppVars.Tied > AppVars.Profile.FishTiedHigh) && AppVars.Profile.FishTiedZero;
                }

                if ((AppVars.Tied > AppVars.Profile.FishTiedHigh) ||
                    AppVars.AutoDrink ||
                    AppVars.AutoFishDrink)
                {
                    var newhtml = MainPhpFindDrink(html);
                    if (!string.IsNullOrEmpty(newhtml))
                    {
                        html = newhtml;
                        AppVars.AutoFishDrinkOnce = true;
                        AppVars.SwitchToPerc = true;

                        if (AppVars.Profile.ShowTrayBaloons)
                        {
                            var sbu = new StringBuilder();
                            sbu.Append("Усталость: ");
                            sbu.Append(AppVars.Tied);
                            sbu.AppendLine();
                            if (AppVars.AutoDrink || AppVars.AutoFishDrink)
                            {
                                sbu.Append("Пьем до нуля");
                            }
                            else
                            {
                                sbu.Append("Делаем глоток");
                            }

                            try
                            {
                                if (AppVars.MainForm != null)
                                {
                                    AppVars.MainForm.BeginInvoke(
                                        new UpdateTrayBaloonDelegate(AppVars.MainForm.UpdateTrayBaloon),
                                        new object[] { sbu.ToString() });
                                }
                            }
                            catch (InvalidOperationException)
                            {
                            }
                        }

                        goto end;
                    }

                    newhtml = MainPhpFindFlora(html);
                    if (!string.IsNullOrEmpty(newhtml))
                    {
                        html = newhtml;
                        goto end;
                    }

                    if (html.IndexOf(" id=wtime>", StringComparison.OrdinalIgnoreCase) != -1)
                    {
                        html = MainPhpWtime(address, html);
                        goto end;
                    }
                }

                // Надо прочтитать умелку?
                if (AppVars.AutoFishCheckUm && (DateTime.Now > AppVars.NeverTimer))
                {
                    var phtml = MainPhpFindPerc(html);
                    if (!string.IsNullOrEmpty(phtml))
                    {
                        html = phtml;
                        goto end;
                    }

                    if (
                        html.IndexOf(@"<input type=button class=lbut value=""Умения"" onclick", StringComparison.OrdinalIgnoreCase) != -1)
                    {
                        html = BuildRedirect("Переключение на умения персонажа", "main.php?mselect=1");
                        goto end;
                    }
                }

                // Надо переодеться?

                if (AppVars.AutoFishCheckUd && (DateTime.Now > AppVars.NeverTimer))
                {
                    var perchtml = MainPhpFindPerc(html);
                    if (!string.IsNullOrEmpty(perchtml))
                    {
                        html = perchtml;
                        goto end;
                    }

                    AppVars.AutoFishWearUd = false;
                    if (MainPhpIsPerc(html))
                    {
                        AppVars.AutoFishWearUd = MainPhpIsMustWearUd(html);
                        AppVars.AutoFishCheckUd = false;
                    }
                }

                if (AppVars.AutoFishWearUd && (DateTime.Now > AppVars.NeverTimer))
                {
                    var invHtml = MainPhpFindInv(html, "&im=0&wca=4");
                    if (!string.IsNullOrEmpty(invHtml))
                    {
                        html = invHtml;
                        goto end;
                    }

                    if (MainPhpIsInv(html))
                    {
                        invHtml = MainPhpWearUd(html);
                        if (string.IsNullOrEmpty(invHtml))
                        {
                            if (!address.EndsWith("im=0&wca=4"))
                            {
                                html = BuildRedirect("Переключение на вещи", "main.php?im=0&wca=4");
                                goto end;
                            }
                        }
                        else
                        {
                            html = invHtml;
                            goto end;
                        }
                    }
                }
            }

            // Быстрые абилки, их нужно проверять после боя

            if (AppVars.FastNeedAbilDarkTeleport || AppVars.FastNeedAbilDarkFog)
            {
                AppLog.d("MainPhp", "MainPhp: fast abil dark teleport=" + AppVars.FastNeedAbilDarkTeleport + " fog=" + AppVars.FastNeedAbilDarkFog);
                if (address.EndsWith("main.php?useaction=addon-action&addid=1", StringComparison.OrdinalIgnoreCase))
                {
                    if (AppVars.FastNeedAbilDarkTeleport)
                    {
                        AppVars.FastNeedAbilDarkTeleport = false;
                        var darkteleporthtml = MainPhpDarkTeleport(html);
                        if (!string.IsNullOrEmpty(darkteleporthtml))
                        {
                            html = darkteleporthtml;
                            goto end;
                        }

                        AppVars.MainForm.WriteChatMsgSafe("Нет возможности применить сумеречный телепорт!");                        
                    }

                    if (AppVars.FastNeedAbilDarkFog)
                    {
                        AppVars.FastNeedAbilDarkFog = false;
                        var darkfoghtml = MainPhpDarkFog(html);
                        if (!string.IsNullOrEmpty(darkfoghtml))
                        {
                            html = darkfoghtml;
                            goto end;
                        }

                        AppVars.MainForm.WriteChatMsgSafe("Нет возможности применить сумеречный туман!");                        
                    }
                }
                else
                {
                    if (address.EndsWith("main.php?useaction=addon-action", StringComparison.OrdinalIgnoreCase))
                    {
                        html = BuildRedirect("Переключение на абилки", "main.php?useaction=addon-action&addid=1");
                        goto end;
                    }

                    html = BuildRedirect("Переключение на возможности", "main.php?useaction=addon-action");
                    goto end;
                }
            }

            // Автолечение ядов и небоевых травм

            if (AppVars.Profile.DoAutoCure)
            {
                AppLog.d("MainPhp", "MainPhp: auto-cure check");
                if (AppVars.PoisonAndWounds[0] > 0)
                {
                    var invhtml = MainPhpFindInv(html, "&im=0&wca=27");
                    if (!string.IsNullOrEmpty(invhtml))
                    {
                        html = invhtml;
                        goto end;
                    }

                    if (MainPhpIsInv(html))
                    {
                        var cureHtml = MainPhpRemovePoison(html);
                        if (string.IsNullOrEmpty(cureHtml))
                        {
                            if (!address.EndsWith("im=0&wca=27"))
                            {
                                html = BuildRedirect("Переключение на зелья", "main.php?im=0&wca=27");
                                goto end;
                            }

                            AppVars.MainForm.WriteChatMsgSafe("У вас отравление и нет зелья лечения отравлений! Автолечение отключено. Не забудьте включить его обратно.");
                            AppVars.PoisonAndWounds[0] = 0;
                            AppVars.Profile.DoAutoCure = false;
                            if ((AppVars.PoisonAndWounds[1] == 0) && (AppVars.PoisonAndWounds[2] == 0) && (AppVars.PoisonAndWounds[3] == 0))
                                AppVars.WearComplect = AppVars.Profile.AutoWearComplect;
                        }
                        else
                        {
                            AppVars.MainForm.WriteChatMsgSafe("Лечим свое отравление...");
                            AppVars.PoisonAndWounds[0]--;

                            if ((AppVars.PoisonAndWounds[1] == 0) && (AppVars.PoisonAndWounds[2] == 0) && (AppVars.PoisonAndWounds[3] == 0))
                                AppVars.WearComplect = AppVars.Profile.AutoWearComplect;

                            html = cureHtml;
                            goto end;                            
                        }
                    }
                }
                else
                {
                    // Есть ли травма ? 
                    if ((AppVars.PoisonAndWounds[1] > 0) || (AppVars.PoisonAndWounds[2] > 0) || (AppVars.PoisonAndWounds[3] > 0))
                    {
                        var invhtml = MainPhpFindInv(html, "&im=0&wca=85");
                        if (!string.IsNullOrEmpty(invhtml))
                        {
                            html = invhtml;
                            goto end;
                        }

                        if (MainPhpIsInv(html))
                        {
                            AppVars.CureNeed = true;
                            AppVars.CureNick = AppVars.Profile.UserNick;
                            if (AppVars.PoisonAndWounds[1] > 0)
                            {
                                AppVars.CureTravm = "1";                                
                            }
                            else if (AppVars.PoisonAndWounds[2] > 0)
                            {
                                AppVars.CureTravm = "2";                                
                            }
                            else if (AppVars.PoisonAndWounds[3] > 0)
                            {
                                AppVars.CureTravm = "3";                                
                            }

                            var cureHtml = MainPhpCure(html);
                            if (string.IsNullOrEmpty(cureHtml))
                            {
                                if (!address.EndsWith("im=0&wca=85"))
                                {
                                    html = BuildRedirect("Переключение на аптечки", "main.php?im=0&wca=85");
                                    goto end;
                                }

                                AppVars.MainForm.WriteChatMsgSafe("У вас травма, но нет возможности ее вылечить! Автолечение отключено. Не забудьте включить его обратно.");
                                AppVars.Profile.DoAutoCure = false;
                                AppVars.CureNeed = false;
                                AppVars.PoisonAndWounds[1] = AppVars.PoisonAndWounds[2] = AppVars.PoisonAndWounds[3] = 0;
                            }
                            else
                            {
                                switch (AppVars.CureTravm)
                                {
                                    case "1":
                                        AppVars.PoisonAndWounds[1]--;
                                        AppVars.MainForm.WriteChatMsgSafe("Лечим свою легкую травму...");
                                        break;
                                    case "2":
                                        AppVars.PoisonAndWounds[2]--;
                                        AppVars.MainForm.WriteChatMsgSafe("Лечим свою среднюю травму...");
                                        break;
                                    case "3":
                                        AppVars.PoisonAndWounds[3]--;
                                        AppVars.MainForm.WriteChatMsgSafe("Лечим свою тяжелую травму...");
                                        break;
                                }

                                if ((AppVars.PoisonAndWounds[1] == 0) && (AppVars.PoisonAndWounds[2] == 0) && (AppVars.PoisonAndWounds[3] == 0))
                                    AppVars.WearComplect = AppVars.Profile.AutoWearComplect;

                                html = cureHtml;
                                goto end;
                            }
                        }
                    }
                }
            }

            if (AppVars.FastNeed &&
                MainPhpIsFastIslandAction() &&
                html.IndexOf("var telep = ", StringComparison.Ordinal) != -1)
            {
                var fastIslandHtml = MainPhpFast(html);
                if (!string.IsNullOrEmpty(fastIslandHtml))
                {
                    if (AppVars.MainForm != null && AppVars.FastId != null)
                    {
                        AppVars.MainForm.WriteChatMsgSafe("Используем " + AppVars.FastId);
                        AppVars.MainForm.FastCancelSafe();
                    }

                    html = fastIslandHtml;
                    goto end;
                }

                if (address.IndexOf("get_id=16", StringComparison.OrdinalIgnoreCase) != -1 && AppVars.MainForm != null)
                {
                    AppVars.MainForm.WriteChatMsgSafe("Спецтелепорт не найден, действие отменено.");
                    AppVars.MainForm.FastCancelSafe();
                }
            }

            if (AppVars.FastNeed &&
                MainPhpIsFastIslandAction() &&
                address.IndexOf("get_id=16", StringComparison.OrdinalIgnoreCase) != -1 &&
                !MainPhpIsInv(html) &&
                AppVars.MainForm != null)
            {
                AppVars.MainForm.FastCancelSafe();
            }

            // Заказано ли быстрое действие?

            if (AppVars.FastNeed)
            {
                AppLog.d("MainPhp", "MainPhp: FastNeed=true, FastId=" + AppVars.FastId);
                var now = DateTime.Now;
                var neverTimerReady = now > AppVars.NeverTimer;
                var isFastTeleport = string.Equals(AppVars.FastId, "i_w28_22.gif", StringComparison.OrdinalIgnoreCase);
                if (neverTimerReady || isFastTeleport)
                {
                    if (!neverTimerReady && isFastTeleport)
                    {
                        var waitMs = Math.Max(0, (int)AppVars.NeverTimer.Subtract(now).TotalMilliseconds);
                        AppLog.d("MainPhp", "FastTeleport: processing outside NeverTimer, waitMs=" + waitMs +
                                            ", address=" + address);
                    }

                    string invHtml, fastHtml;

                    // Определяем, на что мы должны переключиться
                    switch (AppVars.FastId)
                    {
                        case "i_w28_22.gif": // Свиток телепорта
                        case "i_w28_23.gif": // Свиток саморассеивания
                        case "i_w28_28.gif": // Свиток обнаружения
                        case "i_svi_213.gif": // Свиток искажающего тумана
                        case "i_svi_001.gif": // Обычная нападалка
                        case "i_svi_002.gif": // Кровавая нападалка
                        case "i_w28_26.gif": // Боевая нападалка
                        case "i_w28_26X.gif": // Закрытая боевая нападалка
                        case "i_w28_24.gif": // Кулачка
                        case "i_w28_25.gif": // Закрытая кулачка
                        case "i_svi_205.gif": // Закрытая нападалка
                        case "i_w28_27.gif": // Свиток защиты
                        case "Телепорт (Остров Туротор)":
                        case "Телепорт (Гиблая Топь)":
                        case "i_w28_86.gif": // Портал
                            // Работаем со свитками
                            invHtml = MainPhpFindInv(html, "&im=0&wca=28");
                            if (!string.IsNullOrEmpty(invHtml))
                            {
                                if (isFastTeleport)
                                {
                                    AppLog.d("MainPhp", "FastTeleport: inventory redirect prepared outside main reload");
                                }

                                html = invHtml;
                                goto end;
                            }

                            if (MainPhpIsInv(html))
                            {
                                fastHtml = MainPhpFast(html);
                                if (string.IsNullOrEmpty(fastHtml))
                                {
                                    if (!address.EndsWith("im=0&wca=28"))
                                    {
                                        html = BuildRedirect("Переключение на свитки", "main.php?im=0&wca=28");
                                        goto end;
                                    }

                                    if (AppVars.MainForm != null)
                                    {
                                        AppVars.MainForm.WriteChatMsgSafe("Свиток не обнаружен, действие отменено.");
                                        AppVars.MainForm.FastCancelSafe();
                                    }
                                }
                                else
                                {
                                    if (AppVars.MainForm != null && AppVars.FastNick != null)
                                    {
                                        if (string.Equals(AppVars.FastId, "i_w28_22.gif", StringComparison.OrdinalIgnoreCase))
                                        {
                                            var teleportDestination = AppVars.ResolveFastTeleportDestinationName(
                                                AppVars.FastTeleportDestinationId,
                                                AppVars.FastTeleportDestinationName);
                                            AppVars.MainForm.WriteChatMsgSafe($"Телепорт: отправлен запрос в <b>{teleportDestination}</b>");
                                        }
                                        else
                                        {
                                            AppVars.MainForm.WriteChatMsgSafe($"Используем свиток на <b>{AppVars.FastNick}</b>");
                                        }
                                    }

                                    AppVars.FastCount--;
                                    if (AppVars.FastCount == 0)
                                    {
                                        if (AppVars.MainForm != null)
                                            AppVars.MainForm.FastCancelSafe();
                                    }

                                    html = fastHtml;
                                    goto end;                                    
                                }
                            }

                            break;
                        case "Яд":
                        case "Зелье Сильной Спины":
                        case "Зелье Невидимости":
                        case "Зелье Блаженства":
                        case "Зелье Метаболизма":
                        case "Зелье Просветления":
                        case "Зелье Сокрушительных Ударов":
                        case "Зелье Стойкости":
                        case "Зелье Недосягаемости":
                        case "Зелье Точного Попадания":
                        case "Зелье Ловких Ударов":
                        case "Зелье Мужества":
                        case "Зелье Жизни":
                        case "Зелье Лечения":
                        case "Зелье Восстановления Маны":
                        case "Зелье Энергии":
                        case "Зелье Удачи":
                        case "Зелье Силы":
                        case "Зелье Ловкости":
                        case "Зелье Гения":
                        case "Зелье Боевой Славы":
                        case "Зелье Секрет Волшебника":
                        case "Зелье Медитации":
                        case "Зелье Иммунитета":
                        case "Зелье Лечения Отравлений":
                        case "Зелье Огненного Ореола":
                        case "Зелье Колкости":
                        case "Зелье Загрубелой Кожи":
                        case "Зелье Панциря":
                        case "Зелье Человек-гора":
                        case "Зелье Скорости":
                        case "Жажда Жизни":
                        case "Ментальная Жажда":
                        case "Зелье подвижности":
                        case "Ярость Берсерка":
                        case "Зелье Хрупкости":
                        case "Зелье Мифриловый Стержень":
                        case "Зелье Соколиный взор":
                        case "Секретное Зелье":
                            // Работаем со зельями
                            invHtml = MainPhpFindInv(html, "&im=0&wca=27");
                            if (!string.IsNullOrEmpty(invHtml))
                            {
                                html = invHtml;
                                goto end;
                            }

                            if (MainPhpIsInv(html))
                            {
                                fastHtml = MainPhpFast(html);
                                if (string.IsNullOrEmpty(fastHtml))
                                {
                                    if (!address.EndsWith("im=0&wca=27"))
                                    {
                                        html = BuildRedirect("Переключение на зелья", "main.php?im=0&wca=27");
                                        goto end;
                                    }

                                    if (AppVars.MainForm != null)
                                    {
                                        AppVars.MainForm.FastCancelSafe();
                                        AppVars.MainForm.WriteChatMsgSafe("Зелье не обнаружено, действие отменено.");
                                    }
                                }
                                else
                                {
                                    if (AppVars.MainForm != null && AppVars.FastId != null && AppVars.FastNick != null)
                                        AppVars.MainForm.WriteChatMsgSafe(
                                            $"Используем <b><font color=#610B5E>{AppVars.FastId}</font></b> на <b>{AppVars.FastNick}</b>");

                                    AppVars.FastCount--;
                                    if (AppVars.FastCount == 0)
                                    {
                                        if (AppVars.MainForm != null)
                                            AppVars.MainForm.FastCancelSafe();
                                    }
                                   
                                    html = fastHtml;
                                    goto end;
                                }
                            }

                            break;
                        case "Эликсир Блаженства":
                        case "Эликсир Мгновенного Исцеления":
                        case "Эликсир Восстановления":
                        case AppConsts.Bait:
                            // Работаем со эликсирами
                            invHtml = MainPhpFindInv(html, "&im=6");
                            if (!string.IsNullOrEmpty(invHtml))
                            {
                                html = invHtml;
                                goto end;
                            }

                            if (MainPhpIsInv(html))
                            {
                                fastHtml = MainPhpFast(html);
                                if (string.IsNullOrEmpty(fastHtml))
                                {
                                    if (!address.EndsWith("im=6"))
                                    {
                                        html = BuildRedirect("Переключение на эликсиры", "main.php?im=6");
                                        goto end;
                                    }

                                    if (AppVars.MainForm != null)
                                    {
                                        AppVars.MainForm.FastCancelSafe();
                                        AppVars.MainForm.WriteChatMsgSafe(AppVars.FastId == AppConsts.Bait
                                            ? "Приманка не обнаружена, действие отменено."
                                            : "Эликсир не обнаружен, действие отменено.");
                                    }
                                }
                                else
                                {
                                    if (AppVars.MainForm != null && AppVars.FastId != null && AppVars.FastNick != null)
                                        AppVars.MainForm.WriteChatMsgSafe(
                                            $"Используем <b><font color=#610B5E>{AppVars.FastId}</font></b> на <b>{AppVars.FastNick}</b>");

                                    AppVars.FastCount--;
                                    if (AppVars.FastCount == 0)
                                    {
                                        if (AppVars.MainForm != null)
                                            AppVars.MainForm.FastCancelSafe();
                                    }

                                    html = fastHtml;
                                    goto end;
                                }
                            }

                            break;
                        case "Тотем":
                            // Работаем с тотемным нападением
                            if (DateTime.Now > AppVars.NeverTimer)
                            {
                                var newhtml = MainPhpFindFlora(html);
                                if (!string.IsNullOrEmpty(newhtml))
                                {
                                    html = newhtml;
                                    goto end;
                                }

                                fastHtml = MainPhpFast(html);
                                if (string.IsNullOrEmpty(fastHtml))
                                {
                                    if (AppVars.MainForm != null)
                                    {
                                        AppVars.MainForm.FastCancelSafe();
                                        AppVars.MainForm.WriteChatMsgSafe(
                                            "Нападение по тотему сейчас невозможно, действие отменено.");
                                    }
                                }
                                else
                                {
                                    if (DateTime.Now.Subtract(AppVars.FastTotemMessageTime).TotalSeconds >
                                        AppConsts.FastTotemMessageTimeBlockSeconds)
                                    {
                                        AppVars.FastTotemMessageTime = DateTime.Now;
                                        if (AppVars.MainForm != null && AppVars.FastNick != null)
                                            AppVars.MainForm.WriteChatMsgSafe(
                                                $"Используем <b><font color=#610B5E>тотемное нападение</font></b> на <b>{AppVars.FastNick}</b>");
                                    }

                                    AppVars.FastCount--;
                                    if (AppVars.FastCount == 0)
                                    {
                                        if (AppVars.MainForm != null)
                                            AppVars.MainForm.FastCancelSafe();
                                    }

                                    html = fastHtml;
                                    goto end;
                                }
                            }

                            break;

                        default:
                            throw new NotImplementedException($"AppVars.FastId = {AppVars.FastId}");
                    }
                }
                else
                {
                    var waitMs = Math.Max(0, (int)AppVars.NeverTimer.Subtract(now).TotalMilliseconds);
                    AppLog.d("MainPhp", "MainPhp: FastNeed waits NeverTimer, FastId=" + AppVars.FastId +
                                        ", waitMs=" + waitMs);
                }
            }

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateAutoboiResetDelegate(AppVars.MainForm.UpdateAutoboiReset),
                        new object[] { });
                }
            }
            catch (InvalidOperationException)
            {
            }

            /*
             * Новое автопитье
             */

            if (AppVars.AutoDrink && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: auto-drink triggered");
                var newhtml = MainPhpFindDrink(html);
                if (!string.IsNullOrEmpty(newhtml))
                {
                    try
                    {
                        if (AppVars.MainForm != null)
                        {
                            AppVars.MainForm.UpdateCheckTiedSafe();
                        }
                    }
                    // ReSharper disable once EmptyGeneralCatchClause
                    catch (Exception)
                    {
                    }

                    html = newhtml;
                    goto end;
                }
            }

            /*
              * Новая рыбалка
              */

            if (AppVars.Profile.FishAuto && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: fish auto triggered (new fishing)");
                var newhtml = MainPhpFindFlora(html);
                if (!string.IsNullOrEmpty(newhtml))
                {
                    html = newhtml;
                    goto end;
                }

                newhtml = MainPhpFindFish(html);
                if (!string.IsNullOrEmpty(newhtml))
                {
                    html = newhtml;
                    goto end;
                }
            }

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new ReloadChPhpInvokeDelegate(AppVars.MainForm.ReloadChPhpInvoke),
                        new object[] { });
                }
            }
            catch (InvalidOperationException)
            {
            }

            // Оббегаем карту

            if (AppVars.DoSearchBox && !AppVars.AutoMoving && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: search box navigation");
                var dest = FormMain.FindNextDestForBox();
                if (!string.IsNullOrEmpty(dest) && (AppVars.MainForm != null))
                {
                    AppVars.MainForm.MoveToSafe(dest);
                    goto end;
                }
            }

            /*
             * Новый телепорт и навигация
             */

            if (AppVars.AutoMoving && (DateTime.Now > AppVars.NeverTimer))
            {
                AppLog.d("MainPhp", "MainPhp: auto-moving navigation");
                var cityhtml = MainPhpStartFromCityNavigation(html);
                if (!string.IsNullOrEmpty(cityhtml))
                {
                    html = cityhtml;
                    goto end;
                }

                var newhtml = MainPhpCityNavigation(html);
                if (!string.IsNullOrEmpty(newhtml))
                {
                    html = newhtml;
                    goto end;
                }

                if (html.IndexOf("var telep = ", StringComparison.Ordinal) != -1)
                {
                    newhtml = TeleportAjax(html);
                    if (newhtml != null)
                    {
                        html = newhtml;
                        goto end;
                    }
                }
            }

            /*
             * Новая карта
             */

            if (html.IndexOf("var map = ", StringComparison.Ordinal) != -1)
            {
                AppLog.d("MainPhp", "MainPhp: map data found in HTML");
                if ((AppVars.Profile.DoAutoDrinkBlaz) && (AppVars.Tied >= AppVars.Profile.AutoDrinkBlazTied) &&
                    (DateTime.Now > AppVars.NeverTimer))
                {
                    html = BuildRedirect("Требуется обнулить усталость", "main.php");
                    goto end;                    
                }

                html = MapAjax(html);
                if (AppVars.AutoMoving)
                    goto end;
            }

            if ((AppVars.AutoDrink || AppVars.AutoMoving) && (DateTime.Now > AppVars.NeverTimer))
            {
                var newhtml = MainPhpFindFlora(html);
                if (!string.IsNullOrEmpty(newhtml))
                {
                    html = newhtml;
                    goto end;
                }
            }

            // Нужно ли пить Эликсир восстановления?
            if (!AppVars.DoFury)
            {
                var drinkHpMa = MainPhpDrinkHpMa(address, html);
                if (!string.IsNullOrEmpty(drinkHpMa))
                {
                    html = drinkHpMa;
                    goto end;
                }
            }

            // Переключаем на полный инвентарь
            
            /*
            if (MainPhpIsInv(html))
            {
                if (
                    html.IndexOf("<tr><td bgcolor=#D8CDAF width=50% colspan=3><div align=center><font class=invtitle><font color=#000000>свойства</font>", StringComparison.CurrentCultureIgnoreCase) == -1 &&
                    html.IndexOf("<br><img src=http://image.neverlands.ru/solidst.gif", StringComparison.CurrentCultureIgnoreCase) != -1 &&
                    html.IndexOf("<a href=\"?wsi=1\">", StringComparison.CurrentCultureIgnoreCase) != -1)
                {
                    html = BuildRedirect("Переключение на полный инвентарь", "main.php?wsi=1");
                    goto end;
                }
            }
            */

            /*
            if (DateTime.Now.Subtract(AppVars.LastMainPhp).TotalMinutes > 9.5)
            {
                if (MainPhpIsInv(html))
                {
                    var idlehtml = MainPhpFindPerc(html);
                    if (!string.IsNullOrEmpty(idlehtml))
                    {
                        AppVars.IdleTimer = DateTime.Now;
                        html = idlehtml;
                    }                    
                }
                else
                {
                    var idlehtml = MainPhpFindInv(html, string.Empty);
                    if (!string.IsNullOrEmpty(idlehtml))
                    {
                        AppVars.IdleTimer = DateTime.Now;
                        html = idlehtml;
                    }                       
                }
            }
            */

            if (!string.IsNullOrEmpty(html))
            {
                html = html.Replace("document.write(view_t())", string.Empty);
            }

            end:
            AppLog.d("MainPhp", "MainPhp: processing complete");
            if (!string.IsNullOrEmpty(html))
            {
                AppVars.ContentMainPhp = html;
                return Russian.Codepage.GetBytes(html);
            }

            return array;
        }

        private static string MainPhpTryAutoDrinkBlaz(string address, string html)
        {
            if (AutoCutRuntime.IsAutoCutLikeEnabled() && AppVars.AutoCutCleanupPending)
            {
                var cleanupHtml = MainPhpAutoCutCleanupRedirect(
                    address,
                    html,
                    AutoCutRuntime.GetModeTitle(AutoCutRuntime.GetActiveMode()),
                    "before_auto_drink");
                if (!string.IsNullOrEmpty(cleanupHtml))
                {
                    return cleanupHtml;
                }
            }

            if (!AppVars.Profile.DoAutoDrinkBlaz || AppVars.Tied < AppVars.Profile.AutoDrinkBlazTied)
            {
                return null;
            }

            var now = DateTime.Now;
            if (now <= AppVars.NeverTimer)
            {
                if (AutoMineRuntime.HasPendingMineRoute())
                {
                    var waitMs = Math.Max(0, (int)AppVars.NeverTimer.Subtract(now).TotalMilliseconds);
                    AppLog.d(AutoMineRuntime.TraceChain, "MainPhp", "auto-drink blaz waits NeverTimer during mine route, tied=" + AppVars.Tied +
                        " >= " + AppVars.Profile.AutoDrinkBlazTied + ", waitMs=" + waitMs);
                }

                return null;
            }

            AppLog.d("MainPhp", "MainPhp: auto-drink blaz triggered, tied=" + AppVars.Tied + " >= " + AppVars.Profile.AutoDrinkBlazTied);
            var preferPotion = AppVars.Profile.AutoDrinkBlazOrder == 0;
            if (!MainPhpIsInv(html))
            {
                AppVars.DrinkBlazPotOrElixirFirst = false;
                var invHtml = MainPhpFindInv(html, preferPotion ? "&im=0&wca=27" : "&im=6");
                if (!string.IsNullOrEmpty(invHtml))
                {
                    return invHtml;
                }
            }

            if (!MainPhpIsInv(html))
            {
                return null;
            }

            var cureHtml = MainPhpDrinkBlazPotOrElixir(html);
            if (string.IsNullOrEmpty(cureHtml))
            {
                var atPotionPage = MainPhpIsBlazPotionAddress(address);
                var atElixirPage = MainPhpIsBlazElixirAddress(address);
                AppLog.d("MainPhp", "MainPhp: auto-drink blaz item not found, address=" + address +
                    ", firstChecked=" + AppVars.DrinkBlazPotOrElixirFirst +
                    ", atPotion=" + atPotionPage + ", atElixir=" + atElixirPage);

                if (preferPotion)
                {
                    if (atPotionPage)
                    {
                        AppVars.DrinkBlazPotOrElixirFirst = true;
                        return BuildRedirect("Переключение на эликсиры", "main.php?im=6");
                    }

                    if (!atElixirPage || !AppVars.DrinkBlazPotOrElixirFirst)
                    {
                        AppVars.DrinkBlazPotOrElixirFirst = false;
                        return BuildRedirect("Переключение на зелья", "main.php?im=0&wca=27");
                    }
                }
                else
                {
                    if (atElixirPage)
                    {
                        AppVars.DrinkBlazPotOrElixirFirst = true;
                        return BuildRedirect("Переключение на зелья", "main.php?im=0&wca=27");
                    }

                    if (!atPotionPage || !AppVars.DrinkBlazPotOrElixirFirst)
                    {
                        AppVars.DrinkBlazPotOrElixirFirst = false;
                        return BuildRedirect("Переключение на эликсиры", "main.php?im=6");
                    }
                }

                AppVars.DrinkBlazPotOrElixirFirst = false;
                AppVars.MainForm.WriteChatMsgSafe("Ни зелье ни эликсир блаженства не найдены. Автопитье блажа отключено. Не забудьте включить его обратно.");
                AppVars.Profile.DoAutoDrinkBlaz = false;
                return null;
            }

            AppVars.DrinkBlazPotOrElixirFirst = false;
            AppVars.Tied = 0;
            AppVars.SwitchToFlora = true;
            AppLog.i("MainPhp", "MainPhp: auto-drink blaz submitted, return to flora scheduled");
            if (AutoCutRuntime.IsAutoCutLikeEnabled())
            {
                AppLog.i("auto_cut_trace", "MainPhp", "auto-drink blaz submitted: return to flora before auto look");
            }

            return cureHtml;
        }
    }
}
