namespace ANClient.Info
{
    using System;
    using System.Collections.Generic;
    using System.Globalization;
    using System.IO;
    using System.Net;
    using System.Web;
    using ANClient.ANProxy;

    internal sealed class ProfessionRatingRepository
    {
        private const string Tag = "ProfessionRatingRepository";
        private const string UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";
        private const string RateUrlFormat = "http://service.neverlands.ru/rate/weekly_{0}.txt";
        private const string SignBaseUrl = "http://image.neverlands.ru/signs/";
        private const string InfoIconUrl = "http://image.neverlands.ru/chat/info.gif";

        private static readonly object SyncRoot = new object();
        private static readonly List<Category> Categories = BuildCategories();
        private static readonly Dictionary<int, RatingTable> Cache = new Dictionary<int, RatingTable>();

        private ProfessionRatingRepository()
        {
        }

        internal static IList<Category> GetCategories()
        {
            return Categories.AsReadOnly();
        }

        internal static Category FindCategory(int id)
        {
            foreach (Category category in Categories)
            {
                if (category.Id == id)
                {
                    return category;
                }
            }

            return null;
        }

        internal static int GetFirstCategoryId()
        {
            return Categories.Count == 0 ? 0 : Categories[0].Id;
        }

        internal static RatingTable GetCachedRating(int id)
        {
            lock (SyncRoot)
            {
                RatingTable table;
                return Cache.TryGetValue(id, out table) ? table : null;
            }
        }

        internal static RatingTable LoadRating(int id, bool forceRefresh)
        {
            Category category = FindCategory(id);
            if (category == null)
            {
                throw new InvalidOperationException("Unknown profession rating id: " + id.ToString(CultureInfo.InvariantCulture));
            }

            lock (SyncRoot)
            {
                RatingTable cached;
                if (!forceRefresh && Cache.TryGetValue(id, out cached))
                {
                    return cached;
                }
            }

            string url = BuildRatingUrl(id);
            string rawText = FetchRatingText(url);
            RatingTable table = ParseRating(category, url, rawText);
            lock (SyncRoot)
            {
                Cache[id] = table;
            }

            return table;
        }

        internal static string BuildRatingUrl(int id)
        {
            return string.Format(CultureInfo.InvariantCulture, RateUrlFormat, id);
        }

        internal static string BuildPinfoUrl(string nick)
        {
            string safeNick = nick == null ? string.Empty : nick.Trim();
            return "http://www.neverlands.ru/pinfo.cgi?" + HttpUtility.UrlEncode(safeNick, ANClient.AppVars.Codepage).Replace("+", "%20");
        }

        internal static string BuildTotemIconUrl(int clanTotem)
        {
            switch (clanTotem)
            {
                case 1:
                    return SignBaseUrl + "darks.gif";
                case 2:
                    return SignBaseUrl + "lights.gif";
                case 3:
                    return SignBaseUrl + "sumers.gif";
                case 4:
                    return SignBaseUrl + "chaoss.gif";
                default:
                    return string.Empty;
            }
        }

        internal static string BuildClanIconUrl(string clanIco)
        {
            string value = clanIco == null ? string.Empty : clanIco.Trim();
            return value.Length == 0 ? string.Empty : SignBaseUrl + value;
        }

        internal static string GetInfoIconUrl()
        {
            return InfoIconUrl;
        }

        internal static string NormalizeNick(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return string.Empty;
            }

            return System.Text.RegularExpressions.Regex.Replace(value.Replace('\u00A0', ' '), "\\s+", " ").Trim().ToLowerInvariant();
        }

        private static List<Category> BuildCategories()
        {
            var categories = new List<Category>();
            categories.Add(new Category(1, "Каллиграфия"));
            categories.Add(new Category(2, "Ювелирное дело"));
            categories.Add(new Category(3, "Ремесленник"));
            categories.Add(new Category(4, "Доктор"));
            categories.Add(new Category(5, "Алхимия"));
            categories.Add(new Category(6, "Развитие горного дела"));
            categories.Add(new Category(7, "Рыбалка"));
            categories.Add(new Category(8, "Охота"));
            categories.Add(new Category(9, "Кулинария"));
            categories.Add(new Category(10, "Лесозаготовка"));
            categories.Add(new Category(11, "Плотник"));
            categories.Add(new Category(12, "Сталевар"));
            categories.Add(new Category(13, "Травник"));
            categories.Add(new Category(14, "Торговец"));
            categories.Add(new Category(51, "Рукопашный бой"));
            categories.Add(new Category(52, "Воины"));
            categories.Add(new Category(53, "Гладиаторы"));
            categories.Add(new Category(54, "Арена"));
            return categories;
        }

