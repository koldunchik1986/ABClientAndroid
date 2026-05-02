using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using ANClient.ANForms;
using ANClient.ExtMap;
using ANClient.MyHelpers;
using ANClient.MyProfile;

namespace ANClient
{
    /// <summary>
    /// Модуль автоматического компаса — периодический поиск цели через NeverApi
    /// с автоматическим перемещением и уведомлениями.
    /// 
    /// ПОРТИРОВАНО ИЗ: Android → CompasAuto.java (1284 строки)
    /// 
    /// Архитектура (соответствие Android):
    ///   Android: CompasAuto.java → C#: AutoCompass.cs
    ///   Android: CompasAuto.startSearch(nick) → C#: AutoCompass.Start(targetNick)
    ///   Android: CompasAuto.stopSearch() → C#: AutoCompass.Stop()
    ///   Android: CompasAuto.pulse() → C#: AutoCompass.Pulse()
    ///   Android: CompasAuto.BossStage.SEARCHING_TARGET → C#: (управление из BossAutoScenario)
    /// 
    /// Зависимости:
    ///   - NeverApi.GetAll(nick) — получение UserInfo (location, fightLog, level, online)
    ///   - AppVars.LocationReal — текущая позиция персонажа (Android: myCoord)
    ///   - AppVars.AutoCompassEnabled — глобальный флаг активности (Android: autoCompassEnabled)
    ///   - AppVars.CompassSnapshot — кэш последнего снимка PinfoCompassSnapshot
    ///   - AppVars.Profile.CompassAutoOnBattle — искать при бое цели (Android: compassAutoOnBattle)
    ///   - AppVars.Profile.CompassAutoAttack — автоперемещение (Android: compassAutoAttack)
    ///   - AppVars.Profile.CompassAutoWhisper — шёпот при нахождении (Android: compassAutoWhisper)
    ///   - AppVars.Profile.CompassSearchRadius — радиус поиска в шагах (Android: compassSearchRadius)
    ///   - Map.Cells — словарь клеток карты (key=regnum, value=AncCell с Tooltip)
    ///   - MapPath(origin, destinations[]) — расчёт маршрута, Jumps/Destination (Android: MapPath)
    ///   - HelperStrings.SubString() — парсинг подстрок (локация из "Location [coord]")
    ///   - FormMain.MoveToDialog(cell) — переход на клетку (Android: Navigator.moveTo)
    ///   - FormMain.UpdateChat — вывод в чат через BeginInvoke + UpdateChatDelegate
    ///   - FormMain.WriteMessageToChat — отправка шёпота через UpdateWriteRealChatMsgDelegate
    /// 
    /// Таймер: System.Windows.Forms.Timer, интервал 30 сек
    ///   Android: Handler.postDelayed + compassSearchIntervalMs
    /// 
    /// Сценарий работы:
    ///   1. Start(nick) → запуск таймера + первый поиск
    ///   2. TimerTick → CompassSearchAsync(nick) через ThreadPool
    ///   3. CompassSearchAsync → NeverApi.GetAll → PinfoCompassSnapshot → FindPossibleCells → MapPath
    ///   4. Если цель рядом (jumps <= SearchRadius) → OnTargetFoundNearby → MoveToDialog
    ///   5. Если цель в бою + CompassAutoOnBattle → OnTargetFoundInBattle
    ///   6. Stop() → остановка таймера, очистка состояния
    /// </summary>
    internal static class AutoCompass
    {
        private static System.Windows.Forms.Timer _timer; // Android: Handler compassHandler
        private static readonly object Lock = new object();
        private static string _targetNick = string.Empty; // Android: targetNick
        private static bool _isRunning; // Android: isRunning

        internal static bool IsEnabled
        {
            get { return AppVars.AutoCompassEnabled; }
        }

        /// <summary>
        /// Запуск авто-компаса. Аналог Android: CompasAuto.startSearch(nick).
        /// Зависимости: AppVars.AutoCompassEnabled, FormMain.UpdateChat
        /// </summary>
        internal static void Start(string targetNick)
        {
            lock (Lock)
            {
                if (_isRunning)
                    return;

                _targetNick = targetNick;
                _isRunning = true;
                AppVars.AutoCompassEnabled = true;
            }

            if (_timer == null)
            {
                _timer = new System.Windows.Forms.Timer { Interval = 30000 }; // Android: 30 сек
                _timer.Tick += TimerTick;
            }

            _timer.Start();
            ThreadPool.QueueUserWorkItem(CompassSearchAsync, _targetNick);

            AppLog.i("AutoCompass", "START: target=" + _targetNick);

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        $"[AutoCompass] Запуск поиска: {_targetNick}");
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        /// <summary>
        /// Остановка авто-компаса. Аналог Android: CompasAuto.stopSearch().
        /// Зависимости: AppVars.AutoCompassEnabled, FormMain.UpdateChat
        /// </summary>
        internal static void Stop()
        {
            lock (Lock)
            {
                _isRunning = false;
                AppVars.AutoCompassEnabled = false;
                _targetNick = string.Empty;
            }

            if (_timer != null)
            {
                _timer.Stop();
            }

            AppLog.i("AutoCompass", "STOP");

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        "[AutoCompass] Поиск остановлен");
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        /// <summary>
        /// Тик таймера — запуск очередного цикла поиска.
        /// Аналог Android: compassHandler.postDelayed → onSearchTick()
        /// </summary>
        private static void TimerTick(object sender, EventArgs e)
        {
            lock (Lock)
            {
                if (!_isRunning)
                    return;
            }

            ThreadPool.QueueUserWorkItem(CompassSearchAsync, _targetNick);
        }

