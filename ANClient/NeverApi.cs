using System;
using System.Collections.Generic;
using System.Globalization;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading;
using ANClient.ANProxy;
using ANClient.MyHelpers;

namespace ANClient
{
    public static class NeverApi
    {
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        private const string ClansInfoUrl = "http://service.neverlands.ru/info/clans.txt";
        private static readonly Regex HpmpRegex = new Regex(@"(?:var\s+)?hpmp\s*=\s*\[\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*,\s*(?<energy>-?\d+)", RegexOptions.IgnoreCase | RegexOptions.Compiled);
        private static readonly Dictionary<string, string> NameToId = new Dictionary<string, string>();
        private static readonly ReaderWriterLock NameToIdLock = new ReaderWriterLock();

        internal sealed class ClanRoster
        {
            internal readonly UserInfo MainUserInfo;
            internal readonly string ClanId;
            internal readonly string ClanName;
            internal readonly string ClanSign;
            internal readonly ClanRosterMember[] Members;
            internal readonly string[] MemberNicks;

            internal ClanRoster(UserInfo mainUserInfo, string clanId, string clanName, string clanSign, ClanRosterMember[] members)
            {
                MainUserInfo = mainUserInfo;
                ClanId = clanId ?? string.Empty;
                ClanName = clanName ?? string.Empty;
                ClanSign = NormalizeClanIcon(clanSign, clanId);
                Members = members ?? new ClanRosterMember[0];
                MemberNicks = BuildMemberNicks(Members);
            }
        }

        internal sealed class ClanRosterMember
        {
            internal readonly string PlayerId;
            internal readonly string Nick;
            internal readonly int Level;
            internal readonly string Status;

            internal ClanRosterMember(string playerId, string nick, int level, string status)
            {
                PlayerId = playerId ?? string.Empty;
                Nick = nick ?? string.Empty;
                Level = level;
                Status = status ?? string.Empty;
            }
        }

        public static string GetPlayerId(string nick)
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
                AppLog.w("NeverApi", "GetPlayerId: EMPTY_RESPONSE nick=" + normalizedNick);
                return null;
            }

            var spar = data.Split('|');
            if (spar.Length < 1)
            {
                AppLog.w("NeverApi", "GetPlayerId: PARSE_FAIL nick=" + normalizedNick + " raw=" + data);
                return null;
            }

            id = spar[0] == null ? string.Empty : spar[0].Trim();

            // Валидация: id должен быть непустой строкой (число)
            // Android: isEmpty(id) → return null
            if (string.IsNullOrEmpty(id))
            {
                AppLog.w("NeverApi", "GetPlayerId: EMPTY_ID nick=" + normalizedNick + " raw=" + data);
                return null;
            }

            AppLog.d("NeverApi", "GetPlayerId: nick=" + normalizedNick + " id=" + id);

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
            var id = GetPlayerId(nick);
            if (string.IsNullOrEmpty(id))
                return null;

