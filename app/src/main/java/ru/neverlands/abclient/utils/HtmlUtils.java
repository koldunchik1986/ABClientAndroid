package ru.neverlands.abclient.utils;

public final class HtmlUtils {
    private HtmlUtils() {}

    private static final String HTML_HEAD;
    private static final String HTML_MARKER;

    static {
        HTML_MARKER = "<SPAN class=massm>&nbsp;" + AppConsts.APPLICATION_NAME + "&nbsp;</SPAN> ";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head>");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">");
        sb.append("<META Http-Equiv=\"Cache-Control\" Content=\"No-Cache\">");
        sb.append("<META Http-Equiv=\"Pragma\" Content=\"No-Cache\">");
        sb.append("<META Http-Equiv=\"Expires\" Content=\"0\">");
        sb.append("<style type=\"text/css\">" +
                  "body {font-family:Tahoma, Verdana, Arial; font-size:11px; color:black; background-color:white;}" +
                  ".massm { color:white; background-color:#003893; }" +
                  ".gray { color:gray; }" +
                  "</style>");
        sb.append("</head><body>");
        sb.append(HTML_MARKER);
        HTML_HEAD = sb.toString();
    }

    public static String getHead() {
        return HTML_HEAD;
    }

    public static String getMarker() {
        return HTML_MARKER;
    }

    public static String getJsFix() {
        return "window.external = window.AndroidBridge;" +
                "if (typeof top.start !== 'function') { top.start = function() {}; }" +
                "if (typeof window.chatlist_build !== 'function') { window.chatlist_build = function() {}; }" +
                "if (typeof window.get_by_id !== 'function') { window.get_by_id = function(id) { return document.getElementById(id); }; }" +
                "if (typeof top.save_scroll_p !== 'function') { top.save_scroll_p = function() {}; }" +
                "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }" +
                "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }" +
                "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }" +
                "if (typeof window.compl_view !== 'function') { window.compl_view = function() {}; }" +
                "if (typeof window.view_t !== 'function') { window.view_t = function() {}; }" +
                "if (typeof top.ch_refresh_n !== 'function') { top.ch_refresh_n = function() {}; }" +
                "if (typeof window.ButClick !== 'function') { window.ButClick = function() {}; }" +
                "if (typeof top.frames == 'undefined' || !top.frames['main_top']) { " +
                "  if (typeof top.frames == 'undefined') { top.frames = {}; } " +
                "  if (!top.frames['ch_buttons']) { top.frames['ch_buttons'] = { set location(url) { AndroidBridge.loadFrame('ch_buttons', url); } }; } " +
                "  if (!top.frames['ch_refr']) { top.frames['ch_refr'] = { set location(url) { AndroidBridge.loadFrame('ch_refr', url); } }; } " +
                "  if (!top.frames['ch_list']) { top.frames['ch_list'] = { set location(url) { AndroidBridge.loadFrame('ch_list', url); } }; } " +
                "  if (!top.frames['chmain']) { top.frames['chmain'] = { set location(url) { AndroidBridge.loadFrame('chmain', url); } }; } " +
                "  if (!top.frames['main_top']) { top.frames['main_top'] = { " +
                "    set location(url) { AndroidBridge.loadFrame('main_top', url); }, " +
                "    innerHeight: 800, " +
                "    innerWidth: 600, " +
                "    document: { " +
                "      write: function(s) { document.write(s); }, " +
                "      getElementById: function(id) { return document.getElementById(id); } " +
                "    } " +
                "  }; } " +
                "}" +
                "if (top.frames && top.frames['main_top']) { top.frames['main_top'].innerHeight = 800; top.frames['main_top'].innerWidth = 600; }";
    }

    public static byte[] injectJsFix(byte[] body, String url, String contentType) {
        try {
            if (body == null || body.length == 0) return body;
            String jsFix = getJsFix();
            if (contentType != null && contentType.contains("text/html")) {
                String html = Russian.getString(body);
                String fix = "<script type=\"text/javascript\">" + jsFix + "</script>";
                String newHtml = html.toLowerCase().contains("<head>") ? html.replaceFirst("(?i)<head>", "<head>" + fix) : fix + html;
                return Russian.getBytes(newHtml);
            }
            // НЕ инжектируем в .js файлы — стубы определяются в HTML <head> через injectJsFix,
            // а внешние .js файлы загружаются после и содержат реальные определения функций
            return body;
        } catch (Exception e) {
            return body;
        }
    }

    public static String buildPage(String bodyContent) {
        return getHead() + bodyContent + "</body></html>";
    }
}
