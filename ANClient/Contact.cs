using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Net;
using System.Text;
using ANClient.ANForms;
using ANClient.ANProxy;
using ANClient.Helpers;
using ANClient.MyHelpers;
using ANClient.Things;

namespace ANClient
{
    internal sealed class Contact
    {
        private const string DetailsSeparator = "\r\n\r\n--- Данные сервера ---\r\n";

        internal string Name { get; private set; } // Имя 
        internal string Comments { get; set; } // Комментарии
        internal bool Tracing { get; set; } // Нужно ли следить за контактом
        internal int ClassId { get; set; } // Группа: 0 -нейтрал, 1 - враг, 2 - друг
        internal int ToolId { get; set; } // Применение нападалки: 0 - по умолчанию, 1 - боевая , 2 - закрытая боевая, 3 - кулачка, 4 - закрытая кулачка

        internal string PlayerId { get; private set; } // app2: playerID
        internal int PlayerLevel { get; private set; } // app2: playerLevel
        internal int Inclination { get; private set; } // app2: inclination
        internal string InclinationName { get; private set; } // app2: inclinationName
        internal string ClanNumber { get; private set; } // app2: clanNumber
        internal string ClanIco { get; private set; } // app2: clanIco
        internal string ClanName { get; private set; } // app2: clanName
        internal string ClanStatus { get; private set; } // app2: clanStatus
        internal int Gender { get; private set; } // app2: gender
        internal int BlockStatus { get; private set; } // app2: blockStatus
        internal int JailStatus { get; private set; } // app2: jailStatus
        internal int MuteSeconds { get; private set; } // app2: muteSeconds
        internal int MuteForumSeconds { get; private set; } // app2: muteForumSeconds
        internal int OnlineStatus { get; private set; } // app2: onlineStatus
        internal string GeoLocation { get; private set; } // app2: geoLocation
        internal string WarLogNumber { get; private set; } // app2: warLogNumber
        internal string EffectIds { get; private set; } // app2: effectIds
        internal string EffectStates { get; private set; } // app2: effectStates
        
        internal string Sign { get; private set; } // Значок клана
        internal string Align { get; private set; } // Склонность
        internal string Clan { get; private set; } // Название клана
        
        internal string Level { get; private set; } // Уровень
        internal string Location { get; private set; } // Локация

        internal string TreeNode { get; private set; } // Наш TreeNode
        internal string Parent { get; set; } // string.Empty - если мы в корне, иначе это TreeNodeId группы

        private int WoundCounts { get; set; }
        internal int[] Wounds { get; private set; }

        private string[] CodeEffects { get; set; }
        private string[] NameEffects { get; set; }

        private sealed class EffectNameLookup
        {
            internal readonly Dictionary<int, string> ById = new Dictionary<int, string>();
        }

        internal bool IsMolch { get; private set; }
        internal bool IsOnline { get; private set; }
        private DateTime LastUpdated { get; set; }
        internal DateTime NextCheck { get; set; }

        private int Tied { get; set; }
        private string Flog { get; set; }
        private bool IsBotLog { get; set; }
        private string[] PSlots { get; set; }

        private bool _isFirst;
        private int _delay;

        internal Contact(string name, int classid, int toolid, string comments, bool tracing, bool delayedCheck)
        {
            Name = name.Trim();
            ClassId = classid;
            ToolId = toolid;
            Comments = comments;
            Tracing = tracing;
            Sign = string.Empty;
            Align = string.Empty;
            Clan = string.Empty;
            Level = string.Empty;
            Location = string.Empty;
            InternalInit(delayedCheck);
        }

        internal Contact(string name, int classid, int toolid, string sign, string clan, string align, string comments, bool tracing, string level, bool delayedCheck)
        {
            Name = name.Trim();
            ClassId = classid;
            ToolId = toolid;
            Sign = sign;
            Align = align;
            Clan = clan;
            Comments = comments;
            Tracing = tracing;
            Level = level;
            InternalInit(delayedCheck);
        }

        private void InternalInit(bool delayedCheck)
        {
            PlayerId = string.Empty;
            PlayerLevel = ParseIntSafe(Level, 0);
            Inclination = ParseIntSafe(Align, 0);
            InclinationName = ContactRenderHelper.GetInclinationName(Inclination);
            ClanNumber = string.Empty;
            ClanIco = Sign ?? string.Empty;
            ClanName = Clan ?? string.Empty;
            ClanStatus = string.Empty;
            Gender = 0;
            BlockStatus = 0;
            JailStatus = 0;
            MuteSeconds = 0;
            MuteForumSeconds = 0;
            OnlineStatus = 0;
            GeoLocation = string.Empty;
            WarLogNumber = string.Empty;
            EffectIds = string.Empty;
            EffectStates = string.Empty;
            Parent = string.Empty;
            Location = string.Empty;
            WoundCounts = 0;
            Wounds = new int[4];
            CodeEffects = new string[0];
            NameEffects = new string[0];
            Tied = 0;
            Flog = string.Empty;
            _isFirst = true;
            TreeNode = Guid.NewGuid().ToString();
            _delay = 0;
            LastUpdated = DateTime.MinValue;
            NextCheck = delayedCheck ? DateTime.Now.AddSeconds(Dice.Make(30, 90)) : DateTime.Now;
        }

        public override string ToString()
        {
            var result = string.IsNullOrEmpty(Level) ? Name : string.Format(CultureInfo.InvariantCulture, "{0} [{1}]", Name, Level);
            return result;
        }

        internal string BuildTreeText()
        {
            return ToString();
        }

