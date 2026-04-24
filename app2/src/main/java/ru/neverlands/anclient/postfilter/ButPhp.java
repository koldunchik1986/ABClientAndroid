package ru.neverlands.anclient.postfilter;

import ru.neverlands.anclient.utils.AppLog;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.HelperStrings;
import ru.neverlands.anclient.utils.Russian;

public class ButPhp {
    private static final String TAG = "ButPhp";
    private static final Pattern SERVER_DATE_PATTERN = Pattern.compile(
            "serverDate\\s*=\\s*new\\s+Date\\((\\d{4})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SERVER_TIME_DIV_PATTERN = Pattern.compile(
            "id\\s*=\\s*['\"]?serverTime['\"]?[^>]*>\\s*(\\d{1,2}):(\\d{2}):(\\d{2})\\s*<",
            Pattern.CASE_INSENSITIVE
    );
    private static boolean timeDebugLogged = false;

    /**
     * Фильтр but.php (кнопки чата).
     * Зависимости:
     * - Russian.getString / Russian.getBytes: декодирование windows-1251.
     * - AppVars.Profile.ServDiff: хранит смещение локального времени относительно серверного.
     * - AppVars.ServerDateTime: кеш точного серверного времени.
     * - HelperStrings: извлечение подстрок.
     * Назначение:
     * - Синхронизирует серверное время сразу при загрузке but.php (как в ПК версии).
     * - Добавляет name=butinp и заменяет вызовы smilе_open на AndroidBridge.
     * - Инъекция JS для перехвата отправки сообщений чата через скрытый ch_refr.
     */
    public static byte[] process(String address, byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        try {
            String html = Russian.getString(array);

            // В but.php сервер отдаёт текущие часы/минуты/секунды.
            // По аналогии C# (PostFilter.ButPhp) вычисляем ServDiff = localNow - serverNow
            // и сохраняем в профиль для дальнейшего отображения серверного времени.
            int[] jsDate = extractServerDate(html);
            Integer hour = null;
            Integer min = null;
            Integer sec = null;
            Integer year = null;
            Integer month = null;
            Integer day = null;
            if (jsDate != null) {
                year = jsDate[0];
                month = jsDate[1];
                day = jsDate[2];
                hour = jsDate[3];
                min = jsDate[4];
                sec = jsDate[5];
            } else {
                int[] divTime = extractServerTimeDiv(html);
                if (divTime != null) {
                    hour = divTime[0];
                    min = divTime[1];
                    sec = divTime[2];
                } else {
                    hour = extractTimePart(html, "hour");
                    min = extractTimePart(html, "min");
                    sec = extractTimePart(html, "sec");
                }
            }
            if (hour != null && min != null && sec != null) {
                try {
                    long now = System.currentTimeMillis();
                    Calendar cal = Calendar.getInstance();
                    if (year != null && month != null && day != null) {
                        int normalizedMonth = normalizeMonthIndex(month);
                        cal.set(year, normalizedMonth, day, hour, min, sec);
                    } else {
                        cal.setTimeInMillis(now);
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        cal.set(Calendar.MINUTE, min);
                        cal.set(Calendar.SECOND, sec);
                    }
                    cal.set(Calendar.MILLISECOND, 0);
                    long serverMs = cal.getTimeInMillis();
                    // ServDiff нужен для показа "серверного" времени даже если
                    // системный часовой пояс на устройстве неверный.
                    long diff = now - serverMs;
                    if (Math.abs(diff) > 24L * 60L * 60L * 1000L) {
                        AppLog.w(TAG, "Parsed serverDate out of range, skipping update");
                    } else {
                        if (AppVars.Profile != null) {
                            AppVars.Profile.ServDiff = diff;
                        }
                        AppVars.ServerDateTime = new Date(serverMs);
                    }
                    if (year != null) {
                        int outMonth = normalizeMonthIndex(month) + 1;
                        AppLog.d(TAG, "Parsed serverDate: " + year + "-" + outMonth + "-" + day + " "
                                + hour + ":" + min + ":" + sec + ", diff(ms)=" + diff);
                    } else {
                        AppLog.d(TAG, "Parsed server time: " + hour + ":" + min + ":" + sec + ", diff(ms)=" + diff);
                    }
                } catch (Exception ignored) {
                }
            } else {
                AppLog.w(TAG, "Server time not found in but.php (hour/min/sec parse failed)");
                logTimeDebugOnce(html);
            }

            html = html.replace("/b1.gif", "/b1.gif name=butinp");
            html = html.replace("smile_open('')", "if(window.external&&window.external.showSmiles){window.external.showSmiles(1);}");
            html = html.replace("smile_open('2')", "if(window.external&&window.external.showSmiles){window.external.showSmiles(2);}");

            // Инъекция перехвата формы чата: submit -> AndroidBridge.chatSubmit (скрытый ch_refr).
            String inject = "<script type=\"text/javascript\">" +
                    "(function(){" +
                    "function formHasText(f){ if(!f||!f.elements) return false; for(var i=0;i<f.elements.length;i++){ var el=f.elements[i]; if(!el) continue; var tag=(el.tagName||'').toLowerCase(); var type=(el.type||'').toLowerCase(); if(tag==='textarea'||type==='text') return true; if(el.name && (el.name==='text'||el.name==='msg')) return true; } return false; }" +
                    "function collect(f){ try{ return new URLSearchParams(new FormData(f)).toString(); }catch(e){ var parts=[]; if(!f||!f.elements) return ''; for(var i=0;i<f.elements.length;i++){ var el=f.elements[i]; if(!el||!el.name) continue; var t=(el.type||'').toLowerCase(); if((t==='checkbox'||t==='radio') && !el.checked) continue; parts.push(encodeURIComponent(el.name)+'='+encodeURIComponent(el.value||'')); } return parts.join('&'); } }" +
                    "function hookForm(f,idx){ if(!f||f._anclientHook) return; if(!formHasText(f)) return; f._anclientHook=true; console.log('ANCLIENT_CHAT_HOOK', f.name||f.id||('form'+idx));" +
                    "function submitOverride(){ var ok=true; try{ if(typeof f.onsubmit==='function'){ ok = f.onsubmit()!==false; } }catch(e){} if(!ok) return false;" +
                    "var data=collect(f); var action=f.action||f.getAttribute('action')||location.href; var method=(f.method||'POST').toUpperCase();" +
                    "if(window.AndroidBridge&&AndroidBridge.chatSubmit){ console.log('ANCLIENT_CHAT_SUBMIT', method, action, data.length); AndroidBridge.chatSubmit(action, method, data); return false; } return true; }" +
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

    private static Integer extractTimePart(String html, String key) {
        if (html == null || key == null) return null;
        String value = HelperStrings.subString(html, key + "=", "&");
        if (value == null || value.isEmpty()) {
            value = HelperStrings.subString(html, key + "=", "\"");
        }
        if (value == null || value.isEmpty()) {
            value = HelperStrings.subString(html, key + "=", ";");
        }
        if (value == null || value.isEmpty()) {
            Pattern pattern = Pattern.compile("\\b" + key + "\\s*[:=]\\s*['\"]?(\\d{1,2})", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                value = matcher.group(1);
            }
        }
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Извлекает serverDate из but.php (JS: new Date(year, month, day, hour, min, sec)).
     * Зависимости:
     * - Источник: HTML but.php (serverDate в JS).
     * - Учитывает, что месяц может быть как 0-based (JS Date), так и 1-based (серверный шаблон).
     * Использование:
     * - В process(): если есть serverDate — это самый точный источник времени сервера.
     */
    private static int[] extractServerDate(String html) {
        if (html == null) return null;
        Matcher matcher = SERVER_DATE_PATTERN.matcher(html);
        if (!matcher.find()) return null;
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = Integer.parseInt(matcher.group(4));
            int min = Integer.parseInt(matcher.group(5));
            int sec = Integer.parseInt(matcher.group(6));
            return new int[] { year, month, day, hour, min, sec };
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Извлекает текст времени из DIV id=serverTime (HH:mm:ss).
     * Зависимости:
     * - Источник: HTML but.php (визуальное время, уже рассчитанное в JS).
     * Использование:
     * - Резервный источник, если serverDate не найден.
     */
    private static int[] extractServerTimeDiv(String html) {
        if (html == null) return null;
        Matcher matcher = SERVER_TIME_DIV_PATTERN.matcher(html);
        if (!matcher.find()) return null;
        try {
            int hour = Integer.parseInt(matcher.group(1));
            int min = Integer.parseInt(matcher.group(2));
            int sec = Integer.parseInt(matcher.group(3));
            return new int[] { hour, min, sec };
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Нормализует индекс месяца для Calendar.
     * Зависимости:
     * - Calendar ожидает 0-based месяц.
     * - В but.php встречается 0-based и 1-based формат, поэтому выбираем ближайший
     *   к текущему месяцу устройства (минимальная разница).
     * Использование:
     * - При разборе serverDate из JS (new Date(...)).
     */
    private static int normalizeMonthIndex(int month) {
        if (month >= 1 && month <= 12) {
            int candidate1 = month - 1;
            int candidate2 = month;
            int nowMonth = Calendar.getInstance().get(Calendar.MONTH);
            int diff1 = Math.abs(candidate1 - nowMonth);
            int diff2 = Math.abs(candidate2 - nowMonth);
            return diff1 <= diff2 ? candidate1 : candidate2;
        }
        if (month < 0) return 0;
        if (month > 11) return 11;
        return month;
    }

    private static void logTimeDebugOnce(String html) {
        if (timeDebugLogged || html == null) return;
        timeDebugLogged = true;
        String lower = html.toLowerCase();
        AppLog.d(TAG, "but.php length=" + html.length()
                + ", hasHour=" + lower.contains("hour")
                + ", hasMin=" + lower.contains("min")
                + ", hasSec=" + lower.contains("sec"));
        int idx = lower.indexOf("hour");
        if (idx != -1) {
            int start = Math.max(0, idx - 60);
            int end = Math.min(html.length(), idx + 160);
            AppLog.d(TAG, "but.php around 'hour': " + html.substring(start, end));
        } else {
            AppLog.d(TAG, "but.php head: " + html.substring(0, Math.min(300, html.length())));
        }
    }
}
