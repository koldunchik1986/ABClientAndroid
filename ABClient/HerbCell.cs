using System;
using System.Collections.Generic;
using System.Globalization;
using ABClient.MyProfile;

namespace ABClient
{
    internal class HerbCell
    {
        internal string RegNum;
        internal string Herbs;
        internal long UpdatedInTicks;
    }

    internal sealed class AutoCutHerbInfo
    {
        internal string Id;
        internal string Name;
        internal int Skill;
        internal int GrowthMinutes;
        internal string Group;
        internal string LastLocation;
        internal bool Selected;
    }

    internal static class AutoCutCatalog
    {
        internal const string UnknownGroup = "Не определено";
        internal const string DefaultShiftSchedule = "00:50-06:50\r\n06:50-12:50\r\n12:50-18:50\r\n18:50-00:50";

        private static readonly SeedHerb[] SeedHerbs =
            {
                new SeedHerb("86", "Моховик", 0, 60, "5"),
                new SeedHerb("67", "Кассия", 0, 60, "4"),
                new SeedHerb("75", "Аралия", 0, 60, "6"),
                new SeedHerb("114", "Лимон", 5, 120, "7"),
                new SeedHerb("96", "Осот", 5, 60, "2"),
                new SeedHerb("444", "Водоросли приозерные", 5, 60, "2"),
                new SeedHerb("451", "Сахарный тростник", 5, 60, "11"),
                new SeedHerb("102", "Пшеница", 5, 60, "11"),
                new SeedHerb("77", "Гравилат", 10, 120, "2"),
                new SeedHerb("84", "Хвоя", 10, 60, "5"),
                new SeedHerb("440", "Перец Форпостной", 10, 60, "11"),
                new SeedHerb("447", "Боровик", 10, 60, "9"),
                new SeedHerb("448", "Лисички", 10, 60, "9"),
                new SeedHerb("47", "Каланхоэ", 20, 60, "3"),
                new SeedHerb("83", "Сосна", 20, 60, "9"),
                new SeedHerb("439", "Перец Октальский", 20, 60, "11"),
                new SeedHerb("450", "Томат", 20, 60, "11"),
                new SeedHerb("443", "Картофель", 20, 60, "11"),
                new SeedHerb("438", "Сельдерей", 20, 60, "11"),
                new SeedHerb("437", "Петрушка Кровавобережная", 20, 60, "11"),
                new SeedHerb("87", "Бадан", 30, 60, "2"),
                new SeedHerb("442", "Чеснок", 30, 60, "11"),
                new SeedHerb("441", "Укроп болотный", 30, 60, "11"),
                new SeedHerb("103", "Тарвин", 40, 60, "4"),
                new SeedHerb("108", "Змеиный корень", 50, 60, "1"),
                new SeedHerb("118", "Виноград светлый", 50, 60, "8"),
                new SeedHerb("119", "Виноград темный", 50, 60, "8"),
                new SeedHerb("91", "Трифоль", 60, 60, "2"),
                new SeedHerb("115", "Бегония", 75, 60, "6"),
                new SeedHerb("58", "Алтей", 90, 60, "6"),
                new SeedHerb("76", "Бессмертник", 105, 60, "6"),
                new SeedHerb("98", "Катарантус", 105, 60, "1"),
                new SeedHerb("104", "Жизненное дерево", 110, 60, "7"),
                new SeedHerb("60", "Астрагал", 110, 60, "4"),
                new SeedHerb("80", "Ведьмино кольцо", 120, 60, "9"),
                new SeedHerb("88", "Болотник", 120, 60, "2"),
                new SeedHerb("94", "Анис", 130, 60, "7"),
                new SeedHerb("90", "Маклея", 130, 60, "2"),
                new SeedHerb("48", "Каперс", 140, 120, "7"),
                new SeedHerb("56", "Подберезовик", 140, 60, "5"),
                new SeedHerb("112", "Кентарийская дикая роза", 150, 120, "3"),
                new SeedHerb("63", "Девясил", 150, 60, "4"),
                new SeedHerb("89", "Брусника", 160, 120, "2"),
                new SeedHerb("66", "Истод", 160, 60, "3"),
                new SeedHerb("110", "Прагениана", 170, 120, "3"),
                new SeedHerb("92", "Сыроежка", 170, 60, "5"),
                new SeedHerb("107", "Ландыш", 180, 120, "4"),
                new SeedHerb("49", "Кориандр", 180, 60, "7"),
                new SeedHerb("106", "Люминисцентная поганка", 190, 60, "9"),
                new SeedHerb("113", "Антуриум хрустальный", 190, 60, "2"),
                new SeedHerb("74", "Алоэ", 200, 120, "7"),
                new SeedHerb("79", "Термопсис", 20, 60, "6"),
                new SeedHerb("117", "Смертоцвет", 210, 120, "3"),
                new SeedHerb("65", "Карагана", 210, 60, "9"),
                new SeedHerb("52", "Береза", 220, 120, "5"),
                new SeedHerb("68", "Кипрей", 220, 60, "5"),
                new SeedHerb("57", "Поганка", 230, 60, "5"),
                new SeedHerb("93", "Мухомор", 230, 60, "9"),
                new SeedHerb("50", "Крестовник", 240, 60, "11"),
                new SeedHerb("101", "Парибигус", 240, 60, "6"),
                new SeedHerb("46", "Айва", 250, 120, "7"),
                new SeedHerb("111", "Камелия", 250, 120, "3"),
                new SeedHerb("69", "Лен", 250, 60, "11"),
                new SeedHerb("82", "Дягиль", 300, 120, "9"),
                new SeedHerb("99", "Коризиус", 300, 60, "5"),
                new SeedHerb("53", "Дуб", 350, 120, "5"),
                new SeedHerb("61", "Вереск", 350, 60, "4"),
                new SeedHerb("51", "Секуринега", 400, 120, "3"),
                new SeedHerb("97", "Фенхель", 400, 60, "1"),
                new SeedHerb("81", "Амми", 400, 60, "7"),
                new SeedHerb("105", "Секвойя", 400, 60, "4"),
                new SeedHerb("73", "Эфедра", 550, 60, "4"),
                new SeedHerb("116", "Кипарис", 600, 120, "9"),
                new SeedHerb("54", "Ива", 600, 60, "5"),
                new SeedHerb("55", "Лиственница", 600, 60, "9"),
                new SeedHerb("64", "Дурман", 640, 120, "2"),
                new SeedHerb("109", "Пустынный агапантус", 640, 60, "1"),
                new SeedHerb("59", "Арника", 680, 60, "3"),
                new SeedHerb("72", "Чернокорень", 680, 60, "4"),
                new SeedHerb("71", "Рапонтикум", 700, 120, "3"),
                new SeedHerb("95", "Инжир", 720, 120, "1"),
                new SeedHerb("100", "Куфис", 720, 60, "6"),
                new SeedHerb("78", "Родиола", 720, 60, "6"),
                new SeedHerb("62", "Галега", 780, 60, "3"),
                new SeedHerb("85", "Подосиновик", 780, 60, "5"),
                new SeedHerb("70", "Плаун", 800, 60, "2"),
                new SeedHerb("", "Подснежник", 0, 60, "Квестовые"),
                new SeedHerb("", "Кора дуба", 350, 120, UnknownGroup)
            };

