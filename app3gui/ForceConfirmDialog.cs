namespace AnLicenseGui;

/// <summary>
/// Подтверждение опасной операции <c>init-keys --force</c>.
///
/// Согласно <c>app3/instruction.md</c>, перевыпуск ключей ломает совместимость со ВСЕМИ ранее
/// выданными <c>profile.reg</c>. Поэтому кнопка разблокируется только после ручного ввода
/// слова FORCE — как и в существующем меню app3.
/// </summary>
public sealed class ForceConfirmDialog : Form
{
    private const string RequiredWord = "FORCE";

    private readonly TextBox _input = new();
    private readonly Button _ok = new();

    public ForceConfirmDialog()
    {
        Text = "Подтверждение перевыпуска ключей";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        StartPosition = FormStartPosition.CenterParent;
        MaximizeBox = false;
        MinimizeBox = false;
        ClientSize = new Size(540, 250);
        BackColor = Color.White;
        Font = new Font("Segoe UI", 9.5f);

        var warning = new Label
        {
            Text = "ВНИМАНИЕ!\n\n"
                   + "Перевыпуск ключей администратора сделает НЕДЕЙСТВИТЕЛЬНЫМИ все ранее выданные "
                   + "файлы profile.reg. Все пользователи потеряют доступ, пока им не будут выданы "
                   + "новые лицензии.\n\n"
                   + "Выполняйте только если точно понимаете последствия.",
            Dock = DockStyle.Top,
            Height = 120,
            ForeColor = Color.FromArgb(197, 42, 42),
            Padding = new Padding(16, 14, 16, 0)
        };

        var prompt = new Label
        {
            Text = $"Для подтверждения введите слово {RequiredWord}:",
            Dock = DockStyle.Top,
            Height = 26,
            Padding = new Padding(16, 0, 16, 0)
        };

        _input.Dock = DockStyle.Top;
        _input.Margin = new Padding(16);
        _input.Font = new Font("Consolas", 11f);
        _input.TextChanged += (_, _) =>
            _ok.Enabled = string.Equals(_input.Text.Trim(), RequiredWord, StringComparison.Ordinal);

        var inputHost = new Panel { Dock = DockStyle.Top, Height = 40, Padding = new Padding(16, 4, 16, 4) };
        inputHost.Controls.Add(_input);

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            Height = 52,
            FlowDirection = FlowDirection.RightToLeft,
            Padding = new Padding(16, 10, 16, 10)
        };

        var cancel = new Button
        {
            Text = "Отмена",
            DialogResult = DialogResult.Cancel,
            Width = 110,
            Height = 30,
            FlatStyle = FlatStyle.Flat
        };

        _ok.Text = "Перевыпустить";
        _ok.DialogResult = DialogResult.OK;
        _ok.Width = 140;
        _ok.Height = 30;
        _ok.Enabled = false;
        _ok.FlatStyle = FlatStyle.Flat;
        _ok.BackColor = Color.FromArgb(197, 42, 42);
        _ok.ForeColor = Color.White;
        _ok.FlatAppearance.BorderSize = 0;

        buttons.Controls.Add(cancel);
        buttons.Controls.Add(_ok);

        Controls.Add(buttons);
        Controls.Add(inputHost);
        Controls.Add(prompt);
        Controls.Add(warning);

        AcceptButton = _ok;
        CancelButton = cancel;
    }
}
