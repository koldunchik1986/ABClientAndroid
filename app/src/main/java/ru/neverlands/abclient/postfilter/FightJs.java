package ru.neverlands.abclient.postfilter;

import android.util.Log;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.Russian;

/**
 * Пост-фильтр для скрипта fight_v*.js.
 * Модифицирует панель управления боем, добавляя кнопки автобоя.
 * Портировано из FightJs.cs.
 */
public class FightJs {
    /**
     * Обрабатывает скрипт боя.
     * @param array Массив байт с исходным JS-кодом.
     * @return Модифицированный массив байт.
     */
    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            // Обработка гуамода
            if (AppVars.Profile != null && AppVars.Profile.DoGuamod) {
                html = html.replace(
                        "code.php?'+fexp[4]+'\" width=134 height=60></TD>",
                        "code.php?'+fexp[4]+'\" width=134 height=60><br><img src=http://image.neverlands.ru/1x1.gif width=1 height=8><br><span id=guamod3><font class=nickname><font color=#004A7F><b>* * * *</b></font></font></span></TD>");
            }

            // Замена стандартных кнопок на кнопки автобоя
            String oldButtons = "<input type=button value=\" xoд \" name=\"btx0\" class=fbut onclick=\"javascript: StartAct()\"> " +
                                "<input type=button value=сбросить name=\"bt2\" class=fbut onclick=\"javascript: RefreshF()\">";

            String newButtons = "<input type=button value=\" ход (0:00)\" name=\"btx0\" class=fbut onclick=\"javascript: myStartAct()\"> " +
                                "<input type=button value=\"автовыбор\" name=\"btav\" title=\"Предложить ход\" class=fbut onclick=\"javascript: AutoSelect()\"> " +
                                "<input type=button value=\"автоход\" name=\"btav\" title=\"Один ход\" class=fbut onclick=\"javascript: AutoTurn()\"> " +
                                "<input type=button value=\"автобой\" name=\"btab\" title=\"Полный автобой\" class=fbut onclick=\"javascript: AutoBoi()\"> " +
                                "<input type=button value=\"сбросить\" name=\"bt2\" class=fbut onclick=\"javascript: RefreshF()\"> " +
                                " <style type=\"text/css\">" +
                                " .fbutred {" +
                                "  BACKGROUND: #ffcccc;" +
                                "  BORDER: solid 1px #dea6a6" +
                                "  COLOR: #333333;" +
                                "  CURSOR: hand;" +
                                "  FONT: 11px Tahoma, Verdana, Arial;" +
                                "  FONT-WEIGHT: bold;" +
                                " }" +
                                " </style>" +
                                " <SCRIPT language=\"JavaScript\">" +
                                "  document.all(\"btx0\").value = AndroidBridge.XodButtonElapsedTime();" +
                                "  var curTimeInt = setInterval(\"xodtimerproc()\",1000);" +
                                "  function xodtimerproc(){ " +
                                "   document.all(\"btx0\").value = AndroidBridge.XodButtonElapsedTime(); }" +
                                " function myStartAct(){ " +
                                "   AndroidBridge.ResetLastBoiTimer();" +
                                "   StartAct();" +
                                " }" +
                                " </SCRIPT>";

            html = html.replace(oldButtons, newButtons);

            // Перехват завершения боя
            html = html.replace(
                    "<input type=button class=fbut value=\"Завершить\" onclick=\"location",
                    "<input type=button class=fbut value=\"Завершить\" onclick=\"ResetCure(); location");
            
            html = html.replace(
                    "<input type=submit class=fbut value=\"Завершить",
                    "<input type=button class=fbut onclick=\"javascript: ResetCure(); document.forms['FEND'].submit();\" value=\"Завершить");

            // Добавление новых JavaScript-функций
            StringBuilder sb = new StringBuilder(html);
            sb.append("\n");
            sb.append("function AutoSubmit(result)\n");
            sb.append("{\n");
            sb.append("  var ss = result.split(\"|\");\n");
            sb.append("  if (ss.length > 8)\n");
            sb.append("  {\n");
            sb.append("    var form_node = d.getElementById('form_main');\n");
            sb.append("    form_node.appendChild(AddElement('post_id','7'));\n");
            sb.append("    form_node.appendChild(AddElement('vcode',ss[0]));\n");
            sb.append("    form_node.appendChild(AddElement('enemy',ss[1]));\n");
            sb.append("    form_node.appendChild(AddElement('group',ss[2]));\n");
            sb.append("    form_node.appendChild(AddElement('inf_bot',ss[3]));\n");
            sb.append("    form_node.appendChild(AddElement('lev_bot',ss[4]));\n");
            sb.append("    form_node.appendChild(AddElement('ftr',ss[5]));\n");
            sb.append("    form_node.appendChild(AddElement('inu',ss[6]));\n");
            sb.append("    form_node.appendChild(AddElement('inb',ss[7]));\n");
            sb.append("    form_node.appendChild(AddElement('ina',ss[8]));\n");
            sb.append("    fight_f.submit();\n");
            sb.append("  }\n");
            sb.append("}\n");

            sb.append("function AutoSelect()\n");
            sb.append("{\n");
            sb.append("  AndroidBridge.AutoSelect();\n");
            sb.append("}\n");

            sb.append("function AutoTurn()\n");
            sb.append("{\n");
            sb.append("  AndroidBridge.AutoTurn();\n");
            sb.append("}\n");

            sb.append("function AutoUd()\n");
            sb.append("{\n");
            sb.append("  AndroidBridge.AutoUd();\n");
            sb.append("  AutoSelect();\n");
            sb.append("}\n");

            sb.append("function AutoBoi()\n");
            sb.append("{\n");
            sb.append("  AndroidBridge.AutoBoi();\n");
            sb.append("  AutoSelect();\n");
            sb.append("}\n");

            sb.append("function ResetCure()\n");
            sb.append("{\n");
            sb.append("  AndroidBridge.ResetCure();\n");
            sb.append("}\n");

            return Russian.getBytes(sb.toString());
        } catch (Exception e) {
            Log.e("FightJs", "Error processing fight script", e);
            return array;
        }
    }
}
