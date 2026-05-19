using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace ANClient
{
    internal static class ContactRenderHelper
    {
        internal sealed class EffectState
        {
            internal readonly int Id;
            internal readonly int Count;
            internal readonly string Timeout;

            internal EffectState(int id, int count, string timeout)
            {
                Id = id;
                Count = count <= 0 ? 1 : count;
                Timeout = timeout == null ? string.Empty : timeout.Trim();
            }
        }

        internal static bool IsNeutralClanName(string clanName)
        {
            if (string.IsNullOrEmpty(clanName))
                return true;

            return clanName.Trim().Equals("none", StringComparison.OrdinalIgnoreCase);
        }

        internal static string GetInclinationName(int inclination)
        {
            switch (inclination)
            {
                case 4:
                    return "Chaos";
                case 3:
                    return "Sumers";
                case 2:
                    return "Lights";
                case 1:
                    return "Darks";
                default:
                    return "0";
            }
        }

        internal static string GetInclinationSign(string inclination)
        {
            switch (inclination)
            {
                case "1":
                    return "darks.gif";
                case "2":
                    return "lights.gif";
                case "3":
                    return "sumers.gif";
                case "4":
                    return "chaoss.gif";
                case "5":
                    return "light.gif";
                case "6":
                    return "dark.gif";
                case "7":
                    return "sumer.gif";
                case "8":
                    return "chaos.gif";
                case "9":
                    return "angel.gif";
                default:
                    return string.Empty;
            }
        }

        internal static List<EffectState> ParseEffectStatesCsv(string effectStatesCsv, string fallbackEffectIdsCsv)
        {
            var byId = new Dictionary<int, EffectState>();
            var order = new List<int>();
            if (!string.IsNullOrEmpty(effectStatesCsv))
            {
                var entries = effectStatesCsv.Split(',');
                foreach (var entry in entries)
                {
                    if (string.IsNullOrEmpty(entry))
                        continue;

                    var parts = entry.Trim().Split(new[] { ':' }, 3);
                    var id = ParseIntSafe(parts.Length > 0 ? parts[0] : string.Empty, 0);
                    if (id <= 0)
                        continue;

                    var count = ParseIntSafe(parts.Length > 1 ? parts[1] : string.Empty, 1);
                    var timeout = parts.Length > 2 ? SanitizeEffectStatePart(parts[2]) : string.Empty;
                    AddOrMergeEffectState(byId, order, new EffectState(id, count, timeout));
                }
            }

            if (order.Count == 0)
            {
                var fallbackIds = ParseEffectIdsCsv(fallbackEffectIdsCsv);
                foreach (var id in fallbackIds)
                {
                    AddOrMergeEffectState(byId, order, new EffectState(id, 1, string.Empty));
                }
            }

            var result = new List<EffectState>();
            foreach (var id in order)
            {
                result.Add(byId[id]);
            }

            return result;
        }

        internal static List<int> ExtractEffectIds(List<EffectState> effectStates)
        {
            var result = new List<int>();
            if (effectStates == null)
                return result;

            foreach (var state in effectStates)
            {
                if (state == null || state.Id <= 0 || result.Contains(state.Id))
                    continue;

                result.Add(state.Id);
            }

            return result;
        }

        internal static string ToEffectStatesCsv(List<EffectState> effectStates)
        {
            if (effectStates == null || effectStates.Count == 0)
                return string.Empty;

            var byId = new Dictionary<int, EffectState>();
            var order = new List<int>();
            foreach (var state in effectStates)
            {
                AddOrMergeEffectState(byId, order, state);
            }

            var parts = new List<string>();
            foreach (var id in order)
            {
                var state = byId[id];
                parts.Add(state.Id.ToString(CultureInfo.InvariantCulture) + ":" +
                          Math.Max(1, state.Count).ToString(CultureInfo.InvariantCulture) + ":" +
                          SanitizeEffectStatePart(state.Timeout));
            }

            return string.Join(",", parts.ToArray());
        }

        internal static List<int> ParseEffectIdsCsv(string effectIdsCsv)
        {
            var result = new List<int>();
            if (string.IsNullOrEmpty(effectIdsCsv))
                return result;

            var parts = effectIdsCsv.Split(',');
            foreach (var part in parts)
            {
                var id = ParseIntSafe(part, 0);
                if (id > 0 && !result.Contains(id))
                {
                    result.Add(id);
                }
            }

            return result;
        }

        internal static string ToEffectIdsCsv(List<int> effectIds)
        {
            if (effectIds == null || effectIds.Count == 0)
                return string.Empty;

            var result = new List<string>();
            foreach (var effectId in effectIds)
            {
                if (effectId > 0 && !result.Contains(effectId.ToString(CultureInfo.InvariantCulture)))
                {
                    result.Add(effectId.ToString(CultureInfo.InvariantCulture));
                }
            }

            return string.Join(",", result.ToArray());
        }

        internal static string FormatEffectCounterText(EffectState state)
        {
            if (state == null)
                return string.Empty;

            return "[x" + Math.Max(1, state.Count).ToString(CultureInfo.InvariantCulture) + "] (" + NormalizeTimeoutToHourMinute(state.Timeout) + ")";
        }

        internal static string FormatEffectCounterCompactText(EffectState state)
        {
            if (state == null)
                return string.Empty;

            return "[x" + Math.Max(1, state.Count).ToString(CultureInfo.InvariantCulture) + "](" + NormalizeTimeoutToHourMinute(state.Timeout) + ")";
        }

        internal static string FormatEffectCounterHtml(EffectState state)
        {
            if (state == null)
                return string.Empty;

            return "<span style=\"display:inline-block;font-size:75%;line-height:1.05;vertical-align:middle;\">[x" +
                   Math.Max(1, state.Count).ToString(CultureInfo.InvariantCulture) +
                   "]<br>(" +
                   NormalizeTimeoutToHourMinute(state.Timeout) +
                   ")</span>";
        }

        internal static string BuildEffectIconsHtml(string effectStatesCsv, string fallbackEffectIdsCsv)
        {
            var states = ParseEffectStatesCsv(effectStatesCsv, fallbackEffectIdsCsv);
            if (states.Count == 0)
                return string.Empty;

            var sb = new StringBuilder();
            foreach (var state in states)
            {
                if (state == null || state.Id <= 0)
                    continue;

                sb.Append("<span class=\"an-room-effect-wrap\">");
                sb.Append("<img class=\"an-room-injury-icon\" src=http://image.neverlands.ru/pinfo/eff_");
                sb.Append(state.Id.ToString(CultureInfo.InvariantCulture));
                sb.Append(".gif border=0 width=15 height=12 title=\"эффект #");
                sb.Append(state.Id.ToString(CultureInfo.InvariantCulture));
                sb.Append("\" align=absmiddle>");
                sb.Append(FormatEffectCounterHtml(state));
                sb.Append("</span>");
            }

            return sb.ToString();
        }

        private static void AddOrMergeEffectState(Dictionary<int, EffectState> byId, List<int> order, EffectState state)
        {
            if (state == null || state.Id <= 0)
                return;

            EffectState existing;
            if (!byId.TryGetValue(state.Id, out existing))
            {
                byId.Add(state.Id, state);
                order.Add(state.Id);
                return;
            }

            var mergedCount = Math.Max(1, existing.Count) + Math.Max(1, state.Count);
            var mergedTimeout = string.IsNullOrEmpty(existing.Timeout) ? state.Timeout : existing.Timeout;
            byId[state.Id] = new EffectState(state.Id, mergedCount, mergedTimeout);
        }

        private static int ParseIntSafe(string value, int fallback)
        {
            if (string.IsNullOrEmpty(value))
                return fallback;

            int parsed;
            return int.TryParse(value.Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out parsed) ? parsed : fallback;
        }

        private static string SanitizeEffectStatePart(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            return value.Trim().Replace(",", string.Empty).Replace("\n", string.Empty).Replace("\r", string.Empty);
        }

        private static string NormalizeTimeoutToHourMinute(string timeout)
        {
            var safe = timeout == null ? string.Empty : timeout.Trim();
            if (safe.Length == 0)
                return "--:--";

            var parts = safe.Split(':');
            if (parts.Length >= 2)
                return PadTimePart(parts[0]) + ":" + PadTimePart(parts[1]);

            return safe;
        }

        private static string PadTimePart(string value)
        {
            var safe = value == null ? string.Empty : value.Trim();
            return safe.Length == 1 ? "0" + safe : safe;
        }
    }
}
