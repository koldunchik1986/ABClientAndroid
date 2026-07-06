using System;
using System.Globalization;
using System.IO;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;
using ANClient.ANForms;
using ANClient.ANProxy;
using ANClient.PostFilter;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace ANClient.MyGuamod
{
    internal static class AntiCaptchaManager
    {
        private const string Tag = "AntiCaptchaManager";
        private const string ApiCreateTask = "https://api.anti-captcha.com/createTask";
        private const string ApiGetTaskResult = "https://api.anti-captcha.com/getTaskResult";
        private const string CaptchaImageUrlPrefix = "http://www.neverlands.ru/modules/code/code.php?";
        private const int MaxPollCount = 120;
        private const int InitialPollDelayMs = 3000;
        private const int PollDelayMs = 1000;
        private const int CaptchaImageWaitTimeoutMs = 25000;
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

        private static readonly Regex TaskIdRegex = new Regex("\"taskId\"\\s*:\\s*(\\d+)", RegexOptions.Compiled);
        private static readonly Regex ErrorIdRegex = new Regex("\"errorId\"\\s*:\\s*(\\d+)", RegexOptions.Compiled);
        private static readonly Regex StatusRegex = new Regex("\"status\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static readonly Regex TextRegex = new Regex("\"text\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static readonly Regex ErrorCodeRegex = new Regex("\"errorCode\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static readonly Regex ErrorDescriptionRegex = new Regex("\"errorDescription\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static readonly Regex CodeRegex = new Regex("^\\d{5}$", RegexOptions.Compiled);
        private static bool busy;
        private static string lastFailedChallenge = string.Empty;
        private static string waitingImageChallenge = string.Empty;
        private static DateTime waitingImageSince = DateTime.MinValue;
        private static readonly object SubmittedCaptchaLock = new object();
        private static byte[] submittedCaptchaPng;
        private static string submittedCaptchaCode = string.Empty;
        private static string submittedCaptchaSource = string.Empty;
        private static double submittedCaptchaMinConfidence;
        private static DateTime submittedCaptchaAt = DateTime.MinValue;

        internal static bool Busy
        {
            get { return busy; }
        }

        internal static bool TrySolveCurrentCaptcha()
        {
            if (busy)
            {
                return true;
            }

            if (AppVars.Profile == null || !AppVars.Profile.AntiCaptchaEnabled)
            {
                return false;
            }

            var localOcrEnabled = LocalCaptchaSolver.IsEnabled();
            var externalCaptchaEnabled = IsExternalAntiCaptchaEnabled();
            if (!localOcrEnabled && !externalCaptchaEnabled)
            {
                return false;
            }

            var challenge = AppVars.FightLink;
            var codeAddress = AppVars.CodeAddress;
            if (string.IsNullOrEmpty(challenge) || challenge.IndexOf("????", StringComparison.Ordinal) == -1)
            {
                return false;
            }

            if (string.Equals(lastFailedChallenge, challenge, StringComparison.Ordinal))
            {
                return false;
            }

            if (AppVars.CodePng != null && AppVars.CodePng.Length > 0 && !AppVars.IsCodePngForAddress(codeAddress))
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE stale captcha image cleared before solve, expectedHash=" + AppVars.NormalizeCaptchaCodeAddress(codeAddress).GetHashCode().ToString(CultureInfo.InvariantCulture));
                AppVars.ClearCodePng();
            }

            if (AppVars.CodePng == null || AppVars.CodePng.Length == 0)
            {
                var preferBrowserImage = IsAlchemyCaptchaChallenge(challenge);
                if (preferBrowserImage && ShouldWaitForCaptchaImage(challenge, codeAddress, CaptchaImageWaitTimeoutMs))
                {
                    return true;
                }

                TryLoadCaptchaImageFromCodeAddress(challenge, codeAddress);
                if (AppVars.CodePng == null || AppVars.CodePng.Length == 0)
                {
                    if (ShouldWaitForCaptchaImage(challenge, codeAddress, CaptchaImageWaitTimeoutMs))
                    {
                        return true;
                    }

                    AppLog.w(Tag, "ANTI_CAPTCHA_TRACE captcha image wait timeout");
                    ResetCaptchaImageWait();
                    return false;
                }
            }

            ResetCaptchaImageWait();

            var imageBytes = new byte[AppVars.CodePng.Length];
            Buffer.BlockCopy(AppVars.CodePng, 0, imageBytes, 0, AppVars.CodePng.Length);
            busy = true;
            ThreadPool.QueueUserWorkItem(delegate { SolveWorker(challenge, codeAddress, imageBytes); });
            AppLog.i(Tag, "ANTI_CAPTCHA_TRACE started, bytes=" + imageBytes.Length);
            UpdateGuamodMessage("Anti-Captcha: отправка...");
            return true;
        }

        private static bool ShouldWaitForCaptchaImage(string challenge, string codeAddress, int timeoutMs)
        {
            if (string.IsNullOrEmpty(codeAddress))
            {
                return false;
            }

            if (!IsSameCaptchaContext(challenge, codeAddress))
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE captcha image wait cancelled: stale context");
                ResetCaptchaImageWait();
                return false;
            }

            if (!string.Equals(waitingImageChallenge, challenge, StringComparison.Ordinal))
            {
                waitingImageChallenge = challenge;
                waitingImageSince = DateTime.Now;
            }

            var elapsedMs = DateTime.Now.Subtract(waitingImageSince).TotalMilliseconds;
            if (elapsedMs >= timeoutMs)
            {
                return false;
            }

            AppLog.d(Tag, "ANTI_CAPTCHA_TRACE waiting captcha image, elapsedMs=" + ((int)elapsedMs).ToString(CultureInfo.InvariantCulture) + ", timeoutMs=" + timeoutMs.ToString(CultureInfo.InvariantCulture));
            UpdateGuamodMessage("Anti-Captcha: жду картинку...");
            return true;
        }

        private static void ResetCaptchaImageWait()
        {
            waitingImageChallenge = string.Empty;
            waitingImageSince = DateTime.MinValue;
        }

        private static bool IsAlchemyCaptchaChallenge(string challenge)
        {
            return !string.IsNullOrEmpty(challenge) &&
                   challenge.IndexOf("alchemy_ajax.php", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static bool IsFightCompletionCaptchaChallenge(string challenge)
        {
            return !string.IsNullOrEmpty(challenge) &&
                   challenge.IndexOf("main.php", StringComparison.OrdinalIgnoreCase) >= 0 &&
                   challenge.IndexOf("code=????", StringComparison.Ordinal) >= 0 &&
                   challenge.IndexOf("get_id=61", StringComparison.OrdinalIgnoreCase) >= 0 &&
                   challenge.IndexOf("act=7", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static bool IsLowConfidenceDiagnostic(string diagnostic)
        {
            return !string.IsNullOrEmpty(diagnostic) &&
                   diagnostic.StartsWith("low confidence", StringComparison.OrdinalIgnoreCase);
        }

        private static bool IsSameCaptchaContext(string challenge, string codeAddress)
        {
            return string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal) &&
                   AppVars.IsSameCaptchaCodeAddress(AppVars.CodeAddress, codeAddress);
        }

        private static void ClearCaptchaImageIfCurrent(string codeAddress)
        {
            if (AppVars.IsSameCaptchaCodeAddress(AppVars.CodeAddress, codeAddress))
            {
                AppVars.ClearCodePng();
            }
        }

        private static void TryLoadCaptchaImageFromCodeAddress(string challenge, string codeAddress)
        {
            if (string.IsNullOrEmpty(codeAddress) || AppVars.IsCodePngForAddress(codeAddress))
            {
                return;
            }

            if (!IsSameCaptchaContext(challenge, codeAddress))
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE captcha image load skipped: stale context");
                return;
            }

            try
            {
                var requestAddress = BuildCaptchaImageUrl(codeAddress);
                var request = (HttpWebRequest)WebRequest.Create(GameServerSelector.RouteUrlToCurrentServer(requestAddress));
                request.Method = "GET";
                request.UserAgent = BrowserUserAgent;
                request.Accept = "image/png,image/*,*/*";
                request.Timeout = 10000;
                request.ReadWriteTimeout = 10000;
                request.KeepAlive = false;
                if (!DirectGameRequestGuard.Prepare(request, Tag + ".TryLoadCaptchaImageFromCodeAddress"))
                {
                    return;
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
                        AppLog.w(Tag, "ANTI_CAPTCHA_TRACE captcha image http status=" + (int)response.StatusCode);
                        return;
                    }

                    using (var stream = response.GetResponseStream())
                    {
                        if (stream == null)
                        {
                            return;
                        }

                        using (var memory = new MemoryStream())
                        {
                            var buffer = new byte[4096];
                            int read;
                            while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                            {
                                memory.Write(buffer, 0, read);
                            }

                            if (memory.Length > 0)
                            {
                                if (!IsSameCaptchaContext(challenge, codeAddress))
                                {
                                    AppLog.w(Tag, "ANTI_CAPTCHA_TRACE stale captcha image ignored");
                                    return;
                                }

                                AppVars.AssignCodePng(memory.ToArray(), requestAddress);
                                AppLog.i(Tag, "ANTI_CAPTCHA_TRACE captcha image loaded, bytes=" + AppVars.CodePng.Length + ", challengeHash=" + challenge.GetHashCode().ToString(CultureInfo.InvariantCulture) + ", codeAddressHash=" + codeAddress.GetHashCode().ToString(CultureInfo.InvariantCulture));
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE captcha image load failed", ex);
            }
        }

        private static void SolveWorker(string challenge, string codeAddress, byte[] imageBytes)
        {
            try
            {
                if (LocalCaptchaSolver.IsEnabled())
                {
                    string localCode;
                    double localMinConfidence;
                    string localDiagnostic;
                    if (LocalCaptchaSolver.TrySolve(imageBytes, out localCode, out localMinConfidence, out localDiagnostic))
                    {
                        ApplySolvedCaptcha(challenge, codeAddress, localCode, "Local OCR", localMinConfidence, imageBytes);
                        return;
                    }

                    if (IsFightCompletionCaptchaChallenge(challenge) && IsLowConfidenceDiagnostic(localDiagnostic))
                    {
                        FailAndFallback(challenge, "local OCR failed: " + localDiagnostic);
                        return;
                    }

                    if (IsExternalAntiCaptchaEnabled())
                    {
                        AppLog.w(Tag, "LOCAL_OCR_TRACE fallback_external, diagnostic=" + TrimForLog(localDiagnostic));
                    }
                    else
                    {
                        FailAndFallback(challenge, "local OCR failed: " + localDiagnostic);
                        return;
                    }
                }

                if (!IsExternalAntiCaptchaEnabled())
                {
                    FailAndFallback(challenge, "external anti-captcha disabled");
                    return;
                }

                var taskId = CreateTask(imageBytes);
                if (taskId <= 0)
                {
                    FailAndFallback(challenge, "createTask failed");
                    return;
                }

                var text = WaitForResult(taskId);
                if (string.IsNullOrEmpty(text))
                {
                    FailAndFallback(challenge, "empty solution");
                    return;
                }

                if (!ApplySolvedCaptcha(challenge, codeAddress, text, "anti-captcha.com", 1d, imageBytes))
                {
                    FailAndFallback(challenge, "invalid solution");
                }
            }
            catch (Exception ex)
            {
                FailAndFallback(challenge, ex.Message);
            }
            finally
            {
                busy = false;
            }
        }

        private static string BuildCaptchaImageUrl(string codeAddress)
        {
            var value = (codeAddress ?? string.Empty).Trim();
            if (value.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                value.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                return value;
            }

            var token = AppVars.NormalizeCaptchaCodeAddress(value);
            return token.Length == 0 ? value : CaptchaImageUrlPrefix + token;
        }

        private static bool IsExternalAntiCaptchaEnabled()
        {
            return AppVars.Profile != null &&
                   AppVars.Profile.LocalCaptchaExternalFallbackEnabled &&
                   !string.IsNullOrEmpty(AppVars.Profile.AntiCaptchaApiKey);
        }

        private static bool ApplySolvedCaptcha(string challenge, string codeAddress, string code, string source, double minConfidence, byte[] imageBytes)
        {
            code = (code ?? string.Empty).Trim();
            source = string.IsNullOrEmpty(source) ? "anti-captcha" : source;
            if (!CodeRegex.IsMatch(code))
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE invalid solution ignored, source=" + source + ", textLen=" + code.Length.ToString(CultureInfo.InvariantCulture));
                return false;
            }

            if (!IsSameCaptchaContext(challenge, codeAddress))
            {
                ClearCaptchaImageIfCurrent(codeAddress);
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE stale solution ignored, source=" + source);
                return true;
            }

            AppVars.GuamodCode = code;
            RememberSubmittedCaptcha(imageBytes, code, source, minConfidence);
            AppVars.ClearCodePng();
            AppVars.FightLink = AppVars.FightLink.Replace("????", AppVars.GuamodCode);
            lastFailedChallenge = string.Empty;
            TrySubmitSolvedAlchemyLink();
            TrySubmitSolvedAutoboiFightLink();
            UpdateGuamodMessage(source + ": распознано " + AppVars.GuamodCode);
            UpdateTexLog(source + " код: " + AppVars.GuamodCode);
            AppLog.i(Tag, "ANTI_CAPTCHA_TRACE solved, source=" + source + ", textLen=" + AppVars.GuamodCode.Length.ToString(CultureInfo.InvariantCulture) + ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture));
            PostAntiCaptchaCodeSubmittedToChat(AppVars.GuamodCode, source);
            return true;
        }

        internal static bool SaveSubmittedCaptchaAfterWrongCode(string reason)
        {
            byte[] imageBytes;
            string code;
            string source;
            double minConfidence;
            DateTime submittedAt;
            lock (SubmittedCaptchaLock)
            {
                if (submittedCaptchaPng == null || submittedCaptchaPng.Length == 0)
                {
                    AppLog.w("auto_cut_trace", Tag, "wrong captcha image save skipped: no submitted captcha cache, reason=" + (reason ?? "unknown"));
                    return false;
                }

                imageBytes = new byte[submittedCaptchaPng.Length];
                Buffer.BlockCopy(submittedCaptchaPng, 0, imageBytes, 0, submittedCaptchaPng.Length);
                code = submittedCaptchaCode;
                source = submittedCaptchaSource;
                minConfidence = submittedCaptchaMinConfidence;
                submittedAt = submittedCaptchaAt;
                ClearSubmittedCaptchaLocked();
            }

            try
            {
                var captchaDir = Path.Combine(Application.StartupPath, "Logs");
                captchaDir = Path.Combine(captchaDir, "Captcha");
                if (!Directory.Exists(captchaDir))
                {
                    Directory.CreateDirectory(captchaDir);
                }

                var fileName = BuildSavedCaptchaFileName(code);
                var filePath = EnsureUniqueSavedCaptchaPath(Path.Combine(captchaDir, fileName));
                fileName = Path.GetFileName(filePath);
                File.WriteAllBytes(filePath, imageBytes);
                AppLog.w(
                    "auto_cut_trace",
                    Tag,
                    "wrong captcha image saved: file=" + fileName +
                    ", bytes=" + imageBytes.Length.ToString(CultureInfo.InvariantCulture) +
                    ", code=" + (code ?? string.Empty) +
                    ", source=" + (source ?? string.Empty) +
                    ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) +
                    ", submittedAt=" + submittedAt.ToString("HH:mm:ss.fff", CultureInfo.InvariantCulture) +
                    ", reason=" + (reason ?? "unknown"));
                return true;
            }
            catch (Exception ex)
            {
                AppLog.w("auto_cut_trace", Tag, "wrong captcha image save failed: " + ex.Message);
                return false;
            }
        }

        internal static void ForgetSubmittedCaptcha(string reason)
        {
            lock (SubmittedCaptchaLock)
            {
                if (submittedCaptchaPng == null || submittedCaptchaPng.Length == 0)
                {
                    return;
                }

                ClearSubmittedCaptchaLocked();
            }

            AppLog.d("auto_cut_trace", Tag, "submitted captcha cache cleared: reason=" + (reason ?? "unknown"));
        }

        private static void RememberSubmittedCaptcha(byte[] imageBytes, string code, string source, double minConfidence)
        {
            lock (SubmittedCaptchaLock)
            {
                if (imageBytes == null || imageBytes.Length == 0)
                {
                    ClearSubmittedCaptchaLocked();
                    return;
                }

                submittedCaptchaPng = new byte[imageBytes.Length];
                Buffer.BlockCopy(imageBytes, 0, submittedCaptchaPng, 0, imageBytes.Length);
                submittedCaptchaCode = code ?? string.Empty;
                submittedCaptchaSource = source ?? string.Empty;
                submittedCaptchaMinConfidence = minConfidence;
                submittedCaptchaAt = DateTime.Now;
            }
        }

        private static void ClearSubmittedCaptchaLocked()
        {
            submittedCaptchaPng = null;
            submittedCaptchaCode = string.Empty;
            submittedCaptchaSource = string.Empty;
            submittedCaptchaMinConfidence = 0d;
            submittedCaptchaAt = DateTime.MinValue;
        }

        private static string BuildSavedCaptchaFileName(string code)
        {
            var safeCode = Regex.Replace(code ?? string.Empty, "[^0-9]", string.Empty);
            if (safeCode.Length == 0)
            {
                safeCode = "unknown";
            }

            return safeCode + ".png";
        }

        private static string EnsureUniqueSavedCaptchaPath(string path)
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

        private static void TrySubmitSolvedAlchemyLink()
        {
            var mainForm = AppVars.MainForm;
            var link = AppVars.FightLink;
            if (mainForm == null ||
                string.IsNullOrEmpty(link) ||
                link.IndexOf("alchemy_ajax.php", StringComparison.OrdinalIgnoreCase) < 0 ||
                link.IndexOf("????", StringComparison.Ordinal) >= 0)
            {
                return;
            }

            try
            {
                mainForm.BeginInvoke(
                    new MethodInvoker(
                        delegate
                        {
                            if (mainForm.TrySubmitReadyAlchemyFightLink("anti_captcha_solved"))
                            {
                                AppLog.i("auto_cut_trace", Tag, "anti-captcha immediate alchemy submit executed");
                            }
                        }));
                AppLog.i("auto_cut_trace", Tag, "anti-captcha immediate alchemy submit queued");
            }
            catch (ObjectDisposedException ex)
            {
                AppLog.w("auto_cut_trace", Tag, "anti-captcha immediate alchemy submit failed", ex);
            }
            catch (InvalidOperationException ex)
            {
                AppLog.w("auto_cut_trace", Tag, "anti-captcha immediate alchemy submit failed", ex);
            }
        }

        private static void TrySubmitSolvedAutoboiFightLink()
        {
            var mainForm = AppVars.MainForm;
            var link = AppVars.FightLink;
            if (mainForm == null ||
                string.IsNullOrEmpty(link) ||
                link.Length <= 5 ||
                link.IndexOf("alchemy_ajax.php", StringComparison.OrdinalIgnoreCase) >= 0 ||
                link.IndexOf("????", StringComparison.Ordinal) >= 0)
            {
                return;
            }

            try
            {
                mainForm.BeginInvoke(
                    new MethodInvoker(
                        delegate
                        {
                            if (mainForm.TrySubmitReadyAutoboiFightLink("anti_captcha_solved"))
                            {
                                AppLog.i("LezFight", Tag, "anti-captcha immediate autoboi submit executed");
                            }
                        }));
                AppLog.i("LezFight", Tag, "anti-captcha immediate autoboi submit queued");
            }
            catch (ObjectDisposedException ex)
            {
                AppLog.w("LezFight", Tag, "anti-captcha immediate autoboi submit failed", ex);
            }
            catch (InvalidOperationException ex)
            {
                AppLog.w("LezFight", Tag, "anti-captcha immediate autoboi submit failed", ex);
            }
        }

        private static long CreateTask(byte[] imageBytes)
        {
            var taskJson = new JObject();
            taskJson["type"] = "ImageToTextTask";
            taskJson["body"] = Convert.ToBase64String(imageBytes).Replace("\r", string.Empty).Replace("\n", string.Empty);
            taskJson["phrase"] = AppVars.Profile.AntiCaptchaPhrase;
            taskJson["case"] = AppVars.Profile.AntiCaptchaCaseSensitive;
            taskJson["numeric"] = Clamp(AppVars.Profile.AntiCaptchaNumeric, 0, 2);
            taskJson["math"] = Clamp(AppVars.Profile.AntiCaptchaMath, 0, 1);
            taskJson["minLength"] = Clamp(AppVars.Profile.AntiCaptchaMinLength, 0, 20);
            taskJson["maxLength"] = Clamp(AppVars.Profile.AntiCaptchaMaxLength, 0, 20);
            taskJson["languagePool"] = NormalizeLanguagePool(AppVars.Profile.AntiCaptchaLanguagePool);

            var requestJson = new JObject();
            requestJson["clientKey"] = AppVars.Profile.AntiCaptchaApiKey;
            requestJson["softId"] = 0;
            requestJson["task"] = taskJson;

            AppLog.d(Tag, "ANTI_CAPTCHA_TRACE createTask request=" + SanitizeJsonForLog(requestJson));
            var response = PostJson(ApiCreateTask, JsonConvert.SerializeObject(requestJson, Formatting.Indented));
            if (ReadErrorId(response) != 0)
            {
                LogApiError("createTask", response);
                return 0;
            }

            long taskId;
            if (!long.TryParse(ReadRegex(TaskIdRegex, response), NumberStyles.Integer, CultureInfo.InvariantCulture, out taskId))
            {
                return 0;
            }

            return taskId;
        }

        private static string WaitForResult(long taskId)
        {
            for (var attempt = 0; attempt < MaxPollCount; attempt++)
            {
                Thread.Sleep(attempt == 0 ? InitialPollDelayMs : PollDelayMs);

                var requestJson = new JObject();
                requestJson["clientKey"] = AppVars.Profile.AntiCaptchaApiKey;
                requestJson["taskId"] = taskId;
                var response = PostJson(ApiGetTaskResult, JsonConvert.SerializeObject(requestJson, Formatting.Indented));
                if (ReadErrorId(response) != 0)
                {
                    LogApiError("getTaskResult", response);
                    return string.Empty;
                }

                var status = ReadRegex(StatusRegex, response);
                if (string.Equals(status, "processing", StringComparison.OrdinalIgnoreCase))
                {
                    AppLog.d(Tag, "ANTI_CAPTCHA_TRACE processing, task=" + taskId + ", attempt=" + attempt);
                    continue;
                }

                if (string.Equals(status, "ready", StringComparison.OrdinalIgnoreCase))
                {
                    return JsonUnescape(ReadRegex(TextRegex, response));
                }

                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE unexpected status=" + status);
                return string.Empty;
            }

            AppLog.w(Tag, "ANTI_CAPTCHA_TRACE timeout, task=" + taskId);
            return string.Empty;
        }

        private static string PostJson(string url, string json)
        {
            EnsureSecurityProtocol();
            var payload = Encoding.UTF8.GetBytes(json);
            var request = (HttpWebRequest)WebRequest.Create(url);
            request.Method = "POST";
            request.ContentType = "application/json";
            request.UserAgent = BrowserUserAgent;
            request.Timeout = 20000;
            request.ReadWriteTimeout = 20000;
            request.ContentLength = payload.Length;
            request.ServicePoint.Expect100Continue = false;
            using (var stream = request.GetRequestStream())
            {
                stream.Write(payload, 0, payload.Length);
            }

            try
            {
                using (var response = (HttpWebResponse)request.GetResponse())
                using (var stream = response.GetResponseStream())
                using (var reader = new StreamReader(stream, Encoding.UTF8))
                {
                    return reader.ReadToEnd();
                }
            }
            catch (WebException ex)
            {
                var response = ex.Response as HttpWebResponse;
                if (response == null)
                {
                    throw;
                }

                using (response)
                using (var stream = response.GetResponseStream())
                using (var reader = new StreamReader(stream, Encoding.UTF8))
                {
                    var text = reader.ReadToEnd();
                    AppLog.w(Tag, "ANTI_CAPTCHA_TRACE http status=" + (int)response.StatusCode + " raw=" + TrimForLog(text));
                    return text;
                }
            }
        }

        private static void FailAndFallback(string challenge, string message)
        {
            lastFailedChallenge = challenge;
            AppLog.w(Tag, "ANTI_CAPTCHA_TRACE failed: " + message);

            if (TryRefreshFightCaptchaAfterLocalOcrFailure(challenge, message))
            {
                return;
            }

            if (TryScheduleAlchemyRetryAfterLocalOcrFailure(challenge, message))
            {
                return;
            }

            if (AppVars.Profile.DoGuamod && string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal) && AppVars.CodePng != null)
            {
                AppLog.i(Tag, "LOCAL_OCR_TRACE fallback_neuro");
                UpdateGuamodMessage("Anti-Captcha: ошибка, запускаю гуамод");
                Recognizer.Perform();
                return;
            }

            ForgetSubmittedCaptcha("anti_captcha_failed");
            UpdateGuamodMessage("Anti-Captcha: ошибка, ручной ввод отключён");
        }

        private static bool TryRefreshFightCaptchaAfterLocalOcrFailure(string challenge, string message)
        {
            if (!IsFightCompletionCaptchaChallenge(challenge) ||
                string.IsNullOrEmpty(message) ||
                !message.StartsWith("local OCR failed:", StringComparison.Ordinal) ||
                !string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal))
            {
                return false;
            }

            ResetCaptchaImageWait();
            AppVars.ClearCodePng();
            ForgetSubmittedCaptcha("fight_local_ocr_failed");
            UpdateGuamodMessage("Anti-Captcha: OCR не уверен, обновляю капчу боя");
            AppLog.w("LezFight", Tag, "fight captcha refresh scheduled after local OCR failure: " + TrimForLog(message));
            RequestMainPhpReload("fight_captcha_local_ocr_failed");
            return true;
        }

        private static void RequestMainPhpReload(string reason)
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new ReloadMainPhpInvokeDelegate(AppVars.MainForm.ReloadMainPhpInvoke),
                        new object[] { });
                    AppLog.i("LezFight", Tag, "main.php reload queued: reason=" + (reason ?? "unknown"));
                }
            }
            catch (InvalidOperationException ex)
            {
                AppLog.w("LezFight", Tag, "main.php reload queue failed", ex);
            }
        }

        private static bool TryScheduleAlchemyRetryAfterLocalOcrFailure(string challenge, string message)
        {
            if (!IsAlchemyCaptchaChallenge(challenge) ||
                string.IsNullOrEmpty(message) ||
                !message.StartsWith("local OCR failed:", StringComparison.Ordinal) ||
                !string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal))
            {
                return false;
            }

            if (!Filter.CancelPendingAlchemyCut("anti_captcha_failed:local_ocr", true))
            {
                return false;
            }

            lastFailedChallenge = string.Empty;
            ForgetSubmittedCaptcha("local_ocr_failed");
            UpdateGuamodMessage("Anti-Captcha: OCR не уверен, жду повторный огляд");
            AppLog.w("auto_cut_trace", Tag, "anti-captcha alchemy retry scheduled after local OCR failure: " + TrimForLog(message));
            return true;
        }

        private static void EnsureSecurityProtocol()
        {
            try
            {
                const SecurityProtocolType tls11 = (SecurityProtocolType)768;
                const SecurityProtocolType tls12 = (SecurityProtocolType)3072;
                ServicePointManager.SecurityProtocol = ServicePointManager.SecurityProtocol | SecurityProtocolType.Tls | tls11 | tls12;
            }
            catch (NotSupportedException ex)
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE tls12 unavailable", ex);
            }
        }

        private static void UpdateGuamodMessage(string message)
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(new UpdateGuamodMessageDelegate(AppVars.MainForm.UpdateGuamodMessage), new object[] { message });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static void UpdateTexLog(string message)
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(new UpdateTexLogDelegate(AppVars.MainForm.UpdateTexLog), new object[] { message });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static void PostAntiCaptchaCodeSubmittedToChat(string code, string source)
        {
            try
            {
                var safeCode = EscapeHtmlText((code ?? string.Empty).Trim());
                var safeSource = EscapeHtmlText(source ?? "anti-captcha");
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.WriteChatMsgSafe(
                        "<font color=#008000>[Анти-Captcha][" +
                        safeSource +
                        "]: ответ '" +
                        safeCode +
                        "' - код отправлен.</font>");
                }

                AppLog.i(Tag, "ANTI_CAPTCHA_TRACE chat notification posted, codeLen=" + safeCode.Length);
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static string EscapeHtmlText(string value)
        {
            return (value ?? string.Empty)
                .Replace("&", "&amp;")
                .Replace("<", "&lt;")
                .Replace(">", "&gt;")
                .Replace("\"", "&quot;")
                .Replace("'", "&#39;");
        }

        private static int ReadErrorId(string response)
        {
            int errorId;
            return int.TryParse(ReadRegex(ErrorIdRegex, response), NumberStyles.Integer, CultureInfo.InvariantCulture, out errorId) ? errorId : -1;
        }

        private static string ReadRegex(Regex regex, string text)
        {
            if (text == null)
            {
                return string.Empty;
            }

            var match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : string.Empty;
        }

        private static string JsonUnescape(string value)
        {
            return (value ?? string.Empty).Replace("\\\"", "\"").Replace("\\/", "/").Replace("\\n", "\n").Replace("\\r", "\r").Replace("\\\\", "\\");
        }

        private static int Clamp(int value, int min, int max)
        {
            return Math.Max(min, Math.Min(max, value));
        }

        private static string NormalizeLanguagePool(string value)
        {
            return string.Equals(value, "rn", StringComparison.OrdinalIgnoreCase) ? "rn" : "en";
        }

        private static void LogApiError(string stage, string response)
        {
            AppLog.w(
                Tag,
                "ANTI_CAPTCHA_TRACE " + stage +
                " error=" + ReadRegex(ErrorCodeRegex, response) +
                " description=" + JsonUnescape(ReadRegex(ErrorDescriptionRegex, response)) +
                " raw=" + TrimForLog(response));
        }

        private static string SanitizeJsonForLog(JObject json)
        {
            var clone = (JObject)json.DeepClone();
            clone["clientKey"] = "***";
            var task = clone["task"] as JObject;
            if (task != null && task["body"] != null)
            {
                task["body"] = "base64_len=" + Convert.ToString(task["body"]).Length.ToString(CultureInfo.InvariantCulture);
            }

            return JsonConvert.SerializeObject(clone, Formatting.None);
        }

        private static string TrimForLog(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                return string.Empty;
            }

            text = text.Replace("\r", string.Empty).Replace("\n", string.Empty);
            return text.Length <= 600 ? text : text.Substring(0, 600);
        }
    }
}
