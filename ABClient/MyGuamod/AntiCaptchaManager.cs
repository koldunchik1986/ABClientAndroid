using System;
using System.Globalization;
using System.IO;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using ABClient.ABForms;

namespace ABClient.MyGuamod
{
    internal static class AntiCaptchaManager
    {
        private const string Tag = "AntiCaptchaManager";
        private const string ApiCreateTask = "https://api.anti-captcha.com/createTask";
        private const string ApiGetTaskResult = "https://api.anti-captcha.com/getTaskResult";
        private const int MaxPollCount = 24;
        private const int PollDelayMs = 3000;
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

        private static readonly Regex TaskIdRegex = new Regex("\"taskId\"\\s*:\\s*(\\d+)", RegexOptions.Compiled);
        private static readonly Regex ErrorIdRegex = new Regex("\"errorId\"\\s*:\\s*(\\d+)", RegexOptions.Compiled);
        private static readonly Regex StatusRegex = new Regex("\"status\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static readonly Regex TextRegex = new Regex("\"text\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static readonly Regex ErrorCodeRegex = new Regex("\"errorCode\"\\s*:\\s*\"([^\"]*)\"", RegexOptions.Compiled);
        private static bool busy;

        internal static bool TrySolveCurrentCaptchaWithFallback()
        {
            if (busy)
            {
                return true;
            }

            if (AppVars.Profile == null || !AppVars.Profile.AntiCaptchaEnabled)
            {
                return false;
            }

            if (string.IsNullOrEmpty(AppVars.Profile.AntiCaptchaApiKey) || AppVars.CodePng == null || AppVars.CodePng.Length == 0)
            {
                return false;
            }

            var challenge = AppVars.FightLink;
            if (string.IsNullOrEmpty(challenge) || challenge.IndexOf("????", StringComparison.Ordinal) == -1)
            {
                return false;
            }

            var imageBytes = new byte[AppVars.CodePng.Length];
            Buffer.BlockCopy(AppVars.CodePng, 0, imageBytes, 0, AppVars.CodePng.Length);
            busy = true;
            ThreadPool.QueueUserWorkItem(delegate { SolveWorker(challenge, imageBytes); });
            AppLog.i(Tag, "ANTI_CAPTCHA_TRACE started, bytes=" + imageBytes.Length);
            UpdateGuamodMessage("Anti-Captcha: отправка...");
            return true;
        }

        private static void SolveWorker(string challenge, byte[] imageBytes)
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

                if (!string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal))
                {
                    AppLog.w(Tag, "ANTI_CAPTCHA_TRACE stale solution ignored");
                    return;
                }

                AppVars.GuamodCode = text.Trim();
                AppVars.CodePng = null;
                AppVars.FightLink = AppVars.FightLink.Replace("????", AppVars.GuamodCode);
                UpdateGuamodMessage("Anti-Captcha: распознано " + AppVars.GuamodCode);
                UpdateTexLog("Anti-Captcha код: " + AppVars.GuamodCode);
                AppLog.i(Tag, "ANTI_CAPTCHA_TRACE solved, textLen=" + AppVars.GuamodCode.Length);
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

        private static long CreateTask(byte[] imageBytes)
        {
            var sb = new StringBuilder();
            sb.Append("{\"clientKey\":\"");
            sb.Append(JsonEscape(AppVars.Profile.AntiCaptchaApiKey));
            sb.Append("\",\"softId\":0,\"task\":{");
            sb.Append("\"type\":\"ImageToTextTask\",");
            sb.Append("\"body\":\"");
            sb.Append(Convert.ToBase64String(imageBytes));
            sb.Append("\",");
            sb.Append("\"phrase\":");
            sb.Append(AppVars.Profile.AntiCaptchaPhrase ? "true" : "false");
            sb.Append(",\"case\":");
            sb.Append(AppVars.Profile.AntiCaptchaCaseSensitive ? "true" : "false");
            sb.Append(",\"numeric\":");
            sb.Append(Clamp(AppVars.Profile.AntiCaptchaNumeric, 0, 2).ToString(CultureInfo.InvariantCulture));
            sb.Append(",\"math\":");
            sb.Append(Clamp(AppVars.Profile.AntiCaptchaMath, 0, 1).ToString(CultureInfo.InvariantCulture));
            if (AppVars.Profile.AntiCaptchaMinLength > 0)
            {
                sb.Append(",\"minLength\":");
                sb.Append(Clamp(AppVars.Profile.AntiCaptchaMinLength, 0, 20).ToString(CultureInfo.InvariantCulture));
            }

            if (AppVars.Profile.AntiCaptchaMaxLength > 0)
            {
                sb.Append(",\"maxLength\":");
                sb.Append(Clamp(AppVars.Profile.AntiCaptchaMaxLength, 0, 20).ToString(CultureInfo.InvariantCulture));
            }

            sb.Append(",\"languagePool\":\"");
            sb.Append(NormalizeLanguagePool(AppVars.Profile.AntiCaptchaLanguagePool));
            sb.Append("\"}}}");

            var response = PostJson(ApiCreateTask, sb.ToString());
            if (ReadErrorId(response) != 0)
            {
                AppLog.w(Tag, "ANTI_CAPTCHA_TRACE createTask error=" + ReadRegex(ErrorCodeRegex, response));
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
                if (attempt > 0)
                {
                    Thread.Sleep(PollDelayMs);
                }

                var request = "{\"clientKey\":\"" + JsonEscape(AppVars.Profile.AntiCaptchaApiKey) + "\",\"taskId\":" + taskId.ToString(CultureInfo.InvariantCulture) + "}";
                var response = PostJson(ApiGetTaskResult, request);
                if (ReadErrorId(response) != 0)
                {
                    AppLog.w(Tag, "ANTI_CAPTCHA_TRACE getTaskResult error=" + ReadRegex(ErrorCodeRegex, response));
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
            var payload = Encoding.UTF8.GetBytes(json);
            var request = (HttpWebRequest)WebRequest.Create(url);
            request.Method = "POST";
            request.ContentType = "application/json; charset=utf-8";
            request.Accept = "application/json";
            request.UserAgent = BrowserUserAgent;
            request.Timeout = 20000;
            request.ReadWriteTimeout = 20000;
            request.ContentLength = payload.Length;
            using (var stream = request.GetRequestStream())
            {
                stream.Write(payload, 0, payload.Length);
            }

            using (var response = (HttpWebResponse)request.GetResponse())
            using (var stream = response.GetResponseStream())
            using (var reader = new StreamReader(stream, Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }

        private static void FailAndFallback(string challenge, string message)
        {
            AppLog.w(Tag, "ANTI_CAPTCHA_TRACE failed: " + message);
            UpdateGuamodMessage("Anti-Captcha: ошибка, запускаю гуамод");
            if (string.Equals(AppVars.FightLink, challenge, StringComparison.Ordinal) && AppVars.CodePng != null)
            {
                Recognizer.Perform();
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

        private static string JsonEscape(string value)
        {
            return (value ?? string.Empty).Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "\\r").Replace("\n", "\\n");
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
    }
}
