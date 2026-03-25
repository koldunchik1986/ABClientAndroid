package ru.neverlands.abclient.postfilter;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    // Runtime-патч: любые server popup из map.js отправляются в чат и не рисуются как overlay-окна.
    // Unicode escapes используются, чтобы не зависеть от кодировки исходного map.js.
    private static final String OVERLOAD_RUNTIME_PATCH =
            ABCLIENT_MAP_RUNTIME_PATCH_MARKER + "\n"
                    + "(function(){\n"
                    + "if (window.__ab_overload_patch_applied) return;\n"
                    + "window.__ab_overload_patch_applied = true;\n"
                    + "if (typeof window.MessBoxDiv !== 'function') return;\n"
                    + "var __ab_oldMessBoxDiv = window.MessBoxDiv;\n"
                    + "try {\n"
                    + "  var __ab_style_id = 'ab_server_popup_hide';\n"
                    + "  if (!document.getElementById(__ab_style_id)) {\n"
                    + "    var __ab_style = document.createElement('style');\n"
                    + "    __ab_style.id = __ab_style_id;\n"
                    + "    __ab_style.type = 'text/css';\n"
                    + "    __ab_style.textContent = '#static_window,#darker,#uni{display:none !important;}';\n"
                    + "    (document.head || document.documentElement).appendChild(__ab_style);\n"
                    + "  }\n"
                    + "} catch (_ab_e_style) {}\n"
                    + "function __abForwardPopup(mess){\n"
                    + "  try {\n"
                    + "    if (window.external && typeof window.external.PostServerPopupToChat === 'function') {\n"
                    + "      window.external.PostServerPopupToChat(mess == null ? '' : String(mess));\n"
                    + "    }\n"
                    + "  } catch (_ab_e0) {}\n"
                    + "}\n"
                    + "window.MessBoxDiv = function(mess){\n"
                    + "  __abForwardPopup(mess);\n"
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
                    + "  return;\n"
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
                    + "window.__abReloadChList = function(url){\n"
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
                    + "};\n"
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
                    + "  HerbsList: function(list){ return String(__abCall('HerbsList', [String(list)], '')); },\n"
                    + "  TraceCut: function(herb){ return __abCall('TraceCut', [String(herb)], null); },\n"
                    + "  SetNeverTimer: function(ms){ return __abCall('SetNeverTimer', [ms], null); },\n"
                    + "  SetAutoFishMassa: function(v){ return __abCall('SetAutoFishMassa', [v], null); },\n"
                    + "  CheckPri: function(name,myst){ return String(__abCall('CheckPri', [name,myst], '')); },\n"
                    + "  InsertGuaDiv: function(code){ return String(__abCall('InsertGuaDiv', [code], '')); },\n"
                    + "  FishOverload: function(){ return __abCall('FishOverload', [], null); },\n"
                    + "  IsAutoFish: function(){ return !!__abCall('IsAutoFish', [], false); },\n"
                    + "  SetFishNoCaptchaReady: function(){ return __abCall('SetFishNoCaptchaReady', [], null); },\n"
                    + "  ShowOverWarning: function(){ return !!__abCall('ShowOverWarning', [], false); },\n"
                    + "  SetCurrentTied: function(v){ return __abCall('SetCurrentTied', [v], null); },\n"
                    + "  PostServerPopupToChat: function(message){ return __abCall('PostServerPopupToChat', [String(message)], null); },\n"
                    + "  TraceMapRuntime: function(msg){ return __abCall('TraceMapRuntime', [String(msg)], null); }\n"
                    + "};\n"
                    + "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }\n"
                    + "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }\n"
                    + "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }\n"
                    + "})();\n";

    // Runtime-синхронизация текущей усталости в AppVars.Tied из JS-массива hpmp[4] (остаток усталости).
    // curTire = 100 - hpmp[4].
    private static final String TIED_RUNTIME_PATCH =
            "/*ABCLIENT_MAP_RUNTIME_PATCH_TIED*/\n"
                    + "(function(){\n"
                    + "if (window.__ab_tied_patch_applied) return;\n"
                    + "window.__ab_tied_patch_applied = true;\n"
                    + "function __abPushTied(){\n"
                    + "  try {\n"
                    + "    if (!window.external || typeof window.external.SetCurrentTied !== 'function') return;\n"
                    + "    if (typeof hpmp === 'undefined' || !hpmp || hpmp.length < 5) return;\n"
                    + "    var __abMaxTire = parseInt(hpmp[4], 10);\n"
                    + "    if (isNaN(__abMaxTire)) return;\n"
                    + "    var __abCurTire = 100 - __abMaxTire;\n"
                    + "    if (__abCurTire < 0) __abCurTire = 0;\n"
                    + "    if (__abCurTire > 100) __abCurTire = 100;\n"
                    + "    window.external.SetCurrentTied(__abCurTire);\n"
                    + "  } catch (_ab_e_tied) {}\n"
                    + "}\n"
                    + "window.__abPushTied = __abPushTied;\n"
                    + "setInterval(__abPushTied, 1000);\n"
                    + "__abPushTied();\n"
                    + "})();\n";

    public static byte[] process(Context context, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        String serverJs = new String(array, WINDOWS_1251);
        String js = loadBaseMapJs(context, serverJs);
        boolean useAssetBase = !serverJs.equals(js);

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
                "window.__abReloadChList('/ch.php?lo=1');");
        // C# parity (ABClient/map.js): map_ajax шаг отправляется параметрами mx/my, а не x/y.
        // Это критично: сервер на x/y может отвечать "ERR", из-за чего навигатор строит путь, но не двигается.
        String moveParamsPatched = chatReloadSafePatched.replaceAll(
                "map_ajax\\.php\\?act=1&x='\\s*\\+\\s*x\\s*\\+\\s*'&y='\\s*\\+\\s*y\\s*\\+\\s*'&gti=",
                "map_ajax.php?act=1&mx=' + x + '&my=' + y + '&gti=");
        String popupSyncPatched = moveParamsPatched.replace(
                "if (!messb[0]) {",
                "if (!messb[0]) { try { if (window.external && typeof window.external.PostServerPopupToChat === 'function') { window.external.PostServerPopupToChat('Сервер прислал интерактивное окно действия.'); } } catch (_ab_e_popup) {}");
        String patchedWidthDecl = extractVarDecl(fishPatched, DECL_WIDTH_PATTERN);
        String patchedHeightDecl = extractVarDecl(fishPatched, DECL_HEIGHT_PATTERN);
        String patchedScaleDecl = extractScaleDecl(fishPatched);
        int getMapScaleCallCount = countMatches(popupSyncPatched, "GetMapScale\\s*\\(");
        int mapAjaxMxCount = countMatches(popupSyncPatched, "map_ajax\\.php\\?act=1&mx=");
        int mapAjaxXCount = countMatches(popupSyncPatched, "map_ajax\\.php\\?act=1&x=");

        String patched = MAP_JS_SAFE_PRELUDE + popupSyncPatched;
        boolean runtimePatchAppended = false;
        if (!patched.contains(ABCLIENT_MAP_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + OVERLOAD_RUNTIME_PATCH;
            runtimePatchAppended = true;
        }
        if (!patched.contains("/*ABCLIENT_MAP_RUNTIME_PATCH_TIED*/")) {
            patched += "\n" + TIED_RUNTIME_PATCH;
        }

        Log.d(TAG, "process: source=" + (useAssetBase ? "assets/js/map.js" : "server")
                + ", raw(width=" + rawWidthDecl + ",height=" + rawHeightDecl + ",scale=" + rawScaleDecl
                + ",width1Count=" + rawWidthOneCount + ",height1Count=" + rawHeightOneCount + ")"
                + ", patched(width=" + patchedWidthDecl + ",height=" + patchedHeightDecl + ",scale=" + patchedScaleDecl + ")"
                + ", dimPatch=" + (!dimPatched.equals(js))
                + ", fishPatch=" + (!fishPatched.equals(dimPatched))
                + ", chatReloadSafePatch=" + (!chatReloadSafePatched.equals(fishPatched))
                + ", moveParamsPatch=" + (!moveParamsPatched.equals(chatReloadSafePatched))
                + ", popupSyncPatch=" + (!popupSyncPatched.equals(moveParamsPatched))
                + ", mapAjaxMxCount=" + mapAjaxMxCount
                + ", mapAjaxXCount=" + mapAjaxXCount
                + ", getMapScaleCalls=" + getMapScaleCallCount
                + ", runtimePatchAppended=" + runtimePatchAppended);

        return patched.getBytes(WINDOWS_1251);
    }

    private static String loadBaseMapJs(Context context, String fallbackJs) {
        if (context == null) {
            return fallbackJs;
        }
        try (InputStream in = context.getAssets().open("js/map.js");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            String assetJs = baos.toString(StandardCharsets.UTF_8.name());
            if (assetJs == null || assetJs.trim().isEmpty()) {
                return fallbackJs;
            }
            // На случай BOM в файле ассета.
            if (!assetJs.isEmpty() && assetJs.charAt(0) == '\uFEFF') {
                assetJs = assetJs.substring(1);
            }
            return assetJs;
        } catch (Exception e) {
            Log.w(TAG, "process: failed to load assets/js/map.js, fallback to server js", e);
            return fallbackJs;
        }
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
