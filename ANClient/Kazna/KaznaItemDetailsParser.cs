using System;
using System.Text.RegularExpressions;
using System.Web;

namespace ANClient.Kazna
{
    internal static class KaznaItemDetailsParser
    {
        private static readonly Regex UidLinkRegex = new Regex(@"get_id\s*=\s*57[^'""<>\s]*[?&]uid=([^&'""<>\s]+)", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex InputTagRegex = new Regex(@"<input\b[^>]*>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex SrcAttrRegex = new Regex(@"\bsrc\s*=\s*(?:""([^""]*)""|'([^']*)'|([^\s>]+))", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex TagRegex = new Regex(@"<[^>]+>", RegexOptions.IgnoreCase | RegexOptions.Singleline);
        private static readonly Regex BrRegex = new Regex(@"<br\s*/?>", RegexOptions.IgnoreCase | RegexOptions.Singleline);

        internal static KaznaItemDetails ParseFromInventoryEntry(string htmlEntry)
        {
            if (string.IsNullOrEmpty(htmlEntry))
                return null;

            var normalizedHtml = htmlEntry.Replace("&amp;", "&");
            var uid = ExtractUid(normalizedHtml);
            if (string.IsNullOrEmpty(uid))
                return null;

            var name = ExtractName(normalizedHtml);
            var imageUrl = ExtractImageUrl(normalizedHtml, uid);
            var propertiesText = ExtractPropertiesText(normalizedHtml, name);
            var details = new KaznaItemDetails(uid, name, imageUrl, propertiesText, DateTime.Now.Ticks);
            return details.HasKnownDetails ? details : null;
        }

        private static string ExtractUid(string htmlEntry)
        {
            var match = UidLinkRegex.Match(htmlEntry ?? string.Empty);
            return match.Success ? CleanText(HttpUtility.UrlDecode(match.Groups[1].Value)) : string.Empty;
        }

        private static string ExtractName(string htmlEntry)
        {
            var name = SubString(htmlEntry, "<font class=nickname><b> ", "</b>");
            if (string.IsNullOrEmpty(name))
                name = SubString(htmlEntry, "<font class=nickname><b>", "</b>");

            return CleanText(StripTags(name));
        }

        private static string ExtractImageUrl(string htmlEntry, string uid)
        {
            foreach (Match input in InputTagRegex.Matches(htmlEntry ?? string.Empty))
            {
                var tag = input.Value;
                var lower = tag.ToLowerInvariant();
                if (lower.IndexOf("get_id=57", StringComparison.Ordinal) == -1 || lower.IndexOf("uid=" + uid.ToLowerInvariant(), StringComparison.Ordinal) == -1)
                    continue;
                if (lower.IndexOf("type=image", StringComparison.Ordinal) == -1 && lower.IndexOf("type=\"image\"", StringComparison.Ordinal) == -1 && lower.IndexOf("type='image'", StringComparison.Ordinal) == -1)
                    continue;

                var src = ExtractSrc(tag);
                if (!string.IsNullOrEmpty(src))
                    return NormalizeImageUrl(src);
            }

            return string.Empty;
        }

        private static string ExtractSrc(string tag)
        {
            var match = SrcAttrRegex.Match(tag ?? string.Empty);
            if (!match.Success)
                return string.Empty;

            for (var i = 1; i <= 3; i++)
            {
                var value = match.Groups[i].Value;
                if (!string.IsNullOrEmpty(value))
                    return value.Trim();
            }

            return string.Empty;
        }

        private static string ExtractPropertiesText(string htmlEntry, string itemName)
        {
            var start = IndexOfIgnoreCase(htmlEntry, "<font class=weaponch>", 0);
            if (start == -1)
                return string.Empty;

            var end = IndexOfIgnoreCase(htmlEntry, "</td><td", start);
            if (end == -1)
                end = htmlEntry.Length;

            return NormalizePropertiesText(htmlEntry.Substring(start, end - start), itemName);
        }

        private static string NormalizePropertiesText(string html, string itemName)
        {
            var withBreaks = BrRegex.Replace(html ?? string.Empty, "\n");
            var text = StripTags(withBreaks).Replace('\u00A0', ' ');
            var lines = text.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            var cleanName = CleanText(itemName);
            var sb = new System.Text.StringBuilder();
            for (var i = 0; i < lines.Length; i++)
            {
                var line = CleanText(lines[i]);
                if (string.IsNullOrEmpty(line))
                    continue;
                if (!string.IsNullOrEmpty(cleanName) && line.Equals(cleanName, StringComparison.CurrentCultureIgnoreCase))
                    continue;
                if (line.Equals("свойства", StringComparison.CurrentCultureIgnoreCase))
                    continue;
                if (line.Equals("требования", StringComparison.CurrentCultureIgnoreCase))
                    break;

                if (sb.Length > 0)
                    sb.Append('\n');
                sb.Append(line);
            }

            return sb.ToString();
        }

        private static string NormalizeImageUrl(string src)
        {
            var normalized = CleanText(src).Replace("&amp;", "&");
            if (string.IsNullOrEmpty(normalized))
                return string.Empty;
            if (normalized.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || normalized.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                return normalized;
            if (normalized.StartsWith("//", StringComparison.Ordinal))
                return "http:" + normalized;
            if (normalized.StartsWith("/", StringComparison.Ordinal))
                return "http://image.neverlands.ru" + normalized;

            return "http://image.neverlands.ru/" + normalized;
        }

        private static string StripTags(string value)
        {
            return HttpUtility.HtmlDecode(TagRegex.Replace(value ?? string.Empty, " ")) ?? string.Empty;
        }

        private static string CleanText(string value)
        {
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            return Regex.Replace(value.Replace('\u00A0', ' '), @"\s+", " ").Trim();
        }

        private static string SubString(string source, string start, string end)
        {
            if (string.IsNullOrEmpty(source))
                return string.Empty;

            var pos = IndexOfIgnoreCase(source, start, 0);
            if (pos == -1)
                return string.Empty;

            pos += start.Length;
            var posEnd = IndexOfIgnoreCase(source, end, pos);
            return posEnd == -1 ? string.Empty : source.Substring(pos, posEnd - pos);
        }

        private static int IndexOfIgnoreCase(string source, string value, int startIndex)
        {
            if (source == null || value == null)
                return -1;

            return source.IndexOf(value, Math.Max(0, startIndex), StringComparison.CurrentCultureIgnoreCase);
        }
    }
}
