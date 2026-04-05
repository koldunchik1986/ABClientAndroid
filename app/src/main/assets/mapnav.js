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

var abcmapwidth = (((width * 2) + 1) * scale) + (width * 2) + 2;
var abcmapheight = (((height * 2) + 1) * scale) + (height * 2) + 2;
document.write("<div style=\"text-align:center\"><div style=\"display:inline-block;\">");
document.write("<div id=\"world_cont\" style=\"position: absolute; text-align: center; overflow: hidden; width:" + abcmapwidth + "px; height:" + abcmapheight + "px;\"></div>");
document.write("<div id=\"world_cont2\" style=\"width: " + abcmapwidth + "px; height: " + abcmapheight + "px; text-align: left;\"></div>");
document.write("</div></div>");

function showMap(x, y) {
    var table, tbody, tr, td;
    var bridge = __abBridge();

    if (!world) {
        world = document.createElement("DIV");
        world.id = "world_map";
        document.getElementById("world_cont").appendChild(world);
    }

    world.innerHTML = "";

    table = document.createElement("TABLE");
    table.cellPadding = 0;
    table.cellSpacing = 0;
    table.bgColor = "black";
    table.border = "1px solid black";
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
            td.style.backgroundImage = "url(http://image.neverlands.ru/map/world/" + "day" + "/" + dy + "/" + dx + "_" + dy + ".jpg)";
            td.style.backgroundRepeat = "no-repeat";
            td.style.backgroundPosition = "left top";
            td.style.borderWidth = "0";

            td.style.width = "100px";
            td.style.height = "100px";
            td.style.display = "inline-block";
            td.style.verticalAlign = "top";
            td.style.textAlign = "left";
            td.style.opacity = "0.8";
            td.style.filter = "alpha(opacity=80)";

            var isCellExists = bridge && bridge.IsCellExists ? bridge.IsCellExists(dx, dy) : false;
            if (isCellExists) {
                var isframe = (i == 0) && (j == 0);
                td.onclick = function (dx, dy) {
                    return function () {
                        if (!bridge || !bridge.MoveTo || !bridge.GenMoveLink) return false;
                        bridge.MoveTo(bridge.GenMoveLink(dx, dy));
                    };
                }(dx, dy);
                td.title = bridge && bridge.CellAltText ? bridge.CellAltText(dx, dy, scale) : "";
                td.style.cursor = "pointer";
                td.onmouseover = function () { this.style.opacity = "1.0"; this.style.filter = "alpha(opacity=100)"; };
                td.onmouseout = function () { this.style.opacity = "0.8"; this.style.filter = "alpha(opacity=80)"; };
                td.innerHTML = bridge && bridge.CellDivText
                    ? bridge.CellDivText(dx, dy, scale, "", false, isframe)
                    : "";
            }
            else {
                td.style.opacity = "0.4";
                td.style.filter = "alpha(opacity=40)";
                td.onclick = function () { return false; };
                td.title = "";
                td.style.cursor = "default";
                td.onmouseover = function () { return false; };
                td.onmouseout = function () { return false; };
                td.innerHTML = "";
            }

            tr.appendChild(td);
        }

        tbody.appendChild(tr);
    }
}
