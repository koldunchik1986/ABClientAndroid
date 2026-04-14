using System.Text.RegularExpressions;
using ABClient.Helpers;

namespace ABClient.PostFilter
{
    using System;
    using System.Text;
    using MyHelpers;
    using Properties;

    internal static partial class Filter
    {
        /*
        private static byte[] AddJson(byte[] array)
        {
            var html = Russian.Codepage.GetString(array);
            html = Resources.json2 + " " + html;
            return Russian.Codepage.GetBytes(html);
        }
        */

        internal static byte[] PreProcess(string address, byte[] array)
        {
            if (string.IsNullOrEmpty(address))
                return null;

            // Логируем все POST-запросы (тела запросов) для отладки
            if (array != null && array.Length > 0)
            {
                try
                {
                    var postBodyPreview = Helpers.Russian.Codepage.GetString(array);
                    if (postBodyPreview.Length > 150) postBodyPreview = postBodyPreview.Substring(0, 150) + "...";
                    AppLog.d("Filter.PreProcess", "POST_BODY: address=" + address + " body=" + postBodyPreview);
                }
                catch { }
            }

            // Перехват команды !train из чата — на уровне прокси.
            // Когда пользователь вводит "!train 40479" в чат, браузер отправляет POST на ch.php
            // с текстом сообщения. Здесь перехватываем: если text начинается с "!train",
            // выполняем обучение нейросети и блокируем отправку на сервер (возвращаем null).
            // Аналог Android: команда обрабатывается локально, на сервер не уходит.
            // ВАЖНО: сообщение-ответ содержит "[train]" (не "!train"), чтобы не попасть в цикл.
            if (array != null && array.Length > 0 && address.Contains("ch.php"))
            {
                try
                {
                    var postBody = Helpers.Russian.Codepage.GetString(array);
                    // Формат POST: ...&text=%21train+40479... или text=!train+40479
                    // Проверяем только поле text=, не sbmsg (sbmsg=Подача запроса от авто-ответа)
                    var textPos = postBody.IndexOf("text=", StringComparison.Ordinal);
                    if (textPos >= 0)
                    {
                        var textValue = postBody.Substring(textPos + "text=".Length);
                        var ampPos = textValue.IndexOf('&');
                        if (ampPos >= 0)
                            textValue = textValue.Substring(0, ampPos);

                        // Проверяем только %21train (!train) — НЕ перехватываем если это sbmsg авто-ответ
                        if (textValue.StartsWith("%21train", StringComparison.OrdinalIgnoreCase) ||
                            textValue.StartsWith("!train", StringComparison.OrdinalIgnoreCase))
                        {
                            // URL-decode: + → пробел, %20 → пробел, %21 → !
                            string decodedText;
                            try
                            {
                                decodedText = System.Uri.UnescapeDataString(textValue.Replace('+', ' ')).Trim();
                            }
                            catch (System.UriFormatException)
                            {
                                decodedText = textValue.Replace('+', ' ').Replace("%21", "!").Trim();
                            }

                            if (decodedText.StartsWith("!train", StringComparison.OrdinalIgnoreCase))
                            {
                                AppLog.i("Filter.PreProcess", "!TRAIN_INTERCEPTED: text=" + decodedText);
                                HandleTrainCommand(decodedText);
                                return null;
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    AppLog.e("Filter.PreProcess", "!TRAIN_CHECK_FAILED", ex);
                }
            }

            /*
            if (address.Equals("http://forum.neverlands.ru/action/"))
            {
                var html = Russian.Codepage.GetString(array);
                var textOrg = HelperStrings.SubString(html, "&reply_mess=", "&s=");
                if (!string.IsNullOrEmpty(textOrg))
                {
                    //var textOrgDecoded = HelperConverters.NickDecode(textOrg);
                    string[] rug = 
                        { "Ёбаный в рыло! Ты конченый абшник", "Ты засраный хуедав моржовый", "пиздюк блошиный на инфу смотри )))", "Ты пиздоебательный недоебок", "Жопосер перепиздюханный ботами",
                          "Ебать тебя немытым свитком!", "оркский залупенец! ко мне гони!", "Пиздострадатель???", "Ебаны с хуем во лбу", "Пиздоебливая абешка", "Ваш глава - пиздотёрый мудозвон )))",
                          "Пиздоноздря, ты лижешь у админов? )", "пиздячина хуерыльная", "ЧС тебе, хуидло!", "Боеаую тебе в пиздилищу", "Хуилище", "Обезьянья шишка", "Ебливая залупа!", "Ты ставленый раком пиздосос",
                          "куроёб ты мальчик", "пиздовый хуебун", "пиздаш долбанный ограми", "давно боевые не ловил пиздопроситель сучий", "Пиздалон хуебарный держи подарок", "Пиздоворот захуяченный", "хуярез мандавошный",
                          "пиздасер безжопый, иди на аб", "жополиз пиздадавленный", "долбоебатина федя", "держи подарок, пиздулия заштопанная", "ты клоповыёбистый ублюдоёб и клан твой такой же", "Пиздоглист",
                          "пиздолиз шпокнутый", "пиздень напиханная ботами", "Ялдак )", "пиздокопатель с рынка )", "восмьиконечная хуюла Форпоста", "вертохуй из клана вертохуев", "пиздоглазая нубятина",
                          "хуеворот пиздотыренный", "пиздосербало хуетертое", "хуебур пиздуянистый", "пиздоверзилище кукуйское", "пиздюшки тебе", "двуголовое хуило ты в ЧС!",
                          "Пиздодырявина )))", "пиздожал, ты мою инфу видел?", "хуеблядский нубяра", "пиздоногая блядевина еби тебя конем до селезенок", "грушу тебе в пизду и боевую туда же",
                          "хуерык одноногий блаж пей )))", "глиста пиздодырная из клана Глист )", "квесты делай гусеёб )", "пизда горбатая соси у топов", "ебал гужи!!!", "давно пизды не видал?", "хуй твой блошиный",
                          "хуевина", "на пизду тебе боевая???", "достига за пиздоеблю тебе", "мудями позванивай на осаду беги", "дави достиги хуем" };

                    var message = rug[Dice.Make(rug.Length)];
                    var textEncoded = HelperConverters.NickEncode(message);

                    var cd = AppVars.ServerDateTime;
                    for (var i = 0; i < 381; i++)
                    {
                        var ed = cd.AddDays(i);
                        var str = $"((++{AppVars.Profile.UserNick.ToUpperInvariant()}***{ed.ToString("yyyyMMdd")}++))";
                        var buffer = Encoding.UTF8.GetBytes(str);
                        var md5 = MD5.Create();
                        var hashbuffer = md5.ComputeHash(buffer);
                        var m = "ОЕАИНТСРВЛКМПУЯГ";
                        var sb = new StringBuilder();
                        for (var j = 0; j < 16; j++)
                        {
                            sb.Append(m[hashbuffer[j] >> 4]);
                            sb.Append(m[hashbuffer[j] & 0xF]);
                            if (((j + 1) % 4) == 0 && (j != 15))
                                sb.Append('-');
                        }

                        if (!sb.ToString().Equals(AppVars.Profile.UserKey.Trim(), StringComparison.CurrentCultureIgnoreCase))
                            continue;

                        cd = DateTime.MinValue;
                        break;
                    }

                    if (cd > DateTime.MinValue)
                    {
                        html = html.Replace(textOrg, textEncoded);
                        return Russian.Codepage.GetBytes(html);
                    }
                }
            }

            //var htmlx = Russian.Codepage.GetString(array);
            //File.WriteAllText("os1$.txt", htmlx);
            */

            return array;
        }

        internal static byte[] Process(string address, byte[] array)
        {
            if (string.IsNullOrEmpty(address))
                return null;

            var html = Russian.Codepage.GetString(array);

            if (address.Contains(".js"))
            {
                if (address.Contains("/js/hp.js"))
                    return HpJs(array);

                // 2/11/2017 - <SCRIPT src="/js/map.js?v=4"></SCRIPT>
                if (address.Contains("/js/map.js"))
                    return MapJs(array);

                if (address.Contains("/arena"))
                    return ArenaJs();

                /*
                if (address.Contains("/tarena") ||
                    address.Contains("/castle") ||
                    address.Contains("/tower") ||
                    address.Contains("/outpost") ||
                    address.Contains("/cityhall") ||
                    address.Contains("/clanhall"))
                    return AddJson(array);
                    */

                if (address.EndsWith("/js/game.js", StringComparison.OrdinalIgnoreCase))
                {
                    return GameJs(array);
                }

                if (address.IndexOf("pinfo_v01.js", StringComparison.Ordinal) != -1)
                {
                    return PinfoJs(array);
                }

                if (address.Contains("/js/fight_v"))
                {
                    return FightJs(array);
                }

                if (address.Contains("/js/building"))
                {
                    return BuildingJs(array);
                }

                if (address.EndsWith("/js/hpmp.js", StringComparison.OrdinalIgnoreCase))
                {
                    return HpmpJs();
                }

                if (address.EndsWith("/ch/ch_msg_v01.js", StringComparison.OrdinalIgnoreCase))
                {
                    return ChMsgJs(array);
                }

                if (address.EndsWith("/js/pv.js", StringComparison.OrdinalIgnoreCase))
                {
                    return PvJs(array);
                }

                if (address.EndsWith("/ch/ch_list.js", StringComparison.OrdinalIgnoreCase))
                {
                    return ChListJs();
                }

                if (address.EndsWith("/js/svitok.js", StringComparison.OrdinalIgnoreCase))
                {
                    return SvitokJs(array);
                }

                if (address.EndsWith("/js/slots.js", StringComparison.OrdinalIgnoreCase))
                {
                    return SlotsJs(array);
                }

                if (address.IndexOf("/js/logs", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    return LogsJs(array);
                }

                if (address.IndexOf("/js/shop", StringComparison.OrdinalIgnoreCase) != -1)
                {
                    return ShopJs(array);
                }

                if (address.IndexOf("/js/forum/forum_topic.js", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    return ForumTopicJs(array);
                }
            }

            var pos1 = address.IndexOf(".js", StringComparison.CurrentCultureIgnoreCase);
            if (pos1 < 0)
            {
                var pos2 = address.IndexOf(".swf", StringComparison.CurrentCultureIgnoreCase);
                if (pos2 < 0)
                {
                    Log.Write(address, html);
                }
            }

            if (address.StartsWith("http://www.neverlands.ru/index.cgi", StringComparison.OrdinalIgnoreCase) ||
                address.Equals("http://www.neverlands.ru/", StringComparison.OrdinalIgnoreCase))
            {
                return IndexCgi(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/pinfo.cgi", StringComparison.OrdinalIgnoreCase))
            {
                return RemoveDoctype(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/pbots.cgi", StringComparison.OrdinalIgnoreCase))
            {
                return RemoveDoctype(array);
            }

            if (address.StartsWith("http://forum.neverlands.ru/", StringComparison.OrdinalIgnoreCase))
            {
                return RemoveDoctype(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/game.php", StringComparison.OrdinalIgnoreCase))
            {
                return GamePhp(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/main.php", StringComparison.OrdinalIgnoreCase))
            {
                AppVars.NextCheckNoConnection = DateTime.Now.AddMinutes(5);
                return MainPhp(address, array);
            }

            if (address.StartsWith("http://www.neverlands.ru/ch/msg.php", StringComparison.OrdinalIgnoreCase))
            {
                return MsgPhp(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/ch/but.php", StringComparison.OrdinalIgnoreCase))
            {
                return ButPhp(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/gameplay/trade.php", StringComparison.OrdinalIgnoreCase))
            {
                return AppVars.Profile.TorgActive ? TradePhp(array) : array;
            }

            if (address.StartsWith("http://www.neverlands.ru/gameplay/ajax/map_act_ajax.php", StringComparison.OrdinalIgnoreCase))
            {
                return MapActAjaxPhp(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/gameplay/ajax/fish_ajax.php", StringComparison.OrdinalIgnoreCase))
            {
                return FishAjaxPhp(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/gameplay/ajax/shop_ajax.php", StringComparison.OrdinalIgnoreCase))
            {
                return ShopAjaxPhp(array);
            }

            if (address.StartsWith("http://www.neverlands.ru/gameplay/ajax/roulette_ajax.php", StringComparison.OrdinalIgnoreCase))
            {
                return RouletteAjaxPhp(array);
            }
            
            if (address.StartsWith("http://www.neverlands.ru/ch.php?lo=", StringComparison.OrdinalIgnoreCase))
            {
                return ChRoomPhp(array);
            }

            if (address.IndexOf("/ch.php?0", StringComparison.OrdinalIgnoreCase) != -1)
            {
                return ChZero(array);
            }

            if (address.StartsWith(Resources.AddressPInfo))
            {
                return Pinfo(array);
            }

            return array;
        }

        private static string BuildRedirect(string description, string link)
        {
            var sb = new StringBuilder(HelperErrors.Head());
            sb.Append(description);
            sb.Append(
                @"<script language=""JavaScript"">" +
                @"  window.location = """);
            sb.Append(link);
            sb.Append(
                @""";</script></body></html>");
            return sb.ToString();
        }

        private static readonly Regex DocType = new Regex(@"<!DOCTYPE[^>[]*(\[[^]]*\])?>");

        private static string RemoveDoctype(string html)
        {
            return DocType.Replace(html, string.Empty);
        }

        private static byte[] RemoveDoctype(byte[] array)
        {
            var html = Russian.Codepage.GetString(array);
            html = RemoveDoctype(html);
            return Russian.Codepage.GetBytes(html);
        }

        /// <summary>
        /// Обработка команды !train XXXXX — дообучение нейросети капчи.
        /// Вызывается из PreProcess при перехвате POST чата с текстом "!train".
        /// Аналог Android: NeuroBase.Train() через чат-команду.
        /// Зависимости: NeuroBase.Train(), NeuroBase.SaveCustomBase(), AppLog.
        /// </summary>
        private static void HandleTrainCommand(string text)
        {
            var parts = text.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2)
            {
                WriteTrainResult("!train: укажите правильные цифры, например: !train 26359");
                return;
            }

            var digits = parts[1].Trim();
            foreach (var c in digits)
            {
                if (!char.IsDigit(c))
                {
                    WriteTrainResult("!train: только цифры, например: !train 26359");
                    return;
                }
            }

            if (digits.Length < 4 || digits.Length > 7)
            {
                WriteTrainResult("!train: нужно 4-7 цифр, например: !train 26359");
                return;
            }

            try
            {
                Neuro.NeuroBase.Train(digits);
                WriteTrainResult("!train OK: обучено \"" + digits + "\" (векторов в базе: " + Neuro.NeuroBase.NumNodes() + ")");
            }
            catch (Exception ex)
            {
                WriteTrainResult("!train ERROR: " + ex.Message);
                AppLog.e("Filter.HandleTrainCommand", "TRAIN_FAILED", ex);
            }
        }

        /// <summary>
        /// Вывод результата команды !train в чат (через BeginInvoke на UI-поток).
        /// </summary>
        private static void WriteTrainResult(string message)
        {
            try
            {
                if (AppVars.MainForm != null)
                {
                    AppVars.MainForm.BeginInvoke(
                        new ABForms.UpdateWriteRealChatMsgDelegate(AppVars.MainForm.WriteMessageToChat),
                        new object[] { message });
                }
            }
            catch (InvalidOperationException)
            {
            }
        }
    }
}