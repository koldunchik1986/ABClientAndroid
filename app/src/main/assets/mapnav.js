var d = document;
var world = false;

function __abBridge() {
    if (window.external) return window.external;
    if (window.AndroidBridge) return window.AndroidBridge;
    return null;
}

function __abInt(value, fallback) {
    var parsed = parseInt(value, 10);
    return isNaN(parsed) ? fallback : parsed;
}

var __bridge = __abBridge();
var width = __abInt(__bridge && __bridge.GetHalfMapWidth ? __bridge.GetHalfMapWidth() : 1, 1);
var height = __abInt(__bridge && __bridge.GetHalfMapHeight ? __bridge.GetHalfMapHeight() : 1, 1);
var scale = __abInt(__bridge && __bridge.GetMapScale ? __bridge.GetMapScale() : 100, 100);
if (width < 1) width = 1;
if (height < 1) height = 1;
if (scale < 50) scale = 50;
if (scale > 150) scale = 150;

var requestedScale = scale;
var currentScale = scale;
var abcmapwidth = 0;
var abcmapheight = 0;
var lastCenterX = null;
var lastCenterY = null;
var cellCountX = (width * 2) + 1;
var cellCountY = (height * 2) + 1;
var spacingX = cellCountX + 1;
var spacingY = cellCountY + 1;

function __calcAutoScaleByViewport() {
    var viewportW = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var viewportH = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    var fitByWidth = Math.floor((viewportW - spacingX) / cellCountX);
    var fitByHeight = Math.floor((viewportH - spacingY) / cellCountY);
    var fit = Math.min(requestedScale, fitByWidth, fitByHeight);
    if (!isFinite(fit) || fit < 24) {
        fit = Math.min(requestedScale, 24);
    }
    if (fit < 24) fit = 24;
    return fit;
}

function __recalcMapBounds() {
    currentScale = __calcAutoScaleByViewport();
    abcmapwidth = (cellCountX * currentScale) + spacingX;
    abcmapheight = (cellCountY * currentScale) + spacingY;
}

function __applyContainersSize() {
    var cont = document.getElementById("world_cont");
    var cont2 = document.getElementById("world_cont2");
    if (cont) {
        cont.style.width = abcmapwidth + "px";
        cont.style.height = abcmapheight + "px";
    }
    if (cont2) {
        cont2.style.width = abcmapwidth + "px";
        cont2.style.height = abcmapheight + "px";
    }
}

__recalcMapBounds();
document.write("<div style=\"text-align:center\"><div style=\"display:inline-block;\">");
document.write("<div id=\"world_cont\" style=\"position: absolute; text-align: center; overflow: hidden; width:" + abcmapwidth + "px; height:" + abcmapheight + "px;\"></div>");
document.write("<div id=\"world_cont2\" style=\"width: " + abcmapwidth + "px; height: " + abcmapheight + "px; text-align: left;\"></div>");
document.write("</div></div>");

function showMap(x, y) {
    var table, tbody, tr, td;
    var bridge = __abBridge();
    lastCenterX = x;
    lastCenterY = y;
    __recalcMapBounds();
    __applyContainersSize();

    if (!world) {
        world = document.createElement("DIV");
        world.id = "world_map";
        document.getElementById("world_cont").appendChild(world);
    }

    world.innerHTML = "";

    table = document.createElement("TABLE");
    table.cellPadding = 0;
    table.cellSpacing = 1;
    table.border = 0;
    table.style.backgroundColor = "black";
    world.appendChild(table);

    tbody = document.createElement("TBODY");
    table.appendChild(tbody);

    for (var i = -height; i <= height; i++) {
        var dy = y + i;
        tr = d.createElement("TR");
        for (var j = -width; j <= width; j++) {
            var dx = x + j;

            td = d.createElement("TD");
            td.id = "td_" + dx + "_" + dy;
            td.style.width = currentScale + "px";
            td.style.height = currentScale + "px";
            td.style.verticalAlign = "top";
            td.style.textAlign = "left";
            td.style.opacity = "0.8";
            td.style.filter = "alpha(opacity=80)";

            var isCellExists = bridge && bridge.IsCellExists ? bridge.IsCellExists(dx, dy) : false;
            if (isCellExists) {
                var isframe = (i == 0) && (j == 0);
                var clickHandler = function (dx, dy) {
                    return function () {
                        if (!bridge || !bridge.MoveTo || !bridge.GenMoveLink) return false;
                        bridge.MoveTo(bridge.GenMoveLink(dx, dy));
                    };
                }(dx, dy);
                td.title = bridge && bridge.CellAltText ? bridge.CellAltText(dx, dy, currentScale) : "";
                td.style.cursor = "pointer";
                td.onmouseover = function () { this.style.opacity = "1.0"; this.style.filter = "alpha(opacity=100)"; };
                td.onmouseout = function () { this.style.opacity = "0.8"; this.style.filter = "alpha(opacity=80)"; };

                var cellWrap = d.createElement("DIV");
                cellWrap.style.position = "relative";
                cellWrap.style.width = currentScale + "px";
                cellWrap.style.height = currentScale + "px";

                var img = d.createElement("IMG");
                img.src = "http://image.neverlands.ru/map/world/day/" + dy + "/" + dx + "_" + dy + ".jpg";
                img.width = currentScale;
                img.height = currentScale;
                img.style.display = "block";
                img.style.cursor = "pointer";
                img.alt = td.title;
                img.onclick = clickHandler;
                cellWrap.appendChild(img);

                var cellText = d.createElement("DIV");
                cellText.style.position = "absolute";
                cellText.style.left = "0";
                cellText.style.top = "0";
                cellText.style.width = currentScale + "px";
                cellText.style.height = currentScale + "px";
                cellText.style.cursor = "pointer";
                cellText.innerHTML = bridge && bridge.CellDivText
                    ? bridge.CellDivText(dx, dy, currentScale, "", false, isframe)
                    : "";
                cellText.onclick = clickHandler;
                cellWrap.appendChild(cellText);

                td.appendChild(cellWrap);
            }
            else {
                var img = d.createElement("IMG");
                img.src = "http://image.neverlands.ru/map/world/day/" + dy + "/" + dx + "_" + dy + ".jpg";
                img.width = currentScale;
                img.height = currentScale;
                img.style.display = "block";
                td.style.opacity = "0.4";
                td.style.filter = "alpha(opacity=40)";
                td.onclick = function () { return false; };
                td.title = "";
                td.style.cursor = "default";
                td.onmouseover = function () { return false; };
                td.onmouseout = function () { return false; };
                td.appendChild(img);
            }

            tr.appendChild(td);
        }

        tbody.appendChild(tr);
    }
}

window.addEventListener("resize", function () {
    if (lastCenterX !== null && lastCenterY !== null) {
        showMap(lastCenterX, lastCenterY);
    }
});
