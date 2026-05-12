var d = document;
var world = false;
var transport_img = false;
var timer_img = false;
var timer_sec = false;
var width = window.external.GetHalfMapWidth(); // var width = 3;
var height = window.external.GetHalfMapHeight(); // var height = 1;
var move_interval = 50;
var current_x = 0;
var current_y = 0;
var time_left = 0;
var time_left_sec = 0;
var pause = 0;
var t = 0;
var tsec = 0;
var cur_margin_top = 0;
var cur_margin_left = 0;
var dest_x = 0;
var dest_y = 0;
var loaded_left = 0;
var loaded_right = 0;
var loaded_top = 0;
var loaded_bottom = 0;
var moving_status = 0;
var finStatus = 0;
var an_moving_flash_active = false;
var an_moving_flash_timeout = false;
var an_map_pan_installed = false;
var an_map_pan_touch = false;
var an_map_pan_dragging = false;
var an_map_pan_start_x = 0;
var an_map_pan_start_y = 0;
var an_map_pan_last_x = 0;
var an_map_pan_last_y = 0;
var an_map_pan_suppress_click_until = 0;
var gox = 0;
var goy = 0;
var gop = 0;
var avail = new Array();
var bavail = new Array();
var classn = false;
var MESSD = false;
var MDARK = false;
var rinit = 0;
var pngAlpha = 1;
var ua = navigator.userAgent.toLowerCase();

// +ABC
var scale = window.external.GetMapScale();
var abcmapwidth = (((width * 2) + 1) * scale) + (width * 2) + 2;
var abcmapheight = (((height * 2) + 1) * scale) + (height * 2) + 2;
// -ABC

this.isIE = ((ua.indexOf('msie') != -1) && !(ua.indexOf('opera') != -1) && (ua.indexOf('webtv') == -1));
this.versionMinor = parseFloat(navigator.appVersion);
this.versionMajor = parseInt(navigator.appVersion);

if (this.isIE && this.versionMinor >= 4) this.versionMinor = parseFloat(ua.substring(ua.indexOf('msie ') + 5));
if (this.isIE && parseInt(this.versionMinor) < 7) pngAlpha = 0;

function view_build_top() {
    parent.frames["ch_list"].location = "/ch.php?lo=1";

    ins_HP();
    d.write('<table cellpadding=4 cellspacing=0 border=0 width=100%><tr><td bgcolor=#FCFAF3><table cellpadding=0 cellspacing=0 border=0>');
    d.write('<tr><td rowspan=4><font class=nick>' + sh_align(build[2], 0) + sh_sign(build[3], build[4], build[5]) + '<B>' + build[0] + '</B>[' + build[1] + ']</font></td><td><img src=http://image.neverlands.ru/1x1.gif width=1 height=2><br><img src=http://image.neverlands.ru/gameplay/hp.gif width=0 height=6 border=0 id=fHP vspace=0 align=absmiddle><img src=http://image.neverlands.ru/gameplay/nohp.gif width=0 height=6 border=0 id=eHP vspace=0 align=absmiddle></td>' + window.external.UsersOnline() + '</tr>');
    d.write('<tr><td bgcolor=#ffffff><img src=http://image.neverlands.ru/1x1.gif width=1 height=1></td></tr>');
    d.write('<tr><td><img src=http://image.neverlands.ru/gameplay/ma.gif width=0 height=6 border=0 id=fMP vspace=0 align=absmiddle><img src=http://image.neverlands.ru/gameplay/noma.gif width=0 height=6 border=0 id=eMP vspace=0 align=absmiddle></td></tr>');
    d.write('<tr><td><span id=hbar></span></td></tr>');
    d.write('</table></td><td bgcolor=#FCFAF3><div align=center id=ButtonPlace>' + ButtonGen() + '</div></td><td bgcolor=#FCFAF3><div align=right><a href="javascript: top.exit_redir()"><img src=http://image.neverlands.ru/exit.gif align=absmiddle width=15 height=15 border=0></a></div></td></tr></table>');
    cha_HP();

    d.write('<table cellpadding=0 cellspacing=0 border=0 width=100%><tr><td bgcolor=#FFFFFF><img src=http://image.neverlands.ru/1x1.gif width=1 height=1></td></tr><tr><td bgcolor=#B9A05C><img src=http://image.neverlands.ru/1x1.gif width=1 height=1></td></tr><tr><td bgcolor=#F3ECD7><img src=http://image.neverlands.ru/1x1.gif width=1 height=2></td></tr><tr><td bgcolor=#FFFFFF><img src=http://image.neverlands.ru/1x1.gif width=1 height=10></td></tr></table>');
}

function view_build_bottom() {
    d.write('<table cellpadding=0 cellspacing=0 border=0 width=100%><tr><td bgcolor=#FFFFFF><img src=http://image.neverlands.ru/1x1.gif width=1 height=4></td></tr><tr><td align=center>' + view_t() + '</td></tr><tr><td bgcolor=#FFFFFF><img src=http://image.neverlands.ru/1x1.gif width=1 height=10></td></tr></table>');
}

function anCurrentCellFullInfo() {
    try {
        if (window.external && typeof window.external.CurrentCellFullInfo == 'function') {
            return window.external.CurrentCellFullInfo();
        }
    } catch (e) {}
    return '';
}

if (!d.getElementById('an_cell_info_styles')) {
    try {
        var anCellInfoStyle = d.createElement('style');
        anCellInfoStyle.id = 'an_cell_info_styles';
        anCellInfoStyle.type = 'text/css';
        anCellInfoStyle.textContent = '.an-cell-info{max-width:940px;margin:12px auto 6px;padding:14px;border-radius:18px;background:linear-gradient(135deg,#111827 0%,#1f2937 46%,#0f766e 100%);box-shadow:0 14px 34px rgba(15,23,42,.35);color:#e5e7eb;font-family:Verdana,Arial,sans-serif;text-align:left;border:1px solid rgba(255,255,255,.18)}.an-cell-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:12px}.an-cell-kicker{display:inline-block;font-size:10px;text-transform:uppercase;letter-spacing:.12em;color:#93c5fd}.an-cell-title{font-size:18px;font-weight:bold;color:#fff;margin-top:3px}.an-cell-subtitle{font-size:12px;color:#cbd5e1;margin-top:3px}.an-cell-cost{border:1px solid;border-radius:999px;padding:7px 10px;background:rgba(15,23,42,.72);font-weight:bold;white-space:nowrap}.an-cell-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.an-cell-card{background:rgba(255,255,255,.09);border:1px solid rgba(255,255,255,.13);border-top:3px solid;border-radius:14px;padding:10px;min-height:66px}.an-cell-card-title{font-weight:bold;font-size:12px;margin-bottom:7px}.an-cell-card-body{font-size:12px;line-height:1.45;color:#f8fafc}.an-cell-chip{display:inline-block;border-radius:999px;background:rgba(136,187,221,.18);border:1px solid rgba(136,187,221,.45);padding:2px 7px;margin:1px 2px 2px 0;color:#dbeafe;font-weight:bold}.an-cell-chip.blue{background:rgba(51,204,255,.16);border-color:rgba(51,204,255,.45);color:#bae6fd}.an-cell-chip.green{background:rgba(52,211,153,.16);border-color:rgba(52,211,153,.45);color:#bbf7d0}.an-cell-muted{color:#94a3b8}.an-cell-empty{color:#cbd5e1;text-align:center;font-weight:bold}@media(max-width:720px){.an-cell-info{margin:10px 6px;border-radius:14px}.an-cell-head{display:block}.an-cell-cost{display:inline-block;margin-top:8px}.an-cell-grid{grid-template-columns:1fr}}';
        (d.head || d.documentElement).appendChild(anCellInfoStyle);
    } catch (e) {}
}

