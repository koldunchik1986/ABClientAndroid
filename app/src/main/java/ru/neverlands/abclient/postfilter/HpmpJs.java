package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла /js/hpmp.js.
 * Аналог HpmpJs.cs в оригинальном приложении.
 */
public class HpmpJs {

    /**
     * Генерирует JavaScript-код для управления отображением HP/MA и таймеров.
     * @return Сгенерированный JavaScript-код в виде массива байт.
     */
    public static byte[] process() {
        StringBuilder sb = new StringBuilder();

        sb.append("var interv;");
        sb.append("function ins_HP()");
        sb.append("{");
        sb.append("    interv = setInterval(\"cha_HP()\",1000);");
        sb.append("    if(inshp[0] < 0) inshp[0] = 0;");
        sb.append("    if(inshp[3] < 7) inshp[3] = 7;");
        sb.append("}");
        sb.append("function hms(secs)");
        sb.append("{");
        sb.append("    time=[0,0,secs];");
        sb.append("    for(var i=2; i>0; i--)");
        sb.append("    {");
        sb.append("        time[i-1] = Math.floor(time[i]/60);");
        sb.append("        time[i] = time[i]%60; ");
        sb.append("        if (time[i] < 10)");
        sb.append("             time[i] = '0' + time[i];");
        sb.append("    };");
        sb.append("    if (time[0] == 0) ");
        sb.append("    {");
        sb.append("        var mtime = [time[1], time[2]];");
        sb.append("        return mtime.join(':');");
        sb.append("    }");
        sb.append("    return time.join(':');");
        sb.append("}");
        sb.append("function cha_HP()");
        sb.append("{");
        sb.append("    if(inshp[0] < 0) inshp[0] = 0;");
        sb.append("    if(inshp[0] > inshp[1]) inshp[0] = inshp[1];");
        sb.append("    if(inshp[2] > inshp[3]) inshp[2] = inshp[3];");
        sb.append("    if(inshp[0] >= inshp[1] && inshp[2] >= inshp[3]) clearInterval(interv);");
        sb.append("    s_hp_f = Math.round(160*(inshp[0]/inshp[1]));");
        sb.append("    s_ma_f = Math.round(160*(inshp[2]/inshp[3]));");
        sb.append("    d.getElementById('fHP').width = s_hp_f;");
        sb.append("    d.getElementById('eHP').width = 160 - s_hp_f;");
        sb.append("    d.getElementById('fMP').width = s_ma_f;");
        sb.append("    d.getElementById('eMP').width = 160 - s_ma_f;");

        sb.append("       if(document.all(\"hbar\"))"); 
        sb.append("        {");
        sb.append("            var result = '<font class=hpfont>: [<font color=#bb0000><b>' + Math.round(inshp[0]) + '</b>/<b>' + inshp[1] + '</b>';");

        sb.append("            var sHP = Math.round(((inshp[1]-inshp[0])*inshp[4])/inshp[1]);");
        sb.append("            if (sHP > 0) result = result + ' (<b>' + hms(sHP) + '</b>)';");

        sb.append("            result = result + '</font> | <font color=#336699><b>' + Math.round(inshp[2]) + '</b>/<b>' + inshp[3] + '</b>';");

        sb.append("            var sMA = Math.round(((inshp[3]-inshp[2])*inshp[5])/inshp[3]);");
        sb.append("            if (sMA > 0) result = result + ' (<b>' + hms(sMA) + '</b>)';");

        sb.append("            result = result + '</font>]</font>';");
        sb.append("           document.all(\"hbar\").innerHTML = result; ");
        sb.append("        }");
        sb.append("    inshp[0] += inshp[1]/inshp[4];");
        sb.append("    inshp[2] += inshp[3]/inshp[5];");
        sb.append("}");

        return Russian.getBytes(sb.toString());
    }
}
