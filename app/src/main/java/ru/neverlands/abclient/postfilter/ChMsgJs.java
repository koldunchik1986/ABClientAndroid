package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class ChMsgJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        // Hook for filtering every chat message
        html = html.replace(
            "s += txt + \"<BR>\";",
            "s += window.AndroidBridge.chatFilter(txt) + \"<BR>\";");

        // Hook for chat update event
        html = html.replace(
            ",65000);",
            ", 65000); window.AndroidBridge.chatUpdated();");

        // Replace complex logic for private message handling
        html = html.replace(
            "login = login.replace ('%', '');",
            "top.frames['ch_buttons'].document.FBT.text.focus(); " +
            "var prompt = top.frames['ch_buttons'].document.FBT.text.value; " +
            "if (prompt.indexOf('%clan%') == 0 || prompt.indexOf('%pair%') == 0) { login = login.replace('%',''); login = login.replace('%',''); login = login.replace('%',''); top.frames['ch_buttons'].document.FBT.text.value = prompt + '%<' + login + '> '; return false; } else {" +
            "if (login.charAt(2) == '%'){ login = login.substr(3); top.frames['ch_buttons'].document.FBT.text.value = '%pair%%<' + login + '> ' + prompt; return false; } else " +
            "if (login.charAt(1) == '%'){ login = login.substr(2); top.frames['ch_buttons'].document.FBT.text.value = '%clan%%<' + login + '> ' + prompt; return false; } else login = login.substr(1); }"
        );

        // Fix alt attributes for tooltips
        html = html.replace("alt=", "title=");
        html = html.replace(".alt", ".title");

        // Fix scroll positioning issues
        html = html.replace(" + document.body.scrollLeft", "");
        html = html.replace(" + document.body.scrollTop", "");

        return Russian.getBytes(html);
    }
}