function view_map() {
    view_build_top();
    d.write('<table cellpadding=0 cellspacing=0 border=0 width=100%><tr><td bgcolor=#FFFFFF align=center><div id="world_host" style="position:relative; width:' + abcmapwidth + 'px; height:' + abcmapheight + 'px; margin:0 auto;"><div style="position:absolute; left:0; top:0; text-align:center; overflow:hidden; width:' + abcmapwidth + 'px; height:' + abcmapheight + 'px;" id="world_cont"></div><div style="position:absolute; left:0; top:0; width:' + abcmapwidth + 'px; height:' + abcmapheight + 'px; text-align:left;" id="world_cont2"></div></div></td></tr></table>');
    createMapText();

    for (var i = 0; i < map[1].length; i++) {
        avail[map[1][i][0] + '_' + map[1][i][1]] = map[1][i][2];
    }

    if (!map[0][4].length) {
        current_x = map[0][0];
        current_y = map[0][1];
        showMap(current_x, current_y);
    }
    else if (!map[0][4][0]) {
        finStatus = 1;
        loadPath(map[0][4][4], map[0][4][5], map[0][0], map[0][1], (map[0][4][3] - map[0][4][2]), (map[0][4][3] - map[0][4][1]));
        TimerStart((map[0][4][3] - map[0][4][1]), 0);
    }
    else {
        finStatus = 2;
        current_x = map[0][0];
        current_y = map[0][1];
        showMap(current_x, current_y);
        TimerStart(map[0][4][1], 1);
    }

    if (map[0][5]) MessBoxDiv(map[0][5]);

    view_build_bottom();
}

function ButtonGen() {
    var str = '';
    bavail = new Array();
    for (var i = 0; i < mapbt.length; i++) {
        bavail[mapbt[i][0]] = [mapbt[i][2], mapbt[i][3]];
        str += ' <input type=button class=fr_but id="' + mapbt[i][0] + '" value="' + mapbt[i][1] + '" onclick=\'ButClick("' + mapbt[i][0] + '")\'>';
        if (mapbt[i][0] == 'look' || mapbt[i][0] == 'ogl') {
            AnTryAutoCutOgl(mapbt[i][2], 'ButtonGen');
        }
    }
    return str;
}

var an_auto_cut_ogl_guard_until = 0;
function AnAutoCutTrace(message) {
    try {
        if (window.external && typeof window.external.TraceAutoCutRuntime == 'function') {
            window.external.TraceAutoCutRuntime(message);
        }
    } catch (e) {}
}

function AnTryAutoCutOgl(code, source) {
    var now = (new Date()).getTime();
    if (!code) {
        AnAutoCutTrace('skip no look/ogl code, source=' + source);
        return;
    }
    if (now < an_auto_cut_ogl_guard_until) {
        AnAutoCutTrace('skip guard, source=' + source);
        return;
    }
    var allowed = false;
    try {
        allowed = window.external.DoHerbAutoCut();
    } catch (e) {
        AnAutoCutTrace('skip bridge error, source=' + source + ', error=' + e);
        return;
    }
    if (!allowed) {
        AnAutoCutTrace('skip guard false, source=' + source);
        return;
    }
    an_auto_cut_ogl_guard_until = now + 3000;
    AnAutoCutTrace('schedule Ogl, source=' + source);
    setTimeout(function () {
        try {
            if (!window.external.DoHerbAutoCut()) {
                AnAutoCutTrace('cancel delayed Ogl, source=' + source);
                return;
            }
            AnAutoCutTrace('start Ogl, source=' + source);
            Ogl(code);
        } catch (e) {
            AnAutoCutTrace('Ogl failed, source=' + source + ', error=' + e);
        }
    }, 250);
}

function ButClick(id) {
    var goloc = '';
    switch (id) {
        case 'inf': goloc = 'main.php?get_id=56&act=10&go=inf&vcode=' + bavail[id][0]; break;
        case 'inv': goloc = 'main.php?get_id=56&act=10&go=inv&vcode=' + bavail[id][0]; break;
        case 'look': Ogl(bavail[id][0]); break;
        case 'ogl': Ogl(bavail[id][0]); break;
        case 'fis': Fish(bavail[id][0]); break;
        case 'fig': fight_map(bavail[id][0]); break;
        case 'dep': goloc = 'main.php?get_id=56&act=10&go=dep&vcode=' + bavail[id][0]; break;
        case 'dri': Drink(bavail[id][0]); break;
        case 'dig': Digg(bavail[id][0]); break;
        case 'que': QActive(bavail[id][0]); break;
    }

    if (goloc) {
        for (var j = 0; j < bavail[id][1].length; j++) goloc += '&' + bavail[id][1][j][0] + '=' + bavail[id][1][j][1];
        location = goloc;
    }
}

function ButtonSt(st) {
    for (var i = 0; i < mapbt.length; i++) {
        d.getElementById(mapbt[i][0]).disabled = st;
    }
}

function ReInitBut(obj) {
    for (var i = 0; i < obj.length; i++) bavail[obj[i][0]] = [obj[i][2], obj[i][3]];
}

function ReAddBut(obj) {
    var k = mapbt.length;
    for (var i = 0; i < obj.length; i++) {
        var nbutt = d.getElementById(obj[i][0]);
        if (!nbutt) {
            mapbt[k] = [obj[i][0]];
            k++;
            bavail[obj[i][0]] = [obj[i][2], obj[i][3]];
            d.getElementById('ButtonPlace').innerHTML += ' <input type=button class=fr_but id="' + obj[i][0] + '" value="' + obj[i][1] + '" onclick=\'ButClick("' + obj[i][0] + '")\'>';
            if (obj[i][0] == 'look' || obj[i][0] == 'ogl') {
                AnTryAutoCutOgl(obj[i][2], 'ReAddBut');
            }
        }
    }
}

function AnTraceMapRuntime(message) {
    try {
        if (window.external && typeof window.external.TraceMapRuntime == 'function') {
            window.external.TraceMapRuntime(message);
        }
    } catch (e) {}
}

function AnSyncCurrentCell(source) {
    try {
        if (window.external && typeof window.external.UpdateCurrentCellFromCoords == 'function') {
            window.external.UpdateCurrentCellFromCoords(parseInt(current_x), parseInt(current_y), source);
        }
    } catch (e) {
        AnTraceMapRuntime('cell sync failed, source=' + source + ', error=' + e);
    }
    try {
        if (typeof window.__anRenderCellInfo == 'function') {
            setTimeout(window.__anRenderCellInfo, 80);
            setTimeout(window.__anRenderCellInfo, 500);
        }
    } catch (e2) {}
}

