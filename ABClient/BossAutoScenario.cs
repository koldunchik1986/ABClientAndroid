using System;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;
using ABClient.ABForms;
using ABClient.MyHelpers;
using ABClient.MyProfile;

namespace ABClient
{
    /// <summary>
    /// Оркестратор сценария «Авто-Боссы» — автоматическая реакция на события
    /// нападения боссов из системного чата с навигацией и применением Свитка Защиты.
    /// 
    /// ПОРТИРОВАНО ИЗ: Android → BossAuto.java (1938 строк)
    /// 
    /// Архитектура (соответствие Android):
    ///   Android: BossAuto.java          → C#: BossAutoScenario.cs
    ///   Android: BossAuto.BossStage     → C#: BossStage (enum)
    ///   Android: BossAuto.onChatEvent() → C#: OnChatMessage()
    ///   Android: BossAuto.enable()      → C#: Enable()
    ///   Android: BossAuto.disable()     → C#: Disable()
    /// 
    /// Стадии сценария (BossStage):
    ///   Idle                   — ожидание события (Android: IDLE)
    ///   SearchingTarget        — поиск цели через AutoCompass (Android: SEARCHING_TARGET)
    ///   TargetFoundWaitScroll  — цель найдена, ожидание перед Свитком (Android: TARGET_FOUND_WAIT_SCROLL)
    ///   WaitFightStart         — Свиток отправлен, ожидание боя (Android: WAIT_FIGHT_START)
    ///   FightInProgress        — бой идёт, мониторинг (Android: FIGHT_IN_PROGRESS)
    ///   ReturningToOrigin      — возврат на исходную клетку (Android: RETURNING_TO_ORIGIN)
    /// 
    /// Зависимости:
    ///   - AutoCompass.Start/Stop — поиск цели (Android: CompasAuto.startSearch/stopSearch)
    ///   - NeverApi.GetAll(nick) — проверка FightLog цели (Android: NeverApi.GetAll)
    ///   - BossMap.GetRegNum(location) — возможные клетки (Android: BossMap.GetRegNum)
    ///   - ExtMap.MapPath(origin, cells) — расчёт маршрута (Android: MapPath)
    ///   - FormMain.MoveToDialog(cell) — навигация (Android: Navigator.moveTo)
    ///   - FormMain.FastAttackZas(nick) — Свиток Защиты (Android: FastActionManager.fastStart → i_w28_27.gif)
    ///   - FormMain.UpdateChat — уведомления (Android: Chat.addLocalMessage)
    ///   - FormMain.WriteMessageToChat — клан-чат (Android: Chat.sendClanMessage)
    ///   - AppVars.BossAutoEnabled — глобальный флаг (Android: auto_function_auto_boss)
    ///   - AppVars.LocationReal — текущая позиция (Android: LocationReal / myCoord)
    ///   - AppVars.Profile.BossAutoEnabled — настройка профиля (Android: prefs PREF_AUTO_BOSS)
    ///   - AppVars.Profile.BossAutoReport — уведомление в клан (Android: PREF_AUTO_BOSS_CLAN_NOTIFY)
    ///   - AppVars.Profile.BossSay — куда писать (LezSayType: No/Chat/Clan/Pair) (Android: BossSay)
    ///   - AppVars.CompassSnapshot — снимок от AutoCompass (Android: compassSnapshot)
    /// 
    /// Тайминг (соответствие Android):
    ///   SearchTimeout       = 6 мин  (Android: DEFAULT_SEARCH_TIMEOUT_SEC = 360)
    ///   WaitScrollTimeout   = 2 сек  (Android: DEFAULT_WAIT_BEFORE_SCROLL_SEC = 2)
    ///   WaitFightTimeout    = 25 сек (Android: DEFAULT_WAIT_FIGHT_TIMEOUT_SEC = 25)
    ///   ReturnTimeout       = 2 мин  (Android: RETURN_TIMEOUT_MS = 120000)
    ///   TimerTick interval  = 5 сек  (Android: targetFightPollIntervalMs = 1000)
    /// 
    /// Regex-паттерн (BossEventPattern):
    ///   Аналог Android: BOSS_EVENT_PATTERN_FLEX
    ///   Парсит: "Внимание! Случайное событие! Монстр [boss] напал на игрока [nick]."
    ///   Группы: Group[1] = bossName, Group[2] = victimPart (ники через запятую/"и")
    /// 
    /// Клан-уведомление (SendClanNotification):
    ///   Аналог Android: BossAuto.sendClanNotification()
    ///   Формат: "%clan%{bossName} напал на «{nick}» в локации [{location}]. Клетки: {cells}. [[[{flog}]]]"
    ///   Лимит длины: 160 символов (Android: CLAN_EVENT_CHAT_MAX_LEN = 160)
    /// </summary>
    internal static class BossAutoScenario
    {
        private static readonly object Lock = new object();
        private static BossStage _stage = BossStage.Idle; // Android: stage
        private static string _bossName = string.Empty; // Android: bossName
        private static string _targetNick = string.Empty; // Android: targetNick
        private static string _originCell = string.Empty; // Android: originRegNum
        private static DateTime _stageStartTime = DateTime.MinValue; // Android: stageStartedAtMs
        private static System.Windows.Forms.Timer _timer; // Android: Handler + postDelayed

