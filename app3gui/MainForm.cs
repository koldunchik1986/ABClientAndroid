using System.ComponentModel;

namespace AnLicenseGui;

/// <summary>Операции, поддерживаемые Java-инструментом (паритет с app3_menu).</summary>
public enum Operation
{
    Inspect,
    DecodeRequest,
    Issue,
    InitKeys,
    InitKeysForce,
    BuildApp3
}

public sealed class MainForm : Form
{
    // Палитра «тёмная панель + светлый журнал».
    private static readonly Color ColorBg = Color.FromArgb(245, 246, 248);
    private static readonly Color ColorPanel = Color.White;
    private static readonly Color ColorAccent = Color.FromArgb(0, 120, 212);
    private static readonly Color ColorDanger = Color.FromArgb(197, 42, 42);
    private static readonly Color ColorLogBg = Color.FromArgb(30, 30, 32);

    private ProjectPaths? _paths;
    private ToolRunner? _runner;
    private CancellationTokenSource? _cts;

    private readonly ComboBox _operationCombo = new();
    private readonly Label _operationHint = new();

    private readonly TextBox _requestPath = new();
    private readonly Button _requestBrowse = new();
    private readonly Label _requestLabel = new();

    private readonly TextBox _licensePath = new();
    private readonly Button _licenseBrowse = new();
    private readonly Label _licenseLabel = new();

    private readonly ComboBox _expiresCombo = new();
    private readonly Label _expiresLabel = new();

    private readonly ComboBox _grantCombo = new();
    private readonly Label _grantLabel = new();

    private readonly ComboBox _publicCombo = new();
    private readonly Label _publicLabel = new();

    private readonly TextBox _reportPath = new();
    private readonly Button _reportBrowse = new();
    private readonly Label _reportLabel = new();

    private readonly Button _runButton = new();
    private readonly Button _cancelButton = new();

    private readonly RichTextBox _log = new();
    private readonly Label _envLabel = new();
    private readonly StatusStrip _status = new();
    private readonly ToolStripStatusLabel _statusText = new();
    private readonly ToolStripProgressBar _progress = new();

    public MainForm()
    {
        Text = "ANClient — менеджер лицензий";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(1000, 720);
        Size = new Size(1180, 820);
        BackColor = ColorBg;
        Font = new Font("Segoe UI", 9.5f);

        BuildUi();
        DiscoverEnvironment();
        UpdateParameterVisibility();
    }

    // ------------------------------------------------------------------
    // Построение интерфейса
    // ------------------------------------------------------------------

    private void BuildUi()
    {
        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 4,
            Padding = new Padding(14, 12, 14, 4),
            BackColor = ColorBg
        };
        root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        root.Controls.Add(BuildEnvironmentPanel(), 0, 0);
        root.Controls.Add(BuildOperationPanel(), 0, 1);
        root.Controls.Add(BuildLogPanel(), 0, 2);

        Controls.Add(root);

