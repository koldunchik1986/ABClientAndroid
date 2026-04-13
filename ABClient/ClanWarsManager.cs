using System;
using System.Collections.Generic;
using System.Threading;
using ABClient.ABForms;
using ABClient.MyProfile;

namespace ABClient
{
    /// <summary>
    /// Менеджер клановых войн — асинхронный сбор и отображение информации
    /// о боях соклановцев через единый API getinfo.cgi (NeverApi.GetAll).
    /// 
    /// ВНИМАНИЕ: allnl.ru НЕ используется (сервис неработоспособен).
    /// Все данные получаются исключительно через игровое API:
    ///   - getid.cgi — получение ID по нику
    ///   - info.cgi  — получение полной инфы (location, fightLog, online, clan)
    /// 
    /// Источник списка членов клана:
    ///   - AppVars.Profile.Contacts — контакты с включённым Tracing
    ///   - AppVars.BossContacts — босс-контакты (слежение за боссами)
    ///   - Ручное добавление через AddTrackedNick(nick)
    /// 
    /// Зависимости:
    ///   - NeverApi.GetAll(nick) — единый модуль getinfo.cgi (Android: NeverApi.GetAll)
    ///   - AppVars.Profile.UserNick — ник текущего пользователя
    ///   - AppVars.Profile.Contacts — список контактов для проверки
    ///   - AppVars.BossContacts — список босс-контактов для проверки
    ///   - AppVars.ClanWarsList — глобальный список ClanWarInfo
    ///   - AppVars.Profile.DoBossTrace — включено ли слежение за боссами
    ///   - FormMain.UpdateChat — вывод в чат через BeginInvoke + UpdateChatDelegate
    /// 
    /// Соответствие Android:
    ///   Android: BossAuto.java → bossSelfClanToken / cachedSelfClanToken
    ///   В Android клан-членство определяется через NeverApi.GetAll()
    ///   с проверкой userInfo.ClanName == myClanName.
    ///   Здесь применяется тот же подход: NeverApi.GetAll → ClanName comparison.
    /// 
    /// Тайминг:
    ///   RefreshInterval = 5 минут (аналог Android: CLAN_NOTIFY_DELAY_MS + кэш)
    /// </summary>
    internal static class ClanWarsManager
    {
        private static readonly object Lock = new object();
        private static DateTime _lastRefresh = DateTime.MinValue;
        private static readonly TimeSpan RefreshInterval = TimeSpan.FromMinutes(5);
        private static readonly List<string> _trackedNicks = new List<string>();
        private static string _myClanName = string.Empty; // Android: cachedSelfClanToken

        /// <summary>
        /// Добавить ник в список отслеживаемых для проверки боёв.
        /// Аналог Android: BossAuto.addTrackedNick(nick).
        /// </summary>
        internal static void AddTrackedNick(string nick)
        {
            if (string.IsNullOrEmpty(nick))
                return;

            lock (Lock)
            {
                var nickLower = nick.ToLower();
                if (!_trackedNicks.Contains(nickLower))
                    _trackedNicks.Add(nickLower);
            }
        }

        /// <summary>
        /// Удалить ник из списка отслеживаемых.
        /// </summary>
        internal static void RemoveTrackedNick(string nick)
        {
            if (string.IsNullOrEmpty(nick))
                return;

            lock (Lock)
            {
                _trackedNicks.Remove(nick.ToLower());
            }
        }

        /// <summary>
        /// Запуск асинхронного обновления списка клановых боёв.
        /// Защита от частого вызова: не чаще 1 раза в RefreshInterval.
        /// Аналог Android: ClanWarsManager.refreshClanWars() с CLAN_NOTIFY_DELAY_MS.
        /// </summary>
        internal static void RefreshClanWars()
        {
            lock (Lock)
            {
                if (DateTime.Now - _lastRefresh < RefreshInterval)
                    return;

                _lastRefresh = DateTime.Now;
            }

            ThreadPool.QueueUserWorkItem(RefreshAsync);
        }