        // Таймауты — соответствуют Android BossAuto.java
        private static readonly TimeSpan SearchTimeout = TimeSpan.FromMinutes(6); // Android: DEFAULT_SEARCH_TIMEOUT_SEC
        private static readonly TimeSpan WaitFightTimeout = TimeSpan.FromSeconds(25); // Android: DEFAULT_WAIT_FIGHT_TIMEOUT_SEC
        private static readonly TimeSpan WaitScrollTimeout = TimeSpan.FromSeconds(2); // Android: DEFAULT_WAIT_BEFORE_SCROLL_SEC
        private static readonly TimeSpan ReturnTimeout = TimeSpan.FromMinutes(2); // Android: RETURN_TIMEOUT_MS

        // Android: BOSS_EVENT_PATTERN_FLEX — парсинг события босса из чата
        private static readonly Regex BossEventPattern = new Regex(
            @"(?iu)(?:\d{1,2}/\d{1,2}/\d{2,4}\s+\d{1,2}:\d{2}:\d{2}\s+)?(?:внимание!\s*случайное\s+событие!\s*)?монстр\s*[""«]?([^""»]+)[""»]?\s*(?:напал|напала|напали)\s+на\s+(?:игрока|игроков)?\s*(.+?)\s*(?:[.,:;]|$)",
            RegexOptions.Compiled);

        /// <summary>
        /// Стадии сценария Авто-Боссов.
        /// Аналог Android: BossAuto.BossStage (enum с 6 значениями).
        /// </summary>
        private enum BossStage
        {
            Idle,                      // Android: IDLE
            SearchingTarget,           // Android: SEARCHING_TARGET
            TargetFoundWaitScroll,     // Android: TARGET_FOUND_WAIT_SCROLL
            WaitFightStart,            // Android: WAIT_FIGHT_START
            FightInProgress,           // Android: FIGHT_IN_PROGRESS
            ReturningToOrigin          // Android: RETURNING_TO_ORIGIN
        }

        /// <summary>
        /// Активен ли сценарий. Чтение _stage через Lock.
        /// </summary>
        internal static bool IsRunning
        {
            get
            {
                lock (Lock) { return _stage != BossStage.Idle; }
            }
        }

        /// <summary>
        /// Включение авто-боссов. Аналог Android: BossAuto.enable().
        /// Зависимости: AppVars.BossAutoEnabled, AppVars.Profile.BossAutoEnabled
        /// </summary>
        internal static void Enable()
        {
            AppVars.BossAutoEnabled = true;
            if (AppVars.Profile != null)
                AppVars.Profile.BossAutoEnabled = true;

            ReportStatus("[BossAuto] Авто-Боссы включены. Ожидание событий в чате.");
        }

        /// <summary>
        /// Выключение авто-боссов. Аналог Android: BossAuto.disable().
        /// Зависимости: AppVars.BossAutoEnabled, AppVars.Profile.BossAutoEnabled, StopScenario()
        /// </summary>
        internal static void Disable()
        {
            AppVars.BossAutoEnabled = false;
            if (AppVars.Profile != null)
                AppVars.Profile.BossAutoEnabled = false;

            StopScenario();
            ReportStatus("[BossAuto] Авто-Боссы выключены.");
        }