            return GetAllByPlayerId(id, nick);
        }

        internal static ClanRoster GetClanRosterByNick(string nick)
        {
            var mainUserInfo = GetAll(nick);
            if (mainUserInfo == null)
            {
                AppLog.w("NeverApi", "GetClanRosterByNick: EMPTY_USER_INFO nick=" + (nick ?? string.Empty));
                return null;
            }

            return GetClanRosterByUserInfo(mainUserInfo);
        }

        internal static ClanRoster GetClanRosterByUserInfo(UserInfo mainUserInfo)
        {
            if (mainUserInfo == null)
                return null;

            var firstNick = mainUserInfo.Nick;
            var firstSign = mainUserInfo.ClanSign;
            var firstClan = mainUserInfo.ClanName;
            var clanId = NormalizeClanToken(firstSign);
            var members = new List<ClanRosterMember>();
            AddUniqueClanMember(members, new ClanRosterMember(mainUserInfo.PlayerId, firstNick, mainUserInfo.PlayerLevel, mainUserInfo.ClanStatus));

            if (string.IsNullOrEmpty(clanId) || string.IsNullOrEmpty(firstClan) || ContactRenderHelper.IsNeutralClanName(firstClan))
            {
                return new ClanRoster(mainUserInfo, clanId, firstClan, firstSign, members.ToArray());
            }

            var clansText = DownloadPublicText(ClansInfoUrl, "NeverApi.GetClanRoster.clansTxt");
            if (string.IsNullOrEmpty(clansText))
                return null;

            if (!ParseClansTxtRoster(clansText, clanId, firstClan, members))
            {
                AppLog.w("NeverApi", "GetClanRosterByUserInfo: CLAN_NOT_FOUND_IN_SERVICE clan=" + firstClan + " clanId=" + clanId);
                return null;
            }

            AppLog.i("NeverApi", "GetClanRosterByUserInfo: clan=" + firstClan + " clanId=" + clanId + " members=" + members.Count.ToString(CultureInfo.InvariantCulture));
            return new ClanRoster(mainUserInfo, clanId, firstClan, firstSign, members.ToArray());
        }

        private static string DownloadPublicText(string url, string source)
        {
            byte[] buffer;
            var activityAdded = false;
            using (var wc = new WebClient { Proxy = AppVars.LocalProxy })
            {
                try
                {
                    var requestUri = GameServerSelector.RouteUriToCurrentServer(new Uri(url));
                    if (!DirectGameRequestGuard.Prepare(wc, requestUri, source))
                    {
                        return null;
                    }

                    wc.Headers[HttpRequestHeader.UserAgent] = BrowserUserAgent;
                    wc.Headers[HttpRequestHeader.Accept] = "text/plain,*/*;q=0.8";
                    wc.Headers[HttpRequestHeader.AcceptLanguage] = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7";
                    wc.Headers[HttpRequestHeader.CacheControl] = "no-cache";
                    IdleManager.AddActivity();
                    activityAdded = true;
                    buffer = wc.DownloadData(requestUri);
                }
                catch (Exception ex)
                {
                    AppLog.e("NeverApi", source + " FAILED: url=" + url + " error=" + ex.Message, ex);
                    return null;
                }
                finally
                {
                    if (activityAdded)
                    {
                        IdleManager.RemoveActivity();
                    }
                }
            }

            return buffer == null || buffer.Length == 0 ? null : AppVars.Codepage.GetString(buffer);
        }

        private static bool ParseClansTxtRoster(string clansText, string clanId, string clanName, List<ClanRosterMember> members)
        {
            if (string.IsNullOrEmpty(clansText) || string.IsNullOrEmpty(clanId) || members == null)
                return false;

            var rows = clansText.Replace("\r", string.Empty).Split('\n');
            string matchedMembers = null;
            string matchedClanName = null;
            foreach (var row in rows)
            {
                if (string.IsNullOrEmpty(row))
                    continue;

                var clanParts = row.Split(new[] { '|' }, StringSplitOptions.None);
                if (clanParts.Length <= 5)
                    continue;

                var rowClanId = NormalizeClanToken(clanParts[0]);
                var rowClanName = clanParts[1] == null ? string.Empty : clanParts[1].Trim();
                if (!rowClanId.Equals(clanId, StringComparison.OrdinalIgnoreCase) &&
                    (string.IsNullOrEmpty(clanName) || !rowClanName.Equals(clanName, StringComparison.OrdinalIgnoreCase)))
                {
                    continue;
                }

                matchedClanName = rowClanName;
                matchedMembers = clanParts[5] ?? string.Empty;
                break;
            }

            if (matchedMembers == null)
                return false;

            var players = matchedMembers.Split(new[] { '#' }, StringSplitOptions.RemoveEmptyEntries);
            foreach (var player in players)
            {
                var member = ParseClanRosterMember(player);
                if (member == null)
                    continue;

                AddUniqueClanMember(members, member);
            }

            AppLog.i("NeverApi", "ParseClansTxtRoster: clan=" + matchedClanName + " clanId=" + clanId + " rawPlayers=" + players.Length.ToString(CultureInfo.InvariantCulture));
            return true;
        }

        private static ClanRosterMember ParseClanRosterMember(string player)
        {
            if (string.IsNullOrEmpty(player))
                return null;

            var playerParts = player.Split(',');
            if (playerParts.Length <= 1)
                return null;

            return new ClanRosterMember(
                playerParts[0].Trim(),
                playerParts[1].Trim(),
                playerParts.Length > 2 ? ParseIntSafe(playerParts[2], 0) : 0,
                playerParts.Length > 3 ? playerParts[3].Trim() : string.Empty);
        }

        private static string NormalizeClanToken(string clanToken)
        {
            if (string.IsNullOrEmpty(clanToken))
                return string.Empty;

            var token = clanToken.Trim();
            if (token.EndsWith(".gif", StringComparison.OrdinalIgnoreCase))
            {
                token = token.Substring(0, token.Length - ".gif".Length);
            }

            return token;
        }

        private static string NormalizeClanIcon(string clanSign, string clanId)
        {
            var sign = string.IsNullOrEmpty(clanSign) ? string.Empty : clanSign.Trim();
            if (sign.Length == 0)
            {
                sign = NormalizeClanToken(clanId);
            }

            if (sign.Length == 0 || sign.Equals("none", StringComparison.OrdinalIgnoreCase))
                return sign;

            return sign.EndsWith(".gif", StringComparison.OrdinalIgnoreCase) ? sign : sign + ".gif";
        }

        private static void AddUniqueClanMember(List<ClanRosterMember> members, ClanRosterMember member)
        {
            if (members == null || member == null || string.IsNullOrEmpty(member.Nick))
                return;

            var safeNick = member.Nick.Trim();
            if (safeNick.Length == 0)
                return;

            for (var i = 0; i < members.Count; i++)
            {
                var existingMember = members[i];
                if (existingMember != null && safeNick.Equals(existingMember.Nick, StringComparison.OrdinalIgnoreCase))
                {
                    members[i] = MergeClanRosterMember(existingMember, member);
                    return;
                }
            }

            members.Add(member);
        }

        private static ClanRosterMember MergeClanRosterMember(ClanRosterMember existingMember, ClanRosterMember newMember)
        {
            if (existingMember == null)
                return newMember;

            if (newMember == null)
                return existingMember;

            return new ClanRosterMember(
                string.IsNullOrEmpty(existingMember.PlayerId) ? newMember.PlayerId : existingMember.PlayerId,
                string.IsNullOrEmpty(existingMember.Nick) ? newMember.Nick : existingMember.Nick,
                existingMember.Level > 0 ? existingMember.Level : newMember.Level,
                string.IsNullOrEmpty(existingMember.Status) ? newMember.Status : existingMember.Status);
        }

        private static string[] BuildMemberNicks(ClanRosterMember[] members)
        {
            if (members == null || members.Length == 0)
                return new string[0];

            var result = new List<string>();
            foreach (var member in members)
            {
                if (member == null || string.IsNullOrEmpty(member.Nick))
                    continue;

                result.Add(member.Nick);
            }

            return result.ToArray();
        }

        public static UserInfo GetAllByPlayerId(string playerId)
        {
            return GetAllByPlayerId(playerId, null);
        }

        internal static Contact GetContactByNick(string nick, int classId, int toolId, string comments, bool tracing)
        {
            var userInfo = GetAll(nick);
            if (userInfo == null)
                return null;

            return BuildContactFromUserInfo(userInfo, classId, toolId, comments, tracing);
        }

        internal static Contact GetContactByPlayerId(string playerId, int classId, int toolId, string comments, bool tracing)
        {
            var userInfo = GetAllByPlayerId(playerId);
            if (userInfo == null)
                return null;

            return BuildContactFromUserInfo(userInfo, classId, toolId, comments, tracing);
        }

        private static UserInfo GetAllByPlayerId(string id, string fallbackNick)
        {
            var data = GetInfo($"http://www.neverlands.ru/modules/api/info.cgi?playerid={id}&info=1&hmu=1&effects=1&slots=1");
            if (string.IsNullOrEmpty(data))
            {
                AppLog.w("NeverApi", "GetAll: EMPTY_RESPONSE id=" + id + " nick=" + (fallbackNick ?? string.Empty));
                return null;
            }

            var userInfo = ParseUserInfo(id, fallbackNick, data);
            if (userInfo == null)
                return null;

            AppLog.d("NeverApi", "GetAll: nick=" + userInfo.Nick + " level=" + userInfo.Level +
                " online=" + userInfo.Online + " location=" + userInfo.Location +
                " fightLog=" + userInfo.FightLog + " clan=" + userInfo.ClanName);

            return userInfo;
        }

        private static Contact BuildContactFromUserInfo(UserInfo userInfo, int classId, int toolId, string comments, bool tracing)
        {
            var nick = string.IsNullOrEmpty(userInfo.Nick) ? string.Empty : userInfo.Nick;
            var contact = new Contact(nick, classId, toolId, comments ?? string.Empty, tracing, false);
            contact.ApplySnapshot(userInfo);
            return contact;
        }

        private static UserInfo ParseUserInfo(string playerId, string fallbackNick, string data)
        {
            if (string.IsNullOrEmpty(data))
                return null;

            var userInfo = new UserInfo { PlayerId = playerId ?? string.Empty };
            var rows = data.Replace("\r", string.Empty).Split('\n');
            if (rows.Length >= 3 && rows[2].StartsWith("3|", StringComparison.Ordinal))
            {
                ParseSlotsRow(userInfo, rows.Length > 0 ? rows[0] : string.Empty);
                ParseEffectsRow(userInfo, rows.Length > 1 ? rows[1] : string.Empty);
                if (!ParseInfoParts(userInfo, rows[2].Substring(2).Split(new[] { '|' }, StringSplitOptions.None), 0, fallbackNick))
                    return null;

                if (rows.Length > 3)
                {
                    ParseHmuRow(userInfo, rows[3]);
                }

                return userInfo;
            }

            var parts = data.Split(new[] { '|' }, StringSplitOptions.None);
            if (!ParseInfoParts(userInfo, parts, 1, fallbackNick))
                return null;

            return userInfo;
        }

        private static bool ParseInfoParts(UserInfo userInfo, string[] parts, int offset, string fallbackNick)
        {
            if (parts == null || parts.Length < offset + 15)
                return false;

            userInfo.Nick = SafePart(parts, offset).Trim();
            if (string.IsNullOrEmpty(userInfo.Nick))
            {
                userInfo.Nick = fallbackNick ?? string.Empty;
            }

            userInfo.Level = SafePart(parts, offset + 1);
            userInfo.PlayerLevel = ParseIntSafe(userInfo.Level, 0);
            userInfo.Align = SafePart(parts, offset + 2);
            userInfo.Inclination = ParseIntSafe(userInfo.Align, 0);
            userInfo.InclinationName = ContactRenderHelper.GetInclinationName(userInfo.Inclination);
            userInfo.ClanCode = SafePart(parts, offset + 3);
            userInfo.ClanNumber = userInfo.ClanCode;
            userInfo.ClanSign = SafePart(parts, offset + 4);
            userInfo.ClanIco = userInfo.ClanSign;
            userInfo.ClanName = SafePart(parts, offset + 5);
            userInfo.ClanStatus = SafePart(parts, offset + 6);
            userInfo.Sex = SafePart(parts, offset + 7);
            userInfo.Gender = ParseIntSafe(userInfo.Sex, 0);
            userInfo.BlockStatus = ParseIntSafe(SafePart(parts, offset + 8), 0);
            userInfo.JailStatus = ParseIntSafe(SafePart(parts, offset + 9), 0);
            userInfo.Disabled = userInfo.BlockStatus != 0;
            userInfo.Jailed = userInfo.JailStatus != 0;
            userInfo.ChatMuted = SafePart(parts, offset + 10);
            userInfo.ForumMuted = SafePart(parts, offset + 11);
            userInfo.MuteSeconds = ParseIntSafe(userInfo.ChatMuted, 0);
            userInfo.MuteForumSeconds = ParseIntSafe(userInfo.ForumMuted, 0);
            userInfo.OnlineStatus = ParseIntSafe(SafePart(parts, offset + 12), 0);
            userInfo.Online = userInfo.OnlineStatus > 0;
            userInfo.Location = SafePart(parts, offset + 13);
            userInfo.GeoLocation = userInfo.Location;
            userInfo.FightLog = SafePart(parts, offset + 14);
            userInfo.WarLogNumber = userInfo.FightLog;
            if (string.IsNullOrEmpty(userInfo.FightLog))
            {
                userInfo.FightLog = "0";
                userInfo.WarLogNumber = "0";
            }

            return true;
        }

        private static void ParseSlotsRow(UserInfo userInfo, string row)
        {
            userInfo.SlotsCodes = new string[0];
            userInfo.SlotsNames = new string[0];
            if (string.IsNullOrEmpty(row) || !row.StartsWith("1|", StringComparison.Ordinal) || row.Length <= 2)
                return;

            var items = row.Substring(2).Split('@');
            userInfo.SlotsCodes = new string[items.Length];
            userInfo.SlotsNames = new string[items.Length];
            for (var i = 0; i < items.Length; i++)
            {
                var slotParts = items[i].Split(':');
                userInfo.SlotsCodes[i] = slotParts.Length > 0 ? slotParts[0] : string.Empty;
                userInfo.SlotsNames[i] = slotParts.Length > 1 ? slotParts[1] : string.Empty;
            }
        }

        private static void ParseEffectsRow(UserInfo userInfo, string row)
        {
            userInfo.EffectsCodes = new string[0];
            userInfo.EffectsNames = new string[0];
            userInfo.EffectsSizes = new string[0];
            userInfo.EffectsLefts = new string[0];
            userInfo.EffectIds = string.Empty;
            userInfo.EffectStates = string.Empty;
            if (string.IsNullOrEmpty(row) || !row.StartsWith("2|", StringComparison.Ordinal) || row.Length <= 2)
                return;

            var rawEffects = row.Substring(2).Split('@');
            var codes = new List<string>();
            var names = new List<string>();
            var sizes = new List<string>();
            var lefts = new List<string>();
            var effectStates = new List<ContactRenderHelper.EffectState>();
            foreach (var rawEffect in rawEffects)
            {
                if (string.IsNullOrEmpty(rawEffect))
                    continue;

                var effectParts = rawEffect.Split(new[] { '.' }, 4);
                if (effectParts.Length == 0 || string.IsNullOrEmpty(effectParts[0]))
                    continue;

                codes.Add(effectParts[0]);
                names.Add(effectParts.Length > 1 ? effectParts[1] : string.Empty);
                sizes.Add(effectParts.Length > 2 ? effectParts[2] : "1");
                lefts.Add(effectParts.Length > 3 ? effectParts[3] : string.Empty);

                var effectId = ParseIntSafe(effectParts[0], 0);
                if (effectId > 0)
                {
                    effectStates.Add(new ContactRenderHelper.EffectState(effectId, ParseIntSafe(effectParts.Length > 2 ? effectParts[2] : string.Empty, 1), effectParts.Length > 3 ? effectParts[3] : string.Empty));
                }
            }

            userInfo.EffectsCodes = codes.ToArray();
            userInfo.EffectsNames = names.ToArray();
            userInfo.EffectsSizes = sizes.ToArray();
            userInfo.EffectsLefts = lefts.ToArray();
            userInfo.EffectStates = ContactRenderHelper.ToEffectStatesCsv(effectStates);
            userInfo.EffectIds = ContactRenderHelper.ToEffectIdsCsv(ContactRenderHelper.ExtractEffectIds(effectStates));
        }

        private static void ParseHmuRow(UserInfo userInfo, string row)
        {
            if (string.IsNullOrEmpty(row) || !row.StartsWith("4|", StringComparison.Ordinal) || row.Length <= 2)
                return;

            var parts = row.Substring(2).Split('|');
            if (parts.Length < 5)
                return;

            int.TryParse(parts[0], out userInfo.HpCur);
            int.TryParse(parts[1], out userInfo.HpMax);
            int.TryParse(parts[2], out userInfo.MaCur);
            int.TryParse(parts[3], out userInfo.MaMax);
            int.TryParse(parts[4], out userInfo.Tied);
            userInfo.Tied = 100 - userInfo.Tied;
        }

        private static string SafePart(string[] parts, int index)
        {
            return parts != null && index >= 0 && index < parts.Length && parts[index] != null ? parts[index] : string.Empty;
        }

        private static int ParseIntSafe(string value, int fallback)
        {
            if (string.IsNullOrEmpty(value))
                return fallback;

            int parsed;
            return int.TryParse(value.Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out parsed) ? parsed : fallback;
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
            var activityAdded = false;
            using (var wc = new CookieAwareWebClient { Proxy = AppVars.LocalProxy })
            {
                try
                {
                    var requestUri = GameServerSelector.RouteUriToCurrentServer(new Uri(url));
                    if (!DirectGameRequestGuard.Prepare(wc, requestUri, "NeverApi.GetInfo"))
                    {
                        return null;
                    }

                    wc.Headers[HttpRequestHeader.UserAgent] = BrowserUserAgent;
                    wc.Headers[HttpRequestHeader.Accept] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
                    wc.Headers[HttpRequestHeader.AcceptLanguage] = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7";
                    wc.Headers[HttpRequestHeader.CacheControl] = "no-cache";
                    wc.Headers[HttpRequestHeader.Referer] = GameServerSelector.RouteUrlToCurrentServer("http://www.neverlands.ru/main.php");
                    var cookieHost = requestUri.Host.Equals("neverlands.ru", StringComparison.OrdinalIgnoreCase)
                        ? "www.neverlands.ru"
                        : requestUri.Host;
                    var cookies = CookiesManager.Obtain(cookieHost);
                    if (!string.IsNullOrEmpty(cookies))
                    {
                        wc.SetCookies(requestUri, cookies);
                    }

                    IdleManager.AddActivity();
                    activityAdded = true;
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
                            if (!DirectGameRequestGuard.Prepare(wc, requestUri, "NeverApi.GetInfo.CookieRetry"))
                            {
                                return html;
                            }

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
                    if (activityAdded)
                    {
                        IdleManager.RemoveActivity();
                    }
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
