using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;
using System.Web;

namespace ANClient.Kazna
{
    internal static class KaznaParser
    {
        internal const string BaseMainUrl = "http://www.neverlands.ru/main.php";
        internal const string BaseKaznaUrl = "main.php?wfo=1&useaction=clan-action&addid=3";

        private static readonly Regex CategoryLinkRegex = new Regex("<a\\s+[^>]*href\\s*=\\s*['\\\" ]?(?<href>[^'\\\" >]+)[^>]*>\\s*(?<inner>.*?)</a>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex ImageRegex = new Regex("<img\\s+[^>]*src\\s*=\\s*['\\\" ]?(?<src>[^'\\\" >]+)[^>]*>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex AttrTitleRegex = new Regex("\\s(?:title|alt)\\s*=\\s*['\\\"](?<value>.*?)['\\\"]", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex RowRegex = new Regex(@"<tr><td bgcolor=#f.*?</div></td></tr>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex CellRegex = new Regex(@"<td\b[^>]*>(?<cell>.*?)</td>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex WcaRegex = new Regex(@"(?:[?&])wca=([^&]+)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex UidRegex = new Regex(@"(?:[?&])uid=([^&]+)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex IndexRegex = new Regex(@"^\s*(\d+)\.\s*(.*)$", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex DurabilityRegex = new Regex(@"(\d+)\s*/\s*(\d+)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex ArtifactRegex = new Regex(@"(?<!\d)([12]\.\d{2})(?!\d)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex LocationRegex = new Regex("location\\s*=\\s*['\\\" ](?<url>[^'\\\" ]+)['\\\" ]", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex TagRegex = new Regex(@"<[^>]+>", RegexOptions.IgnoreCase | RegexOptions.Singleline);

        internal static bool IsKaznaPage(string address, string html)
        {
            if (!string.IsNullOrEmpty(address) && address.IndexOf("useaction=clan-action&addid=3", StringComparison.OrdinalIgnoreCase) != -1)
                return true;

            return !string.IsNullOrEmpty(html) &&
                   (html.IndexOf("СПИСОК ВЕЩЕЙ КЛАНА", StringComparison.CurrentCultureIgnoreCase) != -1 ||
                    html.IndexOf("main.php?get_id=29&uid=", StringComparison.OrdinalIgnoreCase) != -1);
        }

        internal static KaznaSnapshot Parse(string html, string sourceUrl)
        {
            var snapshot = new KaznaSnapshot();
            snapshot.GeneratedAt = DateTime.Now;
            snapshot.SourceUrl = NormalizeMainUrl(sourceUrl);
            snapshot.CurrentWca = ExtractWca(snapshot.SourceUrl);

            ParseCategories(html, snapshot);
            snapshot.CurrentCategoryTitle = FindCategoryTitle(snapshot.Categories, snapshot.CurrentWca);
            ParseItems(html, snapshot);
            return snapshot;
        }

        internal static string NormalizeMainUrl(string url)
        {
            if (string.IsNullOrEmpty(url))
                return BaseKaznaUrl;

            var normalized = url.Trim().Replace("&amp;", "&");
            if (normalized.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || normalized.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                return normalized;
            if (normalized.StartsWith("?", StringComparison.Ordinal))
                return BaseMainUrl + normalized;
            if (normalized.StartsWith("/", StringComparison.Ordinal))
                return "http://www.neverlands.ru" + normalized;
            if (normalized.StartsWith("main.php", StringComparison.OrdinalIgnoreCase))
                return "http://www.neverlands.ru/" + normalized;

            return normalized;
        }

        internal static string PlainText(string html)
        {
            if (string.IsNullOrEmpty(html))
                return string.Empty;

            var text = html.Replace("&nbsp;", " ").Replace("&amp;", "&");
            text = TagRegex.Replace(text, " ");
            text = HttpUtility.HtmlDecode(text);
            return Regex.Replace(text ?? string.Empty, @"\s+", " ").Trim();
        }

        private static void ParseCategories(string html, KaznaSnapshot snapshot)
        {
            var known = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);
            foreach (Match match in CategoryLinkRegex.Matches(html ?? string.Empty))
            {
                var href = NormalizeMainUrl(match.Groups["href"].Value);
                if (href.IndexOf("useaction=clan-action", StringComparison.OrdinalIgnoreCase) == -1 || href.IndexOf("addid=3", StringComparison.OrdinalIgnoreCase) == -1)
                    continue;

                var wca = ExtractWca(href);
                if (string.IsNullOrEmpty(wca) || known.ContainsKey(wca))
                    continue;

                var inner = match.Groups["inner"].Value;
                var image = ImageRegex.Match(inner);
                if (!image.Success)
                    continue;

                var category = new KaznaCategory();
                category.Wca = wca;
                category.Href = href;
                category.IconUrl = NormalizeAssetUrl(image.Groups["src"].Value);
                category.Title = FirstNonEmpty(ExtractTitle(inner), PlainText(inner), "wca=" + wca);
                known[wca] = true;
                snapshot.Categories.Add(category);
            }
        }

        private static void ParseItems(string html, KaznaSnapshot snapshot)
        {
            var fallbackIndex = 1;
            foreach (Match rowMatch in RowRegex.Matches(html ?? string.Empty))
            {
                var rowHtml = rowMatch.Value;
                var cells = ExtractCells(rowHtml);
                if (cells.Count < 5)
                    continue;

                var durabilityTextRaw = PlainText(cells[2]);
                if (durabilityTextRaw.IndexOf("Долговечность", StringComparison.CurrentCultureIgnoreCase) == -1 || !DurabilityRegex.IsMatch(durabilityTextRaw))
                    continue;

                var parsedName = ParseName(cells[0]);
                if (string.IsNullOrEmpty(parsedName.DisplayName))
                    continue;

                var durability = ParseDurability(durabilityTextRaw);
                var actionLinks = ParseActionLinks(cells[4]);
                var item = new KaznaItem();
                item.Uid = FirstNonEmpty(ExtractUid(actionLinks.TakeUrl), ExtractUid(actionLinks.DonateUrl), ExtractUid(rowHtml));
                item.RowIndex = parsedName.Index > 0 ? parsedName.Index : fallbackIndex;
                item.DisplayName = parsedName.DisplayName;
                item.BaseName = parsedName.BaseName;
                item.Owner = PlainText(cells[1]);
                item.DurabilityText = durability.Text;
                item.CurrentDurability = durability.Current;
                item.MaxDurability = durability.Max;
                item.Status = PlainText(cells[3]);
                item.Free = item.Status.IndexOf("свобод", StringComparison.CurrentCultureIgnoreCase) != -1;
                item.ArtifactCoefficient = parsedName.ArtifactCoefficient;
                item.TakeUrl = actionLinks.TakeUrl;
                item.DonateUrl = actionLinks.DonateUrl;
                item.SourceUrl = snapshot.SourceUrl;
                item.CategoryWca = snapshot.CurrentWca;
                item.CategoryTitle = snapshot.CurrentCategoryTitle;
                item.RowHtml = rowHtml;
                snapshot.Items.Add(item);
                fallbackIndex++;
            }
        }

        private static List<string> ExtractCells(string rowHtml)
        {
            var cells = new List<string>();
            foreach (Match match in CellRegex.Matches(rowHtml ?? string.Empty))
                cells.Add(match.Groups["cell"].Value);

            return cells;
        }

        private static ParsedName ParseName(string nameCell)
        {
            var text = PlainText(nameCell);
            var artifact = string.Empty;
            var artifactMatch = ArtifactRegex.Match(text);
            if (artifactMatch.Success)
            {
                artifact = artifactMatch.Groups[1].Value;
                text = PlainText(text.Replace(artifact, string.Empty));
            }

            var index = -1;
            var indexMatch = IndexRegex.Match(text);
            if (indexMatch.Success)
            {
                int.TryParse(indexMatch.Groups[1].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out index);
                text = PlainText(indexMatch.Groups[2].Value);
            }

            var baseName = text;
            var bold = Regex.Match(nameCell ?? string.Empty, @"<b[^>]*>(?<name>.*?)</b>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
            if (bold.Success)
            {
                var boldText = bold.Groups["name"].Value;
                if (!string.IsNullOrEmpty(artifact))
                    boldText = boldText.Replace(artifact, string.Empty);

                baseName = PlainText(boldText);
            }

            return new ParsedName(index, text, string.IsNullOrEmpty(baseName) ? text : baseName, artifact);
        }

        private static Durability ParseDurability(string text)
        {
            var match = DurabilityRegex.Match(text ?? string.Empty);
            if (!match.Success || match.Groups.Count < 3)
                return new Durability(string.Empty, -1, -1);

            int current;
            int max;
            if (!int.TryParse(match.Groups[1].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out current))
                current = -1;
            if (!int.TryParse(match.Groups[2].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out max))
                max = -1;

            return new Durability(current + "/" + max, current, max);
        }

        private static ActionLinks ParseActionLinks(string actionCell)
        {
            var links = new ActionLinks();
            foreach (Match input in Regex.Matches(actionCell ?? string.Empty, @"<input\b[^>]*>", RegexOptions.IgnoreCase | RegexOptions.Singleline))
            {
                var inputHtml = input.Value;
                var value = PlainText(ExtractAttribute(inputHtml, "value"));
                var onclick = ExtractAttribute(inputHtml, "onclick");
                var location = ExtractLocation(onclick);
                if (string.IsNullOrEmpty(location))
                    continue;

                var url = NormalizeMainUrl(location);
                if (value.IndexOf("Взять из казны", StringComparison.CurrentCultureIgnoreCase) != -1)
                    links.TakeUrl = url;
                else if (value.IndexOf("Пожертвовать", StringComparison.CurrentCultureIgnoreCase) != -1)
                    links.DonateUrl = url;
            }

            return links;
        }

        private static string ExtractAttribute(string html, string name)
        {
            var match = Regex.Match(html ?? string.Empty, "\\s" + Regex.Escape(name) + "\\s*=\\s*(?:'(?<value>.*?)'|\\\"(?<value>.*?)\\\"|(?<value>[^\\s>]+))", RegexOptions.IgnoreCase | RegexOptions.Singleline);
            return match.Success ? match.Groups["value"].Value : string.Empty;
        }

        private static string ExtractLocation(string onclick)
        {
            var match = LocationRegex.Match(onclick ?? string.Empty);
            return match.Success ? match.Groups["url"].Value : string.Empty;
        }

        private static string ExtractUid(string value)
        {
            var match = UidRegex.Match(value ?? string.Empty);
            return match.Success ? HttpUtility.UrlDecode(match.Groups[1].Value) : string.Empty;
        }

        private static string ExtractWca(string value)
        {
            var match = WcaRegex.Match(value ?? string.Empty);
            return match.Success ? HttpUtility.UrlDecode(match.Groups[1].Value) : string.Empty;
        }

        private static string FindCategoryTitle(List<KaznaCategory> categories, string wca)
        {
            if (string.IsNullOrEmpty(wca))
                return string.Empty;

            for (var i = 0; i < categories.Count; i++)
            {
                if (string.Equals(categories[i].Wca, wca, StringComparison.OrdinalIgnoreCase))
                    return categories[i].Title;
            }

            return string.Empty;
        }

        private static string NormalizeAssetUrl(string url)
        {
            if (string.IsNullOrEmpty(url))
                return string.Empty;

            var normalized = url.Trim().Replace("&amp;", "&");
            if (normalized.StartsWith("//", StringComparison.Ordinal))
                return "http:" + normalized;
            if (normalized.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || normalized.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                return normalized;
            if (normalized.StartsWith("/", StringComparison.Ordinal))
                return "http://image.neverlands.ru" + normalized;

            return normalized;
        }

        private static string ExtractTitle(string html)
        {
            var match = AttrTitleRegex.Match(html ?? string.Empty);
            return match.Success ? PlainText(match.Groups["value"].Value) : string.Empty;
        }

        private static string FirstNonEmpty(string first, string second, string third)
        {
            if (!string.IsNullOrEmpty(first))
                return first;
            if (!string.IsNullOrEmpty(second))
                return second;
            return third ?? string.Empty;
        }

        private sealed class ParsedName
        {
            internal readonly int Index;
            internal readonly string DisplayName;
            internal readonly string BaseName;
            internal readonly string ArtifactCoefficient;

            internal ParsedName(int index, string displayName, string baseName, string artifactCoefficient)
            {
                Index = index;
                DisplayName = displayName;
                BaseName = baseName;
                ArtifactCoefficient = artifactCoefficient;
            }
        }

        private sealed class Durability
        {
            internal readonly string Text;
            internal readonly int Current;
            internal readonly int Max;

            internal Durability(string text, int current, int max)
            {
                Text = text;
                Current = current;
                Max = max;
            }
        }

        private sealed class ActionLinks
        {
            internal string TakeUrl = string.Empty;
            internal string DonateUrl = string.Empty;
        }
    }
}