        internal void ApplyPersistedProfile(
            string playerId,
            int playerLevel,
            int inclination,
            string inclinationName,
            string clanNumber,
            string clanIco,
            string clanName,
            string clanStatus,
            int gender,
            int blockStatus,
            int jailStatus,
            int muteSeconds,
            int muteForumSeconds,
            int onlineStatus,
            string geoLocation,
            string warLogNumber,
            string effectIds,
            string effectStates)
        {
            ApplyServerFields(
                Name,
                playerId,
                playerLevel,
                inclination,
                inclinationName,
                clanNumber,
                clanIco,
                clanName,
                clanStatus,
                gender,
                blockStatus,
                jailStatus,
                muteSeconds,
                muteForumSeconds,
                onlineStatus,
                geoLocation,
                warLogNumber,
                effectIds,
                effectStates);
        }

        internal void ApplySnapshot(UserInfo userInfo)
        {
            if (userInfo == null)
                return;

            var location = NormalizeLocation(userInfo.Location);
            var flog = NormalizeFightLog(userInfo.FightLog);
            ApplyServerFields(
                userInfo.Nick,
                userInfo.PlayerId,
                userInfo.PlayerLevel,
                userInfo.Inclination,
                userInfo.InclinationName,
                userInfo.ClanNumber,
                userInfo.ClanIco,
                userInfo.ClanName,
                userInfo.ClanStatus,
                userInfo.Gender,
                userInfo.BlockStatus,
                userInfo.JailStatus,
                userInfo.MuteSeconds,
                userInfo.MuteForumSeconds,
                userInfo.OnlineStatus,
                location,
                flog,
                userInfo.EffectIds,
                userInfo.EffectStates);

            Location = location;
            IsOnline = ResolveIsOnline(userInfo, location);
            OnlineStatus = ResolveOnlineStatus(userInfo, IsOnline);
            Tied = userInfo.Tied;
            Flog = flog;
            PSlots = BuildSlotSnapshot(userInfo);
            ApplyEffectSnapshot(userInfo);
            _isFirst = false;
            _delay = 0;
            NextCheck = DateTime.Now;
        }

        internal void ApplyClanRosterSnapshot(UserInfo userInfo)
        {
            if (userInfo == null)
                return;

            ApplyServerFields(
                userInfo.Nick,
                string.IsNullOrEmpty(userInfo.PlayerId) ? PlayerId : userInfo.PlayerId,
                userInfo.PlayerLevel > 0 ? userInfo.PlayerLevel : PlayerLevel,
                Inclination,
                InclinationName,
                string.IsNullOrEmpty(userInfo.ClanNumber) ? ClanNumber : userInfo.ClanNumber,
                string.IsNullOrEmpty(userInfo.ClanIco) ? ClanIco : userInfo.ClanIco,
                string.IsNullOrEmpty(userInfo.ClanName) ? ClanName : userInfo.ClanName,
                string.IsNullOrEmpty(userInfo.ClanStatus) ? ClanStatus : userInfo.ClanStatus,
                Gender,
                BlockStatus,
                JailStatus,
                MuteSeconds,
                MuteForumSeconds,
                OnlineStatus,
                GeoLocation,
                WarLogNumber,
                EffectIds,
                EffectStates);
        }

        internal string BuildDetailsText()
        {
            var sb = new StringBuilder();
            if (!string.IsNullOrEmpty(Comments))
            {
                sb.Append(Comments);
            }

            var serverDetails = BuildServerDetailsText();
            if (serverDetails.Length > 0)
            {
                sb.Append(DetailsSeparator);
                sb.Append(serverDetails);
            }

            return sb.ToString();
        }

        internal static string ExtractEditableComments(string detailsText)
        {
            if (string.IsNullOrEmpty(detailsText))
                return string.Empty;

            var pos = detailsText.IndexOf(DetailsSeparator, StringComparison.Ordinal);
            if (pos < 0)
                return detailsText;

            return detailsText.Substring(0, pos);
        }

        private void ApplyServerFields(
            string nick,
            string playerId,
            int playerLevel,
            int inclination,
            string inclinationName,
            string clanNumber,
            string clanIco,
            string clanName,
            string clanStatus,
            int gender,
            int blockStatus,
            int jailStatus,
            int muteSeconds,
            int muteForumSeconds,
            int onlineStatus,
            string geoLocation,
            string warLogNumber,
            string effectIds,
            string effectStates)
        {
            if (!string.IsNullOrEmpty(nick))
            {
                Name = nick.Trim();
            }

            PlayerId = playerId ?? string.Empty;
            PlayerLevel = playerLevel > 0 ? playerLevel : ParseIntSafe(Level, 0);
            Inclination = inclination;
            InclinationName = string.IsNullOrEmpty(inclinationName) ? ContactRenderHelper.GetInclinationName(inclination) : inclinationName;
            ClanNumber = clanNumber ?? string.Empty;
            ClanIco = clanIco ?? string.Empty;
            ClanName = clanName ?? string.Empty;
            ClanStatus = clanStatus ?? string.Empty;
            Gender = gender;
            BlockStatus = blockStatus;
            JailStatus = jailStatus;
            MuteSeconds = muteSeconds;
            MuteForumSeconds = muteForumSeconds;
            OnlineStatus = onlineStatus;
            GeoLocation = geoLocation ?? string.Empty;
            WarLogNumber = string.IsNullOrEmpty(warLogNumber) ? string.Empty : warLogNumber;
            EffectIds = effectIds ?? string.Empty;
            EffectStates = effectStates ?? string.Empty;

            if (PlayerLevel > 0)
            {
                Level = PlayerLevel.ToString(CultureInfo.InvariantCulture);
            }

            Align = inclination.ToString(CultureInfo.InvariantCulture);
            Sign = string.IsNullOrEmpty(ClanIco) ? (string.IsNullOrEmpty(Sign) ? "none" : Sign) : ClanIco;
            Clan = ContactRenderHelper.IsNeutralClanName(ClanName) ? string.Empty : ClanName;
        }

