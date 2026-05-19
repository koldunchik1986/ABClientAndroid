using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.Text;
using System.Windows.Forms;
using ANClient.AppControls;
using System.Threading;
using System.Xml;
using ANClient.ANForms;

namespace ANClient
{
    public static class ContactsManager
    {
        public static readonly ReaderWriterLock Rwl = new ReaderWriterLock();
        private const int ContactInfoRefreshDelayMs = 500;
        private const int ContactLookupRefreshDelayMs = 1200;
        private static readonly object ContactApiLock = new object();
        private static readonly object ContactRefreshQueueLock = new object();
        private static readonly Queue<ContactRefreshState> ContactRefreshQueue = new Queue<ContactRefreshState>();
        private static readonly Dictionary<string, ContactRefreshState> ContactRefreshPending = new Dictionary<string, ContactRefreshState>();
        private static DateTime _lastContactApiRequestUtc = DateTime.MinValue;
        private static bool _contactRefreshWorkerRunning;

        private sealed class ContactRefreshState
        {
            internal string Key;
            internal bool NotifyChanges;
            internal string Source;
        }

        private sealed class ClanImportState
        {
            internal TreeViewEx Tree;
            internal string Nick;
            internal string Source;
            internal UserInfo SeedUserInfo;
        }

        private sealed class ClanImportApplyResult
        {
            internal bool Added;
            internal bool Updated;
            internal bool Skipped;
            internal string Key;
        }

