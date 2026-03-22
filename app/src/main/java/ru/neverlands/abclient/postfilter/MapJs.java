package ru.neverlands.abclient.postfilter;

import java.nio.charset.Charset;

/**
 * Пост-фильтр для `js/map.js`.
 *
 * Назначение:
 * - гарантировать наличие базовых JS-функций, которые серверный `map.js` вызывает в верхнем фрейме;
 * - предотвратить падение скрипта на старте (`ReferenceError: ins_HP is not defined`),
 *   из-за которого верхний фрейм остаётся белым.
 *
 * Зависимости:
 * - вызывается маршрутизатором {@link Filter#process(android.content.Context, String, byte[])};
 * - применяется только для ответов `.../js/map.js` (включая вариант с query-параметрами `?v=...`);
 * - не меняет игровую логику `map.js`, только добавляет безопасный prelude с no-op stubs.
 */
public class MapJs {
    private static final String ABCLIENT_MAP_STUB_MARKER = "/*ABCLIENT_MAP_STUBS*/";
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    /**
     * C#-паритет для no-captcha рыбалки:
     * сервер может прислать маркер капчи как пустую строку ("") или строку "00000".
     *
     * В оригинальном `map.js` автозаброс без капчи запускается только по условию `!ingr[1]`,
     * из-за чего кейс `ingr[1] == "00000"` не попадает в auto-flow и `act=2` не отправляется.
     *
     * Зависимости:
     * - server payload `fish_ajax.php?act=1` (`ingr[1]` = captcha-token);
     * - bridge `window.external.IsAutoFish()` / `SetFishNoCaptchaReady()`;
     * - `FishAjaxPhp.processFishAct1(...)`, где "00000" уже трактуется как "капча не требуется".
     */
    private static final String FISH_NO_CAPTCHA_CONDITION_OLD =
            "if (!ingr[1] && window.external.IsAutoFish()) {";
    private static final String FISH_NO_CAPTCHA_CONDITION_NEW =
            "if ((!ingr[1] || ingr[1] == '00000') && window.external.IsAutoFish()) {";

    /**
     * JS-prelude со стабами функций, которые должны существовать до выполнения server-side `map.js`.
     *
     * Включает:
     * - алиас {@code window.external = window.AndroidBridge} — гарантирует, что все вызовы
     *   {@code window.external.ShowOverWarning()}, {@code window.external.IsAutoFish()} и т.д.
     *   доступны даже если map.js грузится до {@code onPageFinished} основного фрейма;
     * - no-op стабы функций, предотвращающие {@code ReferenceError} при инициализации скрипта.
     */
    private static final String MAP_JS_SAFE_PRELUDE =
            ABCLIENT_MAP_STUB_MARKER + "\n" +
            "if (typeof window.AndroidBridge !== 'undefined') { window.external = window.AndroidBridge; }\n" +
            "if (typeof window.ins_HP !== 'function') { window.ins_HP = function() {}; }\n" +
            "if (typeof window.cha_HP !== 'function') { window.cha_HP = function() {}; }\n" +
            "if (typeof window.slots_inv !== 'function') { window.slots_inv = function() {}; }\n";

    /**
     * Возвращает `map.js` с добавленным prelude-стабом.
     *
     * Зависимости и контракт:
     * - вход: сырые байты JS из сетевого ответа (`windows-1251`/ASCII-совместимый контент);
     * - выход: те же байты + prelude в начале файла;
     * - повторно prelude не добавляется (по маркеру `ABCLIENT_MAP_STUB_MARKER`).
     */
    public static byte[] process(byte[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        String js = new String(array, WINDOWS_1251);
        if (js.contains(ABCLIENT_MAP_STUB_MARKER)) {
            return array;
        }

        // Нормализуем no-captcha условие авто-рыбалки: учитываем пустой токен и "00000".
        String normalizedFishNoCaptcha = js.replace(FISH_NO_CAPTCHA_CONDITION_OLD, FISH_NO_CAPTCHA_CONDITION_NEW);
        String patched = MAP_JS_SAFE_PRELUDE + normalizedFishNoCaptcha;
        return patched.getBytes(WINDOWS_1251);
    }
}
