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
     * JS-prelude со стабами функций, которые должны существовать до выполнения server-side `map.js`.
     * Важно: это no-op определения, они не вмешиваются в логику, а только предотвращают `ReferenceError`.
     */
    private static final String MAP_JS_SAFE_PRELUDE =
            ABCLIENT_MAP_STUB_MARKER + "\n" +
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

        String patched = MAP_JS_SAFE_PRELUDE + js;
        return patched.getBytes(WINDOWS_1251);
    }
}
