namespace ANClient.MyGuamod
{
    using System;
    using System.Globalization;
    using System.IO;
    using System.Net;
    using System.Security.Cryptography;
    using System.Text;
    using System.Text.RegularExpressions;
    using System.Threading;
    using Newtonsoft.Json.Linq;

    internal static class LocalCaptchaSolver
    {
        private const string Tag = "LocalCaptchaSolver";
        private const string DefaultServiceUrl = "http://127.0.0.1:8765/";
        private const string BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        private const int TimeoutMs = 30000;
        private const int MaxPollCount = 120;
        private const int PollDelayMs = 250;
        private static readonly Regex CodeRegex = new Regex("^\\d{5}$", RegexOptions.Compiled);

        internal static bool IsEnabled()
        {
            return AppVars.Profile != null && AppVars.Profile.LocalCaptchaOcrEnabled;
        }

        internal static double MinConfidenceThreshold
        {
            get { return ResolveMinConfidenceThreshold(); }
        }

        internal static bool TryRecognizeForTraining(byte[] imageBytes, out string code, out double minConfidence, out string diagnostic)
        {
            code = string.Empty;
            minConfidence = 0d;
            diagnostic = string.Empty;

            if (imageBytes == null || imageBytes.Length == 0)
            {
                diagnostic = "empty image";
                return false;
            }

            var serviceUrl = ResolveServiceUrl();
            var imageHash = HashPrefix(imageBytes);

            try
            {
                AppLog.i(
                    "captcha_training",
                    Tag,
                    "CAPTCHA_TRAINING_OCR createTask, bytes=" + imageBytes.Length.ToString(CultureInfo.InvariantCulture) +
                    ", imageHash=" + imageHash +
                    ", service=" + serviceUrl +
                    ", threshold=" + ResolveMinConfidenceThreshold().ToString("0.0000", CultureInfo.InvariantCulture));

                long taskId;
                if (!CreateTask(serviceUrl, imageBytes, out taskId, out diagnostic))
                {
                    return false;
                }

                if (!WaitForResult(serviceUrl, taskId, out code, out minConfidence, out diagnostic))
                {
                    return false;
                }

                code = (code ?? string.Empty).Trim();
                if (!CodeRegex.IsMatch(code))
                {
                    diagnostic = "invalid code length=" + code.Length.ToString(CultureInfo.InvariantCulture);
                    AppLog.w("captcha_training", Tag, "CAPTCHA_TRAINING_OCR invalid_code, textLen=" + code.Length.ToString(CultureInfo.InvariantCulture));
                    code = string.Empty;
                    return false;
                }

                diagnostic = "recognized min=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture);
                AppLog.i(
                    "captcha_training",
                    Tag,
                    "CAPTCHA_TRAINING_OCR recognized, textLen=" + code.Length.ToString(CultureInfo.InvariantCulture) +
                    ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture));
                return true;
            }
            catch (Exception ex)
            {
                diagnostic = ex.Message;
                AppLog.w("captcha_training", Tag, "CAPTCHA_TRAINING_OCR failed", ex);
                return false;
            }
        }