        internal static bool EnsureProfileCatalog(UserConfig profile)
        {
            if (profile == null)
            {
                return false;
            }

            var changed = false;
            for (var i = 0; i < SeedHerbs.Length; i++)
            {
                changed |= MergeSeedHerb(profile.AutoCutHerbs, SeedHerbs[i]);
            }

            for (var i = 0; i < profile.HerbsAutoCut.Count; i++)
            {
                var name = profile.HerbsAutoCut[i];
                if (string.IsNullOrEmpty(name))
                {
                    continue;
                }

                var herb = Find(profile.AutoCutHerbs, string.Empty, name);
                if (herb == null)
                {
                    profile.AutoCutHerbs.Add(new AutoCutHerbInfo
                                                {
                                                    Id = string.Empty,
                                                    Name = name.Trim(),
                                                    Skill = 0,
                                                    GrowthMinutes = 60,
                                                    Group = UnknownGroup,
                                                    LastLocation = string.Empty,
                                                    Selected = true
                                                });
                    changed = true;
                }
                else if (!herb.Selected)
                {
                    herb.Selected = true;
                    changed = true;
                }
            }

            return changed;
        }

        internal static bool RegisterObservedHerb(UserConfig profile, string id, string name, int growthMinutes, string location)
        {
            if (profile == null || string.IsNullOrEmpty(name))
            {
                return false;
            }

            EnsureProfileCatalog(profile);
            var safeId = SafeNumeric(id);
            var safeName = name.Trim();
            var seed = FindSeed(safeId, safeName);
            var herb = Find(profile.AutoCutHerbs, safeId, safeName);
            var changed = false;
            if (herb == null)
            {
                herb = new AutoCutHerbInfo
                           {
                               Id = safeId,
                               Name = safeName,
                               Skill = seed == null ? 0 : seed.Skill,
                               GrowthMinutes = growthMinutes > 0 ? growthMinutes : (seed == null ? 60 : seed.GrowthMinutes),
                               Group = seed == null ? UnknownGroup : seed.Group,
                               LastLocation = location ?? string.Empty,
                               Selected = false
                           };
                profile.AutoCutHerbs.Add(herb);
                return true;
            }

            if (string.IsNullOrEmpty(herb.Id) && !string.IsNullOrEmpty(safeId))
            {
                herb.Id = safeId;
                changed = true;
            }

            if (!string.Equals(herb.Name, safeName, StringComparison.Ordinal) && !string.IsNullOrEmpty(safeName))
            {
                herb.Name = safeName;
                changed = true;
            }

            if (growthMinutes > 0 && herb.GrowthMinutes != growthMinutes)
            {
                herb.GrowthMinutes = growthMinutes;
                changed = true;
            }

            if (seed != null && (string.IsNullOrEmpty(herb.Group) || herb.Group.Equals(UnknownGroup, StringComparison.OrdinalIgnoreCase)))
            {
                herb.Group = seed.Group;
                changed = true;
            }

            var safeLocation = location ?? string.Empty;
            if (!string.Equals(herb.LastLocation ?? string.Empty, safeLocation, StringComparison.OrdinalIgnoreCase))
            {
                herb.LastLocation = safeLocation;
                changed = true;
            }

            return changed;
        }

