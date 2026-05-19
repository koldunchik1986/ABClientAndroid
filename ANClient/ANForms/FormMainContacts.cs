namespace ANClient.ANForms
{
    using System.Windows.Forms;
    using MyForms;
    using Properties;
    using Tabs;
    using System;
  
    internal sealed partial class FormMain
    {
        private ToolStripButton tsRefreshContact;
        private ToolStripButton tsRefreshAllContacts;
        private ToolStripMenuItem cmtsToolId6;
        private ToolStripMenuItem cmtsToolId7;
        private ToolStripMenuItem miSetGroupToolId6;
        private ToolStripMenuItem miSetGroupToolId7;
        private ToolStripMenuItem cmtsImportClanContacts;
        private ToolStripMenuItem miImportGroupClanContacts;
        private ToolStripMenuItem cmtsRefreshContact;
        private ToolStripMenuItem miRefreshGroupContacts;
        private ToolStripMenuItem miRefreshNeutralContacts;
        private ToolStripMenuItem miRefreshAllContacts;

        private void InitializeContactsExtensions()
        {
            cmtsToolId6 = new ToolStripMenuItem("Яд", null, CmtsToolId6Click) { Name = "cmtsToolId6" };
            cmtsToolId7 = new ToolStripMenuItem("Сильная спина", null, CmtsToolId7Click) { Name = "cmtsToolId7" };
            var personToolIndex = CmPerson.Items.IndexOf(cmtsToolId5);
            if (personToolIndex >= 0)
            {
                CmPerson.Items.Insert(personToolIndex + 1, cmtsToolId6);
                CmPerson.Items.Insert(personToolIndex + 2, cmtsToolId7);
            }

            miSetGroupToolId6 = new ToolStripMenuItem("Применять ко всем яд", null, MiSetGroupToolId6Click) { Name = "miSetGroupToolId6" };
            miSetGroupToolId7 = new ToolStripMenuItem("Применять ко всем сильную спину", null, MiSetGroupToolId7Click) { Name = "miSetGroupToolId7" };
            var groupToolIndex = CmGroup.Items.IndexOf(miSetGroupToolId5);
            if (groupToolIndex >= 0)
            {
                CmGroup.Items.Insert(groupToolIndex + 1, miSetGroupToolId6);
                CmGroup.Items.Insert(groupToolIndex + 2, miSetGroupToolId7);
            }

            cmtsRefreshContact = new ToolStripMenuItem("Обновить данные контакта", Resources._16x16_refresh, CmtsRefreshContactClick) { Name = "cmtsRefreshContact" };
            var personRefreshIndex = CmPerson.Items.IndexOf(toolStripSeparator9);
            var personInsertIndex = personRefreshIndex >= 0 ? personRefreshIndex + 1 : CmPerson.Items.Count;
            CmPerson.Items.Insert(personInsertIndex, cmtsRefreshContact);
            cmtsImportClanContacts = new ToolStripMenuItem("Добавить всех из клана", Resources._16x16_person, CmtsImportClanContactsClick) { Name = "cmtsImportClanContacts" };
            CmPerson.Items.Insert(personInsertIndex + 1, cmtsImportClanContacts);

            miRefreshGroupContacts = new ToolStripMenuItem("Обновить группу", Resources._16x16_refresh, MiRefreshGroupContactsClick) { Name = "miRefreshGroupContacts" };
            miRefreshNeutralContacts = new ToolStripMenuItem("Обновить нейтралов", Resources._16x16_refresh, MiRefreshNeutralContactsClick) { Name = "miRefreshNeutralContacts" };
            miRefreshAllContacts = new ToolStripMenuItem("Обновить все контакты", Resources._16x16_refresh, MiRefreshAllContactsClick) { Name = "miRefreshAllContacts" };
            miImportGroupClanContacts = new ToolStripMenuItem("Добавить всех из клана", Resources._16x16_person, MiImportGroupClanContactsClick) { Name = "miImportGroupClanContacts" };
            var groupRefreshIndex = CmGroup.Items.IndexOf(toolStripSeparator24);
            var insertGroupIndex = groupRefreshIndex >= 0 ? groupRefreshIndex + 1 : CmGroup.Items.Count;
            CmGroup.Items.Insert(insertGroupIndex, miRefreshGroupContacts);
            CmGroup.Items.Insert(insertGroupIndex + 1, miRefreshNeutralContacts);
            CmGroup.Items.Insert(insertGroupIndex + 2, miRefreshAllContacts);
            CmGroup.Items.Insert(insertGroupIndex + 3, miImportGroupClanContacts);

            tsRefreshContact = new ToolStripButton
            {
                DisplayStyle = ToolStripItemDisplayStyle.Image,
                Image = Resources._16x16_refresh,
                ImageTransparentColor = System.Drawing.Color.Magenta,
                Name = "tsRefreshContact",
                ToolTipText = "Обновить выбранный контакт"
            };
            tsRefreshContact.Click += TsRefreshContactClick;

            tsRefreshAllContacts = new ToolStripButton
            {
                DisplayStyle = ToolStripItemDisplayStyle.Image,
                Image = Resources._16x16_refresh,
                ImageTransparentColor = System.Drawing.Color.Magenta,
                Name = "tsRefreshAllContacts",
                ToolTipText = "Обновить все контакты"
            };
            tsRefreshAllContacts.Click += TsRefreshAllContactsClick;

            var toolbarIndex = toolStrip3.Items.IndexOf(toolStripSeparator8);
            toolStrip3.Items.Insert(toolbarIndex >= 0 ? toolbarIndex + 1 : toolStrip3.Items.Count, tsRefreshContact);
            toolStrip3.Items.Insert(toolbarIndex >= 0 ? toolbarIndex + 2 : toolStrip3.Items.Count, tsRefreshAllContacts);
        }

        private void tsContactTrace_Click(object sender, EventArgs e)
        {
            AppVars.Profile.DoContactTrace = tsContactTrace.Checked;
        }

        private void tsBossTrace_Click(object sender, EventArgs e)
        {
            AppVars.Profile.DoBossTrace = tsBossTrace.Checked;
        }

        internal void AddContactFromBulk(string nick)
        {
            ContactsManager.Add(treeContacts, nick);
        }

        private void AddContact(string nick)
        {
            ContactsManager.Add(treeContacts, nick);
        }

        private void DeleteContact()
        {
            var tn = treeContacts.SelectedNode;
            if (tn.Tag == null)
            {
                return;
            }

            ContactsManager.Remove(treeContacts, tn);
        }

        private void SelectContact(TreeNode tn)
        {
            if (tn.Tag == null)
            {
                tsDeleteContact.Enabled = false;
                tsContactPrivate.Enabled = false;
                cmtsDeleteContact.Enabled = false;
                cmtsContactPrivate.Enabled = false;
                tbContactDetails.Text = string.Empty;
                miRemoveGroup.Enabled = true;
                UpdateClanImportMenu(null, tn);
            }
            else
            {
                var contact = (Contact)tn.Tag;
                tsDeleteContact.Enabled = true;
                cmtsDeleteContact.Enabled = true;
                tsContactPrivate.Enabled = true;
                cmtsContactPrivate.Enabled = true;

                cmtsClassNeutral.Checked = false;
                cmtsClassFoe.Checked = false;
                cmtsClassFriend.Checked = false;
                switch (contact.ClassId)
                {
                    case 0:
                        cmtsClassNeutral.Checked = true;
                        break;
                    case 1:
                        cmtsClassFoe.Checked = true;
                        break;
                    case 2:
                        cmtsClassFriend.Checked = true;
                        break;
                    default:
                        cmtsClassNeutral.Checked = true;
                        break;
                }

                cmtsToolId0.Checked = false;
                cmtsToolId1.Checked = false;
                cmtsToolId2.Checked = false;
                cmtsToolId3.Checked = false;
                cmtsToolId4.Checked = false;
                cmtsToolId5.Checked = false;
                cmtsToolId6.Checked = false;
                cmtsToolId7.Checked = false;
                switch (contact.ToolId)
                {
                    case 0:
                        cmtsToolId0.Checked = true;
                        break;
                    case 1:
                        cmtsToolId1.Checked = true;
                        break;
                    case 2:
                        cmtsToolId2.Checked = true;
                        break;
                    case 3:
                        cmtsToolId3.Checked = true;
                        break;
                    case 4:
                        cmtsToolId4.Checked = true;
                        break;
                    case 5:
                        cmtsToolId5.Checked = true;
                        break;
                    case 6:
                        cmtsToolId6.Checked = true;
                        break;
                    case 7:
                        cmtsToolId7.Checked = true;
                        break;
                    default:
                        cmtsToolId0.Checked = true;
                        break;
                }

                tbContactDetails.Text = contact.BuildDetailsText();
                UpdateClanImportMenu(contact, null);
            }
        }

        private void CommentContact()
        {
            var tn = treeContacts.SelectedNode;
            if (tn?.Tag == null)
                return;

            var ce = (Contact)tn.Tag;
            ce.Comments = Contact.ExtractEditableComments(tbContactDetails.Text ?? string.Empty);
            ContactsManager.UpdateComments(ce, ce.Comments);
        }

        private void OpenContact()
        {
            var contact = GetContact();
            if ((contact == null) || string.IsNullOrEmpty(contact.Name))
            {
                return;
            }

            CreateNewTab(TabType.PInfo, Resources.AddressPInfo + contact.Name, false);
        }

        private void OpenQuickFromContact()
        {
            var contact = GetContact();
            if ((contact == null) || string.IsNullOrEmpty(contact.Name))
            {
                return;
            }

            var formQuick = new FormQuick(contact.Name);
            formQuick.Show();
        }

        private Contact GetContact()
        {
            if (treeContacts == null)
            {
                return null;
            }

            if (treeContacts.SelectedNode == null)
            {
                return null;
            }

            var tn = treeContacts.SelectedNode;

            var contact = (Contact) tn.Tag;
            return contact;
        }


        private void WriteContactPrivate()
        {
            if (treeContacts != null)
            {
                var tn = treeContacts.SelectedNode;
                if (tn.Tag == null)
                {
                    return;
                }

                var ce = (Contact)tn.Tag;
                WriteMessageToPrompt("%<" + ce.Name + "> ");
            }

            tabControlLeft.SelectedIndex = 0;
        }

        private void SetContactClass(int classid)
        {
            var tn = treeContacts.SelectedNode;

            var contact = (Contact) tn.Tag;
            if (contact == null)
                return;

            contact.ClassId = classid;
            if (!AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                return;

            AppVars.Profile.Contacts[contact.Name.ToLower()].ClassId = classid;
            if (!tn.Checked)
                return;

            tn.ForeColor = ContactsManager.GetColorOfContact(contact);
            SelectContact(tn);
        }

        private void CmtsClassNeutralClick(object sender, EventArgs e)
        {
            SetContactClass(0);
        }

        private void CmtsClassFoeClick(object sender, EventArgs e)
        {
            SetContactClass(1);
        }

        private void CmtsClassFriendClick(object sender, EventArgs e)
        {
            SetContactClass(2);
        }

        private void SetContactToolId(int toolid)
        {
            var tn = treeContacts.SelectedNode;

            var contact = (Contact) tn.Tag;
            if (contact == null)
                return;
            
            if (!AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                return;

            AppVars.Profile.Contacts[contact.Name.ToLower()].ToolId = toolid;
            SelectContact(tn);
        }

        private void CmtsToolId0Click(object sender, EventArgs e)
        {
            SetContactToolId(0);
        }

        private void CmtsToolId1Click(object sender, EventArgs e)
        {
            SetContactToolId(1);
        }

        private void CmtsToolId2Click(object sender, EventArgs e)
        {
            SetContactToolId(2);
        }

        private void CmtsToolId3Click(object sender, EventArgs e)
        {
            SetContactToolId(3);
        }

        private void CmtsToolId4Click(object sender, EventArgs e)
        {
            SetContactToolId(4);
        }

        private void CmtsToolId5Click(object sender, EventArgs e)
        {
            SetContactToolId(5);
        }

        private void CmtsToolId6Click(object sender, EventArgs e)
        {
            SetContactToolId(6);
        }

        private void CmtsToolId7Click(object sender, EventArgs e)
        {
            SetContactToolId(7);
        }

        private void SetGroupClass(int classid)
        {
            var tngroup = treeContacts.SelectedNode;
            if (tngroup.Tag != null)
                return;

            foreach (TreeNode tn in tngroup.Nodes)
            {
                var contact = (Contact)tn.Tag;
                if (contact == null)
                    continue;

                if (!AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                    continue;

                AppVars.Profile.Contacts[contact.Name.ToLower()].ClassId = classid;
                if (!tn.Checked)
                    continue;

                tn.ForeColor = ContactsManager.GetColorOfContact(contact);
            }
        }

        private void MiSetGroupNeutralClick(object sender, EventArgs e)
        {
            SetGroupClass(0);
        }

        private void MiSetGroupFoeClick(object sender, EventArgs e)
        {
            SetGroupClass(1);
        }

        private void MiSetGroupFriendClick(object sender, EventArgs e)
        {
            SetGroupClass(2);
        }

        private void SetGroupToolId(int toolid)
        {
            var tngroup = treeContacts.SelectedNode;
            if (tngroup.Tag != null)
                return;

            foreach (TreeNode tn in tngroup.Nodes)
            {
                var contact = (Contact)tn.Tag;
                if (contact == null)
                    continue;

                if (!AppVars.Profile.Contacts.ContainsKey(contact.Name.ToLower()))
                    continue;

                AppVars.Profile.Contacts[contact.Name.ToLower()].ToolId = toolid;
            }
        }

        private void MiSetGroupToolId0Click(object sender, EventArgs e)
        {
            SetGroupToolId(0);
        }

        private void MiSetGroupToolId1Click(object sender, EventArgs e)
        {
            SetGroupToolId(1);
        }

        private void MiSetGroupToolId2Click(object sender, EventArgs e)
        {
            SetGroupToolId(2);
        }

        private void MiSetGroupToolId3Click(object sender, EventArgs e)
        {
            SetGroupToolId(3);
        }

        private void MiSetGroupToolId4Click(object sender, EventArgs e)
        {
            SetGroupToolId(4);
        }

        private void MiSetGroupToolId5Click(object sender, EventArgs e)
        {
            SetGroupToolId(5);
        }

        private void MiSetGroupToolId6Click(object sender, EventArgs e)
        {
            SetGroupToolId(6);
        }

        private void MiSetGroupToolId7Click(object sender, EventArgs e)
        {
            SetGroupToolId(7);
        }

        private void RefreshSelectedContact()
        {
            var contact = GetContact();
            if (contact == null)
                return;

            ContactsManager.RefreshContact(contact);
        }

        private void RefreshSelectedGroup()
        {
            if (treeContacts == null || treeContacts.SelectedNode == null)
                return;

            var selectedNode = treeContacts.SelectedNode;
            if (selectedNode.Tag != null)
            {
                RefreshSelectedContact();
                return;
            }

            ContactsManager.RefreshGroupContacts(selectedNode.Name);
        }

        private void CmtsRefreshContactClick(object sender, EventArgs e)
        {
            RefreshSelectedContact();
        }

        private void TsRefreshContactClick(object sender, EventArgs e)
        {
            RefreshSelectedContact();
        }

        private void TsRefreshAllContactsClick(object sender, EventArgs e)
        {
            ContactsManager.RefreshAllContacts();
        }

        private void MiRefreshGroupContactsClick(object sender, EventArgs e)
        {
            RefreshSelectedGroup();
        }

        private void MiRefreshNeutralContactsClick(object sender, EventArgs e)
        {
            ContactsManager.RefreshNeutralContacts();
        }

        private void MiRefreshAllContactsClick(object sender, EventArgs e)
        {
            ContactsManager.RefreshAllContacts();
        }

        private void CmtsImportClanContactsClick(object sender, EventArgs e)
        {
            ImportSelectedContactClan();
        }

        private void MiImportGroupClanContactsClick(object sender, EventArgs e)
        {
            ImportSelectedGroupClan();
        }

        private void ImportSelectedContactClan()
        {
            var contact = GetContact();
            if (contact == null || string.IsNullOrEmpty(GetImportableClanName(contact)))
                return;

            ContactsManager.ImportClan(treeContacts, contact, "ContactsContext");
        }

        private void ImportSelectedGroupClan()
        {
            if (treeContacts == null || treeContacts.SelectedNode == null)
                return;

            var contact = GetFirstImportableContactFromGroup(treeContacts.SelectedNode);
            if (contact == null)
                return;

            ContactsManager.ImportClan(treeContacts, contact, "ContactsGroupContext");
        }

        private void UpdateClanImportMenu(Contact contact, TreeNode groupNode)
        {
            var personClanName = GetImportableClanName(contact);
            if (cmtsImportClanContacts != null)
            {
                cmtsImportClanContacts.Enabled = !string.IsNullOrEmpty(personClanName);
                cmtsImportClanContacts.Text = BuildImportClanText(personClanName);
            }

            var groupClanName = GetImportableClanName(GetFirstImportableContactFromGroup(groupNode));
            if (miImportGroupClanContacts != null)
            {
                miImportGroupClanContacts.Enabled = !string.IsNullOrEmpty(groupClanName);
                miImportGroupClanContacts.Text = BuildImportClanText(groupClanName);
            }
        }

        private static string BuildImportClanText(string clanName)
        {
            return string.IsNullOrEmpty(clanName) ? "Добавить всех из клана" : "Добавить всех из '" + clanName + "'";
        }

        private static string GetImportableClanName(Contact contact)
        {
            if (contact == null)
                return string.Empty;

            var clanName = string.IsNullOrEmpty(contact.ClanName) ? contact.Clan : contact.ClanName;
            if (string.IsNullOrEmpty(clanName) || ContactRenderHelper.IsNeutralClanName(clanName))
                return string.Empty;

            return clanName;
        }

        private static Contact GetFirstImportableContactFromGroup(TreeNode groupNode)
        {
            if (groupNode == null || groupNode.Tag != null)
                return null;

            foreach (TreeNode childNode in groupNode.Nodes)
            {
                var contact = childNode.Tag as Contact;
                if (contact == null || string.IsNullOrEmpty(GetImportableClanName(contact)))
                    continue;

                return contact;
            }

            return null;
        }
    }
}
