package ru.neverlands.abclient.manager;

import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.HelperStrings;

/**
 * Менеджер быстрых действий (портирование FormMainFast.cs + PostFilter/MainPhpFast.cs).
 *
 * Часть 1 (FormMainFast.cs): Управление — fastStart, fastCancel, fastAttack*
 * Часть 2 (MainPhpFast.cs): Парсинг HTML — processMainPhp, mainPhpFast*
 *
 * Паттерн работы:
 * 1. Пользователь нажимает кнопку в QuickActionsBottomSheet → fastAttack*(nick)
 * 2. fastStart(weapon, nick) устанавливает AppVars.FastNeed = true
 * 3. WebView перезагружает main.php
 * 4. Filter.process() → MainPhp.process() → проверяет AppVars.FastNeed → processMainPhp(html)
 * 5. processMainPhp парсит HTML, генерирует форму с авто-submit → WebView отправляет POST
 */
public class FastActionManager {
    private static final String TAG = "FastActionManager";

    // Стандартная HTML-шапка для генерируемых страниц (аналог HelperErrors.Head() в C#)
    private static final String HTML_HEAD = "<html><head><meta http-equiv=\"Content-Type\" " +
            "content=\"text/html; charset=windows-1251\"></head><body>";

    // --- Часть 1: Управление (из FormMainFast.cs) ---

    /**
     * Запуск быстрого действия (аналог FastStartSafe в C#).
     * Устанавливает глобальные переменные и инициирует перезагрузку main.php.
     */
    public static void fastStart(String id, String nick) {
        fastStart(id, nick, 1);
    }

    public static void fastStart(String id, String nick, int count) {
        AppVars.FastNeed = true;
        AppVars.FastId = id;
        AppVars.FastNick = nick;
        AppVars.FastCount = count;
        Log.d(TAG, "fastStart: id=" + id + ", nick=" + nick + ", count=" + count);
        reloadMainFrame();
    }

    /**
     * Отмена быстрого действия (аналог FastCancelSafe в C#).
     */
    public static void fastCancel() {
        AppVars.FastNeed = false;
        AppVars.FastNick = null;
        AppVars.FastId = null;
        AppVars.FastCount = 0;
        AppVars.FastNeedAbilDarkTeleport = false;
        AppVars.FastNeedAbilDarkFog = false;

        if (AppVars.FastWaitEndOfBoiActive) {
            AppVars.FastWaitEndOfBoiCancel = true;
        }
        Log.d(TAG, "fastCancel");
    }

    /**
     * Убирает теги <i></i> из ника (аналог StripItalic в C#).
     */
    public static String stripItalic(String nick) {
        if (nick == null) return "";
        return nick.replace("<i>", "").replace("</i>", "").trim();
    }

    // --- Методы быстрых атак (из FormMainFast.cs) ---
    // Каждый метод устанавливает weapon (=FastId) и вызывает fastStart

    /** Обычная нападалка (аналог FormMain.FastAttack) */
    public static void fastAttack(String nick) {
        fastStart("i_svi_001.gif", stripItalic(nick));
    }

    /** Кровавая нападалка (аналог FormMain.FastAttackBlood) */
    public static void fastAttackBlood(String nick) {
        fastStart("i_svi_002.gif", stripItalic(nick));
    }

    /** Боевая нападалка (аналог FormMain.FastAttackUltimate) */
    public static void fastAttackUltimate(String nick) {
        fastStart("i_w28_26.gif", stripItalic(nick));
    }

    /** Закрытая боевая нападалка (аналог FormMain.FastAttackClosedUltimate) */
    public static void fastAttackClosedUltimate(String nick) {
        fastStart("i_w28_26X.gif", stripItalic(nick));
    }

    /** Закрытая нападалка (аналог FormMain.FastAttackClosed) */
    public static void fastAttackClosed(String nick) {
        fastStart("i_svi_205.gif", stripItalic(nick));
    }

    /** Обычная кулачка (аналог FormMain.FastAttackFist) */
    public static void fastAttackFist(String nick) {
        fastStart("i_w28_24.gif", stripItalic(nick));
    }

    /** Закрытая кулачка (аналог FormMain.FastAttackClosedFist) */
    public static void fastAttackClosedFist(String nick) {
        fastStart("i_w28_25.gif", stripItalic(nick));
    }

    /** Туман (аналог FormMain.FastAttackFog) — без ожидания боя */
    public static void fastAttackFog(String nick) {
        fastStart("i_svi_213.gif", stripItalic(nick));
    }

    /** Яд (аналог FormMain.FastAttackPoison) */
    public static void fastAttackPoison(String nick) {
        fastStart("Яд", stripItalic(nick));
    }

