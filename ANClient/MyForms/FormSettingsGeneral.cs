using ANClient.MyProfile;
using System.Collections.Generic;

namespace ANClient.MyForms
{
    using System;
    using System.Globalization;
    using System.Windows.Forms;

    internal partial class FormSettingsGeneral : Form
    {
        private readonly CheckBox checkBossAutoEnabled = new CheckBox();
        private readonly CheckBox checkBossAutoAttack = new CheckBox();
        private readonly CheckBox checkBossAutoTrace = new CheckBox();
        private readonly CheckBox checkBossAutoReport = new CheckBox();
        private readonly TextBox textBossSearchWords = new TextBox();
        private readonly NumericUpDown numBossSearchInterval = new NumericUpDown();
        private readonly CheckBox checkCompassAutoEnabled = new CheckBox();
        private readonly CheckBox checkCompassAutoOnBattle = new CheckBox();
        private readonly CheckBox checkCompassAutoAttack = new CheckBox();
        private readonly CheckBox checkCompassAutoWhisper = new CheckBox();
        private readonly NumericUpDown numCompassSearchRadius = new NumericUpDown();
        private readonly CheckBox checkAutoMineEnabled = new CheckBox();
        private readonly CheckBox checkAutoMineChatReport = new CheckBox();
        private readonly CheckBox checkAutoMineStopOnEmpty = new CheckBox();
        private readonly TextBox textAutoMinePickaxes = new TextBox();
        private readonly TextBox textAutoMineTorches = new TextBox();