        /// <summary>
        /// Обработка сообщения чата — детект события босса.
        /// Аналог Android: BossAuto.onChatEvent(chatText).
        /// 
        /// Алгоритм:
        ///   1. Проверка: BossAutoEnabled (глобальный + профиль)
        ///   2. Regex-матч BossEventPattern → bossName + victimPart
        ///   3. Разделение victimPart по ","/" и " → victimNicks[]
        ///   4. Если _stage != Idle → игнор (сценарий уже активен, Android: EVENT_DEDUP_WINDOW_MS)
        ///   5. Фиксация: _bossName, _targetNick, _originCell = LocationReal
        ///   6. Переход в SearchingTarget + StartTimer + AutoCompass.Start
        /// 
        /// Вызов из: FormMainChat.ChatFilter() (PostFilter chain)
        /// </summary>
        internal static void OnChatMessage(string chatText)
        {
            if (!AppVars.BossAutoEnabled && !AppVars.Profile.BossAutoEnabled)
                return;

            var match = BossEventPattern.Match(chatText);
            if (!match.Success)
                return;

            var bossName = match.Groups[1].Value.Trim(); // Android: bossName from BOSS_EVENT_PATTERN_FLEX
            var victimPart = match.Groups[2].Value.Trim(); // Android: victimPart

            var victimNicks = victimPart.Split(new[] { ",", " и " }, StringSplitOptions.RemoveEmptyEntries);
            if (victimNicks.Length == 0)
                return;

            var targetNick = victimNicks[0].Trim(); // Android: targetNick = первый ник

            lock (Lock)
            {
                if (_stage != BossStage.Idle) // Android: event dedup window
                {
                    ReportStatus($"[BossAuto] Событие проигнорировано (сценарий активен): {bossName} -> {targetNick}");
                    return;
                }

                _bossName = bossName;
                _targetNick = targetNick;
                _originCell = AppVars.LocationReal; // Android: originRegNum
                _stage = BossStage.SearchingTarget; // Android: SEARCHING_TARGET
                _stageStartTime = DateTime.Now; // Android: stageStartedAtMs
            }

            ReportStatus($"[BossAuto] Босс {bossName} напал на {targetNick}! Начинаю поиск...");

            StartTimer(5000);

            // Делегируем поиск цели в AutoCompass — не дублируем логику навигации
            // Android: BossAuto тоже делегирует поиск в CompasAuto
            AutoCompass.Start(_targetNick);
        }

        /// <summary>
        /// Запуск/перезапуск таймера стадии. Аналог Android: Handler.postDelayed(runnable, delayMs).
        /// </summary>
        private static void StartTimer(int intervalMs)
        {
            if (_timer == null)
            {
                _timer = new System.Windows.Forms.Timer();
                _timer.Tick += TimerTick;
            }

            _timer.Interval = intervalMs;
            _timer.Start();
        }

        private static void StopTimer()
        {
            if (_timer != null)
            {
                _timer.Stop();
            }
        }

        /// <summary>
        /// Тик таймера — диспетчеризация по текущей стадии.
        /// Аналог Android: BossAuto.onTimerTick() → switch(stage).
        /// </summary>
        private static void TimerTick(object sender, EventArgs e)
        {
            BossStage currentStage;
            string targetNick;
            string originCell;
            DateTime stageStart;

            lock (Lock)
            {
                currentStage = _stage;
                targetNick = _targetNick;
                originCell = _originCell;
                stageStart = _stageStartTime;
            }

            switch (currentStage)
            {
                case BossStage.SearchingTarget:
                    ProcessSearchingTarget(targetNick, stageStart);
                    break;

                case BossStage.TargetFoundWaitScroll:
                    ProcessWaitScroll(stageStart);
                    break;

                case BossStage.WaitFightStart:
                    ProcessWaitFightStart(targetNick, stageStart);
                    break;

                case BossStage.FightInProgress:
                    ProcessFightInProgress(targetNick, stageStart);
                    break;

                case BossStage.ReturningToOrigin:
                    ProcessReturningToOrigin(originCell, stageStart);
                    break;
            }
        }

