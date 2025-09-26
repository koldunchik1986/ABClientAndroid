package ru.neverlands.abclient.postfilter;

public class CastleJs {
    /**
     * Обрабатывает /js/castle.js.
     * В оригинальном C# коде сюда добавлялся полифил json2.js, но вызов был закомментирован.
     * В современном WebView это не требуется.
     * @param array Массив байт, содержащий JS-код.
     * @return Неизмененный массив байт.
     */
    public static byte[] process(byte[] array) {
        return array;
    }
}