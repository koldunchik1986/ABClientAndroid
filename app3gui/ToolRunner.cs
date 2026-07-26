using System.Diagnostics;
using System.Text;

namespace AnLicenseGui;

/// <summary>Уровень строки вывода — используется для подсветки в журнале.</summary>
public enum OutputLevel
{
    Info,
    Success,
    Warning,
    Error,
    Command
}

/// <summary>Результат выполнения внешнего процесса.</summary>
public sealed record ToolResult(int ExitCode, TimeSpan Elapsed)
{
    public bool Success => ExitCode == 0;
}

/// <summary>
/// Запуск Java-инструмента лицензий и Gradle из GUI.
///
/// Ключевые моменты:
/// <list type="bullet">
/// <item>рабочий каталог всегда = корень проекта (см. <see cref="ProjectPaths"/>);</item>
/// <item>вывод читается в UTF-8 — иначе русский текст инструмента превращается в кракозябры
/// (у Java в Windows-консоли кодировка по умолчанию не UTF-8);</item>
/// <item>чтение потоков асинхронное, UI не блокируется.</item>
/// </list>
///
/// Важно: вся криптография и формат лицензий остаются в Java-инструменте. GUI ничего не
/// подписывает и не разбирает сам — это исключает расхождение форматов и порчу уже выданных
/// файлов <c>profile.reg</c>.
/// </summary>
public sealed class ToolRunner
{
    private readonly ProjectPaths _paths;
    private readonly Action<string, OutputLevel> _write;

    public ToolRunner(ProjectPaths paths, Action<string, OutputLevel> write)
    {
        _paths = paths;
        _write = write;
    }

    /// <summary>Запускает команду AnLicenseTool (init-keys / decode-request / inspect-license / issue).</summary>
    public Task<ToolResult> RunToolAsync(IReadOnlyList<string> toolArgs, CancellationToken token)
    {
        var classpath = _paths.ResolveClasspath();
        if (classpath is null)
        {
            _write("app3 не собран: не найден app3.jar и каталог классов. Нажмите «Собрать app3».", OutputLevel.Error);
            return Task.FromResult(new ToolResult(-1, TimeSpan.Zero));
        }

        var args = new List<string>
        {
            // Принудительный UTF-8 для stdout/stderr Java — иначе кириллица придёт битой.
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
            "-cp",
            classpath,
            ProjectPaths.MainClass
        };
        args.AddRange(toolArgs);

        return RunProcessAsync(ProjectPaths.ResolveJavaExecutable(), args, token);
    }

    /// <summary>Собирает app3 через Gradle (нужно один раз или после правок Java-кода).</summary>
    public Task<ToolResult> RunGradleBuildAsync(CancellationToken token)
    {
        if (!File.Exists(_paths.GradlewPath))
        {
            _write($"Не найден {_paths.GradlewPath}", OutputLevel.Error);
            return Task.FromResult(new ToolResult(-1, TimeSpan.Zero));
        }

        var args = new List<string> { "--no-daemon", ":app3:build" };
        return RunProcessAsync(_paths.GradlewPath, args, token);
    }

    private async Task<ToolResult> RunProcessAsync(string fileName, IReadOnlyList<string> args, CancellationToken token)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = fileName,
            WorkingDirectory = _paths.Root, // критично: AnLicenseTool.resolveRoot() ищет settings.gradle
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8
        };

        foreach (var arg in args)
        {
            startInfo.ArgumentList.Add(arg);
        }

        _write($"> {fileName} {string.Join(' ', args.Select(QuoteIfNeeded))}", OutputLevel.Command);

        var stopwatch = Stopwatch.StartNew();
        using var process = new Process { StartInfo = startInfo, EnableRaisingEvents = true };

        try
        {
            process.Start();
        }
        catch (Exception ex)
        {
            _write($"Не удалось запустить процесс: {ex.Message}", OutputLevel.Error);
            return new ToolResult(-1, stopwatch.Elapsed);
        }

        var stdoutTask = PumpAsync(process.StandardOutput, OutputLevel.Info);
        var stderrTask = PumpAsync(process.StandardError, OutputLevel.Warning);

        try
        {
            await process.WaitForExitAsync(token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            TryKill(process);
            _write("Операция отменена пользователем.", OutputLevel.Warning);
            return new ToolResult(-1, stopwatch.Elapsed);
        }

        await Task.WhenAll(stdoutTask, stderrTask).ConfigureAwait(false);
        stopwatch.Stop();

        return new ToolResult(process.ExitCode, stopwatch.Elapsed);
    }

    private async Task PumpAsync(StreamReader reader, OutputLevel defaultLevel)
    {
        while (await reader.ReadLineAsync().ConfigureAwait(false) is { } line)
        {
            _write(line, ClassifyLine(line, defaultLevel));
        }
    }

    /// <summary>Подсветка строк вывода по ключевым словам инструмента.</summary>
    private static OutputLevel ClassifyLine(string line, OutputLevel defaultLevel)
    {
        if (line.Length == 0)
        {
            return defaultLevel;
        }

        if (line.Contains("Exception", StringComparison.Ordinal)
            || line.Contains("FAILURE", StringComparison.Ordinal)
            || line.Contains("FAILED", StringComparison.Ordinal)
            || line.Contains("invalid", StringComparison.OrdinalIgnoreCase)
            || line.Contains("Ошибка", StringComparison.OrdinalIgnoreCase))
        {
            return OutputLevel.Error;
        }

        if (line.Contains("BUILD SUCCESSFUL", StringComparison.Ordinal)
            || line.Contains("Готовый файл", StringComparison.OrdinalIgnoreCase)
            || line.Contains("обновлён", StringComparison.OrdinalIgnoreCase)
            || line.Contains("Файл лицензии", StringComparison.OrdinalIgnoreCase))
        {
            return OutputLevel.Success;
        }

        if (line.Contains("истёк", StringComparison.OrdinalIgnoreCase)
            || line.Contains("предупрежд", StringComparison.OrdinalIgnoreCase)
            || line.Contains("WARN", StringComparison.Ordinal))
        {
            return OutputLevel.Warning;
        }

        return defaultLevel;
    }

    private static void TryKill(Process process)
    {
        try
        {
            if (!process.HasExited)
            {
                process.Kill(entireProcessTree: true);
            }
        }
        catch
        {
            // Процесс мог завершиться сам — это не ошибка сценария отмены.
        }
    }

    private static string QuoteIfNeeded(string value) =>
        value.Contains(' ', StringComparison.Ordinal) ? $"\"{value}\"" : value;
}
