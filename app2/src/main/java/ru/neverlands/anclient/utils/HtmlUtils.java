package ru.neverlands.anclient.utils;

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
        return "try { if (window.AndroidBridge) { window.external = window.AndroidBridge; } } catch(e) {}" +
                "if (typeof top.start !== 'function') { top.start = function() {}; }" +
                "if (typeof window.chatlist_build !== 'function') { window.chatlist_build = function() {}; }" +
                "if (typeof window.__anEnsureNode !== 'function') { window.__anEnsureNode = function(id, tag) { try { var list = document.querySelectorAll('[id=\\\"' + id + '\\\"]'); if (list && list.length) { for (var i = 0; i < list.length; i++) { if (!list[i].getAttribute || list[i].getAttribute('data-an-stub') !== '1') return list[i]; } return list[0]; } var host = document.body || document.documentElement; if (!host) return { innerHTML: '', value: '', style: {} }; var el = document.createElement(tag || 'div'); el.id = id; el.setAttribute('data-an-stub', '1'); el.style.display = 'none'; el.style.visibility = 'hidden'; host.appendChild(el); return el; } catch(e) { return { innerHTML: '', value: '', style: {} }; } }; }" +
                "if (!window.__anEnsureCriticalNodes) { window.__anEnsureCriticalNodes = function() { try { window.__anEnsureNode('transfer', 'div'); window.__anEnsureNode('complect', 'div'); window.__anEnsureNode('hbar', 'span'); } catch(e) {} }; }" +
                "if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', window.__anEnsureCriticalNodes); } else { window.__anEnsureCriticalNodes(); }" +
                "if (!document._anclientOrigGetElementById) { document._anclientOrigGetElementById = document.getElementById.bind(document); document.getElementById = function(id) { if (!id) return null; try { var list = document.querySelectorAll('[id=\\\"' + id + '\\\"]'); if (list && list.length) { for (var i = 0; i < list.length; i++) { if (!list[i].getAttribute || list[i].getAttribute('data-an-stub') !== '1') return list[i]; } return list[0]; } } catch(e) {} var el = document._anclientOrigGetElementById(id); if (el) return el; try { var host = document.body || document.documentElement; if (!host) return { innerHTML: '', value: '', style: {}, setAttribute: function(){}, appendChild: function(){}, removeAttribute: function(){}, getAttribute: function(){ return ''; } }; var dummy = document.createElement('span'); dummy.id = id; dummy.setAttribute('data-an-stub', '1'); dummy.style.display = 'none'; host.appendChild(dummy); return dummy; } catch(e2) { return { innerHTML: '', value: '', style: {}, setAttribute: function(){}, appendChild: function(){}, removeAttribute: function(){}, getAttribute: function(){ return ''; } }; } }; }" +
                // КРИТИЧНО: Переопределяем document.all() для совместимости с IE-синтаксисом из старого кода
                // Старый код использует document.all("transfer").innerHTML - это может вернуть null в WebView
                // Гарантируем, что всегда возвращается валидный элемент с методом innerHTML
                "if (typeof document.all !== 'function') { document.all = function(id) { var el = document.getElementById(id); if (el) return el; if (!id) return { innerHTML: '', value: '', style: {} }; try { var dummy = document.createElement('div'); dummy.id = id; dummy.style.display = 'none'; (document.body || document.documentElement).appendChild(dummy); return dummy; } catch(e) { return { innerHTML: '', value: '', style: {} }; } }; }" +
                // В некоторых страницах (и при отсутствии frames) скрипты делают get_by_id(...).innerHTML.
                // Чтобы не падать с TypeError на null, возвращаем dummy-элемент, если id не найден.
                "window.get_by_id = function(id) { return document.getElementById(id) || { innerHTML: '', value: '', style: {} }; };" +
                "if (typeof top.save_scroll_p !== 'function') { top.save_scroll_p = function() {}; }" +
                "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }" +
                "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }" +
                "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }" +
                "if (typeof window.compl_view !== 'function') { window.compl_view = function() {}; }" +
                "if (typeof window.view_t !== 'function') { window.view_t = function() {}; }" +
                "if (typeof top.DeleteTrue !== 'function') { top.DeleteTrue = function(thing){ return confirm('Вы действительно хотите выбросить ' + (thing || 'предмет') + '?'); }; }" +
                "if (typeof top.deletetrue !== 'function') { top.deletetrue = top.DeleteTrue; }" +
                // Мосты чата: обновление, отправка/очистка, и эмуляция frames['ch_*'].
                "if (typeof top.ch_refresh_n !== 'function') { top.ch_refresh_n = function(){ if(window.AndroidBridge && AndroidBridge.chatRefreshN){ AndroidBridge.chatRefreshN(); } }; }" +
                "if (typeof top.set_lmid !== 'function') { top.set_lmid = function(v){ if(window.AndroidBridge && AndroidBridge.chatSetLmid){ AndroidBridge.chatSetLmid(v); } }; }" +
                "if (typeof top.add_msg !== 'function') { top.add_msg = function(t){ if(window.AndroidBridge && AndroidBridge.chatAddMsg){ AndroidBridge.chatAddMsg(t); } }; }" +
                "if (typeof top.say_private !== 'function') { top.say_private = function(n){ if(window.AndroidBridge && AndroidBridge.chatSayPrivate){ AndroidBridge.chatSayPrivate(n); } }; }" +
                "if (typeof top.say_to !== 'function') { top.say_to = function(n){ if(window.AndroidBridge && AndroidBridge.chatSayTo){ AndroidBridge.chatSayTo(n); } }; }" +
                "if (typeof top.clr_input !== 'function') { top.clr_input = function(){ if(window.AndroidBridge && AndroidBridge.chatClearInput){ AndroidBridge.chatClearInput(); } }; }" +
                "if (typeof top.clr_chat !== 'function') { top.clr_chat = function(){ if(window.AndroidBridge && AndroidBridge.chatClearChat){ AndroidBridge.chatClearChat(); } }; }" +
                "if (typeof top.ch_refresh !== 'function') { top.ch_refresh = function(){ if(window.AndroidBridge && AndroidBridge.chatRefreshNow){ AndroidBridge.chatRefreshNow(); } }; }" +
                "if (typeof top.clan_private !== 'function') { top.clan_private = function(){ if(window.AndroidBridge && AndroidBridge.chatClanPrivate){ AndroidBridge.chatClanPrivate(); } }; }" +
                "if (typeof top.change_chatspeed !== 'function') { top.change_chatspeed = function(){ if(window.AndroidBridge && AndroidBridge.chatChangeChatSpeed){ AndroidBridge.chatChangeChatSpeed(); } }; }" +
                "if (typeof top.change_chatsetup !== 'function') { top.change_chatsetup = function(){ if(window.AndroidBridge && AndroidBridge.chatChangeChatSetup){ AndroidBridge.chatChangeChatSetup(); } }; }" +
                "if (typeof top.change_latrus !== 'function') { top.change_latrus = function(){ if(window.AndroidBridge && AndroidBridge.chatChangeLatrus){ AndroidBridge.chatChangeLatrus(); } }; }" +
                "if (typeof top.latrus === 'undefined') { top.latrus = 0; }" +
                "if (typeof window.__anEnsureChatButtonsFrame !== 'function') { window.__anEnsureChatButtonsFrame = function(frame) { try { if (!frame) frame = {}; if (!frame.document) frame.document = {}; var doc = frame.document; if (!doc.FBT) doc.FBT = {}; var fbt = doc.FBT; if (!fbt.text) fbt.text = { value: '', focus: function(){ try { if (window.AndroidBridge && AndroidBridge.chatFocus) AndroidBridge.chatFocus(); } catch(e) {} } }; if (typeof fbt.text.focus !== 'function') fbt.text.focus = function(){ try { if (window.AndroidBridge && AndroidBridge.chatFocus) AndroidBridge.chatFocus(); } catch(e) {} }; if (!fbt.fyo) fbt.fyo = { value: 0 }; if (!fbt.lmid) fbt.lmid = { value: '' }; if (!fbt.schat) fbt.schat = { src: '', alt: '', title: '' }; if (!fbt.spchat) fbt.spchat = { src: '', alt: '', title: '' }; if (!fbt.lrchat) fbt.lrchat = { src: '', alt: '', title: '' }; if (typeof fbt.submit !== 'function') fbt.submit = function() {}; if (typeof doc.getElementById !== 'function') doc.getElementById = function(id) { return document.getElementById(id) || fbt[id] || { innerHTML: '', value: '', style: {}, focus: function(){} }; }; return frame; } catch(e) { return frame || {}; } }; }" +
                "if (typeof window.ButClick !== 'function') { window.ButClick = function() {}; }" +
                "if (typeof top.frames == 'undefined' || !top.frames['main_top']) { " +
                "  if (typeof top.frames == 'undefined') { top.frames = {}; } " +
                "  if (!top.frames['ch_buttons']) { top.frames['ch_buttons'] = window.__anEnsureChatButtonsFrame({ set location(url) { AndroidBridge.loadFrame('ch_buttons', url); } }); } " +
                "  if (!top.frames['ch_refr']) { top.frames['ch_refr'] = { set location(url) { AndroidBridge.loadFrame('ch_refr', url); } }; } " +
                "  if (!top.frames['ch_list']) { top.frames['ch_list'] = { set location(url) { AndroidBridge.loadFrame('ch_list', url); } }; } " +
                "  if (!top.frames['chmain']) { top.frames['chmain'] = { " +
                "    set location(url) { AndroidBridge.loadFrame('chmain', url); }, " +
                "    add_msg: function(t){ if (window.AndroidBridge && AndroidBridge.chatAddMsg) { AndroidBridge.chatAddMsg(t); } }, " +
                "    set_lmid: function(v){ if (window.AndroidBridge && AndroidBridge.chatSetLmid) { AndroidBridge.chatSetLmid(v); } }, " +
                "    ch_refresh_n: function(){ if (window.AndroidBridge && AndroidBridge.chatRefreshN) { AndroidBridge.chatRefreshN(); } }, " +
                "    document: { " +
                "      write: function(s) { document.write(s); }, " +
                "      getElementById: function(id) { return document.getElementById(id) || { innerHTML: '', value: '', style: {} }; } " +
                "    } " +
                "  }; } " +
                "  if (!top.frames['main_top']) { top.frames['main_top'] = { " +
                "    set location(url) { AndroidBridge.loadFrame('main_top', url); }, " +
                "    innerHeight: 800, " +
                "    innerWidth: 600, " +
                "    document: { " +
                "      write: function(s) { document.write(s); }, " +
                "      getElementById: function(id) { return document.getElementById(id) || { innerHTML: '', value: '', style: {} }; } " +
                "    } " +
                "  }; } " +
                "}" +
                "try { if (top.frames && top.frames['ch_buttons']) { top.frames['ch_buttons'] = window.__anEnsureChatButtonsFrame(top.frames['ch_buttons']); } } catch(e) {}" +
                "if (top.frames && top.frames['main_top']) { top.frames['main_top'].innerHeight = 800; top.frames['main_top'].innerWidth = 600; }";
    }

    /**
     * Маркер для генерируемых страниц (редиректы, формы быстрых действий).
     * Страницы с этим маркером НЕ получают инъекцию JS-стубов,
     * т.к. стубы могут помешать document.ff.submit() и window.location.
     */
    public static final String GENERATED_PAGE_MARKER = "<!--ANCLIENT_GENERATED-->";

    public static byte[] injectJsFix(byte[] body, String url, String contentType) {
        try {
            if (body == null || body.length == 0) return body;
            String jsFix = getJsFix();
            if (contentType != null && contentType.contains("text/html")) {
                String html = Russian.getString(body);
                // НЕ инжектируем стубы в наши генерируемые страницы (формы, редиректы).
                // Они содержат document.ff.submit() или window.location, и стубы могут помешать.
                if (html.contains(GENERATED_PAGE_MARKER)) {
                    return body;
                }
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
