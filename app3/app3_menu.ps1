$ErrorActionPreference = 'Stop'

try {
    [Console]::InputEncoding = [System.Text.Encoding]::UTF8
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}

$App3Dir = if ($env:APP3_MENU_DIR) {
    $env:APP3_MENU_DIR
} elseif ($PSScriptRoot) {
    $PSScriptRoot
} else {
    Split-Path -Parent $MyInvocation.MyCommand.Path
}

$RootDir = (Resolve-Path -LiteralPath (Join-Path $App3Dir '..')).Path
$Gradlew = Join-Path $RootDir 'gradlew.bat'
$MainClass = 'ru.neverlands.anclient.license.AnLicenseTool'
$ClassesDir = Join-Path $RootDir 'app3\build\classes\java\main'
$DefaultRequest = Join-Path $App3Dir 'request\request.txt'
$DefaultDecode = 'автоматически: Nick_devicePublicKeySha256.txt рядом с заявкой'
$DefaultLicense = Join-Path $App3Dir 'request\profile.reg'

if (!(Test-Path -LiteralPath $Gradlew)) {
    Write-Host "Не найден Gradle wrapper: $Gradlew"
    exit 1
}

function Pause-Menu {
    [void](Read-Host 'Нажмите Enter для продолжения')
}

function Confirm-Continue {
    $answer = Read-Host 'Продолжить выполнение этого пункта? [Y/n]'
    return !(($answer -ieq 'n') -or ($answer -ieq 'no') -or ($answer -ieq 'н'))
}