        /// <summary>
        /// Асинхронный сбор боёв отслеживаемых персонажей.
        /// Источник данных — ТОЛЬКО NeverApi.GetAll (getinfo.cgi), без внешних сайтов.
        /// 
        /// Алгоритм:
        ///   1. NeverApi.GetAll(myNick) → получаем ClanName текущего юзера
        ///   2. Собираем список проверяемых ников из:
        ///      a. AppVars.Profile.Contacts (Tracing == true)
        ///      b. AppVars.BossContacts (если DoBossTrace)
        ///      c. _trackedNicks (ручные добавления)
        ///   3. Для каждого ника: NeverApi.GetAll(nick) → проверка
        ///      a. Online + FightLog != "0" → в бою
        ///      b. ClanName == _myClanName → соклановец (если клан задан)
        ///   4. Если в бою → добавляем ClanWarInfo + уведомление в чат
        /// 
        /// Зависимости: NeverApi.GetAll (единый модуль getinfo.cgi)
        ///   - getid.cgi?{encnick} → ID персонажа
        ///   - info.cgi?playerid={id}&info=1&hmu=1&effects=1&slots=1 → полная инфа
        /// </summary>
        private static void RefreshAsync(object state)
        {
            var myNick = AppVars.Profile?.UserNick;
            if (string.IsNullOrEmpty(myNick))
                return;

            // Шаг 1: определяем свой клан через getinfo.cgi
            var myInfo = NeverApi.GetAll(myNick);
            if (myInfo == null)
                return;

            _myClanName = myInfo.ClanName ?? string.Empty; // Android: cachedSelfClanToken

            AppLog.d("ClanWars", "MyClan=" + _myClanName);

            // Шаг 2: собираем список ников для проверки
            var nicksToCheck = new List<string>();

            // 2a: Контакты с включённым Tracing
            // Android: ContactsManager.Pulse() → ProcessAsync → NeverApi.GetAll
            try
            {
                foreach (var contact in AppVars.Profile.Contacts)
                {
                    if (contact.Value.Tracing && !nicksToCheck.Contains(contact.Key))
                        nicksToCheck.Add(contact.Key);
                }
            }
            catch (ApplicationException)
            {
            }

            // 2b: Босс-контакты (если слежение включено)
            // Android: BossAuto → BossContacts → BossContact.Process
            if (AppVars.Profile.DoBossTrace && AppVars.BossContacts != null)
            {
                try
                {
                    foreach (var bossContact in AppVars.BossContacts)
                    {
                        var nickLower = bossContact.Key;
                        if (!nicksToCheck.Contains(nickLower))
                            nicksToCheck.Add(nickLower);
                    }
                }
                catch (ApplicationException)
                {
                }
            }

            // 2c: Ручные добавления
            // Android: BossAuto.addTargetNick(nick)
            lock (Lock)
            {
                foreach (var trackedNick in _trackedNicks)
                {
                    if (!nicksToCheck.Contains(trackedNick))
                        nicksToCheck.Add(trackedNick);
                }
            }

            if (nicksToCheck.Count == 0)
                return;

            // Шаг 3: проверяем каждого через NeverApi.GetAll (getinfo.cgi)
            lock (Lock)
            {
                AppVars.ClanWarsList.Clear();
            }

            foreach (var nickKey in nicksToCheck)
            {
                var userInfo = NeverApi.GetAll(nickKey);
                if (userInfo == null)
                    continue;

                // Условие: Online + в бою (FightLog != "0" и не пустой)
                // Android: userInfo.Online && !TextUtils.isEmpty(userInfo.FightLog) && !"0".equals(userInfo.FightLog)
                if (userInfo.Online && !string.IsNullOrEmpty(userInfo.FightLog) && !userInfo.FightLog.Equals("0", StringComparison.Ordinal))
                {
                    var isClanmate = !string.IsNullOrEmpty(_myClanName) &&
                                    userInfo.ClanName.Equals(_myClanName, StringComparison.OrdinalIgnoreCase);

                    var war = new ClanWarInfo
                    {
                        ClanTag = isClanmate ? _myClanName : (userInfo.ClanName ?? string.Empty),
                        EnemyClanTag = string.Empty,
                        StartTime = DateTime.Now,
                        IsActive = true
                    };

                    lock (Lock)
                    {
                        AppVars.ClanWarsList.Add(war);
                    }

                    // Формат сообщения — аналогично BossContact.CheckLives()
                    var clanPrefix = isClanmate ? @"<font color=""#0B610B"">[Клан]</font> " : string.Empty;
                    var message =
                        $"{clanPrefix}<b>{userInfo.Nick}</b> [{userInfo.Level}] — в бою. Локация: <b>{userInfo.Location}</b>. " +
                        $"<a href='http://www.neverlands.ru/logs.fcg?fid={userInfo.FightLog}' onclick='window.open(this.href);' target=_blank>Лог боя</a>";

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

        /// <summary>
        /// Быстрая проверка: находится ли указанный ник в бою.
        /// Использует NeverApi.GetAll (getinfo.cgi) — единый модуль парсинга.
        /// Аналог Android: BossAuto.IsClanMemberInFight(nick).
        /// 
        /// Зависимости: NeverApi.GetAll(nick)
        ///   - getid.cgi?{encnick} → ID
        ///   - info.cgi?playerid={id}&info=1... → UserInfo.FightLog + Online
        /// 
        /// Возвращает true если Online и FightLog не пустой/не "0".
        /// </summary>
        internal static bool IsClanMemberInFight(string nick)
        {
            if (string.IsNullOrEmpty(nick))
                return false;

            var userInfo = NeverApi.GetAll(nick);
            if (userInfo == null)
                return false;

            return userInfo.Online && !string.IsNullOrEmpty(userInfo.FightLog) && !userInfo.FightLog.Equals("0", StringComparison.Ordinal);
        }

        /// <summary>
        /// Быстрая проверка: является ли ник соклановцем и в бою.
        /// Аналог Android: BossAuto.checkClanAndFight(nick).
        /// 
        /// Зависимости: NeverApi.GetAll(nick), _myClanName
        /// </summary>
        internal static bool IsClanmateInFight(string nick)
        {
            if (string.IsNullOrEmpty(nick) || string.IsNullOrEmpty(_myClanName))
                return false;

            var userInfo = NeverApi.GetAll(nick);
            if (userInfo == null)
                return false;

            if (!userInfo.ClanName.Equals(_myClanName, StringComparison.OrdinalIgnoreCase))
                return false;

            return userInfo.Online && !string.IsNullOrEmpty(userInfo.FightLog) && !userInfo.FightLog.Equals("0", StringComparison.Ordinal);
        }
    }
}