        private string BuildServerDetailsText()
        {
            var sb = new StringBuilder();
            AppendDetail(sb, "playerID", PlayerId);
            AppendDetail(sb, "Ник", Name);
            AppendDetail(sb, "Уровень", PlayerLevel > 0 ? PlayerLevel.ToString(CultureInfo.InvariantCulture) : Level);
            AppendDetail(sb, "Склонность", string.IsNullOrEmpty(InclinationName) ? Align : InclinationName + " (" + Align + ")");
            AppendDetail(sb, "Клан", string.IsNullOrEmpty(ClanName) ? Clan : ClanName);
            AppendDetail(sb, "Иконка клана", ClanIco);
            AppendDetail(sb, "Статус в клане", ClanStatus);
            AppendDetail(sb, "Пол", Gender.ToString(CultureInfo.InvariantCulture));
            AppendDetail(sb, "Блокировка", BlockStatus.ToString(CultureInfo.InvariantCulture));
            AppendDetail(sb, "Тюрьма", JailStatus.ToString(CultureInfo.InvariantCulture));
            AppendDetail(sb, "Молчанка чат", MuteSeconds.ToString(CultureInfo.InvariantCulture));
            AppendDetail(sb, "Молчанка форум", MuteForumSeconds.ToString(CultureInfo.InvariantCulture));
            AppendDetail(sb, "Онлайн", OnlineStatus.ToString(CultureInfo.InvariantCulture));
            AppendDetail(sb, "Локация", GeoLocation);
            AppendDetail(sb, "Бой", WarLogNumber);
            AppendDetail(sb, "Эффекты", FormatEffectDetails());
            return sb.ToString();
        }

        private string FormatEffectDetails()
        {
            var states = ContactRenderHelper.ParseEffectStatesCsv(EffectStates, EffectIds);
            if (states.Count == 0)
                return string.Empty;

            var sb = new StringBuilder();
            foreach (var state in states)
            {
                if (sb.Length > 0)
                    sb.Append(", ");

                sb.Append(state.Id.ToString(CultureInfo.InvariantCulture));
                sb.Append(' ');
                sb.Append(ContactRenderHelper.FormatEffectCounterText(state));
            }

            return sb.ToString();
        }

        private static void AppendDetail(StringBuilder sb, string title, string value)
        {
            if (string.IsNullOrEmpty(value))
                return;

            if (sb.Length > 0)
                sb.AppendLine();

            sb.Append(title);
            sb.Append(": ");
            sb.Append(value);
        }

        private static string NormalizeLocation(string location)
        {
            if (string.IsNullOrEmpty(location))
                return string.Empty;

            var result = location;
            var splocation = result.Split(new[] { " [" }, StringSplitOptions.RemoveEmptyEntries);
            if (splocation.Length == 2)
            {
                splocation[1] = splocation[1].Substring(0, splocation[1].Length - 1);
                if ((splocation[1].IndexOf(splocation[0], StringComparison.OrdinalIgnoreCase) != -1) || splocation[1].Contains(","))
                {
                    result = splocation[1];
                }
            }

            return result;
        }

        private static string NormalizeFightLog(string fightLog)
        {
            if (string.IsNullOrEmpty(fightLog) || fightLog.Equals("0", StringComparison.Ordinal))
                return string.Empty;

            return fightLog;
        }

        private static bool ResolveIsOnline(UserInfo userInfo, string normalizedLocation)
        {
            if (userInfo == null)
                return !string.IsNullOrEmpty(normalizedLocation);

            return userInfo.Online || userInfo.OnlineStatus > 0 || !string.IsNullOrEmpty(normalizedLocation);
        }

        private static int ResolveOnlineStatus(UserInfo userInfo, bool isOnline)
        {
            if (userInfo != null && userInfo.OnlineStatus > 0)
                return userInfo.OnlineStatus;

            return isOnline ? 1 : 0;
        }

        private static string[] BuildSlotSnapshot(UserInfo userInfo)
        {
            if (userInfo == null || userInfo.SlotsCodes == null)
                return new string[0];

            var pslots = new string[userInfo.SlotsCodes.Length];
            for (var indexSlot = 0; indexSlot < userInfo.SlotsCodes.Length; indexSlot++)
            {
                var slotName = userInfo.SlotsNames != null && indexSlot < userInfo.SlotsNames.Length ? userInfo.SlotsNames[indexSlot] : string.Empty;
                pslots[indexSlot] = $"{userInfo.SlotsCodes[indexSlot]}:{slotName}";
            }

            return pslots;
        }

        private void ApplyEffectSnapshot(UserInfo userInfo)
        {
            var woundCounts = 0;
            var wounds = new int[4];
            var isMolch = false;
            var codeEffects = new List<string>();
            var nameEffects = new List<string>();
            if (userInfo != null && userInfo.EffectsCodes != null)
            {
                for (var k = 0; k < userInfo.EffectsCodes.Length; k++)
                {
                    var effcode = userInfo.EffectsCodes[k];
                    var effname = userInfo.EffectsNames != null && k < userInfo.EffectsNames.Length ? userInfo.EffectsNames[k] : string.Empty;
                    int effcount;
                    int.TryParse(userInfo.EffectsSizes != null && k < userInfo.EffectsSizes.Length ? userInfo.EffectsSizes[k] : string.Empty, out effcount);
                    switch (effcode)
                    {
                        case "1":
                            woundCounts += effcount;
                            wounds[3] = effcount;
                            break;
                        case "2":
                            woundCounts += effcount;
                            wounds[2] = effcount;
                            break;
                        case "3":
                            woundCounts += effcount;
                            wounds[1] = effcount;
                            break;
                        case "4":
                            woundCounts += effcount;
                            wounds[0] = effcount;
                            break;
                        case "17":
                            isMolch = true;
                            break;
                        default:
                            codeEffects.Add(effcode);
                            var pos = effname.IndexOf(" (", StringComparison.Ordinal);
                            var ename = (pos >= 0 ? effname.Substring(0, pos) : effname).Trim('\'');
                            nameEffects.Add(ename);
                            break;
                    }
                }
            }

            WoundCounts = woundCounts;
            Wounds = wounds;
            IsMolch = isMolch;
            CodeEffects = codeEffects.ToArray();
            NameEffects = nameEffects.ToArray();
        }

