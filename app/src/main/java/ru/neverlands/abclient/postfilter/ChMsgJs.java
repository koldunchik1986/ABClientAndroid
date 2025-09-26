package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class ChMsgJs {
    public static byte[] process(byte[] array) {
        String js = Russian.getString(array);

        js = js.replace(
            "s += txt + \"<BR>\";",
            "s += AndroidBridge.ChatFilter(txt) + \"<BR>\";"
        );

        js = js.replace(
            ",65000);",
            ", 65000); AndroidBridge.ChatUpdated()"
        );

        // TODO: Исправить и раскомментировать проблемные замены
        /*
        js = js.replace(
            "msgp[2].replace(user,'<SPAN alt=\"%' + user2 + '\">' + user + '</SPAN>');",
            "msgp[2].replace(user,'<SPAN alt=\"%\' + user + \'\">' + user + '</SPAN>');"
        );

        js = js.replace(
            "msgp[2] = msgp[2].replace(' " + user, ' <SPAN alt=\"\'" + user2 + "\">' + user + '</SPAN>');",
            "msgp[2] = msgp[2].replace(' " + user, ' <SPAN alt=\"%\' + user + \'\">' + user + '</SPAN>');"
        );
        */

        js = js.replace(
            "login = login.replace ('%', '');",
            "top.frames['ch_buttons'].document.FBT.text.focus(); " +
            "var prompt = top.frames['ch_buttons'].document.FBT.text.value; " +
            "if (prompt.indexOf('%clan%') == 0 || prompt.indexOf('%pair%') == 0) { login = login.replace('%',''); login = login.replace('%',''); login = login.replace('%',''); top.frames['ch_buttons'].document.FBT.text.value = prompt + '%<' + login + '> '; return false; } else {" +
            "if (login.charAt(2) == '%'){ login = login.substr(3); top.frames['ch_buttons'].document.FBT.text.value = '%pair%%<' + login + '> ' + prompt; return false; } else " +
            "if (login.charAt(1) == '%'){ login = login.substr(2); top.frames['ch_buttons'].document.FBT.text.value = '%clan%%<' + login + '> ' + prompt; return false; } else login = login.substr(1); }"
        );

        js = js.replace("alt=", "title=");
        js = js.replace(".alt", ".title");

        js = js.replace(" + document.body.scrollLeft", "");
        js = js.replace(" + document.body.scrollTop", "");

        return Russian.getBytes(js);
    }
}