    /** Сильная спина (аналог FormMain.FastAttackStrong) */
    public static void fastAttackStrong(String nick) {
        fastStart("Зелье Сильной Спины", stripItalic(nick));
    }

    /** Невидимость (аналог FormMain.FastAttackNevidPot) */
    public static void fastAttackNevidPot(String nick) {
        fastStart("Зелье Невидимости", stripItalic(nick));
    }

    /** Портал (аналог FormMain.FastAttackPortal) */
    public static void fastAttackPortal(String nick) {
        fastStart("i_w28_86.gif", stripItalic(nick));
    }

    /** Защита (аналог FormMain.FastAttackZas) */
    public static void fastAttackZas(String nick) {
        fastStart("i_w28_27.gif", stripItalic(nick));
    }

    // --- Часть 2: Парсинг HTML (из PostFilter/MainPhpFast.cs) ---

    /**
     * Основной диспетчер (аналог MainPhpFast в C#).
     * Вызывается из MainPhp.process() когда AppVars.FastNeed == true.
     *
     * @param html HTML-содержимое страницы main.php
     * @return Сгенерированный HTML с авто-submit формой, или null если действие не найдено
     */
    public static String processMainPhp(String html) {
        Log.d(TAG, "processMainPhp: FastNeed=" + AppVars.FastNeed + ", FastId=" + AppVars.FastId
                + ", FastNick=" + AppVars.FastNick + ", htmlLen=" + (html != null ? html.length() : 0));
        if (!AppVars.FastNeed || AppVars.FastId == null) return null;

        // Логируем наличие ключевых паттернов в HTML
        Log.d(TAG, "processMainPhp: contains w28_form=" + html.contains("w28_form(")
                + ", magicreform=" + html.contains("magicreform(")
                + ", abil_svitok=" + html.contains("abil_svitok("));

        String result = null;
        String fastId = AppVars.FastId;

        switch (fastId) {
            // Нападалки (w28_form парсинг)
            case "i_svi_001.gif":
                result = mainPhpFastHit(html, new String[]{"1", "2", "3", "4"}, "обычную нападалку");
                break;
            case "i_svi_002.gif":
                result = mainPhpFastHit(html, new String[]{"5", "6", "7", "8"}, "кровавую нападалку");
                break;
            case "i_w28_26.gif":
                result = mainPhpFastHit(html, new String[]{"26"}, "боевую нападалку");
                break;
            case "i_w28_26X.gif":
                result = mainPhpFastHit(html, new String[]{"29"}, "закрытую боевую нападалку");
                break;
            case "i_svi_205.gif":
                result = mainPhpFastHit(html, new String[]{"30"}, "закрытую нападалку");
                break;
            case "i_w28_24.gif":
                result = mainPhpFastHit(html, new String[]{"24"}, "кулачку");
                break;
            case "i_w28_25.gif":
                result = mainPhpFastHit(html, new String[]{"25"}, "закрытую кулачку");
                break;

            // Абилки
            case "i_svi_213.gif":
                result = mainPhpFastFog(html);
                break;
            case "i_w28_27.gif":
                result = mainPhpFastW28(html, "27", "свиток защиты к");
                break;
            case "i_w28_86.gif":
                result = mainPhpFastW28(html, "86", "портал на");
                break;
            case "i_w28_22.gif":
                result = mainPhpFastW28(html, "22", "телепорт");
                break;

            // Зелья (magicreform парсинг)
            case "Яд":
            case "Зелье Сильной Спины":
            case "Зелье Невидимости":
            case "Зелье Блаженства":
            case "Зелье Метаболизма":
            case "Зелье Просветления":
            case "Зелье Сокрушительных Ударов":
            case "Зелье Стойкости":
            case "Зелье Недосягаемости":
            case "Зелье Точного Попадания":
            case "Зелье Ловких Ударов":
            case "Зелье Мужества":
            case "Зелье Жизни":
            case "Зелье Лечения":
            case "Зелье Восстановления Маны":
            case "Зелье Энергии":
            case "Зелье Удачи":
            case "Зелье Силы":
            case "Зелье Ловкости":
            case "Зелье Гения":
            case "Зелье Боевой Славы":
            case "Зелье Секрет Волшебника":
            case "Зелье Медитации":
            case "Зелье Иммунитета":
            case "Зелье Лечения Отравлений":
            case "Зелье Огненного Ореола":
            case "Зелье Колкости":
            case "Зелье Загрубелой Кожи":
            case "Зелье Панциря":
            case "Зелье Человек-гора":
            case "Зелье Скорости":
            case "Жажда Жизни":
            case "Ментальная Жажда":
            case "Зелье подвижности":
            case "Ярость Берсерка":
            case "Зелье Хрупкости":
            case "Зелье Мифриловый Стержень":
            case "Зелье Соколиный взор":
            case "Секретное Зелье":
                result = mainPhpFastPotion(html);
                break;

            default:
                Log.w(TAG, "processMainPhp: неизвестный FastId = " + fastId);
                break;
        }

        if (result != null) {
            // Действие выполнено, уменьшаем счётчик
            AppVars.FastCount--;
            if (AppVars.FastCount <= 0) {
                AppVars.FastNeed = false;
            }
            Log.d(TAG, "processMainPhp: УСПЕХ для FastId=" + fastId + ", resultLen=" + result.length());
            Log.d(TAG, "processMainPhp: generated HTML: " + result.substring(0, Math.min(300, result.length())));
        } else {
            Log.w(TAG, "processMainPhp: НЕУДАЧА, result=null для FastId=" + fastId);
        }

        return result;
    }