        internal static AutoCutHerbInfo Find(List<AutoCutHerbInfo> herbs, string id, string name)
        {
            var safeId = SafeNumeric(id);
            if (!string.IsNullOrEmpty(safeId))
            {
                for (var i = 0; i < herbs.Count; i++)
                {
                    if (string.Equals(SafeNumeric(herbs[i].Id), safeId, StringComparison.Ordinal))
                    {
                        return herbs[i];
                    }
                }
            }

            var normalizedName = NormalizeName(name);
            if (string.IsNullOrEmpty(normalizedName))
            {
                return null;
            }

            for (var i = 0; i < herbs.Count; i++)
            {
                if (NormalizeName(herbs[i].Name).Equals(normalizedName, StringComparison.Ordinal))
                {
                    return herbs[i];
                }
            }

            return null;
        }

        internal static string NormalizeGroupHeader(string group)
        {
            var safe = string.IsNullOrEmpty(group) ? UnknownGroup : group.Trim();
            return IsDigitsOnly(safe) ? "Группа " + safe : safe;
        }

        internal static string SafeNumeric(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return string.Empty;
            }

            var result = new System.Text.StringBuilder();
            for (var i = 0; i < value.Length; i++)
            {
                if (char.IsDigit(value[i]))
                {
                    result.Append(value[i]);
                }
            }

            return result.ToString();
        }

        private static bool MergeSeedHerb(List<AutoCutHerbInfo> herbs, SeedHerb seed)
        {
            var existing = Find(herbs, seed.Id, seed.Name);
            if (existing == null)
            {
                herbs.Add(new AutoCutHerbInfo
                              {
                                  Id = seed.Id,
                                  Name = seed.Name,
                                  Skill = seed.Skill,
                                  GrowthMinutes = seed.GrowthMinutes,
                                  Group = seed.Group,
                                  LastLocation = string.Empty,
                                  Selected = false
                              });
                return true;
            }

            var changed = false;
            if (string.IsNullOrEmpty(existing.Id) && !string.IsNullOrEmpty(seed.Id))
            {
                existing.Id = seed.Id;
                changed = true;
            }

            if (existing.Skill == 0 && seed.Skill > 0)
            {
                existing.Skill = seed.Skill;
                changed = true;
            }

            if (existing.GrowthMinutes <= 0)
            {
                existing.GrowthMinutes = seed.GrowthMinutes;
                changed = true;
            }

            if (string.IsNullOrEmpty(existing.Group) || existing.Group.Equals(UnknownGroup, StringComparison.OrdinalIgnoreCase))
            {
                existing.Group = seed.Group;
                changed = true;
            }

            return changed;
        }

        private static SeedHerb FindSeed(string id, string name)
        {
            var safeId = SafeNumeric(id);
            for (var i = 0; i < SeedHerbs.Length; i++)
            {
                if (!string.IsNullOrEmpty(safeId) && SeedHerbs[i].Id == safeId)
                {
                    return SeedHerbs[i];
                }
            }

            var normalizedName = NormalizeName(name);
            for (var i = 0; i < SeedHerbs.Length; i++)
            {
                if (NormalizeName(SeedHerbs[i].Name).Equals(normalizedName, StringComparison.Ordinal))
                {
                    return SeedHerbs[i];
                }
            }

            return null;
        }

        private static string NormalizeName(string value)
        {
            return (value ?? string.Empty).Trim().Replace('ё', 'е').Replace('Ё', 'Е').ToLowerInvariant();
        }

        private static bool IsDigitsOnly(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return false;
            }

            for (var i = 0; i < value.Length; i++)
            {
                if (!char.IsDigit(value[i]))
                {
                    return false;
                }
            }