        /// <summary>
        /// Основной цикл поиска. Аналог Android: CompasAuto.compassSearchAsync().
        /// 
        /// Алгоритм:
        ///   1. NeverApi.GetAll(nick) → UserInfo
        ///   2. Заполнение AppVars.CompassSnapshot (PinfoCompassSnapshot)
        ///   3. Проверка: оффлайн → ReportStatus + return
        ///   4. Проверка: в бою (FightLog != "0") → OnTargetFoundInBattle (если CompassAutoOnBattle)
        ///   5. Парсинг локации: HelperStrings.SubString(location, "[", "]")
        ///   6. FindPossibleCells(location) → список regnum-клеток
        ///   7. MapPath(myLocation, cells) → ближайшая клетка + кол-во шагов
        ///   8. Если CompassAutoAttack && jumps <= CompassSearchRadius → OnTargetFoundNearby
        /// 
        /// Зависимости: NeverApi.GetAll, AppVars.CompassSnapshot, AppVars.LocationReal,
        ///   HelperStrings.SubString, Map.Cells, MapPath, AppVars.Profile.Compass*
        /// </summary>
        private static void CompassSearchAsync(object state)
        {
            var nick = state as string;
            if (string.IsNullOrEmpty(nick))
                return;

            var userInfo = NeverApi.GetAll(nick);
            if (userInfo == null)
            {
                ReportStatus($"[AutoCompass] Не удалось получить инфу о {nick}");
                return;
            }

            var location = userInfo.Location; // Android: userInfo.location
            var isOnline = !string.IsNullOrEmpty(location); // Android: isonline

            // Обновляем глобальный снимок — используется BossAutoScenario
            AppVars.CompassSnapshot = new PinfoCompassSnapshot
            {
                Nick = userInfo.Nick,
                Location = location,
                Coord = ExtractCoord(location),
                Timestamp = DateTime.Now,
                IsOnline = isOnline
            };

            AppLog.d("AutoCompass", "SEARCH: nick=" + nick + " online=" + isOnline + " location=" + location + " fightLog=" + (userInfo.FightLog ?? "null"));

            if (!isOnline)
            {
                ReportStatus($"[AutoCompass] {nick} — оффлайн");
                return;
            }

            // Цель в бою — проверяем CompassAutoOnBattle (Android: compassAutoOnBattle)
            if (!string.IsNullOrEmpty(userInfo.FightLog) && !userInfo.FightLog.Equals("0", StringComparison.Ordinal))
            {
                ReportStatus($"[AutoCompass] {nick} — в бою (лог: {userInfo.FightLog})");

                if (AppVars.Profile.CompassAutoOnBattle)
                {
                    OnTargetFoundInBattle(nick, userInfo);
                }

                return;
            }

            // Парсинг локации: "Окрестности Форпоста [Окрестность Форпоста, Биржа]" → "Окрестность Форпоста, Биржа"
            var infLocation = location;
            if (infLocation.IndexOf('[') != -1)
            {
                infLocation = HelperStrings.SubString(infLocation, "[", "]");
            }

            var possibleCells = FindPossibleCells(infLocation); // Android: arrayPossibleLocations
            if (possibleCells.Count == 0)
            {
                ReportStatus($"[AutoCompass] {nick} — локация не найдена: {infLocation}");
                return;
            }

            var myLocation = AppVars.LocationReal; // Android: myCoord / LocationReal
            var path = new MapPath(myLocation, possibleCells.ToArray()); // Android: MapPath

            var nearestCell = path.Destination ?? myLocation; // Android: path.Destination
            var jumps = path.Jumps; // Android: path.Jumps

            ReportStatus($"[AutoCompass] {nick} — {infLocation}, ближайшая клетка: {nearestCell} ({jumps} шагов)");

            // Автоперемещение если включено и цель в радиусе
            // Android: compassAutoAttack + compassSearchRadius
            if (AppVars.Profile.CompassAutoAttack && jumps <= AppVars.Profile.CompassSearchRadius)
            {
                OnTargetFoundNearby(nick, nearestCell, userInfo);
            }
        }

