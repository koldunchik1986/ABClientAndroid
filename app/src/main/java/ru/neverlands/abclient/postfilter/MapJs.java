package ru.neverlands.abclient.postfilter;

import android.util.Log;

import java.nio.charset.Charset;

/**
 * Постфильтр для server-side {@code js/map.js}.
 *
 * Зависимости:
 * - вызывается из {@link Filter#process(android.content.Context, String, byte[])};
 * - использует bridge-методы {@code AndroidBridge/window.external} из {@code WebAppInterface}.
 */
public class MapJs {
    private static final String TAG = "MapJs";
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    // Маркеры, чтобы не дублировать патчи при повторной обработке.
    private static final String ABCLIENT_MAP_STUB_MARKER = "/*ABCLIENT_MAP_STUBS*/";
    private static final String ABCLIENT_MAP_RUNTIME_PATCH_MARKER = "/*ABCLIENT_MAP_RUNTIME_PATCHES*/";

    // C# parity для no-captcha в авто-рыбалке.
    private static final String FISH_NO_CAPTCHA_CONDITION_OLD =
            "if (!ingr[1] && window.external.IsAutoFish()) {";
    private static final String FISH_NO_CAPTCHA_CONDITION_NEW =
            "if ((!ingr[1] || ingr[1] == '00000') && window.external.IsAutoFish()) {";

    // Патч окончания timerst: если активен Навигатор, после таймера продолжаем через go=inf.
    private static final String TIMER_FINISH_LOCATION_OLD =
            "location = 'http://neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_COMPACT =
            "location='http://neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_WWW =
            "location = 'http://www.neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_WWW_COMPACT =
            "location='http://www.neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_WINDOW =
            "window.location='http://neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_WINDOW_SPACED =
            "window.location = 'http://neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_WINDOW_WWW =
            "window.location='http://www.neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_OLD_WINDOW_WWW_SPACED =
            "window.location = 'http://www.neverlands.ru/main.php';";
    private static final String TIMER_FINISH_LOCATION_NEW =
            "if (window.external && window.external.IsAutoMoving && window.external.IsAutoMoving()) {"
                    + "location = 'http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_nav_tick=1&r=' + Math.random();"
                    + "} else {"
                    + "location = 'http://neverlands.ru/main.php';"
                    + "}";

    // Runtime-hook для timerst: даже если строковый replace не совпал, при окончании таймера
    // гарантированно посылаем навигационный tick в go=inf при активном Навигаторе.
    private static final String NAV_TIMER_RUNTIME_PATCH =
            "/*ABCLIENT_NAV_TIMER_RUNTIME_PATCH*/\n"
                    + "(function(){\n"
                    + "if (window.__ab_nav_timer_patch_applied) return;\n"
                    + "window.__ab_nav_timer_patch_applied = true;\n"
                    + "function __ab_nav_tick(){\n"
                    + "  try{\n"
                    + "    if(window.external && window.external.IsAutoMoving && window.external.IsAutoMoving()){\n"
                    + "      location='http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_nav_tick=1&r='+Math.random();\n"
                    + "    }\n"
                    + "  }catch(_ab_nav_e){}\n"
                    + "}\n"
                    + "function __ab_wrap_timerst(){\n"
                    + "  if(typeof window.timerst!=='function') return false;\n"
                    + "  if(window.timerst.__ab_wrapped) return true;\n"
                    + "  var __ab_old=window.timerst;\n"
                    + "  var __ab_new=function(lp){\n"
                    + "    var __ab_ret=__ab_old.apply(this, arguments);\n"
                    + "    try{\n"
                    + "      var __ab_left=(typeof window.time_left_sec!=='undefined')?parseInt(window.time_left_sec,10):1;\n"
                    + "      if(!isNaN(__ab_left) && __ab_left<=0){ __ab_nav_tick(); }\n"
                    + "    }catch(_ab_nav_e2){}\n"
                    + "    return __ab_ret;\n"
                    + "  };\n"
                    + "  __ab_new.__ab_wrapped=true;\n"
                    + "  window.timerst=__ab_new;\n"
                    + "  return true;\n"
                    + "}\n"
                    + "if(!__ab_wrap_timerst()){\n"
                    + "  var __ab_retry=0;\n"
                    + "  var __ab_iid=setInterval(function(){\n"
                    + "    __ab_retry++;\n"
                    + "    if(__ab_wrap_timerst()||__ab_retry>24){ clearInterval(__ab_iid); }\n"
                    + "  },250);\n"
                    + "}\n"
                    + "})();\n";

