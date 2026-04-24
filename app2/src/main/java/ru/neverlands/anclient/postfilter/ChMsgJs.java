package ru.neverlands.anclient.postfilter;

import ru.neverlands.anclient.utils.AppLog;

import ru.neverlands.anclient.utils.Russian;

public class ChMsgJs {
    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            // Инъекция Android‑моста вместо frameset API (в WebView нет настоящих frames).
            StringBuilder inject = new StringBuilder();
            inject.append("window.external = window.AndroidBridge || window.external;\n");
            // Аналоги переходов/фокуса/добавления текста для чата.
            inject.append("function androidChatLoadRefr(u){ if(window.AndroidBridge && AndroidBridge.loadFrame){ AndroidBridge.loadFrame('ch_refr', u); } else { location=u; } }\n");
            inject.append("function androidChatFocus(){ if(window.AndroidBridge && AndroidBridge.chatFocus){ AndroidBridge.chatFocus(); } }\n");
            inject.append("function androidChatAppend(t){ if(window.AndroidBridge && AndroidBridge.chatAppend){ AndroidBridge.chatAppend(t); } }\n");
            inject.append("if (typeof top !== 'undefined') {\n");
            // Мосты для say_private/say_to.
            inject.append("  if (!top.say_private) top.say_private = function(n){ if(window.AndroidBridge && AndroidBridge.chatSayPrivate){ AndroidBridge.chatSayPrivate(n); } };\n");
            inject.append("  if (!top.say_to) top.say_to = function(n){ if(window.AndroidBridge && AndroidBridge.chatSayTo){ AndroidBridge.chatSayTo(n); } };\n");
            inject.append("}\n");

            html = inject.toString() + html;

            // Замены frameset‑зависимых вызовов на Android‑мосты.
            html = html.replace(
                    "top.frames['ch_refr'].location = '/ch.php?a=ign&s=1&u='+nick;",
                    "androidChatLoadRefr('/ch.php?a=ign&s=1&u='+nick);"
            );
            html = html.replace(
                    "top.frames['ch_buttons'].document.FBT.text.focus();",
                    "androidChatFocus();"
            );
            html = html.replace(
                    "top.frames['ch_buttons'].document.FBT.text.value += ' :'+smile+': ';",
                    "androidChatAppend(' :'+smile+': ');"
            );
            html = html.replace(
                    "window.scrollBy(0,65000);",
                    "window.scrollBy(0,65000); if(window.AndroidBridge && AndroidBridge.chatUpdated){ AndroidBridge.chatUpdated(); }"
            );
            // Пропускаем сообщения через ChatFilter (XP/лут/системные, автоответ и т.д.).
            html = html.replace(
                    "s += txt + \"<BR>\";",
                    "s += (window.AndroidBridge && AndroidBridge.chatFilter ? AndroidBridge.chatFilter(txt) : txt) + \"<BR>\";"
            );
            html = html.replace(
                    "if(user2 != '') msgp[1] = msgp[1].replace('<SPAN>','<SPAN alt=\"%'+user2+'\">');",
                    "if(user2 != '') { if (txt.indexOf('%<clan>') >= 0) { msgp[1] = msgp[1].replace('<SPAN>','<SPAN alt=\"%%'+user2+'\">'); } " +
                            "else if (txt.indexOf('%<pair>') >= 0) { msgp[1] = msgp[1].replace('<SPAN>','<SPAN alt=\"%%%'+user2+'\">'); } " +
                            "else { msgp[1] = msgp[1].replace('<SPAN>','<SPAN alt=\"%'+user2+'\">'); } }"
            );

            // Фикс клика по нику: корректно обрабатываем alt/title и префиксы %clan/%pair.
            String fixClick =
                    "\n(function(){\n" +
                    "  if (typeof to_what_who === 'function') {\n" +
                    "    var _orig = to_what_who;\n" +
                    "    to_what_who = function(e){\n" +
                    "      e = e || window.event;\n" +
                    "      var o = e.target || e.srcElement;\n" +
                    "      if (o && o.tagName == 'SPAN') {\n" +
                    "        var login = o.innerHTML;\n" +
                    "        var alt = null;\n" +
                    "        try { alt = (o.getAttribute ? (o.getAttribute('alt') || o.getAttribute('title')) : null); } catch(ex) {}\n" +
                    "        if (alt != null && alt.length > 0) login = alt;\n" +
                    "        var clean = login.replace(/^%+/, '');\n" +
                    "        var parent = o.parentElement || o.parentNode;\n" +
                    "        var text = parent && parent.textContent ? parent.textContent : '';\n" +
                    "        var html = parent && parent.innerHTML ? parent.innerHTML : '';\n" +
                    "        var isClan = /\\bclan\\b/i.test(text) || /%<clan>/i.test(html);\n" +
                    "        var isPair = /\\bpair\\b/i.test(text) || /%<pair>/i.test(html);\n" +
                    "        var prefixCount = 0;\n" +
                    "        while (login.charAt(prefixCount) == '%') prefixCount++;\n" +
                    "        if (login.charAt(0) == '%') {\n" +
                    "          if (prefixCount >= 3) login = '%%%' + clean;\n" +
                    "          else if (prefixCount == 2) login = '%%' + clean;\n" +
                    "          else if (isPair) login = '%%%' + clean;\n" +
                    "          else if (isClan) login = '%%' + clean;\n" +
                    "          else login = '%' + clean;\n" +
                    "          if (top && top.say_private) { top.say_private(login); return false; }\n" +
                    "        } else {\n" +
                    "          if (top && top.say_to) { top.say_to(login); return false; }\n" +
                    "        }\n" +
                    "      }\n" +
                    "      return false;\n" +
                    "    };\n" +
                    "  }\n" +
                    "})();\n";
            html = html + fixClick;

            return Russian.getBytes(html);
        } catch (Exception e) {
            AppLog.e("ChMsgJs", "Error processing ch_msg_v01.js", e);
            return array;
        }
    }
}
