package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

public class FightJs {
    public static byte[] process(byte[] array) {
        String html = Russian.getString(array);

        // Guamod Injection
        if (AppVars.Profile != null && AppVars.Profile.DoGuamod) {
            String guamodOriginal = "code.php?'+fexp[4]+'\" width=134 height=60></TD>";
            String guamodReplacement = "code.php?'+fexp[4]+'\" width=134 height=60><br><img src=http://image.neverlands.ru/1x1.gif width=1 height=8><br><span id=guamod3><font class=nickname><font color=#004A7F><b>* * * *</b></font></font></span></TD>";
            html = html.replace(guamodOriginal, guamodReplacement);
        }

        // Button Panel Injection
        String originalButtons = "<input type=button value=\" xoд \" name=\"btx0\" class=fbut onclick=\"javascript: StartAct()\"> " +
                               "<input type=button value=сбросить name=\"bt2\" class=fbut onclick=\"javascript: RefreshF()\">";

        String newButtons = "<input type=button value=\" ход (0:00)\" name=\"btx0\" class=fbut onclick=\"javascript: myStartAct()\"> " +
                          "<input type=button value=\"автовыбор\" name=\"btav\" title=\"Предложить ход\" class=fbut onclick=\"javascript: AndroidBridge.AutoSelect()\"> " +
                          "<input type=button value=\"автоход\" name=\"btav\" title=\"Один ход\" class=fbut onclick=\"javascript: AndroidBridge.AutoTurn()\"> " +
                          "<input type=button value=\"автобой\" name=\"btab\" title=\"Полный автобой\" class=fbut onclick=\"javascript: AndroidBridge.AutoBoi()\"> " +
                          "<input type=button value=сбросить name=\"bt2\" class=fbut onclick=\"javascript: RefreshF()\"> " +
                          " <style type=\"text/css\">" +
                          " .fbutred {BACKGROUND: #ffcccc; BORDER: solid 1px #dea6a6; COLOR: #333333; CURSOR: hand; FONT: 11px Tahoma, Verdana, Arial; FONT-WEIGHT: bold; }" +
                          " </style>" +
                          " <script language=\"JavaScript\">" +
                          "  document.all.btx0.value = window.AndroidBridge.XodButtonElapsedTime();" +
                          "  var curTimeInt = setInterval(\"xodtimerproc()\",1000);" +
                          "  function xodtimerproc(){ document.all.btx0.value = window.AndroidBridge.XodButtonElapsedTime(); }" +
                          " function myStartAct(){ window.AndroidBridge.ResetLastBoiTimer(); StartAct(); }" +
                          " </script>";

        html = html.replace(originalButtons, newButtons);

        // End-of-Fight Hook
        html = html.replace(
                "<input type=button class=fbut value=\"Завершить\" onclick=\"location",
                "<input type=button class=fbut value=\"Завершить\" onclick=\"javascript:AndroidBridge.ResetCure(); location");

        html = html.replace(
                "<input type=submit class=fbut value=\"Завершить",
                "<input type=button class=fbut onclick=\"javascript: AndroidBridge.ResetCure(); document.forms['FEND'].submit();\" value=\"Завершить");

        // Append JS callback functions
        StringBuilder sb = new StringBuilder(html);
        sb.append("\nfunction AutoSubmit(result){ " +
                "var ss = result.split(\"|\");" +
                "if (ss.length > 8) { " +
                "var form_node = document.getElementById('form_main');" +
                "form_node.appendChild(AddElement('post_id','7'));" +
                "form_node.appendChild(AddElement('vcode',ss[0]));" +
                "form_node.appendChild(AddElement('enemy',ss[1]));" +
                "form_node.appendChild(AddElement('group',ss[2]));" +
                "form_node.appendChild(AddElement('inf_bot',ss[3]));" +
                "form_node.appendChild(AddElement('lev_bot',ss[4]));" +
                "form_node.appendChild(AddElement('ftr',ss[5]));" +
                "form_node.appendChild(AddElement('inu',ss[6]));" +
                "form_node.appendChild(AddElement('inb',ss[7]));" +
                "form_node.appendChild(AddElement('ina',ss[8]));" +
                "fight_f.submit();" +
                "}} " +
                "\nfunction AutoSelect(){ window.AndroidBridge.AutoSelect(); }" +
                "\nfunction AutoTurn(){ window.AndroidBridge.AutoTurn(); }" +
                "\nfunction AutoUd(){ window.AndroidBridge.AutoUd(); AutoSelect(); }" +
                "\nfunction AutoBoi(){ window.AndroidBridge.AutoBoi(); AutoSelect(); }" +
                "\nfunction ResetCure(){ window.AndroidBridge.ResetCure(); }");

        return Russian.getBytes(sb.toString());
    }
}