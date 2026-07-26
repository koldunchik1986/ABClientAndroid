using System.Text;

namespace AnLicenseGui;

/// <summary>
/// Определение путей рабочего окружения инструмента лицензий.
///
/// Почему это отдельный класс:
/// Java-инструмент <c>AnLicenseTool</c> вычисляет корень проекта методом <c>resolveRoot()</c> —
/// он ищет <c>settings.gradle</c> в текущем рабочем каталоге или в родительском. Все относительные
/// пути аргументов резолвятся от рабочего каталога процесса.
///
/// Поэтому GUI обязан запускать Java с <b>рабочим каталогом = корень проекта</b>. Тогда пути
/// работают ровно так, как описано в <c>app3/instruction.md</c>
/// (например, <c>app3/request/profile.reg</c>).
/// </summary>
public sealed class ProjectPaths
{
    /// <summary>Полное имя главного класса Java-инструмента.</summary>
    public const string MainClass = "ru.neverlands.anclient.license.AnLicenseTool";

    public string Root { get; }

    public string JarPath => Path.Combine(Root, "app3", "build", "libs", "app3.jar");

    public string ClassesPath => Path.Combine(Root, "app3", "build", "classes", "java", "main");

    public string GradlewPath => Path.Combine(Root, "gradlew.bat");

    public string KeysDir => Path.Combine(Root, "app3", "keys");

    public string RequestDir => Path.Combine(Root, "app3", "request");

    public string DefaultRequestFile => Path.Combine(RequestDir, "request.txt");

    public string DefaultLicenseFile => Path.Combine(RequestDir, "profile.reg");

    public string PublicKeysProperties =>
        Path.Combine(Root, "app2", "src", "main", "assets", "license_public_keys.properties");

    private ProjectPaths(string root) => Root = root;

    /// <summary>
    /// Ищет корень проекта: поднимается вверх от указанной папки, пока не найдёт settings.gradle.
    /// </summary>
    public static ProjectPaths? TryDiscover(string? startDirectory = null)
    {
        var candidates = new List<string?>
        {
            startDirectory,
            Directory.GetCurrentDirectory(),
            AppContext.BaseDirectory
        };

        foreach (var candidate in candidates)
        {
            if (string.IsNullOrWhiteSpace(candidate))
            {
                continue;
            }

            var found = WalkUpForSettingsGradle(candidate);
            if (found is not null)
            {
                return new ProjectPaths(found);
            }
        }

        return null;
    }

    /// <summary>Создаёт объект путей для явно выбранной пользователем папки (без поиска вверх).</summary>
    public static ProjectPaths? FromExplicitRoot(string directory)
    {
        if (string.IsNullOrWhiteSpace(directory) || !Directory.Exists(directory))
        {
            return null;
        }

        return File.Exists(Path.Combine(directory, "settings.gradle"))
            ? new ProjectPaths(Path.GetFullPath(directory))
            : null;
    }

    private static string? WalkUpForSettingsGradle(string startDirectory)
    {
        try
        {
            var current = new DirectoryInfo(Path.GetFullPath(startDirectory));
            while (current is not null)
            {
                if (File.Exists(Path.Combine(current.FullName, "settings.gradle")))
                {
                    return current.FullName;
                }

                current = current.Parent;
            }
        }
        catch
        {
            // Недоступный путь — просто считаем, что здесь корня нет.
        }

        return null;
    }

    /// <summary>
    /// Находит java.exe: сначала JAVA_HOME, затем PATH.
    /// </summary>
    public static string ResolveJavaExecutable()
    {
        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            var candidate = Path.Combine(javaHome.Trim('"'), "bin", "java.exe");
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return "java";
    }

    /// <summary>
    /// Classpath для запуска инструмента: собранный jar либо каталог классов.
    /// Возвращает null, если app3 ещё не собран.
    /// </summary>
    public string? ResolveClasspath()
    {
        if (File.Exists(JarPath))
        {
            return JarPath;
        }

        var toolClass = Path.Combine(ClassesPath, "ru", "neverlands", "anclient", "license", "AnLicenseTool.class");
        return File.Exists(toolClass) ? ClassesPath : null;
    }

    /// <summary>Краткая сводка окружения для панели состояния.</summary>
    public string DescribeEnvironment()
    {
        var sb = new StringBuilder();
        sb.AppendLine($"Корень проекта:      {Root}");

        var classpath = ResolveClasspath();
        sb.AppendLine(classpath is null
            ? "Сборка app3:         НЕ НАЙДЕНА (нажмите «Собрать app3»)"
            : $"Сборка app3:         {classpath}");

        sb.AppendLine($"Java:                {ResolveJavaExecutable()}");
        sb.AppendLine($"Ключи администратора: {(Directory.Exists(KeysDir) ? KeysDir : "НЕ СОЗДАНЫ")}");
        sb.Append($"Публичные ключи app2: {(File.Exists(PublicKeysProperties) ? "есть" : "НЕТ")}");
        return sb.ToString();
    }
}