function AnStopMovingFlash(source) {
    an_moving_flash_active = false;
    if (an_moving_flash_timeout) {
        clearTimeout(an_moving_flash_timeout);
        an_moving_flash_timeout = false;
    }
    try {
        var movingcell = d.getElementById('movingcell');
        if (movingcell) {
            movingcell.style.borderColor = 'red';
            movingcell.className = '';
            movingcell.removeAttribute('id');
        }
    } catch (e) {}
    AnTraceMapRuntime('moving flash stopped, source=' + source);
}

function AnRefreshCurrentCellMarker(source) {
    try {
        if (window.external && typeof window.external.CellDivText == 'function') {
            var divs = d.getElementsByTagName('DIV');
            for (var idx = 0; idx < divs.length; idx++) {
                var item = divs[idx];
                if (!item || !item.id || item.id.indexOf('divtext_') !== 0) continue;
                var parts = item.id.substring(8).split('_');
                if (parts.length != 2) continue;
                var x = parseInt(parts[0]);
                var y = parseInt(parts[1]);
                if (isNaN(x) || isNaN(y)) continue;
                item.innerHTML = window.external.CellDivText(x, y, scale, '', false, x == parseInt(current_x) && y == parseInt(current_y));
            }
        }
    } catch (e) {
        AnTraceMapRuntime('current marker refresh failed, source=' + source + ', error=' + e);
    }
    try {
        if (typeof window.__anRenderCellInfo == 'function') {
            setTimeout(window.__anRenderCellInfo, 80);
            setTimeout(window.__anRenderCellInfo, 500);
        }
    } catch (e2) {}
}

function showMap(x, y) {
    if (!world) {
        world = d.createElement('DIV');
        world.id = 'world_map';
        d.getElementById('world_cont').appendChild(world);
    }
    world.innerHTML = '';
    cur_margin_top = 0;
    cur_margin_left = 0;
    world.style.marginTop = '0px';
    world.style.marginLeft = '0px';
    table = d.createElement('TABLE');
    world.appendChild(table);
    tbody = d.createElement('TBODY');
    table.appendChild(tbody);
    table.border = 0;
    table.cellPadding = 0;
    table.cellSpacing = 1;
    table.style.backgroundColor = 'black';

    for (i = -height; i <= height; i++) {
        tr = d.createElement('TR');
        for (j = -width; j <= width; j++) {
            dx = x + j;
            dy = y + i;

            var isCellExists = window.external.IsCellExists(dx, dy);
            if (isCellExists) {
                td = d.createElement('TD');
                div = d.createElement('DIV');
                div.style.position = 'relative';
                img = d.createElement('IMG');
                img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + dy + '/' + dx + '_' + dy + '.jpg';
                img.width = scale;
                img.height = scale;
                img.id = 'img_' + dx + '_' + dy;
                img.style.cursor = 'pointer';
                img.alt = window.external.CellAltText(dx, dy, scale);
                img.onclick = function (dx, dy) { return function () { window.external.MoveTo(window.external.GenMoveLink(dx, dy)); } }(dx, dy);
                div.appendChild(img);
                divtext = d.createElement('DIV');
                divtext.style.position = 'absolute';
                divtext.style.top = 0;
                divtext.style.left = 0;
                divtext.style.width = scale + 'px';
                divtext.style.height = scale + 'px';
                divtext.style.cursor = 'pointer';
                divtext.id = 'divtext_' + dx + '_' + dy;
                var isframe = (current_x == dx) && (current_y == dy);
                divtext.innerHTML = window.external.CellDivText(dx, dy, scale, img.onclick, false, isframe);
                divtext.onclick = img.onclick;
                div.appendChild(divtext);
                td.appendChild(div);
                tr.appendChild(td);
            }
            else {
                td = d.createElement('TD');
                img = d.createElement('IMG');
                img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + dy + '/' + dx + '_' + dy + '.jpg';
                img.width = scale;
                img.height = scale;
                img.id = 'img_' + dx + '_' + dy;
                img.alt = '';
                img.style.filter = "alpha(opacity=70)";
                td.appendChild(img);
                tr.appendChild(td);
            }
        }

        tbody.appendChild(tr);
    }

    current_x = x;
    current_y = y;

    loaded_left = x - width;
    loaded_right = x + width;
    loaded_top = y - height;
    loaded_bottom = y + height;
    AnInstallDynamicMapPan('showMap');

    return true;
}

function AnMapPanTrace(message) {
    AnTraceMapRuntime('MAP_PAN ' + message);
}

function AnInstallDynamicMapPan(source) {
    if (an_map_pan_installed) return;
    var host = d.getElementById('world_host');
    if (!host) return;
    an_map_pan_installed = true;
    host.style.touchAction = 'none';
    host.style.webkitUserSelect = 'none';
    host.style.userSelect = 'none';
    if (host.addEventListener) {
        host.addEventListener('touchstart', AnMapPanStart, true);
        host.addEventListener('touchmove', AnMapPanMove, true);
        host.addEventListener('touchend', AnMapPanEnd, true);
        host.addEventListener('touchcancel', AnMapPanEnd, true);
        host.addEventListener('mousedown', AnMapMousePanStart, true);
        d.addEventListener('mousemove', AnMapMousePanMove, true);
        d.addEventListener('mouseup', AnMapMousePanEnd, true);
        host.addEventListener('click', AnMapPanClickGuard, true);
    }
    AnMapPanTrace('installed source=' + source + ', loaded=' + loaded_left + ':' + loaded_right + ':' + loaded_top + ':' + loaded_bottom);
}

function AnMapPanPoint(evt) {
    if (!evt) return null;
    var touch = evt.touches && evt.touches.length ? evt.touches[0] : (evt.changedTouches && evt.changedTouches.length ? evt.changedTouches[0] : evt);
    if (!touch) return null;
    return { x: touch.clientX, y: touch.clientY };
}

function AnMapPanCanStart(evt) {
    if (!world || moving_status == 1) return false;
    if (evt && evt.touches && evt.touches.length > 1) return false;
    return true;
}

function AnMapPanStart(evt) {
    if (!AnMapPanCanStart(evt)) return;
    var point = AnMapPanPoint(evt);
    if (!point) return;
    an_map_pan_touch = true;
    an_map_pan_dragging = false;
    window.__an_map_pan_active = false;
    an_map_pan_start_x = point.x;
    an_map_pan_start_y = point.y;
    an_map_pan_last_x = point.x;
    an_map_pan_last_y = point.y;
}

function AnMapMousePanStart(evt) {
    if (!AnMapPanCanStart(evt)) return;
    var point = AnMapPanPoint(evt);
    if (!point) return;
    an_map_pan_touch = true;
    an_map_pan_dragging = false;
    window.__an_map_pan_active = false;
    an_map_pan_start_x = point.x;
    an_map_pan_start_y = point.y;
    an_map_pan_last_x = point.x;
    an_map_pan_last_y = point.y;
}

function AnMapPanMove(evt) {
    if (!an_map_pan_touch || !world || moving_status == 1) return;
    var point = AnMapPanPoint(evt);
    if (!point) return;
    var totalDx = point.x - an_map_pan_start_x;
    var totalDy = point.y - an_map_pan_start_y;
    if (!an_map_pan_dragging && (Math.abs(totalDx) > 10 || Math.abs(totalDy) > 10)) {
        an_map_pan_dragging = true;
        window.__an_map_pan_active = true;
        AnMapPanTrace('drag start');
    }
    if (!an_map_pan_dragging) return;
    var dx = point.x - an_map_pan_last_x;
    var dy = point.y - an_map_pan_last_y;
    an_map_pan_last_x = point.x;
    an_map_pan_last_y = point.y;
    cur_margin_left += dx;
    cur_margin_top += dy;
    AnEnsureDynamicMapCoverage();
    world.style.marginLeft = parseInt(cur_margin_left) + 'px';
    world.style.marginTop = parseInt(cur_margin_top) + 'px';
    if (evt.preventDefault) evt.preventDefault();
    if (evt.stopPropagation) evt.stopPropagation();
    evt.cancelBubble = true;
    evt.returnValue = false;
}