            return true;
        }

        private sealed class SeedHerb
        {
            internal readonly string Id;
            internal readonly string Name;
            internal readonly int Skill;
            internal readonly int GrowthMinutes;
            internal readonly string Group;

            internal SeedHerb(string id, string name, int skill, int growthMinutes, string group)
            {
                Id = id;
                Name = name;
                Skill = skill;
                GrowthMinutes = growthMinutes;
                Group = group;
            }
        }
    }

    internal static class AutoCutRuntime
    {
        internal const string GarbageItemName = "Бесполезный хлам";

        private const double CleanupFallbackThresholdMass = 10d;
        private static readonly List<string> CheckedCells = new List<string>();
        private static string checkedShiftKey = string.Empty;
        private static DateTime lookRetryAt = DateTime.MinValue;
        private static string lookRetrySource = string.Empty;
        private static bool massSnapshotSyncPending;
        private static DateTime lastMassSnapshotSyncRequestAt = DateTime.MinValue;
        private static string timerRouteReturnCell = string.Empty;
        private static string timerRouteTargetCell = string.Empty;
        private static bool timerRouteReturning;

        internal static void ResetRuntime(string source)
        {
            CheckedCells.Clear();
            checkedShiftKey = BuildShiftKey(DateTime.Now.Subtract(AppVars.Profile.ServDiff));
            lookRetryAt = DateTime.MinValue;
            lookRetrySource = string.Empty;
            massSnapshotSyncPending = false;
            lastMassSnapshotSyncRequestAt = DateTime.MinValue;
            AppVars.AutoCutCleanupPending = false;
            AppVars.AutoCutCleanupReason = string.Empty;
            AppVars.AutoCutHarvestedMassSinceCleanup = 0d;
            AppVars.AutoCutKnownMassMax = 0d;
            ClearTimerRouteState("runtime_reset:" + source);
            AppLog.d("auto_cut_trace", "AutoCutRuntime", "runtime reset: source=" + source);
        }

        internal static bool ShouldAutoLookOnCurrentCell()
        {
            if (AppVars.AutoMoving || AppVars.AutoCutCheckSickle || AppVars.AutoCutCleanupPending || AppVars.Profile == null)
            {
                return false;
            }

            if (AppVars.Profile.HerbsAutoCut.Count == 0)
            {
                return false;
            }

            var cells = GetSearchCells();
            var current = CurrentCell();
            if (RouteNextAfterTimerReturnIfArrived(current))
            {
                return false;
            }

            if (cells.Count > 0 && (string.IsNullOrEmpty(current) || !ContainsCell(cells, current)))
            {
                RouteNextCell("not_on_csv_cell");
                return false;
            }

            if (HasDueHerbTimerForCell(current))
            {
                return true;
            }

            return !IsCellCheckedForCurrentShift(current);
        }

        internal static void OnScanWithoutSelectedHerb(string source)
        {
            MarkCurrentCellChecked("no_selected:" + source);
            if (RouteBackToTimerReturnIfNeeded("no_selected:" + source))
            {
                return;
            }

            RouteNextCell("no_selected:" + source);
        }

        internal static void OnCutSuccess(bool retrySameCell, string source, double resourceMass)
        {
            UpdateInventoryMassAfterCut(resourceMass);
            if (!AppVars.AutoCutCleanupPending)
            {
                MaybeRequestCleanupAfterCut(resourceMass, source);
            }

            if (retrySameCell)
            {
                ScheduleLookRetry("multi_cut:" + source);
                return;
            }

            MarkCurrentCellChecked("cut_success:" + source);
            if (AppVars.AutoCutCleanupPending)
            {
                AppLog.i("auto_cut_trace", "AutoCutRuntime", "cut success waits cleanup: source=" + source);
                return;
            }

            if (RouteBackToTimerReturnIfNeeded("cut_success:" + source))
            {
                return;
            }

            RouteNextCell("cut_success:" + source);
        }

        internal static void RequestGarbageCleanupAfterCut(string source)
        {
            StartCleanup("garbage:" + source, true);
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "garbage cleanup requested: thing=" + GarbageItemName + ", source=" + source);
        }

        internal static bool IsMassSnapshotSyncPending()
        {
            return massSnapshotSyncPending;
        }

        internal static bool HasUsableMassSnapshot()
        {
            return ParseMassSnapshot(AppVars.AutoFishMassa).Max > 0d;
        }

        internal static bool NeedsMassSnapshotBeforeCut()
        {
            if (HasUsableMassSnapshot() || massSnapshotSyncPending)
            {
                return false;
            }

            return DateTime.Now.Subtract(lastMassSnapshotSyncRequestAt).TotalSeconds >= 30d;
        }

        internal static void RequestMassSnapshotBeforeCut(string source)
        {
            massSnapshotSyncPending = true;
            lastMassSnapshotSyncRequestAt = DateTime.Now;
            AppVars.AutoCutCheckSickle = true;
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "mass snapshot requested before cut, source=" + source);
            RequestMainPhpReload();
        }

        internal static void ClearMassSnapshotSyncPending(string source)
        {
            if (!massSnapshotSyncPending)
            {
                return;
            }

            massSnapshotSyncPending = false;
            AppLog.d("auto_cut_trace", "AutoCutRuntime", "mass snapshot sync cleared: source=" + source + ", mass=" + (AppVars.AutoFishMassa ?? string.Empty));
        }

        internal static void UpdateMassSnapshot(string mass)
        {
            var snapshot = ParseMassSnapshot(mass);
            if (snapshot.Max <= 0d)
            {
                return;
            }

            AppVars.AutoCutKnownMassMax = snapshot.Max;
            ClearMassSnapshotSyncPending("mass_updated");
            AppLog.d("auto_cut_trace", "AutoCutRuntime", "mass snapshot updated: current=" + snapshot.Current.ToString("0.##", CultureInfo.InvariantCulture) + ", max=" + snapshot.Max.ToString("0.##", CultureInfo.InvariantCulture));
        }

        internal static void UpdateMassSnapshotFromHtml(string html)
        {
            if (string.IsNullOrEmpty(html))
            {
                return;
            }

            var marker = "Масса Вашего инвентаря: ";
            var pos = html.IndexOf(marker, StringComparison.OrdinalIgnoreCase);
            if (pos == -1)
            {
                return;
            }

            pos += marker.Length;
            var end = html.IndexOf("</b>", pos, StringComparison.OrdinalIgnoreCase);
            if (end == -1)
            {
                end = html.IndexOf('<', pos);
            }

            if (end == -1 || end <= pos)
            {
                return;
            }

            var mass = html.Substring(pos, end - pos).Replace("&nbsp;", string.Empty).Replace(" ", string.Empty).Trim();
            if (mass.Length == 0)
            {
                return;
            }

            AppVars.AutoFishMassa = mass;
            UpdateMassSnapshot(mass);
        }

        internal static void OnCleanupCompleted(string source)
        {
            AppVars.AutoCutCleanupPending = false;
            AppVars.AutoCutCleanupReason = string.Empty;
            AppVars.AutoCutHarvestedMassSinceCleanup = 0d;
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "cleanup completed, source=" + source);
            if (lookRetryAt != DateTime.MinValue)
            {
                AppLog.i("auto_cut_trace", "AutoCutRuntime", "cleanup completed: keep current cell for pending retry, source=" + source);
                return;
            }

            if (RouteBackToTimerReturnIfNeeded("cleanup_completed:" + source))
            {
                return;
            }

            RouteNextCell("cleanup_completed:" + source);
        }

        internal static void RouteNextCellIfCurrentIsNotReady(string source)
        {
            if (ShouldDelayRouteForPreparation())
            {
                AppLog.d("auto_cut_trace", "AutoCutRuntime", "route skip: preparation pending, source=" + source);
                return;
            }

            if (ShouldAutoLookOnCurrentCell())
            {
                AppLog.d("auto_cut_trace", "AutoCutRuntime", "route skip: current cell ready, source=" + source);
                return;
            }

            RouteNextCell(source);
        }

        private static bool ShouldDelayRouteForPreparation()
        {
            if (AppVars.AutoCutCleanupPending || massSnapshotSyncPending || IsAlchemyActionPending())
            {
                return true;
            }

            if (!AppVars.AutoCutCheckSickle)
            {
                return false;
            }

            var cells = GetSearchCells();
            var current = CurrentCell();
            if (cells.Count == 0 || string.IsNullOrEmpty(current))
            {
                return true;
            }

            return ContainsCell(cells, current) && !IsCellCheckedForCurrentShift(current);
        }

        private static bool IsAlchemyActionPending()
        {
            return !string.IsNullOrEmpty(AppVars.FightLink) &&
                   AppVars.FightLink.IndexOf("alchemy_ajax.php", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        internal static void ScheduleLookRetry(string source)
        {
            var retry = DateTime.Now.AddMilliseconds(1500);
            if (AppVars.NeverTimer > retry)
            {
                retry = AppVars.NeverTimer;
            }

            lookRetryAt = retry;
            lookRetrySource = source ?? string.Empty;
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "look retry scheduled: source=" + lookRetrySource + ", at=" + lookRetryAt.ToString("HH:mm:ss"));
        }

        internal static bool ConsumeLookRetryIfDue()
        {
            if (lookRetryAt == DateTime.MinValue || DateTime.Now < lookRetryAt)
            {
                return false;
            }

            AppLog.i("auto_cut_trace", "AutoCutRuntime", "look retry consumed: source=" + lookRetrySource);
            lookRetryAt = DateTime.MinValue;
            lookRetrySource = string.Empty;
            return true;
        }

        private static void RouteNextCell(string source)
        {
            var cells = GetSearchCells();
            if (cells.Count == 0 || AppVars.MainForm == null || AppVars.AutoMoving)
            {
                return;
            }

            var current = CurrentCell();
            RefreshCheckedShift();
            PruneStaleHerbTimersForCurrentShift("route_next:" + source);
            var dueTimerCell = FindDueHerbTimerForRoute(cells, current);
            var next = string.IsNullOrEmpty(dueTimerCell) ? FindNextUncheckedCell(cells, current) : dueTimerCell;
            var reason = string.IsNullOrEmpty(dueTimerCell) ? "unchecked" : "herb_timer";
            if (string.IsNullOrEmpty(next))
            {
                CheckedCells.Clear();
                next = FindNextRoundRobin(cells, current);
                reason = "new_circle";
            }

            if (string.IsNullOrEmpty(next))
            {
                return;
            }

            if (next.Equals(current, StringComparison.OrdinalIgnoreCase))
            {
                ScheduleLookRetry("route_current:" + source);
                return;
            }

            RememberTimerRouteReturnIfNeeded(dueTimerCell, current, source);
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "route next: destination=" + next + ", reason=" + reason + ", source=" + source);
            AppVars.MainForm.MoveToSafe(next);
        }

        private static void RememberTimerRouteReturnIfNeeded(string dueTimerCell, string current, string source)
        {
            if (string.IsNullOrEmpty(dueTimerCell) || string.IsNullOrEmpty(current) || dueTimerCell.Equals(current, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }

            timerRouteReturnCell = current;
            timerRouteTargetCell = dueTimerCell;
            timerRouteReturning = false;
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "timer-route detour remembered: from=" + current + ", target=" + dueTimerCell + ", source=" + source);
        }

        private static bool RouteBackToTimerReturnIfNeeded(string source)
        {
            var current = CurrentCell();
            if (string.IsNullOrEmpty(current) || string.IsNullOrEmpty(timerRouteTargetCell) || string.IsNullOrEmpty(timerRouteReturnCell) ||
                !timerRouteTargetCell.Equals(current, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            if (AppVars.AutoMoving && timerRouteReturnCell.Equals(AppVars.AutoMovingDestinaton, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            var returnCell = timerRouteReturnCell;
            timerRouteTargetCell = string.Empty;
            timerRouteReturning = true;
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "timer-route return: destination=" + returnCell + ", from=" + current + ", source=" + source);
            if (AppVars.MainForm != null)
            {
                AppVars.MainForm.MoveToSafe(returnCell);
                return true;
            }

            ClearTimerRouteState("timer_return_failed:" + source);
            return false;
        }

        private static bool RouteNextAfterTimerReturnIfArrived(string current)
        {
            if (!timerRouteReturning || string.IsNullOrEmpty(timerRouteReturnCell) || string.IsNullOrEmpty(current) ||
                !timerRouteReturnCell.Equals(current, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            ClearTimerRouteState("timer_return_arrived");
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "timer-route returned to source cell, continue CSV route: cell=" + current);
            RouteNextCell("timer_return_arrived");
            return true;
        }

        private static void ClearTimerRouteState(string source)
        {
            if (string.IsNullOrEmpty(timerRouteReturnCell) && string.IsNullOrEmpty(timerRouteTargetCell) && !timerRouteReturning)
            {
                return;
            }

            AppLog.d("auto_cut_trace", "AutoCutRuntime", "timer-route state cleared: source=" + source + ", returnCell=" + timerRouteReturnCell + ", targetCell=" + timerRouteTargetCell + ", returning=" + timerRouteReturning);
            timerRouteReturnCell = string.Empty;
            timerRouteTargetCell = string.Empty;
            timerRouteReturning = false;
        }

        private static List<string> GetSearchCells()
        {
            var result = new List<string>();
            if (AppVars.Profile == null || string.IsNullOrEmpty(AppVars.Profile.AutoCutSearchCellsCsv))
            {
                return result;
            }

            var parts = AppVars.Profile.AutoCutSearchCellsCsv.Split(',', ';', '|', '\r', '\n', ' ', '\t');
            for (var i = 0; i < parts.Length; i++)
            {
                var cell = (parts[i] ?? string.Empty).Trim();
                if (cell.Length == 0 || !IsValidCell(cell) || ContainsCell(result, cell))
                {
                    continue;
                }

                result.Add(cell);
            }

            return result;
        }

        private static bool IsValidCell(string cell)
        {
            var dash = cell.IndexOf('-');
            if (dash <= 0 || dash >= cell.Length - 1)
            {
                return false;
            }

            for (var i = 0; i < cell.Length; i++)
            {
                if (i == dash)
                {
                    continue;
                }

                if (!char.IsDigit(cell[i]))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool ContainsCell(List<string> cells, string cell)
        {
            for (var i = 0; i < cells.Count; i++)
            {
                if (cells[i].Equals(cell, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        private static string FindNextUncheckedCell(List<string> cells, string current)
        {
            var startIndex = IndexOfCell(cells, current);
            for (var offset = 1; offset <= cells.Count; offset++)
            {
                var index = startIndex >= 0 ? (startIndex + offset) % cells.Count : offset - 1;
                var cell = cells[index];
                if (!IsCellCheckedForCurrentShift(cell))
                {
                    return cell;
                }
            }

            return string.Empty;
        }

        private static string FindNextRoundRobin(List<string> cells, string current)
        {
            if (cells.Count == 0)
            {
                return string.Empty;
            }

            var startIndex = IndexOfCell(cells, current);
            var index = startIndex >= 0 ? (startIndex + 1) % cells.Count : 0;
            return cells[index];
        }

        private static int IndexOfCell(List<string> cells, string cell)
        {
            if (string.IsNullOrEmpty(cell))
            {
                return -1;
            }

            for (var i = 0; i < cells.Count; i++)
            {
                if (cells[i].Equals(cell, StringComparison.OrdinalIgnoreCase))
                {
                    return i;
                }
            }

            return -1;
        }

        private static bool IsCellCheckedForCurrentShift(string cell)
        {
            if (string.IsNullOrEmpty(cell))
            {
                return false;
            }

            RefreshCheckedShift();
            return ContainsCell(CheckedCells, cell);
        }

        private static void MarkCurrentCellChecked(string source)
        {
            var current = CurrentCell();
            if (string.IsNullOrEmpty(current))
            {
                return;
            }

            RefreshCheckedShift();
            if (!ContainsCell(CheckedCells, current))
            {
                CheckedCells.Add(current);
            }

            ClearDueHerbTimersForCurrentCell(source);
            AppLog.d("auto_cut_trace", "AutoCutRuntime", "cell checked: " + current + ", source=" + source);
        }

        private static void RefreshCheckedShift()
        {
            if (AppVars.Profile == null)
            {
                return;
            }

            var currentShift = BuildShiftKey(DateTime.Now.Subtract(AppVars.Profile.ServDiff));
            if (checkedShiftKey.Equals(currentShift, StringComparison.Ordinal))
            {
                return;
            }

            checkedShiftKey = currentShift;
            CheckedCells.Clear();
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "checked cells cleared for shift=" + checkedShiftKey);
        }

        private static string BuildShiftKey(DateTime serverTime)
        {
            return serverTime.ToString("yyyyMMdd") + ":" + GetShift(serverTime);
        }

        private static int GetShift(DateTime dateTime)
        {
            int configuredShift;
            if (TryGetConfiguredShift(dateTime, out configuredShift))
            {
                return configuredShift;
            }

            return GetDefaultShift(dateTime);
        }

        private static int GetDefaultShift(DateTime dateTime)
        {
            var d1 = new DateTime(dateTime.Year, dateTime.Month, dateTime.Day, 0, 50, 0);
            var d2 = new DateTime(dateTime.Year, dateTime.Month, dateTime.Day, 6, 50, 0);
            var d3 = new DateTime(dateTime.Year, dateTime.Month, dateTime.Day, 12, 50, 0);
            var d4 = new DateTime(dateTime.Year, dateTime.Month, dateTime.Day, 18, 50, 0);
            if ((dateTime < d1) || (dateTime >= d4))
            {
                return 4;
            }

            if ((dateTime >= d1) && (dateTime < d2))
            {
                return 1;
            }

            if ((dateTime >= d2) && (dateTime < d3))
            {
                return 2;
            }

            return (dateTime >= d3) && (dateTime < d4) ? 3 : 0;
        }

        private static bool TryGetConfiguredShift(DateTime dateTime, out int shift)
        {
            shift = 0;
            if (AppVars.Profile == null || string.IsNullOrEmpty(AppVars.Profile.AutoCutShiftSchedule))
            {
                return false;
            }

            var lines = AppVars.Profile.AutoCutShiftSchedule.Split('\r', '\n');
            for (var i = 0; i < lines.Length; i++)
            {
                var line = (lines[i] ?? string.Empty).Trim();
                if (line.Length == 0)
                {
                    continue;
                }

                var dash = line.IndexOf('-');
                if (dash <= 0 || dash >= line.Length - 1)
                {
                    continue;
                }

                TimeSpan start;
                TimeSpan end;
                if (!TryParseShiftTime(line.Substring(0, dash).Trim(), out start) ||
                    !TryParseShiftTime(line.Substring(dash + 1).Trim(), out end))
                {
                    continue;
                }

                var current = dateTime.TimeOfDay;
                var contains = start <= end ? current >= start && current < end : current >= start || current < end;
                if (contains)
                {
                    shift = shift + 1;
                    return true;
                }

                shift++;
            }

            shift = 0;
            return false;
        }

        private static bool TryParseShiftTime(string value, out TimeSpan result)
        {
            result = TimeSpan.Zero;
            if (string.IsNullOrEmpty(value))
            {
                return false;
            }

            var parts = value.Split(':');
            if (parts.Length != 2)
            {
                return false;
            }

            int hour;
            int minute;
            if (!int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out hour) ||
                !int.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out minute) ||
                hour < 0 || hour > 23 || minute < 0 || minute > 59)
            {
                return false;
            }

            result = new TimeSpan(hour, minute, 0);
            return true;
        }

        private static bool HasDueHerbTimerForCell(string cell)
        {
            if (AppVars.Profile == null || !AppVars.Profile.AutoCutByTimers || string.IsNullOrEmpty(cell))
            {
                return false;
            }

            var timers = AppTimerManager.GetTimers();
            for (var i = 0; i < timers.Length; i++)
            {
                if (IsDueHerbTimerForCell(timers[i], cell))
                {
                    return true;
                }
            }

            return false;
        }

        internal static void ClearDueHerbTimersForCurrentCell(string source)
        {
            var current = CurrentCell();
            if (string.IsNullOrEmpty(current))
            {
                return;
            }

            var timers = AppTimerManager.GetTimers();
            var removed = 0;
            for (var i = timers.Length - 1; i >= 0; i--)
            {
                if (IsDueHerbTimerForCell(timers[i], current))
                {
                    AppTimerManager.RemoveTimerAt(i);
                    removed++;
                }
            }

            if (removed > 0)
            {
                AppLog.i("auto_cut_trace", "AutoCutRuntime", "due herb timers cleared: cell=" + current + ", removed=" + removed + ", source=" + source);
            }
        }

        private static string FindDueHerbTimerForRoute(List<string> cells, string current)
        {
            if (AppVars.Profile == null || !AppVars.Profile.AutoCutByTimers || cells == null || cells.Count == 0)
            {
                return string.Empty;
            }

            var timers = AppTimerManager.GetTimers();
            if (timers.Length == 0)
            {
                return string.Empty;
            }

            var startIndex = IndexOfCell(cells, current);
            for (var offset = 0; offset < cells.Count; offset++)
            {
                var index = startIndex >= 0 ? (startIndex + offset) % cells.Count : offset;
                var cell = cells[index];
                for (var timerIndex = 0; timerIndex < timers.Length; timerIndex++)
                {
                    if (IsDueHerbTimerForCell(timers[timerIndex], cell))
                    {
                        return cell;
                    }
                }
            }

            return string.Empty;
        }

        private static bool IsDueHerbTimerForCell(AppTimer timer, string cell)
        {
            if (timer == null || !timer.IsHerb || string.IsNullOrEmpty(cell))
            {
                return false;
            }

            return timer.TriggerTime <= DateTime.Now &&
                   ExtractTimerCell(timer).Equals(cell, StringComparison.OrdinalIgnoreCase) &&
                   IsHerbTimerInCurrentShift(timer);
        }

        private static void PruneStaleHerbTimersForCurrentShift(string source)
        {
            if (AppVars.Profile == null || !AppVars.Profile.AutoCutByTimers)
            {
                return;
            }

            var timers = AppTimerManager.GetTimers();
            var removed = 0;
            for (var i = timers.Length - 1; i >= 0; i--)
            {
                if (timers[i] == null || !timers[i].IsHerb || string.IsNullOrEmpty(ExtractTimerCell(timers[i])))
                {
                    continue;
                }

                if (!IsHerbTimerInCurrentShift(timers[i]))
                {
                    AppTimerManager.RemoveTimerAt(i);
                    removed++;
                }
            }

            if (removed > 0)
            {
                AppLog.i("auto_cut_trace", "AutoCutRuntime", "stale herb timers removed for shift: removed=" + removed + ", source=" + source);
            }
        }

        private static bool IsHerbTimerInCurrentShift(AppTimer timer)
        {
            if (timer == null || AppVars.Profile == null)
            {
                return false;
            }

            var currentServerTime = DateTime.Now.Subtract(AppVars.Profile.ServDiff);
            var timerServerTime = timer.TriggerTime.Subtract(AppVars.Profile.ServDiff);
            return GetShift(currentServerTime) == GetShift(timerServerTime);
        }

        private static string ExtractTimerCell(AppTimer timer)
        {
            if (timer == null)
            {
                return string.Empty;
            }

            if (IsValidCell(timer.Destination))
            {
                return timer.Destination;
            }

            var description = timer.Description ?? string.Empty;
            var marker = " на ";
            var pos = description.LastIndexOf(marker, StringComparison.OrdinalIgnoreCase);
            if (pos == -1)
            {
                return string.Empty;
            }

            var candidate = description.Substring(pos + marker.Length).Trim().Trim('.', ';', ',');
            return IsValidCell(candidate) ? candidate : string.Empty;
        }

        private static bool MaybeRequestCleanupAfterCut(double resourceMass, string source)
        {
            if (AppVars.Profile == null || !AppVars.Profile.AutoCutCleanupEnabled || resourceMass <= 0d)
            {
                return false;
            }

            AppVars.AutoCutHarvestedMassSinceCleanup += resourceMass;
            var maxMass = AppVars.AutoCutKnownMassMax;
            var threshold = maxMass > 0d ? maxMass * 0.10d : CleanupFallbackThresholdMass;
            if (AppVars.AutoCutHarvestedMassSinceCleanup <= threshold)
            {
                AppLog.d("auto_cut_trace", "AutoCutRuntime", "cleanup threshold not reached: delta=" + FormatDouble(AppVars.AutoCutHarvestedMassSinceCleanup) + ", threshold=" + FormatDouble(threshold));
                return false;
            }

            StartCleanup("mass_delta:" + source, false);
            AppLog.i("auto_cut_trace", "AutoCutRuntime", "cleanup requested: delta=" + FormatDouble(AppVars.AutoCutHarvestedMassSinceCleanup) + ", threshold=" + FormatDouble(threshold));
            return true;
        }

        private static void StartCleanup(string reason, bool dropGarbage)
        {
            if (dropGarbage)
            {
                AppVars.BulkDropThing = GarbageItemName;
                AppVars.BulkDropPrice = string.Empty;
            }

            AppVars.AutoCutCleanupPending = true;
            AppVars.AutoCutCleanupReason = reason ?? string.Empty;
            RequestMainPhpReload();
        }

        private static void RequestMainPhpReload()
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new ABForms.ReloadMainPhpInvokeDelegate(AppVars.MainForm.ReloadMainPhpInvoke),
                        new object[] { });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static string UpdateInventoryMassAfterCut(double resourceMass)
        {
            if (resourceMass <= 0d || string.IsNullOrEmpty(AppVars.AutoFishMassa) || AppVars.AutoFishMassa.IndexOf('/') == -1)
            {
                return AppVars.AutoFishMassa ?? string.Empty;
            }

            var split = AppVars.AutoFishMassa.Split(new[] { '/' }, 2);
            if (split.Length < 2)
            {
                return AppVars.AutoFishMassa;
            }

            var current = ParseDouble(split[0]);
            var next = Math.Max(0d, current + resourceMass);
            AppVars.AutoFishMassa = FormatDouble(next) + "/" + split[1].Trim();
            return AppVars.AutoFishMassa;
        }

        private static MassSnapshot ParseMassSnapshot(string mass)
        {
            var result = new MassSnapshot();
            if (string.IsNullOrEmpty(mass) || mass.IndexOf('/') == -1)
            {
                return result;
            }

            var split = mass.Split(new[] { '/' }, 2);
            result.Current = split.Length > 0 ? ParseDouble(split[0]) : 0d;
            result.Max = split.Length > 1 ? ParseDouble(split[1]) : 0d;
            return result;
        }

        private static double ParseDouble(string value)
        {
            double result;
            return double.TryParse((value ?? string.Empty).Trim().Replace(',', '.'), NumberStyles.Any, CultureInfo.InvariantCulture, out result) ? result : 0d;
        }

        private static string FormatDouble(double value)
        {
            return value.ToString("0.##", CultureInfo.InvariantCulture);
        }

        private struct MassSnapshot
        {
            internal double Current;
            internal double Max;
        }

        private static string CurrentCell()
        {
            return AppVars.Profile == null ? string.Empty : (AppVars.Profile.MapLocation ?? string.Empty).Trim();
        }
    }
}
