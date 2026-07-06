namespace ANClient.ANForms
{
    using System;
    using System.Drawing;
    using System.Windows.Forms;

    internal sealed partial class FormMain
    {
        private int trayFlashFrame;
        private readonly Icon[] trayIcons = new Icon[] { null, null };
        private DateTime trayDigitsWait = DateTime.Now;

        internal bool TrayIsDigitsWaitTooLong()
        {
            if (trayIcon.Text.IndexOf("Ввод цифр", StringComparison.OrdinalIgnoreCase) == -1)
            {
                return false;
            }

            return DateTime.Now.Subtract(trayDigitsWait).TotalMinutes > 1;
        }

        private void TrayShow()
        {
            for (var i = 0; i < 2; i++)
            {
                if (trayIcons[i] != null) continue;
                var objBitmap = new Bitmap(trayImages.Images[i]);
                trayIcons[i] = Icon.FromHandle(objBitmap.GetHicon());
            }

            trayIcon.Text = AppVars.Profile.UserNick;
            TrayShowFrame(0);
            trayIcon.Visible = true;
        }

        private void TrayShowFrame(int frame)
        {
            trayIcon.Icon = trayIcons[frame];
        }

        private void TrayFlash(string message)
        {
            if (timerTray.Enabled || !trayIcon.Visible)
            {
                return;
            }

            if (message.Equals("Ввод цифр", StringComparison.OrdinalIgnoreCase))
            {
                trayDigitsWait = DateTime.Now;
            }

            trayIcon.Text = AppVars.Profile.UserNick + ": " + message;
            trayFlashFrame = 0;
            timerTray.Start();
        }

        private void TrayIconTick()
        {
            trayFlashFrame = 1 - trayFlashFrame;
            TrayShowFrame(trayFlashFrame);
        }

        private void TrayIconDoubleClick()
        {
            if (trayIcon.Text.IndexOf("Ввод цифр", StringComparison.OrdinalIgnoreCase) != -1)
            {
                AppLog.w("Captcha", "MANUAL_CAPTCHA_TRAY_DISABLED");
                UpdateGuamodMessage("Ручной ввод капчи отключён");
                UpdateTexLog("Ручной ввод капчи отключён");
            }

            timerTray.Stop();
            
            Show();
            WindowState = _prevWindowState;
            trayIcon.Visible = false;
        }
    }
}
