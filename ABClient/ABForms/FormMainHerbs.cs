namespace ABClient.ABForms
{
    using System;
    using System.Net;
    using System.Globalization;
    using System.Web;
    using System.Text;
    using System.IO;
    using System.Collections.Generic;
    using Helpers;
    using MyChat;
    using System.Threading;

    internal sealed partial class FormMain
    {
       // private static int _herbsCounter;

        internal static void HerbsList(string list)
        {
            if (list == null || AppVars.Profile == null)
            {   
                return;
            }

            var catalogChanged = false;
            var herbList = new StringBuilder();
            var treeList = new StringBuilder();
            catalogChanged = AutoCutCatalog.EnsureProfileCatalog(AppVars.Profile);
            var entries = list.Split('|');
            for (var i = 0; i < entries.Length; i++)
            {
                if (string.IsNullOrEmpty(entries[i]))
                {
                    continue;
                }

                var parts = entries[i].Split(':');
                if (parts.Length == 0 || string.IsNullOrEmpty(parts[0]))
                {
                    continue;
                }

                var name = parts[0];
                var count = parts.Length > 1 ? parts[1] : "1";
                var rType = parts.Length > 2 ? parts[2] : string.Empty;
                if (AutoCutRuntime.IsTreeCandidate(name, rType))
                {
                    catalogChanged |= AutoCutCatalog.RegisterObservedTree(
                        AppVars.Profile,
                        string.Empty,
                        name,
                        0,
                        AppVars.Profile.MapLocation ?? string.Empty);
                    treeList.Append(name).Append(':').Append(count).Append('|');
                }
                else
                {
                    catalogChanged |= AutoCutCatalog.RegisterObservedHerb(
                        AppVars.Profile,
                        string.Empty,
                        name,
                        0,
                        AppVars.Profile.MapLocation ?? string.Empty);
                    herbList.Append(name).Append(':').Append(count).Append('|');
                }
            }

            UpdateResourceCellCache(AppVars.Profile.HerbCells, herbList.ToString());
            UpdateResourceCellCache(AppVars.Profile.TreeCells, treeList.ToString());

            if (catalogChanged)
            {
                AppLog.i("auto_cut_trace", "FormMainHerbs", "catalog updated from HerbsList, location=" + AppVars.Profile.MapLocation);
                AppVars.Profile.Save();
            }
        }

        private static void UpdateResourceCellCache(SortedDictionary<string, HerbCell> cells, string list)
        {
            if (cells == null || AppVars.Profile == null || string.IsNullOrEmpty(AppVars.Profile.MapLocation))
            {
                return;
            }

            var safeList = list ?? string.Empty;
            var updatedInTicks = DateTime.Now.Subtract(AppVars.Profile.ServDiff).Ticks;
            if (cells.ContainsKey(AppVars.Profile.MapLocation))
            {
                cells[AppVars.Profile.MapLocation].Herbs = safeList;
                cells[AppVars.Profile.MapLocation].UpdatedInTicks = updatedInTicks;
            }
            else
            {
                var herbcell = new HerbCell
                                    {
                                        RegNum = AppVars.Profile.MapLocation,
                                        Herbs = safeList,
                                        UpdatedInTicks = updatedInTicks
                                    };
                cells.Add(AppVars.Profile.MapLocation, herbcell);
            }
        }

        internal static bool IsHerbAutoCut(string herb)
        {
            return IsResourceAutoCut(AutoCutMode.Herb, herb, string.Empty);
        }

        internal static bool IsResourceAutoCut(AutoCutMode mode, string herb, string rType)
        {
            if (herb == null)
            {
                return false;
            }

            if (!AutoCutRuntime.IsAutoCutLikeEnabled() || AppVars.Profile == null || AutoCutRuntime.GetActiveMode() != mode || !AutoCutRuntime.IsResourceCandidateForMode(mode, herb, rType))
            {
                return false;
            }

            AutoCutCatalog.EnsureProfileCatalog(AppVars.Profile);
            var catalog = mode == AutoCutMode.Tree ? AppVars.Profile.AutoCutTrees : AppVars.Profile.AutoCutHerbs;
            var selected = mode == AutoCutMode.Tree ? AppVars.Profile.TreesAutoCut : AppVars.Profile.HerbsAutoCut;
            var catalogHerb = AutoCutCatalog.Find(catalog, string.Empty, herb);
            if (catalogHerb != null)
            {
                return catalogHerb.Selected;
            }

            for (var i = 0; i < selected.Count; i++)
            {
                if (selected[i].Equals(herb, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        internal static void HerbCut(string name)
        {
            if (AppVars.Profile.DoAutoCutWriteChat)
            {
                var message = string.Format(@"{0}: Автоспил травы ""{1}""...", AppVars.AppVersion.ProductShortVersion, name);
                Chat.AddAnswer(message);
            }

            var colormessage = string.Format("Автоспил травы &laquo;<b>{0}</b>&raquo;...", name);
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        new object[] { colormessage });
                }
            }
            catch (InvalidOperationException)
            {
            }

            TraceCut(AutoCutMode.Herb, name);
        }

        internal static bool DoHerbAutoCut()
        {
            var mode = AutoCutRuntime.GetActiveMode();
            var selectedCount = AppVars.Profile == null ? 0 : (mode == AutoCutMode.Tree ? AppVars.Profile.TreesAutoCut.Count : AppVars.Profile.HerbsAutoCut.Count);
            if (!AutoCutRuntime.IsAutoCutLikeEnabled() ||
                AppVars.Profile == null ||
                selectedCount == 0 ||
                AppVars.AutoMoving ||
                DateTime.Now <= AppVars.NeverTimer)
            {
                return false;
            }

            if (AutoCutRuntime.IsAlchemyActionPending())
            {
                AppLog.d("auto_cut_trace", "FormMainHerbs", "auto look skip: alchemy action pending");
                return false;
            }

            if (AutoCutRuntime.ShouldAutoLookOnCurrentCell())
            {
                return true;
            }

            AutoCutRuntime.RouteNextIfCurrentCellCachedNotReady("do_herb_auto_cut");
            return false;
        }

        internal static void TraceCut(string herb)
        {
            TraceCut(AutoCutRuntime.GetActiveMode(), herb);
        }

        internal static void TraceCut(AutoCutMode mode, string herb)
        {
            if (mode == AutoCutMode.Tree)
            {
                TraceTreeCut(herb);
                return;
            }

            TraceCutHerb(herb);
        }

        private static void TraceCutHerb(string herb)
        {
            var colormessage = string.Format("Трава &laquo;<b>{0}</b>&raquo; спилена. ", herb);
            var curTime = DateTime.Now.Subtract(AppVars.Profile.ServDiff);
            var curShift = GetShift(curTime);
            var h = 1;
            switch (herb)
            {
                case "Инжир":
                case "Кипарис":
                case "Брусника":
                case "Смертоцвет":
                case "Лимон":
                case "Дурман":
                case "Камелия":
                case "Ландыш":
                case "Рапонтикум":
                case "Береза":
                case "Дуб":
                case "Алоэ":
                case "Гравилат":
                case "Прагениана":
                case "Айва":
                case "Дягиль":
                case "Каперс":
                case "Секуринега":
                case "Кентарийская дикая роза":
                case "Кора дуба":
                    h = 2;
                    break;
            }

            var minutes = (h * 60) - 2;
            var nextTime = curTime.AddMinutes(minutes);
            var nextShift = GetShift(nextTime);
            if (curShift != nextShift)
            {
                colormessage += "Таймер не установлен, смена трав близка.";
            }
            else
            {
                minutes += 30;
                var appTimer = new AppTimer
                                   {
                                       Description =
                                           string.Format("Вырастет {0} на {1}", herb, AppVars.Profile.MapLocation),
                                       TriggerTime = DateTime.Now.AddMinutes(minutes),
                                       IsHerb = true
                                   };
                AppTimerManager.AddAppTimer(appTimer);
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateTimersDelegate(AppVars.MainForm.UpdateTimers),
                            new object[] {});
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppVars.Profile.Save();
                colormessage += h == 1 ? "Таймер установлен на <b>1</b> час" : "Таймер установлен на <b>2</b> часа.";
            }

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        new object[] { colormessage });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static void TraceTreeCut(string tree)
        {
            var colormessage = string.Format("Дерево &laquo;<b>{0}</b>&raquo; спилено. ", tree);
            var curTime = DateTime.Now.Subtract(AppVars.Profile.ServDiff);
            var curShift = GetShift(curTime);
            AutoCutCatalog.EnsureProfileCatalog(AppVars.Profile);
            var catalogTree = AutoCutCatalog.Find(AppVars.Profile.AutoCutTrees, string.Empty, tree);
            var growthMinutes = catalogTree == null || catalogTree.GrowthMinutes <= 0 ? 30 : catalogTree.GrowthMinutes;
            var minutes = Math.Max(1, growthMinutes - 2);
            var nextTime = curTime.AddMinutes(minutes);
            var nextShift = GetShift(nextTime);
            if (curShift != nextShift)
            {
                colormessage += "Таймер не установлен, смена ресурсов близка.";
            }
            else
            {
                minutes += 30;
                var appTimer = new AppTimer
                                   {
                                       Description = string.Format("Вырастет {0} на {1}", tree, AppVars.Profile.MapLocation),
                                       TriggerTime = DateTime.Now.AddMinutes(minutes),
                                       IsHerb = true,
                                       IsAutoLumberjack = true,
                                       Destination = AppVars.Profile.MapLocation
                                   };
                AppTimerManager.AddAppTimer(appTimer);
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateTimersDelegate(AppVars.MainForm.UpdateTimers),
                            new object[] { });
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppVars.Profile.Save();
                colormessage += string.Format(CultureInfo.InvariantCulture, "Таймер установлен на <b>{0}</b> мин.", growthMinutes);
            }

            if (!AppVars.Profile.DoAutoLumberjackWriteChat)
            {
                return;
            }

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        new object[] { colormessage });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static int GetShift(DateTime dateTime)
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

            if ((dateTime >= d3) && (dateTime < d4))
            {
                return 3;
            }

            return 0;
        }

        internal static void TraceCutID(string herbid)
        {
            string herb = herbid;
            var colormessage = string.Format("Трава &laquo;<b>{0}</b>&raquo; спилена. ", herb);
            var curTime = DateTime.Now.Subtract(AppVars.Profile.ServDiff);
            var curShift = GetShift(curTime);
            var h = 1;
            switch (herb)
            {
                case "Инжир":
                case "Кипарис":
                case "Брусника":
                case "Смертоцвет":
                case "Лимон":
                case "Дурман":
                case "Камелия":
                case "Ландыш":
                case "Рапонтикум":
                case "Береза":
                case "Дуб":
                case "Алоэ":
                case "Гравилат":
                case "Прагениана":
                case "Айва":
                case "Дягиль":
                case "Каперс":
                case "Секуринега":
                case "Кентарийская дикая роза":
                    h = 2;
                    break;
            }

            var minutes = (h * 60) - 2;
            var nextTime = curTime.AddMinutes(minutes);
            var nextShift = GetShift(nextTime);
            if (curShift != nextShift)
            {
                colormessage += "Таймер не установлен, смена трав близка.";
            }
            else
            {
                minutes += 30;
                var appTimer = new AppTimer
                                   {
                                       Description =
                                           string.Format("Вырастет {0} на {1}", herb, AppVars.Profile.MapLocation),
                                       TriggerTime = DateTime.Now.AddMinutes(minutes),
                                       IsHerb = true
                                   };
                AppTimerManager.AddAppTimer(appTimer);
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateTimersDelegate(AppVars.MainForm.UpdateTimers),
                            new object[] {});
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppVars.Profile.Save();
                colormessage += h == 1 ? "Таймер установлен на <b>1</b> час." : "Таймер установлен на <b>2</b> часа.";
            }

            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                        new object[] { colormessage });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }
    }
}
