namespace ANClient.Info
{
    using System;
    using System.Collections.Generic;
    using System.Globalization;
    using System.Threading;

    internal static class ProfessionRatingMonitor
    {
        private const string Tag = "ProfessionRatingMonitor";
        private const string TraceChain = "prof_rating_trace";
        private static readonly object SyncRoot = new object();
        private static readonly Dictionary<string, string> LastCheckedHourByNick = new Dictionary<string, string>();
        private static readonly TimeSpan RetryAfterFailure = TimeSpan.FromMinutes(10);

        private static bool _checkRunning;
        private static DateTime _lastCheckStartedAtUtc = DateTime.MinValue;

        internal static void MaybeCheck()
        {
            if (ANClient.AppVars.Profile == null)
            {
                return;
            }

            string nick = ResolveCurrentNick();
            if (nick.Length == 0)
            {
                return;
            }

            DateTime serverDate = ResolveServerDate();
            if (serverDate.DayOfWeek != DayOfWeek.Sunday || serverDate.Hour < 15)
            {
                return;
            }

            string hourKey = serverDate.ToString("yyyyMMddHH", CultureInfo.InvariantCulture);
            string nickKey = ProfessionRatingRepository.NormalizeNick(nick);
            lock (SyncRoot)
            {
                string lastHour;
                if (LastCheckedHourByNick.TryGetValue(nickKey, out lastHour) && string.Equals(lastHour, hourKey, StringComparison.Ordinal))
                {
                    return;
                }

                if (_checkRunning || DateTime.UtcNow.Subtract(_lastCheckStartedAtUtc) < RetryAfterFailure)
                {
                    return;
                }

                _checkRunning = true;
                _lastCheckStartedAtUtc = DateTime.UtcNow;
            }

            ThreadPool.QueueUserWorkItem(RunCheck, new CheckState(nick, nickKey, hourKey));
        }

        private static void RunCheck(object state)
        {
            var checkState = (CheckState)state;
            bool anySuccess = false;
            int hitCount = 0;
            try
            {
                foreach (ProfessionRatingRepository.Category category in ProfessionRatingRepository.GetCategories())
                {
                    try
                    {
                        ProfessionRatingRepository.RatingTable table = ProfessionRatingRepository.LoadRating(category.Id, true);
                        anySuccess = true;
                        ProfessionRatingRepository.RatingEntry hit = FindTopTenHit(table, checkState.NormalizedNick);
                        if (hit != null)
                        {
                            hitCount++;
                            PostHitNotification(category, hit);
                        }
                    }
                    catch (Exception error)
                    {
                        ANClient.AppLog.w(TraceChain, Tag, "WEEKLY_RATING_CHECK_FAILED: id=" + category.Id.ToString(CultureInfo.InvariantCulture) + ", title=" + category.Title, error);
                    }
                }

                if (anySuccess)
                {
                    lock (SyncRoot)
                    {
                        LastCheckedHourByNick[checkState.NormalizedNick] = checkState.HourKey;
                    }
                }

                ANClient.AppLog.i(TraceChain, Tag, "WEEKLY_RATING_CHECK_DONE: nick=" + checkState.Nick + ", hour=" + checkState.HourKey + ", hits=" + hitCount.ToString(CultureInfo.InvariantCulture) + ", anySuccess=" + anySuccess);
            }
            finally
            {
                lock (SyncRoot)
                {
                    _checkRunning = false;
                }
            }
        }

        private static ProfessionRatingRepository.RatingEntry FindTopTenHit(ProfessionRatingRepository.RatingTable table, string normalizedNick)
        {
            if (table == null || normalizedNick.Length == 0)
            {
                return null;
            }

            int limit = Math.Min(10, table.Entries.Count);
            for (int index = 0; index < limit; index++)
            {
                ProfessionRatingRepository.RatingEntry entry = table.Entries[index];
                if (string.Equals(normalizedNick, ProfessionRatingRepository.NormalizeNick(entry.Nick), StringComparison.Ordinal))
                {
                    return entry;
                }
            }

            return null;
        }

        private static void PostHitNotification(ProfessionRatingRepository.Category category, ProfessionRatingRepository.RatingEntry entry)
        {
            string message = "[ProfessionRatingMonitor] Вы попали в рейтинг " + category.Title + " под № " + entry.Rank.ToString(CultureInfo.InvariantCulture) + ". Не забудьте забрать награду!";
            if (ANClient.AppVars.MainForm != null)
            {
                ANClient.AppVars.MainForm.WriteChatMsgSafe(message);
            }

            ANClient.AppLog.i(TraceChain, Tag, "WEEKLY_RATING_TOP10_NOTIFY: category=" + category.Title + ", rank=" + entry.Rank.ToString(CultureInfo.InvariantCulture) + ", nick=" + entry.Nick);
        }

        private static string ResolveCurrentNick()
        {
            return ANClient.AppVars.Profile == null || ANClient.AppVars.Profile.UserNick == null ? string.Empty : ANClient.AppVars.Profile.UserNick.Trim();
        }

        private static DateTime ResolveServerDate()
        {
            if (ANClient.AppVars.Profile != null && ANClient.AppVars.Profile.ServDiff != TimeSpan.MinValue)
            {
                return DateTime.Now.Subtract(ANClient.AppVars.Profile.ServDiff);
            }

            return ANClient.AppVars.ServerDateTime == DateTime.MinValue ? DateTime.Now : ANClient.AppVars.ServerDateTime;
        }

        private sealed class CheckState
        {
            internal CheckState(string nick, string normalizedNick, string hourKey)
            {
                Nick = nick;
                NormalizedNick = normalizedNick;
                HourKey = hourKey;
            }

            internal string Nick { get; private set; }

            internal string NormalizedNick { get; private set; }

            internal string HourKey { get; private set; }
        }
    }
}