        /// <summary>
        /// Стадия SEARCHING_TARGET: поиск цели (через CompassSnapshot + NeverApi).
        /// Аналог Android: BossAuto.ProcessSearchingTarget().
        /// 
        /// Алгоритм:
        ///   1. Проверка SearchTimeout (6 мин) → StopScenario если превышен
        ///   2. Проверка CompassSnapshot.IsOnline → если null/offline, ждём следующий тик
        ///   3. NeverApi.GetAll(targetNick) → проверка FightLog
        ///   4. Если FightLog == "0" → цель не в бою, ждём
        ///   5. Парсинг локации → HelperStrings.SubString(location, "[", "]")
        ///   6. BossMap.GetRegNum(location) → возможные клетки
        ///   7. MapPath(myLocation, cells) → ближайшая клетка + шаги
        ///   8. Переход: MoveToDialog(nearestCell)
        ///   9. Остановка AutoCompass (не нужен больше)
        ///   10. Переход в TargetFoundWaitScroll
        ///   11. Если BossAutoReport → SendClanNotification
        /// 
        /// Зависимости: AppVars.CompassSnapshot, NeverApi.GetAll, BossMap.GetRegNum,
        ///   MapPath, FormMain.MoveToDialog, AutoCompass.Stop
        /// </summary>
        private static void ProcessSearchingTarget(string targetNick, DateTime stageStart)
        {
            if (DateTime.Now - stageStart > SearchTimeout) // Android: DEFAULT_SEARCH_TIMEOUT_SEC
            {
                ReportStatus($"[BossAuto] Таймаут поиска цели {targetNick}. Отмена.");
                StopScenario();
                return;
            }

            var snapshot = AppVars.CompassSnapshot; // Android: compassSnapshot
            if (snapshot == null || !snapshot.IsOnline)
                return;

            var userInfo = NeverApi.GetAll(targetNick);
            if (userInfo == null)
                return;

            // Цель должна быть в бою — как в Android: targetNick в бою → fight_pm[4]
            if (string.IsNullOrEmpty(userInfo.FightLog) || userInfo.FightLog.Equals("0", StringComparison.Ordinal))
                return;

            var location = userInfo.Location;
            var infLocation = location;
            if (infLocation.IndexOf('[') != -1)
                infLocation = HelperStrings.SubString(infLocation, "[", "]");

            // BossMap.GetRegNum — возможные клетки локации (Android: BossMap.GetRegNum)
            var possibleCells = BossMap.GetRegNum(infLocation);
            if (string.IsNullOrEmpty(possibleCells))
                return;

            var cells = possibleCells.Split(new[] { ", " }, StringSplitOptions.RemoveEmptyEntries);
            if (cells.Length == 0)
                return;

            var myLocation = AppVars.LocationReal; // Android: LocationReal
            var path = new ExtMap.MapPath(myLocation, cells); // Android: MapPath
            var nearestCell = path.Destination ?? myLocation; // Android: path.Destination
            var jumps = path.Jumps; // Android: path.Jumps

            ReportStatus($"[BossAuto] Цель {targetNick} найдена! Клетка: {nearestCell} ({jumps} шагов). Иду.");

            lock (Lock)
            {
                _stage = BossStage.TargetFoundWaitScroll; // Android: TARGET_FOUND_WAIT_SCROLL
                _stageStartTime = DateTime.Now;
            }

            // Останавливаем AutoCompass — навигация теперь через BossAuto
            AutoCompass.Stop();

            // Навигация к ближайшей клетке — аналог FormCompas.WbBeforeNavigate
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        (MethodInvoker)(() => AppVars.MainForm.MoveToDialog(nearestCell)));
                }
            }
            catch (InvalidOperationException)
            {
            }

            // Клан-уведомление — Android: BossAuto.sendClanNotification
            if (AppVars.Profile.BossAutoReport)
            {
                SendClanNotification(_bossName, targetNick, infLocation, possibleCells, userInfo.FightLog);
            }
        }

        /// <summary>
        /// Стадия TARGET_FOUND_WAIT_SCROLL: ожидание перед Свитком Защиты.
        /// Аналог Android: BossAuto.ProcessWaitScroll().
        /// 
        /// Зависимости: WaitScrollTimeout (2 сек), FormMain.FastAttackZas(nick)
        /// FastAttackZas → FastStartSafe("i_w28_27.gif", nick) → Свиток Защиты
        /// Android: FastActionManager.fastStart("i_w28_27.gif", nick)
        /// </summary>
        private static void ProcessWaitScroll(DateTime stageStart)
        {
            if (DateTime.Now - stageStart > WaitScrollTimeout) // Android: DEFAULT_WAIT_BEFORE_SCROLL_SEC
            {
                ReportStatus("[BossAuto] Применяю Свиток Защиты...");

                // Свиток Защиты — FastAction "i_w28_27.gif"
                // Android: FastActionManager.fastStart → i_w28_27.gif (zas scroll)
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            (MethodInvoker)(() =>
                            {
                                FormMain.FastAttackZas(_targetNick); // FormMainFast.cs: FastAttackZas
                            }));
                    }
                }
                catch (InvalidOperationException)
                {
                }

                lock (Lock)
                {
                    _stage = BossStage.WaitFightStart; // Android: WAIT_FIGHT_START
                    _stageStartTime = DateTime.Now;
                }
            }
        }

        /// <summary>
        /// Стадия WAIT_FIGHT_START: ожидание начала боя.
        /// Аналог Android: BossAuto.ProcessWaitFightStart().
        /// 
        /// Алгоритм:
        ///   1. Проверка WaitFightTimeout (25 сек) → StartReturnToOrigin
        ///   2. NeverApi.GetAll(targetNick) → проверка FightLog
        ///   3. Если FightLog != "0" → бой начался → FightInProgress
        /// 
        /// Зависимости: NeverApi.GetAll, WaitFightTimeout
        /// </summary>
        private static void ProcessWaitFightStart(string targetNick, DateTime stageStart)
        {
            if (DateTime.Now - stageStart > WaitFightTimeout) // Android: DEFAULT_WAIT_FIGHT_TIMEOUT_SEC
            {
                ReportStatus($"[BossAuto] Таймаут ожидания боя с {targetNick}. Возврат.");
                StartReturnToOrigin();
                return;
            }

            var userInfo = NeverApi.GetAll(targetNick);
            if (userInfo == null)
                return;

            if (!string.IsNullOrEmpty(userInfo.FightLog) && !userInfo.FightLog.Equals("0", StringComparison.Ordinal))
            {
                ReportStatus($"[BossAuto] Бой начался! Лог: {userInfo.FightLog}");
                lock (Lock)
                {
                    _stage = BossStage.FightInProgress; // Android: FIGHT_IN_PROGRESS
                    _stageStartTime = DateTime.Now;
                }

                StartTimer(3000); // Опрос каждые 3 сек — Android: targetFightPollIntervalMs
            }
        }

        /// <summary>
        /// Стадия FIGHT_IN_PROGRESS: мониторинг боя.
        /// Аналог Android: BossAuto.ProcessFightInProgress().
        /// 
        /// Алгоритм:
        ///   1. Проверка максимального таймаута (10 мин)
        ///   2. NeverApi.GetAll(targetNick) → проверка FightLog
        ///   3. Если FightLog == "0" → бой завершён → StartReturnToOrigin
        /// 
        /// Зависимости: NeverApi.GetAll
        /// </summary>
        private static void ProcessFightInProgress(string targetNick, DateTime stageStart)
        {
            if (DateTime.Now - stageStart > TimeSpan.FromMinutes(10))
            {
                ReportStatus("[BossAuto] Таймаут боя. Возврат.");
                StartReturnToOrigin();
                return;
            }

            var userInfo = NeverApi.GetAll(targetNick);
            if (userInfo == null)
                return;

            // Бой завершён — FightLog обнулился
            if (string.IsNullOrEmpty(userInfo.FightLog) || userInfo.FightLog.Equals("0", StringComparison.Ordinal))
            {
                ReportStatus("[BossAuto] Бой завершён. Возврат на исходную клетку.");
                StartReturnToOrigin(); // Android: RETURNING_TO_ORIGIN
            }
        }

        /// <summary>
        /// Стадия RETURNING_TO_ORIGIN: возврат на исходную клетку.
        /// Аналог Android: BossAuto.ProcessReturningToOrigin().
        /// 
        /// Алгоритм:
        ///   1. Проверка ReturnTimeout (2 мин) → StopScenario
        ///   2. Проверка LocationReal == originCell → StopScenario (успех)
        ///   3. MapPath(current, originCell) → если 1 шаг → MoveToDialog
        /// 
        /// Зависимости: AppVars.LocationReal, MapPath, FormMain.MoveToDialog
        /// </summary>
        private static void ProcessReturningToOrigin(string originCell, DateTime stageStart)
        {
            if (DateTime.Now - stageStart > ReturnTimeout) // Android: RETURN_TIMEOUT_MS
            {
                ReportStatus("[BossAuto] Таймаут возврата. Сценарий завершён.");
                StopScenario();
                return;
            }

            if (AppVars.LocationReal.Equals(originCell, StringComparison.OrdinalIgnoreCase))
            {
                ReportStatus("[BossAuto] Возврат завершён. Сценарий окончен.");
                StopScenario();
                return;
            }

            // Пошаговый возврат — аналог Navigator (Android: Navigator.moveTo)
            var path = new ExtMap.MapPath(AppVars.LocationReal, new[] { originCell });
            if (!string.IsNullOrEmpty(path.Destination) && path.Jumps <= 1)
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            (MethodInvoker)(() => AppVars.MainForm.MoveToDialog(originCell)));
                    }
                }
                catch (InvalidOperationException)
                {
                }
            }
        }

        /// <summary>
        /// Переход в стадию RETURNING_TO_ORIGIN + запуск навигации.
        /// Аналог Android: BossAuto.startReturnToOrigin().
        /// Зависимости: FormMain.MoveToDialog, StartTimer
        /// </summary>
        private static void StartReturnToOrigin()
        {
            lock (Lock)
            {
                _stage = BossStage.ReturningToOrigin; // Android: RETURNING_TO_ORIGIN
                _stageStartTime = DateTime.Now;
            }

            var originCell = _originCell; // Android: originRegNum
            if (!string.IsNullOrEmpty(originCell))
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            (MethodInvoker)(() => AppVars.MainForm.MoveToDialog(originCell)));
                    }
                }
                catch (InvalidOperationException)
                {
                }
            }

            StartTimer(5000); // Опрос каждые 5 сек
        }

        /// <summary>
        /// Полная остановка сценария. Сброс всех полей.
        /// Аналог Android: BossAuto.stopScenario().
        /// Зависимости: StopTimer(), AutoCompass.Stop()
        /// </summary>
        private static void StopScenario()
        {
            StopTimer();
            AutoCompass.Stop(); // Остановка компаса если активен

            lock (Lock)
            {
                _stage = BossStage.Idle;
                _bossName = string.Empty;
                _targetNick = string.Empty;
                _originCell = string.Empty;
                _stageStartTime = DateTime.MinValue;
            }
        }

        /// <summary>
        /// Отправка клан-уведомления о боссе.
        /// Аналог Android: BossAuto.sendClanNotification().
        /// 
        /// Формат: "{suffix}{bossName} напал на «{targetNick}» в локации [{location}]. Клетки: {cells}. [[[{flog}]]]"
        /// suffix: %clan% или %pair% (зависит от BossSay)
        /// Лимит длины: 160 символов (Android: CLAN_EVENT_CHAT_MAX_LEN = 160)
        /// 
        /// Зависимости:
        ///   - AppVars.Profile.BossSay (LezSayType: No/Chat/Clan/Pair) — аналог Android: BossSay
        ///   - FormMain.WriteMessageToChat — отправка через UpdateWriteRealChatMsgDelegate
        /// </summary>
        private static void SendClanNotification(string bossName, string targetNick, string location, string possibleCells, string flog)
        {
            if (AppVars.Profile.BossSay == LezSayType.No) // Не отправлять если No
                return;

            var suffix = string.Empty;
            switch (AppVars.Profile.BossSay)
            {
                case LezSayType.Clan:
                    suffix = "%clan%"; // Формат клан-чата: %clan%text
                    break;
                case LezSayType.Pair:
                    suffix = "%pair%"; // Формат парного чата: %pair%text
                    break;
            }

            var msg = $"{suffix}{bossName} напал на «{targetNick}» в локации [{location}]. Клетки: {possibleCells}. [[[{flog}]]]";
            if (msg.Length > 160) // Android: CLAN_EVENT_CHAT_MAX_LEN
                msg = msg.Substring(0, 157) + "...";

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateWriteRealChatMsgDelegate(AppVars.MainForm.WriteMessageToChat), msg);
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        /// <summary>
        /// Вывод статусного сообщения в чат. Аналог Android: Chat.addLocalMessage().
        /// Зависимости: FormMain.UpdateChat, UpdateChatDelegate
        /// </summary>
        private static void ReportStatus(string message)
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat), message);
                }
            }
            catch (InvalidOperationException)
            {
            }
        }
    }
}