        private static string FetchRatingText(string url)
        {
            var request = (HttpWebRequest)WebRequest.Create(GameServerSelector.RouteUrlToCurrentServer(url));
            request.Method = "GET";
            request.UserAgent = UserAgent;
            request.Accept = "text/plain,*/*";
            request.Timeout = 15000;
            if (!DirectGameRequestGuard.Prepare(request, Tag + ".FetchRatingText"))
            {
                throw new WebException("DIRECT_GAME_REQUEST_BLOCKED");
            }

            ANClient.AppLog.i(Tag, "WEEKLY_RATING_FETCH_START: url=" + url);
            using (var response = (HttpWebResponse)request.GetResponse())
            using (Stream stream = response.GetResponseStream())
            using (var reader = new StreamReader(stream, ANClient.AppVars.Codepage))
            {
                string text = reader.ReadToEnd();
                if (text.Length > 0 && text[0] == '\uFEFF')
                {
                    text = text.Substring(1);
                }

                ANClient.AppLog.i(Tag, "WEEKLY_RATING_FETCH_OK: url=" + url + ", status=" + (int)response.StatusCode + ", chars=" + text.Length.ToString(CultureInfo.InvariantCulture));
                return text;
            }
        }

        private static RatingTable ParseRating(Category category, string sourceUrl, string rawText)
        {
            var entries = new List<RatingEntry>();
            using (var reader = new StringReader(rawText ?? string.Empty))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    string trimmed = line.Trim();
                    if (trimmed.Length == 0 || trimmed.StartsWith("#", StringComparison.Ordinal))
                    {
                        continue;
                    }

                    string[] parts = trimmed.Split('|');
                    if (parts.Length < 5)
                    {
                        ANClient.AppLog.w(Tag, "WEEKLY_RATING_BAD_LINE: id=" + category.Id.ToString(CultureInfo.InvariantCulture) + ", line=" + trimmed);
                        continue;
                    }

                    string nick = parts[2].Trim();
                    if (nick.Length == 0)
                    {
                        continue;
                    }

                    entries.Add(new RatingEntry(entries.Count + 1, ParseInt(parts[0]), parts[1].Trim(), nick, ParseInt(parts[3]), ParseInt(parts[4])));
                }
            }

            ANClient.AppLog.i(Tag, "WEEKLY_RATING_PARSED: id=" + category.Id.ToString(CultureInfo.InvariantCulture) + ", title=" + category.Title + ", entries=" + entries.Count.ToString(CultureInfo.InvariantCulture));
            return new RatingTable(category, sourceUrl, entries, DateTime.Now);
        }

        private static int ParseInt(string value)
        {
            int result;
            return int.TryParse(value == null ? string.Empty : value.Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out result) ? result : 0;
        }

        internal sealed class Category
        {
            internal Category(int id, string title)
            {
                Id = id;
                Title = title;
            }

            internal int Id { get; private set; }

            internal string Title { get; private set; }
        }

        internal sealed class RatingTable
        {
            internal RatingTable(Category category, string sourceUrl, List<RatingEntry> entries, DateTime loadedAt)
            {
                Category = category;
                SourceUrl = sourceUrl;
                Entries = entries;
                LoadedAt = loadedAt;
            }

            internal Category Category { get; private set; }

            internal string SourceUrl { get; private set; }

            internal List<RatingEntry> Entries { get; private set; }

            internal DateTime LoadedAt { get; private set; }
        }

        internal sealed class RatingEntry
        {
            internal RatingEntry(int rank, int clanTotem, string clanIco, string nick, int level, int rate)
            {
                Rank = rank;
                ClanTotem = clanTotem;
                ClanIco = clanIco;
                Nick = nick;
                Level = level;
                Rate = rate;
            }

            internal int Rank { get; private set; }

            internal int ClanTotem { get; private set; }

            internal string ClanIco { get; private set; }

            internal string Nick { get; private set; }

            internal int Level { get; private set; }

            internal int Rate { get; private set; }
        }
    }
}
