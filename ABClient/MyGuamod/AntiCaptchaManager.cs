using System;
using System.Globalization;
using System.IO;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;
using ABClient.ABForms;
using ABClient.ABProxy;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace ABClient.MyGuamod
{
    internal static class AntiCaptchaManager
    {
        private const string Tag = "AntiCaptchaManager";
        private const string ApiCreateTask = "https://api.anti-captcha.com/createTask";
        private const string ApiGetTaskResult = "https://api.anti-captcha.com/getTaskResult";
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
        private static bool busy;
        private static string lastFailedChallenge = string.Empty;
        private static string waitingImageChallenge = string.Empty;
        private static DateTime waitingImageSince = DateTime.MinValue;

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

            if (string.IsNullOrEmpty(AppVars.Profile.AntiCaptchaApiKey))
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

            if (AppVars.CodePng == null || AppVars.CodePng.Length == 0)
            {
                TryLoadCaptchaImageFromCodeAddress(challenge, codeAddress);
                if (AppVars.CodePng == null || AppVars.CodePng.Length == 0)
                {
                    if (ShouldWaitForCaptchaImage(challenge, codeAddress))
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

        private static bool ShouldWaitForCaptchaImage(string challenge, string codeAddress)
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
            if (elapsedMs >= CaptchaImageWaitTimeoutMs)
            {
                return false;
            }

            AppLog.d(Tag, "ANTI_CAPTCHA_TRACE waiting captcha image, elapsedMs=" + ((int)elapsedMs).ToString(CultureInfo.InvariantCulture));
            UpdateGuamodMessage("Anti-Captcha: жду картинку...");
            return true;
        }

        private static void ResetCaptchaImageWait()
        {
            waitingImageChallenge = string.Empty;
            waitingImageSince = DateTime.MinValue;
        }

        private static bool IsSameCaptchaContext(string challenge, string codeAddress)
        {
            return string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal) &&
                   string.Equals(AppVars.CodeAddress ?? string.Empty, codeAddress ?? string.Empty, StringComparison.Ordinal);
        }

        private static void ClearCaptchaImageIfCurrent(string codeAddress)
        {
            if (string.Equals(AppVars.CodeAddress ?? string.Empty, codeAddress ?? string.Empty, StringComparison.Ordinal))
            {
                AppVars.CodePng = null;
            }
        }

        private static void TryLoadCaptchaImageFromCodeAddress(string challenge, string codeAddress)
        {
            if (string.IsNullOrEmpty(codeAddress) || AppVars.CodePng != null && AppVars.CodePng.Length > 0)
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
                var request = (HttpWebRequest)WebRequest.Create(codeAddress);
                request.Method = "GET";
                request.UserAgent = BrowserUserAgent;
                request.Accept = "image/png,image/*,*/*";
                request.Timeout = 10000;
                request.ReadWriteTimeout = 10000;
                request.KeepAlive = false;

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

                                AppVars.CodePng = memory.ToArray();
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

                if (!IsSameCaptchaContext(challenge, codeAddress))
                {
                    ClearCaptchaImageIfCurrent(codeAddress);
                    AppLog.w(Tag, "ANTI_CAPTCHA_TRACE stale solution ignored");
                    return;
                }

                AppVars.GuamodCode = text.Trim();
                AppVars.CodePng = null;
                AppVars.FightLink = AppVars.FightLink.Replace("????", AppVars.GuamodCode);
                lastFailedChallenge = string.Empty;
                TrySubmitSolvedAlchemyLink();
                TrySubmitSolvedAutoboiFightLink();
                UpdateGuamodMessage("Anti-Captcha: распознано " + AppVars.GuamodCode);
                UpdateTexLog("Anti-Captcha код: " + AppVars.GuamodCode);
                AppLog.i(Tag, "ANTI_CAPTCHA_TRACE solved, textLen=" + AppVars.GuamodCode.Length);
                PostAntiCaptchaCodeSubmittedToChat(AppVars.GuamodCode);
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
            if (AppVars.Profile.DoGuamod && string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal) && AppVars.CodePng != null)
            {
                UpdateGuamodMessage("Anti-Captcha: ошибка, запускаю гуамод");
                Recognizer.Perform();
                return;
            }

            UpdateGuamodMessage("Anti-Captcha: ошибка, нужен ручной ввод");
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

        private static void PostAntiCaptchaCodeSubmittedToChat(string code)
        {
            try
            {
                var safeCode = EscapeHtmlText((code ?? string.Empty).Trim());
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.WriteChatMsgSafe(
                        "<font color=#008000>[Анти-Captcha]: ответ сервиса '" +
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
