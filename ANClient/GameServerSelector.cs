using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Windows.Forms;

namespace ANClient
{
    internal static class GameServerSelector
    {
        internal const string ServerDe = "DE";
        internal const string ServerKz = "KZ";
        internal const string DefaultServerCode = ServerDe;
        internal const string ServerDeIp = "136.243.18.79";
        internal const string ServerKzIp = "213.148.10.84";
        internal const string ServerNeverlands = "neverlands.ru";
        internal const int ServerPingPort = 80;
        internal const int ServerPingTimeoutMs = 2500;

        private const char EntrySeparator = '=';
        private const char FieldSeparator = '|';
        private const string ServerListFileName = "anservers.txt";
        private static readonly object SyncRoot = new object();
        private static List<ServerEntry> _serverEntries;

        internal sealed class ServerEntry
        {
            internal ServerEntry(string code, string host, string loginFormServerCode, string title)
            {
                Code = code;
                Host = host;
                LoginFormServerCode = loginFormServerCode;
                Title = title;
            }

            internal string Code { get; private set; }

            internal string Host { get; private set; }

            internal string LoginFormServerCode { get; private set; }

            internal string Title { get; private set; }
        }

        internal static string NormalizeServerCode(string serverCode)
        {
            var text = (serverCode ?? string.Empty).Trim();
            var entries = ServerEntries();
            for (var i = 0; i < entries.Length; i++)
            {
                if (string.Equals(text, entries[i].Code, StringComparison.OrdinalIgnoreCase))
                {
                    return entries[i].Code;
                }
            }

            return entries.Length == 0 ? DefaultServerCode : entries[0].Code;
        }

        internal static int DisplayIndex(string serverCode)
        {
            var normalized = NormalizeServerCode(serverCode);
            var entries = ServerEntries();
            for (var i = 0; i < entries.Length; i++)
            {
                if (string.Equals(entries[i].Code, normalized, StringComparison.OrdinalIgnoreCase))
                {
                    return i;
                }
            }

            return 0;
        }

        internal static string CodeForDisplayValue(string displayValue)
        {
            var text = (displayValue ?? string.Empty).Trim();
            var codePart = text;
            var space = codePart.IndexOf(' ');
            var bracket = codePart.IndexOf('(');
            var cut = -1;
            if (space >= 0)
            {
                cut = space;
            }

            if (bracket >= 0 && (cut < 0 || bracket < cut))
            {
                cut = bracket;
            }

            if (cut > 0)
            {
                codePart = codePart.Substring(0, cut).Trim();
            }

            var entries = ServerEntries();
            for (var i = 0; i < entries.Length; i++)
            {
                if (string.Equals(codePart, entries[i].Code, StringComparison.OrdinalIgnoreCase))
                {
                    return entries[i].Code;
                }
            }

            return NormalizeServerCode(text);
        }

        internal static string LoginFormServerCode(string serverCode)
        {
            return EntryForCode(serverCode).LoginFormServerCode;
        }

        internal static string ServerIp(string serverCode)
        {
            return ServerHost(serverCode);
        }

        internal static string ServerHost(string serverCode)
        {
            return EntryForCode(serverCode).Host;
        }

        internal static ServerEntry[] ServerEntries()
        {
            EnsureLoaded();
            lock (SyncRoot)
            {
                return _serverEntries.ToArray();
            }
        }

        internal static string DisplayName(string serverCode, long? pingMs)
        {
            var entry = EntryForCode(serverCode);
            string pingText;
            if (!pingMs.HasValue)
            {
                pingText = "... ms";
            }
            else if (pingMs.Value < 0)
            {
                pingText = "timeout";
            }
            else
            {
                pingText = pingMs.Value + " ms";
            }

            var title = string.IsNullOrEmpty(entry.Title) || string.Equals(entry.Title, entry.Code, StringComparison.OrdinalIgnoreCase)
                            ? entry.Code
                            : entry.Code + " " + entry.Title;
            return title + " (" + entry.Host + ") - " + pingText;
        }

        internal static string EditableServerListText()
        {
            return EntriesToText(ServerEntries());
        }

        internal static string DefaultEditableServerListText()
        {
            return EntriesToText(DefaultEntries().ToArray());
        }

        internal static bool SaveEditableServerList(string text, out string error)
        {
            List<ServerEntry> entries;
            if (!TryParseServerEntries(text, out entries, out error))
            {
                return false;
            }

            lock (SyncRoot)
            {
                try
                {
                    File.WriteAllText(ServerListFilePath(), EntriesToText(entries.ToArray()), new UTF8Encoding(false));
                }
                catch (IOException ex)
                {
                    error = "Не удалось сохранить список серверов: " + ex.Message;
                    return false;
                }
                catch (UnauthorizedAccessException ex)
                {
                    error = "Нет доступа для сохранения списка серверов: " + ex.Message;
                    return false;
                }

                _serverEntries = entries;
            }

            return true;
        }

