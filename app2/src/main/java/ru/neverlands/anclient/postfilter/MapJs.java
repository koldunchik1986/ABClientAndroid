package ru.neverlands.anclient.postfilter;

import android.content.Context;
import ru.neverlands.anclient.utils.AppLog;

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
    private static final String ANCLIENT_MAP_STUB_MARKER = "/*ANCLIENT_MAP_STUBS*/";
    private static final String ANCLIENT_MAP_RUNTIME_PATCH_MARKER = "/*ANCLIENT_MAP_RUNTIME_PATCHES*/";
    private static final String ANCLIENT_MAP_AUTO_CUT_RUNTIME_PATCH_MARKER = "/*ANCLIENT_MAP_RUNTIME_PATCH_AUTO_CUT*/";

    // C# parity для no-captcha в авто-рыбалке.
    private static final String FISH_NO_CAPTCHA_CONDITION_OLD =
            "if (!ingr[1] && window.external.IsAutoFish()) {";
    private static final String FISH_NO_CAPTCHA_CONDITION_NEW =
            "if ((!ingr[1] || ingr[1] == '00000') && window.external.IsAutoFish()) {";

    // Runtime-патч: любые server popup из map.js отправляются в чат и не рисуются как overlay-окна.
    // Unicode escapes используются, чтобы не зависеть от кодировки исходного map.js.
    private static final String OVERLOAD_RUNTIME_PATCH =
            ANCLIENT_MAP_RUNTIME_PATCH_MARKER + "\n"
                    + "(function(){\n"
                    + "if (window.__an_overload_patch_applied) return;\n"
                    + "window.__an_overload_patch_applied = true;\n"
                    + "if (typeof window.MessBoxDiv !== 'function') return;\n"
                    + "var __an_oldMessBoxDiv = window.MessBoxDiv;\n"
                    + "try {\n"
                    + "  var __an_style_id = 'an_server_popup_hide';\n"
                    + "  if (!document.getElementById(__an_style_id)) {\n"
                    + "    var __an_style = document.createElement('style');\n"
                    + "    __an_style.id = __an_style_id;\n"
                    + "    __an_style.type = 'text/css';\n"
                    + "    __an_style.textContent = '#static_window,#darker,#uni{display:none !important;}';\n"
                    + "    (document.head || document.documentElement).appendChild(__an_style);\n"
                    + "  }\n"
                    + "} catch (_an_e_style) {}\n"
                    + "function __anForwardPopup(mess){\n"
                    + "  try {\n"
                    + "    if (window.external && typeof window.external.PostServerPopupToChat === 'function') {\n"
                    + "      window.external.PostServerPopupToChat(mess == null ? '' : String(mess));\n"
                    + "    }\n"
                    + "  } catch (_an_e0) {}\n"
                    + "}\n"
                    + "window.MessBoxDiv = function(mess){\n"
                    + "  __anForwardPopup(mess);\n"
                    + "  try {\n"
                    + "    var __an_msg = (mess == null ? '' : String(mess)).toLowerCase();\n"
                    + "    var __an_overload = __an_msg.indexOf('\\u0440\\u044e\\u043a\\u0437\\u0430\\u043a') !== -1\n"
                    + "      || __an_msg.indexOf('\\u0437\\u0430\\u043c\\u0435\\u0434\\u043b\\u0435\\u043d') !== -1\n"
                    + "      || __an_msg.indexOf('\\u0442\\u044f\\u0436\\u0435\\u043b') !== -1;\n"
                    + "    var __an_canCheck = window.external && typeof window.external.ShowOverWarning === 'function';\n"
                    + "    if (__an_overload && __an_canCheck && !window.external.ShowOverWarning()) {\n"
                    + "      try { if (typeof window.external.FishOverload === 'function') { window.external.FishOverload(); } } catch (_an_e1) {}\n"
                    + "      return;\n"
                    + "    }\n"
                    + "  } catch (_an_e2) {}\n"
                    + "  return;\n"
                    + "};\n"
                    + "})();\n";

    /**
     * Базовый prelude:
     * - ставит alias `window.external = window.AndroidBridge`;
     * - добавляет no-op stubs для функций, которые map.js вызывает до полной инициализации.
     */
    private static final String MAP_JS_SAFE_PRELUDE =
            ANCLIENT_MAP_STUB_MARKER + "\n"
                    + "(function(){\n"
                    + "var __an = (typeof window.AndroidBridge !== 'undefined') ? window.AndroidBridge : null;\n"
                    + "function __anCall(name,args,defVal){\n"
                    + "  try {\n"
                    + "    if (__an && typeof __an[name] === 'function') {\n"
                    + "      var v = __an[name].apply(__an, args || []);\n"
                    + "      return (typeof v === 'undefined' || v === null) ? defVal : v;\n"
                    + "    }\n"
                    + "  } catch (_an_e) {}\n"
                    + "  return defVal;\n"
                    + "}\n"
                    + "window.__anReloadChList = function(url){\n"
                    + "  try {\n"
                    + "    if (window.parent && window.parent.frames && window.parent.frames['ch_list']) {\n"
                    + "      window.parent.frames['ch_list'].location = url;\n"
                    + "      return;\n"
                    + "    }\n"
                    + "  } catch (_an_e3) {}\n"
                    + "  try {\n"
                    + "    if (__an && typeof __an.loadFrame === 'function') {\n"
                    + "      __an.loadFrame('ch_list', url);\n"
                    + "    }\n"
                    + "  } catch (_an_e4) {}\n"
                    + "};\n"
                    + "window.external = {\n"
                    + "  GetHalfMapWidth: function(){ return parseInt(__anCall('GetHalfMapWidth', [], 4), 10) || 4; },\n"
                    + "  GetHalfMapHeight: function(){ return parseInt(__anCall('GetHalfMapHeight', [], 3), 10) || 3; },\n"
                    + "  GetMapScale: function(){\n"
                    + "    var __an_scale = parseInt(__anCall('GetMapScale', [], 75), 10);\n"
                    + "    if (isNaN(__an_scale) || __an_scale < 50 || __an_scale > 150) __an_scale = 75;\n"
                    + "    return __an_scale;\n"
                    + "  },\n"
                    + "  UsersOnline: function(){ return String(__anCall('UsersOnline', [], '')); },\n"
                    + "  DoHerbAutoCut: function(){ return !!__anCall('DoHerbAutoCut', [], false); },\n"
                    + "  IsCellExists: function(x,y){ return !!__anCall('IsCellExists', [x,y], true); },\n"
                    + "  CellAltText: function(x,y,scale){ return String(__anCall('CellAltText', [x,y,scale], '')); },\n"
                    + "  GenMoveLink: function(x,y){ return String(__anCall('GenMoveLink', [x,y], '')); },\n"
                    + "  MoveTo: function(dest){ return __anCall('MoveTo', [dest], null); },\n"
                    + "  CellDivText: function(x,y,scale,link,showmove,isframe){ return String(__anCall('CellDivText', [x,y,scale,link,showmove,isframe], '')); },\n"
                    + "  DoHideMiniMap: function(){ return !!__anCall('DoHideMiniMap', [], false); },\n"
                    + "  MapText: function(){ return String(__anCall('MapText', [], '')); },\n"
                    + "  HerbsList: function(list){ return String(__anCall('HerbsList', [String(list)], '')); },\n"
                    + "  IsHerbAutoCut: function(herb){ return !!__anCall('IsHerbAutoCut', [String(herb)], false); },\n"
                    + "  HerbCut: function(herb){ return __anCall('HerbCut', [String(herb)], null); },\n"
                    + "  TraceCut: function(herb){ return __anCall('TraceCut', [String(herb)], null); },\n"
                    + "  TraceAutoCutRuntime: function(message){ return __anCall('TraceAutoCutRuntime', [String(message)], null); },\n"
                    + "  SetNeverTimer: function(ms){ return __anCall('SetNeverTimer', [ms], null); },\n"
                    + "  SetAutoFishMassa: function(v){ return __anCall('SetAutoFishMassa', [v], null); },\n"
                    + "  CheckPri: function(name,myst){ return String(__anCall('CheckPri', [name,myst], '')); },\n"
                    + "  InsertGuaDiv: function(code){ return String(__anCall('InsertGuaDiv', [code], '')); },\n"
                    + "  FishOverload: function(){ return __anCall('FishOverload', [], null); },\n"
                    + "  IsAutoFish: function(){ return !!__anCall('IsAutoFish', [], false); },\n"
                    + "  SetFishNoCaptchaReady: function(){ return __anCall('SetFishNoCaptchaReady', [], null); },\n"
                    + "  ShowOverWarning: function(){ return !!__anCall('ShowOverWarning', [], false); },\n"
                    + "  SetCurrentTied: function(v){ return __anCall('SetCurrentTied', [v], null); },\n"
                    + "  PostServerPopupToChat: function(message){ return __anCall('PostServerPopupToChat', [String(message)], null); },\n"
                    + "  TraceMapRuntime: function(msg){ return __anCall('TraceMapRuntime', [String(msg)], null); }\n"
                    + "};\n"
                    + "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }\n"
                    + "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }\n"
                    + "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }\n"
                    + "})();\n";

    // Runtime-синхронизация текущей усталости в AppVars.Tied из JS-массива hpmp[4] (остаток усталости).
    // curTire = 100 - hpmp[4].
    private static final String TIED_RUNTIME_PATCH =
            "/*ANCLIENT_MAP_RUNTIME_PATCH_TIED*/\n"
                    + "(function(){\n"
                    + "if (window.__an_tied_patch_applied) return;\n"
                    + "window.__an_tied_patch_applied = true;\n"
                    + "function __anPushTied(){\n"
                    + "  try {\n"
                    + "    if (!window.external || typeof window.external.SetCurrentTied !== 'function') return;\n"
                    + "    if (typeof hpmp === 'undefined' || !hpmp || hpmp.length < 5) return;\n"
                    + "    var __anMaxTire = parseInt(hpmp[4], 10);\n"
                    + "    if (isNaN(__anMaxTire)) return;\n"
                    + "    var __anCurTire = 100 - __anMaxTire;\n"
                    + "    if (__anCurTire < 0) __anCurTire = 0;\n"
                    + "    if (__anCurTire > 100) __anCurTire = 100;\n"
                    + "    window.external.SetCurrentTied(__anCurTire);\n"
                    + "  } catch (_an_e_tied) {}\n"
                    + "}\n"
                    + "window.__anPushTied = __anPushTied;\n"
                    + "setInterval(__anPushTied, 1000);\n"
                    + "__anPushTied();\n"
                    + "})();\n";

    /**
     * Runtime fallback for AutoCut: the primary hook lives in assets/js/map.js ButtonGen/ReAddBut.
     * This wrapper re-checks the same mapbt button after view_map() finishes, so a missed initial
     * document.write phase cannot leave AutoCut stuck on a ready cell.
     */
    private static final String AUTO_CUT_RUNTIME_PATCH =
            ANCLIENT_MAP_AUTO_CUT_RUNTIME_PATCH_MARKER + "\n"
                    + "(function(){\n"
                    + "if (window.__an_auto_cut_patch_applied) return;\n"
                    + "window.__an_auto_cut_patch_applied = true;\n"
                    + "function __anTraceAutoCut(msg){\n"
                    + "  try {\n"
                    + "    if (window.external && typeof window.external.TraceAutoCutRuntime === 'function') {\n"
                    + "      window.external.TraceAutoCutRuntime(msg);\n"
                    + "    }\n"
                    + "  } catch (_an_e) {}\n"
                    + "}\n"
                    + "function __anFindOglCode(){\n"
                    + "  try {\n"
                    + "    if (typeof mapbt === 'undefined' || !mapbt) return '';\n"
                    + "    var __anLegacyOglCode = '';\n"
                    + "    for (var i = 0; i < mapbt.length; i++) {\n"
                    + "      if (!mapbt[i]) continue;\n"
                    + "      if (mapbt[i][0] === 'look') return mapbt[i][2] || '';\n"
                    + "      if (mapbt[i][0] === 'ogl') __anLegacyOglCode = mapbt[i][2] || '';\n"
                    + "    }\n"
                    + "    return __anLegacyOglCode;\n"
                    + "  } catch (_an_e) {}\n"
                    + "  return '';\n"
                    + "}\n"
                    + "window.__anTryAutoCutFromMap = function(source){\n"
                    + "  var code = __anFindOglCode();\n"
                    + "  if (!code) {\n"
                    + "    __anTraceAutoCut('skip no look/ogl button, source=' + source);\n"
                    + "    return;\n"
                    + "  }\n"
                    + "  if (typeof window.AnTryAutoCutOgl === 'function') {\n"
                    + "    window.AnTryAutoCutOgl(code, source);\n"
                    + "    return;\n"
                    + "  }\n"
                    + "  try {\n"
                    + "    if (!window.external || typeof window.external.DoHerbAutoCut !== 'function' || !window.external.DoHerbAutoCut()) {\n"
                    + "      __anTraceAutoCut('skip fallback guard false, source=' + source);\n"
                    + "      return;\n"
                    + "    }\n"
                    + "  } catch (_an_e_guard) {\n"
                    + "    __anTraceAutoCut('skip fallback bridge error, source=' + source + ', error=' + _an_e_guard);\n"
                    + "    return;\n"
                    + "  }\n"
                    + "  var now = (new Date()).getTime();\n"
                    + "  if (window.__an_auto_cut_fallback_guard_until && now < window.__an_auto_cut_fallback_guard_until) {\n"
                    + "    __anTraceAutoCut('skip fallback guard, source=' + source);\n"
                    + "    return;\n"
                    + "  }\n"
                    + "  window.__an_auto_cut_fallback_guard_until = now + 3000;\n"
                    + "  __anTraceAutoCut('start fallback Ogl, source=' + source);\n"
                    + "  try { Ogl(code); } catch (_an_e_ogl) { __anTraceAutoCut('fallback Ogl failed, source=' + source + ', error=' + _an_e_ogl); }\n"
                    + "};\n"
                    + "if (typeof window.view_map === 'function' && !window.__an_auto_cut_view_map_wrapped) {\n"
                    + "  var __anOldViewMap = window.view_map;\n"
                    + "  window.view_map = function(){\n"
                    + "    var result = __anOldViewMap.apply(this, arguments);\n"
                    + "    setTimeout(function(){ window.__anTryAutoCutFromMap('view_map'); }, 50);\n"
                    + "    return result;\n"
                    + "  };\n"
                    + "  window.__an_auto_cut_view_map_wrapped = true;\n"
                    + "}\n"
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
                "window.__anReloadChList('/ch.php?lo=1');");
        // C# parity (ABClient/map.js): map_ajax шаг отправляется параметрами mx/my, а не x/y.
        // Это критично: сервер на x/y может отвечать "ERR", из-за чего навигатор строит путь, но не двигается.
        String moveParamsPatched = chatReloadSafePatched.replaceAll(
                "map_ajax\\.php\\?act=1&x='\\s*\\+\\s*x\\s*\\+\\s*'&y='\\s*\\+\\s*y\\s*\\+\\s*'&gti=",
                "map_ajax.php?act=1&mx=' + x + '&my=' + y + '&gti=");
        String popupSyncPatched = moveParamsPatched.replace(
                "if (!messb[0]) {",
                "if (!messb[0]) { try { if (window.external && typeof window.external.PostServerPopupToChat === 'function') { window.external.PostServerPopupToChat('Сервер прислал интерактивное окно действия.'); } } catch (_an_e_popup) {}");
        String patchedWidthDecl = extractVarDecl(fishPatched, DECL_WIDTH_PATTERN);
        String patchedHeightDecl = extractVarDecl(fishPatched, DECL_HEIGHT_PATTERN);
        String patchedScaleDecl = extractScaleDecl(fishPatched);
        int getMapScaleCallCount = countMatches(popupSyncPatched, "GetMapScale\\s*\\(");
        int mapAjaxMxCount = countMatches(popupSyncPatched, "map_ajax\\.php\\?act=1&mx=");
        int mapAjaxXCount = countMatches(popupSyncPatched, "map_ajax\\.php\\?act=1&x=");

        String patched = MAP_JS_SAFE_PRELUDE + popupSyncPatched;
        boolean runtimePatchAppended = false;
        if (!patched.contains(ANCLIENT_MAP_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + OVERLOAD_RUNTIME_PATCH;
            runtimePatchAppended = true;
        }
        if (!patched.contains("/*ANCLIENT_MAP_RUNTIME_PATCH_TIED*/")) {
            patched += "\n" + TIED_RUNTIME_PATCH;
        }
        if (!patched.contains(ANCLIENT_MAP_AUTO_CUT_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + AUTO_CUT_RUNTIME_PATCH;
        }

        AppLog.d(TAG, "process: source=" + (useAssetBase ? "assets/js/map.js" : "server")
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
            AppLog.w(TAG, "process: failed to load assets/js/map.js, fallback to server js", e);
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
