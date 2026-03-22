package ru.neverlands.abclient.postfilter;

import android.util.Log;

import java.nio.charset.Charset;

/**
 * Постфильтр для server-side `js/map.js`.
 *
 * Зависимости:
 * - вызывается из {@link Filter#process(android.content.Context, String, byte[])};
 * - использует bridge-методы `AndroidBridge/window.external` из `WebAppInterface`.
 *
 * Важно:
 * - синхронизация серверного таймера теперь делается из `main.php` (`wtime/tdsec/secgo`);
 * - старые nav timer-патчи в `map.js` удалены как лишние.
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

    // Runtime-патч suppress popup "перегруз рюкзака".
    // Unicode escapes используются, чтобы не зависеть от кодировки исходного map.js.
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

    // Fallback для случаев, когда в main.php нет wtime/tdsec/secgo и серверный таймер живет только в map.js.
    // Тогда после окончания timerst форсируем один runtime-tick:
    // - Навигатор: go=inf&ab_nav_tick=1
    // - Авто-рыбалка: go=inf&af_tick=1 (+vcode, если доступен в JS-контексте)
    private static final String TIMER_RUNTIME_FALLBACK_PATCH =
            "/*ABCLIENT_TIMER_RUNTIME_FALLBACK*/\n"
                    + "(function(){\n"
                    + "if (window.__ab_timer_fallback_applied) return;\n"
                    + "window.__ab_timer_fallback_applied = true;\n"
                    + "var __ab_last_fire = 0;\n"
                    + "function __ab_fire_tick(){\n"
                    + "  var __ab_now = Date.now ? Date.now() : (new Date()).getTime();\n"
                    + "  if ((__ab_now - __ab_last_fire) < 1200) return;\n"
                    + "  __ab_last_fire = __ab_now;\n"
                    + "  try {\n"
                    + "    if (window.external && window.external.IsAutoMoving && window.external.IsAutoMoving()) {\n"
                    + "      location = 'http://neverlands.ru/main.php?get_id=56&act=10&go=inf&ab_nav_tick=1&r=' + Math.random();\n"
                    + "      return;\n"
                    + "    }\n"
                    + "    if (window.external && window.external.IsAutoFish && window.external.IsAutoFish()) {\n"
                    + "      var __ab_url = 'http://neverlands.ru/main.php?get_id=56&act=10&go=inf&af_tick=1&r=' + Math.random();\n"
                    + "      try {\n"
                    + "        if (typeof window.vcode === 'string' && window.vcode.length > 0) {\n"
                    + "          __ab_url += '&vcode=' + encodeURIComponent(window.vcode);\n"
                    + "        }\n"
                    + "      } catch (_ab_vcode_e) {}\n"
                    + "      location = __ab_url;\n"
                    + "    }\n"
                    + "  } catch (_ab_fire_e) {}\n"
                    + "}\n"
                    + "function __ab_left_sec(){\n"
                    + "  try {\n"
                    + "    if (typeof window.time_left_sec !== 'undefined') {\n"
                    + "      var __ab_left = parseInt(window.time_left_sec, 10);\n"
                    + "      if (!isNaN(__ab_left)) return __ab_left;\n"
                    + "    }\n"
                    + "  } catch (_ab_left_e) {}\n"
                    + "  return 1;\n"
                    + "}\n"
                    + "function __ab_wrap_timerst(){\n"
                    + "  if (typeof window.timerst !== 'function') return false;\n"
                    + "  if (window.timerst.__ab_wrapped) return true;\n"
                    + "  var __ab_old = window.timerst;\n"
                    + "  var __ab_new = function(lp){\n"
                    + "    var __ab_ret = __ab_old.apply(this, arguments);\n"
                    + "    try {\n"
                    + "      if (__ab_left_sec() <= 0) __ab_fire_tick();\n"
                    + "    } catch (_ab_timer_e) {}\n"
                    + "    return __ab_ret;\n"
                    + "  };\n"
                    + "  __ab_new.__ab_wrapped = true;\n"
                    + "  window.timerst = __ab_new;\n"
                    + "  return true;\n"
                    + "}\n"
                    + "if (!__ab_wrap_timerst()) {\n"
                    + "  var __ab_retry = 0;\n"
                    + "  var __ab_iid = setInterval(function(){\n"
                    + "    __ab_retry++;\n"
                    + "    if (__ab_wrap_timerst() || __ab_retry > 24) { clearInterval(__ab_iid); }\n"
                    + "  }, 250);\n"
                    + "}\n"
                    + "})();\n";

    /**
     * Базовый prelude:
     * - ставит alias `window.external = window.AndroidBridge`;
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

        String patched = MAP_JS_SAFE_PRELUDE + fishPatched;
        boolean runtimePatchAppended = false;
        if (!patched.contains(ABCLIENT_MAP_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + OVERLOAD_RUNTIME_PATCH + "\n" + TIMER_RUNTIME_FALLBACK_PATCH;
            runtimePatchAppended = true;
        }

        Log.d(TAG, "process: fishPatch=" + (!fishPatched.equals(js))
                + ", runtimePatchAppended=" + runtimePatchAppended);

        return patched.getBytes(WINDOWS_1251);
    }
}
