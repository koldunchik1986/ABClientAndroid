namespace ANClient.ANProxy
{
    using System;
    using System.Threading;

    internal static class ProxyRequestQueue
    {
        private const int GameRequestSpacingMs = 1000;
        private const int SkipLogThrottleMs = 10000;
        private static readonly object SyncRoot = new object();
        private static long _nextAllowedGameRequestMs;
        private static long _nextSkipLogMs;

        internal static void WaitTurn(string url, string host, bool isGameHost, bool isCache)
        {
            string reason;
            if (!ShouldQueue(url, host, isGameHost, isCache, out reason))
            {
                LogSkipThrottled(url, isGameHost, isCache, reason);
                return;
            }

            int waitMs = ReserveSlot();
            if (waitMs <= 0)
            {
                return;
            }

            AppLog.d("ProxyRequestQueue", "queued game action: reason=" + reason + ", waitMs=" + waitMs + ", url=" + SafeUrl(url));
            Thread.Sleep(waitMs);
        }

        private static bool ShouldQueue(string url, string host, bool isGameHost, bool isCache, out string reason)
        {
            reason = "not_game_host";
            if (!isGameHost || isCache || string.IsNullOrEmpty(url) || string.IsNullOrEmpty(host))
            {
                if (isGameHost && isCache)
                {
                    reason = "cache_or_static";
                }

                if (isGameHost && string.IsNullOrEmpty(url))
                {
                    reason = "empty_url";
                }

                return false;
            }

            if (url.IndexOf("top.list.ru", StringComparison.OrdinalIgnoreCase) >= 0 ||
                url.IndexOf("counter.yadro.ru", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                reason = "counter";
                return false;
            }

            string pathAndQuery = PathAndQuery(url, host);
            string path = PathOnly(pathAndQuery);

            if (IsStaticPath(path))
            {
                reason = "static_path";
                return false;
            }

            if (IsContactApiLookup(path))
            {
                reason = "contact_api_lookup";
                return false;
            }

            if (IsSafeLookup(path))
            {
                reason = "safe_lookup";
                return false;
            }

            if (IsReadOnlyChatFrame(path, pathAndQuery))
            {
                reason = "read_only_chat_frame";
                return false;
            }

            if (path.Equals("/main.php", StringComparison.OrdinalIgnoreCase))
            {
                reason = "main_php";
                return true;
            }

            if (path.StartsWith("/gameplay/ajax/", StringComparison.OrdinalIgnoreCase))
            {
                reason = "gameplay_ajax";
                return true;
            }

            if (path.Equals("/ch.php", StringComparison.OrdinalIgnoreCase))
            {
                reason = "room_or_chat_dynamic";
                return true;
            }

            if (path.EndsWith(".php", StringComparison.OrdinalIgnoreCase) ||
                path.EndsWith(".cgi", StringComparison.OrdinalIgnoreCase) ||
                path.EndsWith(".fcg", StringComparison.OrdinalIgnoreCase))
            {
                reason = "dynamic_game_endpoint";
                return true;
            }

            reason = "dynamic_game_page";
            return true;
        }

        private static string PathAndQuery(string url, string host)
        {
            string value = url ?? string.Empty;
            int schemeIndex = value.IndexOf("://", StringComparison.Ordinal);
            if (schemeIndex >= 0)
            {
                int pathIndex = value.IndexOf('/', schemeIndex + 3);
                return pathIndex >= 0 ? value.Substring(pathIndex) : "/";
            }

            if (!string.IsNullOrEmpty(host) && value.StartsWith(host, StringComparison.OrdinalIgnoreCase))
            {
                value = value.Substring(host.Length);
            }

            if (value.Length == 0)
            {
                return "/";
            }

            return value[0] == '/' ? value : "/" + value;
        }

        private static string PathOnly(string pathAndQuery)
        {
            if (string.IsNullOrEmpty(pathAndQuery))
            {
                return "/";
            }

            int queryIndex = pathAndQuery.IndexOf('?');
            return queryIndex >= 0 ? pathAndQuery.Substring(0, queryIndex) : pathAndQuery;
        }

        private static bool IsStaticPath(string path)
        {
            return path.EndsWith(".gif", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".jpg", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".jpeg", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".png", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".swf", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".ico", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".css", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".js", StringComparison.OrdinalIgnoreCase) ||
                   path.EndsWith(".txt", StringComparison.OrdinalIgnoreCase);
        }

        private static bool IsContactApiLookup(string path)
        {
            return path.Equals("/modules/api/getid.cgi", StringComparison.OrdinalIgnoreCase) ||
                   path.Equals("/modules/api/info.cgi", StringComparison.OrdinalIgnoreCase);
        }

        private static bool IsSafeLookup(string path)
        {
            return path.Equals("/modules/api/getcity.cgi", StringComparison.OrdinalIgnoreCase) ||
                   path.Equals("/pinfo.cgi", StringComparison.OrdinalIgnoreCase) ||
                   path.Equals("/pbots.cgi", StringComparison.OrdinalIgnoreCase) ||
                   path.Equals("/logs.fcg", StringComparison.OrdinalIgnoreCase);
        }

        private static bool IsReadOnlyChatFrame(string path, string pathAndQuery)
        {
            if (path.StartsWith("/ch/", StringComparison.OrdinalIgnoreCase) &&
                path.EndsWith(".html", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            return path.Equals("/ch.php", StringComparison.OrdinalIgnoreCase) &&
                   pathAndQuery.IndexOf("/ch.php?0", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static void LogSkipThrottled(string url, bool isGameHost, bool isCache, string reason)
        {
            if (!isGameHost || isCache)
            {
                return;
            }

            lock (SyncRoot)
            {
                long nowMs = UtcNowMs();
                if (nowMs < _nextSkipLogMs)
                {
                    return;
                }

                _nextSkipLogMs = nowMs + SkipLogThrottleMs;
            }

            AppLog.d("ProxyRequestQueue", "skipped game request queue: reason=" + reason + ", url=" + SafeUrl(url));
        }

        private static int ReserveSlot()
        {
            lock (SyncRoot)
            {
                long nowMs = UtcNowMs();
                long scheduledMs = nowMs;
                if (_nextAllowedGameRequestMs > nowMs)
                {
                    scheduledMs = _nextAllowedGameRequestMs;
                }

                _nextAllowedGameRequestMs = scheduledMs + GameRequestSpacingMs;
                long waitMs = scheduledMs - nowMs;
                if (waitMs <= 0)
                {
                    return 0;
                }

                return waitMs > int.MaxValue ? int.MaxValue : (int)waitMs;
            }
        }

        private static long UtcNowMs()
        {
            return DateTime.UtcNow.Ticks / TimeSpan.TicksPerMillisecond;
        }

        private static string SafeUrl(string url)
        {
            url = MaskQueryParam(url, "vcode=");
            if (string.IsNullOrEmpty(url) || url.Length <= 220)
            {
                return url ?? string.Empty;
            }

            return url.Substring(0, 220);
        }

        private static string MaskQueryParam(string value, string key)
        {
            if (string.IsNullOrEmpty(value) || string.IsNullOrEmpty(key))
            {
                return value;
            }

            int startIndex = value.IndexOf(key, StringComparison.OrdinalIgnoreCase);
            while (startIndex >= 0)
            {
                int valueStartIndex = startIndex + key.Length;
                int valueEndIndex = value.IndexOf('&', valueStartIndex);
                if (valueEndIndex < 0)
                {
                    valueEndIndex = value.Length;
                }

                value = value.Substring(0, valueStartIndex) + "<redacted>" + value.Substring(valueEndIndex);
                startIndex = value.IndexOf(key, valueStartIndex + "<redacted>".Length, StringComparison.OrdinalIgnoreCase);
            }

            return value;
        }
    }
}
