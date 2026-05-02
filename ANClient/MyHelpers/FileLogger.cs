using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace ANClient.MyHelpers
{
    /// <summary>
    /// Файловое логирование — аналог Android FileLogger.java.
    /// 
    /// Пишет отладочные файлы в папку Logs/ рядом с исполняемым файлом.
    /// Структура папок (аналог Android files/Logs/):
    ///   Logs/Critical/{сегмент}_{chain}.log — основные цепочки
    ///   Logs/pool/{сегмент}_proxy.txt — proxy-трафик
    /// 
    /// Сегментация: 10-минутные сегменты (аналог Android SEGMENT_FORMAT).
    /// Ротация: макс. 8 МБ на файл, до 2 бакапов (.1, .2).
    /// 
    /// Потокобезопасность: Single-threaded Executor (аналог Android IO ExecutorService),
    ///   все записи сериализуются через единственный фоновый поток.
    /// 
    /// Зависимости: Application.StartupPath (папка Logs/).
    /// </summary>
    internal static class FileLogger
    {
        private const long MaxFileBytes = 8L * 1024L * 1024L;
        private const int MaxRotations = 2;
        private static readonly object FileLock = new object();

        // Single-threaded queue — аналог Android IO ExecutorService
        private static readonly Thread WriterThread;
        private static readonly AutoResetEvent WakeEvent = new AutoResetEvent(false);
        private static volatile bool _shutdown = false;

        static FileLogger()
        {
            WriterThread = new Thread(WriterLoop) { IsBackground = true, Name = "an-file-logger" };
            WriterThread.Start();
        }

        private static readonly ProducerConsumerQueue Queue = new ProducerConsumerQueue();

        private class LogEntry
        {
            internal string Level;
            internal string Chain;
            internal string Message;
            internal Exception Error;
            internal bool IsProxy;
        }

        private class ProducerConsumerQueue
        {
            private readonly object _lock = new object();
            private readonly System.Collections.Generic.Queue<LogEntry> _items = new System.Collections.Generic.Queue<LogEntry>();

            internal void Enqueue(LogEntry entry)
            {
                lock (_lock)
                {
                    _items.Enqueue(entry);
                }
            }

            internal bool Dequeue(out LogEntry entry)
            {
                lock (_lock)
                {
                    if (_items.Count == 0)
                    {
                        entry = null;
                        return false;
                    }
                    entry = _items.Dequeue();
                    return true;
                }
            }

            internal int Count
            {
                get
                {
                    lock (_lock) { return _items.Count; }
                }
            }
        }

        private static void WriterLoop()
        {
            while (!_shutdown)
            {
                try
                {
                    WakeEvent.WaitOne(5000, false);
                    LogEntry entry;
                    while (Queue.Dequeue(out entry))
                    {
                        try
                        {
                            AppendLineToFile(entry);
                        }
                        catch
                        {
                        }
                    }
                }
                catch
                {
                }
            }
        }

        internal static void Trace(string chain, string message)
        {
            Write("TRACE", chain, message, null);
        }

        internal static void Log(string message)
        {
            Trace("FileLogger", message);
        }

        internal static void Warn(string chain, string message)
        {
            Write("WARN", chain, message, null);
        }

        internal static void Error(string chain, string message, Exception error)
        {
            Write("ERROR", chain, message, error);
        }

        internal static void ProxyPool(string message)
        {
            WriteToProxySegment("TRACE", message, null);
        }

        internal static void ProxyPoolError(string message, Exception error)
        {
            WriteToProxySegment("ERROR", message, error);
        }

        private static void Write(string level, string chain, string message, Exception error)
        {
            if (string.IsNullOrEmpty(chain) || string.IsNullOrEmpty(message))
                return;

            Queue.Enqueue(new LogEntry
            {
                Level = level ?? "TRACE",
                Chain = SanitizeChain(chain),
                Message = SanitizeMessage(message),
                Error = error
            });
            WakeEvent.Set();
        }

        private static void WriteToProxySegment(string level, string message, Exception error)
        {
            if (string.IsNullOrEmpty(message))
                return;

            Queue.Enqueue(new LogEntry
            {
                Level = level ?? "TRACE",
                Chain = "proxy",
                Message = SanitizeMessage(message),
                Error = error,
                IsProxy = true
            });
            WakeEvent.Set();
        }

        private static void AppendLineToFile(LogEntry entry)
        {
            try
            {
                var logsRoot = ResolveLogsRoot();
                if (logsRoot == null)
                    return;

                string targetFile;
                if (entry.IsProxy)
                {
                    var poolDir = Path.Combine(logsRoot, "pool");
                    if (!Directory.Exists(poolDir))
                        Directory.CreateDirectory(poolDir);
                    var segment = BuildSegmentName(DateTime.Now);
                    targetFile = Path.Combine(poolDir, segment + "_proxy.txt");
                }
                else
                {
                    var criticalDir = Path.Combine(logsRoot, "Critical");
                    if (!Directory.Exists(criticalDir))
                        Directory.CreateDirectory(criticalDir);
                    var segment = BuildSegmentName(DateTime.Now);
                    targetFile = Path.Combine(criticalDir, segment + "_" + entry.Chain + ".log");
                }

                RotateIfNeeded(targetFile);

                var line = string.Format("{0} [{1}] {2}{3}",
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff"),
                    entry.Level,
                    entry.Message,
                    Environment.NewLine);

                if (entry.Error != null)
                {
                    line += entry.Error.ToString() + Environment.NewLine;
                }

                lock (FileLock)
                {
                    File.AppendAllText(targetFile, line, Encoding.UTF8);
                }
            }
            catch
            {
            }
        }

        private static string ResolveLogsRoot()
        {
            try
            {
                var path = Path.Combine(Application.StartupPath, "Logs");
                if (!Directory.Exists(path))
                    Directory.CreateDirectory(path);
                return path;
            }
            catch
            {
                return null;
            }
        }

        private static void RotateIfNeeded(string filePath)
        {
            try
            {
                if (!File.Exists(filePath))
                    return;

                var info = new FileInfo(filePath);
                if (info.Length < MaxFileBytes)
                    return;

                for (var i = MaxRotations; i >= 1; i--)
                {
                    var src = i == 1 ? filePath : filePath + "." + (i - 1);
                    var dst = filePath + "." + i;
                    if (!File.Exists(src))
                        continue;
                    if (File.Exists(dst))
                        File.Delete(dst);
                    File.Move(src, dst);
                }
            }
            catch
            {
            }
        }

        private static string SanitizeChain(string chain)
        {
            var value = chain.Trim().ToLowerInvariant();
            foreach (var c in System.IO.Path.GetInvalidFileNameChars())
                value = value.Replace(c, '_');
            if (string.IsNullOrEmpty(value))
                value = "critical";
            return value;
        }

        private static string SanitizeMessage(string message)
        {
            var value = message.Replace('\n', ' ').Replace('\r', ' ').Trim();
            if (value.Length > 3000)
                value = value.Substring(0, 3000) + "...";
            return value;
        }

        private static string BuildSegmentName(DateTime now)
        {
            var hourPrefix = now.ToString("yyyyMMdd_HH");
            var bucket = (now.Minute / 10) * 10;
            return hourPrefix + "_" + bucket.ToString("00");
        }
    }
}
