package ru.neverlands.anclient.postfilter;

import ru.neverlands.anclient.utils.Russian;

/**
 * Порт postfilter для /js/hpmp.js из ПК-версии (ABClient/PostFilter/HpmpJs.cs).
 *
 * Зависимости и контракт:
 * - Вызывается из {@link Filter#process} по URL "/js/hpmp.js".
 * - Скрипт рассчитывает и рисует полосы HP/MA в верхнем блоке карты (go=ret),
 *   включая таймеры восстановления в элементе `hbar`.
 * - Использует уже существующие в странице переменные (`inshp`, `s_hp_f`, `s_ma_f`, `d`),
 *   которые сервер подготавливает в map.js/hpmp.js окружении.
 */
public class HpmpJs {

    private HpmpJs() {
        // Utility class.
    }

    /**
     * Возвращает JS-код hpmp.js в windows-1251 (как в остальных postfilter-скриптах).
     */
    public static byte[] process() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("var interv;");
        sb.append("function __anNum(v,d){var n=parseFloat(v);return isNaN(n)?d:n;}");
        sb.append("function __anNormInshp(){");
        sb.append("if(typeof inshp==='undefined'||!inshp) inshp=[0,1,0,7,2000,9000];");
        sb.append("inshp[0]=__anNum(inshp[0],0);");
        sb.append("inshp[1]=__anNum(inshp[1],1);");
        sb.append("inshp[2]=__anNum(inshp[2],0);");
        sb.append("inshp[3]=__anNum(inshp[3],7);");
        sb.append("inshp[4]=__anNum(inshp[4],2000);");
        sb.append("inshp[5]=__anNum(inshp[5],9000);");
        sb.append("if(inshp[1]<1) inshp[1]=1;");
        sb.append("if(inshp[3]<7) inshp[3]=7;");
        sb.append("if(inshp[4]<1) inshp[4]=1;");
        sb.append("if(inshp[5]<1) inshp[5]=1;");
        sb.append("}");
        sb.append("function ins_HP(){");
        sb.append("__anNormInshp();");
        sb.append("interv = setInterval(\"cha_HP()\",1000);");
        sb.append("if(inshp[0] < 0) inshp[0] = 0;");
        sb.append("if(inshp[3] < 7) inshp[3] = 7;");
        sb.append("}");
        sb.append("function hms(secs){");
        sb.append("time=[0,0,secs];");
        sb.append("for(var i=2; i>0; i--){");
        sb.append("time[i-1] = Math.floor(time[i]/60);");
        sb.append("time[i] = time[i]%60;");
        sb.append("if (time[i] < 10) time[i] = '0' + time[i];");
        sb.append("}");
        sb.append("if (time[0] == 0){");
        sb.append("var mtime = [time[1], time[2]];");
        sb.append("return mtime.join(':');");
        sb.append("}");
        sb.append("return time.join(':');");
        sb.append("}");
        sb.append("function cha_HP(){");
        sb.append("__anNormInshp();");
        sb.append("if(inshp[0] < 0) inshp[0] = 0;");
        sb.append("if(inshp[0] > inshp[1]) inshp[0] = inshp[1];");
        sb.append("if(inshp[2] > inshp[3]) inshp[2] = inshp[3];");
        sb.append("if(inshp[0] >= inshp[1] && inshp[2] >= inshp[3]) clearInterval(interv);");
        sb.append("s_hp_f = Math.round(160*(inshp[0]/inshp[1]));");
        sb.append("s_ma_f = Math.round(160*(inshp[2]/inshp[3]));");
        sb.append("d.getElementById('fHP').width = s_hp_f;");
        sb.append("d.getElementById('eHP').width = 160 - s_hp_f;");
        sb.append("d.getElementById('fMP').width = s_ma_f;");
        sb.append("d.getElementById('eMP').width = 160 - s_ma_f;");
        sb.append("var __an_hbar = null;");
        sb.append("try{");
        sb.append("var __an_hbars = d.querySelectorAll ? d.querySelectorAll('#hbar') : null;");
        sb.append("if(__an_hbars && __an_hbars.length){");
        sb.append("for(var __an_hi = __an_hbars.length - 1; __an_hi >= 0; __an_hi--){");
        sb.append("var __an_candidate = __an_hbars[__an_hi];");
        sb.append("if(!__an_candidate) continue;");
        sb.append("var __an_isStub = false;");
        sb.append("var __an_hidden = false;");
        sb.append("try{ __an_isStub = __an_candidate.getAttribute && __an_candidate.getAttribute('data-an-stub') === '1'; }catch(_an_hbar_stub){}");
        sb.append("try{ var __an_style = window.getComputedStyle ? window.getComputedStyle(__an_candidate) : __an_candidate.style; __an_hidden = !!(__an_style && (__an_style.display === 'none' || __an_style.visibility === 'hidden')); }catch(_an_hbar_style){}");
        sb.append("if(!__an_hbar){ __an_hbar = __an_candidate; }");
        sb.append("if(!__an_isStub && !__an_hidden){ __an_hbar = __an_candidate; break; }");
        sb.append("}");
        sb.append("}");
        sb.append("}catch(_an_hbar_qs){}");
        sb.append("if(!__an_hbar){ try{ __an_hbar = d.getElementById('hbar'); }catch(_an_hbar_e1){} }");
        sb.append("if(!__an_hbar){ try{ if(typeof document.all === 'function'){ __an_hbar = document.all('hbar'); } }catch(_an_hbar_e2){} }");
        sb.append("if(__an_hbar){");
        sb.append("var result = '<font class=hpfont>: [<font color=#bb0000><b>' + Math.round(inshp[0]) + '</b>/<b>' + inshp[1] + '</b>';");
        sb.append("var sHP = Math.round(((inshp[1]-inshp[0])*inshp[4])/inshp[1]);");
        sb.append("if (sHP > 0) result = result + ' (<b>' + hms(sHP) + '</b>)';");
        sb.append("result = result + '</font> | <font color=#336699><b>' + Math.round(inshp[2]) + '</b>/<b>' + inshp[3] + '</b>';");
        sb.append("var sMA = Math.round(((inshp[3]-inshp[2])*inshp[5])/inshp[3]);");
        sb.append("if (sMA > 0) result = result + ' (<b>' + hms(sMA) + '</b>)';");
        sb.append("var __an_tireSuffix = '';");
        sb.append("try{");
        sb.append("var __an_curTire = NaN;");
        sb.append("var __an_maxTire = NaN;");
        sb.append("if (typeof hpmp !== 'undefined' && hpmp && hpmp.length > 4) { __an_maxTire = parseInt(hpmp[4], 10); }");
        sb.append("if (!isNaN(__an_maxTire)) { __an_curTire = 100 - __an_maxTire; }");
        sb.append("if (isNaN(__an_curTire)) {");
        sb.append("var __an_bridge = (window.AndroidBridge&&typeof window.AndroidBridge.GetCurrentTied==='function')?window.AndroidBridge:((window.external&&typeof window.external.GetCurrentTied==='function')?window.external:null);");
        sb.append("if (__an_bridge) {");
        sb.append("var __an_bridgeTied = parseInt(__an_bridge.GetCurrentTied(), 10);");
        sb.append("if (!isNaN(__an_bridgeTied)) { __an_curTire = __an_bridgeTied; }");
        sb.append("}");
        sb.append("}");
        sb.append("if (!isNaN(__an_curTire)) {");
        sb.append("if (__an_curTire < 0) __an_curTire = 0;");
        sb.append("if (__an_curTire > 100) __an_curTire = 100;");
        sb.append("__an_tireSuffix = ' | <font color=#333333><b>Усталость:</b> <b>' + __an_curTire + '</b></font>'; ");
        sb.append("}");
        sb.append("}catch(_an_tire_e){}");
        sb.append("result = result + '</font>]' + __an_tireSuffix + '</font>';");
        sb.append("__an_hbar.innerHTML = result;");
        sb.append("}");
        sb.append("inshp[0] += inshp[1]/inshp[4];");
        sb.append("inshp[2] += inshp[3]/inshp[5];");
        sb.append("}");

        return Russian.getBytes(sb.toString());
    }
}