    // Дополнительный runtime-патч для suppress popup "перегруз рюкзака".
    // Используем unicode escapes, чтобы не зависеть от кодировки исходника.
    private static final String OVERLOAD_RUNTIME_PATCH =
            ABCLIENT_MAP_RUNTIME_PATCH_MARKER + "\n"
                    + "(function(){\n"
                    + "if (window.__ab_overload_patch_applied) return;\n"
                    + "window.__ab_overload_patch_applied = true;\n"
                    + "if (typeof window.MessBoxDiv !== 'function') return;\n"
                    + "var __ab_oldMessBoxDiv = window.MessBoxDiv;\n"
                    + "window.MessBoxDiv = function(mess){\n"
                    + "  try {\n"
                    + "    var __ab_msg = (mess == null ? '' : String(mess)).toLowerCase();\n"
                    + "    var __ab_overload = __ab_msg.indexOf('\\u0440\\u044e\\u043a\\u0437\\u0430\\u043a') !== -1\n"
                    + "      || __ab_msg.indexOf('\\u0437\\u0430\\u043c\\u0435\\u0434\\u043b\\u0435\\u043d') !== -1\n"
                    + "      || __ab_msg.indexOf('\\u0442\\u044f\\u0436\\u0435\\u043b') !== -1;\n"
                    + "    var __ab_canCheck = window.external && typeof window.external.ShowOverWarning === 'function';\n"
                    + "    if (__ab_overload && __ab_canCheck && !window.external.ShowOverWarning()) {\n"
                    + "      try { if (typeof window.external.FishOverload === 'function') { window.external.FishOverload(); } } catch (_ab_e1) {}\n"
                    + "      return;\n"
                    + "    }\n"
                    + "  } catch (_ab_e2) {}\n"
                    + "  return __ab_oldMessBoxDiv.apply(this, arguments);\n"
                    + "};\n"
                    + "})();\n";

    /**
     * Базовый prelude:
     * - ставит alias {@code window.external = window.AndroidBridge};
     * - добавляет no-op stubs для функций, которые map.js вызывает до полной инициализации.
     */
    private static final String MAP_JS_SAFE_PRELUDE =
            ABCLIENT_MAP_STUB_MARKER + "\n"
                    + "if (typeof window.AndroidBridge !== 'undefined') { window.external = window.AndroidBridge; }\n"
                    + "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }\n"
                    + "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }\n"
                    + "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }\n";

    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        String js = new String(array, WINDOWS_1251);
        if (js.contains(ABCLIENT_MAP_STUB_MARKER)) {
            return array;
        }

        String fishPatched = js.replace(FISH_NO_CAPTCHA_CONDITION_OLD, FISH_NO_CAPTCHA_CONDITION_NEW);
        String timerPatched = fishPatched
                .replace(TIMER_FINISH_LOCATION_OLD, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_COMPACT, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_WWW, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_WWW_COMPACT, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_WINDOW, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_WINDOW_SPACED, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_WINDOW_WWW, TIMER_FINISH_LOCATION_NEW)
                .replace(TIMER_FINISH_LOCATION_OLD_WINDOW_WWW_SPACED, TIMER_FINISH_LOCATION_NEW);

        String patched = MAP_JS_SAFE_PRELUDE + timerPatched;
        boolean runtimePatchAppended = false;
        if (!patched.contains(ABCLIENT_MAP_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + OVERLOAD_RUNTIME_PATCH + "\n" + NAV_TIMER_RUNTIME_PATCH;
            runtimePatchAppended = true;
        }

        Log.d(TAG, "process: fishPatch=" + (!fishPatched.equals(js))
                + ", navTimerPatch=" + (!timerPatched.equals(fishPatched))
                + ", runtimePatchAppended=" + runtimePatchAppended);

        return patched.getBytes(WINDOWS_1251);
    }
}
