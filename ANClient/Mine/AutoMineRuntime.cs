using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Forms;
using System.Xml;
using ANClient.ANForms;

namespace ANClient
{
    internal static class AutoMineRuntime
    {
        internal const string TraceChain = "auto_mine_trace";
        internal const string PickaxeInventoryFilter = "&im=0&wca=3";
        internal const string TorchInventoryFilter = "&im=0&wca=4";

        private const int AutoDigDedupMilliseconds = 1500;
        private const int AutoDigExtraDelaySeconds = 2;
        private const int MineDiggEventDedupMilliseconds = 10000;

        private static readonly Regex DiggButtonRegex = new Regex(
            "[\\\"']digg[\\\"']\\s*,\\s*[\\\"']Начать добычу[\\\"']\\s*,\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static readonly Regex DiggCallRegex = new Regex(
            "Digg\\s*\\(\\s*[\\\"']([^\\\"']+)[\\\"']\\s*\\)",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static readonly Regex PosRegex = new Regex(
            "var\\s+pos\\s*=\\s*\\[([^\\]]*)\\]",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static readonly Regex MineIdRegex = new Regex(
            "var\\s+mineid\\s*=\\s*[\\\"']?([^;\\\"']+)[\\\"']?\\s*;",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static readonly Regex MineNameRegex = new Regex(
            "var\\s+mine\\s*=\\s*\\[\\s*[\\\"']([^\\\"']+)[\\\"']",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static readonly string[] PickaxeNames =
            {
                "Легкая кирка",
                "Тяжелая кирка",
                "Сбалансированная кирка",
                "Кирка Мастера-рудокопа",
                "Праздничная Кирка Рудокопа"
            };

        private static readonly string[] TorchNames =
            {
                "Смоляной факел",
                "Масляный факел",
                "Дварфийский фонарь"
            };

        private static readonly object MapLock = new object();
        private static readonly Dictionary<string, Dictionary<string, MineCell>> MapCells = new Dictionary<string, Dictionary<string, MineCell>>(StringComparer.OrdinalIgnoreCase);
        private static readonly List<string> PendingRouteKeys = new List<string>();

        private static bool mapLoaded;
        private static bool mapLoadFailed;
        private static string lastMineId = string.Empty;
        private static string lastLevel = string.Empty;
        private static int lastX;
        private static int lastY;
        private static bool hasLastPosition;
        private static string lastDigCode = string.Empty;
        private static string pendingTargetX = string.Empty;
        private static string pendingTargetY = string.Empty;
        private static int pendingRouteSteps = -1;
        private static string lastDigDispatchKey = string.Empty;
        private static DateTime lastDigDispatchAt = DateTime.MinValue;
        private static DateTime nextAutoDigAllowedAt = DateTime.MinValue;
        private static string lastMineDiggEventKey = string.Empty;
        private static DateTime lastMineDiggEventAt = DateTime.MinValue;
        private static string lastDispatchedDirection = string.Empty;

        internal static string[] GetDefaultPickaxeNames()
        {
            return (string[])PickaxeNames.Clone();
        }

        internal static string[] GetDefaultTorchNames()
        {
            return (string[])TorchNames.Clone();
        }

        internal static string[] GetEnabledPickaxeNames()
        {
            return GetEnabledNames(AppVars.Profile == null ? string.Empty : AppVars.Profile.AutoMinePickaxesCsv, PickaxeNames);
        }

        internal static string[] GetEnabledTorchNames()
        {
            return GetEnabledNames(AppVars.Profile == null ? string.Empty : AppVars.Profile.AutoMineTorchesCsv, TorchNames);
        }

        internal static bool IsAutoMinePage(string html)
        {
            if (string.IsNullOrEmpty(html))
                return false;

            return html.IndexOf("view_mine();", StringComparison.OrdinalIgnoreCase) >= 0 ||
                   html.IndexOf("var mine = [", StringComparison.OrdinalIgnoreCase) >= 0 ||
                   html.IndexOf("var pos = [", StringComparison.OrdinalIgnoreCase) >= 0 ||
                   html.IndexOf("\"digg\",\"Начать добычу\"", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        internal static void OnEnabled(string source)
        {
            AppVars.AutoMineCheckPickaxe = true;
            AppVars.AutoMineArmedPickaxe = false;
            AppVars.AutoMinePickaxeHand = string.Empty;
            AppVars.AutoMinePickaxeHandD = string.Empty;
            AppVars.AutoMineCheckTorch = false;
            AppVars.AutoMineTorchReady = false;
            lastDigDispatchKey = string.Empty;
            lastDigDispatchAt = DateTime.MinValue;
            nextAutoDigAllowedAt = DateTime.MinValue;
            lastMineDiggEventKey = string.Empty;
            lastMineDiggEventAt = DateTime.MinValue;
            AppLog.i(TraceChain, "AutoMineRuntime", "enabled: source=" + source);
        }

        internal static void OnDisabled(string source)
        {
            AppVars.AutoMineCheckPickaxe = false;
            AppVars.AutoMineArmedPickaxe = false;
            AppVars.AutoMinePickaxeHand = string.Empty;
            AppVars.AutoMinePickaxeHandD = string.Empty;
            AppVars.AutoMineCheckTorch = false;
            AppVars.AutoMineTorchReady = false;
            lastDigCode = string.Empty;
            lastDigDispatchKey = string.Empty;
            lastDigDispatchAt = DateTime.MinValue;
            nextAutoDigAllowedAt = DateTime.MinValue;
            lastMineDiggEventKey = string.Empty;
            lastMineDiggEventAt = DateTime.MinValue;
            ClearPendingRoute("disabled:" + source);
            AppLog.i(TraceChain, "AutoMineRuntime", "disabled: source=" + source);
        }

        internal static void UpdateMinePageSnapshot(string html, string address)
        {
            if (string.IsNullOrEmpty(html))
                return;

            var digCode = ExtractDigCode(html);
            if (!string.IsNullOrEmpty(digCode))
            {
                lastDigCode = digCode;
            }

            var posMatch = PosRegex.Match(html);
            if (posMatch.Success)
            {
                var parts = posMatch.Groups[1].Value.Split(',');
                int parsedX;
                int parsedY;
                if (parts.Length > 1 && int.TryParse(CleanJsToken(parts[1]), NumberStyles.Integer, CultureInfo.InvariantCulture, out parsedX))
                {
                    lastX = parsedX;
                    hasLastPosition = true;
                }

                if (parts.Length > 0 && int.TryParse(CleanJsToken(parts[0]), NumberStyles.Integer, CultureInfo.InvariantCulture, out parsedY))
                {
                    lastY = parsedY;
                    hasLastPosition = true;
                }

                if (parts.Length > 2)
                {
                    lastLevel = CleanJsToken(parts[2]);
                }
            }

            var mineIdMatch = MineIdRegex.Match(html);
            if (mineIdMatch.Success)
            {
                lastMineId = CleanJsToken(mineIdMatch.Groups[1].Value);
            }

            var mineNameMatch = MineNameRegex.Match(html);
            if (mineNameMatch.Success)
            {
                var mineId = DeriveMineIdFromName(CleanJsToken(mineNameMatch.Groups[1].Value));
                if (!string.IsNullOrEmpty(mineId))
                {
                    lastMineId = mineId;
                }
            }

            if (string.IsNullOrEmpty(lastMineId))
            {
                lastMineId = DeriveMineIdFromName(html);
            }

            EnsureMapLoaded();
            RefreshPendingRoute("snapshot");
            AppLog.d(TraceChain, "AutoMineRuntime", "snapshot: address=" + Safe(address) + ", mineId=" + lastMineId + ", pos=" + lastX.ToString(CultureInfo.InvariantCulture) + "/" + lastY.ToString(CultureInfo.InvariantCulture) + "/" + lastLevel + ", hasDig=" + (!string.IsNullOrEmpty(digCode)) + ", pending=" + pendingTargetX + "/" + pendingTargetY);
        }

        internal static string ExtractDigCode(string html)
        {
            if (string.IsNullOrEmpty(html))
                return string.Empty;

            var match = DiggButtonRegex.Match(html);
            if (match.Success)
                return CleanJsToken(match.Groups[1].Value);

            match = DiggCallRegex.Match(html);
            return match.Success ? CleanJsToken(match.Groups[1].Value) : string.Empty;
        }

        internal static bool ShouldDispatchAutoDig(string code, string source)
        {
            var safeCode = CleanJsToken(code);
            if (safeCode.Length == 0 || !AppVars.DoAutoMine || !AppVars.AutoMineArmedPickaxe)
            {
                AppLog.d(TraceChain, "AutoMineRuntime", "auto dig rejected: source=" + Safe(source) + ", enabled=" + AppVars.DoAutoMine + ", armed=" + AppVars.AutoMineArmedPickaxe);
                return false;
            }

            var wait = GetAutoDigWaitMs();
            if (wait > 0)
            {
                AppLog.d(TraceChain, "AutoMineRuntime", "auto dig waits: " + wait.ToString(CultureInfo.InvariantCulture) + "ms, source=" + Safe(source));
                return false;
            }

            var dispatchKey = safeCode + "|" + lastMineId + "|" + lastX.ToString(CultureInfo.InvariantCulture) + "|" + lastY.ToString(CultureInfo.InvariantCulture);
            if (dispatchKey.Equals(lastDigDispatchKey, StringComparison.Ordinal) && DateTime.Now.Subtract(lastDigDispatchAt).TotalMilliseconds < AutoDigDedupMilliseconds)
            {
                AppLog.d(TraceChain, "AutoMineRuntime", "auto dig rejected: dedup, source=" + Safe(source));
                return false;
            }

            lastDigDispatchKey = dispatchKey;
            lastDigDispatchAt = DateTime.Now;
            AppLog.i(TraceChain, "AutoMineRuntime", "auto dig approved: source=" + Safe(source));
            return true;
        }

        internal static int GetAutoDigWaitMs()
        {
            var waitUntil = nextAutoDigAllowedAt;
            if (AppVars.NeverTimer > waitUntil)
                waitUntil = AppVars.NeverTimer;

            if (waitUntil <= DateTime.Now)
                return 0;

            var ms = (int)Math.Min(int.MaxValue, Math.Max(0, waitUntil.Subtract(DateTime.Now).TotalMilliseconds));
            return ms;
        }

        internal static bool OnMineDiggReport(string eventKey, int serverDelaySeconds)
        {
            if (serverDelaySeconds > 0)
            {
                nextAutoDigAllowedAt = DateTime.Now.AddSeconds(serverDelaySeconds + AutoDigExtraDelaySeconds);
                AppLog.i(TraceChain, "AutoMineRuntime", "next dig delayed: server=" + serverDelaySeconds.ToString(CultureInfo.InvariantCulture) + "s, next=" + nextAutoDigAllowedAt.ToString("HH:mm:ss"));
            }

            if (!string.IsNullOrEmpty(eventKey) && eventKey.Equals(lastMineDiggEventKey, StringComparison.Ordinal) && DateTime.Now.Subtract(lastMineDiggEventAt).TotalMilliseconds < MineDiggEventDedupMilliseconds)
                return false;

            lastMineDiggEventKey = eventKey ?? string.Empty;
            lastMineDiggEventAt = DateTime.Now;
            return true;
        }

        internal static void OnMineAjaxResponse(string address, string html)
        {
            if (string.IsNullOrEmpty(html))
                return;

            var lower = html.ToLower(AppVars.Culture);
            if (lower.IndexOf("вам нужна кирка", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                AppVars.AutoMineCheckPickaxe = true;
                AppVars.AutoMineArmedPickaxe = false;
                RequestMainPhpReload("pickaxe_required");
                AppLog.w(TraceChain, "AutoMineRuntime", "server requires pickaxe: address=" + Safe(address));
                return;
            }

            if (lower.IndexOf("вам нужен факел", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                AppVars.AutoMineCheckTorch = true;
                AppVars.AutoMineTorchReady = false;
                RequestMainPhpReload("torch_required");
                AppLog.w(TraceChain, "AutoMineRuntime", "server requires torch: address=" + Safe(address));
                return;
            }

            if (lower.IndexOf("невозможно пройти", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                ClearPendingRoute("cannot_move:" + lastDispatchedDirection);
                AppLog.w(TraceChain, "AutoMineRuntime", "server rejected move, route cleared: dir=" + lastDispatchedDirection);
                return;
            }

            if (lower.IndexOf("вы не нашли ни одного ресурса", StringComparison.OrdinalIgnoreCase) >= 0 && AppVars.Profile != null && AppVars.Profile.AutoMineStopOnEmpty)
            {
                DisableAutoMine("Пустая добыча. АвтоШахтёр выключен.");
            }
        }

        internal static string GetCellImg(string x, string y)
        {
            int ix;
            int iy;
            if (!int.TryParse(CleanJsToken(x), NumberStyles.Integer, CultureInfo.InvariantCulture, out ix) ||
                !int.TryParse(CleanJsToken(y), NumberStyles.Integer, CultureInfo.InvariantCulture, out iy))
                return string.Empty;

            var cell = GetActiveCell(ix, iy);
            return cell == null ? string.Empty : cell.Img ?? string.Empty;
        }

        internal static string GetMineCellHtml(string x, string y, string level)
        {
            int ix;
            int iy;
            if (!int.TryParse(CleanJsToken(x), NumberStyles.Integer, CultureInfo.InvariantCulture, out ix) ||
                !int.TryParse(CleanJsToken(y), NumberStyles.Integer, CultureInfo.InvariantCulture, out iy))
                return string.Empty;

            var isCurrent = hasLastPosition && ix == lastX && iy == lastY;
            var isTarget = pendingTargetX.Equals(ix.ToString(CultureInfo.InvariantCulture), StringComparison.Ordinal) && pendingTargetY.Equals(iy.ToString(CultureInfo.InvariantCulture), StringComparison.Ordinal);
            var isRoute = IsPendingRouteCell(ix, iy);
            var cell = GetActiveCell(ix, iy);
            var color = isCurrent ? "#ffff00" : isTarget ? "#66ff66" : isRoute ? "#ff3333" : cell != null && cell.Usefull > 0 ? "#9cff9c" : "#ffffff";
            var border = isCurrent ? "border:2px solid #ffff00;" : isTarget ? "border:2px solid #66ff66;" : isRoute ? "border:2px solid #ff3333;" : string.Empty;
            return "<span style=\"position:absolute;left:0;right:0;top:50px;text-align:center;font-family:Verdana;font-size:10px;font-weight:bold;color:" + color + ";text-shadow:1px 1px #000,-1px -1px #000;" + border + "\">" + ix.ToString(CultureInfo.InvariantCulture) + "-" + iy.ToString(CultureInfo.InvariantCulture) + "</span>";
        }

        internal static string MineMoveTo(string x, string y)
        {
            int ix;
            int iy;
            if (!int.TryParse(CleanJsToken(x), NumberStyles.Integer, CultureInfo.InvariantCulture, out ix) ||
                !int.TryParse(CleanJsToken(y), NumberStyles.Integer, CultureInfo.InvariantCulture, out iy))
                return string.Empty;

            pendingTargetX = ix.ToString(CultureInfo.InvariantCulture);
            pendingTargetY = iy.ToString(CultureInfo.InvariantCulture);
            RefreshPendingRoute("mineMoveTo");
            AppLog.i(TraceChain, "AutoMineRuntime", "manual route target=" + pendingTargetX + "/" + pendingTargetY + ", first=" + GetFirstRouteDirection());
            if (PendingRouteKeys.Count > 1)
            {
                AppVars.AutoMineCheckTorch = true;
            }

            return GetFirstRouteDirection();
        }

        internal static bool HasPendingMineRoute()
        {
            return PendingRouteKeys.Count > 1 || (!string.IsNullOrEmpty(pendingTargetX) && !string.IsNullOrEmpty(pendingTargetY));
        }

        internal static string GetNextMineMoveDirection(string x, string y, string level, string source)
        {
            int ix;
            int iy;
            if (int.TryParse(CleanJsToken(x), NumberStyles.Integer, CultureInfo.InvariantCulture, out ix))
            {
                lastX = ix;
                hasLastPosition = true;
            }

            if (int.TryParse(CleanJsToken(y), NumberStyles.Integer, CultureInfo.InvariantCulture, out iy))
            {
                lastY = iy;
                hasLastPosition = true;
            }

            var safeLevel = CleanJsToken(level);
            if (!string.IsNullOrEmpty(safeLevel))
            {
                lastLevel = safeLevel;
            }

            RefreshPendingRoute("next:" + Safe(source));
            var direction = GetFirstRouteDirection();
            if (!string.IsNullOrEmpty(direction) && !AppVars.AutoMineTorchReady)
            {
                AppVars.AutoMineCheckTorch = true;
                AppLog.i(TraceChain, "AutoMineRuntime", "route waits torch check: dir=" + direction + ", source=" + Safe(source));
                RequestMainPhpReload("route_torch_check");
                return string.Empty;
            }

            return direction;
        }

        internal static void MarkMineRouteMoveDispatched(string x, string y, string level, string direction, string source)
        {
            lastDispatchedDirection = (direction ?? string.Empty).Trim().ToLowerInvariant();
            AppLog.i(TraceChain, "AutoMineRuntime", "route move dispatched: from=" + Safe(x) + "/" + Safe(y) + "/" + Safe(level) + ", dir=" + lastDispatchedDirection + ", source=" + Safe(source));
        }

        internal static string GetMoveText()
        {
            if (string.IsNullOrEmpty(pendingTargetX) || string.IsNullOrEmpty(pendingTargetY))
                return hasLastPosition ? "Текущая клетка " + lastX.ToString(CultureInfo.InvariantCulture) + "-" + lastY.ToString(CultureInfo.InvariantCulture) : string.Empty;

            var steps = pendingRouteSteps < 0 ? 0 : pendingRouteSteps;
            return "Пункт назначения " + pendingTargetX + "-" + pendingTargetY + "<br>Ещё переходов: " + steps.ToString(CultureInfo.InvariantCulture);
        }

        internal static string BuildPendingMoveInjection(string source)
        {
            if (!HasPendingMineRoute())
                return string.Empty;

            return "<script language=\"JavaScript\">setTimeout(function(){try{if(window.__ancScheduleMineRouteStep){window.__ancScheduleMineRouteStep('" + JsEscape(source) + "',350);}}catch(e){}},350);</script>";
        }

        internal static void StopBecauseNoPickaxe()
        {
            DisableAutoMine("Кирки не найдены в инвентаре. Отключаем автошахту.");
        }

        internal static void StopBecauseNoTorch()
        {
            ClearPendingRoute("no_torch");
            DisableAutoMine("Факелы/фонари не найдены в инвентаре. Отключаем АвтоШахтёр.");
        }

        internal static void DisableAutoMine(string reason)
        {
            AppVars.DoAutoMine = false;
            if (AppVars.Profile != null)
            {
                AppVars.Profile.AutoMine = false;
            }

            OnDisabled("disable:" + reason);
            if (AppVars.MainForm != null)
            {
                AppVars.MainForm.WriteChatMsgSafe(reason);
                AppVars.MainForm.UpdateAutoMineOff();
            }
        }

        internal static void RequestMainPhpReload(string source)
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppLog.i(TraceChain, "AutoMineRuntime", "main.php reload requested: source=" + source);
                    AppVars.MainForm.BeginInvoke(new ReloadMainPhpInvokeDelegate(AppVars.MainForm.ReloadMainPhpInvoke), new object[] { });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static string[] GetEnabledNames(string csv, string[] defaults)
        {
            if (string.IsNullOrEmpty(csv))
                return (string[])defaults.Clone();

            var parts = csv.Split('|');
            var result = new List<string>();
            for (var i = 0; i < parts.Length; i++)
            {
                var name = (parts[i] ?? string.Empty).Trim();
                if (name.Length > 0)
                    result.Add(name);
            }

            return result.Count == 0 ? (string[])defaults.Clone() : result.ToArray();
        }

        private static void EnsureMapLoaded()
        {
            if (mapLoaded || mapLoadFailed)
                return;

            lock (MapLock)
            {
                if (mapLoaded || mapLoadFailed)
                    return;

                var path = FindMapPath();
                if (string.IsNullOrEmpty(path))
                {
                    mapLoadFailed = true;
                    AppLog.w(TraceChain, "AutoMineRuntime", "map_mines.xml not found");
                    return;
                }

                try
                {
                    var document = new XmlDocument();
                    document.Load(path);
                    var mines = document.SelectNodes("/mines/mine");
                    var count = 0;
                    if (mines != null)
                    {
                        foreach (XmlNode mineNode in mines)
                        {
                            var mineId = ReadAttr(mineNode, "mineid");
                            var level = ReadAttr(mineNode, "level");
                            if (string.IsNullOrEmpty(level))
                                level = "1";

                            var mapKey = BuildMapKey(mineId, level);
                            if (!MapCells.ContainsKey(mapKey))
                                MapCells.Add(mapKey, new Dictionary<string, MineCell>(StringComparer.OrdinalIgnoreCase));

                            foreach (XmlNode cellNode in mineNode.ChildNodes)
                            {
                                if (!"cell".Equals(cellNode.Name, StringComparison.OrdinalIgnoreCase))
                                    continue;

                                var cell = new MineCell
                                               {
                                                   MineId = mineId,
                                                   Level = level,
                                                   X = ReadIntAttr(cellNode, "x"),
                                                   Y = ReadIntAttr(cellNode, "y"),
                                                   Img = ReadAttr(cellNode, "img"),
                                                   Usefull = ReadIntAttr(cellNode, "usefull")
                                               };
                                MapCells[mapKey][BuildCellKey(cell.X, cell.Y)] = cell;
                                count++;
                            }
                        }
                    }

                    mapLoaded = true;
                    AppLog.i(TraceChain, "AutoMineRuntime", "map_mines loaded: path=" + path + ", cells=" + count.ToString(CultureInfo.InvariantCulture));
                }
                catch (Exception ex)
                {
                    mapLoadFailed = true;
                    AppLog.e(TraceChain, "AutoMineRuntime", "map_mines load failed", ex);
                }
            }
        }

        private static string FindMapPath()
        {
            var roots = new List<string>();
            if (!string.IsNullOrEmpty(Application.StartupPath))
                roots.Add(Application.StartupPath);
            if (!string.IsNullOrEmpty(AppDomain.CurrentDomain.BaseDirectory))
                roots.Add(AppDomain.CurrentDomain.BaseDirectory);
            roots.Add(Directory.GetCurrentDirectory());

            for (var i = 0; i < roots.Count; i++)
            {
                var root = roots[i];
                var candidates = new[]
                                     {
                                         Path.Combine(root, "map_mines.xml"),
                                         Path.GetFullPath(Path.Combine(root, @"..\map_mines.xml")),
                                         Path.GetFullPath(Path.Combine(root, @"..\..\map_mines.xml")),
                                         Path.GetFullPath(Path.Combine(root, @"..\..\..\map_mines.xml"))
                                     };
                for (var j = 0; j < candidates.Length; j++)
                {
                    if (File.Exists(candidates[j]))
                        return candidates[j];
                }
            }

            return string.Empty;
        }

        private static void RefreshPendingRoute(string source)
        {
            PendingRouteKeys.Clear();
            pendingRouteSteps = -1;
            if (string.IsNullOrEmpty(pendingTargetX) || string.IsNullOrEmpty(pendingTargetY) || !hasLastPosition)
                return;

            int tx;
            int ty;
            if (!int.TryParse(pendingTargetX, NumberStyles.Integer, CultureInfo.InvariantCulture, out tx) ||
                !int.TryParse(pendingTargetY, NumberStyles.Integer, CultureInfo.InvariantCulture, out ty))
                return;

            if (tx == lastX && ty == lastY)
            {
                ClearPendingRoute("arrived:" + source);
                return;
            }

            var route = BuildRoute(lastX, lastY, tx, ty);
            if (route.Count == 0)
            {
                AppLog.w(TraceChain, "AutoMineRuntime", "route not found: from=" + lastX + "/" + lastY + ", to=" + tx + "/" + ty + ", source=" + source);
                return;
            }

            PendingRouteKeys.AddRange(route);
            pendingRouteSteps = Math.Max(0, PendingRouteKeys.Count - 1);
        }

        private static List<string> BuildRoute(int sx, int sy, int tx, int ty)
        {
            EnsureMapLoaded();
            var result = new List<string>();
            var cells = GetActiveMap();
            if (cells == null || !cells.ContainsKey(BuildCellKey(sx, sy)) || !cells.ContainsKey(BuildCellKey(tx, ty)))
                return result;

            var start = BuildCellKey(sx, sy);
            var target = BuildCellKey(tx, ty);
            var queue = new Queue<string>();
            var visited = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            queue.Enqueue(start);
            visited[start] = string.Empty;
            while (queue.Count > 0)
            {
                var key = queue.Dequeue();
                if (key.Equals(target, StringComparison.OrdinalIgnoreCase))
                    break;

                var cell = cells[key];
                AddNeighbor(queue, visited, cells, key, cell, cell.X + 1, cell.Y, cell.CanMoveRight);
                AddNeighbor(queue, visited, cells, key, cell, cell.X, cell.Y + 1, cell.CanMoveDown);
                AddNeighbor(queue, visited, cells, key, cell, cell.X - 1, cell.Y, cell.CanMoveLeft);
                AddNeighbor(queue, visited, cells, key, cell, cell.X, cell.Y - 1, cell.CanMoveUp);
            }

            if (!visited.ContainsKey(target))
                return result;

            var current = target;
            while (!string.IsNullOrEmpty(current))
            {
                result.Insert(0, current);
                current = visited[current];
            }

            return result;
        }

        private static void AddNeighbor(Queue<string> queue, Dictionary<string, string> visited, Dictionary<string, MineCell> cells, string fromKey, MineCell from, int nx, int ny, bool allowed)
        {
            if (!allowed)
                return;

            var key = BuildCellKey(nx, ny);
            if (!cells.ContainsKey(key) || visited.ContainsKey(key))
                return;

            visited[key] = fromKey;
            queue.Enqueue(key);
        }

        private static string GetFirstRouteDirection()
        {
            if (PendingRouteKeys.Count < 2)
                return string.Empty;

            int x1;
            int y1;
            int x2;
            int y2;
            if (!TryParseCellKey(PendingRouteKeys[0], out x1, out y1) || !TryParseCellKey(PendingRouteKeys[1], out x2, out y2))
                return string.Empty;

            if (x2 == x1 + 1 && y2 == y1)
                return "right";
            if (x2 == x1 - 1 && y2 == y1)
                return "left";
            if (x2 == x1 && y2 == y1 + 1)
                return "down";
            if (x2 == x1 && y2 == y1 - 1)
                return "up";

            return string.Empty;
        }

        private static void ClearPendingRoute(string reason)
        {
            if (PendingRouteKeys.Count == 0 && string.IsNullOrEmpty(pendingTargetX) && string.IsNullOrEmpty(pendingTargetY))
                return;

            PendingRouteKeys.Clear();
            pendingTargetX = string.Empty;
            pendingTargetY = string.Empty;
            pendingRouteSteps = -1;
            AppLog.i(TraceChain, "AutoMineRuntime", "route cleared: reason=" + reason);
        }

        private static bool IsPendingRouteCell(int x, int y)
        {
            var key = BuildCellKey(x, y);
            for (var i = 0; i < PendingRouteKeys.Count; i++)
            {
                if (PendingRouteKeys[i].Equals(key, StringComparison.OrdinalIgnoreCase))
                    return true;
            }

            return false;
        }

        private static Dictionary<string, MineCell> GetActiveMap()
        {
            var key = BuildMapKey(lastMineId, string.IsNullOrEmpty(lastLevel) ? "1" : lastLevel);
            Dictionary<string, MineCell> cells;
            if (MapCells.TryGetValue(key, out cells))
                return cells;

            key = BuildMapKey(lastMineId, "1");
            return MapCells.TryGetValue(key, out cells) ? cells : null;
        }

        private static MineCell GetActiveCell(int x, int y)
        {
            EnsureMapLoaded();
            var cells = GetActiveMap();
            if (cells == null)
                return null;

            MineCell cell;
            return cells.TryGetValue(BuildCellKey(x, y), out cell) ? cell : null;
        }

        private static string BuildMapKey(string mineId, string level)
        {
            return (mineId ?? string.Empty).Trim() + ":" + (string.IsNullOrEmpty(level) ? "1" : level.Trim());
        }

        private static string BuildCellKey(int x, int y)
        {
            return x.ToString(CultureInfo.InvariantCulture) + "-" + y.ToString(CultureInfo.InvariantCulture);
        }

        private static bool TryParseCellKey(string key, out int x, out int y)
        {
            x = 0;
            y = 0;
            if (string.IsNullOrEmpty(key))
                return false;

            var parts = key.Split('-');
            return parts.Length == 2 && int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out x) && int.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out y);
        }

        private static string DeriveMineIdFromName(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            if (value.IndexOf("Подгор", StringComparison.OrdinalIgnoreCase) >= 0)
                return "1";
            if (value.IndexOf("Провал", StringComparison.OrdinalIgnoreCase) >= 0)
                return "2";
            if (value.IndexOf("Пыльн", StringComparison.OrdinalIgnoreCase) >= 0)
                return "3";
            return string.Empty;
        }

        private static string CleanJsToken(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            return value.Trim().Trim('"', '\'', ' ', '\r', '\n', '\t');
        }

        private static string ReadAttr(XmlNode node, string name)
        {
            if (node == null || node.Attributes == null || node.Attributes[name] == null)
                return string.Empty;
            return node.Attributes[name].Value ?? string.Empty;
        }

        private static int ReadIntAttr(XmlNode node, string name)
        {
            int value;
            return int.TryParse(ReadAttr(node, name), NumberStyles.Integer, CultureInfo.InvariantCulture, out value) ? value : 0;
        }

        private static string Safe(string value)
        {
            return value == null ? string.Empty : value.Trim();
        }

        private static string JsEscape(string value)
        {
            return (value ?? string.Empty).Replace("\\", "\\\\").Replace("'", "\\'");
        }
    }
}
