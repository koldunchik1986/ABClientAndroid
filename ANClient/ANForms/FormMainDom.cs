using ANClient.MyChat;

namespace ANClient.ANForms
{
    using System;
    using System.Drawing;
    using System.Windows.Forms;

    internal sealed partial class FormMain
    {
        private void EnterFishCode(string code)
        {
            var framebuttons = GetFrame("main_top");
            if (framebuttons == null)
            {
                return;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return;
                }

                if (!string.IsNullOrEmpty(code))
                {
                    var prompt = framebuttons.Document.GetElementById("CAPCODE");
                    if (prompt == null)
                    {
                        return;
                    }

                    prompt.SetAttribute("value", code);
                }

                var fishbutton = framebuttons.Document.GetElementById("fishbutton");
                if (fishbutton != null)
                {
                    fishbutton.InvokeMember("click");
                }

                ChangeAutoboiState(AppVars.Profile.LezDoAutoboi ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff);
            }
            // ReSharper disable EmptyGeneralCatchClause
            catch
            // ReSharper restore EmptyGeneralCatchClause
            {
            }
        }

        internal bool TrySubmitReadyAlchemyFightLink(string source)
        {
            var link = AppVars.FightLink;
            if (string.IsNullOrEmpty(link) ||
                link.IndexOf("alchemy_ajax.php", StringComparison.OrdinalIgnoreCase) < 0 ||
                link.IndexOf("????", StringComparison.Ordinal) >= 0)
            {
                return false;
            }

            AppVars.FightLink = string.Empty;
            UpdateTexLog("Срез травы");
            AppLog.i("auto_cut_trace", "FormMainDom", "alchemy ready submit: source=" + (source ?? "unknown"));
            EnterAlchemyCode(link);
            ChangeAutoboiState(AppVars.Profile.LezDoAutoboi ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff);
            return true;
        }

        internal bool TrySubmitReadyAutoboiFightLink(string source)
        {
            var link = AppVars.FightLink;
            if (AppVars.Profile == null ||
                !AppVars.Profile.LezDoAutoboi ||
                string.IsNullOrEmpty(link) ||
                link.Length <= 5 ||
                link.IndexOf("????", StringComparison.Ordinal) >= 0 ||
                link.IndexOf("alchemy_ajax.php", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                return false;
            }

            AppVars.Profile.Pers.Ready = 0;
            UpdateTexLog("Завершение боя");
            if (link.IndexOf("=00000&", StringComparison.Ordinal) == -1)
            {
                SetMainTopInvoke(link);
            }

            PulseTrayBalloonAfterFightLinkSubmit();
            AppVars.FightLink = string.Empty;
            ChangeAutoboiState(AppVars.Profile.LezDoAutoboi ? AutoboiState.AutoboiOn : AutoboiState.AutoboiOff);
            AppLog.i("LezFight", "FormMainDom", "autoboi ready submit: source=" + (source ?? "unknown"));
            return true;
        }

        private void PulseTrayBalloonAfterFightLinkSubmit()
        {
            if (!AppVars.Profile.ShowTrayBaloons || !trayIcon.Visible)
            {
                return;
            }

            try
            {
                LockBaloon.AcquireWriterLock(5000);
                try
                {
                    trayIcon.Visible = false;
                    trayIcon.Visible = true;
                }
                finally
                {
                    LockBaloon.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        private void EnterAlchemyCode(string link)
        {
            if (string.IsNullOrEmpty(link))
            {
                return;
            }

            var framebuttons = GetFrame("main_top");
            if (framebuttons == null || framebuttons.Document == null)
            {
                return;
            }

            try
            {
                var ajaxLink = link;
                const string fullPrefix = "http://www.neverlands.ru/gameplay/ajax/";
                if (ajaxLink.StartsWith(fullPrefix, StringComparison.OrdinalIgnoreCase))
                {
                    ajaxLink = ajaxLink.Substring(fullPrefix.Length);
                }

                framebuttons.Document.InvokeScript("AjaxGet", new object[] { ajaxLink });
                AppLog.i("auto_cut_trace", "FormMainDom", "alchemy submit via AjaxGet: " + ajaxLink);
            }
            catch (Exception ex)
            {
                AppLog.w("auto_cut_trace", "FormMainDom", "alchemy submit failed", ex);
            }
        }

        private HtmlWindow GetFrame(string name)
        {
            try
            {
                if (browserGame != null &&
                    browserGame.Document != null &&
                    browserGame.Document.Window != null &&
                    browserGame.Document.Window != null)
                {
                    return browserGame.Document.Window.Frames == null ? null : browserGame.Document.Window.Frames[name];
                }
            }
            catch (ArgumentException)
            {
                return null;
            }
            catch (Exception)
            {
                return null;
            }

            return null;
        }

        private void NavigateFrame(string frame, string address)
        {
            var success = false;
            var window = GetFrame(frame);
            if (window != null)
            {
                try
                {
                    window.Navigate(address);
                    success = true;
                }
                catch (Exception)
                {
                    success = false;
                }
            }

            if (!success)
            {
                UpdateGame("Перезаход из-за ошибок в браузере или структуре фреймов. ");
            }
        }

        internal void ReloadChPhpInvoke()
        {
            NavigateFrame("ch_list", "http://www.neverlands.ru/ch.php?lo=1");
        }

        internal void SetMainTopInvoke(string address)
        {
            NavigateFrame("main_top", address);
        }

        internal void ReloadMainPhpInvoke()
        {
            SetMainTopInvoke("http://www.neverlands.ru/main.php");
        }

        private void WriteMessageToPrompt(string msg)
        {
            if (string.IsNullOrEmpty(msg))
            {
                return;
            }

            var framebuttons = GetFrame("ch_buttons");
            if (framebuttons == null)
            {
                return;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return;
                }

                var prompt = framebuttons.Document.GetElementById("text");
                if (prompt == null)
                {
                    return;
                }

                prompt.SetAttribute("value", msg);
                prompt.Focus();
            }
            catch
            {
            }
        }

        private void AddMessageToPrompt(string msg)
        {
            if (string.IsNullOrEmpty(msg))
            {
                return;
            }

            var framebuttons = GetFrame("ch_buttons");
            if (framebuttons == null)
            {
                return;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return;
                }

                var prompt = framebuttons.Document.GetElementById("text");
                if (prompt == null)
                {
                    return;
                }

                var currentPrompt = prompt.GetAttribute("value");
                prompt.SetAttribute("value", currentPrompt + msg);
                prompt.Focus();
            }
            catch
            {
            }
        }

        private void InsertMessageToPrompt(string msg)
        {
            if (string.IsNullOrEmpty(msg))
            {
                return;
            }

            var framebuttons = GetFrame("ch_buttons");
            if (framebuttons == null)
            {
                return;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return;
                }

                var prompt = framebuttons.Document.GetElementById("text");
                if (prompt == null)
                {
                    return;
                }

                var currentPrompt = prompt.GetAttribute("value");
                if (currentPrompt.StartsWith(msg, StringComparison.OrdinalIgnoreCase))
                {
                    return;
                }

                prompt.SetAttribute("value", msg + currentPrompt);
                prompt.Focus();
            }
            catch
            {
            }
        }

        internal void WriteMessageToChat(string msg)
        {
            if (string.IsNullOrEmpty(msg))
            {
                return;
            }

            /*
            try
            {
                while (browserGame.ReadyState != WebBrowserReadyState.Complete)
                {
                    Application.DoEvents();
                }
            }
            catch
            {
                return;
            }
             */ 

            var framebuttons = GetFrame("ch_buttons");
            if (framebuttons == null)
            {
                return;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return;
                }

                var prompt = framebuttons.Document.GetElementById("text");
                if (prompt == null)
                {
                    return;
                }

                prompt.SetAttribute("value", msg);
                var butinp = framebuttons.Document.GetElementById("butinp");
                if (butinp == null)
                {
                    return;
                }

                butinp.InvokeMember("onClick");
                prompt.SetAttribute("value", string.Empty);
            }
                // ReSharper disable EmptyGeneralCatchClause
            catch
                // ReSharper restore EmptyGeneralCatchClause
            {
            }            
        }

