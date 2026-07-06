namespace ANClient.MyForms
{
    using System;
    using System.Collections.Generic;
    using System.Net;
    using System.Threading;
    using System.Windows.Forms;
    using MyProfile;

    internal partial class FormProfile : Form
    {
        private const string ConstNewTitle = "Новый персонаж";
        private const string ConstNoProxyTitle = "Попытка определения прокси";
        private const string ConstNoProxyMessage = "Настройки прокси можно определить только, если они явно прописаны в Internet Explorer";
        private const string ConstPasswordProtected = "Зашифровать пароли (рекомендуется)";
        private const string ConstPasswordNotProtected = "Держать пароли открытыми (не рекомендуется)";
        private const string ConstSave = "Сохранить";
        private const int ConstServerPingRefreshMs = 10000;

        private readonly string _stringTitle;
        private bool passwordProtected;
        private System.Windows.Forms.Timer serverPingTimer;
        private volatile bool serverPingRunning;
        private readonly Dictionary<string, long?> serverPingMsByCode = new Dictionary<string, long?>();

        internal FormProfile(UserConfig userConfig)
        {
            InitializeComponent();
            InitializeGameServerSelector();
            if (userConfig == null)
            {
                _stringTitle = ConstNewTitle;
                SelectedUserConfig = new UserConfig();
                SetSelectedServerCode(SelectedUserConfig.GameServerCode);
            }
            else
            {
                _stringTitle = userConfig.UserNick;
                SelectedUserConfig = userConfig;
                textUsername.Text = userConfig.UserNick;
                SetSelectedServerCode(userConfig.GameServerCode);
                textUserKey.Text = userConfig.UserKey;
                textPassword.Text = userConfig.UserPassword;
                textFlashPassword.Text = userConfig.UserPasswordFlash;
                checkAutoLogon.Checked = userConfig.UserAutoLogon;
                checkUseProxy.Checked = userConfig.DoProxy;
                textProxyAddress.Text = userConfig.ProxyAddress;
                textProxyUsername.Text = userConfig.ProxyUserName;
                textProxyPassword.Text = userConfig.ProxyPassword;
                if (!string.IsNullOrEmpty(userConfig.ConfigHash))
                {
                    passwordProtected = true;
                    linkPasswordProtected.Text = ConstPasswordNotProtected;
                }

                buttonOk.Text = ConstSave;
                CheckAvailability();
            }
        }

        /// <summary>
        /// Выбранная конфигурация
        /// </summary>
        internal UserConfig SelectedUserConfig { get; private set; }

        private void FormProfile_Load(object sender, EventArgs e)
        {
            Text = _stringTitle;
            StartServerPingRefresh();
        }

        private void TextUsername_TextChanged(object sender, EventArgs e)
        {
            CheckAvailability();
        }

        private void TextPassword_TextChanged(object sender, EventArgs e)
        {
            CheckAvailability();
        }

        private void LinkDetectProxy_LinkClicked(object sender, LinkLabelLinkClickedEventArgs e)
        {
            var defaultWebProxy = WebRequest.DefaultWebProxy;
            var gameProxyUrl = defaultWebProxy.GetProxy(new Uri(AppConsts.GameUrl));
            if (AppConsts.GameUrl.Equals(gameProxyUrl.OriginalString))
            {
                MessageBox.Show(
                    ConstNoProxyMessage,
                    ConstNoProxyTitle,
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
                checkUseProxy.Checked = false;
                textProxyAddress.Enabled = false;
                textProxyAddress.Text = string.Empty;
                textProxyUsername.Enabled = false;
                textProxyPassword.Enabled = false;
            }
            else
            {
                checkUseProxy.Checked = true;
                textProxyAddress.Enabled = true;
                textProxyAddress.Text = gameProxyUrl.Authority;
                textProxyUsername.Enabled = true;
                textProxyPassword.Enabled = true;
            }

            textProxyUsername.Text = string.Empty;
            textProxyPassword.Text = string.Empty;
        }

        private void CheckVisiblePasswords_CheckedChanged(object sender, EventArgs e)
        {
            var visiblePasswords = !checkVisiblePasswords.Checked;
            textPassword.UseSystemPasswordChar = visiblePasswords;
            textFlashPassword.UseSystemPasswordChar = visiblePasswords;
            textProxyPassword.UseSystemPasswordChar = visiblePasswords;
        }

        private void CheckUseProxy_CheckedChanged(object sender, EventArgs e)
        {
            var enabledProxy = checkUseProxy.Checked;
            textProxyAddress.Enabled = enabledProxy;
            textProxyUsername.Enabled = enabledProxy;
            textProxyPassword.Enabled = enabledProxy;
            CheckAvailability();
        }

        private void TextProxyAddress_TextChanged(object sender, EventArgs e)
        {
            CheckAvailability();
        }

        private void LinkPasswordProtected_LinkClicked(object sender, LinkLabelLinkClickedEventArgs e)
        {
            if (!passwordProtected)
            {
                using (var formNewPassword = new FormNewPassword())
                {
                    if (formNewPassword.ShowDialog() == DialogResult.OK)
                    {
                        SelectedUserConfig.UserPassword = textPassword.Text.Trim();
                        SelectedUserConfig.UserPasswordFlash = textFlashPassword.Text.Trim();
                        SelectedUserConfig.Encrypt(formNewPassword.Password);
                        passwordProtected = true;
                        linkPasswordProtected.Text = ConstPasswordNotProtected;
                        checkAutoLogon.Enabled = false;
                        checkAutoLogon.Checked = false;
                    }
                }
            }
            else
            {
                SelectedUserConfig.Decrypt(SelectedUserConfig.ConfigPassword);
                SelectedUserConfig.ConfigHash = string.Empty;
                passwordProtected = false;
                linkPasswordProtected.Text = ConstPasswordProtected;
                checkAutoLogon.Enabled = true;
                checkAutoLogon.Checked = false;
            }
        }

        private void ButtonOk_Click(object sender, EventArgs e)
        {
            SelectedUserConfig.UserNick = textUsername.Text.Trim();
            SelectedUserConfig.GameServerCode = ANClient.GameServerSelector.CodeForDisplayValue(comboGameServer.Text);
            SelectedUserConfig.UserKey = textUserKey.Text.Trim();
            SelectedUserConfig.UserPassword = textPassword.Text.Trim();
            SelectedUserConfig.UserPasswordFlash = textFlashPassword.Text.Trim();
            SelectedUserConfig.UserAutoLogon = checkAutoLogon.Checked;
            SelectedUserConfig.DoProxy = checkUseProxy.Checked;
            SelectedUserConfig.ProxyAddress = textProxyAddress.Text.Trim();
            SelectedUserConfig.ProxyUserName = textProxyUsername.Text.Trim();
            SelectedUserConfig.ProxyPassword = textProxyPassword.Text.Trim();
        }

        private void CheckAvailability()
        {
            var nickAndPasswordPresented = !string.IsNullOrEmpty(textUsername.Text.Trim()) &&
                                           !string.IsNullOrEmpty(textPassword.Text.Trim());

            linkPasswordProtected.Enabled = nickAndPasswordPresented;
            checkAutoLogon.Enabled = nickAndPasswordPresented && !passwordProtected;
            var proxyValid = !checkUseProxy.Checked ||
                             (checkUseProxy.Checked && !string.IsNullOrEmpty(textProxyAddress.Text.Trim()));
            buttonOk.Enabled = nickAndPasswordPresented && proxyValid;
        }

        private void InitializeGameServerSelector()
        {
            UpdateServerDisplayNames();
            SetSelectedServerCode(ANClient.GameServerSelector.DefaultServerCode);
        }

        private void SetSelectedServerCode(string serverCode)
        {
            if (comboGameServer.Items.Count == 0)
            {
                UpdateServerDisplayNames();
            }

            comboGameServer.SelectedIndex = ANClient.GameServerSelector.DisplayIndex(serverCode);
        }

        private void StartServerPingRefresh()
        {
            if (serverPingTimer != null)
            {
                return;
            }

            serverPingTimer = new System.Windows.Forms.Timer();
            serverPingTimer.Interval = ConstServerPingRefreshMs;
            serverPingTimer.Tick += delegate { RefreshServerPingsAsync(); };
            RefreshServerPingsAsync();
            serverPingTimer.Start();
        }

        private void RefreshServerPingsAsync()
        {
            if (serverPingRunning)
            {
                return;
            }

            serverPingRunning = true;
            var entries = ANClient.GameServerSelector.ServerEntries();
            ThreadPool.QueueUserWorkItem(
                delegate
                {
                    var pings = new Dictionary<string, long?>();
                    for (var i = 0; i < entries.Length; i++)
                    {
                        pings[entries[i].Code] = ANClient.GameServerSelector.MeasureTcpPingMs(
                            entries[i].Code,
                            ANClient.GameServerSelector.ServerPingTimeoutMs);
                    }

                    try
                    {
                        BeginInvoke(
                            new MethodInvoker(
                                delegate
                                {
                                    serverPingRunning = false;
                                    serverPingMsByCode.Clear();
                                    foreach (var pair in pings)
                                    {
                                        serverPingMsByCode[pair.Key] = pair.Value;
                                    }

                                    UpdateServerDisplayNames();
                                }));
                    }
                    catch (ObjectDisposedException)
                    {
                        serverPingRunning = false;
                    }
                    catch (InvalidOperationException)
                    {
                        serverPingRunning = false;
                    }
                });
        }

        private void UpdateServerDisplayNames()
        {
            var selectedServerCode = comboGameServer.Items.Count == 0
                                          ? ANClient.GameServerSelector.DefaultServerCode
                                          : ANClient.GameServerSelector.CodeForDisplayValue(comboGameServer.Text);
            var entries = ANClient.GameServerSelector.ServerEntries();

            comboGameServer.BeginUpdate();
            comboGameServer.Items.Clear();
            for (var i = 0; i < entries.Length; i++)
            {
                long? pingMs;
                serverPingMsByCode.TryGetValue(entries[i].Code, out pingMs);
                comboGameServer.Items.Add(ANClient.GameServerSelector.DisplayName(entries[i].Code, pingMs));
            }

            if (comboGameServer.Items.Count > 0)
            {
                comboGameServer.SelectedIndex = ANClient.GameServerSelector.DisplayIndex(selectedServerCode);
            }

            comboGameServer.EndUpdate();
        }

        private void ButtonEditGameServers_Click(object sender, EventArgs e)
        {
            while (true)
            {
                using (var form = CreateEditServersDialog())
                {
                    var textServers = (TextBox)form.Controls["textServers"];
                    if (form.ShowDialog(this) != DialogResult.OK)
                    {
                        return;
                    }

                    string error;
                    if (ANClient.GameServerSelector.SaveEditableServerList(textServers.Text, out error))
                    {
                        serverPingMsByCode.Clear();
                        UpdateServerDisplayNames();
                        RefreshServerPingsAsync();
                        return;
                    }

                    MessageBox.Show(
                        error,
                        "Ошибка списка серверов",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error);
                }
            }
        }

        private Form CreateEditServersDialog()
        {
            var form = new Form();
            form.Text = "Редактирование серверов";
            form.StartPosition = FormStartPosition.CenterParent;
            form.FormBorderStyle = FormBorderStyle.FixedDialog;
            form.MinimizeBox = false;
            form.MaximizeBox = false;
            form.ClientSize = new System.Drawing.Size(430, 300);

            var label = new Label();
            label.Left = 10;
            label.Top = 10;
            label.Width = 410;
            label.Height = 32;
            label.Text = "Формат: CODE=host|server|title. Для neverlands.ru поле server можно оставить пустым.";

            var textServers = new TextBox();
            textServers.Name = "textServers";
            textServers.Left = 10;
            textServers.Top = 45;
            textServers.Width = 410;
            textServers.Height = 205;
            textServers.Multiline = true;
            textServers.ScrollBars = ScrollBars.Vertical;
            textServers.AcceptsReturn = true;
            textServers.AcceptsTab = true;
            textServers.Text = ANClient.GameServerSelector.EditableServerListText();

            var buttonReset = new Button();
            buttonReset.Left = 10;
            buttonReset.Top = 262;
            buttonReset.Width = 105;
            buttonReset.Text = "По умолчанию";
            buttonReset.Click += delegate { textServers.Text = ANClient.GameServerSelector.DefaultEditableServerListText(); };

            var buttonOkDialog = new Button();
            buttonOkDialog.Left = 250;
            buttonOkDialog.Top = 262;
            buttonOkDialog.Width = 80;
            buttonOkDialog.Text = "Сохранить";
            buttonOkDialog.DialogResult = DialogResult.OK;

            var buttonCancelDialog = new Button();
            buttonCancelDialog.Left = 340;
            buttonCancelDialog.Top = 262;
            buttonCancelDialog.Width = 80;
            buttonCancelDialog.Text = "Отмена";
            buttonCancelDialog.DialogResult = DialogResult.Cancel;

            form.Controls.Add(label);
            form.Controls.Add(textServers);
            form.Controls.Add(buttonReset);
            form.Controls.Add(buttonOkDialog);
            form.Controls.Add(buttonCancelDialog);
            form.AcceptButton = buttonOkDialog;
            form.CancelButton = buttonCancelDialog;
            return form;
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (serverPingTimer != null)
            {
                serverPingTimer.Stop();
                serverPingTimer.Dispose();
                serverPingTimer = null;
            }

            base.OnFormClosed(e);
        }
    }
}
