package ru.neverlands.abclient.postfilter;

import android.content.Context;
import android.util.Log;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern DECL_WIDTH_PATTERN = Pattern.compile("var\\s+width\\s*=\\s*([^;]+);");
    private static final Pattern DECL_HEIGHT_PATTERN = Pattern.compile("var\\s+height\\s*=\\s*([^;]+);");
    private static final Pattern DECL_SCALE_PATTERN = Pattern.compile("var\\s+scale\\s*=\\s*([^;]+);");
    private static final Pattern ASSIGN_SCALE_PATTERN = Pattern.compile("(?m)^\\s*scale\\s*=\\s*([^;]+);");

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

    /**
     * Базовый prelude:
     * - ставит alias `window.external = window.AndroidBridge`;
     * - добавляет no-op stubs для функций, которые map.js вызывает до полной инициализации.
     */
    private static final String MAP_JS_SAFE_PRELUDE =
            ABCLIENT_MAP_STUB_MARKER + "\n"
                    + "(function(){\n"
                    + "var __ab = (typeof window.AndroidBridge !== 'undefined') ? window.AndroidBridge : null;\n"
                    + "function __abCall(name,args,defVal){\n"
                    + "  try {\n"
                    + "    if (__ab && typeof __ab[name] === 'function') {\n"
                    + "      var v = __ab[name].apply(__ab, args || []);\n"
                    + "      return (typeof v === 'undefined' || v === null) ? defVal : v;\n"
                    + "    }\n"
                    + "  } catch (_ab_e) {}\n"
                    + "  return defVal;\n"
                    + "}\n"
                    + "function __abReloadChList(url){\n"
                    + "  try {\n"
                    + "    if (window.parent && window.parent.frames && window.parent.frames['ch_list']) {\n"
                    + "      window.parent.frames['ch_list'].location = url;\n"
                    + "      return;\n"
                    + "    }\n"
                    + "  } catch (_ab_e3) {}\n"
                    + "  try {\n"
                    + "    if (__ab && typeof __ab.loadFrame === 'function') {\n"
                    + "      __ab.loadFrame('ch_list', url);\n"
                    + "    }\n"
                    + "  } catch (_ab_e4) {}\n"
                    + "}\n"
                    + "window.external = {\n"
                    + "  GetHalfMapWidth: function(){ return parseInt(__abCall('GetHalfMapWidth', [], 4), 10) || 4; },\n"
                    + "  GetHalfMapHeight: function(){ return parseInt(__abCall('GetHalfMapHeight', [], 3), 10) || 3; },\n"
                    + "  GetMapScale: function(){\n"
                    + "    var __ab_scale = parseInt(__abCall('GetMapScale', [], 75), 10);\n"
                    + "    if (isNaN(__ab_scale) || __ab_scale < 50 || __ab_scale > 100) __ab_scale = 75;\n"
                    + "    return __ab_scale;\n"
                    + "  },\n"
                    + "  UsersOnline: function(){ return String(__abCall('UsersOnline', [], '')); },\n"
                    + "  DoHerbAutoCut: function(){ return !!__abCall('DoHerbAutoCut', [], false); },\n"
                    + "  IsCellExists: function(x,y){ return !!__abCall('IsCellExists', [x,y], true); },\n"
                    + "  CellAltText: function(x,y,scale){ return String(__abCall('CellAltText', [x,y,scale], '')); },\n"
                    + "  GenMoveLink: function(x,y){ return String(__abCall('GenMoveLink', [x,y], '')); },\n"
                    + "  MoveTo: function(dest){ return __abCall('MoveTo', [dest], null); },\n"
                    + "  CellDivText: function(x,y,scale,link,showmove,isframe){ return String(__abCall('CellDivText', [x,y,scale,link,showmove,isframe], '')); },\n"
                    + "  DoHideMiniMap: function(){ return !!__abCall('DoHideMiniMap', [], false); },\n"
                    + "  MapText: function(){ return String(__abCall('MapText', [], '')); },\n"
                    + "  HerbsList: function(){ return String(__abCall('HerbsList', [], '')); },\n"
                    + "  TraceCut: function(a,b,c,d,e){ return __abCall('TraceCut', [a,b,c,d,e], null); },\n"
                    + "  SetNeverTimer: function(ms){ return __abCall('SetNeverTimer', [ms], null); },\n"
                    + "  SetAutoFishMassa: function(v){ return __abCall('SetAutoFishMassa', [v], null); },\n"
                    + "  CheckPri: function(name,myst){ return String(__abCall('CheckPri', [name,myst], '')); },\n"
                    + "  InsertGuaDiv: function(code){ return String(__abCall('InsertGuaDiv', [code], '')); },\n"
                    + "  FishOverload: function(){ return __abCall('FishOverload', [], null); },\n"
                    + "  IsAutoFish: function(){ return !!__abCall('IsAutoFish', [], false); },\n"
                    + "  SetFishNoCaptchaReady: function(){ return __abCall('SetFishNoCaptchaReady', [], null); },\n"
                    + "  ShowOverWarning: function(){ return !!__abCall('ShowOverWarning', [], false); },\n"
                    + "  TraceMapRuntime: function(msg){ return __abCall('TraceMapRuntime', [String(msg)], null); }\n"
                    + "};\n"
                    + "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }\n"
                    + "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }\n"
                    + "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }\n"
                    + "})();\n";

    public static byte[] process(Context context, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        String serverJs = new String(array, WINDOWS_1251);
        String js = serverJs;

        String rawWidthDecl = extractVarDecl(js, DECL_WIDTH_PATTERN);
        String rawHeightDecl = extractVarDecl(js, DECL_HEIGHT_PATTERN);
        String rawScaleDecl = extractScaleDecl(js);
        int rawWidthOneCount = countMatches(js, "width\\s*=\\s*1\\s*;");
        int rawHeightOneCount = countMatches(js, "height\\s*=\\s*1\\s*;");

        String dimPatched = js
                // C# parity: фиксируем базовые размеры карты через bridge до первого вызова view_map().
                .replaceAll("var\\s+width\\s*=\\s*3\\s*;", "var width = window.external.GetHalfMapWidth();")
                .replaceAll("var\\s+height\\s*=\\s*1\\s*;", "var height = window.external.GetHalfMapHeight();")
                .replaceAll("var\\s+scale\\s*=\\s*\\d+\\s*;", "var scale = window.external.GetMapScale();")
                .replaceAll("(?m)^\\s*scale\\s*=\\s*\\d+\\s*;", "scale = window.external.GetMapScale();");

        String fishPatched = dimPatched.replace(FISH_NO_CAPTCHA_CONDITION_OLD, FISH_NO_CAPTCHA_CONDITION_NEW);
        String chatReloadSafePatched = fishPatched.replaceAll(
                "parent\\.frames\\[\"ch_list\"\\]\\.location\\s*=\\s*\"/ch\\.php\\?lo=1\"\\s*;",
                "__abReloadChList('/ch.php?lo=1');");
        String patchedWidthDecl = extractVarDecl(fishPatched, DECL_WIDTH_PATTERN);
        String patchedHeightDecl = extractVarDecl(fishPatched, DECL_HEIGHT_PATTERN);
        String patchedScaleDecl = extractScaleDecl(fishPatched);
        int getMapScaleCallCount = countMatches(chatReloadSafePatched, "GetMapScale\\s*\\(");

        String patched = MAP_JS_SAFE_PRELUDE + chatReloadSafePatched;
        boolean runtimePatchAppended = false;
        if (!patched.contains(ABCLIENT_MAP_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + OVERLOAD_RUNTIME_PATCH;
            runtimePatchAppended = true;
        }

        Log.d(TAG, "process: raw(width=" + rawWidthDecl + ",height=" + rawHeightDecl + ",scale=" + rawScaleDecl
                + ",width1Count=" + rawWidthOneCount + ",height1Count=" + rawHeightOneCount + ")"
                + ", patched(width=" + patchedWidthDecl + ",height=" + patchedHeightDecl + ",scale=" + patchedScaleDecl + ")"
                + ", dimPatch=" + (!dimPatched.equals(js))
                + ", fishPatch=" + (!fishPatched.equals(dimPatched))
                + ", chatReloadSafePatch=" + (!chatReloadSafePatched.equals(fishPatched))
                + ", getMapScaleCalls=" + getMapScaleCallCount
                + ", runtimePatchAppended=" + runtimePatchAppended);

        return patched.getBytes(WINDOWS_1251);
    }

    private static String extractVarDecl(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return "na";
        }
        return matcher.group(1).trim();
    }

    private static String extractScaleDecl(String source) {
        String scaleDecl = extractVarDecl(source, DECL_SCALE_PATTERN);
        if (!"na".equals(scaleDecl)) {
            return scaleDecl;
        }
        return extractVarDecl(source, ASSIGN_SCALE_PATTERN);
    }

    private static int countMatches(String source, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