        private static Dictionary<int, ContactRenderHelper.EffectState> BuildChatEffectStateMap(string effectStates, string effectIds)
        {
            var result = new Dictionary<int, ContactRenderHelper.EffectState>();
            var states = ContactRenderHelper.ParseEffectStatesCsv(effectStates, effectIds);
            foreach (var state in states)
            {
                if (state == null || !IsChatEffect(state.Id))
                    continue;

                result[state.Id] = state;
            }

            return result;
        }

        private static EffectNameLookup BuildEffectNameLookup(IList<string> codes, IList<string> names)
        {
            var result = new EffectNameLookup();
            if (codes == null)
                return result;

            for (var index = 0; index < codes.Count; index++)
            {
                var id = ParseIntSafe(codes[index], 0);
                if (!IsChatEffect(id))
                    continue;

                var name = names != null && index < names.Count ? NormalizeEffectName(names[index]) : string.Empty;
                if (string.IsNullOrEmpty(name))
                    name = "эффект #" + id.ToString(CultureInfo.InvariantCulture);

                result.ById[id] = name;
            }

            return result;
        }

        private static bool IsChatEffect(int id)
        {
            return id > 0 && id != 1 && id != 2 && id != 3 && id != 4 && id != 17;
        }

        private static string NormalizeEffectName(string effectName)
        {
            if (string.IsNullOrEmpty(effectName))
                return string.Empty;

            var pos = effectName.IndexOf(" (", StringComparison.Ordinal);
            return (pos >= 0 ? effectName.Substring(0, pos) : effectName).Trim('\'');
        }

        private static string GetEffectName(EffectNameLookup lookup, int effectId)
        {
            if (lookup != null)
            {
                string name;
                if (lookup.ById.TryGetValue(effectId, out name) && !string.IsNullOrEmpty(name))
                    return name;
            }

            return "эффект #" + effectId.ToString(CultureInfo.InvariantCulture);
        }

        private static string BuildEffectChatEntry(ContactRenderHelper.EffectState state, string effectName)
        {
            if (state == null || state.Id <= 0)
                return string.Empty;

            var safeName = string.IsNullOrEmpty(effectName) ? "эффект #" + state.Id.ToString(CultureInfo.InvariantCulture) : effectName;
            return string.Format(
                CultureInfo.InvariantCulture,
                "&nbsp;<img src=http://image.neverlands.ru/pinfo/eff_{0}.gif width=15 height=15 align=absmiddle title=\"{1}\">&nbsp;{1} {2}",
                state.Id,
                safeName,
                ContactRenderHelper.FormatEffectCounterText(state));
        }

        private static string BuildEffectChangeChatEntry(ContactRenderHelper.EffectState oldState, ContactRenderHelper.EffectState newState, string effectName)
        {
            if (newState == null || newState.Id <= 0)
                return string.Empty;

            var safeName = string.IsNullOrEmpty(effectName) ? "эффект #" + newState.Id.ToString(CultureInfo.InvariantCulture) : effectName;
            var oldCounter = oldState == null ? string.Empty : ContactRenderHelper.FormatEffectCounterText(oldState);
            var newCounter = ContactRenderHelper.FormatEffectCounterText(newState);
            return string.Format(
                CultureInfo.InvariantCulture,
                "&nbsp;<img src=http://image.neverlands.ru/pinfo/eff_{0}.gif width=15 height=15 align=absmiddle title=\"{1}\">&nbsp;{1} {2}&rarr;{3}",
                newState.Id,
                safeName,
                oldCounter,
                newCounter);
        }

        private static bool HasEffectCountChanged(ContactRenderHelper.EffectState oldState, ContactRenderHelper.EffectState newState)
        {
            return oldState != null && newState != null && Math.Max(1, oldState.Count) != Math.Max(1, newState.Count);
        }

        private static bool HasEffectTimeoutRefresh(ContactRenderHelper.EffectState oldState, ContactRenderHelper.EffectState newState)
        {
            if (oldState == null || newState == null)
                return false;

            if (HasEffectCountChanged(oldState, newState))
                return false;

            int oldSeconds;
            int newSeconds;
            if (!TryParseEffectTimeoutSeconds(oldState.Timeout, out oldSeconds) || !TryParseEffectTimeoutSeconds(newState.Timeout, out newSeconds))
                return false;

            return newSeconds > oldSeconds + 60;
        }

        private static bool TryParseEffectTimeoutSeconds(string timeout, out int seconds)
        {
            seconds = 0;
            if (string.IsNullOrEmpty(timeout))
                return false;

            var parts = timeout.Trim().Split(':');
            if (parts.Length < 2)
                return false;

            int hours;
            int minutes;
            int secs = 0;
            if (!int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out hours) ||
                !int.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out minutes))
            {
                return false;
            }

            if (parts.Length > 2 && !int.TryParse(parts[2], NumberStyles.Integer, CultureInfo.InvariantCulture, out secs))
                secs = 0;

