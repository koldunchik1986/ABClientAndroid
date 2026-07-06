namespace ANClient.ANForms
{
    using System;
    using System.Drawing;
    using System.Windows.Forms;

    internal sealed class FormSettingsAntiCaptcha : Form
    {
        private readonly CheckBox checkEnabled = new CheckBox();
        private readonly CheckBox checkLocalOcr = new CheckBox();
        private readonly TextBox textLocalOcrServiceUrl = new TextBox();
        private readonly NumericUpDown numericLocalConfidence = new NumericUpDown();
        private readonly CheckBox checkExternalFallback = new CheckBox();
        private readonly TextBox textApiKey = new TextBox();
        private readonly CheckBox checkPhrase = new CheckBox();
        private readonly CheckBox checkCase = new CheckBox();
        private readonly ComboBox comboNumeric = new ComboBox();
        private readonly CheckBox checkMath = new CheckBox();
        private readonly NumericUpDown numericMinLength = new NumericUpDown();
        private readonly NumericUpDown numericMaxLength = new NumericUpDown();
        private readonly ComboBox comboLanguagePool = new ComboBox();

        internal FormSettingsAntiCaptcha()
        {
            Text = "Anti-Captcha";
            Font = new Font("Tahoma", 8.25F, FontStyle.Regular, GraphicsUnit.Point, 204);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            StartPosition = FormStartPosition.CenterParent;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(520, 405);

            BuildUi();
            LoadSettings();
        }

        private void BuildUi()
        {
            checkEnabled.AutoSize = true;
            checkEnabled.Text = "Автораспознавание капчи";
            checkEnabled.Location = new Point(15, 15);
            Controls.Add(checkEnabled);

            checkLocalOcr.AutoSize = true;
            checkLocalOcr.Text = "Локальный Keras OCR через localhost API";
            checkLocalOcr.Location = new Point(15, 48);
            Controls.Add(checkLocalOcr);

            var labelLocalService = new Label();
            labelLocalService.AutoSize = true;
            labelLocalService.Text = "Service URL:";
            labelLocalService.Location = new Point(15, 82);
            Controls.Add(labelLocalService);

            textLocalOcrServiceUrl.Location = new Point(112, 79);
            textLocalOcrServiceUrl.Size = new Size(380, 21);
            Controls.Add(textLocalOcrServiceUrl);

            var labelConfidence = new Label();
            labelConfidence.AutoSize = true;
            labelConfidence.Text = "Мин. confidence (0 = не блокировать):";
            labelConfidence.Location = new Point(15, 115);
            Controls.Add(labelConfidence);

            numericLocalConfidence.Minimum = 0;
            numericLocalConfidence.Maximum = 1;
            numericLocalConfidence.DecimalPlaces = 2;
            numericLocalConfidence.Increment = 0.05M;
            numericLocalConfidence.Location = new Point(235, 112);
            numericLocalConfidence.Size = new Size(65, 21);
            Controls.Add(numericLocalConfidence);

            checkExternalFallback.AutoSize = true;
            checkExternalFallback.Text = "Если локальный OCR не сработал, использовать anti-captcha.com";
            checkExternalFallback.Location = new Point(15, 148);
            Controls.Add(checkExternalFallback);

            var labelApiKey = new Label();
            labelApiKey.AutoSize = true;
            labelApiKey.Text = "API key:";
            labelApiKey.Location = new Point(15, 181);
            Controls.Add(labelApiKey);

            textApiKey.Location = new Point(112, 178);
            textApiKey.Size = new Size(380, 21);
            Controls.Add(textApiKey);

            checkPhrase.AutoSize = true;
            checkPhrase.Text = "phrase";
            checkPhrase.Location = new Point(15, 215);
            Controls.Add(checkPhrase);

            checkCase.AutoSize = true;
            checkCase.Text = "case sensitive";
            checkCase.Location = new Point(95, 215);
            Controls.Add(checkCase);

            checkMath.AutoSize = true;
            checkMath.Text = "math";
            checkMath.Location = new Point(225, 215);
            Controls.Add(checkMath);

            var labelNumeric = new Label();
            labelNumeric.AutoSize = true;
            labelNumeric.Text = "numeric:";
            labelNumeric.Location = new Point(15, 251);
            Controls.Add(labelNumeric);

            comboNumeric.DropDownStyle = ComboBoxStyle.DropDownList;
            comboNumeric.Items.Add("0 - любые символы");
            comboNumeric.Items.Add("1 - только цифры");
            comboNumeric.Items.Add("2 - без цифр");
            comboNumeric.Location = new Point(82, 248);
            comboNumeric.Size = new Size(155, 21);
            Controls.Add(comboNumeric);

            var labelLanguage = new Label();
            labelLanguage.AutoSize = true;
            labelLanguage.Text = "languagePool:";
            labelLanguage.Location = new Point(260, 251);
            Controls.Add(labelLanguage);

            comboLanguagePool.DropDownStyle = ComboBoxStyle.DropDownList;
            comboLanguagePool.Items.Add("en");
            comboLanguagePool.Items.Add("rn");
            comboLanguagePool.Location = new Point(350, 248);
            comboLanguagePool.Size = new Size(82, 21);
            Controls.Add(comboLanguagePool);

            var labelMin = new Label();
            labelMin.AutoSize = true;
            labelMin.Text = "minLength:";
            labelMin.Location = new Point(15, 286);
            Controls.Add(labelMin);

            numericMinLength.Minimum = 0;
            numericMinLength.Maximum = 20;
            numericMinLength.Location = new Point(82, 284);
            numericMinLength.Size = new Size(55, 21);
            Controls.Add(numericMinLength);

            var labelMax = new Label();
            labelMax.AutoSize = true;
            labelMax.Text = "maxLength:";
            labelMax.Location = new Point(165, 286);
            Controls.Add(labelMax);

            numericMaxLength.Minimum = 0;
            numericMaxLength.Maximum = 20;
            numericMaxLength.Location = new Point(235, 284);
            numericMaxLength.Size = new Size(55, 21);
            Controls.Add(numericMaxLength);

            var buttonOk = new Button();
            buttonOk.Text = "OK";
            buttonOk.Location = new Point(335, 365);
            buttonOk.DialogResult = DialogResult.OK;
            buttonOk.Click += buttonOk_Click;
            Controls.Add(buttonOk);

            var buttonCancel = new Button();
            buttonCancel.Text = "Отмена";
            buttonCancel.Location = new Point(417, 365);
            buttonCancel.DialogResult = DialogResult.Cancel;
            Controls.Add(buttonCancel);

            AcceptButton = buttonOk;
            CancelButton = buttonCancel;
        }

        private void LoadSettings()
        {
            checkEnabled.Checked = AppVars.Profile.AntiCaptchaEnabled;
            checkLocalOcr.Checked = AppVars.Profile.LocalCaptchaOcrEnabled;
            textLocalOcrServiceUrl.Text = AppVars.Profile.LocalCaptchaOcrServiceUrl ?? string.Empty;
            numericLocalConfidence.Value = Convert.ToDecimal(Math.Max(0d, Math.Min(1d, AppVars.Profile.LocalCaptchaOcrMinConfidence)));
            checkExternalFallback.Checked = AppVars.Profile.LocalCaptchaExternalFallbackEnabled;
            textApiKey.Text = AppVars.Profile.AntiCaptchaApiKey ?? string.Empty;
            checkPhrase.Checked = AppVars.Profile.AntiCaptchaPhrase;
            checkCase.Checked = AppVars.Profile.AntiCaptchaCaseSensitive;
            comboNumeric.SelectedIndex = Math.Max(0, Math.Min(2, AppVars.Profile.AntiCaptchaNumeric));
            checkMath.Checked = AppVars.Profile.AntiCaptchaMath == 1;
            numericMinLength.Value = Math.Max(0, Math.Min(20, AppVars.Profile.AntiCaptchaMinLength));
            numericMaxLength.Value = Math.Max(0, Math.Min(20, AppVars.Profile.AntiCaptchaMaxLength));
            comboLanguagePool.SelectedItem = AppVars.Profile.AntiCaptchaLanguagePool == "rn" ? "rn" : "en";
        }

        private void buttonOk_Click(object sender, EventArgs e)
        {
            AppVars.Profile.AntiCaptchaEnabled = checkEnabled.Checked;
            AppVars.Profile.LocalCaptchaOcrEnabled = checkLocalOcr.Checked;
            AppVars.Profile.LocalCaptchaOcrServiceUrl = textLocalOcrServiceUrl.Text.Trim();
            AppVars.Profile.LocalCaptchaOcrMinConfidence = Convert.ToDouble(numericLocalConfidence.Value);
            AppVars.Profile.LocalCaptchaExternalFallbackEnabled = checkExternalFallback.Checked;
            AppVars.Profile.AntiCaptchaApiKey = textApiKey.Text.Trim();
            AppVars.Profile.AntiCaptchaPhrase = checkPhrase.Checked;
            AppVars.Profile.AntiCaptchaCaseSensitive = checkCase.Checked;
            AppVars.Profile.AntiCaptchaNumeric = Math.Max(0, comboNumeric.SelectedIndex);
            AppVars.Profile.AntiCaptchaMath = checkMath.Checked ? 1 : 0;
            AppVars.Profile.AntiCaptchaMinLength = Convert.ToInt32(numericMinLength.Value);
            AppVars.Profile.AntiCaptchaMaxLength = Convert.ToInt32(numericMaxLength.Value);
            AppVars.Profile.AntiCaptchaLanguagePool = Convert.ToString(comboLanguagePool.SelectedItem) == "rn" ? "rn" : "en";
            AppVars.Profile.Save();
            Close();
        }
    }
}