    // --- Парсеры ---

    /**
     * Универсальный парсер w28_form для нападалок (аналог mainPhpFastHit/BloodHit/Ultimate/etc в C#).
     * Все нападалки используют одинаковый паттерн, отличаясь только wsubid и post_id=8.
     *
     * @param html          HTML страницы main.php
     * @param validSubIds   допустимые значения wsubid (например {"1","2","3","4"} для обычной)
     * @param description   описание для лога ("обычную нападалку")
     * @return сгенерированный HTML с формой или null
     */
    private static String mainPhpFastHit(String html, String[] validSubIds, String description) {
        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            boolean validSub = false;
            for (String id : validSubIds) {
                if (wsubid.equals(id)) { validSub = true; break; }
            }
            if (!validSub) continue;

            // Генерируем HTML с формой авто-submit (аналог C# StringBuilder)
            return HTML_HEAD +
                    "Используем " + description + " на " + AppVars.FastNick + "..." +
                    "<form action=main.php method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"8\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=pnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                    "</form>" +
                    "<script language=\"JavaScript\">document.ff.submit();</script></body></html>";
        }

        Log.w(TAG, description + " не найдена в HTML");
        return null;
    }

    /**
     * Универсальный парсер w28_form для свитков/порталов (аналог mainPhpFastZas/Portal/Teleport в C#).
     * Используют post_id=25 и pnick (кроме телепорта).
     */
    private static String mainPhpFastW28(String html, String targetSubId, String description) {
        String patternW28Form = "w28_form(";
        int p1 = 0;
        while (p1 != -1) {
            p1 = html.indexOf(patternW28Form, p1);
            if (p1 == -1) break;

            p1 += patternW28Form.length();
            int p2 = html.indexOf(")", p1);
            if (p2 == -1) continue;

            String args = html.substring(p1, p2);
            if (args.isEmpty()) continue;

            String[] arg = args.split(",");
            if (arg.length < 4) continue;

            String vcode = arg[0].replace("'", "").trim();
            String wuid = arg[1].replace("'", "").trim();
            String wsubid = arg[2].replace("'", "").trim();
            String wsolid = arg[3].replace("'", "").trim();

            if (!wsubid.equals(targetSubId)) continue;

            return HTML_HEAD +
                    "Применяем " + description + " " + AppVars.FastNick + "..." +
                    "<form action=main.php method=POST name=ff>" +
                    "<input name=post_id type=hidden value=\"25\">" +
                    "<input name=vcode type=hidden value=\"" + vcode + "\">" +
                    "<input name=wuid type=hidden value=\"" + wuid + "\">" +
                    "<input name=wsubid type=hidden value=\"" + wsubid + "\">" +
                    "<input name=wsolid type=hidden value=\"" + wsolid + "\">" +
                    "<input name=pnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                    "</form>" +
                    "<script language=\"JavaScript\">document.ff.submit();</script></body></html>";
        }

        Log.w(TAG, description + " не найден в HTML");
        return null;
    }

    /**
     * Парсер для тумана (abil_svitok) — аналог mainPhpFastFog в C#.
     * Ищет abil_svitok('wuid','wmid','wmsolid','name','wmcode')
     */
    private static String mainPhpFastFog(String html) {
        String namesvitok = "'Свиток Искажающего Тумана'";
        int p0 = html.indexOf(namesvitok);
        if (p0 == -1) { Log.w(TAG, "Туман не найден"); return null; }

        int ps = html.lastIndexOf('<', p0);
        if (ps == -1) return null;
        ps++;
        int pe = html.indexOf('>', p0);
        if (pe == -1) return null;

        String chunk = html.substring(ps, pe);
        if (!chunk.contains("abil_svitok(")) return null;

        String args = HelperStrings.subString(chunk, "abil_svitok('", "')");
        if (args == null || args.isEmpty()) return null;

        String[] arg = args.split("'");
        if (arg.length < 9) return null;

        String wuid = arg[0];
        String wmid = arg[2];
        String wmsolid = arg[4];
        String wmcode = arg[8];

        return HTML_HEAD +
                "Используем Свиток Искажающего Тумана..." +
                "<form action=main.php method=POST name=ff>" +
                "<input name=post_id type=hidden value=\"44\">" +
                "<input name=uid type=hidden value=\"" + wuid + "\">" +
                "<input name=mid type=hidden value=\"" + wmid + "\">" +
                "<input name=curs type=hidden value=\"" + wmsolid + "\">" +
                "<input name=vcode type=hidden value=\"" + wmcode + "\">" +
                "<input name=fnick type=hidden value=\"" + AppVars.FastNick + "\">" +
                "</form>" +
                "<script language=\"JavaScript\">document.ff.submit();</script></body></html>";
    }

    /**
     * Парсер для зелий (magicreform) — аналог mainPhpFastPotion в C#.
     * Ищет magicreform('wuid','target','potionName','wmcode')
     */
    private static String mainPhpFastPotion(String html) {
        String namepotion = "'" + AppVars.FastId + "'";
        int p0 = indexOfIgnoreCase(html, namepotion, 0);
        if (p0 == -1) { Log.w(TAG, "Зелье не найдено: " + AppVars.FastId); return null; }

        int ps = html.lastIndexOf('<', p0);
        if (ps == -1) return null;
        ps++;
        int pe = html.indexOf('>', p0);
        if (pe == -1) return null;

        String chunk = html.substring(ps, pe);
        if (indexOfIgnoreCase(chunk, "magicreform(", 0) == -1) return null;

        String args = HelperStrings.subString(chunk, "magicreform('", "')");
        if (args == null || args.isEmpty()) return null;

        String[] arg = args.split("'");
        if (arg.length < 7) return null;

        String wuid = arg[0];
        String wmcode = arg[6];

        return HTML_HEAD +
                "Используем " + AppVars.FastId + "..." +
                "<form action=main.php method=POST name=ff>" +
                "<input name=magicrestart type=hidden value=\"1\">" +
                "<input name=magicreuid type=hidden value=\"" + wuid + "\">" +
                "<input name=vcode type=hidden value=\"" + wmcode + "\">" +
                "<input name=post_id type=hidden value=\"46\">" +
                "<input name=fornickname type=hidden value=\"" + AppVars.FastNick + "\">" +
                "</form>" +
                "<script language=\"JavaScript\">document.ff.submit();</script></body></html>";
    }

    // --- Утилиты ---

    /**
     * Перезагружает main.php в WebView через broadcast (аналог ReloadMainFrame в C#).
     * Навигирует на правильную вкладку инвентаря в зависимости от типа FastId:
     * - Нападалки/свитки (i_svi_*, i_w28_*) → вкладка свитков (wca=28)
     * - Зелья → вкладка зелий (wca=27)
     * - Абилки (туман) → вкладка свитков (wca=28)
     */
    private static void reloadMainFrame() {
        if (AppVars.getContext() == null) return;

        String url = getInventoryTabUrl();

        Intent intent = new Intent(AppVars.ACTION_WEBVIEW_LOAD_URL);
        intent.putExtra("url", url);
        LocalBroadcastManager.getInstance(AppVars.getContext()).sendBroadcast(intent);
    }

    /**
     * Определяет URL вкладки инвентаря по FastId (аналог логики из C# FormMainFast).
     */
    private static String getInventoryTabUrl() {
        if (AppVars.FastId == null) {
            return "http://neverlands.ru/main.php";
        }

        String fastId = AppVars.FastId;

        // Зелья → вкладка зелий (wca=27)
        if (!fastId.endsWith(".gif")) {
            // Все зелья не имеют .gif суффикса (напр. "Яд", "Зелье Сильной Спины")
            return "http://neverlands.ru/main.php?get_id=56&act=10&im=0&wca=27";
        }

        // Нападалки и свитки → вкладка свитков (wca=28)
        if (fastId.startsWith("i_svi_") || fastId.startsWith("i_w28_")) {
            return "http://neverlands.ru/main.php?get_id=56&act=10&im=0&wca=28";
        }

        // Fallback — обычная main.php
        return "http://neverlands.ru/main.php";
    }

    /**
     * Case-insensitive indexOf (аналог string.IndexOf с StringComparison.CurrentCultureIgnoreCase).
     */
    private static int indexOfIgnoreCase(String source, String target, int fromIndex) {
        if (source == null || target == null) return -1;
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();
        return lowerSource.indexOf(lowerTarget, fromIndex);
    }
}
