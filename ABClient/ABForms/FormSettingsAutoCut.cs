namespace ABClient.ABForms
{
    using System;
    using System.Drawing;
    using System.Text;
    using System.Windows.Forms;

    internal partial class FormSettingsAutoCut : Form
    {
        private TextBox textAutoCutCells;
        private CheckBox checkAutoCutCleanup;
        private CheckBox checkAutoCutByTimers;
        private TextBox textAutoCutShiftSchedule;
        private CheckedListBox checkedListSickles;
        private CheckBox checkAntiCaptchaEnabled;
        private TextBox textAntiCaptchaApiKey;
        private CheckBox checkAntiCaptchaPhrase;
        private CheckBox checkAntiCaptchaCase;
        private ComboBox comboAntiCaptchaNumeric;
        private CheckBox checkAntiCaptchaMath;
        private NumericUpDown numericAntiCaptchaMinLength;
        private NumericUpDown numericAntiCaptchaMaxLength;
        private ComboBox comboAntiCaptchaLanguagePool;

        internal FormSettingsAutoCut()
        {
            InitializeComponent();
            BuildAdditionalSettingsUi();
            AutoCutCatalog.EnsureProfileCatalog(AppVars.Profile);
            FillHerbsList();
            LoadSettingsToControls();
        }

        private void BuildAdditionalSettingsUi()
        {
            ClientSize = new Size(900, 620);

            listViewHerbs.Location = new Point(12, 29);
            listViewHerbs.Size = new Size(405, 495);
            listViewHerbs.Columns.Clear();
            listViewHerbs.Columns.Add("Трава", 145);
            listViewHerbs.Columns.Add("ID", 42);
            listViewHerbs.Columns.Add("Умение", 58);
            listViewHerbs.Columns.Add("Рост", 52);
            listViewHerbs.Columns.Add("Клетка", 80);
            listViewHerbs.HeaderStyle = ColumnHeaderStyle.Clickable;

            buttonSelectAll.Location = new Point(12, 535);
            buttonUnselectAll.Location = new Point(150, 535);
            buttonAccept.Location = new Point(680, 585);
            buttonCancel.Location = new Point(784, 585);

            var groupAutoCut = new GroupBox();
            groupAutoCut.Text = "Авто-Травник";
            groupAutoCut.Location = new Point(430, 12);
            groupAutoCut.Size = new Size(440, 280);
            Controls.Add(groupAutoCut);

            checkDoAutoCutWriteChat.Location = new Point(15, 22);
            groupAutoCut.Controls.Add(checkDoAutoCutWriteChat);

            var labelCells = new Label();
            labelCells.AutoSize = true;
            labelCells.Text = "Клетки обхода (CSV: 12-34, 12-35):";
            labelCells.Location = new Point(15, 52);
            groupAutoCut.Controls.Add(labelCells);

            textAutoCutCells = new TextBox();
            textAutoCutCells.Location = new Point(15, 70);
            textAutoCutCells.Size = new Size(405, 21);
            groupAutoCut.Controls.Add(textAutoCutCells);

            checkAutoCutCleanup = new CheckBox();
            checkAutoCutCleanup.AutoSize = true;
            checkAutoCutCleanup.Text = "Открывать инвентарь для cleanup после роста массы";
            checkAutoCutCleanup.Location = new Point(15, 98);
            groupAutoCut.Controls.Add(checkAutoCutCleanup);

            checkAutoCutByTimers = new CheckBox();
            checkAutoCutByTimers.AutoSize = true;
            checkAutoCutByTimers.Text = "Срезать по таймерам роста";
            checkAutoCutByTimers.Location = new Point(15, 122);
            groupAutoCut.Controls.Add(checkAutoCutByTimers);

            var labelShifts = new Label();
            labelShifts.AutoSize = true;
            labelShifts.Text = "Смены трав:";
            labelShifts.Location = new Point(15, 148);
            groupAutoCut.Controls.Add(labelShifts);

            textAutoCutShiftSchedule = new TextBox();
            textAutoCutShiftSchedule.AcceptsReturn = true;
            textAutoCutShiftSchedule.Multiline = true;
            textAutoCutShiftSchedule.ScrollBars = ScrollBars.Vertical;
            textAutoCutShiftSchedule.Location = new Point(15, 166);
            textAutoCutShiftSchedule.Size = new Size(180, 96);
            groupAutoCut.Controls.Add(textAutoCutShiftSchedule);

            var labelSickles = new Label();
            labelSickles.AutoSize = true;
            labelSickles.Text = "Разрешённые серпы:";
            labelSickles.Location = new Point(210, 148);
            groupAutoCut.Controls.Add(labelSickles);

            checkedListSickles = new CheckedListBox();
            checkedListSickles.CheckOnClick = true;
            checkedListSickles.Location = new Point(210, 166);
            checkedListSickles.Size = new Size(210, 94);
            groupAutoCut.Controls.Add(checkedListSickles);

            var groupAntiCaptcha = new GroupBox();
            groupAntiCaptcha.Text = "Auto-Captcha / anti-captcha.com";
            groupAntiCaptcha.Location = new Point(430, 305);
            groupAntiCaptcha.Size = new Size(440, 250);
            Controls.Add(groupAntiCaptcha);

            checkAntiCaptchaEnabled = new CheckBox();
            checkAntiCaptchaEnabled.AutoSize = true;
            checkAntiCaptchaEnabled.Text = "Использовать Anti-Captcha (anti-captcha.com)";
            checkAntiCaptchaEnabled.Location = new Point(15, 24);
            groupAntiCaptcha.Controls.Add(checkAntiCaptchaEnabled);

            var labelApiKey = new Label();
            labelApiKey.AutoSize = true;
            labelApiKey.Text = "API key:";
            labelApiKey.Location = new Point(15, 53);
            groupAntiCaptcha.Controls.Add(labelApiKey);

            textAntiCaptchaApiKey = new TextBox();
            textAntiCaptchaApiKey.Location = new Point(75, 50);
            textAntiCaptchaApiKey.Size = new Size(345, 21);
            groupAntiCaptcha.Controls.Add(textAntiCaptchaApiKey);

            checkAntiCaptchaPhrase = new CheckBox();
            checkAntiCaptchaPhrase.AutoSize = true;
            checkAntiCaptchaPhrase.Text = "phrase";
            checkAntiCaptchaPhrase.Location = new Point(15, 84);
            groupAntiCaptcha.Controls.Add(checkAntiCaptchaPhrase);

            checkAntiCaptchaCase = new CheckBox();
            checkAntiCaptchaCase.AutoSize = true;
            checkAntiCaptchaCase.Text = "case sensitive";
            checkAntiCaptchaCase.Location = new Point(95, 84);
            groupAntiCaptcha.Controls.Add(checkAntiCaptchaCase);

            checkAntiCaptchaMath = new CheckBox();
            checkAntiCaptchaMath.AutoSize = true;
            checkAntiCaptchaMath.Text = "math";
            checkAntiCaptchaMath.Location = new Point(220, 84);
            groupAntiCaptcha.Controls.Add(checkAntiCaptchaMath);

            var labelNumeric = new Label();
            labelNumeric.AutoSize = true;
            labelNumeric.Text = "numeric:";
            labelNumeric.Location = new Point(15, 118);
            groupAntiCaptcha.Controls.Add(labelNumeric);

            comboAntiCaptchaNumeric = new ComboBox();
            comboAntiCaptchaNumeric.DropDownStyle = ComboBoxStyle.DropDownList;
            comboAntiCaptchaNumeric.Items.Add("0 - любые символы");
            comboAntiCaptchaNumeric.Items.Add("1 - только цифры");
            comboAntiCaptchaNumeric.Items.Add("2 - без цифр");
            comboAntiCaptchaNumeric.Location = new Point(75, 115);
            comboAntiCaptchaNumeric.Size = new Size(150, 21);
            groupAntiCaptcha.Controls.Add(comboAntiCaptchaNumeric);

            var labelLanguage = new Label();
            labelLanguage.AutoSize = true;
            labelLanguage.Text = "languagePool:";
            labelLanguage.Location = new Point(240, 118);
            groupAntiCaptcha.Controls.Add(labelLanguage);

            comboAntiCaptchaLanguagePool = new ComboBox();
            comboAntiCaptchaLanguagePool.DropDownStyle = ComboBoxStyle.DropDownList;
            comboAntiCaptchaLanguagePool.Items.Add("en");
            comboAntiCaptchaLanguagePool.Items.Add("rn");
            comboAntiCaptchaLanguagePool.Location = new Point(325, 115);
            comboAntiCaptchaLanguagePool.Size = new Size(95, 21);
            groupAntiCaptcha.Controls.Add(comboAntiCaptchaLanguagePool);

            var labelMin = new Label();
            labelMin.AutoSize = true;
            labelMin.Text = "minLength:";
            labelMin.Location = new Point(15, 153);
            groupAntiCaptcha.Controls.Add(labelMin);

            numericAntiCaptchaMinLength = new NumericUpDown();
            numericAntiCaptchaMinLength.Minimum = 0;
            numericAntiCaptchaMinLength.Maximum = 20;
            numericAntiCaptchaMinLength.Location = new Point(85, 151);
            numericAntiCaptchaMinLength.Size = new Size(55, 21);
            groupAntiCaptcha.Controls.Add(numericAntiCaptchaMinLength);

            var labelMax = new Label();
            labelMax.AutoSize = true;
            labelMax.Text = "maxLength:";
            labelMax.Location = new Point(160, 153);
            groupAntiCaptcha.Controls.Add(labelMax);

            numericAntiCaptchaMaxLength = new NumericUpDown();
            numericAntiCaptchaMaxLength.Minimum = 0;
            numericAntiCaptchaMaxLength.Maximum = 20;
            numericAntiCaptchaMaxLength.Location = new Point(230, 151);
            numericAntiCaptchaMaxLength.Size = new Size(55, 21);
            groupAntiCaptcha.Controls.Add(numericAntiCaptchaMaxLength);
        }

        private void FillHerbsList()
        {
            listViewHerbs.BeginUpdate();
            listViewHerbs.Items.Clear();
            listViewHerbs.Groups.Clear();
            for (var i = 0; i < AppVars.Profile.AutoCutHerbs.Count; i++)
            {
                var herb = AppVars.Profile.AutoCutHerbs[i];
                if (herb == null || string.IsNullOrEmpty(herb.Name))
                {
                    continue;
                }

                var group = GetOrCreateGroup(herb.Group);
                var item = new ListViewItem(herb.Name, group);
                item.Checked = herb.Selected;
                item.Tag = herb;
                item.SubItems.Add(herb.Id ?? string.Empty);
                item.SubItems.Add(herb.Skill.ToString());
                item.SubItems.Add(herb.GrowthMinutes.ToString());
                item.SubItems.Add(herb.LastLocation ?? string.Empty);
                item.ToolTipText = string.Format(
                    "id={0}; умение={1}; рост={2} мин; группа={3}; последняя клетка={4}",
                    herb.Id,
                    herb.Skill,
                    herb.GrowthMinutes,
                    AutoCutCatalog.NormalizeGroupHeader(herb.Group),
                    herb.LastLocation);
                listViewHerbs.Items.Add(item);
            }

            listViewHerbs.EndUpdate();
        }

        private ListViewGroup GetOrCreateGroup(string group)
        {
            var header = AutoCutCatalog.NormalizeGroupHeader(group);
            for (var i = 0; i < listViewHerbs.Groups.Count; i++)
            {
                if (listViewHerbs.Groups[i].Header.Equals(header, StringComparison.OrdinalIgnoreCase))
                {
                    return listViewHerbs.Groups[i];
                }
            }

            var result = new ListViewGroup(header, HorizontalAlignment.Left);
            listViewHerbs.Groups.Add(result);
            return result;
        }

        private void LoadSettingsToControls()
        {
            checkDoAutoCutWriteChat.Checked = AppVars.Profile.DoAutoCutWriteChat;
            textAutoCutCells.Text = AppVars.Profile.AutoCutSearchCellsCsv ?? string.Empty;
            checkAutoCutCleanup.Checked = AppVars.Profile.AutoCutCleanupEnabled;
            checkAutoCutByTimers.Checked = AppVars.Profile.AutoCutByTimers;
            textAutoCutShiftSchedule.Text = string.IsNullOrEmpty(AppVars.Profile.AutoCutShiftSchedule)
                                                ? AutoCutCatalog.DefaultShiftSchedule
                                                : AppVars.Profile.AutoCutShiftSchedule;
            LoadSicklesToControls();

            checkAntiCaptchaEnabled.Checked = AppVars.Profile.AntiCaptchaEnabled;
            textAntiCaptchaApiKey.Text = AppVars.Profile.AntiCaptchaApiKey ?? string.Empty;
            checkAntiCaptchaPhrase.Checked = AppVars.Profile.AntiCaptchaPhrase;
            checkAntiCaptchaCase.Checked = AppVars.Profile.AntiCaptchaCaseSensitive;
            comboAntiCaptchaNumeric.SelectedIndex = Math.Max(0, Math.Min(2, AppVars.Profile.AntiCaptchaNumeric));
            checkAntiCaptchaMath.Checked = AppVars.Profile.AntiCaptchaMath == 1;
            numericAntiCaptchaMinLength.Value = Math.Max(0, Math.Min(20, AppVars.Profile.AntiCaptchaMinLength));
            numericAntiCaptchaMaxLength.Value = Math.Max(0, Math.Min(20, AppVars.Profile.AntiCaptchaMaxLength));
            comboAntiCaptchaLanguagePool.SelectedItem = AppVars.Profile.AntiCaptchaLanguagePool == "rn" ? "rn" : "en";
        }

        private void LoadSicklesToControls()
        {
            checkedListSickles.Items.Clear();
            var names = ParsedDressed.GetAutoCutSickleNames();
            var saved = AppVars.Profile.AutoCutSicklesCsv ?? string.Empty;
            var useDefault = string.IsNullOrEmpty(saved);
            for (var i = 0; i < names.Length; i++)
            {
                checkedListSickles.Items.Add(names[i], useDefault || ContainsCsvToken(saved, names[i]));
            }
        }

        private void buttonSelectAll_Click(object sender, EventArgs e)
        {
            for (var i = 0; i < listViewHerbs.Items.Count; i++)
            {
                listViewHerbs.Items[i].Checked = true;
            }
        }

        private void buttonUnselectAll_Click(object sender, EventArgs e)
        {
            for (var i = 0; i < listViewHerbs.Items.Count; i++)
            {
                listViewHerbs.Items[i].Checked = false;
            }
        }

        private void buttonAccept_Click(object sender, EventArgs e)
        {
            AppVars.Profile.HerbsAutoCut.Clear();
            for (var i = 0; i < listViewHerbs.Items.Count; i++)
            {
                var herb = listViewHerbs.Items[i].Tag as AutoCutHerbInfo;
                if (herb != null)
                {
                    herb.Selected = listViewHerbs.Items[i].Checked;
                }

                if (listViewHerbs.Items[i].Checked)
                {
                    AppVars.Profile.HerbsAutoCut.Add(herb == null ? listViewHerbs.Items[i].Text : herb.Name);
                }
            }

            AppVars.Profile.DoAutoCutWriteChat = checkDoAutoCutWriteChat.Checked;
            AppVars.Profile.AutoCutSearchCellsCsv = textAutoCutCells.Text.Trim();
            AppVars.Profile.AutoCutCleanupEnabled = checkAutoCutCleanup.Checked;
            AppVars.Profile.AutoCutByTimers = checkAutoCutByTimers.Checked;
            AppVars.Profile.AutoCutShiftSchedule = textAutoCutShiftSchedule.Text.Trim();
            AppVars.Profile.AutoCutSicklesCsv = BuildSicklesCsv();

            AppVars.Profile.AntiCaptchaEnabled = checkAntiCaptchaEnabled.Checked;
            AppVars.Profile.AntiCaptchaApiKey = textAntiCaptchaApiKey.Text.Trim();
            AppVars.Profile.AntiCaptchaPhrase = checkAntiCaptchaPhrase.Checked;
            AppVars.Profile.AntiCaptchaCaseSensitive = checkAntiCaptchaCase.Checked;
            AppVars.Profile.AntiCaptchaNumeric = Math.Max(0, comboAntiCaptchaNumeric.SelectedIndex);
            AppVars.Profile.AntiCaptchaMath = checkAntiCaptchaMath.Checked ? 1 : 0;
            AppVars.Profile.AntiCaptchaMinLength = Convert.ToInt32(numericAntiCaptchaMinLength.Value);
            AppVars.Profile.AntiCaptchaMaxLength = Convert.ToInt32(numericAntiCaptchaMaxLength.Value);
            AppVars.Profile.AntiCaptchaLanguagePool = Convert.ToString(comboAntiCaptchaLanguagePool.SelectedItem) == "rn" ? "rn" : "en";

            AppVars.DoHerbAutoCut = AppVars.Profile.HerbsAutoCut.Count > 0;
            if (AppVars.DoHerbAutoCut)
            {
                AutoCutRuntime.ResetRuntime("settings_saved");
                AppVars.AutoCutCheckSickle = true;
                AppVars.AutoCutArmedSickle = false;
            }

            AppVars.Profile.Save();
            Close();
        }

        private string BuildSicklesCsv()
        {
            var builder = new StringBuilder();
            for (var i = 0; i < checkedListSickles.Items.Count; i++)
            {
                if (!checkedListSickles.GetItemChecked(i))
                {
                    continue;
                }

                if (builder.Length > 0)
                {
                    builder.Append('|');
                }

                builder.Append(Convert.ToString(checkedListSickles.Items[i]));
            }

            return builder.ToString();
        }

        private static bool ContainsCsvToken(string csv, string token)
        {
            if (string.IsNullOrEmpty(csv) || string.IsNullOrEmpty(token))
            {
                return false;
            }

            var parts = csv.Split('|');
            for (var i = 0; i < parts.Length; i++)
            {
                if (parts[i].Trim().Equals(token, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }
    }
}