        /// <summary>
        /// Поиск возможных клеток по названию локации.
        /// Аналог Android: CompasAuto → arrayPossibleLocations (перебор Map.Cells).
        /// Зависимости: Map.Cells (key=regnum, value=AncCell.Tooltip)
        /// </summary>
        private static List<string> FindPossibleCells(string locationName)
        {
            var result = new List<string>();

            if (string.IsNullOrEmpty(locationName))
                return result;

            foreach (var cellKey in Map.Cells.Keys)
            {
                var cell = Map.Cells[cellKey];
                if (cell != null && cell.Tooltip.Equals(locationName, StringComparison.OrdinalIgnoreCase))
                {
                    result.Add(cellKey);
                }
            }

            return result;
        }

        /// <summary>
        /// Извлечение координаты из строки локации "Location [coord]".
        /// Зависимости: HelperStrings.SubString
        /// </summary>
        private static string ExtractCoord(string location)
        {
            if (string.IsNullOrEmpty(location))
                return string.Empty;

            if (location.IndexOf('[') != -1)
            {
                return HelperStrings.SubString(location, "[", "]");
            }

            return string.Empty;
        }

        /// <summary>
        /// Обработка: цель рядом. Переход на клетку + шёпот.
        /// Аналог Android: CompasAuto.onTargetFoundNearby().
        /// 
        /// Зависимости:
        ///   - FormMain.MoveToDialog(cell) — навигация (Android: Navigator.moveTo)
        ///   - FormMain.WriteMessageToChat — шёпот (Android: Chat.sendWhisper)
        ///   - AppVars.Profile.CompassAutoWhisper (Android: compassAutoWhisper)
        /// </summary>
        private static void OnTargetFoundNearby(string nick, string cell, UserInfo userInfo)
        {
            ReportStatus($"[AutoCompass] Цель рядом! Идём на {cell}");

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        $"[AutoCompass] Перемещение к {nick} ({cell})");
                }
            }
            catch (InvalidOperationException)
            {
            }

            // Навигация — аналог FormCompas.WbBeforeNavigate → MoveToDialog
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        (MethodInvoker)(() => AppVars.MainForm.MoveToDialog(cell)));
                }
            }
            catch (InvalidOperationException)
            {
            }

            // Шёпот цели — Android: compassAutoWhisper
            if (AppVars.Profile.CompassAutoWhisper)
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        var whisperMsg = $"%{nick}% Вижу тебя!"; // Формат: %nick% text
                        AppVars.MainForm.BeginInvoke(
                            new UpdateWriteRealChatMsgDelegate(AppVars.MainForm.WriteMessageToChat),
                            whisperMsg);
                    }
                }
                catch (InvalidOperationException)
                {
                }
            }
        }

        /// <summary>
        /// Обработка: цель в бою. Если CompassAutoAttack — перемещение к месту боя.
        /// Аналог Android: CompasAuto.onTargetFoundInBattle().
        /// 
        /// Зависимости:
        ///   - FindPossibleCells(infLocation) → список клеток
        ///   - MapPath(myLocation, cells) → ближайшая клетка
        ///   - FormMain.MoveToDialog(nearestCell) — навигация
        ///   - AppVars.Profile.CompassAutoAttack (Android: compassAutoAttack)
        ///   - AppVars.Profile.CompassSearchRadius (Android: compassSearchRadius)
        /// </summary>
        private static void OnTargetFoundInBattle(string nick, UserInfo userInfo)
        {
            var location = userInfo.Location;
            var infLocation = location;
            if (infLocation.IndexOf('[') != -1)
            {
                infLocation = HelperStrings.SubString(infLocation, "[", "]");
            }

            ReportStatus($"[AutoCompass] {nick} в бою, локация: {infLocation}");

            if (AppVars.Profile.CompassAutoAttack)
            {
                var possibleCells = FindPossibleCells(infLocation);
                if (possibleCells.Count > 0)
                {
                    var myLocation = AppVars.LocationReal;
                    var path = new MapPath(myLocation, possibleCells.ToArray());
                    var nearestCell = path.Destination ?? myLocation;

                    if (path.Jumps <= AppVars.Profile.CompassSearchRadius)
                    {
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
                    }
                }
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

        /// <summary>
        /// Внешний запуск цикла поиска. Вызывается из ContactsManager.Pulse().
        /// Аналог Android: CompasAuto.pulse().
        /// </summary>
        internal static void Pulse()
        {
            lock (Lock)
            {
                if (!_isRunning || string.IsNullOrEmpty(_targetNick))
                    return;
            }

            ThreadPool.QueueUserWorkItem(CompassSearchAsync, _targetNick);
        }
    }
}