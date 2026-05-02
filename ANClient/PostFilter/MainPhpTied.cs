namespace ANClient.PostFilter
{
    using System;
    using System.Globalization;
    using System.Text.RegularExpressions;
    using ANForms;

    internal static partial class Filter
    {
        private const string LegacyTiedMarker = "Усталость:</td><td bgcolor=#336699 nowrap><font class=proce><font color=#ffffff><b><div align=center>&nbsp;<b>";
        private static readonly Regex HtmlTagRegex = new Regex("<[^>]+>", RegexOptions.Compiled);
        private static readonly Regex LeadingNumberRegex = new Regex(@"^\s*(\d{1,3})\s*%?(\s|$)", RegexOptions.Compiled);
        private static readonly Regex WhitespaceRegex = new Regex(@"\s+", RegexOptions.Compiled);
        private static readonly Regex HpmpRegex = new Regex(@"(?:var\s+)?hpmp\s*=\s*\[\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*(?<energy>-?\d+)", RegexOptions.IgnoreCase | RegexOptions.Compiled);

        private static bool MainPhpTied(string html)
        {
            int tied;
            string source;
            if (!TryParseMainPhpTied(html, out tied, out source))
            {
                if (!string.IsNullOrEmpty(html) && html.IndexOf("Усталость", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    AppLog.w("MainPhp", "MainPhp: tied label found but value was not parsed");
                }

                return false;
            }

            UpdateTiedFromMainPhp(tied, source);
            return true;
        }

        private static bool TryParseMainPhpTied(string html, out int tied, out string source)
        {
            tied = 0;
            source = null;
            if (string.IsNullOrEmpty(html))
            {
                return false;
            }

            var postied = html.IndexOf(LegacyTiedMarker, StringComparison.OrdinalIgnoreCase);
            if (postied != -1 && TryParseLegacyTied(html, postied + LegacyTiedMarker.Length, out tied))
            {
                source = "legacy_marker";
                return true;
            }

            if (TryParseLabeledTied(html, out tied))
            {
                source = "label";
                return true;
            }

            if (TryParseHpmpTied(html, out tied))
            {
                source = "hpmp";
                return true;
            }

            return false;
        }

        private static bool TryParseLegacyTied(string html, int postied, out int tied)
        {
            tied = 0;
            var pos2 = html.IndexOf("</b>", postied, StringComparison.OrdinalIgnoreCase);
            if (pos2 == -1)
            {
                return false;
            }

            var stied = html.Substring(postied, pos2 - postied);
            if (!int.TryParse(stied, NumberStyles.Integer, CultureInfo.InvariantCulture, out tied))
            {
                return false;
            }

            tied = NormalizeTied(tied);
            return true;
        }

        private static bool TryParseLabeledTied(string html, out int tied)
        {
            tied = 0;
            var pos = 0;
            while (pos >= 0 && pos < html.Length)
            {
                pos = html.IndexOf("Усталость", pos, StringComparison.OrdinalIgnoreCase);
                if (pos == -1)
                {
                    return false;
                }

                var rowStart = html.LastIndexOf("<tr", pos, StringComparison.OrdinalIgnoreCase);
                if (rowStart == -1 || pos - rowStart > 700)
                {
                    rowStart = html.LastIndexOf("<td", pos, StringComparison.OrdinalIgnoreCase);
                }

                if (rowStart == -1 || pos - rowStart > 700)
                {
                    rowStart = pos;
                }

                var end = html.IndexOf("</tr>", pos, StringComparison.OrdinalIgnoreCase);
                if (end == -1 || end - pos > 700)
                {
                    end = Math.Min(html.Length, pos + 700);
                }

                var fragment = html.Substring(rowStart, end - rowStart);
                var text = HtmlTagRegex.Replace(fragment, " ")
                    .Replace("&nbsp;", " ")
                    .Replace("&#160;", " ")
                    .Replace("%", " ");
                text = WhitespaceRegex.Replace(text, " ").Trim();
                var colon = text.IndexOf(':');
                if (colon == -1 || colon + 1 >= text.Length)
                {
                    pos += "Усталость".Length;
                    continue;
                }

                var label = text.Substring(0, colon).Trim();
                if (!label.Equals("Усталость", StringComparison.OrdinalIgnoreCase))
                {
                    pos += "Усталость".Length;
                    continue;
                }

                text = text.Substring(colon + 1);

                var match = LeadingNumberRegex.Match(text);
                if (match.Success && int.TryParse(match.Groups[1].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out tied))
                {
                    tied = NormalizeTied(tied);
                    return true;
                }

                pos += "Усталость".Length;
            }

            return false;
        }

        private static bool TryParseHpmpTied(string html, out int tied)
        {
            tied = 0;
            var match = HpmpRegex.Match(html);
            if (!match.Success)
            {
                return false;
            }

            int energy;
            if (!int.TryParse(match.Groups["energy"].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out energy))
            {
                return false;
            }

            tied = NormalizeTied(100 - energy);
            return true;
        }

        private static int NormalizeTied(int tied)
        {
            if (tied < 0)
            {
                return 0;
            }

            return tied > 100 ? 100 : tied;
        }

        private static void UpdateTiedFromMainPhp(int tied, string source)
        {
            AppLog.d("MainPhp", "MainPhp: tied parsed from " + source + ", tied=" + tied + "%");
            try
            {
                if (AppVars.MainForm != null)
                    AppVars.MainForm.BeginInvoke(
                        new UpdateTiedDelegate(AppVars.MainForm.UpdateTied),
                        new object[] { tied });
            }
            catch (InvalidOperationException)
            {
            }
        }
    }
}