        /*
        private bool PressOgl()
        {            
            var framebuttons = GetFrame("main_top");
            if (framebuttons == null)
            {
                return false;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return false;
                }

                var buttonOgl = framebuttons.Document.GetElementById("ogl");
                if (buttonOgl == null)
                {
                    return false;
                }

                if (!buttonOgl.Enabled)
                {
                    return false;
                }

                buttonOgl.InvokeMember("click");
                CheckTied();
                return true;
            }
            catch
            {
            }

            return false;
        }
         */ 

        private void WriteColorMessageToChat(string msg)
        {
            if (string.IsNullOrEmpty(msg))
            {
                return;
            }

            var framechat = GetFrame("chmain");
            if (framechat == null)
            {
                return;
            }

            try
            {
                var div = framechat.Document?.GetElementById("msg");
                if (div == null)
                {
                    return;
                }

                div.InnerHtml += msg;
                //Chat.AddStringToChat(msg);
                framechat.ScrollTo(new Point(0, 0xFFFF));            
            }
// ReSharper disable EmptyGeneralCatchClause
            catch
// ReSharper restore EmptyGeneralCatchClause
            {
            }

            msg = msg.Replace("<BR>", string.Empty);
            Chat.AddStringToChat(msg);
        }

        private void WriteMessageToGuamod(string msg)
        {
            if (string.IsNullOrEmpty(msg))
            {
                return;
            }

            /*
            try
            {
                while (browserGame.ReadyState != WebBrowserReadyState.Complete)
                {
                    Application.DoEvents();
                }
            }
            catch
            {
                return;
            }
             */ 

            var framebuttons = GetFrame("main_top");
            if (framebuttons == null)
            {
                return;
            }

            try
            {
                if (framebuttons.Document == null)
                {
                    return;
                }

                var prompt = framebuttons.Document.GetElementById("guamod3");
                if (prompt == null)
                {
                    return;
                }

                prompt.InnerHtml = msg;
            }
                // ReSharper disable EmptyGeneralCatchClause
            catch
                // ReSharper restore EmptyGeneralCatchClause
            {
            }
        }

        private string ReadChat()
        {
            try
            {
                if (browserGame.ReadyState == WebBrowserReadyState.Uninitialized)
                {
                    return null;
                }
            }
            catch
            {
                return null;
            }

            var framechat = GetFrame("chmain");
            if (framechat == null)
            {
                return null;
            }

            try
            {
                if (framechat.Document == null)
                {
                    return null;
                }

                var div = framechat.Document.GetElementById("msg");
                return div == null ? null : div.InnerHtml;
            }
                // ReSharper disable EmptyGeneralCatchClause
            catch
                // ReSharper restore EmptyGeneralCatchClause
            {
            }

            return null;
        }
    }
}