        internal FormSettingsGeneral()
        {
            InitializeComponent();

            var tabPageBossAuto = new TabPage { Text = @"АвтоБосс", UseVisualStyleBackColor = true };
            tabControlSettings.Controls.Add(tabPageBossAuto);

            var bossGroup = new GroupBox { Text = @"Авто-Босс", Dock = DockStyle.Top, Height = 185 };
            checkBossAutoEnabled.Text = @"Включить авто-боссов";
            checkBossAutoAttack.Text = @"Автонападение при нахождении";
            checkBossAutoTrace.Text = @"Слежение за контактами боссов";
            checkBossAutoReport.Text = @"Уведомление в клан-чат";
            checkBossAutoEnabled.Left = 10; checkBossAutoEnabled.Top = 20;
            checkBossAutoAttack.Left = 10; checkBossAutoAttack.Top = 42;
            checkBossAutoTrace.Left = 10; checkBossAutoTrace.Top = 64;
            checkBossAutoReport.Left = 10; checkBossAutoReport.Top = 86;

            var labelBossSearchWords = new Label { Text = @"Фильтр боссов:", Left = 10, Top = 118, AutoSize = true };
            textBossSearchWords.Left = 115; textBossSearchWords.Top = 115; textBossSearchWords.Width = 290;

            var labelBossSearchInterval = new Label { Text = @"Таймаут поиска, сек:", Left = 10, Top = 150, AutoSize = true };
            numBossSearchInterval.Minimum = 30; numBossSearchInterval.Maximum = 1800; numBossSearchInterval.Increment = 30;
            numBossSearchInterval.Left = 145; numBossSearchInterval.Top = 147; numBossSearchInterval.Width = 70;

            bossGroup.Controls.AddRange(new Control[]
            {
                checkBossAutoEnabled,
                checkBossAutoAttack,
                checkBossAutoTrace,
                checkBossAutoReport,
                labelBossSearchWords,
                textBossSearchWords,
                labelBossSearchInterval,
                numBossSearchInterval
            });
            tabPageBossAuto.Controls.Add(bossGroup);

            var compassGroup = new GroupBox { Text = @"Авто-Компас", Dock = DockStyle.Top, Height = 120 };
            checkCompassAutoEnabled.Text = @"Включить автокомпас";
            checkCompassAutoOnBattle.Text = @"Искать при бое цели";
            checkCompassAutoAttack.Text = @"Автоперемещение к цели";
            checkCompassAutoWhisper.Text = @"Шептать при нахождении";
            numCompassSearchRadius.Minimum = 1; numCompassSearchRadius.Maximum = 50;
            numCompassSearchRadius.Value = 5; numCompassSearchRadius.Width = 50;
            var lblRadius = new Label { Text = @"Радиус (шагов):", Left = 200, Top = 20, AutoSize = true };
            checkCompassAutoEnabled.Left = 10; checkCompassAutoEnabled.Top = 20;
            checkCompassAutoOnBattle.Left = 10; checkCompassAutoOnBattle.Top = 42;
            checkCompassAutoAttack.Left = 10; checkCompassAutoAttack.Top = 64;
            checkCompassAutoWhisper.Left = 10; checkCompassAutoWhisper.Top = 86;
            numCompassSearchRadius.Left = 310; numCompassSearchRadius.Top = 18;
            compassGroup.Controls.AddRange(new Control[] { checkCompassAutoEnabled, checkCompassAutoOnBattle, checkCompassAutoAttack, checkCompassAutoWhisper, lblRadius, numCompassSearchRadius });
            tabPage1.Controls.Add(compassGroup);

            BuildAutoMineSettingsTab();

            checkBoxDoAutoDrinkBlaz.Checked = AppVars.Profile.DoAutoDrinkBlaz;
            textBoxAutoDrinkBlazTied.Text = AppVars.Profile.AutoDrinkBlazTied.ToString(CultureInfo.InvariantCulture);
            groupBoxDoAutoDrinkBlaz.Text = @"Автопитье блажа";
            checkBoxDoAutoDrinkBlaz.Enabled = true;
            textBoxAutoDrinkBlazTied.Enabled = true;

            checkboxDoPromptExit.Checked = AppVars.Profile.DoPromptExit;
            checkboxDoTray.Checked = AppVars.Profile.DoTray;
            checkboxShowTrayBaloons.Checked = AppVars.Profile.ShowTrayBaloons;

            checkboxDoKeepChatMoving.Checked = AppVars.Profile.ChatKeepMoving;
            checkboxDoKeepChatGame.Checked = AppVars.Profile.ChatKeepGame;
            checkboxDoKeepChatLog.Checked = AppVars.Profile.ChatKeepLog;
            linkChatSizeLog.Tag = AppVars.Profile.ChatSizeLog;
            linkChatSizeLog.Text = "Размер чата: " + AppVars.Profile.ChatSizeLog + "Кб";
            checkDoChatLevels.Checked = AppVars.Profile.DoChatLevels;

            checkStatReset.Checked = AppVars.Profile.Stat.Reset;

            checkboxRazdChatReport.Checked = AppVars.Profile.RazdChatReport;

            numCureNV1.Value = AppVars.Profile.CureNV[0];
            numCureNV2.Value = AppVars.Profile.CureNV[1];
            numCureNV3.Value = AppVars.Profile.CureNV[2];
            numCureNV4.Value = AppVars.Profile.CureNV[3];
            textCureAsk1.Text = AppVars.Profile.CureAsk[0];
            textCureAsk2.Text = AppVars.Profile.CureAsk[1];
            textCureAsk3.Text = AppVars.Profile.CureAsk[2];
            textCureAsk4.Text = AppVars.Profile.CureAsk[3];
            textCureAdv.Text = AppVars.Profile.CureAdv;
            textCureAfter.Text = AppVars.Profile.CureAfter;
            textCureBoi.Text = AppVars.Profile.CureBoi;
            checkE1.Checked = AppVars.Profile.CureEnabled[0];
            checkE2.Checked = AppVars.Profile.CureEnabled[1];
            checkE3.Checked = AppVars.Profile.CureEnabled[2];
            checkE4.Checked = AppVars.Profile.CureEnabled[3];
            checkD04.Checked = AppVars.Profile.CureDisabledLowLevels;

            checkBoxDoExtendMap.Checked = AppVars.Profile.MapShowExtend;
            
            numBigMapWidth.Maximum = AppConsts.MapBigWidthMax;
            numBigMapWidth.Minimum = AppConsts.MapBigWidthMin;
            numBigMapWidth.Value = AppVars.Profile.MapBigWidth;
            
            numBigMapHeight.Maximum = AppConsts.MapBigHeightMax;
            numBigMapHeight.Minimum = AppConsts.MapBigHeightMin;
            numBigMapHeight.Value = AppVars.Profile.MapBigHeight;
            
            numBigMapScale.Maximum = AppConsts.MapBigScaleMax;
            numBigMapScale.Minimum = AppConsts.MapBigScaleMin;
            numBigMapScale.Value = AppVars.Profile.MapBigScale;
            
            numBigMapTransparency.Maximum = AppConsts.MapBigTransparencyMax;
            numBigMapTransparency.Minimum = AppConsts.MapBigTransparencyMin;
            numBigMapTransparency.Value = AppVars.Profile.MapBigTransparency;
            
            checkBoxBigMapBackColorWhite.Checked = AppVars.Profile.MapShowBackColorWhite;
            checkBoxMapDrawRegion.Checked = AppVars.Profile.MapDrawRegion;

            numMiniMapWidth.Maximum = AppConsts.MapMiniWidthMax;
            numMiniMapWidth.Minimum = AppConsts.MapMiniWidthMin;
            numMiniMapWidth.Value = AppVars.Profile.MapMiniWidth;

            numMiniMapHeight.Maximum = AppConsts.MapMiniHeightMax;
            numMiniMapHeight.Minimum = AppConsts.MapMiniHeightMin;
            numMiniMapHeight.Value = AppVars.Profile.MapMiniHeight;

            numMiniMapScale.Maximum = AppConsts.MapMiniScaleMax;
            numMiniMapScale.Minimum = AppConsts.MapMiniScaleMin;
            numMiniMapScale.Value = AppVars.Profile.MapMiniScale;

            checkShowMiniMap.Checked = AppVars.Profile.MapShowMiniMap;

            numFishTiedHigh.Value = AppVars.Profile.FishTiedHigh;

            checkFishTiedZero.Checked = AppVars.Profile.FishTiedZero;
            
            checkboxStopOverW.Checked = AppVars.Profile.FishStopOverWeight;

            checkUseSounds.Checked = AppVars.Profile.Sound.Enabled;
            checkDoPlayDigits.Checked = AppVars.Profile.Sound.DoPlayDigits;
            checkDoPlayAttack.Checked = AppVars.Profile.Sound.DoPlayAttack;
            checkDoPlaySndMsg.Checked = AppVars.Profile.Sound.DoPlaySndMsg;
            checkDoPlayRefresh.Checked = AppVars.Profile.Sound.DoPlayRefresh;
            checkDoPlayAlarm.Checked = AppVars.Profile.Sound.DoPlayAlarm;
            checkDoPlayTimer.Checked = AppVars.Profile.Sound.DoPlayTimer;

            numAdvMin.Value = (int)((decimal)AppVars.Profile.AutoAdv.Sec / 60);
            numAdvSec.Value = AppVars.Profile.AutoAdv.Sec % 60;
            textPhraz.Text = AppVars.Profile.AutoAdv.Phraz;

            checkFishAutoWear.Checked = AppVars.Profile.FishAutoWear;
            for (var i = 0; i < comboFishHand1.Items.Count; i++)
            {
                if (!AppVars.Profile.FishHandOne.Equals((string)comboFishHand1.Items[i], StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                comboFishHand1.SelectedIndex = i;
                break;
            }

            if (comboFishHand1.SelectedIndex == -1)
            {
                comboFishHand1.SelectedIndex = 0;
            }

            for (var i = 0; i < comboFishHand2.Items.Count; i++)
            {
                if (!AppVars.Profile.FishHandTwo.Equals((string)comboFishHand2.Items[i], StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                comboFishHand2.SelectedIndex = i;
                break;
            }

            if (comboFishHand2.SelectedIndex == -1)
            {
                comboFishHand2.SelectedIndex = 0;
            }

            checkPrimBread.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Bread) != 0;
            checkPrimWorm.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Worm) != 0;
            checkPrimBigWorm.Checked = (AppVars.Profile.FishEnabledPrims & Prims.BigWorm) != 0;
            checkPrimStink.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Stink) != 0;
            checkPrimFly.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Fly) != 0;
            checkPrimLight.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Light) != 0;
            checkPrimMorm.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Morm) != 0;
            checkPrimHiFlight.Checked = (AppVars.Profile.FishEnabledPrims & Prims.HiFlight) != 0;
            checkPrimDonka.Checked = (AppVars.Profile.FishEnabledPrims & Prims.Donka) != 0;

