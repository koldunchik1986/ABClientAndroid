using System;
using System.Collections.Generic;
using System.Globalization;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading;
using ABClient.ABProxy;
using ABClient.MyHelpers;

namespace ABClient
{
    public static class NeverApi
    {
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        private static readonly Regex HpmpRegex = new Regex(@"(?:var\s+)?hpmp\s*=\s*\[\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*(?<energy>-?\d+)", RegexOptions.IgnoreCase | RegexOptions.Compiled);
        private static readonly Dictionary<string, string> NameToId = new Dictionary<string, string>();
        private static readonly ReaderWriterLock NameToIdLock = new ReaderWriterLock();

        private static string GetUserId(string nick)
        {
            if (string.IsNullOrEmpty(nick))
                return null;

            var normalizedNick = nick.Trim();

            // Кэш: in-memory lookup (Android: nickIdCache + nameToId)
            string id;
            try
            {
                NameToIdLock.AcquireReaderLock(1000);
                try
                {
                    if (NameToId.TryGetValue(normalizedNick, out id) && !string.IsNullOrEmpty(id))
                        return id;
                }
                finally
                {
                    NameToIdLock.ReleaseReaderLock();
                }
            }
            catch (ApplicationException)
            {
            }

            // getid.cgi?{encnick} → ответ: "id|nick"
            // Android: resolvePlayerIdByNick → getid.cgi → parts[0].trim()
            var encnick = HelperConverters.NickEncode(normalizedNick);
            var url = $"http://www.neverlands.ru/modules/api/getid.cgi?{encnick}";
            var data = GetInfo(url);
            if (string.IsNullOrEmpty(data))
            {
                AppLog.w("NeverApi", "GetUserId: EMPTY_RESPONSE nick=" + normalizedNick);
                return null;
            }

            var spar = data.Split('|');
            if (spar.Length < 1)
            {
                AppLog.w("NeverApi", "GetUserId: PARSE_FAIL nick=" + normalizedNick + " raw=" + data);
                return null;
            }

            id = spar[0] == null ? string.Empty : spar[0].Trim();

            // Валидация: id должен быть непустой строкой (число)
            // Android: isEmpty(id) → return null
            if (string.IsNullOrEmpty(id))
            {
                AppLog.w("NeverApi", "GetUserId: EMPTY_ID nick=" + normalizedNick + " raw=" + data);
                return null;
            }

            AppLog.d("NeverApi", "GetUserId: nick=" + normalizedNick + " id=" + id);

            // Получаем нормализованное имя из ответа (spar[1])
            var resolvedNick = spar.Length > 1 && !string.IsNullOrEmpty(spar[1])
                ? spar[1].Trim()
                : normalizedNick;

            try
            {
                NameToIdLock.AcquireWriterLock(1000);
                try
                {
                    // Кэшируем по ключу из ответа сервера (resolvedNick)
                    // Android: cacheNickIdRecord → upsertNickIdMappingLocked
                    if (NameToId.ContainsKey(resolvedNick))
                        NameToId[resolvedNick] = id;
                    else
                        NameToId.Add(resolvedNick, id);

                    // Также кэшируем по оригинальному нику (если отличается)
                    if (!resolvedNick.Equals(normalizedNick, StringComparison.OrdinalIgnoreCase))
                    {
                        if (NameToId.ContainsKey(normalizedNick))
                            NameToId[normalizedNick] = id;
                        else
                            NameToId.Add(normalizedNick, id);
                    }
                }
                finally
                {
                    NameToIdLock.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }

            return id;
        }

        public static UserInfo GetAll(string nick)
        {
            var userInfo = new UserInfo();

            var id = GetUserId(nick);
            if (string.IsNullOrEmpty(id))
                return null;

            var data = GetInfo($"http://www.neverlands.ru/modules/api/info.cgi?playerid={id}&info=1&hmu=1&effects=1&slots=1");
            /*
                1|tnsx4hoq.gif:Шлем Гладиатора:|0|0|45|0|60|0|150@...
                2|
                3|Черный|16|0|n|none|||0|1|0|0|0|0||18834655
                4|0|785|0|112|77
            */
            if (string.IsNullOrEmpty(data))
            {
                AppLog.w("NeverApi", "GetAll: EMPTY_RESPONSE id=" + id + " nick=" + nick);
                return null;
            }

            var sp = data.Split('\n');
            if (sp.Length < 4)
                return null;

            // Строка 0: "1|slots_data" — обрезаем префикс "1|"
            if (sp[0].Length < 2)
                return null;

            userInfo.SlotsCodes = new string[0];
            userInfo.SlotsNames = new string[0];

            var sp1 = sp[0].Substring(2).Split('@');
            if (sp1.Length < 16)
                return null;

            userInfo.SlotsCodes = new string[16];
            userInfo.SlotsNames = new string[16];

            for (var i = 0; i < 16; i++)
            {
                var sps = sp1[i].Split(':');
                if (sps.Length < 2)
                    return null;

                userInfo.SlotsCodes[i] = sps[0];
                userInfo.SlotsNames[i] = sps[1];
            }

            userInfo.EffectsCodes = new string[0];
            userInfo.EffectsNames = new string[0];
            userInfo.EffectsSizes = new string[0];
            userInfo.EffectsLefts = new string[0];

            if (sp[1].Length > 2)
            {
                var sp2 = sp[1].Substring(2).Split('@');
                userInfo.EffectsCodes = new string[sp2.Length];
                userInfo.EffectsNames = new string[sp2.Length];
                userInfo.EffectsSizes = new string[sp2.Length];
                userInfo.EffectsLefts = new string[sp2.Length];
                for (var i = 0; i < sp2.Length; i++)
                {
                    var sps = sp2[i].Split('.');
                    if (sps.Length < 4)
                        return null;

                    userInfo.EffectsCodes[i] = sps[0];
                    userInfo.EffectsNames[i] = sps[1];
                    userInfo.EffectsSizes[i] = sps[2];
                    userInfo.EffectsLefts[i] = sps[3];
                }
            }

            var sp3 = sp[2].Substring(2).Split('|');
            if (sp3.Length < 15) // sp3[14] = FightLog — нужно минимум 15 элементов
                return null;

            userInfo.Nick = sp3[0].Trim();
            userInfo.Level = sp3[1];
            userInfo.Align = sp3[2];
            userInfo.ClanCode = sp3[3];
            userInfo.ClanSign = sp3[4];
            userInfo.ClanName = sp3[5];
            userInfo.ClanStatus = sp3[6];
            userInfo.Sex = sp3[7];
            if (!sp3[8].Equals("0"))
                userInfo.Disabled = true;

            if (!sp3[9].Equals("0"))
                userInfo.Jailed = true;

            userInfo.ChatMuted = sp3[10];
            userInfo.ForumMuted = sp3[11];
            if (!sp3[12].Equals("0"))
                userInfo.Online = true;

            userInfo.Location = sp3.Length > 13 ? sp3[13] : string.Empty;
            userInfo.FightLog = sp3.Length > 14 ? sp3[14] : "0";

            AppLog.d("NeverApi", "GetAll: nick=" + userInfo.Nick + " level=" + userInfo.Level +
                " online=" + userInfo.Online + " location=" + userInfo.Location +
                " fightLog=" + userInfo.FightLog + " clan=" + userInfo.ClanName);

            var sp4 = sp[3].Substring(2).Split('|');
            if (sp4.Length < 5)
                return null;

            int.TryParse(sp4[0], out userInfo.HpCur);
            int.TryParse(sp4[1], out userInfo.HpMax);
            int.TryParse(sp4[2], out userInfo.MaCur);
            int.TryParse(sp4[3], out userInfo.MaMax);
            int.TryParse(sp4[4], out userInfo.Tied);
            userInfo.Tied = 100 - userInfo.Tied;

            return userInfo;
        }

        public static string GetPInfo(string nick)
        {
            var url = HelperConverters.AddressEncode(string.Concat("http://www.neverlands.ru/pinfo.cgi?", nick));
            return GetInfo(url);
        }

        public static bool TryGetTiedFromPInfo(string nick, out int tied)
        {
            tied = 0;
            if (string.IsNullOrEmpty(nick))
            {
                AppLog.w("NeverApi", "TryGetTiedFromPInfo: EMPTY_NICK");
                return false;
            }

            var html = GetPInfo(nick);
            if (string.IsNullOrEmpty(html))
            {
                AppLog.w("NeverApi", "TryGetTiedFromPInfo: EMPTY_RESPONSE nick=" + nick);
                return false;
            }

            if (!TryParseTiedFromPInfoHtml(html, out tied))
            {
                AppLog.w("NeverApi", "TryGetTiedFromPInfo: HPMP_NOT_FOUND nick=" + nick);
                return false;
            }

            AppLog.i("NeverApi", "TryGetTiedFromPInfo: nick=" + nick + " tied=" + tied + "%");
            return true;
        }

        public static bool TryParseTiedFromPInfoHtml(string html, out int tied)
        {
            tied = 0;
            if (string.IsNullOrEmpty(html))
            {
                return false;
            }

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

        public static string GetFlog(string flog)
        {
            var url = HelperConverters.AddressEncode(string.Concat("http://neverlands.ru/logs.fcg?fid=", flog));
            return GetInfo(url);
        }

        private static string GetInfo(string url)
        {
            string html = null;
            using (var wc = new CookieAwareWebClient { Proxy = AppVars.LocalProxy })
            {
                try
                {
                    var requestUri = new Uri(url);
                    wc.Headers[HttpRequestHeader.UserAgent] = BrowserUserAgent;
                    wc.Headers[HttpRequestHeader.Accept] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
                    wc.Headers[HttpRequestHeader.AcceptLanguage] = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7";
                    wc.Headers[HttpRequestHeader.CacheControl] = "no-cache";
                    wc.Headers[HttpRequestHeader.Referer] = "http://www.neverlands.ru/main.php";
                    var cookieHost = requestUri.Host.Equals("neverlands.ru", StringComparison.OrdinalIgnoreCase)
                        ? "www.neverlands.ru"
                        : requestUri.Host;
                    var cookies = CookiesManager.Obtain(cookieHost);
                    if (!string.IsNullOrEmpty(cookies))
                    {
                        wc.SetCookies(requestUri, cookies);
                    }

                    IdleManager.AddActivity();
                    var buffer = wc.DownloadData(requestUri);
                    if (buffer != null)
                    {
                        if (buffer.Length == 0)
                        {
                            AppLog.w("NeverApi", "GetInfo: EMPTY_BODY url=" + url + " cookies=" + !string.IsNullOrEmpty(cookies));
                        }

                        html = AppVars.Codepage.GetString(buffer);
                        if (html.IndexOf("Cookie...", StringComparison.CurrentCultureIgnoreCase) != -1)
                        {
                            buffer = wc.DownloadData(requestUri);
                            if (buffer != null)
                            {
                                html = AppVars.Codepage.GetString(buffer);
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    AppLog.e("NeverApi", "GetInfo FAILED: url=" + url + " error=" + ex.Message, ex);
                }
                finally
                {
                    IdleManager.RemoveActivity();
                }
            }

            return html;
        }

        private static int NormalizeTied(int tied)
        {
            if (tied < 0)
            {
                return 0;
            }

            return tied > 100 ? 100 : tied;
        }
    }
}
