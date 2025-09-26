
            if (trlist.Count > 0)
            {
                sbt.Append(trlist.Count);
                sbt.Append(": ");
                for (var j = 0; j < 4; j++)
                {
                    if (nt[j] == 0)
                    {
                        sbt.Append('-');
                    }
                    else
                    {
                        sbt.Append(nt[j]);
                    }

                    if (j < 3)
                    {
                        sbt.Append('/');
                    }
                }

                var trek = new ToolStripMenuItem("Реклама")
                {
                    Image = Properties.Resources._16x16_private,
                };

                var namett = new[] { "легкие", "средние", "тяжелые", "боевые" };
                for (var i = 0; i < 4; i++)
                {
                    if (sbtt[i].Length <= 0) continue;
                    var tt = new ToolStripMenuItem("Реклама тем, у кого " + namett[i]) { Image = Properties.Resources._16x16_private, Tag = (i + 1) + ":" + sbtt[i] };
                    if (AppVars.MainForm != null) tt.Click += FormMain.OnTravmAskToolStripMenuItemClick;
                    trek.DropDownItems.Add(tt);
                }

                var trall = new ToolStripMenuItem("Реклама всем травмированным")
                {
                    Image = Properties.Resources._16x16_private,
                    Tag = sbtx.ToString()
                };

                if (AppVars.MainForm != null) trall.Click += FormMain.OnTravmAdvAllToolStripMenuItemClick;
                trek.DropDownItems.Add(trall);

                var trallchat = new ToolStripMenuItem("Реклама в общий чат");
                if (AppVars.MainForm != null) trallchat.Click += FormMain.OnTravmChatToolStripMenuItemClick;
                trek.DropDownItems.Add(trallchat);

                trlist.Add(trek);
            }

            try
            {
                if (AppVars.MainForm != null)
                    AppVars.MainForm.BeginInvoke(
                        new UpdateRoomDelegate(AppVars.MainForm.UpdateRoom),
                        new object[] { pvlist.ToArray(), sbt.ToString(), trlist.ToArray() });
            }
            catch (InvalidOperationException)
            {
            }

            resultFilterProcRoom.NumCharsInRoom = par.Length;
            if (enemyAttack.Count > 0)
            {
                var filtredenemyAttack = new List<string>();
                foreach (var nick in filtredenemyAttack)
                {
                    if (IsCharInBlackList(nick))
                        continue;

                    filtredenemyAttack.Add(nick);
                }

                resultFilterProcRoom.EnemyAttack = filtredenemyAttack.Count > 0 ? enemyAttack[Dice.Make(filtredenemyAttack.Count)] : enemyAttack[Dice.Make(enemyAttack.Count)];
            }

            return resultFilterProcRoom;
        }

        private static void FilterGetWalkers(string html)
        {
            if (!AppVars.DoShowWalkers)
            {
                return;
            }

            var mLocnow = HelperStrings.SubString(html, "<font class=placename><b>", "</b>");
            if (string.IsNullOrEmpty(mLocnow))
            {
                return;
            }

            var arg = HelperStrings.SubString(html, "new Array(", ");");
            if (string.IsNullOrEmpty(arg))
            {
                return;
            }

            var par = arg.Split(new[] { @"""," }, StringSplitOptions.RemoveEmptyEntries);
            if (par.Length == 0)
            {
                return;
            }

            var mDnow = new Dictionary<string, string>();
            for (var i = 0; i < par.Length; i++)
            {
                var pararg = par[i].Substring(3, par[i].Length - 3);
                var pars = pararg.Split(':');
                if (pars.Length < 3)
                {
                    continue;
                }

                if (pars[1].IndexOf("<i>", StringComparison.OrdinalIgnoreCase) == -1)
                    mDnow.Add(pars[1], pararg);
            }

            if ((AppVars.Profile.MapLocation == AppVars.MyCoordOld) && (mLocnow == AppVars.MyLocOld))
            {
                var myDleft = new Dictionary<string, string>();
                var keyleft = AppVars.MyCharsOld.Keys;
                foreach (var kl in keyleft)
                {
                    if (kl != null && !mDnow.ContainsKey(kl))
                    {
                        if (kl.Equals(AppVars.Profile.UserNick, StringComparison.CurrentCultureIgnoreCase))
                        {
                            if (AppVars.MainForm != null)
                                AppVars.MainForm.WriteChatMsgSafe("<b><font color=#01A9DB>Мы ушли в невид</font></b>");
                        }
                        else
                            myDleft.Add(kl, AppVars.MyCharsOld[kl]);
                    }
                }

                var myDcome = new Dictionary<string, string>();
                var keycome = mDnow.Keys;
                foreach (var kc in keycome)
                {
                    if (kc != null && !AppVars.MyCharsOld.ContainsKey(kc))
                    {
                        if (kc.Equals(AppVars.Profile.UserNick, StringComparison.CurrentCultureIgnoreCase))
                        {
                            if (AppVars.MainForm != null)
                                AppVars.MainForm.WriteChatMsgSafe("<b><font color=#DF0101>Мы вышли из невида!</font></b>");
                        }
                        else
                            myDcome.Add(kc, mDnow[kc]);
                    }
                }

                var diffn = AppVars.MyNevids - AppVars.MyNevidsOld;
                if ((myDleft.Count != 0) || (myDcome.Count != 0) || (diffn != 0))
                {
                    var sb = new StringBuilder();
                    var i = 0;
                    if (diffn > 0)
                    {
                        i = 1;
                        sb.Append("<font color=#5D7C91><b>");
                        if (diffn == 1)
                        {
                            sb.Append("Невидимка");
                        }
                        else
                        {
                            sb.Append(diffn);
                            sb.Append(" невидимок");
                        }

                        sb.Append("</b></font>");
                    }

                    if (myDcome.Count > 0)
                    {
                        keycome = myDcome.Keys;

                        foreach (var kc in keycome)
                        {
                            if (i > 0)
                            {
                                sb.Append(", ");
                            }

                            i++;
                            sb.Append(HtmlChar(myDcome[kc]));
                        }
                    }

                    if (i > 0)
                    {
                        sb.Append(i > 1 ? " приходят в локацию" : " приходит в локацию");
                    }

                    AppVars.MyWalkers1 = sb.ToString();
                    sb.Length = 0;
                    i = 0;
                    if (diffn < 0)
                    {
                        i = 1;
                        sb.Append("<font color=#5D7C91><b>");
                        if (diffn == -1)
                        {
                            sb.Append("Невидимка");
                        }
                        else
                        {
                            sb.Append(-diffn);
                            sb.Append(" невидимок");
                        }

                        sb.Append("</b></font>");
                    }

                    if (myDleft.Count > 0)
                    {
                        keyleft = myDleft.Keys;

                        foreach (var kl in keyleft)
                        {
                            if (i > 0)
                            {
                                sb.Append(", ");
                            }

                            i++;
                            sb.Append(HtmlChar(myDleft[kl]));
                        }
                    }

                    if (i > 0)
                    {
                        sb.Append(i > 1 ? " покидают локацию" : " покидает локацию");
                    }

                    AppVars.MyWalkers2 = sb.ToString();
                }
            }

            AppVars.MyCoordOld = AppVars.Profile.MapLocation;
            AppVars.MyLocOld = mLocnow;
            AppVars.MyCharsOld.Clear();
            AppVars.MyCharsOld = new Dictionary<string, string>(mDnow);
            AppVars.MyNevidsOld = AppVars.MyNevids;

            if (!string.IsNullOrEmpty(AppVars.MyWalkers1))
            {
                EventSounds.PlayAlarm();
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateChatDelegate(AppVars.MainForm.UpdateChat), AppVars.MyWalkers1);
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppVars.MyWalkers1 = string.Empty;
            }

            if (!string.IsNullOrEmpty(AppVars.MyWalkers2))
            {
                try
                {
                    if (AppVars.MainForm != null)
                    {
                        AppVars.MainForm.BeginInvoke(
                            new UpdateChatDelegate(AppVars.MainForm.UpdateChat),
                            new object[] { AppVars.MyWalkers2 });
                    }
                }
                catch (InvalidOperationException)
                {
                }

                AppVars.MyWalkers2 = string.Empty;
            }
        }

        private static string HtmlChar(string schar)
        {
            var strArray = schar.Split(new[] { ':' });
            var nnSec = strArray[1];
            var login = strArray[1];
            while (nnSec.Contains("+"))
            {
                nnSec = nnSec.Replace("+", "%2B");
            }

            if (login.Contains("<i>"))
            {
                login = login.Replace("<i>", String.Empty);
                login = login.Replace("</i>", String.Empty);
                nnSec = nnSec.Replace("<i>", String.Empty);
                nnSec = nnSec.Replace("</i>", String.Empty);
            }

            var ss = string.Empty;
            var altadd = string.Empty;
            if (strArray[3].Length > 1)
            {
                var signArray = strArray[3].Split(new[] { ';' });
                if (signArray[2].Length > 1)
                {
                    altadd = " (" + signArray[2] + ")";
                }

                ss =
                    "<img src=http://image.neverlands.ru/signs/" +
                    signArray[0] +
                    @" width=15 height=12 align=absmiddle alt=""" +
                    signArray[1] +
                    altadd +
                    @""">&nbsp;";
            }

            var sleeps = string.Empty;
            if (strArray[4].Length > 1)
            {
                sleeps =
                    @"<img src=http://image.neverlands.ru/signs/molch.gif width=15 height=12 border=0 alt=""" +
                    strArray[4] +
                    @""" align=absmiddle>";
            }

            var ign = string.Empty;
            if (strArray[5] == "1")
            {
                ign =
                    @"<a href=""javascript:ch_clear_ignor('" +
                    login +
                    @"');""><img src=http://image.neverlands.ru/signs/ignor/3.gif width=15 height=12 border=0 alt=""Снять игнорирование""></a>";
            }

            var inj = string.Empty;
            if (strArray[6] != "0")
            {
                inj = @"<img src=http://image.neverlands.ru/chat/tr4.gif border=0 width=15 height=12 alt=""" +
                      strArray[6] +
                      @""" align=absmiddle>";

                if (strArray[6].Contains("боевая"))
                {
                    strArray[1] = @"<font color=""#666600"">" + strArray[1] + "</font>";
                }
                else
                {
                    if (strArray[6].Contains("тяжелая"))
                    {
                        strArray[1] = @"<font color=""#c10000"">" + strArray[1] + "</font>";
                    }
                    else
                    {
                        if (strArray[6].Contains("средняя"))
                        {
                            strArray[1] = @"<font color=""#e94c69"">" + strArray[1] + "</font>";
                        }
                        else
                        {
                            if (strArray[6].Contains("легкая"))
                            {
                                strArray[1] = @"<font color=""#ef7f94"">" + strArray[1] + "</font>";
                            }
                        }
                    }
                }
            }

            var psg = string.Empty;
            if (strArray[7] != "0")
            {
                var dilers = new[] { "", "Дилер", "", "", "", "", "", "", "", "", "", "Помощник дилера" };
                psg =
                    "<img src=http://image.neverlands.ru/signs/d_sm_" +
                    strArray[7] +
                    @".gif width=15 height=12 align=absmiddle border=0 alt=""" +
                    dilers[int.Parse(strArray[7])] +
                    @""">&nbsp;";
            }

            var align = string.Empty;
            if (strArray[8] != "0")
            {
                var signArray = strArray[8].Split(new[] { ';' });
                if (signArray.Length >= 2)
                {
                    align =
                        "<img src=http://image.neverlands.ru/signs/" +
                        signArray[0] +
                        @" width=15 height=12 align=absmiddle border=0 alt=""" +
                        signArray[1] +
                        @""">&nbsp";
                }
            }

            return
                @"<a href=""#"" onclick=""top.say_private('" +
                login +
                @"');""><img src=http://image.neverlands.ru/chat/private.gif width=11 height=12 border=0 align=absmiddle></a>&nbsp;" +
                psg +
                align +
                ss +
                @"<a class=""activenick"" href=""#"" onclick=""top.say_to('" +
                login +
                @"');""><font class=nickname><b>" +
                strArray[1] +
                "</b></a>[" +
                strArray[2] +
                @"]</font><a href=""http://www.neverlands.ru/pinfo.cgi?" +
                nnSec +
                @""" onclick=""window.open(this.href);""><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=absmiddle></a>" +
                sleeps +
                ign +
                inj;
        }
    }
}
