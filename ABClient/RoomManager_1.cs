
                if (AppVars.DoSelfNevid && !AppVars.SelfNevidNeed && pars[1].Equals(AppVars.Profile.UserNick, StringComparison.CurrentCultureIgnoreCase))
                {
                    AppVars.SelfNevidNeed = true;
                    AppVars.SelfNevidSkl = userSklStatus;
                    AppVars.SelfNevidStage = 0;
                    FormMain.ReloadMainFrame();
                }

                if (AppVars.Profile.DoChatLevels)
                    ChatUsersManager.AddUser(new ChatUser(userName, userLevel, userSign, userStatus));

                if (ContactsManager.GetClassIdOfContact(userName) == 1)
                    enemyAttack.Add(userName);

                if (pars[3].StartsWith("pv", StringComparison.OrdinalIgnoreCase))
                {
                    var pos = pars[3].LastIndexOf(';');
                    if (pos == -1)
                    {
                        continue;
                    }

                    var tsmi = new ToolStripMenuItem(userName)
                    {
                        Image = Properties.Resources._16x16_private,
                        ToolTipText = pars[3].Substring(pos + 1),
                        AutoToolTip = true
                    };
                    if (AppVars.MainForm != null)
                    {
                        tsmi.Click += AppVars.MainForm.OnPvFastToolStripMenuItemClick;
                    }

                    pvlist.Add(tsmi);
                }

                if (string.IsNullOrEmpty(pars[6]) || pars[6].Equals("0", StringComparison.Ordinal))
                    continue;

                var ntr = new int[4];
                var str = new[] { "легкая", "средняя", "тяжелая", "боевая" };
                var travmtime = new int[4];
                var tr = pars[6].Split(new []{", "}, StringSplitOptions.RemoveEmptyEntries);
                for (var t = 0; t < tr.Length; t++)
                {
                    var ttt = tr[t];
                    for (var j = 0; j < 4; j++)
                    {
                        if (!AppVars.Profile.CureEnabled[j])
                        {
                            continue;
                        }

                        if (ttt.IndexOf(str[j], StringComparison.OrdinalIgnoreCase) == -1)
                        {
                            continue;
                        }

                        int ttime;
                        int min;
                        int hour;
                        var tan = ttt.Split(' ');
                        switch (tan.Length)
                        {
                            case 4:
                                if (!int.TryParse(tan[2], out min))
                                {
                                    min = 0;
                                }

                                if (tan[3].Equals("ч", StringComparison.OrdinalIgnoreCase))
                                {
                                    ttime = min * 60;
                                }
                                else
                                {
                                    ttime = min;
                                }

                                break;
                            case 5:
                                if (!int.TryParse(tan[3], out min))
                                {
                                    min = 0;
                                }

                                if (tan[4].Equals("ч", StringComparison.OrdinalIgnoreCase))
                                {
                                    ttime = min * 60;
                                }
                                else
                                {
                                    ttime = min;
                                }

                                break;
                            case 6:
                                if (!int.TryParse(tan[4], out min))
                                {
                                    min = 0;
                                }

                                if (tan[5].Equals("ч", StringComparison.OrdinalIgnoreCase))
                                {
                                    ttime = min * 60;
                                }
                                else
                                {
                                    if (!int.TryParse(tan[2], out hour))
                                    {
                                        hour = 0;
                                    }

                                    ttime = (hour * 60) + min;
                                }

                                break;
                            case 7:
                                if (!int.TryParse(tan[3], out hour))
                                {
                                    hour = 0;
                                }

                                if (!int.TryParse(tan[5], out min))
                                {
                                    min = 0;
                                }

                                ttime = (hour * 60) + min;
                                break;
                            case 8:
                                if (!int.TryParse(tan[4], out hour))
                                {
                                    hour = 0;
                                }

                                if (!int.TryParse(tan[6], out min))
                                {
                                    min = 0;
                                }

                                ttime = (hour * 60) + min;
                                break;
                            default:
                                ttime = 0;
                                break;
                        }

                        if (travmtime[j] < ttime)
                        {
                            travmtime[j] = ttime;
                        }

                        ntr[j]++;
                    }
                }

                if ((ntr[0] + ntr[1] + ntr[2] + ntr[3]) <= 0) continue;
                var sb = new StringBuilder();
                sb.Append(userName);
                sb.Append(" [");
                sb.Append(userLevel);
                sb.Append("]: ");
                if (ntr[0] > 0 && ((ntr[1] + ntr[2] + ntr[3]) == 0))
                {
                    if (ntr[0] == 1)
                    {
                        sb.Append("легкая");
                    }
                    else
                    {
                        sb.Append(ntr[0]);
                        sb.Append(" легких");
                    }

                    sb.Append(' ');
                    sb.Append(HelperConverters.MinsToStr(travmtime[0]));
                }
                else
                {
                    if (ntr[1] > 0 && ((ntr[0] + ntr[2] + ntr[3]) == 0))
                    {
                        if (ntr[1] == 1)
                        {
                            sb.Append("средняя");
                        }
                        else
                        {
                            sb.Append(ntr[1]);
                            sb.Append(" средних");
                        }

                        sb.Append(' ');
                        sb.Append(HelperConverters.MinsToStr(travmtime[1]));
                    }
                    else
                    {
                        if (ntr[2] > 0 && ((ntr[0] + ntr[1] + ntr[3]) == 0))
                        {
                            if (ntr[2] == 1)
                            {
                                sb.Append("тяжелая");
                            }
                            else
                            {
                                sb.Append(ntr[2]);
                                sb.Append(" тяжелых");
                            }

                            sb.Append(' ');
                            sb.Append(HelperConverters.MinsToStr(travmtime[2]));
                        }
                        else
                        {
                            if (ntr[3] > 0 && ((ntr[0] + ntr[1] + ntr[2]) == 0))
                            {
                                if (ntr[3] == 1)
                                {
                                    sb.Append("боевая");
                                }
                                else
                                {
                                    sb.Append(ntr[3]);
                                    sb.Append(" боевых");
                                }

                                sb.Append(' ');
                                sb.Append(HelperConverters.MinsToStr(travmtime[3]));
                            }
                            else
                            {
                                for (var j = 0; j < 4; j++)
                                {
                                    if (ntr[j] == 0)
                                    {
                                        sb.Append('-');
                                    }
                                    else
                                    {
                                        sb.Append(ntr[j]);
                                    }

                                    if (j < 3)
                                    {
                                        sb.Append('/');
                                    }
                                }

                                var travmmax = 0;
                                for (var j = 3; j >= 0; j--)
                                {
                                    if (travmtime[j] == 0)
                                    {
                                        continue;
                                    }

                                    travmmax = travmtime[j];
                                    break;
                                }

                                sb.Append(' ');
                                sb.Append(HelperConverters.MinsToStr(travmmax));
                            }
                        }
                    }
                }

                if (sbtx.Length > 0)
                {
                    sbtx.Append(':');
                }

                sbtx.Append(userName);
                var trmi = new ToolStripMenuItem(sb.ToString())
                {
                    Image = (ntr[3] > 0
                                 ? Properties.Resources._15x12_tr4
                                 : (ntr[2] > 0
                                        ? Properties.Resources._15x12_tr3
                                        : (ntr[1] > 0
                                               ? Properties.Resources._15x12_tr2
                                               : Properties.Resources._15x12_tr1))),
                    ImageScaling = ToolStripItemImageScaling.None
                };

                if (AppVars.Profile.CureDisabledLowLevels &&
                    (pars[2].Equals("0", StringComparison.Ordinal) ||
                      pars[2].Equals("1", StringComparison.Ordinal) ||
                      pars[2].Equals("2", StringComparison.Ordinal) ||
                      pars[2].Equals("3", StringComparison.Ordinal) ||
                      pars[2].Equals("4", StringComparison.Ordinal)))
                {
                    trmi.Enabled = false;
                }
                else
                {
                    var travmtype = -1;
                    if (ntr[3] > 0)
                    {
                        nt[3]++;
                        travmtype = 3;
                    }
                    else
                    {
                        if (ntr[2] > 0)
                        {
                            nt[2]++;
                            if (travmtype == -1) travmtype = 2;
                        }
                        else
                        {
                            if (ntr[1] > 0)
                            {
                                nt[1]++;
                                if (travmtype == -1) travmtype = 1;
                            }
                            else
                            {
                                nt[0]++;
                                if (travmtype == -1) travmtype = 0;
                            }
                        }
                    }

                    var nametr = new[] { "легкую", "среднюю", "тяжелую", "боевую" };
                    if (travmtype != -1)
                    {
                        if (sbtt[travmtype].Length > 0)
                        {
                            sbtt[travmtype].Append(':');
                        }

                        sbtt[travmtype].Append(userName);
                    }

                    sb.Length = 0;
                    sb.Append(userName);
                    sb.Append(" [");
                    sb.Append(userLevel);
                    sb.Append(']');
                    var trmi1 = new ToolStripMenuItem(sb.ToString()) { Image = Properties.Resources._16x16_private, Tag = userName };
                    if (AppVars.MainForm != null) trmi1.Click += AppVars.MainForm.OnTravmFastToolStripMenuItemClick;
                    trmi.DropDownItems.Add(trmi1);
                    var trmi3 = new ToolStripMenuItem("Открыть инфу") { Image = Properties.Resources._16x16_info, Tag = userName };
                    if (AppVars.MainForm != null) trmi3.Click += AppVars.MainForm.OnTravmInfoToolStripMenuItemClick;
                    trmi.DropDownItems.Add(trmi3);
                    trmi.DropDownItems.Add(new ToolStripSeparator());

                    var icontr = new[]
                                     {
                                         Properties.Resources._15x12_tr1,
                                         Properties.Resources._15x12_tr2,
                                         Properties.Resources._15x12_tr3,
                                         Properties.Resources._15x12_tr4
                                     };
                    for (var t = 0; t < 4; t++)
                    {
                        if (ntr[t] <= 0) continue;
                        var tst = new ToolStripMenuItem("Лечить " + nametr[t] + " травму")
                        {
                            Image = icontr[t],
                            ImageScaling = ToolStripItemImageScaling.None,
                            Tag = userName + ":" + (t + 1)
                        };
                        if (AppVars.MainForm != null) tst.Click += FormMain.OnTravmCureToolStripMenuItemClick;
                        trmi.DropDownItems.Add(tst);
                    }

                    trmi.DropDownItems.Add(new ToolStripSeparator());

                    var trmi2 = new ToolStripMenuItem("Отправить рекламу") { Tag = userName };
                    if (AppVars.MainForm != null) trmi2.Click += FormMain.OnTravmAdvToolStripMenuItemClick;
                    trmi.DropDownItems.Add(trmi2);
                }

                trlist.Add(trmi);
            }