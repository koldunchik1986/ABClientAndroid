using System;
using System.Globalization;
using System.Text.RegularExpressions;
using ANClient.ANForms;

namespace ANClient.PostFilter
{
    internal static partial class Filter
    {
        private static readonly Regex AutoMineMainPhpLinkRegex = new Regex(
            "(?:https?://(?:www\\.)?neverlands\\.ru/|\\.\\./|/)?main\\.php\\?[^'\"\\s<]+",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static string MainPhpAutoMine(string address, string html)
        {
            if (string.IsNullOrEmpty(html))
                return null;

            var isMinePage = AutoMineRuntime.IsAutoMinePage(html);
            if (isMinePage)
            {
                AutoMineRuntime.UpdateMinePageSnapshot(html, address);
            }

            if (isMinePage && AutoMineRuntime.HasPendingMineRoute())
            {
                var routeScript = AutoMineRuntime.BuildPendingMoveInjection("main_php_mine_page_route");
                if (!string.IsNullOrEmpty(routeScript))
                    return InjectBeforeBodyEnd(html, routeScript);
            }

            if (AppVars.AutoMineCheckTorch && (!AppVars.DoAutoMine || AutoMineRuntime.HasPendingMineRoute()))
            {
                var torchHtml = ProcessAutoMineTorchCheck(address, html);
                if (!string.IsNullOrEmpty(torchHtml))
                    return torchHtml;
            }

            if (!AppVars.DoAutoMine)
                return null;

            if (AppVars.AutoMineCheckPickaxe)
            {
                var checkHtml = ProcessAutoMinePickaxeCheck(address, html);
                if (!string.IsNullOrEmpty(checkHtml))
                    return checkHtml;
            }

            if (!AppVars.AutoMineArmedPickaxe)
            {
                var wearHtml = ProcessAutoMinePickaxeWear(address, html);
                if (!string.IsNullOrEmpty(wearHtml))
                    return wearHtml;
            }

            if (AppVars.AutoMineCheckTorch)
            {
                var torchHtml = ProcessAutoMineTorchCheck(address, html);
                if (!string.IsNullOrEmpty(torchHtml))
                    return torchHtml;
            }

            if (isMinePage && AppVars.AutoMineArmedPickaxe)
            {
                var routeScript = AutoMineRuntime.BuildPendingMoveInjection("main_php_mine_page");
                if (!string.IsNullOrEmpty(routeScript))
                    return InjectBeforeBodyEnd(html, routeScript);
            }

            return null;
        }

        private static string ProcessAutoMinePickaxeCheck(string address, string html)
        {
            if (MainPhpIsPerc(html) || MainPhpIsInv(html))
            {
                if (MainPhpArmedAutoMinePickaxe(html))
                {
                    AppVars.AutoMineCheckPickaxe = false;
                    return BuildReturnToMineRedirect("АвтоШахтёр: кирка проверена", "auto_mine_pickaxe_checked", html);
                }

                return null;
            }

            var perchtml = MainPhpFindPerc(html);
            if (!string.IsNullOrEmpty(perchtml))
                return perchtml;

            return BuildRedirect("АвтоШахтёр: персонаж", "main.php?get_id=56&act=10&go=inf&an_auto_mine_pickaxe=1");
        }

        private static string ProcessAutoMinePickaxeWear(string address, string html)
        {
            var invHtml = MainPhpFindInv(html, AutoMineRuntime.PickaxeInventoryFilter);
            if (!string.IsNullOrEmpty(invHtml))
                return invHtml;

            if (MainPhpIsInv(html))
            {
                invHtml = MainPhpWearAutoMinePickaxe(html);
                if (!string.IsNullOrEmpty(invHtml))
                    return invHtml;

                if (!AddressHasInventoryFilter(address, AutoMineRuntime.PickaxeInventoryFilter))
                    return BuildRedirect("АвтоШахтёр: кирки", "main.php?im=0&wca=3&an_auto_mine_pickaxe=1");

                AutoMineRuntime.StopBecauseNoPickaxe();
            }

            return null;
        }

        private static string ProcessAutoMineTorchCheck(string address, string html)
        {
            if (MainPhpArmedAutoMineTorch(html))
            {
                AppVars.AutoMineTorchReady = true;
                AppVars.AutoMineCheckTorch = false;
                return BuildReturnToMineRedirect("АвтоШахтёр: факел готов", "auto_mine_torch_ready", html);
            }

            var invHtml = MainPhpFindInv(html, AutoMineRuntime.TorchInventoryFilter);
            if (!string.IsNullOrEmpty(invHtml))
                return invHtml;

            if (MainPhpIsInv(html))
            {
                invHtml = MainPhpWearAutoMineTorch(html);
                if (!string.IsNullOrEmpty(invHtml))
                    return invHtml;

                if (!AddressHasInventoryFilter(address, AutoMineRuntime.TorchInventoryFilter))
                    return BuildRedirect("АвтоШахтёр: факел", "main.php?im=0&wca=4&an_auto_mine_torch=1");

                AutoMineRuntime.StopBecauseNoTorch();
            }
            else
            {
                return BuildRedirect("АвтоШахтёр: факел", "main.php?im=0&wca=4&an_auto_mine_torch=1");
            }

            return null;
        }

        private static bool MainPhpArmedAutoMinePickaxe(string html)
        {
            var parsedDressed = new ParsedDressed(html);
            if (!parsedDressed.Valid)
                return false;

            var result = parsedDressed.IsWearAutoMinePickaxe(AutoMineRuntime.GetEnabledPickaxeNames());
            AppVars.AutoMineArmedPickaxe = result;
            if (result)
            {
                AppLog.i(AutoMineRuntime.TraceChain, "MainPhpAutoMine", "pickaxe armed: " + AppVars.AutoMinePickaxeHand + " " + AppVars.AutoMinePickaxeHandD);
            }

            return result;
        }

        private static bool MainPhpArmedAutoMineTorch(string html)
        {
            var parsedDressed = new ParsedDressed(html);
            var result = parsedDressed.Valid && parsedDressed.IsWearAutoMineTorch(AutoMineRuntime.GetEnabledTorchNames());
            if (result)
            {
                AppLog.i(AutoMineRuntime.TraceChain, "MainPhpAutoMine", "torch armed: " + AppVars.AutoMineTorchHand + " " + AppVars.AutoMineTorchHandD);
            }

            return result;
        }

        private static string MainPhpWearAutoMinePickaxe(string html)
        {
            if (MainPhpArmedAutoMinePickaxe(html))
            {
                AppVars.AutoMineCheckPickaxe = false;
                AppVars.AutoMineArmedPickaxe = true;
                return BuildReturnToMineRedirect("АвтоШахтёр: кирка уже надета", "auto_mine_pickaxe_ready", html);
            }

            var invList = GetInvList(html);
            var pickaxes = AutoMineRuntime.GetEnabledPickaxeNames();
            foreach (var thing in invList)
            {
                for (var i = 0; i < pickaxes.Length; i++)
                {
                    if (thing.Name.IndexOf(pickaxes[i], StringComparison.CurrentCultureIgnoreCase) >= 0 && !string.IsNullOrEmpty(thing.WearLink))
                    {
                        AppVars.AutoMineCheckPickaxe = true;
                        AppLog.i(AutoMineRuntime.TraceChain, "MainPhpAutoMine", "wear pickaxe: " + thing.Name);
                        return BuildRedirect("Одеваем " + thing.Name, thing.WearLink);
                    }
                }
            }

            return null;
        }

        private static string MainPhpWearAutoMineTorch(string html)
        {
            if (MainPhpArmedAutoMineTorch(html))
            {
                AppVars.AutoMineTorchReady = true;
                AppVars.AutoMineCheckTorch = false;
                return BuildReturnToMineRedirect("АвтоШахтёр: факел уже надет", "auto_mine_torch_ready", html);
            }

            var invList = GetInvList(html);
            var torches = AutoMineRuntime.GetEnabledTorchNames();
            foreach (var thing in invList)
            {
                for (var i = 0; i < torches.Length; i++)
                {
                    if (thing.Name.IndexOf(torches[i], StringComparison.CurrentCultureIgnoreCase) >= 0 && !string.IsNullOrEmpty(thing.WearLink))
                    {
                        AppVars.AutoMineCheckTorch = true;
                        AppVars.AutoMineTorchReady = false;
                        AppLog.i(AutoMineRuntime.TraceChain, "MainPhpAutoMine", "wear torch: " + thing.Name);
                        return BuildRedirect("Одеваем " + thing.Name, thing.WearLink);
                    }
                }
            }

            return null;
        }

        private static string BuildReturnToMineRedirect(string title, string actionName, string html)
        {
            var link = FindAutoMineReturnLink(html);
            if (!string.IsNullOrEmpty(link))
            {
                AppLog.i(AutoMineRuntime.TraceChain, "MainPhpAutoMine", "return to mine using parsed link: action=" + actionName + ", link=" + link);
                return BuildRedirect(title, AddAutoMineReturnMarker(link));
            }

            link = "main.php?get_id=56&act=10&go=ret";
            var vcode = ExtractAutoMineMenuVcode(html, "ret");
            if (!string.IsNullOrEmpty(vcode))
            {
                link += "&vcode=" + vcode;
            }
            else
            {
                AppLog.w(AutoMineRuntime.TraceChain, "MainPhpAutoMine", "return to mine without vcode: action=" + actionName);
            }

            return BuildRedirect(title, AddAutoMineReturnMarker(link));
        }

        private static string FindAutoMineReturnLink(string html)
        {
            if (string.IsNullOrEmpty(html))
                return string.Empty;

            var retVcode = ExtractAutoMineMenuVcode(html, "ret");
            var link = FindMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=ret", "vcode=");
            if (string.IsNullOrEmpty(link))
                link = FindMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=ret");
            if (string.IsNullOrEmpty(link) && !string.IsNullOrEmpty(retVcode))
                link = "main.php?get_id=56&act=10&go=ret&vcode=" + retVcode;
            if (string.IsNullOrEmpty(link))
            {
                var infLink = FindMainPhpLinkByQueryParts(html, "get_id=56", "act=10", "go=inf", "vcode=");
                if (!string.IsNullOrEmpty(infLink))
                    link = infLink.Replace("go=inf", "go=ret");
            }
            if (string.IsNullOrEmpty(link))
                link = FindAutoMineReturnButtonLink(html, retVcode);

            return NormalizeAutoMineMainLink(link);
        }

        private static string FindMainPhpLinkByQueryParts(string html, params string[] queryParts)
        {
            if (string.IsNullOrEmpty(html))
                return string.Empty;

            var source = html.Replace("&amp;", "&");
            var matches = AutoMineMainPhpLinkRegex.Matches(source);
            for (var i = 0; i < matches.Count; i++)
            {
                var candidate = NormalizeAutoMineMainLink(matches[i].Value);
                if (string.IsNullOrEmpty(candidate))
                    continue;

                var found = true;
                if (queryParts != null)
                {
                    for (var j = 0; j < queryParts.Length; j++)
                    {
                        var part = queryParts[j];
                        if (!string.IsNullOrEmpty(part) && candidate.IndexOf(part, StringComparison.OrdinalIgnoreCase) < 0)
                        {
                            found = false;
                            break;
                        }
                    }
                }

                if (found)
                    return candidate;
            }

            return string.Empty;
        }

        private static string FindAutoMineReturnButtonLink(string html, string retVcode)
        {
            const string returnMarker = "value=\"Вернуться\">";
            var posReturn = html.IndexOf(returnMarker, StringComparison.OrdinalIgnoreCase);
            if (posReturn < 0)
                return string.Empty;

            const string onClickPrefix = "onclick=\"location='";
            var posOnClick = html.LastIndexOf(onClickPrefix, posReturn, StringComparison.OrdinalIgnoreCase);
            if (posOnClick < 0)
                return string.Empty;

            var linkStart = posOnClick + onClickPrefix.Length;
            var linkEnd = html.IndexOf('\'', linkStart);
            if (linkEnd <= linkStart)
                return string.Empty;

            var link = NormalizeAutoMineMainLink(html.Substring(linkStart, linkEnd - linkStart));
            if (!string.IsNullOrEmpty(retVcode))
                return "main.php?get_id=56&act=10&go=ret&vcode=" + retVcode;
            if (link.IndexOf("go=inf", StringComparison.OrdinalIgnoreCase) >= 0)
                return link.Replace("go=inf", "go=ret");
            if (link.Equals("main.php", StringComparison.OrdinalIgnoreCase) || link.Equals("main.php?", StringComparison.OrdinalIgnoreCase))
                return "main.php?get_id=56&act=10&go=ret";
            return link;
        }

        private static string ExtractAutoMineMenuVcode(string html, string menuKey)
        {
            if (string.IsNullOrEmpty(html) || string.IsNullOrEmpty(menuKey))
                return string.Empty;

            var pattern = "\\[\\s*[\\\"']" + Regex.Escape(menuKey) + "[\\\"']\\s*,\\s*[\\\"'][^\\\"']*[\\\"']\\s*,\\s*[\\\"']([^\\\"']+)";
            var match = Regex.Match(html, pattern, RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);
            return match.Success ? match.Groups[1].Value : string.Empty;
        }

        private static string NormalizeAutoMineMainLink(string link)
        {
            if (string.IsNullOrEmpty(link))
                return string.Empty;

            var normalized = link.Trim().Replace("&amp;", "&");
            while (normalized.StartsWith("../", StringComparison.Ordinal))
                normalized = normalized.Substring(3);
            if (normalized.StartsWith("http://www.neverlands.ru/", StringComparison.OrdinalIgnoreCase))
                normalized = normalized.Substring("http://www.neverlands.ru/".Length);
            else if (normalized.StartsWith("https://www.neverlands.ru/", StringComparison.OrdinalIgnoreCase))
                normalized = normalized.Substring("https://www.neverlands.ru/".Length);
            else if (normalized.StartsWith("http://neverlands.ru/", StringComparison.OrdinalIgnoreCase))
                normalized = normalized.Substring("http://neverlands.ru/".Length);
            else if (normalized.StartsWith("https://neverlands.ru/", StringComparison.OrdinalIgnoreCase))
                normalized = normalized.Substring("https://neverlands.ru/".Length);
            else if (normalized.StartsWith("/main.php", StringComparison.OrdinalIgnoreCase))
                normalized = normalized.Substring(1);
            else if (normalized.StartsWith("?", StringComparison.Ordinal))
                normalized = "main.php" + normalized;

            return normalized;
        }

        private static string AddAutoMineReturnMarker(string link)
        {
            if (string.IsNullOrEmpty(link))
                return link;

            var result = link;
            if (result.IndexOf("an_auto_mine=", StringComparison.OrdinalIgnoreCase) < 0)
                result += (result.IndexOf('?') >= 0 ? "&" : "?") + "an_auto_mine=1";
            result += "&r=" + DateTime.Now.Ticks.ToString(CultureInfo.InvariantCulture);
            return result;
        }

        private static string InjectBeforeBodyEnd(string html, string injection)
        {
            if (string.IsNullOrEmpty(html) || string.IsNullOrEmpty(injection))
                return html;

            var bodyEnd = html.LastIndexOf("</body>", StringComparison.OrdinalIgnoreCase);
            return bodyEnd >= 0 ? html.Insert(bodyEnd, injection) : html + injection;
        }

        private static bool AddressHasInventoryFilter(string address, string filter)
        {
            return !string.IsNullOrEmpty(address) &&
                   !string.IsNullOrEmpty(filter) &&
                   address.IndexOf(filter.TrimStart('&'), StringComparison.OrdinalIgnoreCase) >= 0;
        }
    }
}
