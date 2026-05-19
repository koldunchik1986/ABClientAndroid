namespace ANClient.ANProxy
{
    using System;
    using System.Net;

    internal static class DirectGameRequestGuard
    {
        private const string Tag = "DirectGameRequestGuard";
        private const int BlockLogThrottleMs = 10000;
        private static readonly object SyncRoot = new object();
        private static long _lastBlockedLogMs;

        private delegate void ApplyProxyDelegate(WebProxy proxy);

        internal static bool Prepare(HttpWebRequest request, string source)
        {
            if (request == null)
            {
                return false;
            }

            return Prepare(request.RequestUri, delegate(WebProxy proxy) { request.Proxy = proxy; }, source);
        }

        internal static bool Prepare(WebClient client, Uri address, string source)
        {
            if (client == null)
            {
                return false;
            }

            return Prepare(address, delegate(WebProxy proxy) { client.Proxy = proxy; }, source);
        }

        private static bool Prepare(Uri address, ApplyProxyDelegate applyProxy, string source)
        {
            if (address == null || !IsNeverlandsHost(address.Host))
            {
                return true;
            }

            bool usesLocalProxy = false;
            if (AppVars.Profile != null && AppVars.Profile.DoProxy)
            {
                if (AppVars.LocalProxy == null)
                {
                    LogBlocked(source, address);
                    return false;
                }

                applyProxy(AppVars.LocalProxy);
                usesLocalProxy = true;
            }
            else if (AppVars.LocalProxy != null)
            {
                applyProxy(AppVars.LocalProxy);
                usesLocalProxy = true;
            }

            if (!usesLocalProxy)
            {
                ProxyRequestQueue.WaitTurn(address.Host + address.PathAndQuery, address.Host, true, IsCache(address));
            }

            return true;
        }

        private static bool IsNeverlandsHost(string host)
        {
            return !string.IsNullOrEmpty(host) &&
                   (host.Equals("neverlands.ru", StringComparison.OrdinalIgnoreCase) ||
                    host.EndsWith(".neverlands.ru", StringComparison.OrdinalIgnoreCase));
        }

        private static bool IsCache(Uri address)
        {
            var path = address.AbsolutePath ?? string.Empty;
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

        private static void LogBlocked(string source, Uri address)
        {
            lock (SyncRoot)
            {
                var nowMs = UtcNowMs();
                if (nowMs - _lastBlockedLogMs < BlockLogThrottleMs)
                {
                    return;
                }

                _lastBlockedLogMs = nowMs;
            }

            AppLog.e(Tag, "DIRECT_GAME_REQUEST_BLOCKED: source=" + Safe(source) + ", reason=proxy_enabled_but_local_proxy_missing, url=" + SafeUrl(address));
        }

        private static long UtcNowMs()
        {
            return DateTime.UtcNow.Ticks / TimeSpan.TicksPerMillisecond;
        }

        private static string Safe(string value)
        {
            return string.IsNullOrEmpty(value) ? "unknown" : value;
        }

        private static string SafeUrl(Uri address)
        {
            var value = address == null ? string.Empty : address.ToString();
            if (value.Length <= 220)
            {
                return value;
            }

            return value.Substring(0, 220);
        }
    }
}