            checkboxFishChatReport.Checked = AppVars.Profile.FishChatReport;
            checkboxFishChatReportColor.Checked = AppVars.Profile.FishChatReportColor;

            checkAutoAnswer.Checked = AppVars.Profile.DoAutoAnswer;
            textAutoAnswer.Text = AppVars.Profile.AutoAnswer.Replace(AppConsts.Br, Environment.NewLine);

            checkLightForum.Checked = AppVars.Profile.LightForum;

            textTorgTable.Text = AppVars.Profile.TorgTabl;
            textTorgMessageAdv.Text = AppVars.Profile.TorgMessageAdv;
            textTorgAdvTime.Text = AppVars.Profile.TorgAdvTime.ToString(CultureInfo.InvariantCulture);
            textTorgMessageNoMoney.Text = AppVars.Profile.TorgMessageNoMoney;
            textTorgMessageTooExp.Text = AppVars.Profile.TorgMessageTooExp;
            textTorgMessageThanks.Text = AppVars.Profile.TorgMessageThanks;
            textTorgMessageLess90.Text = AppVars.Profile.TorgMessageLess90;
            checkTorgSliv.Checked = AppVars.Profile.TorgSliv;
            textTorgMinLevel.Text = AppVars.Profile.TorgMinLevel.ToString(CultureInfo.InvariantCulture);
            textTorgEx.Text = AppVars.Profile.TorgEx;
            textTorgDeny.Text = AppVars.Profile.TorgDeny;