function AnMapMousePanMove(evt) {
    AnMapPanMove(evt);
}

function AnMapPanEnd(evt) {
    if (!an_map_pan_touch) return;
    if (an_map_pan_dragging) {
        an_map_pan_suppress_click_until = (new Date()).getTime() + 600;
        AnMapPanTrace('drag end margin=' + parseInt(cur_margin_left) + ':' + parseInt(cur_margin_top) + ', loaded=' + loaded_left + ':' + loaded_right + ':' + loaded_top + ':' + loaded_bottom);
        if (evt && evt.preventDefault) evt.preventDefault();
        if (evt && evt.stopPropagation) evt.stopPropagation();
    }
    an_map_pan_touch = false;
    an_map_pan_dragging = false;
    window.__an_map_pan_active = false;
}

function AnMapMousePanEnd(evt) {
    AnMapPanEnd(evt);
}

function AnMapPanClickGuard(evt) {
    if ((new Date()).getTime() > an_map_pan_suppress_click_until) return;
    if (evt.preventDefault) evt.preventDefault();
    if (evt.stopImmediatePropagation) evt.stopImmediatePropagation();
    if (evt.stopPropagation) evt.stopPropagation();
    evt.cancelBubble = true;
    evt.returnValue = false;
    AnMapPanTrace('click suppressed after drag');
    return false;
}

function AnEnsureDynamicMapCoverage() {
    if (!world) return;
    var cell = scale + 1;
    var visibleColumns = (width * 2) + 1;
    var visibleRows = (height * 2) + 1;
    var leftVisible = loaded_left - (cur_margin_left / cell);
    var rightVisible = leftVisible + visibleColumns - 1;
    var topVisible = loaded_top - (cur_margin_top / cell);
    var bottomVisible = topVisible + visibleRows - 1;
    var guard = 0;
    while (rightVisible > loaded_right + 0.25 && guard < 32) {
        loaded_right += 1;
        loadMap('right');
        guard++;
    }
    guard = 0;
    while (leftVisible < loaded_left - 0.25 && guard < 32) {
        loaded_left -= 1;
        loadMap('left');
        leftVisible = loaded_left - (cur_margin_left / cell);
        guard++;
    }
    guard = 0;
    while (bottomVisible > loaded_bottom + 0.25 && guard < 32) {
        loaded_bottom += 1;
        loadMap('bottom');
        guard++;
    }
    guard = 0;
    while (topVisible < loaded_top - 0.25 && guard < 32) {
        loaded_top -= 1;
        loadMap('top');
        topVisible = loaded_top - (cur_margin_top / cell);
        guard++;
    }
}

function finFunction() {
    moving_status = 0;
    AnStopMovingFlash('finFunction');
    switch (finStatus) {
        case 0:

            current_x = parseInt(arr_res[1]);
            current_y = parseInt(arr_res[2]);
            var objmap = eval(arr_res[5]);
            map[0][2] = objmap[0];
            map[0][3] = objmap[1];
            map[1] = eval(arr_res[3]);
            MapReInit(map[1]);
            AnRefreshCurrentCellMarker('finFunction GO');
            mapbt = eval(arr_res[4]);
            d.getElementById('ButtonPlace').innerHTML = ButtonGen();
            if (objmap[2]) MessBoxDiv(objmap[2]);

            break;
        case 1:

            finStatus = 0;
            current_x = map[0][0];
            current_y = map[0][1];
            ButtonSt(false);
            MapReInit(map[1]);
            AnRefreshCurrentCellMarker('finFunction pending');

            break;
    }

    parent.frames["ch_list"].location = "/ch.php?lo=1";
}

function MapReInit(obj) {
    avail = new Array();
    for (var i = 0; i < obj.length; i++) {
        avail[obj[i][0] + '_' + obj[i][1]] = obj[i][2];
    }

    for (i = -height; i <= height; i++) {
        for (j = -width; j <= width; j++) {
            imgid = d.getElementById('img_' + (current_x + j) + '_' + (current_y + i));

            dx = current_x + j;
            dy = current_y + i;

            if (imgid) {
                imgid.onclick = function (dx, dy) { return function () { window.external.MoveTo(window.external.GenMoveLink(dx, dy)); } }(dx, dy);
                var divid = d.getElementById('divtext_' + dx + '_' + dy);
                if (divid) {
                    divid.onclick = imgid.onclick;
                    divid.style.cursor = 'pointer';
                }
            }
        }
    }
}

function move() {
    path = ((time_left) / (pause * 1000));

    if (time_left <= 0) {
        clearInterval(t);
        finFunction();
    }

    if (window.external.DoHideMiniMap()) {
        world.innerHTML = '';
    }
    else {

        if (dest_y < current_y) {
            app_y = dest_y + (Math.abs(dest_y - current_y) * path);
            if ((app_y - height) <= (loaded_top + 0.2)) {
                loaded_top -= 1;
                loadMap('top', loaded_top);
            }

            if ((app_y + (height * 2)) <= (loaded_bottom)) {
                loaded_bottom -= 1;
                freeMap('bottom');
            }

            cur_margin_top += (Math.abs(dest_y - current_y) * (scale + 1)) / (pause * 1000 / move_interval);
        }
        else if (dest_y > current_y) {
            app_y = dest_y - (Math.abs(dest_y - current_y) * path);
            if ((app_y + height) >= (loaded_bottom - 0.2)) {
                loaded_bottom += 1;
                loadMap('bottom', loaded_bottom);
            }

            if ((app_y - (height * 2)) >= (loaded_top)) {
                loaded_top += 1;
                freeMap('top');
            }

            cur_margin_top -= (Math.abs(dest_y - current_y) * (scale + 1)) / (pause * 1000 / move_interval);
        }

        if (dest_x < current_x) {
            app_x = dest_x + (Math.abs(dest_x - current_x) * path);
            if ((app_x - width) <= (loaded_left + 0.2)) {
                loaded_left -= 1;
                loadMap('left', loaded_left);
            }

            if ((app_x + (width * 2)) <= (loaded_right)) {
                loaded_right -= 1;
                freeMap('right');
            }

            cur_margin_left += (Math.abs(dest_x - current_x) * (scale + 1)) / (pause * 1000 / move_interval);
        }
        else if (dest_x > current_x) {
            app_x = dest_x - (Math.abs(dest_x - current_x) * path);
            if ((app_x + width) >= (loaded_right - 0.2)) {
                loaded_right += 1;
                loadMap('right', loaded_right);
            }

            if ((app_x - (width * 2)) >= (loaded_left)) {
                loaded_left += 1;
                freeMap('left');
            }

            cur_margin_left -= (Math.abs(dest_x - current_x) * (scale + 1)) / (pause * 1000 / move_interval);
        }

        world.style.marginTop = parseInt(cur_margin_top) + 'px';
        world.style.marginLeft = parseInt(cur_margin_left) + 'px';
    }

    time_left -= move_interval;
}

