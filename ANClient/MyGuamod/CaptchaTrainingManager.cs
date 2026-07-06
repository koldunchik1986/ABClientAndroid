using System;
using System.Globalization;
using System.IO;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;
using ANClient.ANProxy;

namespace ANClient.MyGuamod
{
    internal static class CaptchaTrainingManager
    {
        private const string Tag = "CaptchaTrainingManager";
        private const string Chain = "captcha_training";
        private const string LazurnyRegNum = "8-141";
        private const string StoreAjaxUrl = "http://www.neverlands.ru/gameplay/ajax/store_ajax.php";
        private const string CaptchaImageUrlPrefix = "http://www.neverlands.ru/modules/code/code.php?";
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        private const int RequestTimeoutMs = 15000;
        private const int LoopDelayMs = 3000;
        private const int NextClickMinDelayMs = 2000;
        private const int NextClickMaxDelayMs = 2000;

        private static readonly object SyncRoot = new object();
        private static readonly Random RandomSource = new Random();
        private static readonly Regex StoreTokenRegex = new Regex("^STORE@([a-fA-F0-9]{16,64})@", RegexOptions.Compiled | RegexOptions.IgnoreCase);
        private static readonly Regex[] VCodeRegexes = new[]
        {
            new Regex("name\\s*=\\s*[\\\"']?vcode[\\\"']?[^>]*value\\s*=\\s*[\\\"']?([a-fA-F0-9]{16,64})", RegexOptions.Compiled | RegexOptions.IgnoreCase),
            new Regex("value\\s*=\\s*[\\\"']?([a-fA-F0-9]{16,64})[\\\"']?[^>]*name\\s*=\\s*[\\\"']?vcode", RegexOptions.Compiled | RegexOptions.IgnoreCase),
            new Regex("[?&]vcode=([a-fA-F0-9]{16,64})", RegexOptions.Compiled | RegexOptions.IgnoreCase),
            new Regex("\\bvcode\\s*[:=]\\s*[\\\"']([a-fA-F0-9]{16,64})[\\\"']", RegexOptions.Compiled | RegexOptions.IgnoreCase)
        };

        private static bool enabled;
        private static bool workerRunning;
        private static bool processingImage;
        private static string lastImageToken = string.Empty;
        private static DateTime lastImageAt = DateTime.MinValue;
        private static DateTime nextLocationWarnAt = DateTime.MinValue;
        private static DateTime nextVCodeWarnAt = DateTime.MinValue;
        private static DateTime nextReloadAt = DateTime.MinValue;

        internal static bool Enabled
        {
            get
            {
                lock (SyncRoot)
                {
                    return enabled;
                }
            }
        }

        internal static void Start(string source)
        {
            lock (SyncRoot)
            {
                if (enabled)
                {
                    return;
                }

                enabled = true;
                AppLog.i(Chain, Tag, "CAPTCHA_TRAINING enabled: source=" + Safe(source));
            }

            ScheduleResourcesClick("start", 250);
        }

        internal static void Stop(string source)
        {
            lock (SyncRoot)
            {
                if (!enabled)
                {
                    return;
                }

                enabled = false;
                processingImage = false;
                AppLog.i(Chain, Tag, "CAPTCHA_TRAINING disabled: source=" + Safe(source));
            }
        }

        internal static void OnCaptchaImageIntercepted(string codeAddress, byte[] imageBytes)
        {
            if (!IsEnabledSnapshot())
            {
                return;
            }

            if (!IsAtTrainingLocation())
            {
                if (ConsumeThrottle(ref nextLocationWarnAt, 30))
                {
                    AppLog.w(
                        Chain,
                        Tag,
                        "CAPTCHA_TRAINING image ignored: location=" + Safe(AppVars.LocationReal) +
                        ", expected=" + LazurnyRegNum +
                        ", mapLocation=" + (AppVars.Profile == null ? string.Empty : Safe(AppVars.Profile.MapLocation)));
                }

                return;
            }

            if (imageBytes == null || imageBytes.Length == 0)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING image ignored: empty image");
                return;
            }

