namespace AnLicenseGui;

/// <summary>
/// Точка входа GUI-менеджера лицензий ANClient.
///
/// Роль приложения: удобная оболочка над Java-инструментом
/// <c>ru.neverlands.anclient.license.AnLicenseTool</c> (модуль <c>app3</c>).
///
/// Криптография, формат ANREQ1/ANREG2, подписи и цепочка изменений остаются исключительно
/// в Java-инструменте — GUI ничего не подписывает и не разбирает самостоятельно. Это гарантирует,
/// что уже выданные файлы <c>profile.reg</c> не станут несовместимыми из-за второй реализации.
/// </summary>
internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}