function timerst(lp) {
    time_left_sec -= 1000;

    // +ABC
    window.external.SetNeverTimer(time_left_sec);
    // -ABC

    if (time_left_sec <= 0) {
        if (lp) {
            ButtonSt(false);
            MapReInit(map[1]);
            finStatus = 0;
            AnRefreshCurrentCellMarker('timerst lp');
        }
        AnStopMovingFlash('timerst complete');
        timer_img.src = 'http://image.neverlands.ru/1x1.gif';
        d.getElementById('tdsec').innerHTML = '';
        d.getElementById('timertxt').style.display = 'none';
        d.getElementById('timerdiv').style.display = 'none';
        d.getElementById('timerfon').style.display = 'none';
        clearInterval(tsec);
        try {
            if (window.external && typeof window.external.TraceMapRuntime == 'function') {
                window.external.TraceMapRuntime('timerst complete, stay on current map, lp=' + lp);
            }
        } catch (e) {}
    }
    else {
        d.getElementById('tdsec').innerHTML = (time_left_sec / 1000);
    }
}

function RetClass() {
    var userAgent = navigator.userAgent.toLowerCase();
    if (userAgent.indexOf('mac') != -1 && userAgent.indexOf('firefox') != -1) classn = 'TB_overlayMacFFBGHack';
    else classn = 'TB_overlayBG';
    return classn;
}

function StateReady() {
    switch (arr_res[0]) {
        case 'GO':

            MapReInit([]);
            var divid = 'divtext_' + gox + '_' + goy;
            var targetDiv = d.getElementById(divid);
            if (targetDiv) {
                targetDiv.innerHTML = window.external.CellDivText(gox, goy, scale, '', true, false);
            }
            var mapTextDiv = d.getElementById('maptext');
            if (mapTextDiv) {
                mapTextDiv.innerHTML = window.external.MapText();
            }
            an_moving_flash_active = true;
            Flash1();

            dest_x = gox;
            dest_y = goy;
            pause = gop;

            TimerStart(pause, 0);
            time_left = pause * 1000;
            moving_status = 1;

            ButtonSt(true);
            t = setInterval("move()", move_interval);
            

            break;

            //case 'AL':
        case 'RESO':

            var n_map = eval(arr_res[2]);
            if (n_map[0] > 0) {
                map[1] = n_map[1];
                map[0][2] = n_map[0];
                ReAddBut(eval(arr_res[3]));
            }

            var dis_map = eval(arr_res[4]);
            if (!dis_map[0]) ReInitBut(eval(arr_res[3]));
            else {
                mapbt = eval(arr_res[3]);
                d.getElementById('ButtonPlace').innerHTML = ButtonGen();
                MapReInit([]);
            }

            if (dis_map[1][1]) TimerStart(dis_map[1][1], 1);
            if (ND) RemoveDialogDiv();

            var messb = eval(arr_res[1]);
            if (ND === false) {
                if (!messb[0]) {
                    ND = d.createElement('div');
                    ND.id = 'darker';
                    ND.className = (classn ? classn : RetClass());
                    ND.style.display = 'block';
                    d.body.appendChild(ND);

                    ND = d.createElement('div');
                    ND.className = 'png';

                    //   
                    var buttons = '';
                    var ingr = eval(arr_res[5]);
                    var did = 'uni';
                    ND.id = 'uni';

                    switch (ingr[0]) {
                        case 0:
                            var tr = 0;
                            var butalt;
                            var messal = '<FORM id="ALHF"><table cellpadding=0 cellspacing=0 border=0 width=100%><tr><td bgcolor=#CCCCCC><table cellpadding=10 cellspacing=1 border=0 width=100%>' + (ingr[1] != '00000' ? '<tr><td bgcolor=#FFFFFF colspan=4 class="centr"><img src=http://image.neverlands.ru/1x1.gif width=1 height=10><br><img src="http://neverlands.ru/modules/code/code.php?' + ingr[1] + '" width=134 height=60><br><img src=http://image.neverlands.ru/1x1.gif width=1 height=10><br>: <input type=text name=code size=4 class=gr_text id=CAPCODE><br><img src=http://image.neverlands.ru/1x1.gif width=1 height=10></td></tr>' : '');

                            // ABC
                            var abcingr = '';
                            // -ABC

                            for (var i = 4; i < ingr.length; i++) {
                                tr++;
                                if (tr == 1) messal += '<tr>';
                                butalt = ingr[i][10] == 4 ? 'Срезать' : 'Срубить';
                                messal += '<td bgcolor=#FFFFFF valign=top width=25%><div align=center>' + (!ingr[i][9] ? '<input type=button class=lbutdis value="' + butalt + '" DISABLED>' : '<input type=button class=lbut value="' + butalt + '" onclick="ResoStart(\'' + ingr[i][0] + '\',' + ingr[2] + ',' + ingr[3] + ',\'' + ingr[i][3] + '\',\'' + ingr[i][2] + '\',\'' + ingr[i][4] + '\',\'' + ingr[i][5] + '\',\'' + ingr[i][6] + '\',\'' + ingr[i][7] + '\',\'' + ingr[i][9] + '\',\'' + ingr[i][10] + '\',\'' + ingr[i][1] + '\')">') + '<br><br><img src=http://image.neverlands.ru/resources/' + ingr[i][0] + '.gif width=60 height=60><br><font class=freetxt><b>' + ingr[i][1] + '</b><br><br>: ' + ingr[i][8] + '  ' + ingr[i][11] + '</font></div></td>';

                                // ABC
                                abcingr += ingr[i][1] + ':' + (!ingr[i][9] ? '0' : '1') + '|';
                                // -ABC

                                if (tr == 4) {
                                    messal += '</tr>';
                                    tr = 0;
                                }
                            }

                            tr++;
                            if (tr != 1) {
                                for (var i = tr; i < 5; i++) messal += '<td bgcolor=#FFFFFF width=25%>&nbsp;</td>';
                                messal += '</tr>';
                            }

                            messal += '</table></td></tr></table></FORM>';

                            // ABC
                            window.external.HerbsList(abcingr);
                            // -ABC

                            buttons = '<a class="but ok" href="javascript: RemoveDialogDiv();"></a>';
                            break;

                        case 1:
                            var messal = '<FORM id="FISHF"><table cellspacing=0 cellpadding=0 border=0 width=100%><tr><td bgcolor=#CCCCCC><table cellspacing=1 cellpadding=5 border=0 width=100%><tr><td bgcolor=#FFFFFF colspan=5 class="centr" class=nickname><font class=inv><b>' + ((ingr[4] - ingr[3]) > 10 ? '' : '<font color=#CC0000>!  .</font> ') + '  : ' + ingr[3] + '/' + ingr[4] + '</b></font></td></tr><tr><td bgcolor=#FFFFFF colspan=2></td><td bgcolor=#FFFFFF class="centr" width=60%><b> </b></td><td bgcolor=#FFFFFF class="centr" width=40%><b> </b></td></tr>';

                            // ABC
                            window.external.SetAutoFishMassa(ingr[3] + '/' + ingr[4]);
                            // -ABC

                            for (var i = 5; i < ingr.length; i++) messal += '<tr><td bgcolor=#FFFFFF class="centr"><input type=radio name=primid value=' + ingr[i][0] + (ingr[i][2] > 4 ? '' : ' DISABLED') + window.external.CheckPri(ingr[i][1], ingr[i][2]) + '></td><td bgcolor=#FFFFFF><img src=http://image.neverlands.ru/tools/' + ingr[i][0] + '.gif width=60 height=60></td><td bgcolor=#FFFFFF class="centr"><b>' + ingr[i][1] + '</b></td><td bgcolor=#FFFFFF class="centr"><b>' + ingr[i][2] + '</b></td></tr>';
                            messal += (ingr[1] ? '<tr><td bgcolor=#FFFFFF colspan=5 class="centr"><img src=http://image.neverlands.ru/1x1.gif width=1 height=10><br><img src="http://neverlands.ru/modules/code/code.php?' + ingr[1] + '" width=134 height=60><br><img src=http://image.neverlands.ru/1x1.gif width=1 height=10><br>: <input type=text name=code size=4 class=gr_text id=CAPCODE><br><img src=http://image.neverlands.ru/1x1.gif width=1 height=10>' + window.external.InsertGuaDiv(ingr[1]) + '</td></tr>' : '') + '</table></td></tr></table></FORM>';
                            buttons = '<a class="but lov" id=fishbutton href="javascript: FishStart(\'' + ingr[2] + '\',' + (ingr[1] ? 1 : 0) + ');"></a>';

                            // ABC
                            if ((ingr[4] - ingr[3]) <= 10) {
                                window.external.FishOverload();
                            }

                            if (!ingr[1] && window.external.IsAutoFish()) {
                                window.external.SetFishNoCaptchaReady();
                                //FishStart(ingr[2], (ingr[1] ? 1 : 0));
                            }

                            // -ABC


                            break;
                    }

                    var mhtml = '<table width="760" cellspacing="0" cellpadding="0" border="0" class="uni_window"><tr><td class="wu_top_left png"></td><td class="wu_top"></td><td class="wu_top_right png"></td></tr><tr><td class="wu_l_gr"></td><td class="wu_m_gr">' + messal + '</td><td class="wu_r_gr"><a href="javascript: RemoveDialogDiv();" class="circ"></a></td> </tr><tr><td class="wu_b_l png"></td><td width="auto" class="wu_b_m"><table width="100%" cellspacing="0" cellpadding="0" border="0"><tr><td class="wu_b_m_l"></td><td>' + buttons + '</td><td class="wu_b_m_r"></td></tr></table></td><td class="wu_b_r png"></td></tr><tr><td colspan="3"><div class="wu_bb_l png"></div><div class="wu_bb_r png"></div></td></tr></table>';

                    d.body.appendChild(ND);

                    LD = d.getElementById(did);
                    LD.innerHTML = mhtml;

                    DD = d.getElementById('darker');
                    DD.style.height = getDocHeight() + 'px';

                }
                else MessBoxDiv(messb[0]);
            }

            break;
        case 'MESS':

            if (ND) RemoveDialogDiv();
            var messb = eval(arr_res[1]);
            if (messb[2]) TimerStart(messb[2], 1);
            MessBoxDiv(messb[0]);

            break;
        case 'F5':

            location = 'main.php';

            break;
    }
}