        internal static void ResetServerList()
        {
            lock (SyncRoot)
            {
                _serverEntries = DefaultEntries();
                File.WriteAllText(ServerListFilePath(), EntriesToText(_serverEntries.ToArray()), new UTF8Encoding(false));
            }
        }

        internal static string CurrentServerCode()
        {
            if (AppVars.Profile == null)
            {
                return DefaultServerCode;
            }

            return NormalizeServerCode(AppVars.Profile.GameServerCode);
        }

        internal static bool IsNeverlandsGameHost(string host)
        {
            var normalized = NormalizeHost(host);
            return normalized.Equals("neverlands.ru", StringComparison.OrdinalIgnoreCase) ||
                   normalized.Equals("www.neverlands.ru", StringComparison.OrdinalIgnoreCase) ||
                   IsConfiguredServerHost(normalized);
        }

        internal static bool IsConfiguredServerHost(string host)
        {
            var normalized = NormalizeHost(host);
            var entries = ServerEntries();
            for (var i = 0; i < entries.Length; i++)
            {
                if (string.Equals(normalized, NormalizeHost(entries[i].Host), StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        internal static bool IsSelectedServerHost(string host)
        {
            return string.Equals(NormalizeHost(host), NormalizeHost(ServerHost(CurrentServerCode())), StringComparison.OrdinalIgnoreCase);
        }

        internal static string ProxyRouteHost(string host)
        {
            if (IsNeverlandsGameHost(host) && !IsSelectedServerHost(host))
            {
                return ServerHost(CurrentServerCode());
            }

            return host;
        }

        internal static string RouteUrlToCurrentServer(string rawUrl)
        {
            try
            {
                return RouteUriToCurrentServer(new Uri(rawUrl)).ToString();
            }
            catch (UriFormatException)
            {
                return rawUrl;
            }
        }

        internal static Uri RouteUriToCurrentServer(Uri uri)
        {
            if (uri == null || !IsNeverlandsGameHost(uri.Host) || IsSelectedServerHost(uri.Host))
            {
                return uri;
            }

            var builder = new UriBuilder(uri);
            builder.Host = ServerHost(CurrentServerCode());
            if (uri.IsDefaultPort)
            {
                builder.Port = -1;
            }

            return builder.Uri;
        }

        internal static string[] CookieHosts()
        {
            var hosts = new List<string>();
            AddUniqueHost(hosts, ServerHost(CurrentServerCode()));
            AddUniqueHost(hosts, "www.neverlands.ru");
            AddUniqueHost(hosts, "neverlands.ru");
            return hosts.ToArray();
        }

        internal static long MeasureTcpPingMs(string serverCode, int timeoutMs)
        {
            var startedAt = DateTime.UtcNow;
            var socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
            try
            {
                var asyncResult = socket.BeginConnect(ServerHost(serverCode), ServerPingPort, null, null);
                var connected = asyncResult.AsyncWaitHandle.WaitOne(Math.Max(1, timeoutMs), false);
                if (!connected)
                {
                    return -1L;
                }

                socket.EndConnect(asyncResult);
                return Math.Max(0L, (long)DateTime.UtcNow.Subtract(startedAt).TotalMilliseconds);
            }
            catch (SocketException)
            {
                return -1L;
            }
            catch (ObjectDisposedException)
            {
                return -1L;
            }
            finally
            {
                socket.Close();
            }
        }

        private static ServerEntry EntryForCode(string serverCode)
        {
            var normalized = (serverCode ?? string.Empty).Trim();
            var entries = ServerEntries();
            for (var i = 0; i < entries.Length; i++)
            {
                if (string.Equals(normalized, entries[i].Code, StringComparison.OrdinalIgnoreCase))
                {
                    return entries[i];
                }
            }

            return entries.Length == 0 ? DefaultEntries()[0] : entries[0];
        }

        private static void EnsureLoaded()
        {
            lock (SyncRoot)
            {
                if (_serverEntries != null)
                {
                    return;
                }

                string error;
                List<ServerEntry> entries;
                try
                {
                    if (File.Exists(ServerListFilePath()) &&
                        TryParseServerEntries(File.ReadAllText(ServerListFilePath(), Encoding.UTF8), out entries, out error))
                    {
                        _serverEntries = entries;
                        return;
                    }
                }
                catch (IOException)
                {
                }
                catch (UnauthorizedAccessException)
                {
                }

                _serverEntries = DefaultEntries();
            }
        }

        private static string ServerListFilePath()
        {
            return Path.Combine(Application.StartupPath, ServerListFileName);
        }

        private static List<ServerEntry> DefaultEntries()
        {
            var entries = new List<ServerEntry>();
            entries.Add(new ServerEntry(ServerDe, ServerDeIp, "de", ServerDe));
            entries.Add(new ServerEntry(ServerKz, ServerKzIp, "KZ", ServerKz));
            entries.Add(new ServerEntry(ServerNeverlands, ServerNeverlands, string.Empty, ServerNeverlands));
            return entries;
        }

        private static bool TryParseServerEntries(string text, out List<ServerEntry> entries, out string error)
        {
            entries = new List<ServerEntry>();
            error = null;
            var usedCodes = new List<string>();
            var lines = (text ?? string.Empty).Replace("\r\n", "\n").Replace('\r', '\n').Split('\n');
            for (var i = 0; i < lines.Length; i++)
            {
                var line = lines[i].Trim();
                if (line.Length == 0 || line.StartsWith("#", StringComparison.Ordinal))
                {
                    continue;
                }

                var separator = line.IndexOf(EntrySeparator);
                if (separator <= 0)
                {
                    error = "Строка " + (i + 1) + ": нужен формат CODE=host|server|title";
                    return false;
                }

                var code = line.Substring(0, separator).Trim();
                var fields = line.Substring(separator + 1).Split(FieldSeparator);
                var host = fields.Length > 0 ? fields[0].Trim() : string.Empty;
                var loginServerCode = fields.Length > 1 ? fields[1].Trim() : string.Empty;
                var title = fields.Length > 2 ? fields[2].Trim() : code;
                if (string.IsNullOrEmpty(code) || string.IsNullOrEmpty(host))
                {
                    error = "Строка " + (i + 1) + ": код и host обязательны";
                    return false;
                }

                if (ContainsCode(usedCodes, code))
                {
                    error = "Строка " + (i + 1) + ": дублируется код сервера " + code;
                    return false;
                }

                usedCodes.Add(code);
                entries.Add(new ServerEntry(code, NormalizeHost(host), loginServerCode, title));
            }

            EnsureDefaultEntry(entries, ServerDe, ServerDeIp, "de", ServerDe);
            EnsureDefaultEntry(entries, ServerKz, ServerKzIp, "KZ", ServerKz);
            EnsureDefaultEntry(entries, ServerNeverlands, ServerNeverlands, string.Empty, ServerNeverlands);
            return true;
        }

        private static void EnsureDefaultEntry(List<ServerEntry> entries, string code, string host, string loginServerCode, string title)
        {
            for (var i = 0; i < entries.Count; i++)
            {
                if (string.Equals(entries[i].Code, code, StringComparison.OrdinalIgnoreCase))
                {
                    return;
                }
            }

            entries.Add(new ServerEntry(code, host, loginServerCode, title));
        }

        private static string EntriesToText(ServerEntry[] entries)
        {
            var sb = new StringBuilder();
            for (var i = 0; i < entries.Length; i++)
            {
                if (sb.Length > 0)
                {
                    sb.AppendLine();
                }

                sb.Append(entries[i].Code);
                sb.Append(EntrySeparator);
                sb.Append(entries[i].Host);
                sb.Append(FieldSeparator);
                sb.Append(entries[i].LoginFormServerCode);
                sb.Append(FieldSeparator);
                sb.Append(entries[i].Title);
            }

            return sb.ToString();
        }

        private static bool ContainsCode(List<string> codes, string code)
        {
            for (var i = 0; i < codes.Count; i++)
            {
                if (string.Equals(codes[i], code, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        private static void AddUniqueHost(List<string> hosts, string host)
        {
            var normalized = NormalizeHost(host);
            if (string.IsNullOrEmpty(normalized))
            {
                return;
            }

            for (var i = 0; i < hosts.Count; i++)
            {
                if (string.Equals(hosts[i], normalized, StringComparison.OrdinalIgnoreCase))
                {
                    return;
                }
            }

            hosts.Add(normalized);
        }

        private static string NormalizeHost(string host)
        {
            var normalized = (host ?? string.Empty).Trim();
            if (normalized.StartsWith("http://", StringComparison.OrdinalIgnoreCase))
            {
                normalized = normalized.Substring("http://".Length);
            }
            else if (normalized.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                normalized = normalized.Substring("https://".Length);
            }

            var slash = normalized.IndexOf('/');
            if (slash >= 0)
            {
                normalized = normalized.Substring(0, slash);
            }

            var colon = normalized.LastIndexOf(':');
            if (colon > 0 && normalized.IndexOf(']') == -1)
            {
                normalized = normalized.Substring(0, colon);
            }

            return normalized.Trim();
        }
    }
}
