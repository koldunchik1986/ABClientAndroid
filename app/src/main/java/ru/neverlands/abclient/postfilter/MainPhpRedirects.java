package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.HtmlUtils;

final class MainPhpRedirects {

    private MainPhpRedirects() {
    }

    static String buildRedirectHtml(String description, String link) {
        String normalizedLink = FightAuto.normalizeNeverlandsMainLink(link);
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ABClient</title></head><body>"
                + description
                + "<script language=\"JavaScript\">window.location = \"" + normalizedLink + "\";</script></body></html>";
    }
}