        internal static bool TrySolve(byte[] imageBytes, out string code, out double minConfidence, out string diagnostic)
        {
            code = string.Empty;
            minConfidence = 0d;
            diagnostic = string.Empty;

            if (imageBytes == null || imageBytes.Length == 0)
            {
                diagnostic = "empty image";
                return false;
            }

            var serviceUrl = ResolveServiceUrl();
            var threshold = ResolveMinConfidenceThreshold();
            var imageHash = HashPrefix(imageBytes);

            try
            {
                AppLog.i(Tag, "LOCAL_OCR_TRACE createTask, bytes=" + imageBytes.Length.ToString(CultureInfo.InvariantCulture) + ", imageHash=" + imageHash + ", service=" + serviceUrl);

                long taskId;
                if (!CreateTask(serviceUrl, imageBytes, out taskId, out diagnostic))
                {
                    return false;
                }

                if (!WaitForResult(serviceUrl, taskId, out code, out minConfidence, out diagnostic))
                {
                    return false;
                }

                code = (code ?? string.Empty).Trim();
                if (!CodeRegex.IsMatch(code))
                {
                    diagnostic = "invalid code length=" + code.Length.ToString(CultureInfo.InvariantCulture);
                    AppLog.w(Tag, "LOCAL_OCR_TRACE invalid_code, textLen=" + code.Length.ToString(CultureInfo.InvariantCulture));
                    code = string.Empty;
                    return false;
                }

                if (minConfidence < threshold)
                {
                    diagnostic = "low confidence min=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) + ", threshold=" + threshold.ToString("0.0000", CultureInfo.InvariantCulture) + ", code=" + code;
                    AppLog.w(Tag, "LOCAL_OCR_TRACE low_confidence rejected, " + diagnostic);
                    code = string.Empty;
                    return false;
                }

                diagnostic = "solved min=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture);
                AppLog.i(Tag, "LOCAL_OCR_TRACE solved, textLen=" + code.Length.ToString(CultureInfo.InvariantCulture) + ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture));
                return true;
            }
            catch (Exception ex)
            {
                diagnostic = ex.Message;
                AppLog.w(Tag, "LOCAL_OCR_TRACE failed", ex);
                return false;
            }
        }

        private static bool CreateTask(string serviceUrl, byte[] imageBytes, out long taskId, out string diagnostic)
        {
            taskId = 0;
            diagnostic = string.Empty;

            var taskJson = new JObject();
            taskJson["type"] = "ImageToTextTask";
            taskJson["body"] = Convert.ToBase64String(imageBytes).Replace("\r", string.Empty).Replace("\n", string.Empty);
            taskJson["phrase"] = AppVars.Profile != null && AppVars.Profile.AntiCaptchaPhrase;
            taskJson["case"] = AppVars.Profile != null && AppVars.Profile.AntiCaptchaCaseSensitive;
            taskJson["numeric"] = AppVars.Profile == null ? 1 : Clamp(AppVars.Profile.AntiCaptchaNumeric, 0, 2);
            taskJson["math"] = AppVars.Profile == null ? 0 : Clamp(AppVars.Profile.AntiCaptchaMath, 0, 1);
            taskJson["minLength"] = AppVars.Profile == null ? 5 : Clamp(AppVars.Profile.AntiCaptchaMinLength, 0, 20);
            taskJson["maxLength"] = AppVars.Profile == null ? 5 : Clamp(AppVars.Profile.AntiCaptchaMaxLength, 0, 20);
            taskJson["languagePool"] = NormalizeLanguagePool(AppVars.Profile == null ? "en" : AppVars.Profile.AntiCaptchaLanguagePool);

            var requestJson = new JObject();
            requestJson["clientKey"] = "local";
            requestJson["task"] = taskJson;

            var responseText = PostJson(BuildEndpoint(serviceUrl, "createTask"), requestJson.ToString(Newtonsoft.Json.Formatting.None));
            var response = JObject.Parse(responseText);
            if (ReadInt(response["errorId"], -1) != 0)
            {
                diagnostic = "createTask error=" + ReadString(response["errorCode"]) + ", description=" + ReadString(response["errorDescription"]);
                AppLog.w(Tag, "LOCAL_OCR_TRACE createTask failed: " + diagnostic);
                return false;
            }

            taskId = ReadLong(response["taskId"], 0);
            if (taskId <= 0)
            {
                diagnostic = "taskId is empty";
                AppLog.w(Tag, "LOCAL_OCR_TRACE createTask failed: " + diagnostic);
                return false;
            }

            return true;
        }

        private static bool WaitForResult(string serviceUrl, long taskId, out string code, out double minConfidence, out string diagnostic)
        {
            code = string.Empty;
            minConfidence = 0d;
            diagnostic = string.Empty;

            for (var attempt = 0; attempt < MaxPollCount; attempt++)
            {
                if (attempt > 0)
                {
                    Thread.Sleep(PollDelayMs);
                }

                var requestJson = new JObject();
                requestJson["clientKey"] = "local";
                requestJson["taskId"] = taskId;

                var responseText = PostJson(BuildEndpoint(serviceUrl, "getTaskResult"), requestJson.ToString(Newtonsoft.Json.Formatting.None));
                var response = JObject.Parse(responseText);
                if (ReadInt(response["errorId"], -1) != 0)
                {
                    diagnostic = "getTaskResult error=" + ReadString(response["errorCode"]) + ", description=" + ReadString(response["errorDescription"]);
                    AppLog.w(Tag, "LOCAL_OCR_TRACE getTaskResult failed: " + diagnostic);
                    return false;
                }

                var status = ReadString(response["status"]);
                if (string.Equals(status, "processing", StringComparison.OrdinalIgnoreCase))
                {
                    AppLog.d(Tag, "LOCAL_OCR_TRACE processing, task=" + taskId.ToString(CultureInfo.InvariantCulture) + ", attempt=" + attempt.ToString(CultureInfo.InvariantCulture));
                    continue;
                }

                if (string.Equals(status, "ready", StringComparison.OrdinalIgnoreCase))
                {
                    var solution = response["solution"] as JObject;
                    code = solution == null ? string.Empty : ReadString(solution["text"]);
                    minConfidence = solution == null ? 0d : ReadDouble(solution["minConfidence"]);
                    if (minConfidence <= 0d)
                    {
                        minConfidence = ReadDouble(response["minConfidence"]);
                    }

                    AppLog.i(Tag, "LOCAL_OCR_TRACE ready, task=" + taskId.ToString(CultureInfo.InvariantCulture) + ", code=" + (code ?? string.Empty).Trim() + ", textLen=" + (code ?? string.Empty).Trim().Length.ToString(CultureInfo.InvariantCulture) + ", minConfidence=" + minConfidence.ToString("0.0000", CultureInfo.InvariantCulture) + ", threshold=" + ResolveMinConfidenceThreshold().ToString("0.0000", CultureInfo.InvariantCulture));
                    return true;
                }

                diagnostic = "unexpected status=" + status;
                AppLog.w(Tag, "LOCAL_OCR_TRACE getTaskResult failed: " + diagnostic);
                return false;
            }

            diagnostic = "timeout task=" + taskId.ToString(CultureInfo.InvariantCulture);
            AppLog.w(Tag, "LOCAL_OCR_TRACE timeout, " + diagnostic);
            return false;
        }

        private static string PostJson(string url, string json)
        {
            var payload = Encoding.UTF8.GetBytes(json);
            var request = (HttpWebRequest)WebRequest.Create(url);
            request.Method = "POST";
            request.ContentType = "application/json";
            request.UserAgent = BrowserUserAgent;
            request.Timeout = TimeoutMs;
            request.ReadWriteTimeout = TimeoutMs;
            request.ContentLength = payload.Length;
            request.KeepAlive = false;
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
                    AppLog.w(Tag, "LOCAL_OCR_TRACE http status=" + ((int)response.StatusCode).ToString(CultureInfo.InvariantCulture) + " raw=" + TailForLog(text));
                    return text;
                }
            }
        }

        private static string ResolveServiceUrl()
        {
            var configured = AppVars.Profile == null ? string.Empty : AppVars.Profile.LocalCaptchaOcrServiceUrl;
            if (string.IsNullOrEmpty(configured))
            {
                return DefaultServiceUrl;
            }

            configured = configured.Trim();
            if (configured.Length == 0)
            {
                return DefaultServiceUrl;
            }

            if (configured.IndexOf("://", StringComparison.Ordinal) < 0)
            {
                configured = "http://" + configured;
            }

            return configured.EndsWith("/", StringComparison.Ordinal) ? configured : configured + "/";
        }

        private static string BuildEndpoint(string serviceUrl, string action)
        {
            return serviceUrl + action;
        }

        private static double ResolveMinConfidenceThreshold()
        {
            if (AppVars.Profile == null || AppVars.Profile.LocalCaptchaOcrMinConfidence < 0d || AppVars.Profile.LocalCaptchaOcrMinConfidence > 1d)
            {
                return 0.90d;
            }

            return AppVars.Profile.LocalCaptchaOcrMinConfidence;
        }

        private static int Clamp(int value, int min, int max)
        {
            return Math.Max(min, Math.Min(max, value));
        }

        private static string NormalizeLanguagePool(string value)
        {
            return string.Equals(value, "rn", StringComparison.OrdinalIgnoreCase) ? "rn" : "en";
        }

        private static double ReadDouble(JToken token)
        {
            if (token == null)
            {
                return 0d;
            }

            try
            {
                if (token.Type == JTokenType.Float || token.Type == JTokenType.Integer)
                {
                    return token.Value<double>();
                }
            }
            catch (FormatException)
            {
            }
            catch (InvalidCastException)
            {
            }

            var text = token.ToString(Newtonsoft.Json.Formatting.None).Trim().Trim('"').Replace(',', '.');
            double value;
            return double.TryParse(text, NumberStyles.Float, CultureInfo.InvariantCulture, out value) ? value : 0d;
        }

        private static int ReadInt(JToken token, int defaultValue)
        {
            int value;
            return token != null && int.TryParse(token.ToString(), NumberStyles.Integer, CultureInfo.InvariantCulture, out value) ? value : defaultValue;
        }

        private static long ReadLong(JToken token, long defaultValue)
        {
            long value;
            return token != null && long.TryParse(token.ToString(), NumberStyles.Integer, CultureInfo.InvariantCulture, out value) ? value : defaultValue;
        }

        private static string ReadString(JToken token)
        {
            return token == null ? string.Empty : Convert.ToString(token, CultureInfo.InvariantCulture);
        }

        private static string HashPrefix(byte[] bytes)
        {
            try
            {
                using (var sha = new SHA256Managed())
                {
                    var hash = sha.ComputeHash(bytes);
                    var builder = new StringBuilder();
                    for (var i = 0; i < hash.Length && i < 6; i++)
                    {
                        builder.Append(hash[i].ToString("x2", CultureInfo.InvariantCulture));
                    }

                    return builder.ToString();
                }
            }
            catch (Exception)
            {
                return "unknown";
            }
        }

        private static string TailForLog(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return string.Empty;
            }

            value = value.Replace("\r", string.Empty).Replace("\n", " ").Trim();
            return value.Length <= 600 ? value : value.Substring(value.Length - 600);
        }

    }
}