//function MapHerbCut(herb, arg) {
//    window.external.TraceCut(herb);
//    AjaxGet(arg);
//}

function TimerStart(secgo, mrinit) {
    if (time_left_sec <= 0) {
        if (mrinit) {
            ButtonSt(true);
            MapReInit([]);
        }
        time_left_sec = secgo * 1000;
        // +ABC: уведомить Java о таймере немедленно (не ждать первый timerst через 1с).
        // Без этого anti-loop guard (NeverTimer=now+1500) истекает раньше первого
        // SetNeverTimer из timerst, вызывая каскадные перезагрузки страницы.
        window.external.SetNeverTimer(time_left_sec);
        // -ABC
        if (!timer_img) createCursor();
        timer_img.src = 'http://image.neverlands.ru/map/world/timer.png';
        d.getElementById('timerfon').style.display = 'block';
        d.getElementById('timerdiv').style.display = 'block';
        d.getElementById('tdsec').innerHTML = secgo;
        tsec = setInterval('timerst(' + mrinit + ')', 1000);
    }
    else time_left_sec += secgo * 1000;
}

function MessBoxDiv(mess) {
    if (mess.indexOf('Рюкзак') != -1) {
        if (!window.external.ShowOverWarning()) {
            return;
        }
    }

    if (!MESSD) {
        MDARK = d.createElement('div');
        MDARK.id = 'darker';
        MDARK.className = (classn ? classn : RetClass());
        MDARK.style.display = 'block';
        d.body.appendChild(MDARK);

        MESSD = d.createElement('div');
        MESSD.className = 'png';
        MESSD.id = 'static_window';
        MESSD.innerHTML = '<div class="ws_top png"></div><div class="ws_right png"></div><div class="ws_bottom png"></div><div class="ws_middle"><a href="javascript: MessBoxDivClose();" class="circ"></a><div class="text">' + mess + '</div><a class="cl_but" href="javascript: MessBoxDivClose();"></a></div>';
        d.body.appendChild(MESSD);
    }
}

function MessBoxDivClose() {
    d.body.removeChild(MESSD);
    d.body.removeChild(MDARK);
    MDARK = false;
    MESSD = false;
}

function fight_map(vcode) {
    top.frames['ch_buttons'].document.FBT.text.focus();
    MessBoxDiv('<form action=main.php method=POST><input type=hidden name=post_id value="8"><input type=hidden name=vcode value=' + vcode + '><table cellpadding=5 cellspacing=0 border=0 width=100%><tr><td><b>  </b></td></tr><tr><td> : <input type="text" name=pnick class=gr_text maxlength=20></td></tr><tr><td align=center><input type=submit value="" class=gr_but></td></tr></table></FORM>');
    d.all('pnick').focus();
    ActionFormUse = 'pnick';
}

function AlhStart(name, ct, cid, uid, curs, mass, muid, p, resl, vcode) {
    var CAP;
    var errm = '';
    CAP = d.getElementById("CAPCODE").value;
    if (CAP) {
        // ABC
        window.external.TraceCut(name);
        // -ABC

        AjaxGet('alchemy_ajax.php?act=2&ct=' + ct + '&cid=' + cid + '&uid=' + uid + '&curs=' + curs + '&mass=' + mass + '&muid=' + muid + '&p=' + p + '&resl=' + resl + '&vcode=' + vcode + '&code=' + CAP + '&r=' + Math.random());
    }
    else errm = '  .';
    if (errm) MessBoxDiv(errm);
}

function ResoStart(res_id, r_x, r_y, r_time, l_time, uid, curs, mass, p, vcode, r_type, res_name) {
    var CAP;
    var errm = '';
    CAP = d.getElementById("CAPCODE").value;
    if (CAP) {

        // ABC
        window.external.TraceCut(res_name);
        // -ABC

        AjaxGet('alchemy_ajax.php?act=3&res_id=' + res_id + '&r_x=' + r_x + '&r_y=' + r_y + '&r_time=' + r_time + '&r_type=' + r_type + '&uid=' + uid + '&curs=' + curs + '&mass=' + mass + '&p=' + p + '&l_time=' + l_time + '&vcode=' + vcode + '&code=' + CAP + '&r=' + Math.random());
    }
    else errm = '  .';
    if (errm) MessBoxDiv(errm);
}

