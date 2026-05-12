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
    private static final String ANCLIENT_MAP_CELL_INFO_RUNTIME_PATCH_MARKER = "/*ANCLIENT_MAP_RUNTIME_PATCH_CELL_INFO*/";

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
                    + "  CurrentCellFullInfo: function(){ return String(__anCall('CurrentCellFullInfo', [], '')); },\n"
                    + "  IsCurrentCellFullInfoEnabled: function(){ return !!__anCall('IsCurrentCellFullInfoEnabled', [], true); },\n"
                    + "  SelectedCellFullInfo: function(x,y){ return String(__anCall('SelectedCellFullInfo', [x,y], '')); },\n"
                    + "  UpdateCurrentCellFromCoords: function(x,y,source){ return __anCall('UpdateCurrentCellFromCoords', [x,y,String(source || '')], null); },\n"
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

    /**
     * Runtime-patch для modern-блока полной информации клетки.
     *
     * Почему не только `assets/js/map.js view_build_bottom()`:
     * - логи показали, что серверный `/js/map.js?v=6` подменяется через `MapJs`, но вызова
     *   `CurrentCellFullInfo()` в bridge нет, значит конкретная загруженная версия map.js могла
     *   не дойти до изменённого `view_build_bottom()` или отрисовать bottom вне видимой области;
     * - этот patch цепляется к уже существующему `view_map()` и добавляет блок рядом с DOM карты
     *   после завершения document.write/showMap, не создавая нового HTTP-контура.
     */
    private static final String CURRENT_CELL_INFO_RUNTIME_PATCH =
            ANCLIENT_MAP_CELL_INFO_RUNTIME_PATCH_MARKER + "\n"
                    + "(function(){\n"
                    + "if (window.__an_cell_info_patch_applied) return;\n"
                    + "window.__an_cell_info_patch_applied = true;\n"
                    + "function __anTraceCellInfo(msg){\n"
                    + "  try { if (window.external && typeof window.external.TraceMapRuntime === 'function') window.external.TraceMapRuntime('CELL_INFO ' + msg); } catch (_e) {}\n"
                    + "}\n"
                    + "var __anSelectedCellInfo = null;\n"
                    + "var __anLongPressTimer = null;\n"
                    + "var __anSuppressClickUntil = 0;\n"
                    + "function __anEnsureCellInfoStyles(){\n"
                    + "  try {\n"
                    + "    var css = '.an-cell-info{max-width:940px;margin:12px auto 6px;padding:14px;border-radius:18px;background:linear-gradient(135deg,#111827 0%,#1f2937 46%,#0f766e 100%);box-shadow:0 14px 34px rgba(15,23,42,.35);color:#e5e7eb;font-family:Verdana,Arial,sans-serif;text-align:left;border:1px solid rgba(255,255,255,.18)}.an-cell-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:12px}.an-cell-kicker{display:inline-block;font-size:10px;text-transform:uppercase;letter-spacing:.12em;color:#93c5fd}.an-cell-title{font-size:18px;font-weight:bold;color:#fff;margin-top:3px}.an-cell-subtitle{font-size:12px;color:#cbd5e1;margin-top:3px}.an-cell-cost{border:1px solid;border-radius:999px;padding:7px 10px;background:rgba(15,23,42,.72);font-weight:bold;white-space:nowrap}.an-cell-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.an-cell-card{background:rgba(255,255,255,.09);border:1px solid rgba(255,255,255,.13);border-top:3px solid;border-radius:14px;padding:10px;min-height:66px}.an-cell-card-title{font-weight:bold;font-size:12px;margin-bottom:7px}.an-cell-card-body{font-size:12px;line-height:1.45;color:#f8fafc}.an-cell-chip{display:inline-block;border-radius:999px;background:rgba(136,187,221,.18);border:1px solid rgba(136,187,221,.45);padding:2px 7px;margin:1px 2px 2px 0;color:#dbeafe;font-weight:bold}.an-cell-chip.blue{background:rgba(51,204,255,.16);border-color:rgba(51,204,255,.45);color:#bae6fd}.an-cell-chip.green{background:rgba(52,211,153,.16);border-color:rgba(52,211,153,.45);color:#bbf7d0}.an-cell-muted{color:#94a3b8}.an-cell-empty{color:#cbd5e1;text-align:center;font-weight:bold}@media(max-width:720px){.an-cell-info{margin:10px 6px;border-radius:14px}.an-cell-head{display:block}.an-cell-cost{display:inline-block;margin-top:8px}.an-cell-grid{grid-template-columns:1fr}}';\n"
                    + "    var s = document.getElementById('an_cell_info_styles');\n"
                    + "    if (!s || String(s.tagName || '').toUpperCase() !== 'STYLE') {\n"
                    + "      s = document.createElement('style');\n"
                    + "      s.id = 'an_cell_info_styles';\n"
                    + "      s.type = 'text/css';\n"
                    + "    }\n"
                    + "    if (s.styleSheet) s.styleSheet.cssText = css; else s.textContent = css;\n"
                    + "    var styleHost = document.head || document.getElementsByTagName('head')[0] || document.documentElement || document.body;\n"
                    + "    if (styleHost && s.parentNode !== styleHost) styleHost.appendChild(s);\n"
                    + "    try { if (s.sheet) s.sheet.disabled = false; } catch (_e_sheet) {}\n"
                    + "  } catch (_e_style) { __anTraceCellInfo('style error ' + _e_style); }\n"
                    + "}\n"
                    + "function __anApplyCellInfoInlineStyles(root){\n"
                    + "  try {\n"
                    + "    if (!root || !root.getElementsByTagName) return;\n"
                    + "    var infos = root.getElementsByClassName ? root.getElementsByClassName('an-cell-info') : [];\n"
                    + "    for (var i=0;i<infos.length;i++) { infos[i].style.cssText += ';max-width:940px;margin:12px auto 6px;padding:14px;border-radius:18px;background:linear-gradient(135deg,#111827 0%,#1f2937 46%,#0f766e 100%);box-shadow:0 14px 34px rgba(15,23,42,.35);color:#e5e7eb;font-family:Verdana,Arial,sans-serif;text-align:left;border:1px solid rgba(255,255,255,.18);'; }\n"
                    + "    var heads = root.getElementsByClassName ? root.getElementsByClassName('an-cell-head') : [];\n"
                    + "    for (var h=0;h<heads.length;h++) { heads[h].style.cssText += ';display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:12px;'; }\n"
                    + "    var kickers = root.getElementsByClassName ? root.getElementsByClassName('an-cell-kicker') : [];\n"
                    + "    for (var k=0;k<kickers.length;k++) { kickers[k].style.cssText += ';display:inline-block;font-size:10px;text-transform:uppercase;letter-spacing:.12em;color:#93c5fd;'; }\n"
                    + "    var titles = root.getElementsByClassName ? root.getElementsByClassName('an-cell-title') : [];\n"
                    + "    for (var t=0;t<titles.length;t++) { titles[t].style.cssText += ';font-size:18px;font-weight:bold;color:#fff;margin-top:3px;'; }\n"
                    + "    var subtitles = root.getElementsByClassName ? root.getElementsByClassName('an-cell-subtitle') : [];\n"
                    + "    for (var st=0;st<subtitles.length;st++) { subtitles[st].style.cssText += ';font-size:12px;color:#cbd5e1;margin-top:3px;'; }\n"
                    + "    var costs = root.getElementsByClassName ? root.getElementsByClassName('an-cell-cost') : [];\n"
                    + "    for (var co=0;co<costs.length;co++) { costs[co].style.cssText += ';border:1px solid;border-radius:999px;padding:7px 10px;background:rgba(15,23,42,.72);font-weight:bold;white-space:nowrap;'; }\n"
                    + "    var grids = root.getElementsByClassName ? root.getElementsByClassName('an-cell-grid') : [];\n"
                    + "    for (var g=0;g<grids.length;g++) { grids[g].style.cssText += ';display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;'; }\n"
                    + "    var cards = root.getElementsByClassName ? root.getElementsByClassName('an-cell-card') : [];\n"
                    + "    for (var c=0;c<cards.length;c++) { cards[c].style.cssText += ';background:rgba(255,255,255,.09);border:1px solid rgba(255,255,255,.13);border-top:3px solid;border-radius:14px;padding:10px;min-height:66px;'; }\n"
                    + "    var cardTitles = root.getElementsByClassName ? root.getElementsByClassName('an-cell-card-title') : [];\n"
                    + "    for (var ct=0;ct<cardTitles.length;ct++) { cardTitles[ct].style.cssText += ';font-weight:bold;font-size:12px;margin-bottom:7px;'; }\n"
                    + "    var bodies = root.getElementsByClassName ? root.getElementsByClassName('an-cell-card-body') : [];\n"
                    + "    for (var b=0;b<bodies.length;b++) { bodies[b].style.cssText += ';font-size:12px;line-height:1.45;color:#f8fafc;'; }\n"
                    + "    var chips = root.getElementsByClassName ? root.getElementsByClassName('an-cell-chip') : [];\n"
                    + "    for (var ch=0;ch<chips.length;ch++) { chips[ch].style.cssText += ';display:inline-block;border-radius:999px;background:rgba(136,187,221,.18);border:1px solid rgba(136,187,221,.45);padding:2px 7px;margin:1px 2px 2px 0;color:#dbeafe;font-weight:bold;'; if (String(chips[ch].className).indexOf('blue') >= 0) chips[ch].style.cssText += ';background:rgba(51,204,255,.16);border-color:rgba(51,204,255,.45);color:#bae6fd;'; if (String(chips[ch].className).indexOf('green') >= 0) chips[ch].style.cssText += ';background:rgba(52,211,153,.16);border-color:rgba(52,211,153,.45);color:#bbf7d0;'; }\n"
                    + "    var muted = root.getElementsByClassName ? root.getElementsByClassName('an-cell-muted') : [];\n"
                    + "    for (var m=0;m<muted.length;m++) { muted[m].style.cssText += ';color:#94a3b8;'; }\n"
                    + "  } catch (_e_inline_style) { __anTraceCellInfo('inline style error ' + _e_inline_style); }\n"
                    + "}\n"
                    + "function __anRenderCellInfo(){\n"
                    + "  try {\n"
                    + "    var holder = document.getElementById('an_current_cell_info_host');\n"
                    + "    if (window.external && typeof window.external.IsCurrentCellFullInfoEnabled === 'function' && !window.external.IsCurrentCellFullInfoEnabled()) {\n"
                    + "      if (holder) { holder.innerHTML = ''; holder.style.display = 'none'; }\n"
                    + "      __anTraceCellInfo('hidden by settings');\n"
                    + "      return;\n"
                    + "    }\n"
                    + "    __anEnsureCellInfoStyles();\n"
                    + "    if (!window.external || typeof window.external.CurrentCellFullInfo !== 'function') { __anTraceCellInfo('skip no bridge'); return; }\n"
                    + "    var html = '';\n"
                    + "    if (__anSelectedCellInfo && typeof window.external.SelectedCellFullInfo === 'function') html = String(window.external.SelectedCellFullInfo(__anSelectedCellInfo.x, __anSelectedCellInfo.y) || '');\n"
                    + "    if (!html) html = String(window.external.CurrentCellFullInfo() || '');\n"
                    + "    if (!html) { __anTraceCellInfo('skip empty html'); return; }\n"
                    + "    var anchor = document.getElementById('world_host') || document.getElementById('world_cont2') || document.getElementById('world_cont');\n"
                    + "    if (!anchor || !anchor.parentNode) { __anTraceCellInfo('skip no map anchor'); return; }\n"
                    + "    var insertAfter = anchor;\n"
                    + "    try {\n"
                    + "      var node = anchor;\n"
                    + "      while (node && node !== document.body) {\n"
                    + "        if (String(node.tagName || '').toUpperCase() === 'TABLE') { insertAfter = node; break; }\n"
                    + "        node = node.parentNode;\n"
                    + "      }\n"
                    + "    } catch (_e_anchor_walk) {}\n"
                    + "    if (!insertAfter || !insertAfter.parentNode) insertAfter = anchor;\n"
                    + "    holder = document.getElementById('an_current_cell_info_host');\n"
                    + "    if (!holder) {\n"
                    + "      holder = document.createElement('div');\n"
                    + "      holder.id = 'an_current_cell_info_host';\n"
                    + "    }\n"
                    + "    holder.style.display = 'block';\n"
                    + "    holder.style.clear = 'both';\n"
                    + "    holder.style.width = '100%';\n"
                    + "    holder.style.boxSizing = 'border-box';\n"
                    + "    if (holder.parentNode !== insertAfter.parentNode || holder.previousSibling !== insertAfter) {\n"
                    + "      insertAfter.parentNode.insertBefore(holder, insertAfter.nextSibling);\n"
                    + "      __anTraceCellInfo('host attached after map table, anchor=' + (anchor.id || anchor.tagName || '') + ', after=' + (insertAfter.id || insertAfter.tagName || ''));\n"
                    + "    }\n"
                    + "    holder.innerHTML = html;\n"
                    + "    __anApplyCellInfoInlineStyles(holder);\n"
                    + "    __anTraceCellInfo('rendered len=' + html.length);\n"
                    + "  } catch (_e_render) { __anTraceCellInfo('error ' + _e_render); }\n"
                    + "}\n"
                    + "window.__anRenderCellInfo = __anRenderCellInfo;\n"
                    + "function __anClearSelectedCellInfo(source){\n"
                    + "  if (__anSelectedCellInfo) __anTraceCellInfo('selected clear source=' + source + ', cell=' + __anSelectedCellInfo.x + ':' + __anSelectedCellInfo.y);\n"
                    + "  __anSelectedCellInfo = null;\n"
                    + "}\n"
                    + "function __anCellCoordsFromEventTarget(target){\n"
                    + "  var node = target;\n"
                    + "  while (node && node !== document.body) {\n"
                    + "    var id = String(node.id || '');\n"
                    + "    var prefix = id.indexOf('divtext_') === 0 ? 'divtext_' : (id.indexOf('img_') === 0 ? 'img_' : '');\n"
                    + "    if (prefix) {\n"
                    + "      var parts = id.substring(prefix.length).split('_');\n"
                    + "      if (parts.length === 2) {\n"
                    + "        var x = parseInt(parts[0], 10);\n"
                    + "        var y = parseInt(parts[1], 10);\n"
                    + "        if (!isNaN(x) && !isNaN(y)) return {x:x,y:y};\n"
                    + "      }\n"
                    + "    }\n"
                    + "    node = node.parentNode;\n"
                    + "  }\n"
                    + "  return null;\n"
                    + "}\n"
                    + "function __anStartCellLongPress(evt){\n"
                    + "  try {\n"
                    + "    var coords = __anCellCoordsFromEventTarget(evt.target || evt.srcElement);\n"
                    + "    if (!coords) return;\n"
                    + "    if (__anLongPressTimer) clearTimeout(__anLongPressTimer);\n"
                    + "    __anLongPressTimer = setTimeout(function(){\n"
                    + "      __anLongPressTimer = null;\n"
                    + "      if (window.__an_map_pan_active) { __anTraceCellInfo('long press skipped during map pan'); return; }\n"
                    + "      __anSelectedCellInfo = coords;\n"
                    + "      __anSuppressClickUntil = new Date().getTime() + 900;\n"
                    + "      __anTraceCellInfo('selected by long press ' + coords.x + ':' + coords.y);\n"
                    + "      __anRenderCellInfo();\n"
                    + "    }, 650);\n"
                    + "  } catch (_e_lp_start) { __anTraceCellInfo('long press start error ' + _e_lp_start); }\n"
                    + "}\n"
                    + "function __anCancelCellLongPress(){ if (__anLongPressTimer) { clearTimeout(__anLongPressTimer); __anLongPressTimer = null; } }\n"
                     + "function __anSuppressLongPressClick(evt){\n"
                    + "  try {\n"
                    + "    var clickCoords = __anCellCoordsFromEventTarget(evt.target || evt.srcElement);\n"
                    + "    if (!clickCoords) return;\n"
                    + "    if (new Date().getTime() > __anSuppressClickUntil) { __anClearSelectedCellInfo('cell click'); return; }\n"
                    + "    if (evt.preventDefault) evt.preventDefault();\n"
                    + "    if (evt.stopImmediatePropagation) evt.stopImmediatePropagation();\n"
                    + "    if (evt.stopPropagation) evt.stopPropagation();\n"
                    + "    evt.cancelBubble = true;\n"
                    + "    evt.returnValue = false;\n"
                     + "  } catch (_e_lp_click) {}\n"
                     + "}\n"
                    + "function __anShowSelectedCellInfo(coords, source, evt){\n"
                    + "  try {\n"
                    + "    if (!coords) return false;\n"
                    + "    __anSelectedCellInfo = coords;\n"
                    + "    __anSuppressClickUntil = new Date().getTime() + 900;\n"
                    + "    if (evt) {\n"
                    + "      if (evt.preventDefault) evt.preventDefault();\n"
                    + "      if (evt.stopImmediatePropagation) evt.stopImmediatePropagation();\n"
                    + "      if (evt.stopPropagation) evt.stopPropagation();\n"
                    + "      evt.cancelBubble = true;\n"
                    + "      evt.returnValue = false;\n"
                    + "    }\n"
                    + "    __anTraceCellInfo('selected by ' + source + ' ' + coords.x + ':' + coords.y);\n"
                    + "    __anRenderCellInfo();\n"
                    + "    return false;\n"
                    + "  } catch (_e_selected_show) { __anTraceCellInfo('selected show error ' + _e_selected_show); }\n"
                    + "  return false;\n"
                    + "}\n"
                    + "function __anCellContextMenu(evt){\n"
                    + "  try { return __anShowSelectedCellInfo(__anCellCoordsFromEventTarget(evt.target || evt.srcElement), 'contextmenu', evt); } catch (_e_ctx) { __anTraceCellInfo('contextmenu error ' + _e_ctx); }\n"
                    + "  return false;\n"
                    + "}\n"
                     + "function __anInstallCellLongPress(){\n"
                     + "  if (window.__an_cell_info_long_press_installed) return;\n"
                     + "  window.__an_cell_info_long_press_installed = true;\n"
                     + "  if (document.addEventListener) {\n"
                     + "    document.addEventListener('touchstart', __anStartCellLongPress, true);\n"
                     + "    document.addEventListener('touchend', __anCancelCellLongPress, true);\n"
                     + "    document.addEventListener('touchcancel', __anCancelCellLongPress, true);\n"
                     + "    document.addEventListener('mousedown', __anStartCellLongPress, true);\n"
                     + "    document.addEventListener('mousemove', __anCancelCellLongPress, true);\n"
                     + "    document.addEventListener('mouseup', __anCancelCellLongPress, true);\n"
                     + "    document.addEventListener('mouseleave', __anCancelCellLongPress, true);\n"
                     + "    document.addEventListener('click', __anSuppressLongPressClick, true);\n"
                    + "    document.addEventListener('contextmenu', __anCellContextMenu, true);\n"
                    + "    __anTraceCellInfo('long press handlers installed');\n"
                     + "  }\n"
                     + "}\n"
                    + "function __anSyncCurrentCellFromGlobals(source){\n"
                    + "  __anClearSelectedCellInfo(source);\n"
                    + "  try { if (window.external && typeof window.external.UpdateCurrentCellFromCoords === 'function' && typeof window.current_x !== 'undefined' && typeof window.current_y !== 'undefined') window.external.UpdateCurrentCellFromCoords(parseInt(window.current_x), parseInt(window.current_y), source); } catch (_e_sync) { __anTraceCellInfo('sync error ' + _e_sync); }\n"
                    + "  setTimeout(__anRenderCellInfo, 80);\n"
                    + "  setTimeout(__anRenderCellInfo, 500);\n"
                    + "}\n"
                    + "function __anRefreshCellInfoOnly(){ setTimeout(__anRenderCellInfo, 80); setTimeout(__anRenderCellInfo, 500); }\n"
                    + "function __anStopMovingFlashCompat(source){\n"
                    + "  try { window.an_moving_flash_active = false; } catch (_e_flag) {}\n"
                    + "  try { var movingcell = document.getElementById('movingcell'); if (movingcell) { movingcell.style.borderColor = 'red'; movingcell.className = ''; movingcell.removeAttribute('id'); } } catch (_e_cell) {}\n"
                    + "  __anTraceCellInfo('moving flash compat stop source=' + source);\n"
                    + "}\n"
                    + "if (typeof window.finFunction === 'function' && !window.__an_cell_info_fin_wrapped) {\n"
                    + "  var __anOldCellInfoFinFunction = window.finFunction;\n"
                    + "  window.finFunction = function(){ var result = __anOldCellInfoFinFunction.apply(this, arguments); __anStopMovingFlashCompat('finFunction wrapper'); __anSyncCurrentCellFromGlobals('finFunction wrapper'); return result; };\n"
                    + "  window.__an_cell_info_fin_wrapped = true;\n"
                    + "}\n"
                    + "if (typeof window.timerst === 'function' && !window.__an_cell_info_timer_wrapped) {\n"
                    + "  var __anOldCellInfoTimerst = window.timerst;\n"
                    + "  window.timerst = function(lp){ var result = __anOldCellInfoTimerst.apply(this, arguments); try { if (typeof window.time_left_sec !== 'undefined' && window.time_left_sec <= 0) { __anStopMovingFlashCompat('timerst wrapper'); __anRefreshCellInfoOnly(); } } catch (_e_timer) {} return result; };\n"
                    + "  window.__an_cell_info_timer_wrapped = true;\n"
                    + "}\n"
                    + "if (typeof window.view_map === 'function' && !window.__an_cell_info_view_map_wrapped) {\n"
                    + "  var __anOldCellInfoViewMap = window.view_map;\n"
                    + "  window.view_map = function(){\n"
                    + "    var result = __anOldCellInfoViewMap.apply(this, arguments);\n"
                    + "    __anSyncCurrentCellFromGlobals('view_map wrapper');\n"
                    + "    setTimeout(__anRenderCellInfo, 120);\n"
                    + "    setTimeout(__anRenderCellInfo, 700);\n"
                    + "    return result;\n"
                    + "  };\n"
                    + "  window.__an_cell_info_view_map_wrapped = true;\n"
                    + "}\n"
                    + "__anInstallCellLongPress();\n"
                    + "setTimeout(__anRenderCellInfo, 500);\n"
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
        if (!patched.contains(ANCLIENT_MAP_CELL_INFO_RUNTIME_PATCH_MARKER)) {
            patched += "\n" + CURRENT_CELL_INFO_RUNTIME_PATCH;
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
                + ", runtimePatchAppended=" + runtimePatchAppended
                + ", cellInfoPatch=" + patched.contains(ANCLIENT_MAP_CELL_INFO_RUNTIME_PATCH_MARKER));

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
