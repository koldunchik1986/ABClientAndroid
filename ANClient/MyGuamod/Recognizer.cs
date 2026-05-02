using ANClient.Neuro;

namespace ANClient.MyGuamod
{
    using System;
    using System.Drawing;
    using System.IO;
    using System.Text;
    using System.Threading;
    using ANForms;
    using Properties;

    internal static class Recognizer
    {
        private static DateTime TimeStart = DateTime.MinValue;
        private static DateTime TimeLast = DateTime.MinValue;
        private static NeuroBase neuro;

        internal static bool Busy { get; set; }

        internal static bool Ready { get; set; }

        internal static void Perform()
        {
            if (Busy)
            {
                return;
            }

            if (!Ready)
            {
                PrepareData();
            }

            Busy = true;
            ThreadPool.QueueUserWorkItem(Hop);
        }

        internal static void Hop(object stateInfo)
        {
            var stamp = DateTime.Now;

            do
            {
            } 
            while (AppVars.CodePng == null);

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateGuamodMessageDelegate(AppVars.MainForm.UpdateGuamodMessage),
                        new object[] { "Идет распознавание..." });
                }
            }
            catch (InvalidOperationException)
            {
            }

            neuro = new NeuroBase();
            var newresultOne = string.Empty;

            // Сохраняем капчу в файл для анализа/переобучения
            try
            {
                var captchaDir = System.IO.Path.Combine(System.Windows.Forms.Application.StartupPath, "Logs");
                captchaDir = System.IO.Path.Combine(captchaDir, "Captcha");
                if (!System.IO.Directory.Exists(captchaDir))
                    System.IO.Directory.CreateDirectory(captchaDir);
                var captchaFile = System.IO.Path.Combine(captchaDir, DateTime.Now.ToString("yyyyMMdd_HHmmss") + ".png");
                System.IO.File.WriteAllBytes(captchaFile, AppVars.CodePng);
                AppLog.d("Recognizer", "CAPTCHA_SAVED: " + captchaFile + " size=" + AppVars.CodePng.Length);
            }
            catch (Exception saveEx)
            {
                AppLog.w("Recognizer", "CAPTCHA_SAVE_FAILED: " + saveEx.Message);
            }

            using (var ms = new MemoryStream(AppVars.CodePng))
            {
                try
                {
                    using (var image = Image.FromStream(ms))
                    {
                        using (var bitmapSource = new Bitmap(image))
                        {
                            neuro.Calculate(bitmapSource);
                            newresultOne = NeuroBase.Gyp();
                        }
                    }
                }
                catch (ArgumentException ex)
                {
                    AppLog.e("Recognizer", "CALCULATE_ARG_EXCEPTION", ex);
                    try
                    {
                        if (AppVars.MainForm != null)
                        {
                            AppVars.MainForm.BeginInvoke(
                                new UpdateGuamodMessageDelegate(AppVars.MainForm.UpdateGuamodMessage),
                                new object[] { "Ошибка распознавания" });
                        }
                    }
                    catch (InvalidOperationException)
                    {
                    }
                }
                catch (Exception ex)
                {
                    AppLog.e("Recognizer", "CALCULATE_EXCEPTION", ex);
                    try
                    {
                        if (AppVars.MainForm != null)
                        {
                            AppVars.MainForm.BeginInvoke(
                                new UpdateGuamodMessageDelegate(AppVars.MainForm.UpdateGuamodMessage),
                                new object[] { "Ошибка распознавания" });
                        }
                    }
                    catch (InvalidOperationException)
                    {
                    }
                }
            }

            newresultOne = string.IsNullOrEmpty(newresultOne) ? "сбой" : newresultOne.Trim();

            // Отладочный вывод в чат: распознанные цифры + auto-detect info
            var debugInfo = NeuroBase.DebugInfo();
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        "[CAPTCHA_DEBUG] " + debugInfo);
                }
            }
            catch (InvalidOperationException)
            {
            }

            AppLog.i("Recognizer", "RESULT: " + newresultOne + " | " + debugInfo);

            if (!string.IsNullOrEmpty(newresultOne) && (newresultOne[0] != '0') && (newresultOne[0] != '1'))
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateGuamodMessageDelegate(AppVars.MainForm.UpdateGuamodMessage),
                            new object[] { "Распознано " + newresultOne });
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppVars.GuamodCode = newresultOne;
                AppVars.ClearCodePng();
                Thread.Sleep(5000);
                AppVars.FightLink = AppVars.FightLink.Replace("????", AppVars.GuamodCode);
            
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        var strkod =
                            "Код: " +
                            AppVars.GuamodCode +
                            "; время = " +
                            MyHelpers.HelperConverters.TimeSpanToString(DateTime.Now.Subtract(stamp));
                        AppVars.MainForm.BeginInvoke(
                            new UpdateTexLogDelegate(AppVars.MainForm.UpdateTexLog),
                            new object[] { strkod });
                    }
                }
                catch (InvalidOperationException)
                {
                }

                Busy = false;
                return;
            }

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateGuamodMessageDelegate(AppVars.MainForm.UpdateGuamodMessage),
                        new object[] { "Недостоверное распознавание" });
                }
            }
            catch (InvalidOperationException)
            {
            }

            AppVars.FightLink = string.Empty;
            AppVars.Autoboi = AutoboiState.AutoboiOn;

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new ReloadMainPhpInvokeDelegate(AppVars.MainForm.ReloadMainPhpInvoke),
                        new object[] { });
                }
            }
            catch (InvalidOperationException)
            {
            }

            Busy = false;
        }

        internal static void PrepareData()
        {
            if (NeuroBase.NumNodes() != 0)
            {
                return;
            }

            // Сначала пробуем загрузить кастомную обученную базу
            NeuroBase.LoadCustomBase();

            if (NeuroBase.NumNodes() != 0)
            {
                AppLog.i("Recognizer", "Using custom neuro base, nodes=" + NeuroBase.NumNodes());
                Ready = true;
                return;
            }

            // Fallback: встроенная база из ресурсов
            using (var stream = new MemoryStream(Resources.anneuro))
            {
                var array = new byte[stream.Length];
                stream.Read(array, 0, (int) stream.Length);
                NeuroBase.LoadFromArray(array);
            }

            AppLog.i("Recognizer", "Using built-in neuro base, nodes=" + NeuroBase.NumNodes());
            Ready = true;
        }

        internal static TimeSpan BuildTimeDiff()
        {
            var diff = DateTime.Now.Subtract(TimeLast);
            if (diff.TotalSeconds > 120)
            {
                TimeStart = DateTime.Now;
            }

            TimeLast = DateTime.Now;
            return TimeLast.Subtract(TimeStart);
        }

        internal static string BuildTimeString()
        {
            var diff = BuildTimeDiff();
            if (diff.TotalSeconds < 5)
            {
                return string.Empty;
            }

            var sb = new StringBuilder();
            sb.Append(diff.Minutes);
            sb.Append(':');
            sb.Append(diff.Seconds.ToString("00"));
            return sb.ToString();
        }
    }
}