function FishStart(vcode, ver) {
    var CAP;
    var errm = '';
    if (ver) CAP = d.getElementById("CAPCODE").value;
    else CAP = 1;

    if (CAP) {
        var primid = '';
        var ff = d.getElementById("FISHF");
        var radio = ff.primid;
        if (radio.value) primid = radio.value;
        else {
            for (var i = 0; i < radio.length; i++) {
                if (radio[i].checked) {
                    primid = radio[i].value;
                    break;
                }
            }
        }
        if (primid) {
            AjaxGet('fish_ajax.php?act=2&primid=' + primid + '&vcode=' + vcode + (ver ? '&code=' + CAP : '') + '&r=' + Math.random());
        }
        else errm = '  .';
    }
    else errm = '  .';
    if (errm) MessBoxDiv(errm);
}

function getDocHeight() {
    return Math.max(Math.max(d.body.scrollHeight, d.documentElement.scrollHeight), Math.max(d.body.offsetHeight, d.documentElement.offsetHeight), Math.max(d.body.clientHeight, d.documentElement.clientHeight));
}

function moveMapTo(x, y, ps) {
    if (moving_status == 1) return false;
    d.getElementById('maptext').innerHTML = '  ...';
    gox = x;
    goy = y;
    gop = ps;
    AjaxGet('map_ajax.php?act=1&mx=' + x + '&my=' + y + '&gti=' + map[0][2] + '&vcode=' + avail[x + '_' + y] + '&r=' + Math.random());
    return true;
}

function Ogl(code) {
    AjaxGet('alchemy_ajax.php?act=1&vcode=' + code + '&r=' + Math.random());
}

function Fish(code) {
    AjaxGet('fish_ajax.php?act=1&vcode=' + code + '&r=' + Math.random());
}

function Drink(code) {
    AjaxGet('map_act_ajax.php?act=1&vcode=' + code + '&sm=' + (map[1].length ? 1 : 0) + '&r=' + Math.random());
}

function Digg(code) {
    AjaxGet('map_act_ajax.php?act=2&vcode=' + code + '&sm=' + (map[1].length ? 1 : 0) + '&r=' + Math.random());
}

function loadMap(dir) {
    tbody = world.lastChild.lastChild;
    switch (dir) {
        case 'bottom':

            tr = d.createElement('TR');
            for (i = loaded_left; i <= loaded_right; i++) {
                var isCellExists = window.external.IsCellExists(i, loaded_bottom);
                if (isCellExists) {
                    td = d.createElement('TD');
                    div = d.createElement('DIV');
                    div.style.position = 'relative';
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + loaded_bottom + '/' + i + '_' + loaded_bottom + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + i + '_' + loaded_bottom;
                    img.style.cursor = 'pointer';
                    img.onclick = function (dx, dy) { return function () { window.external.MoveTo(window.external.GenMoveLink(dx, dy)); } }(i, loaded_bottom);
                    img.alt = window.external.CellAltText(i, loaded_bottom, scale);
                    div.appendChild(img);
                    divtext = d.createElement('DIV');
                    divtext.style.position = 'absolute';
                    divtext.style.top = 0;
                    divtext.style.left = 0;
                    divtext.style.width = scale + 'px';
                    divtext.style.height = scale + 'px';
                    divtext.style.cursor = 'pointer';
                    divtext.id = 'divtext_' + i + '_' + loaded_bottom;
                    divtext.innerHTML = window.external.CellDivText(i, loaded_bottom, scale, img.onclick, false, false);
                    divtext.onclick = img.onclick;
                    div.appendChild(divtext);
                    td.appendChild(div);
                    tr.appendChild(td);
                }
                else {
                    td = d.createElement('TD');
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + loaded_bottom + '/' + i + '_' + loaded_bottom + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + i + '_' + loaded_bottom;
                    img.alt = '';
                    img.style.filter = "alpha(opacity=70)";
                    td.appendChild(img);
                    tr.appendChild(td);
                }
            }

            tbody.appendChild(tr);

            break;
        case 'top':

            cur_margin_top -= (scale + 1);
            tr = d.createElement('TR');
            for (i = loaded_left; i <= loaded_right; i++) {
                var isCellExists = window.external.IsCellExists(i, loaded_top);
                if (isCellExists) {
                    td = d.createElement('TD');
                    div = d.createElement('DIV');
                    div.style.position = 'relative';
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + loaded_top + '/' + i + '_' + loaded_top + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + i + '_' + loaded_top;
                    img.style.cursor = 'pointer';
                    img.onclick = function (dx, dy) { return function () { window.external.MoveTo(window.external.GenMoveLink(dx, dy)); } }(i, loaded_top);
                    img.alt = window.external.CellAltText(i, loaded_top, scale);
                    div.appendChild(img);
                    divtext = d.createElement('DIV');
                    divtext.style.position = 'absolute';
                    divtext.style.top = 0;
                    divtext.style.left = 0;
                    divtext.style.width = scale + 'px';
                    divtext.style.height = scale + 'px';
                    divtext.style.cursor = 'pointer';
                    divtext.id = 'divtext_' + i + '_' + loaded_top;
                    divtext.innerHTML = window.external.CellDivText(i, loaded_top, scale, img.onclick, false, false);
                    divtext.onclick = img.onclick;
                    div.appendChild(divtext);
                    td.appendChild(div);
                    tr.appendChild(td);
                }
                else {
                    td = d.createElement('TD');
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + loaded_top + '/' + i + '_' + loaded_top + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + i + '_' + loaded_top;
                    img.alt = '';
                    img.style.filter = "alpha(opacity=70)";
                    td.appendChild(img);
                    tr.appendChild(td);
                }
            }

            tbody.insertBefore(tr, tbody.firstChild);

            break;
        case 'right':

            for (i = loaded_top; i <= loaded_bottom; i++) {
                tr = tbody.childNodes[i - loaded_top];
                var isCellExists = window.external.IsCellExists(loaded_right, i);
                if (isCellExists) {
                    td = d.createElement('TD');
                    div = d.createElement('DIV');
                    div.style.position = 'relative';
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + i + '/' + loaded_right + '_' + i + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + loaded_right + '_' + i;
                    img.style.cursor = 'pointer';
                    img.onclick = function (dx, dy) { return function () { window.external.MoveTo(window.external.GenMoveLink(dx, dy)); } }(loaded_right, i);
                    img.alt = window.external.CellAltText(loaded_right, i, scale);
                    div.appendChild(img);
                    divtext = d.createElement('DIV');
                    divtext.style.position = 'absolute';
                    divtext.style.top = 0;
                    divtext.style.left = 0;
                    divtext.style.width = scale + 'px';
                    divtext.style.height = scale + 'px';
                    divtext.style.cursor = 'pointer';
                    divtext.id = 'divtext_' + loaded_right + '_' + i;
                    divtext.innerHTML = window.external.CellDivText(loaded_right, i, scale, img.onclick, false, false);
                    divtext.onclick = img.onclick;
                    div.appendChild(divtext);
                    td.appendChild(div);
                    tr.appendChild(td);
                }
                else {
                    td = d.createElement('TD');
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + i + '/' + loaded_right + '_' + i + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + loaded_right + '_' + i;
                    img.alt = '';
                    img.style.filter = "alpha(opacity=70)";
                    td.appendChild(img);
                    tr.appendChild(td);
                }
            }

            break;
        case 'left':

            cur_margin_left -= (scale + 1);
            for (i = loaded_top; i <= loaded_bottom; i++) {
                tr = tbody.childNodes[i - loaded_top];
                var isCellExists = window.external.IsCellExists(loaded_left, i);
                if (isCellExists) {
                    td = d.createElement('TD');
                    div = d.createElement('DIV');
                    div.style.position = 'relative';
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + i + '/' + loaded_left + '_' + i + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + loaded_left + '_' + i;
                    img.style.cursor = 'pointer';
                    img.onclick = function (dx, dy) { return function () { window.external.MoveTo(window.external.GenMoveLink(dx, dy)); } }(loaded_left, i);
                    img.alt = window.external.CellAltText(loaded_left, i, scale);
                    div.appendChild(img);
                    divtext = d.createElement('DIV');
                    divtext.style.position = 'absolute';
                    divtext.style.top = 0;
                    divtext.style.left = 0;
                    divtext.style.width = scale + 'px';
                    divtext.style.height = scale + 'px';
                    divtext.style.cursor = 'pointer';
                    divtext.id = 'divtext_' + loaded_left + '_' + i;
                    divtext.innerHTML = window.external.CellDivText(loaded_left, i, scale, img.onclick, false, false);
                    divtext.onclick = img.onclick;
                    div.appendChild(divtext);
                    td.appendChild(div);
                    tr.insertBefore(td, tr.firstChild);
                }
                else {
                    td = d.createElement('TD');
                    img = d.createElement('IMG');
                    img.src = 'http://image.neverlands.ru/map/world/' + map[0][3] + '/' + i + '/' + loaded_left + '_' + i + '.jpg';
                    img.width = scale;
                    img.height = scale;
                    img.id = 'img_' + loaded_left + '_' + i;
                    img.alt = '';
                    img.style.filter = "alpha(opacity=70)";
                    td.appendChild(img);
                    tr.insertBefore(td, tr.firstChild);
                }
            }

            break;
    }
}