        public static void Init(TreeViewEx tree)
        {
            if (AppVars.Profile.Contacts.Count == 0)
            {
                AppVars.Profile.Contacts.Add("Мастер Создатель".ToLower(), new Contact("Мастер Создатель", 0, 0, "Платите деньги за солнечный свет...", true, false));
                AppVars.Profile.Contacts.Add("Шандор-Волшебник".ToLower(), new Contact("Шандор-Волшебник", 0, 0, "Массовик-затейник", true, false));
                AppVars.Profile.Contacts.Add("Черный".ToLower(), new Contact("Черный", 2, 0, "Автор клиента ANClient (2007-2016)\r\n\nE-mail: wmlab@hotmail.com\r\n\nSkype: wmlab.home", true, false));
            }

            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    tree.BeginUpdate();
                    tree.Nodes.Clear();

                    foreach (var contact in AppVars.Profile.Contacts)
                        Add(tree, contact.Value);

                    tree.EndUpdate();
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        public static void LoadBossUsers()
        {
            AppLog.d("ContactsManager", "LoadBossUsers");
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var bossUsersPath = Path.Combine(System.Windows.Forms.Application.StartupPath, "bossusers.xml");
                    if (File.Exists(bossUsersPath))
                    {
                        string bossusers;
                        try
                        {
                            bossusers = File.ReadAllText(bossUsersPath, Encoding.UTF8);
                        }
                        catch
                        {
                            return;
                        }

                        AppVars.BossContacts = new SortedList<string, BossContact>();
                        var rawList = new List<BossContact>();
                        var xmlDocument = new XmlDocument();
                        xmlDocument.LoadXml(bossusers);
                        var bossusersNodeList = xmlDocument.GetElementsByTagName("contactentry");
                        AppLog.d("ContactsManager", "LoadBossUsers: " + bossusersNodeList.Count + " entries found");
                        foreach (XmlNode bossUser in bossusersNodeList)
                        {
                            if (bossUser.Attributes == null)
                                continue;

                            var name = bossUser.Attributes["name"].Value;
                            var lastbossupdated = Convert.ToDateTime(bossUser.Attributes["lastbossupdated"].Value, CultureInfo.InvariantCulture);
                            var contact = new BossContact(name, true, lastbossupdated);
                            rawList.Add(contact);
                        }

                        rawList.Sort(SortByLastBossUpdated);
                        var count = Math.Min(rawList.Count, 100);
                        AppLog.d("ContactsManager", "LoadBossUsers: loaded " + count + " boss contacts");
                        for (var i = 0; i < count; i++)
                        {
                            AppVars.BossContacts.Add(rawList[i].Name.ToLower(), rawList[i]);
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        private static int SortByLastBossUpdated(BossContact x, BossContact y)
        {
            return y.LastBossUpdated.CompareTo(x.LastBossUpdated);
        }

        public static void SaveBossUsers()
        {
            AppLog.d("ContactsManager", "SaveBossUsers");
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var wSettings = new XmlWriterSettings { Indent = true };
                    var ms = new MemoryStream();
                    var xmlWriter = XmlWriter.Create(ms, wSettings);
                    xmlWriter.WriteStartDocument();
                    xmlWriter.WriteStartElement("bossusers");

                    foreach (var contact in AppVars.BossContacts)
                    {
                        xmlWriter.WriteStartElement("contactentry");

                        xmlWriter.WriteStartAttribute("name");
                        xmlWriter.WriteString(contact.Value.Name ?? string.Empty);
                        xmlWriter.WriteEndAttribute();

                        xmlWriter.WriteStartAttribute("lastbossupdated");
                        xmlWriter.WriteValue(contact.Value.LastBossUpdated);
                        xmlWriter.WriteEndAttribute();

                        xmlWriter.WriteEndElement();
                    }

                    xmlWriter.WriteEndElement();

                    xmlWriter.WriteEndDocument();
                    xmlWriter.Flush();

                    try
                    {
                        var fileStream = new FileStream("bossusers_new.xml", FileMode.Create);
                        ms.WriteTo(fileStream);
                        fileStream.Close();
                        ms.Close();

                        if (File.Exists("bossusers.xml"))
                            File.Delete("bossusers.xml");

                        File.Move("bossusers_new.xml", "bossusers.xml");
                    }
                    catch (IOException)
                    {
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        public static void AddUsers(string args)
        {
            AppLog.d("ContactsManager", "AddUsers");
            var added = false;
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var users = args.Split(new[] { "],[" }, StringSplitOptions.RemoveEmptyEntries);
                    foreach (var user in users)
                    {
                        var spar = user.Split(',');
                        if (spar.Length < 5)
                            continue;

                        if (spar[0].Equals("1"))
                        {
                            var nick = spar[1].Trim('\"');

                            if (AppVars.BossContacts.ContainsKey(nick.ToLower()))
                            {
                                AppVars.BossContacts[nick.ToLower()].LastBossUpdated =
                                    DateTime.Now.Subtract(AppVars.Profile.ServDiff);
                            }
                            else
                            {
                                var contact = new BossContact(nick, true, DateTime.Now.Subtract(AppVars.Profile.ServDiff));
                                AppVars.BossContacts.Add(contact.Name.ToLower(), contact);
                                AppLog.i("ContactsManager", "AddUsers: boss contact added nick=" + nick);
                                var message = $"Контакт [{nick}] добавлен в слежение";
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

                                added = true;
                            }
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }

            if (added)
                SaveBossUsers();
        }


        public static void Pulse()
        {
            AppLog.d("ContactsManager", "Pulse");
            var nextContactKey = string.Empty;
            var isBossContact = false;
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var nextCheck = DateTime.MaxValue;
                    if (AppVars.Profile.DoContactTrace)
                    {
                        foreach (var contact in AppVars.Profile.Contacts)
                        {
                            if (!contact.Value.Tracing)
                                continue;

                            if (!string.IsNullOrEmpty(nextContactKey) && (contact.Value.NextCheck >= nextCheck))
                                continue;

                            nextContactKey = contact.Key;
                            nextCheck = contact.Value.NextCheck;
                        }
                    }

                    if (AppVars.Profile.DoBossTrace && AppVars.BossContacts != null)
                    {
                        foreach (var contact in AppVars.BossContacts)
                        {
                            if (!string.IsNullOrEmpty(nextContactKey) && contact.Value.NextCheck >= nextCheck)
                                continue;

                            nextContactKey = contact.Key;
                            isBossContact = true;
                            nextCheck = contact.Value.NextCheck;
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }

            if (string.IsNullOrEmpty(nextContactKey))
                return;

            if (!isBossContact)
            {
                AppLog.d("ContactsManager", "Pulse: contact check key=" + nextContactKey);
                if (AppVars.Profile.Contacts.ContainsKey(nextContactKey))
                {
                    AppVars.Profile.Contacts[nextContactKey].NextCheck =
                        AppVars.Profile.Contacts[nextContactKey].NextCheck.AddMinutes(1);

                    QueueContactRefresh(nextContactKey, true, "Pulse");
                }
            }
            else
            {
                AppLog.d("ContactsManager", "Pulse: boss contact check key=" + nextContactKey);
                if (AppVars.BossContacts.ContainsKey(nextContactKey))
                {
                    AppVars.BossContacts[nextContactKey].NextCheck =
                        AppVars.BossContacts[nextContactKey].NextCheck.AddMinutes(1);

                    ThreadPool.QueueUserWorkItem(ProcessBossAsync, nextContactKey);
                }
            }
        }

        private static void ProcessAsync(object state)
        {
            var nextContactKey = (string)state;
            QueueContactRefresh(nextContactKey, true, "ProcessAsync");
        }

        private static void ProcessBossAsync(object state)
        {
            var nextContactKey = (string)state;
            AppLog.d("ContactsManager", "ProcessBossAsync: key=" + nextContactKey);
            BossContact contact;
            if (!AppVars.BossContacts.TryGetValue(nextContactKey, out contact))
                return;

            var nick = contact.Name;
            WaitContactApiTurn("ProcessBossAsync", ContactLookupRefreshDelayMs);
            var userInfo = NeverApi.GetAll(nick);
            if (!AppVars.BossContacts.TryGetValue(nextContactKey, out contact))
                return;

            contact.Process(userInfo);
        }

        private static string GetParentName(Contact contact)
        {
            var admins = new[] { "Мастер Создатель", "Шандор-Волшебник", "Иксуй", "Хатор-Законник", "Хранитель" };

            if (Array.IndexOf(admins, contact.Name) >= 0 || 
                (!string.IsNullOrEmpty(contact.Sign) && (contact.Sign.StartsWith("c279", StringComparison.OrdinalIgnoreCase) || contact.Sign.StartsWith("c280", StringComparison.OrdinalIgnoreCase))))
            {
                return "Администраторы";
            }

            if (!string.IsNullOrEmpty(contact.Sign) && contact.Sign.StartsWith("pv", StringComparison.OrdinalIgnoreCase))
            {
                return "Представители власти";
            }

            return string.IsNullOrEmpty(contact.Clan) ? string.Empty : contact.Clan;
        }

        private static void Add(TreeView tree, Contact contact)
        {
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var tn = MakeTreeNode(contact);
                    var nameGroup = GetParentName(contact);
                    if (string.IsNullOrEmpty(nameGroup))
                    {
                        tree.Nodes.Add(tn);
                    }
                    else
                    {
                        if (!tree.Nodes.ContainsKey(nameGroup))
                        {
                            var tnparent = MakeGroupNode(nameGroup, contact);
                            tree.Nodes.Insert(0, tnparent);
                            tnparent.Nodes.Add(tn);
                            AppVars.Profile.Contacts[contact.Name.ToLower()].Parent = nameGroup;
                            UpdateGroupCounter(tnparent);
                        }
                        else
                        {
                            var tnparent = tree.Nodes[nameGroup];
                            tnparent.Nodes.Add(tn);
                            if (tn.Checked && !tnparent.Checked)
                            {
                                tnparent.Checked = true;
                            }

                            AppVars.Profile.Contacts[contact.Name.ToLower()].Parent = nameGroup;
                            UpdateGroupCounter(tnparent);
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        internal static void Add(TreeViewEx tree, string nick)
        {
            if (string.IsNullOrEmpty(nick))
                return;

            Contact contact = null;
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var key = nick.Trim().ToLower();
                    if (AppVars.Profile.Contacts.ContainsKey(key))
                        return;

                    contact = new Contact(nick, 0, 0, string.Empty, true, false);
                    AppVars.Profile.Contacts.Add(key, contact);
                    Add(tree, contact);
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }

            if (contact == null)
                return;

            AppVars.Profile.Save();
            AppLog.i("ContactsManager", "Add: queued full contact lookup nick=" + contact.Name);
            QueueContactRefresh(contact.Name.ToLower(), false, "Add");
        }

        internal static void ImportClan(TreeViewEx tree, string nick, string source)
        {
            if (tree == null || string.IsNullOrEmpty(nick))
                return;

            var importSource = string.IsNullOrEmpty(source) ? "ContactsManager.ImportClan" : source;
            AppLog.i("ContactsManager", "ImportClan: queued nick=" + nick + " source=" + importSource);
            NotifyImportChat(importSource, "запущено добавление клана по персонажу [" + nick + "]");
            ThreadPool.QueueUserWorkItem(ImportClanAsync, new ClanImportState { Tree = tree, Nick = nick.Trim(), Source = importSource });
        }

        internal static void ImportClan(TreeViewEx tree, Contact contact, string source)
        {
            if (tree == null || contact == null || string.IsNullOrEmpty(contact.Name))
                return;

            var importSource = string.IsNullOrEmpty(source) ? "ContactsManager.ImportClan" : source;
            var seedUserInfo = new UserInfo
            {
                Nick = contact.Name,
                ClanSign = contact.ClanIco,
                ClanIco = contact.ClanIco,
                ClanName = string.IsNullOrEmpty(contact.ClanName) ? contact.Clan : contact.ClanName
            };

            AppLog.i("ContactsManager", "ImportClan: queued contact nick=" + contact.Name + " clan=" + seedUserInfo.ClanName + " source=" + importSource);
            NotifyImportChat(importSource, "запущено добавление клана [" + seedUserInfo.ClanName + "] по контакту [" + contact.Name + "]");
            ThreadPool.QueueUserWorkItem(ImportClanAsync, new ClanImportState { Tree = tree, Nick = contact.Name.Trim(), Source = importSource, SeedUserInfo = seedUserInfo });
        }

        private static void ImportClanAsync(object state)
        {
            var importState = state as ClanImportState;
            if (importState == null || importState.Tree == null || string.IsNullOrEmpty(importState.Nick))
                return;

            if (importState.SeedUserInfo == null)
            {
                WaitContactApiTurn("ImportClan.GetRoster", ContactLookupRefreshDelayMs);
            }

            var roster = importState.SeedUserInfo == null
                ? NeverApi.GetClanRosterByNick(importState.Nick)
                : NeverApi.GetClanRosterByUserInfo(importState.SeedUserInfo);
            if (roster == null || roster.MainUserInfo == null)
            {
                AppLog.w("ContactsManager", "ImportClan: roster not found nick=" + importState.Nick + " source=" + importState.Source);
                NotifyImportChat(importState.Source, "не удалось получить список клана для [" + importState.Nick + "]");
                return;
            }

            if (string.IsNullOrEmpty(roster.ClanName) || ContactRenderHelper.IsNeutralClanName(roster.ClanName))
            {
                NotifyImportChat(importState.Source, "у персонажа [" + roster.MainUserInfo.Nick + "] не найден клан");
                return;
            }

            if (roster.MemberNicks.Length == 0)
            {
                NotifyImportChat(importState.Source, "список клана [" + roster.ClanName + "] пуст");
                return;
            }

            NotifyImportChat(importState.Source, "быстро добавляем всех из '" + roster.ClanName + "': " + roster.MemberNicks.Length.ToString(CultureInfo.InvariantCulture));
            var added = 0;
            var updated = 0;
            var skipped = 0;
            var refreshKeys = new List<string>();

            foreach (var member in roster.Members)
            {
                if (member == null || string.IsNullOrEmpty(member.Nick))
                    continue;

                var userInfo = BuildClanRosterSnapshot(roster, member);
                var applyResult = ApplyImportedContact(importState.Tree, userInfo, member.Nick);
                if (applyResult.Added)
                    added++;
                else if (applyResult.Updated)
                    updated++;
                else if (applyResult.Skipped)
                    skipped++;

                if (!applyResult.Skipped)
                {
                    AddUniqueContactKey(refreshKeys, applyResult.Key);
                }
            }

            AppVars.Profile.Save();
            AppLog.i("ContactsManager", "ImportClan: phase1 finished clan=" + roster.ClanName + " added=" + added.ToString(CultureInfo.InvariantCulture) + " updated=" + updated.ToString(CultureInfo.InvariantCulture) + " skipped=" + skipped.ToString(CultureInfo.InvariantCulture) + " refreshQueued=" + refreshKeys.Count.ToString(CultureInfo.InvariantCulture));
            NotifyImportChat(importState.Source, "быстрое добавление из '" + roster.ClanName + "' завершено: новых " + added.ToString(CultureInfo.InvariantCulture) + ", обновлено " + updated.ToString(CultureInfo.InvariantCulture) + ", пропущено " + skipped.ToString(CultureInfo.InvariantCulture) + "; проверка в очереди " + refreshKeys.Count.ToString(CultureInfo.InvariantCulture));

            if (refreshKeys.Count > 0)
            {
                QueueContactListRefresh(refreshKeys, "ImportClan.PostRefresh clan=" + roster.ClanName);
            }
        }

        private static UserInfo BuildClanRosterSnapshot(NeverApi.ClanRoster roster, NeverApi.ClanRosterMember member)
        {
            var level = member == null ? 0 : member.Level;
            return new UserInfo
            {
                PlayerId = member == null ? string.Empty : member.PlayerId,
                Nick = member == null ? string.Empty : member.Nick,
                Level = level > 0 ? level.ToString(CultureInfo.InvariantCulture) : string.Empty,
                PlayerLevel = level,
                ClanCode = roster == null ? string.Empty : roster.ClanId,
                ClanNumber = roster == null ? string.Empty : roster.ClanId,
                ClanSign = roster == null ? string.Empty : roster.ClanSign,
                ClanIco = roster == null ? string.Empty : roster.ClanSign,
                ClanName = roster == null ? string.Empty : roster.ClanName,
                ClanStatus = member == null ? string.Empty : member.Status
            };
        }

        private static void AddUniqueContactKey(List<string> keys, string key)
        {
            if (keys == null || string.IsNullOrEmpty(key))
                return;

            foreach (var existingKey in keys)
            {
                if (key.Equals(existingKey, StringComparison.OrdinalIgnoreCase))
                    return;
            }

            keys.Add(key);
        }

        private static ClanImportApplyResult ApplyImportedContact(TreeViewEx tree, UserInfo userInfo, string fallbackNick)
        {
            var result = new ClanImportApplyResult();
            if (tree == null || tree.IsDisposed)
            {
                result.Skipped = true;
                return result;
            }

            if (tree.InvokeRequired)
            {
                var wait = new ManualResetEvent(false);
                ClanImportApplyResult invokedResult = null;
                Exception invokeError = null;
                try
                {
                    tree.BeginInvoke((MethodInvoker)delegate
                    {
                        try
                        {
                            invokedResult = ApplyImportedContact(tree, userInfo, fallbackNick);
                        }
                        catch (Exception ex)
                        {
                            invokeError = ex;
                        }
                        finally
                        {
                            wait.Set();
                        }
                    });
                    wait.WaitOne();
                }
                catch (InvalidOperationException)
                {
                    result.Skipped = true;
                    return result;
                }

                if (invokeError != null)
                {
                    AppLog.e("ContactsManager", "ApplyImportedContact: UI invoke failed nick=" + (fallbackNick ?? string.Empty), invokeError);
                    result.Skipped = true;
                    return result;
                }

                return invokedResult ?? result;
            }

            var contactName = userInfo == null || string.IsNullOrEmpty(userInfo.Nick) ? fallbackNick : userInfo.Nick;
            if (string.IsNullOrEmpty(contactName))
            {
                result.Skipped = true;
                return result;
            }

            var key = contactName.Trim().ToLower();
            Contact contact;
            var added = false;
            var updated = false;
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    if (AppVars.Profile.Contacts.TryGetValue(key, out contact))
                    {
                        if (userInfo != null)
                        {
                            contact.ApplyClanRosterSnapshot(userInfo);
                        }

                        updated = true;
                    }
                    else
                    {
                        contact = new Contact(contactName, 0, 0, string.Empty, true, false);
                        if (userInfo != null)
                        {
                            contact.ApplyClanRosterSnapshot(userInfo);
                        }

                        AppVars.Profile.Contacts.Add(contact.Name.ToLower(), contact);
                        added = true;
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
                result.Skipped = true;
                return result;
            }

            if (added)
            {
                Add(tree, contact);
                result.Added = true;
                result.Key = contact.Name.ToLower();
                return result;
            }

            if (updated)
            {
                NormalizeContactKey(key, contact);
                Update(tree, contact);
                result.Updated = true;
                result.Key = contact.Name.ToLower();
                return result;
            }

            result.Skipped = true;
            return result;
        }

        private static void NotifyImportChat(string source, string text)
        {
            if (AppVars.MainForm == null || string.IsNullOrEmpty(text))
                return;

            AppVars.MainForm.WriteChatMsgSafe("[" + source + "] " + text);
        }

        private static TreeNode MakeTreeNode(Contact ce)
        {
            var tn = new TreeNode
            {
                Name = ce.TreeNode,
                Text = ce.BuildTreeText(),
                ContextMenuStrip = AppVars.MainForm.CmPerson,
                Checked = ce.Tracing,
                ForeColor = Color.LightBlue,
                Tag = ce
            };

            tn.ImageKey = tn.SelectedImageKey = PrepareContactSign(ce);
            return tn;
        }

        private static TreeNode MakeGroupNode(string name, Contact ce)
        {
            var tn = new TreeNode
            {
                Name = name,
                Text = name,
                ContextMenuStrip = AppVars.MainForm.CmGroup,
                Checked = ce.Tracing,
                ForeColor = Color.Black,
                Tag = null
            };

            tn.ImageKey = tn.SelectedImageKey = PrepareContactSign(ce);
            return tn;
        }

        internal static int GetClassIdOfContact(string nick)
        {
            if (!AppVars.Profile.Contacts.ContainsKey(nick.ToLower()))
                return -1;

            var classid = AppVars.Profile.Contacts[nick.ToLower()].ClassId;
            return classid;
        }

        internal static int GetToolIdOfContact(string nick)
        {
            if (!AppVars.Profile.Contacts.ContainsKey(nick.ToLower()))
                return 0;

            var toolid = AppVars.Profile.Contacts[nick.ToLower()].ToolId;
            if (toolid < 0)
                return 0;

            if (toolid > 7)
                return 7;

            return toolid;
        }

        internal static int GetLevelOfContact(string nick)
        {
            if (string.IsNullOrEmpty(nick) || !AppVars.Profile.Contacts.ContainsKey(nick.ToLower()))
                return 0;

            return Math.Max(0, AppVars.Profile.Contacts[nick.ToLower()].PlayerLevel);
        }

        internal static int[] GetEffectIdsOfContact(string nick)
        {
            if (string.IsNullOrEmpty(nick) || !AppVars.Profile.Contacts.ContainsKey(nick.ToLower()))
                return new int[0];

            return ContactRenderHelper.ParseEffectIdsCsv(AppVars.Profile.Contacts[nick.ToLower()].EffectIds).ToArray();
        }

        internal static string GetEffectIdsCsvOfContact(string nick)
        {
            if (string.IsNullOrEmpty(nick) || !AppVars.Profile.Contacts.ContainsKey(nick.ToLower()))
                return string.Empty;

            return AppVars.Profile.Contacts[nick.ToLower()].EffectIds ?? string.Empty;
        }

        internal static string GetEffectHtmlOfContact(string nick)
        {
            if (string.IsNullOrEmpty(nick) || !AppVars.Profile.Contacts.ContainsKey(nick.ToLower()))
                return string.Empty;

            var contact = AppVars.Profile.Contacts[nick.ToLower()];
            return ContactRenderHelper.BuildEffectIconsHtml(contact.EffectStates, contact.EffectIds);
        }

        internal static void RefreshContact(Contact contact)
        {
            if (contact == null)
                return;

            QueueContactRefresh(contact.Name.ToLower(), false, "RefreshContact");
        }

        internal static void RefreshAllContacts()
        {
            var keys = new List<string>();
            try
            {
                Rwl.AcquireReaderLock(5000);
                try
                {
                    foreach (var contact in AppVars.Profile.Contacts)
                    {
                        keys.Add(contact.Key);
                    }
                }
                finally
                {
                    Rwl.ReleaseReaderLock();
                }
            }
            catch (ApplicationException)
            {
            }

            QueueContactListRefresh(keys, "all");
        }

        internal static void RefreshGroupContacts(string group)
        {
            if (string.IsNullOrEmpty(group))
                return;

            var keys = new List<string>();
            try
            {
                Rwl.AcquireReaderLock(5000);
                try
                {
                    foreach (var contact in AppVars.Profile.Contacts)
                    {
                        if (group.Equals(contact.Value.Parent, StringComparison.OrdinalIgnoreCase) || group.Equals(GetParentName(contact.Value), StringComparison.OrdinalIgnoreCase))
                        {
                            keys.Add(contact.Key);
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseReaderLock();
                }
            }
            catch (ApplicationException)
            {
            }

            QueueContactListRefresh(keys, "group=" + group);
        }

        internal static void RefreshNeutralContacts()
        {
            var keys = new List<string>();
            try
            {
                Rwl.AcquireReaderLock(5000);
                try
                {
                    foreach (var contact in AppVars.Profile.Contacts)
                    {
                        if (ContactRenderHelper.IsNeutralClanName(contact.Value.ClanName))
                        {
                            keys.Add(contact.Key);
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseReaderLock();
                }
            }
            catch (ApplicationException)
            {
            }

            QueueContactListRefresh(keys, "neutral");
        }

        private static void QueueContactListRefresh(List<string> keys, string source)
        {
            if (keys == null || keys.Count == 0)
            {
                AppLog.i("ContactsManager", "RefreshContacts: skip empty source=" + source);
                return;
            }

            AppLog.i("ContactsManager", "RefreshContacts: queued count=" + keys.Count.ToString(CultureInfo.InvariantCulture) + " source=" + source);
            foreach (var key in keys)
            {
                QueueContactRefresh(key, false, source);
            }
        }

        private static void RefreshContactListAsync(object state)
        {
            var keys = state as string[];
            if (keys == null)
                return;

            foreach (var key in keys)
            {
                RefreshContactByKey(key, false);
            }
        }

        private static void RefreshContactAsync(object state)
        {
            var refreshState = state as ContactRefreshState;
            if (refreshState == null || string.IsNullOrEmpty(refreshState.Key))
                return;

            RefreshContactByKey(refreshState.Key, refreshState.NotifyChanges);
        }

        private static void QueueContactRefresh(string key, bool notifyChanges, string source)
        {
            if (string.IsNullOrEmpty(key))
                return;

            var normalizedKey = key.ToLower();
            var shouldStartWorker = false;
            lock (ContactRefreshQueueLock)
            {
                ContactRefreshState existing;
                if (ContactRefreshPending.TryGetValue(normalizedKey, out existing))
                {
                    existing.NotifyChanges = existing.NotifyChanges || notifyChanges;
                    return;
                }

                var state = new ContactRefreshState
                {
                    Key = normalizedKey,
                    NotifyChanges = notifyChanges,
                    Source = source ?? string.Empty
                };
                ContactRefreshPending.Add(normalizedKey, state);
                ContactRefreshQueue.Enqueue(state);
                if (!_contactRefreshWorkerRunning)
                {
                    _contactRefreshWorkerRunning = true;
                    shouldStartWorker = true;
                }
            }

            if (shouldStartWorker)
            {
                ThreadPool.QueueUserWorkItem(ContactRefreshQueueAsync);
            }
        }

        private static void ContactRefreshQueueAsync(object state)
        {
            while (true)
            {
                ContactRefreshState refreshState;
                lock (ContactRefreshQueueLock)
                {
                    if (ContactRefreshQueue.Count == 0)
                    {
                        _contactRefreshWorkerRunning = false;
                        return;
                    }

                    refreshState = ContactRefreshQueue.Dequeue();
                    ContactRefreshPending.Remove(refreshState.Key);
                }

                try
                {
                    AppLog.d("ContactsManager", "ContactRefreshWorker: key=" + refreshState.Key + " notify=" + refreshState.NotifyChanges.ToString(CultureInfo.InvariantCulture) + " source=" + refreshState.Source);
                    RefreshContactByKey(refreshState.Key, refreshState.NotifyChanges);
                }
                catch (Exception ex)
                {
                    AppLog.w("ContactsManager", "ContactRefreshWorker: refresh failed key=" + refreshState.Key + " source=" + refreshState.Source, ex);
                }
            }
        }

        private static void RefreshContactByKey(string key, bool notifyChanges)
        {
            Contact contact;
            if (string.IsNullOrEmpty(key) || !AppVars.Profile.Contacts.TryGetValue(key, out contact))
                return;

            var hasPlayerId = !string.IsNullOrEmpty(contact.PlayerId);
            WaitContactApiTurn("RefreshContactByKey", hasPlayerId ? ContactInfoRefreshDelayMs : ContactLookupRefreshDelayMs);
            AppLog.d("ContactsManager", "RefreshContactByKey: route=" + (hasPlayerId ? "info.cgi" : "getid+info.cgi") + " key=" + key + " playerId=" + contact.PlayerId);
            var userInfo = hasPlayerId
                ? NeverApi.GetAllByPlayerId(contact.PlayerId)
                : NeverApi.GetAll(contact.Name);

            if (userInfo == null)
            {
                AppLog.w("ContactsManager", "RefreshContactByKey: EMPTY_USER_INFO key=" + key + " playerId=" + contact.PlayerId);
                return;
            }

            if (!AppVars.Profile.Contacts.TryGetValue(key, out contact))
                return;

            if (notifyChanges)
            {
                contact.Process(userInfo);
            }
            else
            {
                contact.ApplySnapshot(userInfo);
                NotifyContactUpdated(contact);
            }

            NormalizeContactKey(key, contact);
            AppVars.Profile.Save();
        }

        private static void NormalizeContactKey(string oldKey, Contact contact)
        {
            if (contact == null || string.IsNullOrEmpty(contact.Name))
                return;

            var newKey = contact.Name.ToLower();
            if (newKey.Equals(oldKey, StringComparison.Ordinal))
                return;

            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    if (!AppVars.Profile.Contacts.ContainsKey(oldKey) || AppVars.Profile.Contacts.ContainsKey(newKey))
                        return;

                    AppVars.Profile.Contacts.Remove(oldKey);
                    AppVars.Profile.Contacts.Add(newKey, contact);
                    AppLog.i("ContactsManager", "NormalizeContactKey: " + oldKey + " -> " + newKey);
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        private static void NotifyContactUpdated(Contact contact)
        {
            if (contact == null || AppVars.MainForm == null)
                return;

            try
            {
                AppVars.MainForm.BeginInvoke(new UpdateContactDelegate(AppVars.MainForm.UpdateContact), contact);
            }
            catch (InvalidOperationException)
            {
            }
        }

        private static void WaitContactApiTurn(string source, int delayMs)
        {
            lock (ContactApiLock)
            {
                var now = DateTime.UtcNow;
                var elapsedMs = (now - _lastContactApiRequestUtc).TotalMilliseconds;
                if (_lastContactApiRequestUtc != DateTime.MinValue && elapsedMs < delayMs)
                {
                    var sleepMs = delayMs - (int)elapsedMs;
                    if (sleepMs > 0)
                    {
                        AppLog.d("ContactsManager", "WaitContactApiTurn: source=" + source + " sleepMs=" + sleepMs.ToString(CultureInfo.InvariantCulture));
                        Thread.Sleep(sleepMs);
                    }
                }

                _lastContactApiRequestUtc = DateTime.UtcNow;
            }
        }

        internal static Color GetColorOfContact(Contact contact)
        {
            switch (contact.ClassId)
            {
                case 0:
                    return contact.IsOnline ? Color.Black : Color.LightGray;
                case 1:
                    return contact.IsOnline ? Color.DarkRed : Color.LightPink;
                case 2:
                    return contact.IsOnline ? Color.DarkGreen : Color.MediumAquamarine;
                default:
                    return Color.Black;
            }
        }

        private static Color GetColorOfGroup(int classid, bool isOnline)
        {
            switch (classid)
            {
                case 0:
                    return isOnline ? Color.Black : Color.LightGray;
                case 1:
                    return isOnline ? Color.DarkRed : Color.LightPink;
                case 2:
                    return isOnline ? Color.DarkGreen : Color.MediumAquamarine;
                default:
                    return Color.Black;
            }
        }

        internal static void Update(TreeViewEx tree, Contact contact)
        {
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    if (AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                    {
                        if (!AppVars.Profile.Contacts[contact.Name.ToLower()].Tracing)
                        {
                            return;
                        }
                    }

                    var tn = FindNode(tree, contact);
                    if (tn == null)
                    {
                        return;
                    }

                    var treeText = contact.BuildTreeText();
                    if (!string.Equals(tn.Text, treeText, StringComparison.Ordinal))
                    {
                        tn.Text = treeText;
                    }

                    var contactColor = GetColorOfContact(contact);
                    if (tn.ForeColor != contactColor)
                    {
                        tn.ForeColor = contactColor;
                    }

                    var imageKey = PrepareContactSign(contact);
                    if (!string.Equals(tn.ImageKey, imageKey, StringComparison.Ordinal) ||
                        !string.Equals(tn.SelectedImageKey, imageKey, StringComparison.Ordinal))
                    {
                        tn.ImageKey = imageKey;
                        tn.SelectedImageKey = imageKey;
                    }

                    if (!string.Equals(tn.ToolTipText, contact.Location, StringComparison.Ordinal))
                    {
                        tn.ToolTipText = contact.Location;
                    }

                    tn.Tag = contact;

                    var nameGroup = GetParentName(contact) ?? string.Empty;
                    var currentParent = tn.Parent == null ? string.Empty : tn.Parent.Name;
                    var storedParent = contact.Parent ?? string.Empty;
                    var needsMove = !nameGroup.Equals(currentParent, StringComparison.OrdinalIgnoreCase);
                    var needsParentSync = !nameGroup.Equals(storedParent, StringComparison.OrdinalIgnoreCase);
                    var treeUpdateStarted = false;
                    try
                    {
                        if (needsMove)
                        {
                            tree.BeginUpdate();
                            treeUpdateStarted = true;

                            var wasSelected = tree.SelectedNode == tn;
                            var oldParent = tn.Parent;
                            if (oldParent == null)
                            {
                                tree.Nodes.Remove(tn);
                            }
                            else
                            {
                                oldParent.Nodes.Remove(tn);
                                UpdateGroupCounter(oldParent);
                            }

                            if (string.IsNullOrEmpty(nameGroup))
                            {
                                tree.Nodes.Add(tn);
                            }
                            else
                            {
                                TreeNode tnparent;
                                if (!tree.Nodes.ContainsKey(nameGroup))
                                {
                                    tnparent = MakeGroupNode(nameGroup, contact);
                                    tree.Nodes.Insert(0, tnparent);
                                }
                                else
                                {
                                    tnparent = tree.Nodes[nameGroup];
                                }

                                tnparent.Nodes.Add(tn);
                            }

                            if (wasSelected)
                            {
                                tree.SelectedNode = tn;
                            }
                        }

                        if (needsMove || needsParentSync)
                        {
                            contact.Parent = nameGroup;
                            if (AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                                AppVars.Profile.Contacts[contact.Name.ToLower()].Parent = nameGroup;
                        }

                        if (!string.IsNullOrEmpty(nameGroup) && tree.Nodes.ContainsKey(nameGroup))
                        {
                            var tnparent = tree.Nodes[nameGroup];
                            UpdateGroupCounter(tnparent);
                        }
                    }
                    finally
                    {
                        if (treeUpdateStarted)
                        {
                            tree.EndUpdate();
                        }
                    }

                    tree.InvalidateNodeRow(tn);
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        private static void UpdateGroupCounter(TreeNode tngroup)
        {
            var countTracing = 0;
            var countOnline = 0;
            var countClass = new int[3];
            foreach (TreeNode node in tngroup.Nodes)
            {
                var contact = (Contact)node.Tag;
                if (contact == null)
                {
                    continue;
                }

                if ((contact.ClassId >= 0) && (contact.ClassId <= 2))
                {
                    countClass[contact.ClassId]++;
                }

                if (contact.Tracing)
                {
                    countTracing++;
                    if (contact.IsOnline)
                    {
                        countOnline++;
                    }
                }
            }

            tngroup.Text = countTracing == 0 ? tngroup.Name : string.Format("{0} ({1}/{2})", tngroup.Name, countOnline, countTracing);
            if (!tngroup.Checked)
            {
                tngroup.ForeColor = Color.LightBlue;
            }
            else
            {
                if ((countClass[0] > 0) && (countClass[1] == 0) && (countClass[2] == 0))
                    tngroup.ForeColor = GetColorOfGroup(0, countOnline > 0);
                else
                {
                    if ((countClass[0] == 0) && (countClass[1] > 0) && (countClass[2] == 0))
                        tngroup.ForeColor = GetColorOfGroup(1, countOnline > 0);
                    else
                    {
                        if ((countClass[0] == 0) && (countClass[1] == 0) && (countClass[2] > 0))
                            tngroup.ForeColor = GetColorOfGroup(2, countOnline > 0);
                        else
                        {
                            tngroup.ForeColor = countOnline > 0 ? Color.Black : Color.LightGray;
                        }
                    }
                }
            }
        }

        internal static void UpdateComments(Contact contact, string comment)
        {
            if (AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
            {
                AppVars.Profile.Contacts[contact.Name.ToLower()].Comments = comment;
            }
        }

        internal static void Remove(TreeViewEx tree, TreeNode tn)
        {
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    var contact = (Contact)tn.Tag;
                    tn.Remove();
                    if (!string.IsNullOrEmpty(contact.Parent) && tree.Nodes.ContainsKey(contact.Parent))
                    {
                        var tnparent = tree.Nodes[contact.Parent];
                        UpdateGroupCounter(tnparent);
                    }

                    if (AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                    {
                        AppVars.Profile.Contacts.Remove(contact.Name.ToLower());
                    }

                    AppVars.Profile.Save();
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        internal static void RemoveGroup(TreeViewEx tree, string group)
        {
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    if (!tree.Nodes.ContainsKey(group))
                        return;

                    var tngroup = tree.Nodes[group];
                    tree.BeginUpdate();
                    for (int i = tngroup.Nodes.Count - 1; i >= 0; i--)
                    {
                        var tn = tngroup.Nodes[i];
                        var contact = (Contact)tn.Tag;
                        tn.Remove();

                        if (AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                        {
                            AppVars.Profile.Contacts.Remove(contact.Name.ToLower());
                        }
                    }

                    tngroup.Remove();
                    tree.EndUpdate();

                    AppVars.Profile.Save();
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        internal static void AfterCheck(TreeView tree, TreeNode tn)
        {
            try
            {
                Rwl.AcquireWriterLock(5000);
                try
                {
                    if (tn.Tag == null)
                    {
                        foreach (TreeNode node in tn.Nodes)
                        {
                            node.Checked = tn.Checked;
                        }
                    }
                    else
                    {
                        var contact = (Contact)tn.Tag;
                        if (AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                        {
                            AppVars.Profile.Contacts[contact.Name.ToLower()].Tracing = tn.Checked;
                        }

                        if (!string.IsNullOrEmpty(contact.Parent) && tree.Nodes.ContainsKey(contact.Parent))
                        {
                            var tnparent = tree.Nodes[contact.Parent];
                            UpdateGroupCounter(tnparent);
                        }

                        if (!tn.Checked)
                        {
                            tn.ForeColor = Color.LightBlue;
                        }
                    }
                }
                finally
                {
                    Rwl.ReleaseWriterLock();
                }
            }
            catch (ApplicationException)
            {
            }
        }

        private static string PrepareContactSign(Contact contact)
        {
            if (contact.Wounds[3] > 0)
            {
                return "injury0";
            }

            if (contact.Wounds[2] > 0)
            {
                return "injury1";
            }

            if (contact.Wounds[1] > 0)
            {
                return "injury4";
            }

            if (contact.Wounds[0] > 0)
            {
                return "injury4";
            }

            if (contact.IsMolch)
            {
                return "molch";
            }

            if (string.IsNullOrEmpty(contact.Sign))
            {
                return "neutral";
            }

            if (string.CompareOrdinal(contact.Sign, "none") == 0)
            {
                if (string.IsNullOrEmpty(contact.Align))
                {
                    return "neutral";
                }

                var ali1 = string.Empty;
                switch (contact.Align)
                {
                    case "1":
                        ali1 = "darks.gif";
                        break;
                    case "2":
                        ali1 = "lights.gif";
                        break;
                    case "3":
                        ali1 = "sumers.gif";
                        break;
                    case "4":
                        ali1 = "chaoss.gif";
                        break;
                    case "5":
                        ali1 = "light.gif";
                        break;
                    case "6":
                        ali1 = "dark.gif";
                        break;
                    case "7":
                        ali1 = "sumer.gif";
                        break;
                    case "8":
                        ali1 = "chaos.gif";
                        break;
                    case "9":
                        ali1 = "angel.gif";
                        break;
                }

                if (string.IsNullOrEmpty(ali1))
                {
                    return "none";
                }

                if (AppVars.MainForm.ImageListContacts.Images.ContainsKey(ali1))
                {
                    return ali1;
                }

                var pathali1 = Path.Combine(Application.StartupPath, @"ancache\image.neverlands.ru\signs\" + ali1);
                if (!File.Exists(pathali1))
                {
                    return "neutral";
                }

                try
                {
                    AppVars.MainForm.ImageListContacts.Images.Add(ali1, Image.FromFile(pathali1));
                }
                catch
                {
                    return "neutral";
                }

                return ali1;
            }

            if (AppVars.MainForm.ImageListContacts.Images.ContainsKey(contact.Sign))
            {
                return contact.Sign;
            }

            var path = Path.Combine(Application.StartupPath, @"ancache\image.neverlands.ru\signs\" + contact.Sign);
            if (!File.Exists(path))
            {
                return "neutral";
            }

            try
            {
                AppVars.MainForm.ImageListContacts.Images.Add(contact.Sign, Image.FromFile(path));
            }
            catch
            {
                return "neutral";
            }

            return contact.Sign;
        }

        private static TreeNode FindNode(TreeView tree, Contact contact)
        {
            if (tree.Nodes.ContainsKey(contact.TreeNode))
            {
                return tree.Nodes[contact.TreeNode];
            }

            if (tree.Nodes.ContainsKey(contact.Parent))
            {
                var tnparent = tree.Nodes[contact.Parent];
                if (tnparent.Nodes.ContainsKey(contact.TreeNode))
                {
                    return tnparent.Nodes[contact.TreeNode];
                }
            }

            return null;
        }
    }
}