        _statusText.Text = "Готово";
        _statusText.Spring = true;
        _statusText.TextAlign = ContentAlignment.MiddleLeft;
        _progress.Visible = false;
        _progress.Style = ProgressBarStyle.Marquee;
        _status.Items.Add(_statusText);
        _status.Items.Add(_progress);
        _status.SizingGrip = false;
        Controls.Add(_status);
    }

    private Control BuildEnvironmentPanel()
    {
        var group = new GroupBox
        {
            Text = "  Окружение  ",
            Dock = DockStyle.Top,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Padding = new Padding(12, 8, 12, 12),
            BackColor = ColorPanel,
            ForeColor = Color.FromArgb(60, 60, 60),
            Font = new Font("Segoe UI Semibold", 9.5f)
        };

        _envLabel.AutoSize = true;
        _envLabel.Font = new Font("Consolas", 9f);
        _envLabel.ForeColor = Color.FromArgb(70, 70, 70);
        _envLabel.Dock = DockStyle.Top;
        _envLabel.Padding = new Padding(0, 4, 0, 8);

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            WrapContents = false
        };

        buttons.Controls.Add(MakeButton("Обновить сведения", (_, _) => DiscoverEnvironment(), 150));
        buttons.Controls.Add(MakeButton("Выбрать корень проекта…", (_, _) => PickRoot(), 190));
        buttons.Controls.Add(MakeButton("Открыть папку заявок", (_, _) => OpenFolder(_paths?.RequestDir), 170));
        buttons.Controls.Add(MakeButton("Открыть папку ключей", (_, _) => OpenFolder(_paths?.KeysDir), 170));

        var host = new Panel { Dock = DockStyle.Top, AutoSize = true };
        host.Controls.Add(buttons);
        host.Controls.Add(_envLabel);
        group.Controls.Add(host);
        return group;
    }

    private Control BuildOperationPanel()
    {
        var group = new GroupBox
        {
            Text = "  Операция  ",
            Dock = DockStyle.Top,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Padding = new Padding(12, 10, 12, 12),
            BackColor = ColorPanel,
            ForeColor = Color.FromArgb(60, 60, 60),
            Font = new Font("Segoe UI Semibold", 9.5f)
        };

        var grid = new TableLayoutPanel
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            ColumnCount = 3,
            Padding = new Padding(0, 6, 0, 0)
        };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 190));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 110));

        _operationCombo.DropDownStyle = ComboBoxStyle.DropDownList;
        _operationCombo.Font = new Font("Segoe UI", 10f);
        _operationCombo.Dock = DockStyle.Fill;
        _operationCombo.Items.AddRange(new object[]
        {
            "Проверить файл лицензии",
            "Прочитать заявку от устройства",
            "Выдать / обновить доступ",
            "Подготовить ключи администратора",
            "ПЕРЕВЫПУСТИТЬ ключи (ломает все лицензии)",
            "Собрать app3"
        });
        _operationCombo.SelectedIndex = 0;
        _operationCombo.SelectedIndexChanged += (_, _) => UpdateParameterVisibility();

        AddRow(grid, MakeLabel("Что сделать:"), _operationCombo, null);

        _operationHint.AutoSize = false;
        _operationHint.Dock = DockStyle.Fill;
        _operationHint.Height = 34;
        _operationHint.ForeColor = Color.FromArgb(110, 110, 110);
        _operationHint.Font = new Font("Segoe UI", 9f, FontStyle.Italic);
        AddRow(grid, MakeLabel(string.Empty), _operationHint, null);

        ConfigurePathBox(_requestPath);
        ConfigureBrowse(_requestBrowse, () => PickFile(_requestPath, "Заявка от устройства (*.txt)|*.txt|Все файлы|*.*"));
        _requestLabel.Text = "Заявка (request.txt):";
        AddRow(grid, _requestLabel, _requestPath, _requestBrowse);

        ConfigurePathBox(_licensePath);
        ConfigureBrowse(_licenseBrowse, () => PickFile(_licensePath, "Файл лицензии (*.reg)|*.reg|Все файлы|*.*"));
        _licenseLabel.Text = "Файл лицензии (profile.reg):";
        AddRow(grid, _licenseLabel, _licensePath, _licenseBrowse);

        ConfigurePathBox(_reportPath);
        ConfigureBrowse(_reportBrowse, () => PickSaveFile(_reportPath, "Отчёт (*.txt)|*.txt"));
        _reportLabel.Text = "Отчёт (необязательно):";
        AddRow(grid, _reportLabel, _reportPath, _reportBrowse);

        ConfigureEditableCombo(_expiresCombo, new[]
        {
            "0  — без срока",
            "10m — 10 минут",
            "2h — 2 часа",
            "7d — 7 дней",
            "30d — 30 дней"
        });
        _expiresLabel.Text = "Срок доступа:";
        AddRow(grid, _expiresLabel, _expiresCombo, null);

        ConfigureEditableCombo(_grantCombo, new[]
        {
            "full — полный доступ нику",
            "limited — базовый набор",
            "none — не выдавать индивидуальный grant",
            "anti_captcha",
            "auto_cut",
            "auto_lumberjack",
            "auto_mine"
        });
        _grantLabel.Text = "Доступ нику:";
        AddRow(grid, _grantLabel, _grantCombo, null);

        ConfigureEditableCombo(_publicCombo, new[]
        {
            "limited — базовый общий доступ",
            "none — без общего доступа",
            "full — полный общий (осторожно)"
        });
        _publicLabel.Text = "Общий доступ для всех:";
        AddRow(grid, _publicLabel, _publicCombo, null);

        var actions = new FlowLayoutPanel
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            Padding = new Padding(190, 8, 0, 0)
        };

        _runButton.Text = "Выполнить";
        _runButton.Width = 150;
        _runButton.Height = 34;
        _runButton.BackColor = ColorAccent;
        _runButton.ForeColor = Color.White;
        _runButton.FlatStyle = FlatStyle.Flat;
        _runButton.FlatAppearance.BorderSize = 0;
        _runButton.Font = new Font("Segoe UI Semibold", 10f);
        _runButton.Click += async (_, _) => await ExecuteAsync();
        actions.Controls.Add(_runButton);

        _cancelButton.Text = "Отмена";
        _cancelButton.Width = 100;
        _cancelButton.Height = 34;
        _cancelButton.Enabled = false;
        _cancelButton.FlatStyle = FlatStyle.Flat;
        _cancelButton.Click += (_, _) => _cts?.Cancel();
        actions.Controls.Add(_cancelButton);

        var host = new Panel { Dock = DockStyle.Top, AutoSize = true };
        host.Controls.Add(actions);
        host.Controls.Add(grid);
        group.Controls.Add(host);
        return group;
    }

    private Control BuildLogPanel()
    {
        var group = new GroupBox
        {
            Text = "  Журнал  ",
            Dock = DockStyle.Fill,
            Padding = new Padding(10, 8, 10, 10),
            BackColor = ColorPanel,
            ForeColor = Color.FromArgb(60, 60, 60),
            Font = new Font("Segoe UI Semibold", 9.5f)
        };

        _log.Dock = DockStyle.Fill;
        _log.ReadOnly = true;
        _log.BackColor = ColorLogBg;
        _log.ForeColor = Color.Gainsboro;
        _log.Font = new Font("Consolas", 9.5f);
        _log.BorderStyle = BorderStyle.None;
        _log.WordWrap = false;
        _log.ScrollBars = RichTextBoxScrollBars.Both;
        _log.DetectUrls = false;

        var buttons = new FlowLayoutPanel
        {
            Dock = DockStyle.Bottom,
            AutoSize = true,
            FlowDirection = FlowDirection.LeftToRight,
            Padding = new Padding(0, 6, 0, 0)
        };
        buttons.Controls.Add(MakeButton("Очистить", (_, _) => _log.Clear(), 100));
        buttons.Controls.Add(MakeButton("Копировать всё", (_, _) => CopyLog(), 130));

        group.Controls.Add(_log);
        group.Controls.Add(buttons);
        return group;
    }

    // ------------------------------------------------------------------
    // Вспомогательные конструкторы контролов
    // ------------------------------------------------------------------

    private static Label MakeLabel(string text) => new()
    {
        Text = text,
        AutoSize = false,
        Dock = DockStyle.Fill,
        TextAlign = ContentAlignment.MiddleLeft,
        Font = new Font("Segoe UI", 9.5f),
        ForeColor = Color.FromArgb(50, 50, 50),
        Height = 30
    };

    private static Button MakeButton(string text, EventHandler onClick, int width)
    {
        var button = new Button
        {
            Text = text,
            Width = width,
            Height = 28,
            FlatStyle = FlatStyle.Flat,
            Margin = new Padding(0, 0, 8, 0),
            BackColor = Color.FromArgb(238, 240, 243)
        };
        button.FlatAppearance.BorderColor = Color.FromArgb(205, 208, 212);
        button.Click += onClick;
        return button;
    }

    private static void ConfigurePathBox(TextBox box)
    {
        box.Dock = DockStyle.Fill;
        box.Font = new Font("Consolas", 9.5f);
        box.Margin = new Padding(0, 3, 6, 3);
    }

    private static void ConfigureBrowse(Button button, Action onClick)
    {
        button.Text = "Обзор…";
        button.Dock = DockStyle.Fill;
        button.FlatStyle = FlatStyle.Flat;
        button.BackColor = Color.FromArgb(238, 240, 243);
        button.Margin = new Padding(0, 3, 0, 3);
        button.FlatAppearance.BorderColor = Color.FromArgb(205, 208, 212);
        button.Click += (_, _) => onClick();
    }

    private static void ConfigureEditableCombo(ComboBox combo, string[] items)
    {
        combo.Dock = DockStyle.Fill;
        combo.DropDownStyle = ComboBoxStyle.DropDown; // разрешён ручной ввод CSV
        combo.Font = new Font("Segoe UI", 9.5f);
        combo.Margin = new Padding(0, 3, 6, 3);
        combo.Items.AddRange(items);
        combo.SelectedIndex = 0;
    }

    private static void AddRow(TableLayoutPanel grid, Control label, Control editor, Control? extra)
    {
        var row = grid.RowCount;
        grid.RowCount = row + 1;
        grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        grid.Controls.Add(label, 0, row);
        grid.Controls.Add(editor, 1, row);
        if (extra is not null)
        {
            grid.Controls.Add(extra, 2, row);
        }
    }

    // ------------------------------------------------------------------
    // Логика
    // ------------------------------------------------------------------

    private Operation SelectedOperation => (Operation)_operationCombo.SelectedIndex;

    private void UpdateParameterVisibility()
    {
        var op = SelectedOperation;

        bool needRequest = op is Operation.DecodeRequest or Operation.Issue;
        bool needLicense = op is Operation.Inspect or Operation.Issue;
        bool needIssueParams = op is Operation.Issue;
        bool needReport = op is Operation.DecodeRequest;

        SetRowVisible(_requestLabel, _requestPath, _requestBrowse, needRequest);
        SetRowVisible(_licenseLabel, _licensePath, _licenseBrowse, needLicense);
        SetRowVisible(_reportLabel, _reportPath, _reportBrowse, needReport);
        SetRowVisible(_expiresLabel, _expiresCombo, null, needIssueParams);
        SetRowVisible(_grantLabel, _grantCombo, null, needIssueParams);
        SetRowVisible(_publicLabel, _publicCombo, null, needIssueParams);

        _operationHint.Text = op switch
        {
            Operation.Inspect =>
                "Только чтение: показывает подпись, цепочку изменений, все ники и сроки. Ничего не меняет.",
            Operation.DecodeRequest =>
                "Расшифровывает ANREQ1-заявку и сохраняет отчёт с полями устройства рядом с заявкой.",
            Operation.Issue =>
                "Создаёт или обновляет profile.reg. Grants других ников и устройств сохраняются.",
            Operation.InitKeys =>
                "Создаёт ключи администратора, если их ещё нет. Существующие ключи не трогает.",
            Operation.InitKeysForce =>
                "ОПАСНО: перевыпускает ключи. Все ранее выданные profile.reg станут недействительными.",
            Operation.BuildApp3 =>
                "Пересобирает Java-инструмент (нужно после изменений в AnLicenseTool.java).",
            _ => string.Empty
        };

        _runButton.BackColor = op == Operation.InitKeysForce ? ColorDanger : ColorAccent;
        _runButton.Text = op == Operation.InitKeysForce ? "Перевыпустить ключи" : "Выполнить";
    }

    private static void SetRowVisible(Control label, Control editor, Control? extra, bool visible)
    {
        label.Visible = visible;
        editor.Visible = visible;
        if (extra is not null)
        {
            extra.Visible = visible;
        }
    }

    private void DiscoverEnvironment()
    {
        _paths = ProjectPaths.TryDiscover();
        if (_paths is null)
        {
            _envLabel.Text = "Корень проекта не найден (нет settings.gradle).\nНажмите «Выбрать корень проекта…».";
            _envLabel.ForeColor = ColorDanger;
            _runner = null;
            return;
        }

        _envLabel.ForeColor = Color.FromArgb(70, 70, 70);
        _envLabel.Text = _paths.DescribeEnvironment();
        _runner = new ToolRunner(_paths, AppendLog);

        if (string.IsNullOrWhiteSpace(_requestPath.Text))
        {
            _requestPath.Text = _paths.DefaultRequestFile;
        }

        if (string.IsNullOrWhiteSpace(_licensePath.Text))
        {
            _licensePath.Text = _paths.DefaultLicenseFile;
        }
    }

    private void PickRoot()
    {
        using var dialog = new FolderBrowserDialog
        {
            Description = "Укажите корень проекта (папку с settings.gradle)",
            UseDescriptionForTitle = true
        };

        if (dialog.ShowDialog(this) != DialogResult.OK)
        {
            return;
        }

        var explicitRoot = ProjectPaths.FromExplicitRoot(dialog.SelectedPath);
        if (explicitRoot is null)
        {
            MessageBox.Show(this, "В выбранной папке нет settings.gradle.", "Неверная папка",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        _paths = explicitRoot;
        _runner = new ToolRunner(_paths, AppendLog);
        _envLabel.ForeColor = Color.FromArgb(70, 70, 70);
        _envLabel.Text = _paths.DescribeEnvironment();
        _requestPath.Text = _paths.DefaultRequestFile;
        _licensePath.Text = _paths.DefaultLicenseFile;
    }

    private async Task ExecuteAsync()
    {
        if (_paths is null || _runner is null)
        {
            MessageBox.Show(this, "Сначала укажите корень проекта.", "Нет окружения",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        var op = SelectedOperation;

        if (op == Operation.InitKeysForce && !ConfirmForce())
        {
            return;
        }

        List<string>? args = BuildArguments(op);
        if (op != Operation.BuildApp3 && args is null)
        {
            return;
        }

        SetBusy(true, op == Operation.BuildApp3 ? "Сборка app3…" : "Выполняется…");
        _cts = new CancellationTokenSource();

        try
        {
            var result = op == Operation.BuildApp3
                ? await _runner.RunGradleBuildAsync(_cts.Token)
                : await _runner.RunToolAsync(args!, _cts.Token);

            if (result.Success)
            {
                AppendLog($"— Готово за {result.Elapsed.TotalSeconds:F1} с —", OutputLevel.Success);
                _statusText.Text = $"Успешно ({result.Elapsed.TotalSeconds:F1} с)";
            }
            else
            {
                AppendLog($"— Завершено с ошибкой (код {result.ExitCode}) —", OutputLevel.Error);
                _statusText.Text = $"Ошибка (код {result.ExitCode})";
            }

            DiscoverEnvironmentSafe();
        }
        finally
        {
            SetBusy(false, _statusText.Text ?? "Готово");
            _cts?.Dispose();
            _cts = null;
        }
    }

    private List<string>? BuildArguments(Operation op)
    {
        switch (op)
        {
            case Operation.Inspect:
            {
                var license = _licensePath.Text.Trim();
                if (!RequireExistingFile(license, "файл лицензии"))
                {
                    return null;
                }

                return new List<string> { "inspect-license", license };
            }

            case Operation.DecodeRequest:
            {
                var request = _requestPath.Text.Trim();
                if (!RequireExistingFile(request, "заявку от устройства"))
                {
                    return null;
                }

                var args = new List<string> { "decode-request", request };
                var report = _reportPath.Text.Trim();
                if (report.Length > 0)
                {
                    args.Add(report);
                }

                return args;
            }

            case Operation.Issue:
            {
                var request = _requestPath.Text.Trim();
                if (!RequireExistingFile(request, "заявку от устройства"))
                {
                    return null;
                }

                var license = _licensePath.Text.Trim();
                if (license.Length == 0)
                {
                    MessageBox.Show(this, "Укажите путь к profile.reg.", "Не хватает данных",
                        MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return null;
                }

                return new List<string>
                {
                    "issue",
                    request,
                    license,
                    FirstToken(_expiresCombo.Text),
                    FirstToken(_grantCombo.Text),
                    FirstToken(_publicCombo.Text)
                };
            }

            case Operation.InitKeys:
                return new List<string> { "init-keys" };

            case Operation.InitKeysForce:
                return new List<string> { "init-keys", "--force" };

            default:
                return new List<string>();
        }
    }

    /// <summary>
    /// Берёт из подписи пункта только само значение: «10m — 10 минут» -> «10m».
    /// Введённый вручную CSV (например «auto_fight,auto_fish») остаётся без изменений.
    /// </summary>
    private static string FirstToken(string value)
    {
        var text = value.Trim();
        var dashIndex = text.IndexOf('—');
        if (dashIndex > 0)
        {
            text = text[..dashIndex];
        }

        return text.Trim();
    }

    private bool RequireExistingFile(string path, string what)
    {
        if (path.Length == 0)
        {
            MessageBox.Show(this, $"Укажите {what}.", "Не хватает данных",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return false;
        }

        var full = Path.IsPathRooted(path) ? path : Path.Combine(_paths!.Root, path);
        if (!File.Exists(full))
        {
            MessageBox.Show(this, $"Файл не найден:\n{full}", "Не найдено",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return false;
        }

        return true;
    }

    private bool ConfirmForce()
    {
        using var dialog = new ForceConfirmDialog();
        return dialog.ShowDialog(this) == DialogResult.OK;
    }

    private void SetBusy(bool busy, string status)
    {
        _runButton.Enabled = !busy;
        _cancelButton.Enabled = busy;
        _operationCombo.Enabled = !busy;
        _progress.Visible = busy;
        _statusText.Text = status;
        Cursor = busy ? Cursors.AppStarting : Cursors.Default;
    }

    private void DiscoverEnvironmentSafe()
    {
        if (_paths is not null)
        {
            _envLabel.Text = _paths.DescribeEnvironment();
        }
    }

    private void AppendLog(string line, OutputLevel level)
    {
        if (InvokeRequired)
        {
            BeginInvoke(() => AppendLog(line, level));
            return;
        }

        var color = level switch
        {
            OutputLevel.Success => Color.FromArgb(106, 217, 126),
            OutputLevel.Warning => Color.FromArgb(232, 193, 96),
            OutputLevel.Error => Color.FromArgb(240, 113, 113),
            OutputLevel.Command => Color.FromArgb(120, 180, 255),
            _ => Color.Gainsboro
        };

        _log.SelectionStart = _log.TextLength;
        _log.SelectionLength = 0;
        _log.SelectionColor = color;
        _log.AppendText(line + Environment.NewLine);
        _log.SelectionColor = _log.ForeColor;
        _log.ScrollToCaret();
    }

    private void CopyLog()
    {
        if (_log.TextLength == 0)
        {
            return;
        }

        Clipboard.SetText(_log.Text);
        _statusText.Text = "Журнал скопирован в буфер обмена";
    }

    private void PickFile(TextBox target, string filter)
    {
        using var dialog = new OpenFileDialog
        {
            Filter = filter,
            InitialDirectory = ResolveInitialDirectory(target.Text)
        };

        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            target.Text = dialog.FileName;
        }
    }

    private void PickSaveFile(TextBox target, string filter)
    {
        using var dialog = new SaveFileDialog
        {
            Filter = filter,
            InitialDirectory = ResolveInitialDirectory(target.Text),
            OverwritePrompt = true
        };

        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            target.Text = dialog.FileName;
        }
    }

    private string ResolveInitialDirectory(string current)
    {
        try
        {
            if (current.Length > 0)
            {
                var dir = Path.GetDirectoryName(Path.IsPathRooted(current)
                    ? current
                    : Path.Combine(_paths?.Root ?? Directory.GetCurrentDirectory(), current));
                if (!string.IsNullOrEmpty(dir) && Directory.Exists(dir))
                {
                    return dir;
                }
            }

            if (_paths is not null && Directory.Exists(_paths.RequestDir))
            {
                return _paths.RequestDir;
            }
        }
        catch
        {
            // Некорректный путь в поле — просто откроем диалог с расположением по умолчанию.
        }

        return Directory.GetCurrentDirectory();
    }

    private void OpenFolder(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !Directory.Exists(path))
        {
            MessageBox.Show(this, "Папка ещё не создана.", "Нет папки",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        try
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = path,
                UseShellExecute = true
            });
        }
        catch (Win32Exception ex)
        {
            MessageBox.Show(this, ex.Message, "Не удалось открыть папку",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        _cts?.Cancel();
        base.OnFormClosing(e);
    }
}
