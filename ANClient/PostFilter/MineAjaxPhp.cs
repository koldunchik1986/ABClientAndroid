using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using ANClient.ANForms;

namespace ANClient.PostFilter
{
    internal static partial class Filter
    {
        private static readonly Regex MineDiggReportRegex = new Regex(
            "(?:^|\\^)DIGG@([^@\\r\\n]+)@(\\d+)",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

        private static readonly Regex MineResourceRegex = new Regex(
            "([^,.:]+?)\\s*\\((\\d+(?:[\\.,]\\d+)?)\\)",
            RegexOptions.CultureInvariant);

        private static byte[] MineAjaxPhp(string address, byte[] array)
        {
            if (array == null || array.Length == 0)
                return array;

            var html = AppVars.Codepage.GetString(array);
            if (string.IsNullOrEmpty(html))
                return array;

            var shouldHandle = AppVars.DoAutoMine || AutoMineRuntime.HasPendingMineRoute() ||
                               html.IndexOf("вам нужен факел", StringComparison.OrdinalIgnoreCase) >= 0 ||
                               html.IndexOf("вам нужна кирка", StringComparison.OrdinalIgnoreCase) >= 0;
            if (!shouldHandle)
                return array;

            AutoMineRuntime.OnMineAjaxResponse(address, html);
            var report = ParseMineDiggReport(address, html);
            var isNewReport = true;
            if (report != null)
            {
                isNewReport = AutoMineRuntime.OnMineDiggReport(report.EventKey, report.ServerDelaySeconds);
                var statsUpdated = false;
                if (isNewReport && report.DeltaByResourceKg.Count > 0 && AppVars.MainForm != null)
                {
                    AppVars.MainForm.UpdateMineResourceStatsSafe(report.DeltaByResourceKg);
                    statsUpdated = true;
                }

                AppLog.i(AutoMineRuntime.TraceChain, "MineAjaxPhp", "DIGG report parsed: resources=" + report.Resources.Count.ToString(CultureInfo.InvariantCulture) + ", statsUpdated=" + statsUpdated + ", serverDelay=" + report.ServerDelaySeconds.ToString(CultureInfo.InvariantCulture) + ", new=" + isNewReport);
            }

            if (AppVars.DoAutoMine && AppVars.Profile != null && AppVars.Profile.AutoMineChatReport)
            {
                PublishMineChatReport(address, html, report, isNewReport);
            }

            return array;
        }

        private static MineDiggReport ParseMineDiggReport(string address, string html)
        {
            if (string.IsNullOrEmpty(html))
                return null;

            var match = MineDiggReportRegex.Match(html);
            if (!match.Success)
                return null;

            var reportText = CompactText(match.Groups[1].Value);
            int serverDelay;
            if (!int.TryParse(match.Groups[2].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out serverDelay))
                serverDelay = 0;

            var resources = ParseMineResources(reportText);
            return new MineDiggReport((address ?? string.Empty) + "|" + reportText + "|" + serverDelay.ToString(CultureInfo.InvariantCulture), reportText, serverDelay, resources, BuildMineResourceDelta(resources));
        }

        private static Dictionary<string, double> BuildMineResourceDelta(List<MineResourceEntry> resources)
        {
            var result = new Dictionary<string, double>(StringComparer.OrdinalIgnoreCase);
            if (resources == null)
                return result;

            for (var i = 0; i < resources.Count; i++)
            {
                var entry = resources[i];
                if (entry == null || string.IsNullOrEmpty(entry.Name) || entry.Kg <= 0d)
                    continue;

                if (result.ContainsKey(entry.Name))
                    result[entry.Name] += entry.Kg;
                else
                    result.Add(entry.Name, entry.Kg);
            }

            return result;
        }

        private static List<MineResourceEntry> ParseMineResources(string reportText)
        {
            var result = new List<MineResourceEntry>();
            if (string.IsNullOrEmpty(reportText))
                return result;

            var resourcePart = reportText;
            var colon = resourcePart.IndexOf(':');
            if (colon >= 0 && colon + 1 < resourcePart.Length)
                resourcePart = resourcePart.Substring(colon + 1);

            var digIndex = resourcePart.IndexOf("Добываем", StringComparison.OrdinalIgnoreCase);
            if (digIndex >= 0)
                resourcePart = resourcePart.Substring(0, digIndex);

            var matcher = MineResourceRegex.Match(resourcePart);
            while (matcher.Success)
            {
                double kg;
                if (double.TryParse(matcher.Groups[2].Value.Replace(',', '.'), NumberStyles.Any, CultureInfo.InvariantCulture, out kg) && kg > 0d)
                {
                    result.Add(new MineResourceEntry(CompactText(matcher.Groups[1].Value), kg));
                }

                matcher = matcher.NextMatch();
            }

            return result;
        }

        private static void PublishMineChatReport(string address, string html, MineDiggReport report, bool isNewReport)
        {
            if (report != null && !isNewReport)
                return;

            var message = string.Empty;
            if (report != null)
            {
                message = BuildDiggChatMessage(report);
            }
            else if (html.IndexOf("Обнаружены ресурсы", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                message = CompactFromMarker(html, "Обнаружены ресурсы");
            }
            else if (html.IndexOf("Добыты ресурсы", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                message = CompactFromMarker(html, "Добыты ресурсы");
            }
            else if (html.IndexOf("Вы не нашли ни одного ресурса", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                message = "Вы не нашли ни одного ресурса";
            }

            if (string.IsNullOrEmpty(message) || AppVars.MainForm == null)
                return;

            AppVars.MainForm.WriteChatMsgSafe("<font color=#333399><b>[auto_mine/mine_ajax]</b></font> " + EscapeHtml(message));
            AppLog.i(AutoMineRuntime.TraceChain, "MineAjaxPhp", "chat report posted: address=" + (address ?? string.Empty) + ", message=" + message);
        }

        private static string BuildDiggChatMessage(MineDiggReport report)
        {
            var builder = new StringBuilder();
            builder.Append("Ресурсы: ");
            if (report.Resources.Count == 0)
            {
                builder.Append("не распознаны");
            }
            else
            {
                for (var i = 0; i < report.Resources.Count; i++)
                {
                    if (i > 0)
                        builder.Append(", ");
                    builder.Append(report.Resources[i].Name);
                    builder.Append(" (");
                    builder.Append(FormatKg(report.Resources[i].Kg));
                    builder.Append(" кг)");
                }
            }

            if (report.ServerDelaySeconds > 0)
            {
                builder.Append(". Задержка: ");
                builder.Append((report.ServerDelaySeconds + 2).ToString(CultureInfo.InvariantCulture));
                builder.Append(" сек.");
            }

            return builder.ToString();
        }

        private static string CompactFromMarker(string value, string marker)
        {
            var index = value.IndexOf(marker, StringComparison.OrdinalIgnoreCase);
            return CompactText(index >= 0 ? value.Substring(index) : value);
        }

        private static string CompactText(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            var result = Regex.Replace(value.Replace('\u00A0', ' '), "<[^>]+>", " ");
            result = Regex.Replace(result, "\\s+", " ").Trim();
            return result.Length > 400 ? result.Substring(0, 400) + "..." : result;
        }

        private static string FormatKg(double value)
        {
            var formatted = value.ToString("0.##", CultureInfo.InvariantCulture);
            return formatted;
        }

        private static string EscapeHtml(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            return value.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace("\"", "&quot;").Replace("'", "&#39;");
        }

        private sealed class MineDiggReport
        {
            internal readonly string EventKey;
            internal readonly string ReportText;
            internal readonly int ServerDelaySeconds;
            internal readonly List<MineResourceEntry> Resources;
            internal readonly Dictionary<string, double> DeltaByResourceKg;

            internal MineDiggReport(string eventKey, string reportText, int serverDelaySeconds, List<MineResourceEntry> resources, Dictionary<string, double> deltaByResourceKg)
            {
                EventKey = eventKey;
                ReportText = reportText;
                ServerDelaySeconds = serverDelaySeconds;
                Resources = resources ?? new List<MineResourceEntry>();
                DeltaByResourceKg = deltaByResourceKg ?? new Dictionary<string, double>(StringComparer.OrdinalIgnoreCase);
            }
        }

        private sealed class MineResourceEntry
        {
            internal readonly string Name;
            internal readonly double Kg;

            internal MineResourceEntry(string name, double kg)
            {
                Name = name;
                Kg = kg;
            }
        }
    }
}