            var token = AppVars.NormalizeCaptchaCodeAddress(codeAddress);
            if (string.IsNullOrEmpty(token))
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING image ignored: empty token");
                return;
            }

            byte[] copy;
            lock (SyncRoot)
            {
                if (processingImage)
                {
                    AppLog.d(Chain, Tag, "CAPTCHA_TRAINING image ignored: OCR already running, tokenHash=" + token.GetHashCode().ToString(CultureInfo.InvariantCulture));
                    return;
                }

                if (string.Equals(lastImageToken, token, StringComparison.Ordinal) && DateTime.Now.Subtract(lastImageAt).TotalSeconds < 30d)
                {
                    AppLog.d(Chain, Tag, "CAPTCHA_TRAINING image ignored: duplicate tokenHash=" + token.GetHashCode().ToString(CultureInfo.InvariantCulture));
                    return;
                }

                copy = new byte[imageBytes.Length];
                Buffer.BlockCopy(imageBytes, 0, copy, 0, imageBytes.Length);
                processingImage = true;
                lastImageToken = token;
                lastImageAt = DateTime.Now;
            }

            AppLog.i(
                Chain,
                Tag,
                "CAPTCHA_TRAINING image captured: bytes=" + copy.Length.ToString(CultureInfo.InvariantCulture) +
                ", tokenHash=" + token.GetHashCode().ToString(CultureInfo.InvariantCulture));
            ThreadPool.QueueUserWorkItem(delegate { ProcessCapturedImage(copy, token); });
        }

        private static void ProcessCapturedImage(byte[] captchaBytes, string token)
        {
            try
            {
                string code;
                double minConfidence;
                string diagnostic;
                var recognized = LocalCaptchaSolver.TryRecognizeForTraining(captchaBytes, out code, out minConfidence, out diagnostic);
                var threshold = LocalCaptchaSolver.MinConfidenceThreshold;

                if (recognized && minConfidence >= threshold)
                {
                    SaveCaptcha(captchaBytes, "True", BuildTrueFileName(code));
                    AppLog.i(
                        Chain,
                        Tag,
                        "CAPTCHA_TRAINING saved true: textLen=" + (code ?? string.Empty).Length.ToString(CultureInfo.InvariantCulture) +
                        ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) +
                        ", threshold=" + threshold.ToString("0.0000", CultureInfo.InvariantCulture) +
                        ", tokenHash=" + (token ?? string.Empty).GetHashCode().ToString(CultureInfo.InvariantCulture));
                }
                else
                {
                    SaveCaptcha(captchaBytes, "Train", BuildTrainFileName(recognized ? code : null));
                    AppLog.i(
                        Chain,
                        Tag,
                        "CAPTCHA_TRAINING saved train: recognized=" + recognized +
                        ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) +
                        ", threshold=" + threshold.ToString("0.0000", CultureInfo.InvariantCulture) +
                        ", diagnostic=" + TrimForLog(diagnostic) +
                        ", tokenHash=" + (token ?? string.Empty).GetHashCode().ToString(CultureInfo.InvariantCulture));
                }
            }
            catch (Exception ex)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING OCR processing failed", ex);
            }
            finally
            {
                lock (SyncRoot)
                {
                    processingImage = false;
                }
            }

            ScheduleResourcesClick("ocr_result", NextClickDelayMs());
        }

        private static int NextClickDelayMs()
        {
            lock (RandomSource)
            {
                return RandomSource.Next(NextClickMinDelayMs, NextClickMaxDelayMs + 1);
            }
        }

        private static void ScheduleResourcesClick(string source, int delayMs)
        {
            ThreadPool.QueueUserWorkItem(
                delegate
                {
                    if (delayMs > 0)
                    {
                        Thread.Sleep(delayMs);
                    }

                    if (!IsEnabledSnapshot())
                    {
                        return;
                    }

                    RequestResourcesClick(source, delayMs);
                });
        }

        private static void QueueWorkerLocked()
        {
            if (workerRunning)
            {
                return;
            }

            workerRunning = true;
            ThreadPool.QueueUserWorkItem(WorkerLoop);
        }

        private static void WorkerLoop(object state)
        {
            try
            {
                while (IsEnabledSnapshot())
                {
                    try
                    {
                        RunOnce();
                    }
                    catch (Exception ex)
                    {
                        AppLog.w(Chain, Tag, "CAPTCHA_TRAINING iteration failed", ex);
                    }

                    if (IsEnabledSnapshot())
                    {
                        Thread.Sleep(LoopDelayMs);
                    }
                }
            }
            finally
            {
                lock (SyncRoot)
                {
                    workerRunning = false;
                    if (enabled)
                    {
                        QueueWorkerLocked();
                    }
                }
            }
        }

        private static void RunOnce()
        {
            if (!IsAtTrainingLocation())
            {
                if (ConsumeThrottle(ref nextLocationWarnAt, 30))
                {
                    AppLog.w(
                        Chain,
                        Tag,
                        "CAPTCHA_TRAINING skipped: location=" + Safe(AppVars.LocationReal) +
                        ", expected=" + LazurnyRegNum +
                        ", mapLocation=" + (AppVars.Profile == null ? string.Empty : Safe(AppVars.Profile.MapLocation)));
                }

                return;
            }

            var vcode = ResolveVCode();
            if (string.IsNullOrEmpty(vcode))
            {
                if (ConsumeThrottle(ref nextVCodeWarnAt, 15))
                {
                    AppLog.w(Chain, Tag, "CAPTCHA_TRAINING skipped: vcode not found in current main.php context");
                }

                RequestMainReload("captcha_training_no_vcode");
                return;
            }

            var storeText = FetchText(BuildStoreAjaxUrl(vcode), Tag + ".StoreAjax");
            var token = ParseStoreCaptchaToken(storeText);
            if (string.IsNullOrEmpty(token))
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING skipped: STORE captcha token not found, responseTail=" + TrimForLog(storeText));
                return;
            }

            var captchaBytes = FetchBytes(CaptchaImageUrlPrefix + token, Tag + ".CaptchaImage");
            if (captchaBytes == null || captchaBytes.Length == 0)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING skipped: empty captcha image, tokenHash=" + token.GetHashCode().ToString(CultureInfo.InvariantCulture));
                return;
            }

            string code;
            double minConfidence;
            string diagnostic;
            var recognized = LocalCaptchaSolver.TryRecognizeForTraining(captchaBytes, out code, out minConfidence, out diagnostic);
            var threshold = LocalCaptchaSolver.MinConfidenceThreshold;

            if (recognized && minConfidence >= threshold)
            {
                SaveCaptcha(captchaBytes, "True", BuildTrueFileName(code));
                AppLog.i(
                    Chain,
                    Tag,
                    "CAPTCHA_TRAINING saved true: textLen=" + (code ?? string.Empty).Length.ToString(CultureInfo.InvariantCulture) +
                    ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) +
                    ", threshold=" + threshold.ToString("0.0000", CultureInfo.InvariantCulture));
                return;
            }

            SaveCaptcha(captchaBytes, "Train", BuildTrainFileName(recognized ? code : null));
            AppLog.i(
                Chain,
                Tag,
                "CAPTCHA_TRAINING saved train: recognized=" + recognized +
                ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) +
                ", threshold=" + threshold.ToString("0.0000", CultureInfo.InvariantCulture) +
                ", diagnostic=" + TrimForLog(diagnostic));
        }

        private static bool IsEnabledSnapshot()
        {
            lock (SyncRoot)
            {
                return enabled;
            }
        }

        private static bool IsAtTrainingLocation()
        {
            var profileLocation = AppVars.Profile == null ? string.Empty : AppVars.Profile.MapLocation;
            return string.Equals(AppVars.LocationReal ?? string.Empty, LazurnyRegNum, StringComparison.OrdinalIgnoreCase) ||
                   string.Equals(profileLocation ?? string.Empty, LazurnyRegNum, StringComparison.OrdinalIgnoreCase);
        }

        private static string ResolveVCode()
        {
            var html = AppVars.ContentMainPhp ?? string.Empty;
            if (html.Length == 0)
            {
                return string.Empty;
            }

            for (var i = 0; i < VCodeRegexes.Length; i++)
            {
                var match = VCodeRegexes[i].Match(html);
                if (match.Success && match.Groups.Count > 1)
                {
                    return match.Groups[1].Value.Trim();
                }
            }

            const string legacyMarker = "=vcode value=";
            var pos = html.IndexOf(legacyMarker, StringComparison.OrdinalIgnoreCase);
            if (pos >= 0)
            {
                var start = pos + legacyMarker.Length;
                var end = html.IndexOf('>', start);
                var raw = end > start ? html.Substring(start, end - start) : html.Substring(start);
                raw = raw.Trim().Trim('"', '\'');
                var match = Regex.Match(raw, "[a-fA-F0-9]{16,64}");
                if (match.Success)
                {
                    return match.Value;
                }
            }

            return string.Empty;
        }

        private static string BuildStoreAjaxUrl(string vcode)
        {
            var random = NextRandom().ToString("0.################", CultureInfo.InvariantCulture);
            return StoreAjaxUrl + "?vcode=" + Uri.EscapeDataString(vcode) + "&t=2&r=" + random;
        }

        private static double NextRandom()
        {
            lock (RandomSource)
            {
                return RandomSource.NextDouble();
            }
        }

        private static string ParseStoreCaptchaToken(string storeText)
        {
            var match = StoreTokenRegex.Match(storeText ?? string.Empty);
            return match.Success && match.Groups.Count > 1 ? match.Groups[1].Value.Trim() : string.Empty;
        }

        private static string FetchText(string rawUrl, string source)
        {
            var bytes = FetchBytes(rawUrl, source);
            return bytes == null || bytes.Length == 0 ? string.Empty : AppVars.Codepage.GetString(bytes);
        }

        private static byte[] FetchBytes(string rawUrl, string source)
        {
            var requestUrl = GameServerSelector.RouteUrlToCurrentServer(rawUrl);
            var request = (HttpWebRequest)WebRequest.Create(requestUrl);
            request.Method = "GET";
            request.UserAgent = BrowserUserAgent;
            request.Accept = rawUrl.IndexOf("code.php?", StringComparison.OrdinalIgnoreCase) >= 0 ? "image/png,image/*,*/*" : "*/*";
            request.Referer = GameServerSelector.RouteUrlToCurrentServer("http://www.neverlands.ru/main.php");
            request.Timeout = RequestTimeoutMs;
            request.ReadWriteTimeout = RequestTimeoutMs;
            request.KeepAlive = false;
            request.AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate;
            request.Headers[HttpRequestHeader.CacheControl] = "no-cache";

            if (!DirectGameRequestGuard.Prepare(request, source))
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING request blocked by guard: source=" + Safe(source));
                return null;
            }

            var cookies = CookiesManager.Obtain("www.neverlands.ru");
            if (!string.IsNullOrEmpty(cookies))
            {
                request.Headers.Add("Cookie", cookies);
            }

            using (var response = (HttpWebResponse)request.GetResponse())
            {
                if (response.StatusCode != HttpStatusCode.OK)
                {
                    AppLog.w(Chain, Tag, "CAPTCHA_TRAINING http status=" + ((int)response.StatusCode).ToString(CultureInfo.InvariantCulture) + ", source=" + Safe(source));
                    return null;
                }

                using (var stream = response.GetResponseStream())
                using (var memory = new MemoryStream())
                {
                    if (stream == null)
                    {
                        return null;
                    }

                    var buffer = new byte[8192];
                    int read;
                    while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        memory.Write(buffer, 0, read);
                    }

                    return memory.ToArray();
                }
            }
        }

        private static void SaveCaptcha(byte[] bytes, string subdir, string fileName)
        {
            var dir = Path.Combine(Application.StartupPath, "Logs");
            dir = Path.Combine(dir, "Captcha");
            dir = Path.Combine(dir, subdir);
            if (!Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            var path = EnsureUniquePath(Path.Combine(dir, fileName));
            File.WriteAllBytes(path, bytes);
        }

        private static string BuildTrainFileName(string code)
        {
            var safeCode = NormalizeCaptchaCode(code);
            if (safeCode.Length > 0)
            {
                return safeCode + ".png";
            }

            return DateTime.Now.ToString("HHmmss", CultureInfo.InvariantCulture) + ".png";
        }

        private static string BuildTrueFileName(string code)
        {
            var safeCode = NormalizeCaptchaCode(code);
            if (safeCode.Length == 0)
            {
                safeCode = "unknown";
            }

            return safeCode + ".png";
        }

        private static string NormalizeCaptchaCode(string code)
        {
            return Regex.Replace(code ?? string.Empty, "[^0-9]", string.Empty);
        }

        private static string EnsureUniquePath(string path)
        {
            if (!File.Exists(path))
            {
                return path;
            }

            var dir = Path.GetDirectoryName(path) ?? string.Empty;
            var name = Path.GetFileNameWithoutExtension(path);
            var ext = Path.GetExtension(path);
            for (var i = 1; i < 1000; i++)
            {
                var candidate = Path.Combine(dir, name + "_" + i.ToString("000", CultureInfo.InvariantCulture) + ext);
                if (!File.Exists(candidate))
                {
                    return candidate;
                }
            }

            return Path.Combine(dir, name + "_" + DateTime.Now.Ticks.ToString(CultureInfo.InvariantCulture) + ext);
        }

        private static void RequestMainReload(string reason)
        {
            lock (SyncRoot)
            {
                if (DateTime.Now < nextReloadAt)
                {
                    return;
                }

                nextReloadAt = DateTime.Now.AddSeconds(30);
            }

            var mainForm = AppVars.MainForm;
            if (mainForm == null)
            {
                return;
            }

            try
            {
                mainForm.BeginInvoke(
                    new MethodInvoker(
                        delegate
                        {
                            mainForm.ReloadMainPhpInvoke();
                        }));
                AppLog.i(Chain, Tag, "CAPTCHA_TRAINING main.php reload queued: reason=" + Safe(reason));
            }
            catch (ObjectDisposedException ex)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING main.php reload queue failed", ex);
            }
            catch (InvalidOperationException ex)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING main.php reload queue failed", ex);
            }
        }

        private static void RequestResourcesClick(string reason, int delayMs)
        {
            var mainForm = AppVars.MainForm;
            if (mainForm == null)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING resources click skipped: main form missing, reason=" + Safe(reason));
                return;
            }

            try
            {
                mainForm.BeginInvoke(
                    new MethodInvoker(
                        delegate
                        {
                            mainForm.TryCaptchaTrainingClickResources(reason);
                        }));
                AppLog.i(
                    Chain,
                    Tag,
                    "CAPTCHA_TRAINING resources click queued: reason=" + Safe(reason) +
                    ", delayMs=" + delayMs.ToString(CultureInfo.InvariantCulture));
            }
            catch (ObjectDisposedException ex)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING resources click queue failed", ex);
            }
            catch (InvalidOperationException ex)
            {
                AppLog.w(Chain, Tag, "CAPTCHA_TRAINING resources click queue failed", ex);
            }
        }

        private static bool ConsumeThrottle(ref DateTime nextAt, int seconds)
        {
            lock (SyncRoot)
            {
                var now = DateTime.Now;
                if (now < nextAt)
                {
                    return false;
                }

                nextAt = now.AddSeconds(seconds);
                return true;
            }
        }

        private static string Safe(string value)
        {
            return string.IsNullOrEmpty(value) ? "unknown" : value;
        }

        private static string TrimForLog(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                return string.Empty;
            }

            text = text.Replace('\r', ' ').Replace('\n', ' ').Trim();
            return text.Length <= 180 ? text : text.Substring(text.Length - 180);
        }
    }
}
