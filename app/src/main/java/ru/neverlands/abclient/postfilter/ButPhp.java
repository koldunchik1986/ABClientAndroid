package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

public class ButPhp {
    public static byte[] process(String address, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            html = html.replace("/b1.gif", "/b1.gif name=butinp");
            html = html.replace("smile_open('')", "if(window.external&&window.external.showSmiles){window.external.showSmiles(1);}");
            html = html.replace("smile_open('2')", "if(window.external&&window.external.showSmiles){window.external.showSmiles(2);}");

            String inject = "<script type=\"text/javascript\">" +
                    "(function(){" +
                    "function formHasText(f){ if(!f||!f.elements) return false; for(var i=0;i<f.elements.length;i++){ var el=f.elements[i]; if(!el) continue; var tag=(el.tagName||'').toLowerCase(); var type=(el.type||'').toLowerCase(); if(tag==='textarea'||type==='text') return true; if(el.name && (el.name==='text'||el.name==='msg')) return true; } return false; }" +
                    "function collect(f){ try{ return new URLSearchParams(new FormData(f)).toString(); }catch(e){ var parts=[]; if(!f||!f.elements) return ''; for(var i=0;i<f.elements.length;i++){ var el=f.elements[i]; if(!el||!el.name) continue; var t=(el.type||'').toLowerCase(); if((t==='checkbox'||t==='radio') && !el.checked) continue; parts.push(encodeURIComponent(el.name)+'='+encodeURIComponent(el.value||'')); } return parts.join('&'); } }" +
                    "function hookForm(f,idx){ if(!f||f._abclientHook) return; if(!formHasText(f)) return; f._abclientHook=true; console.log('ABCLIENT_CHAT_HOOK', f.name||f.id||('form'+idx));" +
                    "function submitOverride(){ var ok=true; try{ if(typeof f.onsubmit==='function'){ ok = f.onsubmit()!==false; } }catch(e){} if(!ok) return false;" +
                    "var data=collect(f); var action=f.action||f.getAttribute('action')||location.href; var method=(f.method||'POST').toUpperCase();" +
                    "if(window.AndroidBridge&&AndroidBridge.chatSubmit){ console.log('ABCLIENT_CHAT_SUBMIT', method, action, data.length); AndroidBridge.chatSubmit(action, method, data); return false; } return true; }" +
                    "f.addEventListener('submit', function(e){ if(!submitOverride()) e.preventDefault(); }, true);" +
                    "var orig=f.submit; f.submit=function(){ submitOverride(); };" +
                    "}" +
                    "function hookAll(){ var forms=document.forms||[]; for(var i=0;i<forms.length;i++){ hookForm(forms[i], i); } }" +
                    "hookAll(); setTimeout(hookAll, 300); setTimeout(hookAll, 1000);" +
                    "})();" +
                    "</script>";
            if (html.toLowerCase().contains("</head>")) {
                html = html.replaceFirst("(?i)</head>", inject + "</head>");
            } else {
                html = inject + html;
            }

            return Russian.getBytes(html);
        } catch (Exception e) {
            return array;
        }
    }
}