            seconds = hours * 3600 + minutes * 60 + secs;
            return true;
        }

        private static int ParseIntSafe(string value, int fallback)
        {
            if (string.IsNullOrEmpty(value))
                return fallback;

            int parsed;
            return int.TryParse(value.Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out parsed) ? parsed : fallback;
        }

        internal void Process(UserInfo userInfo)
        {
            var currentTimeStamp = DateTime.Now;
            if (userInfo == null)
                return;

            var tied = userInfo.Tied;
            var isKa = false;
            var sslots = new StringBuilder();
            var pslots = new string[userInfo.SlotsCodes.Length];
            for (var indexSlot = 0; indexSlot < userInfo.SlotsCodes.Length; indexSlot++)
            {
                pslots[indexSlot] = $"{userInfo.SlotsCodes[indexSlot]}:{userInfo.SlotsNames[indexSlot]}";
                if ((indexSlot != 0) && (indexSlot != 1) && (indexSlot != 2) && (indexSlot != 3) &&
                    (indexSlot != 7) && (indexSlot != 10) &&
                    (indexSlot != 11) && (indexSlot != 12) && (indexSlot != 13) && (indexSlot != 14) &&
                    (indexSlot != 15))
                    continue;

                var thingImage = userInfo.SlotsCodes[indexSlot];
                if (!isKa)
                {
                    var tl = ThingsDb.Find(thingImage);
                    if (tl.Count == 0)
                        isKa = true;
                }

                sslots.Append(thingImage);
                sslots.Append('@');
            }

            if (sslots.Length == 0)
                return;

            sslots.Length--;

            var nick = userInfo.Nick;
            var location = NormalizeLocation(userInfo.Location);
            var isonline = ResolveIsOnline(userInfo, location);
            var flog = NormalizeFightLog(userInfo.FightLog);

            var clan = userInfo.ClanName;

            var woundCounts = 0;
            var wounds = new int[4];
            var isMolch = false;
            var codeEffects = new List<string>();
            var nameEffects = new List<string>();

            // var effects = [[1,'Боевая травма (x9) (еще 23:06:17)'],[2,'Тяжелая травма (x2) (еще 07:01:22)'],[17,'Молчанка (еще 00:00:05)']];
            var sbeff = new StringBuilder();
            if (userInfo.EffectsCodes.Length > 0)
            {
                for (var k = 0; k < userInfo.EffectsCodes.Length; k++)
                {
                    var effcode = userInfo.EffectsCodes[k];
                    var effname = userInfo.EffectsNames[k];
                    sbeff.AppendFormat(
                        @"&nbsp;<img src=http://image.neverlands.ru/pinfo/eff_{0}.gif width=15 height=15 align=absmiddle title=""{1}"">",
                        effcode,
                        effname);
                    int effcount;
                    int.TryParse(userInfo.EffectsSizes[k], out effcount);

                    switch (effcode)
                    {
                        case "1":
                            woundCounts += effcount;
                            wounds[3] = effcount;
                            break;

                        case "2":
                            woundCounts += effcount;
                            wounds[2] = effcount;
                            break;

                        case "3":
                            woundCounts += effcount;
                            wounds[1] = effcount;
                            break;

                        case "4":
                            woundCounts += effcount;
                            wounds[0] = effcount;
                            break;

                        case "17":
                            isMolch = true;
                            break;

                        default:
                            codeEffects.Add(effcode);
                            var pos = effname.IndexOf(" (", StringComparison.Ordinal);
                            var ename = (pos >= 0 ? effname.Substring(0, pos) : effname).Trim('\'');
                            nameEffects.Add(ename);
                            break;
                    }
                }
            }

            var oldEffectIds = EffectIds;
            var oldEffectStates = EffectStates;

            ApplyServerFields(
                nick,
                userInfo.PlayerId,
                userInfo.PlayerLevel,
                userInfo.Inclination,
                userInfo.InclinationName,
                userInfo.ClanNumber,
                userInfo.ClanIco,
                clan,
                userInfo.ClanStatus,
                userInfo.Gender,
                userInfo.BlockStatus,
                userInfo.JailStatus,
                userInfo.MuteSeconds,
                userInfo.MuteForumSeconds,
                userInfo.OnlineStatus,
                location,
                flog,
                userInfo.EffectIds,
                userInfo.EffectStates);

            var sb = new StringBuilder();
            var messagePrefix = HtmlContactEntry(this)/* + sbeff*/;

            // Вход/выход

            if (!_isFirst && IsOnline != isonline)
            {
                if (sb.Length > 0)
                    sb.Append(',');

                if (isonline)
                    sb.AppendFormat(@" появляется в <font color=""#3F7F62"">{0}</font>", location);
                else
                    sb.AppendFormat(
                        isKa
                            ? @" исчезает в <font color=""#3F7F62"">{0}</font>"
                            : @" выходит из игры в <font color=""#3F7F62"">{0}</font>", Location);
            }

            // Переодевания

            if (ClassId != 2)
            {
                if (!_isFirst)
                {
                    var sbp = new StringBuilder();
                    var changes = 0;
                    if (pslots != null && PSlots != null && pslots.Length == PSlots.Length)
                    {
                        for (var i = 0; i < pslots.Length; i++)
                        {
                            var opars = PSlots[i].Split(':');
                            if (opars.Length < 2)
                                continue;

                            var npars = pslots[i].Split(':');
                            if (npars.Length < 2)
                                continue;

                            var oimage = opars[0];
                            var nimage = npars[0];

                            var oname = opars[1];
                            var nname = npars[1];

                            if (oimage.Equals(nimage, StringComparison.CurrentCultureIgnoreCase) &&
                                oname.Equals(nname, StringComparison.CurrentCultureIgnoreCase))
                                continue;

                            sbp.Append(sbp.Length > 0 ? ", " : " ");
                            if (nimage.StartsWith("sl_"))
                            {
                                sbp.Append("снимает");
                                sbp.Append(" &laquo;");
                                sbp.Append(oname);
                            }
                            else
                            {
                                sbp.Append("одевает");
                                sbp.Append(" &laquo;");
                                sbp.Append(nname);
                            }

                            sbp.Append("&raquo;");
                            changes++;
                        }
                    }

                    if (sbp.Length > 0)
                    {
                        if (sb.Length > 0)
                            sb.Append(',');

                        sb.Append(changes > 3 ? " переодевается" : sbp.ToString());
                    }
                }
            }

            // Изменение усталости

            if (ClassId != 2)
            {
                if (!_isFirst && isonline && IsOnline && Location != location && !string.IsNullOrEmpty(location))
                {
                    if (sb.Length > 0)
                        sb.Append(',');

                    if (Tied != tied)
                        sb.AppendFormat(@" усталость <b>{0}</b> в <font color=""#3F7F62"">{1}</font>", tied, location);
                    else
                        sb.AppendFormat(@" переходит в <font color=""#3F7F62"">{0}</font>", location);
                }
                else
                {
                    if (!_isFirst && isonline && IsOnline && Location == location && Tied < tied &&
                        !string.IsNullOrEmpty(location))
                    {
                        if (sb.Length > 0)
                            sb.Append(',');

                        sb.AppendFormat(@" усталость {0}&rarr;<b>{1}</b> в <font color=""#3F7F62"">{2}</font>", Tied,
                            tied, location);
                    }
                }
            }

            // Вступление в бой

            if (!_isFirst && isonline && (string.IsNullOrEmpty(Flog) && !string.IsNullOrEmpty(flog) || (!string.IsNullOrEmpty(Flog) && !string.IsNullOrEmpty(flog) && !Flog.Equals(flog, StringComparison.Ordinal))))
            {
                IsBotLog = false;
                var wc = new WebClient {Proxy = AppVars.LocalProxy};
                byte[] bufferLog = null;
                try
                {
                    IdleManager.AddActivity();
                    var fightLogUri = new Uri("http://www.neverlands.ru/logs.fcg?fid=" + flog);
                    if (DirectGameRequestGuard.Prepare(wc, fightLogUri, "Contact.FightLog"))
                    {
                        bufferLog = wc.DownloadData(fightLogUri);
                    }
                }
                catch (Exception ex)
                {
                    Debug.Print(ex.Message);
                }
                finally
                {
                    wc.Dispose();
                    IdleManager.RemoveActivity();
                }

                // var logs = [[1387615150,6],[[0,"12:39"],"Бой между",[4,1]," и",[1,2,"*Трогвар*",22,1,"c138"]," начался (закрытое боевое нападение) (21.12.2013 12:39:10)."]];
                // var logs = [[1387626458,6],[[0,"15:47"],"Бой между",[4,1]," и",[1,2,"~Shtaket~",18,3,"c167"]," начался (боевое нападение) (21.12.2013 15:47:38)."],[[0,"15:47"],[1,1,"Ruber",22,1,"necr"]," <B>вмешался в бой.</B>"],[[0,"15:49"],[1,1,"_NickName_",22,1,"glow"]," использовал заклинание <img src=http://image.neverlands.ru/signs/darks.gif width=15 height=12 border=0 align=absmiddle> «<B>Темное нападение</B>» и вмешался в бой.</B>"],[[0,"15:49"],[1,2,"~Shtaket~",18,3,"c167"]," получил осложненную травму <font color=#E34242><b>«Множественные повреждения основания свода черепа»</b></font>."],[[0,"15:49"],"<B>Бой закончен по таймауту</B> (",[4,1]," )."],[[0,"15:49"],"<B>Победа за</B>",[4,1],",",[1,1,"_NickName_",22,1,"glow"],",",[1,1,"Ruber",22,1,"necr"],"."]];

                if (bufferLog != null)
                {
                    var fight = Russian.Codepage.GetString(bufferLog);

                    if ((fight.IndexOf("(нападение бота)", StringComparison.OrdinalIgnoreCase) != -1) ||
                        (fight.IndexOf("var logs = [];", StringComparison.OrdinalIgnoreCase) != -1))
                    {
                        IsBotLog = true;
                    }

                    if (!IsBotLog)
                    {
                        if (sb.Length > 0)
                            sb.Append(',');

                        var fighttype = HelperStrings.SubString(fight, " начался (", ")") ?? "обычное нападение";
                        if (fight.IndexOf("Бой (жертвенный)", StringComparison.OrdinalIgnoreCase) != -1)
                            fighttype = "жертвенный бой";


                        var livesg1 = HelperStrings.SubString(fight, "var lives_g1 = [", "];");
                        if (!string.IsNullOrEmpty(livesg1))
                        {
                            var pars = livesg1.Split(',');
                            var nick1 = (pars.Length > 2) && !livesg1.StartsWith("[4")
                                ? pars[1].Trim('"')
                                : "невидимка";

                            var livesg2 = HelperStrings.SubString(fight, "var lives_g2 = [", "];");
                            if (!string.IsNullOrEmpty(livesg2))
                            {
                                pars = livesg2.Split(',');
                                var nick2 = (pars.Length > 2) && !livesg2.StartsWith("[4")
                                    ? pars[1].Trim('"')
                                    : "невидимка";

                                if (livesg2.IndexOf(Name, StringComparison.Ordinal) == -1)
                                {
                                    // нападает на перса nick2
                                    sb.AppendFormat(
                                        @" <a href=http://www.neverlands.ru/logs.fcg?fid={0} onclick=""window.open(this.href);"">нападает</a> на {1} ({2})",
                                        flog, HtmlPercEntry(nick2), fighttype);
                                }
                                else
                                {
                                    if (nick1.Equals("невидимка", StringComparison.OrdinalIgnoreCase))
                                    {
                                        // на меня напал невидимка
                                        sb.AppendFormat(
                                            @" <a href=http://www.neverlands.ru/logs.fcg?fid={0} onclick=""window.open(this.href);"">атакован</a> невидимкой ({1})",
                                            flog, fighttype);
                                    }
                                    else
                                    {
                                        // на меня напал перс nick1
                                        sb.AppendFormat(
                                            @" <a href=http://www.neverlands.ru/logs.fcg?fid={0} onclick=""window.open(this.href);"">атакован</a> {1} ({2})",
                                            flog, HtmlPercEntry(nick1), fighttype);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Получение молчанки

            if (!_isFirst && IsMolch != isMolch)
            {
                if (sb.Length > 0)
                    sb.Append(',');

                sb.AppendFormat(isMolch ? " получает молчанку" : " выходит из молчания");
            }

            // Травмы

            if (!_isFirst && (WoundCounts > 0) && (woundCounts == 0))
            {
                if (sb.Length > 0)
                    sb.Append(',');

                sb.AppendFormat(" излечивается от всех травм");
            }

            // Травмы

            if (!_isFirst && (WoundCounts > 0) && (woundCounts > 0) && (woundCounts < WoundCounts))
            {
                if (sb.Length > 0)
                    sb.Append(',');

                sb.AppendFormat(" излечивается (травм стало: {0})", woundCounts);
            }

            // Травмы

            if (!_isFirst && (woundCounts > WoundCounts))
            {
                if (sb.Length > 0)
                    sb.Append(',');

                if ((woundCounts - WoundCounts) > 1)
                    sb.AppendFormat(" получает несколько травм");
                else
                {
                    string wound = "никакую";
                    if (wounds[3] > Wounds[3])
                        wound = "боевую";
                    else
                    {
                        if (wounds[2] > Wounds[2])
                            wound = "тяжелую";
                        else
                        {
                            if (wounds[1] > Wounds[1])
                                wound = "среднюю";
                            else
                            {
                                if (wounds[0] > Wounds[0])
                                    wound = "легкую";
                            }
                        }
                    }

                    sb.AppendFormat(" получает {0} травму (травм стало: {1})", wound, woundCounts);
                }
            }

            // Эффекты

            if (ClassId != 2)
            {
                if (!_isFirst)
                {
                    var oldEffectStateById = BuildChatEffectStateMap(oldEffectStates, oldEffectIds);
                    var newEffectStateById = BuildChatEffectStateMap(userInfo.EffectStates, userInfo.EffectIds);
                    var oldEffectNames = BuildEffectNameLookup(CodeEffects, NameEffects);
                    var newEffectNames = BuildEffectNameLookup(codeEffects, nameEffects);

                    var sbadd = new StringBuilder();
                    foreach (var effectState in newEffectStateById.Values)
                    {
                        if (!oldEffectStateById.ContainsKey(effectState.Id))
                        {
                            if (sbadd.Length == 0)
                                sbadd.Append(" получает");

                            sbadd.Append(BuildEffectChatEntry(effectState, GetEffectName(newEffectNames, effectState.Id)));
                        }
                    }

                    if (sbadd.Length > 0)
                    {
                        if (sb.Length > 0)
                            sb.Append(',');

                        sb.Append(sbadd);
                    }

                    var sbrem = new StringBuilder();
                    foreach (var effectState in oldEffectStateById.Values)
                    {
                        if (!newEffectStateById.ContainsKey(effectState.Id))
                        {
                            if (sbrem.Length == 0)
                                sbrem.Append(" теряет");

                            sbrem.Append(BuildEffectChatEntry(effectState, GetEffectName(oldEffectNames, effectState.Id)));
                        }
                    }

                    if (sbrem.Length > 0)
                    {
                        if (sb.Length > 0)
                            sb.Append(',');

                        sb.Append(sbrem);
                    }

                    var sbchg = new StringBuilder();
                    foreach (var effectState in newEffectStateById.Values)
                    {
                        ContactRenderHelper.EffectState oldState;
                        if (!oldEffectStateById.TryGetValue(effectState.Id, out oldState))
                            continue;

                        if (!HasEffectCountChanged(oldState, effectState) && !HasEffectTimeoutRefresh(oldState, effectState))
                            continue;

                        if (sbchg.Length == 0)
                            sbchg.Append(" меняет");

                        sbchg.Append(BuildEffectChangeChatEntry(oldState, effectState, GetEffectName(newEffectNames, effectState.Id)));
                    }

                    if (sbchg.Length > 0)
                    {
                        if (sb.Length > 0)
                            sb.Append(',');

                        sb.Append(sbchg);
                    }
                }
            }

            NameEffects = nameEffects.ToArray();
            CodeEffects = codeEffects.ToArray();

            OnlineStatus = ResolveOnlineStatus(userInfo, isonline);
            GeoLocation = location;
            WarLogNumber = flog;
            IsOnline = isonline;
            Location = location;
            Tied = tied;
            Flog = flog;
            PSlots = pslots;
            IsMolch = isMolch;
            WoundCounts = woundCounts;
            Wounds = wounds;

            _isFirst = false;
            if (sb.Length > 0)
            {
                _delay = 0;
                var message = messagePrefix + sb;
                if (currentTimeStamp > LastUpdated)
                {
                    LastUpdated = currentTimeStamp;
                    try
                    {
                        if (AppVars.MainForm != null)
                        {
                            AppVars.MainForm.BeginInvoke(
                                new UpdateChatDelegate(AppVars.MainForm.UpdateChat), message);
                        }
                    }
                    catch (InvalidOperationException)
                    {
                    }
                }
            }
            else
            {
                _delay += Dice.Make(1, 1000);
                if (_delay > 10000)
                {
                    _delay = 10000;
                }
            }

            if (!IsOnline)
            {
                if (ClassId != 2)
                    _delay = 60000;
            }

            NextCheck = DateTime.Now.AddMilliseconds(_delay);

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateContactDelegate(AppVars.MainForm.UpdateContact), this);
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static string HtmlPercEntry(string nick)
        {
            var userInfo = NeverApi.GetAll(nick);
            if (userInfo == null)
                return "Аноним";

            nick = userInfo.Nick;
            var nnhtmlSec = nick.Replace("+", "%2B");
            var colorNick = nick;
            switch (ContactsManager.GetClassIdOfContact(nick))
            {
                case 1:
                    colorNick = @"<font color=""#8A0808"">" + colorNick + "</font>";
                    break;
                case 2:
                    colorNick = @"<font color=""#0B610B"">" + colorNick + "</font>";
                    break;
                default:
                    colorNick = @"<font color=""#000000"">" + colorNick + "</font>";
                    break;
            }

            var align = userInfo.Align;
            var sign = userInfo.ClanSign;
            var level = userInfo.Level;
            var clan = userInfo.ClanName;

            var sbeff = new StringBuilder();
            if (userInfo.EffectsCodes.Length > 0)
            {
                for (var k = 0; k < userInfo.EffectsCodes.Length; k++)
                {
                    var effcode = userInfo.EffectsCodes[k];
                    var effname = userInfo.EffectsNames[k];
                    sbeff.AppendFormat(
                        @"&nbsp;<img src=http://image.neverlands.ru/pinfo/eff_{0}.gif width=15 height=15 align=absmiddle title=""{1}"">",
                        effcode,
                        effname);
                }
            }

            var sleeps = string.Empty/*sbeff.ToString()*/;
            var ali1 = string.Empty;
            var ali2 = string.Empty;
            switch (align)
            {
                case "0":
                    ali1 = string.Empty;
                    ali2 = string.Empty;
                    break;
                case "1":
                    ali1 = "darks.gif";
                    ali2 = "Дети Тьмы";
                    break;
                case "2":
                    ali1 = "lights.gif";
                    ali2 = "Дети Света";
                    break;
                case "3":
                    ali1 = "sumers.gif";
                    ali2 = "Дети Сумерек";
                    break;
                case "4":
                    ali1 = "chaoss.gif";
                    ali2 = "Дети Хаоса";
                    break;
                case "5":
                    ali1 = "light.gif";
                    ali2 = "Истинный Свет";
                    break;
                case "6":
                    ali1 = "dark.gif";
                    ali2 = "Истинная Тьма";
                    break;
                case "7":
                    ali1 = "sumer.gif";
                    ali2 = "Нейтральные Сумерки";
                    break;
                case "8":
                    ali1 = "chaos.gif";
                    ali2 = "Абсолютный Хаос";
                    break;
                case "9":
                    ali1 = "angel.gif";
                    ali2 = "Ангел";
                    break;
            }

            align = string.IsNullOrEmpty(ali1)?
                string.Empty :
                "<img src=http://image.neverlands.ru/signs/" +
                ali1 +
                @" width=15 height=12 align=absmiddle border=0 title=""" +
                ali2 +
                @""">&nbsp";

            var ss = string.Empty;
            if (!string.IsNullOrEmpty(clan))
            {
                ss =
                    "<img src=http://image.neverlands.ru/signs/" +
                    sign +
                    @" width=15 height=12 align=absmiddle title=""" +
                    clan +
                    @""">&nbsp;";
            }

            var result =
                @"<a href=""#"" onclick=""top.say_private('" +
                nick +
                @"');""><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;" +
                align +
                ss +
                @"<a class=""activenick"" href=""#"" onclick=""top.say_to('" + nick + @"');""><font class=nickname><b>" +
                colorNick +
                "</b></a>[" +
                level +
                @"]</font><a href=""http://www.neverlands.ru/pinfo.cgi?" +
                nnhtmlSec +
                @""" onclick=""window.open(this.href);""><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>" +
                sleeps;
            return result;
        }

        private static string HtmlContactEntry(Contact contact)
        {
            var nnhtmlSec = contact.Name;
            {
                nnhtmlSec = nnhtmlSec.Replace("+", "%2B");
            }

            var colorNick = contact.Name;
            switch (contact.ClassId)
            {
                case 0:
                    colorNick = @"<font color=""#000000"">" + colorNick + "</font>";
                    break;
                case 1:
                    colorNick = @"<font color=""#8A0808"">" + colorNick + "</font>";
                    break;
                case 2:
                    colorNick = @"<font color=""#0B610B"">" + colorNick + "</font>";
                    break;
            }

            var sleeps = string.Empty;
            var ali1 = string.Empty;
            var ali2 = string.Empty;
            
            switch (contact.Align)
            {
                case "0":
                    ali1 = string.Empty;
                    ali2 = string.Empty;
                    break;
                case "1":
                    ali1 = "darks.gif";
                    ali2 = "Дети Тьмы";
                    break;
                case "2":
                    ali1 = "lights.gif";
                    ali2 = "Дети Света";
                    break;
                case "3":
                    ali1 = "sumers.gif";
                    ali2 = "Дети Сумерек";
                    break;
                case "4":
                    ali1 = "chaoss.gif";
                    ali2 = "Дети Хаоса";
                    break;
                case "5":
                    ali1 = "light.gif";
                    ali2 = "Истинный Свет";
                    break;
                case "6":
                    ali1 = "dark.gif";
                    ali2 = "Истинная Тьма";
                    break;
                case "7":
                    ali1 = "sumer.gif";
                    ali2 = "Нейтральные Сумерки";
                    break;
                case "8":
                    ali1 = "chaos.gif";
                    ali2 = "Абсолютный Хаос";
                    break;
                case "9":
                    ali1 = "angel.gif";
                    ali2 = "Ангел";
                    break;
            }

            var align = string.IsNullOrEmpty(ali1)
                ? string.Empty
                : "<img src=http://image.neverlands.ru/signs/" +
                  ali1 +
                  @" width=15 height=12 align=absmiddle border=0 title=""" +
                  ali2 +
                  @""">&nbsp";

            var ss = string.Empty;
            if (!string.IsNullOrEmpty(contact.Clan))
            {
                ss =
                    "<img src=http://image.neverlands.ru/signs/" +
                    contact.Sign +
                    @" width=15 height=12 align=absmiddle title=""" +
                    contact.Clan +
                    @""">&nbsp;";
            }

            /* top.say_private( top.say_to( не работает */
            var result =
                @"<a href=""#"" onclick=""top.say_private('" +
                contact.Name +
                @"');""><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;" +
                align +
                ss +
                @"<a class=""activenick"" href=""#"" onclick=""top.say_to('" + contact.Name + @"');""><font class=nickname><b>" +
                colorNick +
                "</b></a>[" +
                contact.Level +
                @"]</font><a href=""http://www.neverlands.ru/pinfo.cgi?" +
                nnhtmlSec +
                @""" onclick=""window.open(this.href);""><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>" +
                sleeps;
            return result;
        }
    }
}
