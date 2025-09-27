package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class HpmpJs {
    public static byte[] process() {
        String script = "var interv;" +
                "function ins_HP(){ " +
                "    interv = setInterval(\"cha_HP()\",1000);" +
                "    if(inshp[0] < 0) inshp[0] = 0;" +
                "    if(inshp[3] < 7) inshp[3] = 7;" +
                "}" +
                "function hms(secs){ " +
                "    time=[0,0,secs];" +
                "    for(var i=2; i>0; i--){ " +
                "        time[i-1] = Math.floor(time[i]/60);" +
                "        time[i] = time[i]%60;" +
                "        if (time[i] < 10) time[i] = '0' + time[i];" +
                "    };" +
                "    if (time[0] == 0) { var mtime = [time[1], time[2]]; return mtime.join(':'); }" +
                "    return time.join(':');" +
                "}" +
                "function cha_HP(){ " +
                "    if(inshp[0] < 0) inshp[0] = 0;" +
                "    if(inshp[0] > inshp[1]) inshp[0] = inshp[1];" +
                "    if(inshp[2] > inshp[3]) inshp[2] = inshp[3];" +
                "    if(inshp[0] >= inshp[1] && inshp[2] >= inshp[3]) clearInterval(interv);" +
                "    var s_hp_f = Math.round(160*(inshp[0]/inshp[1]));" +
                "    var s_ma_f = Math.round(160*(inshp[2]/inshp[3]));" +
                "    document.getElementById('fHP').width = s_hp_f;" +
                "    document.getElementById('eHP').width = 160 - s_hp_f;" +
                "    document.getElementById('fMP').width = s_ma_f;" +
                "    document.getElementById('eMP').width = 160 - s_ma_f;" +
                "    if(document.all.hbar){ " +
                "        var result = '<font class=hpfont>: [<font color=#bb0000><b>' + Math.round(inshp[0]) + '</b>/<b>' + inshp[1] + '</b>';" +
                "        var sHP = Math.round(((inshp[1]-inshp[0])*inshp[4])/inshp[1]);" +
                "        if (sHP > 0) result = result + ' (<b>' + hms(sHP) + '</b>)';" +
                "        result = result + '</font> | <font color=#336699><b>' + Math.round(inshp[2]) + '</b>/<b>' + inshp[3] + '</b>';" +
                "        var sMA = Math.round(((inshp[3]-inshp[2])*inshp[5])/inshp[3]);" +
                "        if (sMA > 0) result = result + ' (<b>' + hms(sMA) + '</b>)';" +
                "        result = result + '</font>]</font>';" +
                "        document.all.hbar.innerHTML = result;" +
                "    }" +
                "    inshp[0] += inshp[1]/inshp[4];" +
                "    inshp[2] += inshp[3]/inshp[5];" +
                "}";
        return Russian.getBytes(script);
    }
}