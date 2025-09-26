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

    public static String buildPage(String bodyContent) {
        return getHead() + bodyContent + "</body></html>";
    }
}
