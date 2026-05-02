namespace ANClient.ANForms
{
    using System;
    using System.Drawing;
    using System.Windows.Forms;

    internal sealed class FormSettingsAntiCaptcha : Form
    {
        private readonly CheckBox checkEnabled = new CheckBox();
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
            ClientSize = new Size(460, 260);

            BuildUi();
            LoadSettings();
        }

        private void BuildUi()
        {
            checkEnabled.AutoSize = true;
            checkEnabled.Text = "Использовать Anti-Captcha (anti-captcha.com)";
            checkEnabled.Location = new Point(15, 15);
            Controls.Add(checkEnabled);

            var labelApiKey = new Label();
            labelApiKey.AutoSize = true;
            labelApiKey.Text = "API key:";
            labelApiKey.Location = new Point(15, 48);
            Controls.Add(labelApiKey);

            textApiKey.Location = new Point(82, 45);
            textApiKey.Size = new Size(350, 21);
            Controls.Add(textApiKey);

            checkPhrase.AutoSize = true;
            checkPhrase.Text = "phrase";
            checkPhrase.Location = new Point(15, 82);
            Controls.Add(checkPhrase);

            checkCase.AutoSize = true;
            checkCase.Text = "case sensitive";
            checkCase.Location = new Point(95, 82);
            Controls.Add(checkCase);

            checkMath.AutoSize = true;
            checkMath.Text = "math";
            checkMath.Location = new Point(225, 82);
            Controls.Add(checkMath);

            var labelNumeric = new Label();
            labelNumeric.AutoSize = true;
            labelNumeric.Text = "numeric:";
            labelNumeric.Location = new Point(15, 118);
            Controls.Add(labelNumeric);

            comboNumeric.DropDownStyle = ComboBoxStyle.DropDownList;
            comboNumeric.Items.Add("0 - любые символы");
            comboNumeric.Items.Add("1 - только цифры");
            comboNumeric.Items.Add("2 - без цифр");
            comboNumeric.Location = new Point(82, 115);
            comboNumeric.Size = new Size(155, 21);
            Controls.Add(comboNumeric);

            var labelLanguage = new Label();
            labelLanguage.AutoSize = true;
            labelLanguage.Text = "languagePool:";
            labelLanguage.Location = new Point(260, 118);
            Controls.Add(labelLanguage);

            comboLanguagePool.DropDownStyle = ComboBoxStyle.DropDownList;
            comboLanguagePool.Items.Add("en");
            comboLanguagePool.Items.Add("rn");
            comboLanguagePool.Location = new Point(350, 115);
            comboLanguagePool.Size = new Size(82, 21);
            Controls.Add(comboLanguagePool);

            var labelMin = new Label();
            labelMin.AutoSize = true;
            labelMin.Text = "minLength:";
            labelMin.Location = new Point(15, 153);
            Controls.Add(labelMin);

            numericMinLength.Minimum = 0;
            numericMinLength.Maximum = 20;
            numericMinLength.Location = new Point(82, 151);
            numericMinLength.Size = new Size(55, 21);
            Controls.Add(numericMinLength);

            var labelMax = new Label();
            labelMax.AutoSize = true;
            labelMax.Text = "maxLength:";
            labelMax.Location = new Point(165, 153);
            Controls.Add(labelMax);

            numericMaxLength.Minimum = 0;
            numericMaxLength.Maximum = 20;
            numericMaxLength.Location = new Point(235, 151);
            numericMaxLength.Size = new Size(55, 21);
            Controls.Add(numericMaxLength);

            var buttonOk = new Button();
            buttonOk.Text = "OK";
            buttonOk.Location = new Point(275, 215);
            buttonOk.DialogResult = DialogResult.OK;
            buttonOk.Click += buttonOk_Click;
            Controls.Add(buttonOk);

            var buttonCancel = new Button();
            buttonCancel.Text = "Отмена";
            buttonCancel.Location = new Point(357, 215);
            buttonCancel.DialogResult = DialogResult.Cancel;
            Controls.Add(buttonCancel);

            AcceptButton = buttonOk;
            CancelButton = buttonCancel;
        }

        private void LoadSettings()
        {
            checkEnabled.Checked = AppVars.Profile.AntiCaptchaEnabled;
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
