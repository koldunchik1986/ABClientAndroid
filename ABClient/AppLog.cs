using System;
using System.Diagnostics;
using ABClient.MyHelpers;

namespace ABClient
{
    /// <summary>
    /// Единая точка логирования — аналог Android AppLog.java.
    /// 
    /// Пишет в:
    ///   1. System.Diagnostics.Debug (аналог Android Logcat, видно в Output VS)
    ///   2. FileLogger (файлы Logs/Critical/*.log)
    /// 
    /// Использование:
    ///   AppLog.d("TAG", "message")           — DEBUG
    ///   AppLog.i("TAG", "message")           — INFO
    ///   AppLog.w("TAG", "message")           — WARNING
    ///   AppLog.w("TAG", "message", exception) — WARNING + exception
    ///   AppLog.e("TAG", "message")           — ERROR
    ///   AppLog.e("TAG", "message", exception) — ERROR + exception
    ///   
    /// Формат в файле: "2026-04-13 20:50:39.123 [TRACE] message"
    /// Chain (для имени файла): tag → lower + sanitize → "tag"
    /// </summary>
    internal static class AppLog
    {
        private const string DefaultChain = "app_log";

        internal static void d(string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] " + message);
            FileLogger.Trace(ResolveChain(tag), Normalize(message));
        }

        internal static void d(string chain, string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] " + message);
            FileLogger.Trace(ResolveChain(chain), Normalize(message));
        }

        internal static void i(string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] " + message);
            FileLogger.Trace(ResolveChain(tag), Normalize(message));
        }

        internal static void i(string chain, string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] " + message);
            FileLogger.Trace(ResolveChain(chain), Normalize(message));
        }

        internal static void w(string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] WARN: " + message);
            FileLogger.Warn(ResolveChain(tag), Normalize(message));
        }

        internal static void w(string chain, string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] WARN: " + message);
            FileLogger.Warn(ResolveChain(chain), Normalize(message));
        }

        internal static void w(string tag, string message, Exception error)
        {
            Debug.WriteLine("[" + tag + "] WARN: " + message + " " + error);
            FileLogger.Warn(ResolveChain(tag), Normalize(message));
            if (error != null)
            {
                FileLogger.Error(ResolveChain(tag), "warning throwable", error);
            }
        }

        internal static void w(string chain, string tag, string message, Exception error)
        {
            Debug.WriteLine("[" + tag + "] WARN: " + message + " " + error);
            FileLogger.Warn(ResolveChain(chain), Normalize(message));
            if (error != null)
            {
                FileLogger.Error(ResolveChain(chain), "warning throwable", error);
            }
        }

        internal static void e(string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] ERROR: " + message);
            FileLogger.Error(ResolveChain(tag), Normalize(message), null);
        }

        internal static void e(string chain, string tag, string message)
        {
            Debug.WriteLine("[" + tag + "] ERROR: " + message);
            FileLogger.Error(ResolveChain(chain), Normalize(message), null);
        }

        internal static void e(string tag, string message, Exception error)
        {
            Debug.WriteLine("[" + tag + "] ERROR: " + message + " " + error);
            FileLogger.Error(ResolveChain(tag), Normalize(message), error);
        }

        internal static void e(string chain, string tag, string message, Exception error)
        {
            Debug.WriteLine("[" + tag + "] ERROR: " + message + " " + error);
            FileLogger.Error(ResolveChain(chain), Normalize(message), error);
        }

        private static string ResolveChain(string tag)
        {
            if (string.IsNullOrEmpty(tag))
                return DefaultChain;
            return tag.Trim().ToLowerInvariant();
        }

        private static string Normalize(string message)
        {
            if (string.IsNullOrEmpty(message))
                return string.Empty;
            return message;
        }
    }
}