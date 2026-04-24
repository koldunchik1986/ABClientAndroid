package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.HtmlUtils;

/**
 * Единый генератор служебных redirect-страниц main.php.
 *
 * Источник выноса: MainPhp.buildRedirectHtml(description, link).
 * Зависимости: FightAuto.normalizeNeverlandsMainLink(link) нормализует относительные neverlands-ссылки,
 * HtmlUtils.GENERATED_PAGE_MARKER помечает страницу как сгенерированную клиентом для downstream-фильтров.
 */
final class MainPhpRedirects {

    private MainPhpRedirects() {
    }

    /**
     * Строит HTML, который сразу переводит WebView на normalizedLink через JavaScript.
     *
     * Важные переменные:
     * - description: текст в body для пользователя/лога.
     * - link: исходная server/client ссылка.
     * - normalizedLink: итог после FightAuto.normalizeNeverlandsMainLink(link), попадает в window.location.
     */
    static String buildRedirectHtml(String description, String link) {
        String normalizedLink = FightAuto.normalizeNeverlandsMainLink(link);
        return HtmlUtils.GENERATED_PAGE_MARKER
                + "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1251\">"
                + "<title>ABClient</title></head><body>"
                + description
                + "<script language=\"JavaScript\">window.location = \"" + normalizedLink + "\";</script></body></html>";
    }
}