function freeMap(dir) {
    tbody = world.lastChild.lastChild;
    switch (dir) {
        case 'top':

            cur_margin_top += (scale + 1);
            tr = tbody.firstChild;
            tbody.removeChild(tr);

            break
        case 'bottom':

            tr = tbody.lastChild;
            tbody.removeChild(tr);

            break
        case 'left':

            cur_margin_left += (scale + 1);
            for (i = loaded_top; i <= loaded_bottom; i++) {
                tr = tbody.childNodes[i - loaded_top];
                tr.removeChild(tr.firstChild);
            }

            break
        case 'right':

            for (i = loaded_top; i <= loaded_bottom; i++) {
                tr = tbody.childNodes[i - loaded_top];
                tr.removeChild(tr.lastChild);
            }

            break
    }

    return true;
}

function loadPath(from_x, from_y, to_x, to_y, ptime_all, ptime_left) {
    if (moving_status == 1) return false;
    path = ((ptime_all - ptime_left) / ptime_all);
    app_x = from_x + ((to_x - from_x) * path);
    app_y = from_y + ((to_y - from_y) * path);
    showMap(parseInt(app_x), parseInt(app_y));

    if (to_x < from_x) {
        loaded_right++;
        loadMap('right');
    }

    if (to_y < from_y) {
        loaded_bottom++;
        loadMap('bottom');
    }

    current_x = app_x;
    current_y = app_y;
    dest_x = to_x;
    dest_y = to_y;

    cur_margin_left = -(Math.abs(parseInt(app_x) - app_x) * (scale + 1));
    cur_margin_top = -(Math.abs(parseInt(app_y) - app_y) * (scale + 1));

    pause = ptime_left;
    time_left = pause * 1000;

    moving_status = 1;
    t = setInterval("move()", move_interval);
    return true;
}

function createCursor() {
    div = d.createElement('DIV');
    div.id = 'timerfon';

    div.style.display = 'none';
    div.style.position = 'absolute';
    div.style.marginLeft = (width * scale) + 'px';
    div.style.marginTop = '4px';

    timer_img = d.createElement('IMG');
    timer_img.width = 100;
    timer_img.height = 100;

    div.appendChild(timer_img);
    d.getElementById('world_cont2').appendChild(div);

    div = d.createElement('DIV');
    div.id = 'timerdiv';

    div.style.display = 'none';
    div.style.position = 'absolute';
    div.style.marginLeft = ((width) * scale) + 'px';
    div.style.marginTop = '46px';
    div.innerHTML = '<table cellpadding=0 cellspacing=0 border=0 width=100><tr><td align=center id="tdsec" class="timer_s"></td></tr></table>';

    d.getElementById('world_cont2').appendChild(div);
}

function createMapText() {
    div = d.createElement('DIV');
    div.id = 'timertxt';

    div.style.position = 'absolute';
    div.style.marginLeft = '0px';
    div.style.marginTop = '80px';
    div.innerHTML = '<table cellpadding=0 cellspacing=0 border=0 width=' + abcmapwidth + '><tr><td align=center id="maptext" style="font-family: Verdana; font-size: 12px; color: #999999; font-weight: bold; filter:glow(color=black, strength=5);"></td></tr></table>';

    d.getElementById('world_cont2').appendChild(div);
}

function showCursor() {
    if (!transport_img) {
        createCursor();
    }
    transport_img.src = 'http://image.neverlands.ru/map/nl_cursor.png';
}

function showTransport(name, from_x, from_y, to_x, to_y, p, type) {
    if (!transport_img) {
        createCursor();
    }

    rad = Math.atan2((to_y - from_y), (to_x - from_x));

    pi = 3.141592;
    grad = Math.round(rad / pi * 180 / (360 / p));
    if (grad == p) grad = 0;
    if (grad < 0) grad = p + grad;


    if (pngAlpha) transport_img.src = 'http://image.neverlands.ru/map/' + name + '_' + grad + '.' + type;
    else {
        transport_img = ReInitCursor();
        transport_img.src = 'http://image.neverlands.ru/map/' + name + '_' + grad + '.' + type;
    }

    return true;
}

function ReInitCursor() {
    var new_tr = d.createElement('IMG');
    new_tr.width = 100;
    new_tr.height = 100;
    transport_img.parentNode.appendChild(new_tr);
    transport_img.parentNode.removeChild(transport_img);
    return new_tr;
}

function Flash1() {
    if (!an_moving_flash_active) return;
    var movingcell = d.getElementById('movingcell');
    if (!movingcell) {
        AnStopMovingFlash('Flash1 missing movingcell');
        return;
    }
    movingcell.style.borderColor = 'white';
    movingcell.className = 'white';
    an_moving_flash_timeout = setTimeout('Flash2()', 50);
}

function Flash2() {
    if (!an_moving_flash_active) return;
    var movingcell = d.getElementById('movingcell');
    if (!movingcell) {
        AnStopMovingFlash('Flash2 missing movingcell');
        return;
    }
    movingcell.style.borderColor = 'red';
    an_moving_flash_timeout = setTimeout('Flash1()', 750);
}