            checkDoInvPack.Checked = AppVars.Profile.DoInvPack;
            checkDoInvPackDolg.Checked = AppVars.Profile.DoInvPackDolg;
            checkDoInvSort.Checked = AppVars.Profile.DoInvSort;

            checkDoShowFastAttack.Checked = AppVars.Profile.DoShowFastAttack;
            checkDoShowFastAttackBlood.Checked = AppVars.Profile.DoShowFastAttackBlood;
            checkDoShowFastAttackUltimate.Checked = AppVars.Profile.DoShowFastAttackUltimate;
            checkDoShowFastAttackClosedUltimate.Checked = AppVars.Profile.DoShowFastAttackClosedUltimate;
            checkDoShowFastAttackClosed.Checked = AppVars.Profile.DoShowFastAttackClosed;
            checkDoShowFastAttackFist.Checked = AppVars.Profile.DoShowFastAttackFist;
            checkDoShowFastAttackClosedFist.Checked = AppVars.Profile.DoShowFastAttackClosedFist;
            checkDoShowFastAttackOpenNevid.Checked = AppVars.Profile.DoShowFastAttackOpenNevid;
            checkDoShowFastAttackPoison.Checked = AppVars.Profile.DoShowFastAttackPoison;
            checkDoShowFastAttackStrong.Checked = AppVars.Profile.DoShowFastAttackStrong;
            checkDoShowFastAttackNevid.Checked = AppVars.Profile.DoShowFastAttackNevid;
            checkDoShowFastAttackFog.Checked = AppVars.Profile.DoShowFastAttackFog;
            checkDoShowFastAttackZas.Checked = AppVars.Profile.DoShowFastAttackZas;
            checkDoShowFastAttackTotem.Checked = AppVars.Profile.DoShowFastAttackTotem;
            checkDoShowFastAttackPortal.Checked = AppVars.Profile.DoShowFastAttackPortal;

            checkShowOverWarning.Checked = AppVars.Profile.ShowOverWarning;
            checkDoStopOnDig.Checked = AppVars.Profile.DoStopOnDig;

            checkDoRob.Checked = AppVars.Profile.DoRob;
            checkDoAutoCure.Checked = AppVars.Profile.DoAutoCure;
            textAutoWearComplect.Text = AppVars.Profile.AutoWearComplect ?? string.Empty;

            comboBoxDoAutoDrinkBlaz.SelectedIndex = AppVars.Profile.AutoDrinkBlazOrder;

            switch (AppVars.Profile.BossSay)
            {
                case LezSayType.No:
                    radioSayNo.Checked = true;
                    break;

                case LezSayType.Chat:
                    radioSayChat.Checked = true;
                    break;

                case LezSayType.Clan:
                    radioSayClan.Checked = true;
                    break;

                case LezSayType.Pair:
                    radioSayPair.Checked = true;
                    break;
            }

            checkBossAutoEnabled.Checked = AppVars.Profile.BossAutoEnabled;
            checkBossAutoAttack.Checked = AppVars.Profile.BossAutoAttack;
            checkBossAutoTrace.Checked = AppVars.Profile.BossAutoTrace;
            checkBossAutoReport.Checked = AppVars.Profile.BossAutoReport;
            textBossSearchWords.Text = AppVars.Profile.BossSearchWords ?? string.Empty;
            numBossSearchInterval.Value = Math.Max(
                numBossSearchInterval.Minimum,
                Math.Min(numBossSearchInterval.Maximum, AppVars.Profile.BossSearchInterval <= 0 ? 360 : AppVars.Profile.BossSearchInterval));

            checkCompassAutoEnabled.Checked = AppVars.Profile.CompassAutoEnabled;
            checkCompassAutoOnBattle.Checked = AppVars.Profile.CompassAutoOnBattle;
            checkCompassAutoAttack.Checked = AppVars.Profile.CompassAutoAttack;
            checkCompassAutoWhisper.Checked = AppVars.Profile.CompassAutoWhisper;
            numCompassSearchRadius.Value = Math.Max(numCompassSearchRadius.Minimum, AppVars.Profile.CompassSearchRadius);

