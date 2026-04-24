package ru.neverlands.anclient.postfilter;

import java.nio.charset.Charset;

/**
 * Аналог C# CounterJs.cs — заменяет серверный counter.js.
 *
 * C# возвращает пустую заглушку {@code function counterview(referr){}} и не использует
 * counterview для навигации (C# полагается на собственный NeverTimer).
 *
 * Android не имеет NeverTimer, поэтому counterview должен работать по-настоящему:
 * при завершении отсчёта он вызывается с referr-URL и должен выполнить переход.
 * Используем AndroidBridge.redirectToUrl для надёжной навигации в WebView.
 */
public class CounterJs {
    private static final Charset CP1251 = Charset.forName("windows-1251");

    public static byte[] process() {
        String js =
            "function counterview(referr){" +
                "try{" +
                    "if(typeof AndroidBridge!=='undefined'" +
                        "&&typeof AndroidBridge.redirectToUrl==='function'){" +
                        "AndroidBridge.redirectToUrl(referr);" +
                    "}else{" +
                        "location.href=referr;" +
                    "}" +
                "}catch(e){" +
                    "location.href=referr;" +
                "}" +
            "}";
        return js.getBytes(CP1251);
    }
}
