package ru.neverlands.anclient.info;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.neverlands.anclient.network.NetworkClient;
import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.utils.AppVars;

/**
 * Единый loader еженедельных рейтингов `service.neverlands.ru/rate/weekly_{id}.txt`.
 */
public final class ProfessionRatingRepository {
    private static final String TAG = "ProfessionRatingRepository";
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final String RATE_URL_FORMAT = "http://service.neverlands.ru/rate/weekly_%d.txt";
    private static final String SIGN_BASE_URL = "http://image.neverlands.ru/signs/";
    private static final String INFO_ICON_URL = "http://image.neverlands.ru/chat/info.gif";

    private static final List<Category> CATEGORIES;
    private static final Map<Integer, RatingTable> CACHE = new LinkedHashMap<>();

    static {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category(1, "Каллиграфия"));
        categories.add(new Category(2, "Ювелирное дело"));
        categories.add(new Category(3, "Ремесленник"));
        categories.add(new Category(4, "Доктор"));
        categories.add(new Category(5, "Алхимия"));
        categories.add(new Category(6, "Развитие горного дела"));
        categories.add(new Category(7, "Рыбалка"));
        categories.add(new Category(8, "Охота"));
        categories.add(new Category(9, "Кулинария"));
        categories.add(new Category(10, "Лесозаготовка"));
        categories.add(new Category(11, "Плотник"));
        categories.add(new Category(12, "Сталевар"));
        categories.add(new Category(13, "Травник"));
        categories.add(new Category(14, "Торговец"));
        categories.add(new Category(51, "Рукопашный бой"));
        categories.add(new Category(52, "Воины"));
        categories.add(new Category(53, "Гладиаторы"));
        categories.add(new Category(54, "Арена"));
        CATEGORIES = Collections.unmodifiableList(categories);
    }

    private ProfessionRatingRepository() {
    }

    public static List<Category> getCategories() {
        return CATEGORIES;
    }

    public static Category findCategory(int id) {
        for (Category category : CATEGORIES) {
            if (category.id == id) {
                return category;
            }
        }
        return null;
    }

    public static synchronized RatingTable getCachedRating(int id) {
        return CACHE.get(id);
    }

    public static RatingTable loadRating(int id, boolean forceRefresh) throws IOException {
        Category category = findCategory(id);
        if (category == null) {
            throw new IOException("Unknown rating id: " + id);
        }
        synchronized (ProfessionRatingRepository.class) {
            RatingTable cached = CACHE.get(id);
            if (!forceRefresh && cached != null) {
                return cached;
            }
        }

        String url = buildRatingUrl(id);
        String rawText = fetchRatingText(url);
        RatingTable table = parseRating(category, url, rawText);
        synchronized (ProfessionRatingRepository.class) {
            CACHE.put(id, table);
        }
        return table;
    }

    public static String buildRatingUrl(int id) {
        return String.format(Locale.US, RATE_URL_FORMAT, id);
    }

    public static String buildPinfoUrl(String nick) {
        String safeNick = nick == null ? "" : nick.trim();
        try {
            String encoded = URLEncoder.encode(safeNick, "windows-1251")
                    .replace("+", "%20");
            return "http://www.neverlands.ru/pinfo.cgi?" + encoded;
        } catch (Exception error) {
            return "http://www.neverlands.ru/pinfo.cgi?" + safeNick.replace(" ", "%20");
        }
    }

    public static String buildTotemIconUrl(int clanTotem) {
        switch (clanTotem) {
            case 1:
                return SIGN_BASE_URL + "darks.gif";
            case 2:
                return SIGN_BASE_URL + "lights.gif";
            case 3:
                return SIGN_BASE_URL + "sumers.gif";
            case 4:
                return SIGN_BASE_URL + "chaoss.gif";
            default:
                return "";
        }
    }

    public static String buildClanIconUrl(String clanIco) {
        String value = clanIco == null ? "" : clanIco.trim();
        return value.isEmpty() ? "" : SIGN_BASE_URL + value;
    }

    public static String getInfoIconUrl() {
        return INFO_ICON_URL;
    }

    public static String normalizeNick(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String fetchRatingText(String url) throws IOException {
        OkHttpClient client = NetworkClient.getInstance();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", AppVars.BROWSER_USER_AGENT)
                .header("Accept", "text/plain,*/*")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response for " + url);
            }
            String text = new String(body.bytes(), WINDOWS_1251);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            AppLog.d(TAG, "WEEKLY_RATING_FETCHED: url=" + url + ", chars=" + text.length());
            return text;
        }
    }

    private static RatingTable parseRating(Category category, String sourceUrl, String rawText) throws IOException {
        List<RatingEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(rawText == null ? "" : rawText))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\|", -1);
                if (parts.length < 5) {
                    AppLog.w(TAG, "WEEKLY_RATING_BAD_LINE: id=" + category.id + ", line=" + trimmed);
                    continue;
                }
                String nick = parts[2].trim();
                if (nick.isEmpty()) {
                    continue;
                }
                entries.add(new RatingEntry(
                        entries.size() + 1,
                        parseInt(parts[0]),
                        parts[1].trim(),
                        nick,
                        parseInt(parts[3]),
                        parseInt(parts[4])));
            }
        }
        AppLog.i(TAG, "WEEKLY_RATING_PARSED: id=" + category.id
                + ", title=" + category.title + ", entries=" + entries.size());
        return new RatingTable(category, sourceUrl, Collections.unmodifiableList(entries), System.currentTimeMillis());
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static final class Category {
        public final int id;
        public final String title;

        public Category(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public static final class RatingTable {
        public final Category category;
        public final String sourceUrl;
        public final List<RatingEntry> entries;
        public final long loadedAtMs;

        public RatingTable(Category category, String sourceUrl, List<RatingEntry> entries, long loadedAtMs) {
            this.category = category;
            this.sourceUrl = sourceUrl;
            this.entries = entries;
            this.loadedAtMs = loadedAtMs;
        }
    }

    public static final class RatingEntry {
        public final int rank;
        public final int clanTotem;
        public final String clanIco;
        public final String nick;
        public final int level;
        public final int rate;

        public RatingEntry(int rank, int clanTotem, String clanIco, String nick, int level, int rate) {
            this.rank = rank;
            this.clanTotem = clanTotem;
            this.clanIco = clanIco;
            this.nick = nick;
            this.level = level;
            this.rate = rate;
        }
    }
}