            checkAutoMineEnabled.Checked = AppVars.Profile.AutoMine;
            checkAutoMineChatReport.Checked = AppVars.Profile.AutoMineChatReport;
            checkAutoMineStopOnEmpty.Checked = AppVars.Profile.AutoMineStopOnEmpty;
            textAutoMinePickaxes.Text = FormatAutoMineNames(AppVars.Profile.AutoMinePickaxesCsv, AutoMineRuntime.GetDefaultPickaxeNames());
            textAutoMineTorches.Text = FormatAutoMineNames(AppVars.Profile.AutoMineTorchesCsv, AutoMineRuntime.GetDefaultTorchNames());
        }

        private void BuildAutoMineSettingsTab()
        {
            var tabPageAutoMine = new TabPage { Text = @"АвтоШахтёр", UseVisualStyleBackColor = true };
            tabControlSettings.Controls.Add(tabPageAutoMine);

            var group = new GroupBox { Text = @"Авто-Шахтёр", Dock = DockStyle.Top, Height = 245 };
            checkAutoMineEnabled.Text = @"Включить автошахтёра";
            checkAutoMineChatReport.Text = @"Писать отчёт о добыче в чат";
            checkAutoMineStopOnEmpty.Text = @"Останавливать при пустой добыче";
            checkAutoMineEnabled.Left = 10;
            checkAutoMineEnabled.Top = 20;
            checkAutoMineChatReport.Left = 10;
            checkAutoMineChatReport.Top = 44;
            checkAutoMineStopOnEmpty.Left = 10;
            checkAutoMineStopOnEmpty.Top = 68;

            var labelPickaxes = new Label { Text = @"Кирки через |:", Left = 10, Top = 104, AutoSize = true };
            textAutoMinePickaxes.Left = 120;
            textAutoMinePickaxes.Top = 100;
            textAutoMinePickaxes.Width = 380;

            var labelTorches = new Label { Text = @"Факелы через |:", Left = 10, Top = 136, AutoSize = true };
            textAutoMineTorches.Left = 120;
            textAutoMineTorches.Top = 132;
            textAutoMineTorches.Width = 380;

            var hint = new Label
            {
                Text = @"Пустое поле означает стандартный список из ПК-версии.",
                Left = 10,
                Top = 178,
                AutoSize = true
            };

            group.Controls.AddRange(new Control[]
            {
                checkAutoMineEnabled,
                checkAutoMineChatReport,
                checkAutoMineStopOnEmpty,
                labelPickaxes,
                textAutoMinePickaxes,
                labelTorches,
                textAutoMineTorches,
                hint
            });
            tabPageAutoMine.Controls.Add(group);
        }

        private void OnButtonOkClick(object sender, EventArgs e)
        {
            AppVars.Profile.DoAutoDrinkBlaz = checkBoxDoAutoDrinkBlaz.Checked;
            int autoDrinkBlazTied;
            if (!int.TryParse(textBoxAutoDrinkBlazTied.Text, out autoDrinkBlazTied))
            {
                autoDrinkBlazTied = 84;
            }

            AppVars.Profile.AutoDrinkBlazTied = autoDrinkBlazTied;

            AppVars.Profile.DoPromptExit = checkboxDoPromptExit.Checked;
            AppVars.Profile.DoTray = checkboxDoTray.Checked;
            AppVars.Profile.ShowTrayBaloons = checkboxShowTrayBaloons.Checked;

            AppVars.Profile.ChatKeepMoving = checkboxDoKeepChatMoving.Checked;
            AppVars.Profile.ChatKeepGame = checkboxDoKeepChatGame.Checked;
            AppVars.Profile.ChatKeepLog = checkboxDoKeepChatLog.Checked;
            AppVars.Profile.ChatSizeLog = (int) linkChatSizeLog.Tag;
            AppVars.Profile.DoChatLevels = checkDoChatLevels.Checked;
            
            AppVars.Profile.DoRob = checkDoRob.Checked;

            if (!AppVars.Profile.Stat.Reset && checkStatReset.Checked)
            {
                AppVars.Profile.Stat.LastUpdateDay = DateTime.Now.DayOfYear;
            }

            AppVars.Profile.Stat.Reset = checkStatReset.Checked;

            AppVars.Profile.RazdChatReport = checkboxRazdChatReport.Checked;

            AppVars.Profile.CureNV[0] = (int)numCureNV1.Value;
            AppVars.Profile.CureNV[1] = (int)numCureNV2.Value;
            AppVars.Profile.CureNV[2] = (int)numCureNV3.Value;
            AppVars.Profile.CureNV[3] = (int)numCureNV4.Value;
            AppVars.Profile.CureAsk[0] = textCureAsk1.Text;
            AppVars.Profile.CureAsk[1] = textCureAsk2.Text;
            AppVars.Profile.CureAsk[2] = textCureAsk3.Text;
            AppVars.Profile.CureAsk[3] = textCureAsk4.Text;
            AppVars.Profile.CureAdv = textCureAdv.Text;
            AppVars.Profile.CureAfter = textCureAfter.Text;
            AppVars.Profile.CureBoi = textCureBoi.Text;
            AppVars.Profile.CureEnabled[0] = checkE1.Checked;
            AppVars.Profile.CureEnabled[1] = checkE2.Checked;
            AppVars.Profile.CureEnabled[2] = checkE3.Checked;
            AppVars.Profile.CureEnabled[3] = checkE4.Checked;
            AppVars.Profile.CureDisabledLowLevels = checkD04.Checked;
           
            AppVars.Profile.MapShowExtend = checkBoxDoExtendMap.Checked;
            AppVars.Profile.MapBigWidth = (int)numBigMapWidth.Value;
            AppVars.Profile.MapBigHeight = (int)numBigMapHeight.Value;
            AppVars.Profile.MapBigScale = (int)numBigMapScale.Value;
            AppVars.Profile.MapBigTransparency = (int)numBigMapTransparency.Value;
            AppVars.Profile.MapShowBackColorWhite = checkBoxBigMapBackColorWhite.Checked;
            AppVars.Profile.MapDrawRegion = checkBoxMapDrawRegion.Checked;

            AppVars.Profile.MapMiniWidth = (int)numMiniMapWidth.Value;
            AppVars.Profile.MapMiniHeight = (int)numMiniMapHeight.Value;
            AppVars.Profile.MapMiniScale = (int)numMiniMapScale.Value;
            AppVars.Profile.MapShowMiniMap = checkShowMiniMap.Checked;

            AppVars.Profile.FishStopOverWeight = checkboxStopOverW.Checked;

            AppVars.Profile.Sound.Enabled = checkUseSounds.Checked;
            AppVars.Profile.Sound.DoPlayDigits = checkDoPlayDigits.Checked;
            AppVars.Profile.Sound.DoPlayAttack = checkDoPlayAttack.Checked;
            AppVars.Profile.Sound.DoPlaySndMsg = checkDoPlaySndMsg.Checked;
            AppVars.Profile.Sound.DoPlayRefresh = checkDoPlayRefresh.Checked;
            AppVars.Profile.Sound.DoPlayAlarm = checkDoPlayAlarm.Checked;
            AppVars.Profile.Sound.DoPlayTimer = checkDoPlayTimer.Checked;

            AppVars.Profile.AutoAdv.Phraz = textPhraz.Text;
            AppVars.Profile.AutoAdv.Sec = (int)((numAdvMin.Value * 60) + numAdvSec.Value);
            if (AppVars.Profile.AutoAdv.Sec < 30)
            {
                AppVars.Profile.AutoAdv.Sec = 600;
            }

            AppVars.Profile.FishAutoWear = checkFishAutoWear.Checked;
            AppVars.Profile.FishHandOne = (string)comboFishHand1.SelectedItem;
            AppVars.Profile.FishHandTwo = (string)comboFishHand2.SelectedItem;

            AppVars.Profile.FishTiedHigh = (int)numFishTiedHigh.Value;
            AppVars.Profile.FishTiedZero = checkFishTiedZero.Checked;

            AppVars.Profile.FishEnabledPrims = 0;
            if (checkPrimBread.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Bread;
            }

            if (checkPrimWorm.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Worm;
            }

            if (checkPrimBigWorm.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.BigWorm;
            }

            if (checkPrimStink.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Stink;
            }

            if (checkPrimFly.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Fly;
            }

            if (checkPrimLight.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Light;
            }

            if (checkPrimDonka.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Donka;
            }

            if (checkPrimMorm.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.Morm;
            }

            if (checkPrimHiFlight.Checked)
            {
                AppVars.Profile.FishEnabledPrims += (int) Prims.HiFlight;
            }

            AppVars.Profile.FishChatReport = checkboxFishChatReport.Checked;
            AppVars.Profile.FishChatReportColor = checkboxFishChatReportColor.Checked;

            AppVars.Profile.DoAutoAnswer = checkAutoAnswer.Checked;
            AppVars.Profile.AutoAnswer = textAutoAnswer.Text.Trim().Replace(Environment.NewLine, AppConsts.Br);
            AutoAnswerMachine.SetAnswers(AppVars.Profile.AutoAnswer);

            AppVars.Profile.LightForum = checkLightForum.Checked;

            AppVars.Profile.TorgTabl = textTorgTable.Text;
            TorgList.Parse(textTorgTable.Text);
            AppVars.Profile.TorgMessageAdv = textTorgMessageAdv.Text;

            int advTime;
            if (int.TryParse(textTorgAdvTime.Text, out advTime))
            {
                AppVars.Profile.TorgAdvTime = advTime;
            }

            AppVars.Profile.TorgMessageNoMoney = textTorgMessageNoMoney.Text;
            AppVars.Profile.TorgMessageTooExp = textTorgMessageTooExp.Text;
            AppVars.Profile.TorgMessageThanks = textTorgMessageThanks.Text;
            AppVars.Profile.TorgMessageLess90 = textTorgMessageLess90.Text;
            AppVars.Profile.TorgSliv = checkTorgSliv.Checked;

            int minlevel;
            if (int.TryParse(textTorgMinLevel.Text, out minlevel))
            {
                AppVars.Profile.TorgMinLevel = minlevel;
            }

            AppVars.Profile.TorgEx = textTorgEx.Text;
            AppVars.Profile.TorgDeny = textTorgDeny.Text;

            AppVars.Profile.DoInvPack = checkDoInvPack.Checked;
            AppVars.Profile.DoInvPackDolg = checkDoInvPackDolg.Checked;
            AppVars.Profile.DoInvSort = checkDoInvSort.Checked;

            AppVars.Profile.DoShowFastAttack = checkDoShowFastAttack.Checked;
            AppVars.Profile.DoShowFastAttackBlood = checkDoShowFastAttackBlood.Checked; 
            AppVars.Profile.DoShowFastAttackUltimate = checkDoShowFastAttackUltimate.Checked;
            AppVars.Profile.DoShowFastAttackClosedUltimate = checkDoShowFastAttackClosedUltimate.Checked;
            AppVars.Profile.DoShowFastAttackClosed = checkDoShowFastAttackClosed.Checked;
            AppVars.Profile.DoShowFastAttackFist = checkDoShowFastAttackFist.Checked;
            AppVars.Profile.DoShowFastAttackClosedFist = checkDoShowFastAttackClosedFist.Checked;
            AppVars.Profile.DoShowFastAttackOpenNevid = checkDoShowFastAttackOpenNevid.Checked;
            AppVars.Profile.DoShowFastAttackPoison = checkDoShowFastAttackPoison.Checked;
            AppVars.Profile.DoShowFastAttackStrong = checkDoShowFastAttackStrong.Checked;
            AppVars.Profile.DoShowFastAttackNevid = checkDoShowFastAttackNevid.Checked;
            AppVars.Profile.DoShowFastAttackFog = checkDoShowFastAttackFog.Checked;
            AppVars.Profile.DoShowFastAttackZas = checkDoShowFastAttackZas.Checked;
            AppVars.Profile.DoShowFastAttackTotem = checkDoShowFastAttackTotem.Checked;
            AppVars.Profile.DoShowFastAttackPortal = checkDoShowFastAttackPortal.Checked;

            AppVars.Profile.ShowOverWarning = checkShowOverWarning.Checked;
            AppVars.Profile.DoStopOnDig = checkDoStopOnDig.Checked;

            AppVars.Profile.DoAutoCure = checkDoAutoCure.Checked;
            AppVars.Profile.AutoWearComplect = textAutoWearComplect.Text;

            AppVars.Profile.AutoDrinkBlazOrder = comboBoxDoAutoDrinkBlaz.SelectedIndex;

            if (radioSayNo.Checked)
                AppVars.Profile.BossSay = LezSayType.No;

            if (radioSayChat.Checked)
                AppVars.Profile.BossSay = LezSayType.Chat;

            if (radioSayClan.Checked)
                AppVars.Profile.BossSay = LezSayType.Clan;

            if (radioSayPair.Checked)
                AppVars.Profile.BossSay = LezSayType.Pair;

            AppVars.Profile.BossAutoEnabled = checkBossAutoEnabled.Checked;
            AppVars.Profile.BossAutoAttack = checkBossAutoAttack.Checked;
            AppVars.Profile.BossAutoTrace = checkBossAutoTrace.Checked;
            AppVars.Profile.BossAutoReport = checkBossAutoReport.Checked;
            AppVars.Profile.BossSearchWords = textBossSearchWords.Text.Trim();
            AppVars.Profile.BossSearchInterval = (int)numBossSearchInterval.Value;

            AppVars.Profile.CompassAutoEnabled = checkCompassAutoEnabled.Checked;
            AppVars.Profile.CompassAutoOnBattle = checkCompassAutoOnBattle.Checked;
            AppVars.Profile.CompassAutoAttack = checkCompassAutoAttack.Checked;
            AppVars.Profile.CompassAutoWhisper = checkCompassAutoWhisper.Checked;
            AppVars.Profile.CompassSearchRadius = (int)numCompassSearchRadius.Value;

            AppVars.Profile.AutoMine = checkAutoMineEnabled.Checked;
            AppVars.Profile.AutoMineChatReport = checkAutoMineChatReport.Checked;
            AppVars.Profile.AutoMineStopOnEmpty = checkAutoMineStopOnEmpty.Checked;
            AppVars.Profile.AutoMinePickaxesCsv = NormalizeAutoMineNames(textAutoMinePickaxes.Text, AutoMineRuntime.GetDefaultPickaxeNames());
            AppVars.Profile.AutoMineTorchesCsv = NormalizeAutoMineNames(textAutoMineTorches.Text, AutoMineRuntime.GetDefaultTorchNames());

            AppVars.Profile.Save();
        }

        private static string FormatAutoMineNames(string saved, string[] defaults)
        {
            return string.IsNullOrEmpty(saved) ? string.Join("|", defaults) : saved;
        }

        private static string NormalizeAutoMineNames(string value, string[] defaults)
        {
            var formattedDefault = string.Join("|", defaults);
            if (string.IsNullOrEmpty(value))
                return string.Empty;

            var parts = value.Split(new[] { '|', ';', ',', '\r', '\n', '\t' }, StringSplitOptions.RemoveEmptyEntries);
            var result = new List<string>();
            for (var i = 0; i < parts.Length; i++)
            {
                var name = parts[i].Trim();
                if (name.Length > 0)
                {
                    result.Add(name);
                }
            }

            if (result.Count == 0)
                return string.Empty;

            var joined = string.Join("|", result.ToArray());
            return joined.Equals(formattedDefault, StringComparison.OrdinalIgnoreCase) ? string.Empty : joined;
        }

        private void OnLinkChatSizeLogLinkClicked(object sender, LinkLabelLinkClickedEventArgs e)
        {
            using (var ff = new FormEnterInt("Размер чата", (int)((LinkLabel)sender).Tag, 8, 128))
            {
                if (ff.ShowDialog() != DialogResult.OK)
                {
                    return;
                }

                linkChatSizeLog.Tag = ff.Val;
                linkChatSizeLog.Text = "Размер чата: " + ff.Val + "Кб";
            }
        }

        private void textTorgTable_Validating(object sender, System.ComponentModel.CancelEventArgs e)
        {
            if (TorgList.Parse(textTorgTable.Text))
            {
                return;
            }

            e.Cancel = true;
            textTorgTable.Text = AppVars.Profile.TorgTabl;
            errorTorg.SetError(textTorgTable, "Ошибка в таблице торга");
        }

        private void textTorgTable_Validated(object sender, EventArgs e)
        {
            errorTorg.SetError(textTorgTable, string.Empty);
        }

        private void textTorgAdvTime_Validating(object sender, System.ComponentModel.CancelEventArgs e)
        {
            int advTime;
            if (int.TryParse(textTorgAdvTime.Text, out advTime))
            {
                if (advTime >= AppConsts.TorgAdvTimeMin && advTime <= AppConsts.TorgAdvTimeMax)
                {
                    return;
                }
            }

            e.Cancel = true;
            textTorgAdvTime.SelectAll();
            errorTorg.SetError(textTorgAdvTime, "Должно быть целое число минут, например, 10");
        }

        private void textTorgAdvTime_Validated(object sender, EventArgs e)
        {
            errorTorg.SetError(textTorgAdvTime, string.Empty);
        }

        private void textTorgMinLevel_Validated(object sender, EventArgs e)
        {
            errorTorg.SetError(textTorgMinLevel, string.Empty);
        }

        private void textTorgMinLevel_Validating(object sender, System.ComponentModel.CancelEventArgs e)
        {
            int minlevel;
            if (int.TryParse(textTorgMinLevel.Text, out minlevel))
            {
                if (minlevel >= 1 && minlevel <= 33)
                {
                    return;
                }
            }

            e.Cancel = true;
            textTorgMinLevel.SelectAll();
            errorTorg.SetError(textTorgMinLevel, "Уровень вещи - это целое число от 1 до 34, например, 10");
        }
    }
}