function Invoke-App3Build {
    Push-Location $RootDir
    try {
        & $Gradlew --no-daemon ':app3:build' 2>&1 | ForEach-Object { Write-Host $_ }
        return [int]$LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Ensure-Classes {
    Push-Location $RootDir
    try {
        & $Gradlew --no-daemon ':app3:classes' 2>&1 | ForEach-Object { Write-Host $_ }
        return [int]$LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Invoke-LicenseTool([string[]]$ToolArgs) {
    $classesStatus = Ensure-Classes
    if ($classesStatus -ne 0) {
        Write-Host "Не удалось собрать app3 classes: $classesStatus"
        return $classesStatus
    }

    Push-Location $RootDir
    try {
        & java '-Dfile.encoding=UTF-8' -cp $ClassesDir $MainClass @ToolArgs 2>&1 | ForEach-Object { Write-Host $_ }
        $status = $LASTEXITCODE
        if ($status -ne 0) {
            Write-Host "Команда завершилась с ошибкой: $status"
        }
        return [int]$status
    } finally {
        Pop-Location
    }
}

function Ask-RequestPath {
    $value = Read-Host "Путь к заявке от устройства [$DefaultRequest]"
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = $DefaultRequest
    }
    if (!(Test-Path -LiteralPath $value)) {
        Write-Host "Не найдена заявка: $value"
        Pause-Menu
        return $null
    }
    return $value
}

function Ask-LicensePath {
    $value = Read-Host "Путь к файлу лицензии [$DefaultLicense]"
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = $DefaultLicense
    }
    return $value
}

function Ask-AccessTime([string]$Hint) {
    Write-Host 'Срок индивидуального доступа:'
    Write-Host '  0 = без срока, 10m = 10 минут, 2h = 2 часа, 7d = 7 дней, epoch millis = 1770000000000.'
    Write-Host "  Подсказка: $Hint"
    $value = Read-Host 'Срок доступа [0]'
    if ([string]::IsNullOrWhiteSpace($value)) {
        return '0'
    }
    return $value
}

function Ask-PublicAccess {
    Write-Host 'Общий доступ для всех профилей: Enter = не менять существующий файл; для нового файла будет базовый набор.'
    Write-Host 'Значения: limited = базовый, none = нет общего доступа, full = полный, или список функций через запятую.'
    $value = Read-Host 'Общий доступ [Enter/default]'
    return $value
}

function Show-FeatureTokens {
    Write-Host 'Коды функций для ручного списка через запятую:'
    Write-Host '  auto_fight, quick_actions, auto_fish, auto_bait, auto_attack'
    Write-Host '  auto_compass, auto_boss, auto_invisible, location_tracking'
    Write-Host '  auto_detect, auto_summon, auto_cure, auto_drink, auto_moving'
    Write-Host '  auto_treasure, auto_cut, auto_refresh, auto_skin'
    Write-Host '  anti_captcha (только full/custom grant; из publicFeatures удаляется)'
    Write-Host '  open_contacts, open_pinfo, open_logs, open_stats, timers, refresh_contacts'
    Write-Host '  quick_self_rass, quick_open_nevid, quick_teleport, quick_island'
    Write-Host '  quick_totem, quick_elixir_blaz, quick_elixir_cure, quick_elixir_restore'
    Write-Host '  clans'
}

function Show-InitKeysHelp {
    Clear-Host
    Write-Host '[1] Подготовить ключи администратора'
    Write-Host 'Назначение: создать закрытые ключи администратора для выпуска лицензий'
    Write-Host 'и открытые ключи, которые Android-приложение использует для проверки profile.reg.'
    Write-Host 'Без принудительного режима существующие ключи не перезаписываются.'
    Write-Host 'Пример CLI: app3\app3_menu.bat init-keys'
    Write-Host 'Результат: app3\keys\*.pkcs8, app3\keys\*.x509, app2\src\main\assets\license_public_keys.properties'
    Write-Host ''
}

function Show-InitKeysForceHelp {
    Clear-Host
    Write-Host '[2] Полностью перевыпустить ключи администратора'
    Write-Host 'Назначение: удалить совместимость со старыми лицензиями и начать новую цепочку ключей.'
    Write-Host 'Пример CLI: app3\app3_menu.bat init-keys --force'
    Write-Host 'ВНИМАНИЕ: старые profile.reg перестанут проверяться после смены ключей.'
    Write-Host ''
}

function Show-DecodeRequestHelp {
    Clear-Host
    Write-Host '[3] Прочитать заявку от устройства'
    Write-Host 'Назначение: расшифровать файл заявки и сохранить понятный отчёт.'
    Write-Host 'Вход: файл заявки от Android-устройства.'
    Write-Host 'Выход: текстовый отчёт с ником, ключом устройства, отпечатком и подписью APK.'
    Write-Host 'Пример CLI: app3\app3_menu.bat decode-request app3\request\request.txt app3\request\profileName.txt'
    Write-Host 'Этот отчёт потом используется проверкой лицензии, чтобы показывать ник вместо hash.'
    Write-Host ''
}

function Show-InspectLicenseHelp {
    Clear-Host
    Write-Host '[4] Проверить текущий файл лицензии'
    Write-Host 'Назначение: показать, что реально записано в profile.reg.'
    Write-Host 'Проверка покажет:'
    Write-Host '  - действительна ли подпись администратора и цепочка изменений;'
    Write-Host '  - какие функции открыты всем профилям без срока;'
    Write-Host '  - какие ники имеют индивидуальный доступ;'
    Write-Host '  - какие функции выданы каждому нику;'
    Write-Host '  - когда доступ истекает и сколько осталось времени.'
    Write-Host 'Важно: сам profile.reg хранит защищённый hash ника, а не открытый ник.'
    Write-Host 'В новых profile.reg ник берётся из встроенного зашифрованного индекса.'
    Write-Host 'Папка с заявками больше не спрашивается. Для старых profile.reg без индекса её можно передать вторым CLI-аргументом.'
    Write-Host 'Пример CLI: app3\app3_menu.bat check'
    Write-Host 'Пример напрямую: app3\app3_menu.bat inspect-license app3\request\profile.reg'
    Write-Host 'Legacy fallback: app3\app3_menu.bat inspect-license app3\request\profile.reg app3\request'
    Write-Host ''
}

function Show-IssueFullHelp {
    Clear-Host
    Write-Host '[5] Выдать нику полный доступ'
    Write-Host 'Назначение: открыть конкретному нику/устройству все функции, включая Anti-Captcha.'
    Write-Host 'Что задаётся:'
    Write-Host '  - заявка от устройства, по ней определяется ник и устройство;'
    Write-Host '  - файл лицензии, который будет создан или обновлён;'
    Write-Host '  - срок действия полного доступа: 0 без срока, 10m, 2h, 7d;'
    Write-Host '  - общий доступ для всех профилей: Enter = не менять, новый файл = базовый доступ.'
    Write-Host 'Пример для Блудя full на 10 минут:'
    Write-Host '  app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m full limited'
    Write-Host 'Что выбрать в menu: пункт 5, путь заявки Enter, путь лицензии Enter, срок 10m, общий доступ limited или Enter.'
    Write-Host ''
}

function Show-IssueLimitedHelp {
    Clear-Host
    Write-Host '[6] Выдать нику базовый доступ'
    Write-Host 'Назначение: открыть конкретному нику/устройству только базовый набор.'
    Write-Host 'Базовый набор: Авто-Бой, Авто-Рыбалка, Авто-Охота, Навигатор, Компас, Быстрые действия, Таймеры, Контакты, Кланы, Статистика, PINFO.'
    Write-Host 'Срок можно задать так же: 0 без срока, 10m, 2h, 7d.'
    Write-Host 'Общий доступ для всех можно оставить как есть или обновить отдельно.'
    Write-Host 'Пример: app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 7d limited limited'
    Write-Host ''
}

function Show-PublicOnlyHelp {
    Clear-Host
    Write-Host '[7] Обновить только общий доступ для всех'
    Write-Host 'Назначение: создать или обновить файл лицензии без индивидуального доступа для ника.'
    Write-Host 'Итог: все профили с этим profile.reg получают выбранный общий набор функций.'
    Write-Host 'Anti-Captcha в общий набор не попадает даже при public full; выдавайте её full/custom grant.'
    Write-Host 'Срок тут не используется, потому что индивидуальный доступ не создаётся.'
    Write-Host 'Общий доступ по умолчанию: базовый набор. Можно выбрать полный, пустой или конкретные функции.'
    Write-Host 'Пример: app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 0 none limited'
    Write-Host ''
}

function Show-ManualIssueHelp {
    Clear-Host
    Write-Host '[8] Ручная выдача с выбором конкретных функций'
    Write-Host 'Назначение: точно указать, какие функции открыть нику и какие оставить общими.'
    Write-Host 'Срок индивидуального доступа: 0, never, 10m, 2h, 7d, или Unix epoch millis.'
    Write-Host 'Набор для ника: full, limited, none/off/empty/public-only, или список функций через запятую.'
    Write-Host 'Общий набор для всех: full, limited, none/off/empty, или список функций через запятую; anti_captcha из него удаляется.'
    Write-Host 'Пример custom на 10 минут:'
    Write-Host '  app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m auto_fight,auto_fish limited'
    Write-Host 'Пример вручную открыть только Anti-Captcha на 10 минут:'
    Write-Host '  app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m anti_captcha limited'
    Write-Host ''
    Show-FeatureTokens
}

function Show-BuildHelp {
    Clear-Host
    Write-Host '[9] Собрать app3'
    Write-Host 'Назначение: собрать JVM-модуль app3.'
    Write-Host 'Пример CLI: app3\app3_menu.bat build'
    Write-Host 'Реальная Gradle-команда: .\gradlew.bat --no-daemon :app3:build'
    Write-Host ''
}

function Show-FullHelp {
    Clear-Host
    Write-Host '=============================================================='
    Write-Host '                    Справка по меню лицензий'
    Write-Host '=============================================================='
    Write-Host 'Прямые команды:'
    Write-Host '  init-keys [--force]'
    Write-Host '  decode-request request.txt [profileName.txt]'
    Write-Host '  inspect-license [profile.reg] [legacy-папка с request.txt]'
    Write-Host '  issue request.txt [profile.reg] [срок] [доступ ника] [общий доступ]'
    Write-Host '  build'
    Write-Host ''
    Write-Host 'Срок индивидуального доступа:'
    Write-Host '  0, never, none, unlimited, forever = без срока.'
    Write-Host '  10m = 10 минут от текущего времени ПК.'
    Write-Host '  2h = 2 часа от текущего времени ПК.'
    Write-Host '  7d = 7 дней от текущего времени ПК.'
    Write-Host '  1770000000000 = абсолютный Unix epoch milliseconds.'
    Write-Host 'Важно: просто 10 не означает 10 минут. Для минут пиши 10m.'
    Write-Host ''
    Write-Host 'Индивидуальный набор для конкретного ника:'
    Write-Host '  full = все quick actions плюс clans, включая Anti-Captcha.'
    Write-Host '  limited, free, basic = базовый общедоступный набор.'
    Write-Host '  none, off, empty, public-only = не создавать индивидуальный доступ.'
    Write-Host '  custom CSV = перечисление кодов функций через запятую.'
    Write-Host ''
    Write-Host 'Общий набор для всех профилей с этим profile.reg:'
    Write-Host '  limited = базовый public-доступ всем профилям bundle.'
    Write-Host '  none, off, empty = без public-доступа.'
    Write-Host '  full = full public-доступ всем профилям bundle, но без Anti-Captcha.'
    Write-Host '  custom CSV = перечисление кодов функций через запятую; anti_captcha из public удаляется.'
    Write-Host ''
    Show-FeatureTokens
    Write-Host ''
    Write-Host 'Примеры:'
    Write-Host '  Проверить текущий profile.reg:'
    Write-Host '    app3\app3_menu.bat check'
    Write-Host '  Блудя full на 10 минут:'
    Write-Host '    app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m full limited'
    Write-Host '  Full без срока:'
    Write-Host '    app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 0 full limited'
    Write-Host '  Limited на 7 дней:'
    Write-Host '    app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 7d limited limited'
    Write-Host '  Public-only limited:'
    Write-Host '    app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 0 none limited'
    Write-Host '  Выборочный доступ на 2 часа:'
    Write-Host '    app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 2h auto_fight,auto_fish,quick_actions limited'
    Write-Host '  Anti-Captcha вручную на 10 минут:'
    Write-Host '    app3\app3_menu.bat issue app3\request\request.txt app3\request\profile.reg 10m anti_captcha limited'
    Write-Host ''
}

function Open-Instruction {
    Start-Process (Join-Path $App3Dir 'instruction.md')
}

function Run-DecodeRequestMenu {
    Show-DecodeRequestHelp
    if (!(Confirm-Continue)) { return }
    $request = Ask-RequestPath
    if ($null -eq $request) { return }
    $output = Read-Host 'Куда записать расшифровку [Enter = Nick_devicePublicKeySha256.txt рядом с заявкой]'
    if ([string]::IsNullOrWhiteSpace($output)) {
        [void](Invoke-LicenseTool @('decode-request', $request))
    } else {
        [void](Invoke-LicenseTool @('decode-request', $request, $output))
    }
    Pause-Menu
}

function Run-InspectLicenseMenu {
    Show-InspectLicenseHelp
    if (!(Confirm-Continue)) { return }
    $license = Ask-LicensePath
    [void](Invoke-LicenseTool @('inspect-license', $license))
    Pause-Menu
}

function Run-IssueFullMenu {
    Show-IssueFullHelp
    if (!(Confirm-Continue)) { return }
    $request = Ask-RequestPath
    if ($null -eq $request) { return }
    $license = Ask-LicensePath
    $time = Ask-AccessTime '10m для теста full на 10 минут'
    $public = Ask-PublicAccess
    $toolArgs = @('issue', $request, $license, $time, 'full')
    if (![string]::IsNullOrWhiteSpace($public)) { $toolArgs += $public }
    [void](Invoke-LicenseTool $toolArgs)
    Pause-Menu
}

function Run-IssueLimitedMenu {
    Show-IssueLimitedHelp
    if (!(Confirm-Continue)) { return }
    $request = Ask-RequestPath
    if ($null -eq $request) { return }
    $license = Ask-LicensePath
    $time = Ask-AccessTime '7d например limited на 7 дней'
    $public = Ask-PublicAccess
    $toolArgs = @('issue', $request, $license, $time, 'limited')
    if (![string]::IsNullOrWhiteSpace($public)) { $toolArgs += $public }
    [void](Invoke-LicenseTool $toolArgs)
    Pause-Menu
}

function Run-PublicOnlyMenu {
    Show-PublicOnlyHelp
    if (!(Confirm-Continue)) { return }
    $request = Ask-RequestPath
    if ($null -eq $request) { return }
    $license = Ask-LicensePath
    $public = Read-Host 'Общий доступ для всех профилей [limited]'
    if ([string]::IsNullOrWhiteSpace($public)) { $public = 'limited' }
    [void](Invoke-LicenseTool @('issue', $request, $license, '0', 'none', $public))
    Pause-Menu
}

function Run-ManualIssueMenu {
    Show-ManualIssueHelp
    if (!(Confirm-Continue)) { return }
    $request = Ask-RequestPath
    if ($null -eq $request) { return }
    $license = Ask-LicensePath
    $time = Ask-AccessTime '10m, 2h, 7d, 0, или epoch millis'
    $features = Read-Host 'Набор функций для ника [full]'
    if ([string]::IsNullOrWhiteSpace($features)) { $features = 'full' }
    $public = Ask-PublicAccess
    $toolArgs = @('issue', $request, $license, $time, $features)
    if (![string]::IsNullOrWhiteSpace($public)) { $toolArgs += $public }
    [void](Invoke-LicenseTool $toolArgs)
    Pause-Menu
}

function Run-BuildMenu {
    Show-BuildHelp
    if (!(Confirm-Continue)) { return }
    $status = Invoke-App3Build
    if ($status -eq 0) {
        Write-Host 'Сборка app3 успешна.'
    } else {
        Write-Host "Сборка завершилась с ошибкой: $status"
    }
    Pause-Menu
}

function Show-Menu {
    while ($true) {
        Clear-Host
        Write-Host '=============================================================='
        Write-Host '              ANClient: меню выпуска лицензий'
        Write-Host '=============================================================='
        Write-Host 'Рабочие файлы по умолчанию:'
        Write-Host "  Заявка от устройства:        $DefaultRequest"
        Write-Host "  Расшифровка заявки:          $DefaultDecode"
        Write-Host "  Готовый файл лицензии:       $DefaultLicense"
        Write-Host ''
        Write-Host 'Быстрый тест: выдать текущему нику полный доступ на 10 минут,'
        Write-Host 'а всем остальным оставить общий базовый доступ:'
        Write-Host '  app3\app3_menu.bat full10m'
        Write-Host ''
        Write-Host ' 1. Подготовить ключи администратора'
        Write-Host '    Делать один раз перед первым выпуском лицензий.'
        Write-Host ''
        Write-Host ' 2. Полностью перевыпустить ключи администратора'
        Write-Host '    ВНИМАНИЕ: старые файлы лицензий перестанут приниматься.'
        Write-Host ''
        Write-Host ' 3. Прочитать заявку от устройства'
        Write-Host '    Покажет ник, устройство, подпись APK и сохранит отчёт рядом.'
        Write-Host ''
        Write-Host ' 4. Проверить текущий файл лицензии'
        Write-Host '    Распишет: общий доступ, все ники, функции и оставшееся время.'
        Write-Host ''
        Write-Host ' 5. Выдать нику полный доступ'
        Write-Host '    Полный доступ можно ограничить временем: 10m, 2h, 7d или без срока.'
        Write-Host ''
        Write-Host ' 6. Выдать нику базовый доступ'
        Write-Host '    Базовый набор: Авто-Бой, Авто-Рыбалка, Авто-Охота, Навигатор, Компас, Быстрые действия, Таймеры, Контакты, Кланы, Статистика, PINFO.'
        Write-Host ''
        Write-Host ' 7. Обновить только общий доступ для всех'
        Write-Host '    Индивидуальный доступ для текущего ника не создаётся.'
        Write-Host ''
        Write-Host ' 8. Ручная выдача с выбором конкретных функций'
        Write-Host '    Для точечной настройки: отдельные функции через запятую.'
        Write-Host ''
        Write-Host ' 9. Собрать app3'
        Write-Host ' I. Открыть подробную инструкцию'
        Write-Host ' H. Полная справка по срокам и наборам функций'
        Write-Host ' 0. Выход'
        Write-Host ''

        $choice = Read-Host 'Выбор'
        switch -Regex ($choice) {
            '^(1)$' { Show-InitKeysHelp; if (Confirm-Continue) { [void](Invoke-LicenseTool @('init-keys')); Pause-Menu } }
            '^(2)$' { Show-InitKeysForceHelp; $confirm = Read-Host 'Напишите FORCE для продолжения'; if ($confirm -ceq 'FORCE') { [void](Invoke-LicenseTool @('init-keys', '--force')); Pause-Menu } }
            '^(3)$' { Run-DecodeRequestMenu }
            '^(4)$' { Run-InspectLicenseMenu }
            '^(5)$' { Run-IssueFullMenu }
            '^(6)$' { Run-IssueLimitedMenu }
            '^(7)$' { Run-PublicOnlyMenu }
            '^(8)$' { Run-ManualIssueMenu }
            '^(9)$' { Run-BuildMenu }
            '^(?i)i$' { Open-Instruction }
            '^(?i)h$' { Show-FullHelp; Pause-Menu }
            '^(0)$' { return }
            default { Write-Host "Неизвестный пункт: $choice"; Pause-Menu }
        }
    }
}

if ($args.Count -gt 0) {
    $command = $args[0].ToLowerInvariant()
    switch ($command) {
        'menu' { Show-Menu; exit 0 }
        'help' { Show-FullHelp; exit 0 }
        'build' { exit (Invoke-App3Build) }
        'check' { exit (Invoke-LicenseTool @('inspect-license', $DefaultLicense)) }
        'inspect' { exit (Invoke-LicenseTool @('inspect-license', $DefaultLicense)) }
        'full10m' { exit (Invoke-LicenseTool @('issue', $DefaultRequest, $DefaultLicense, '10m', 'full', 'limited')) }
        default { exit (Invoke-LicenseTool $args) }
    }
}

Show-Menu
