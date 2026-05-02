using System;
using System.Collections.Generic;

namespace ANClient
{
    /// <summary>
    /// Снимок данных pinfo-запроса для AutoCompass.
    /// 
    /// Зависимости:
    ///   - AppVars.CompassSnapshot (глобальное хранилище текущего снимка)
    ///   - AutoCompass.CompassSearchAsync() — создаёт и заполняет этот объект
    ///   - BossAutoScenario.ProcessSearchingTarget() — читает CompassSnapshot для принятия решений
    /// 
    /// Соответствие Android:
    ///   Android: CompasAuto.java → PinfoCompassSnapshot (внутренний класс)
    ///   Поля: nick → nick, location → location, coord → coord,
    ///         timestamp → timestamp, isOnline → isOnline
    /// </summary>
    internal class PinfoCompassSnapshot
    {
        internal string Nick { get; set; }
        internal string Location { get; set; }
        internal string Coord { get; set; }
        internal DateTime Timestamp { get; set; }
        internal bool IsOnline { get; set; }
    }

    /// <summary>
    /// Информация о клановой войне/боевом событии соклановца.
    /// 
    /// Зависимости:
    ///   - AppVars.ClanWarsList (глобальный список текущих боёв соклановцев)
    ///   - ClanWarsManager.RefreshAsync() — создаёт и заполняет этот объект
    /// 
    /// Соответствие Android:
    ///   Android: BossAuto.java → bossTargetClanToken / bossSelfClanToken (строковые токены)
    ///   В C# упрощено: вместо токенов — ClanTag/EnemyClanTag + StartTime + IsActive
    /// </summary>
    internal class ClanWarInfo
    {
        internal string ClanTag { get; set; }
        internal string EnemyClanTag { get; set; }
        internal DateTime StartTime { get; set; }
        internal bool IsActive { get; set; }
    }
}