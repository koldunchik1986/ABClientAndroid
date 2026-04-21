package ru.neverlands.abclient.utils;

import ru.neverlands.abclient.utils.AppLog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// 🎯 Импорт FileLogger для критичного логирования

/**
 * SessionManager - единая система управления сессией с сервером.
 * 
 * Основные задачи:
 * 1. Парсить VCode из каждого HTML ответа сервера
 * 2. Валидировать VCode перед использованием в AJAX запросах
 * 3. Обрабатывать ошибки "Неверный код защиты"
 * 4. Отслеживать версию контекста для избежания конфликтов между модулями
 * 
 * По аналогии с C# версией: VCode это НЕ постоянный буфер, а одноразовая
 * переменная сеанса, которая парсится из текущего контента и сразу забывается.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final SessionManager instance = new SessionManager();

    private volatile SessionContext currentContext;
    private final ReentrantReadWriteLock contextLock = new ReentrantReadWriteLock();
    
    // Отслеживание боевого контекста - vcode должен жить дольше во время боя
    private volatile boolean fightInProgress = false;
    private volatile long fightStartTimeMs = 0L;
    private volatile String fightStartVCode = null;  // 🎯 Кэш vcode с момента начала боя
    private static final long FIGHT_CONTEXT_TIMEOUT = 120_000L;  // 2 минуты для боя
    
    // Паттерны для парсинга VCode из HTML
    private static final Pattern[] VCODE_PATTERNS = {
        // 🎯 fight_pm array (боевой контекст): var fight_pm = [300,200,70,0,"3f7c5d...",...]
        Pattern.compile("var\\s+fight_pm\\s*=\\s*\\[[^\\[\\]]*?[,\\s]\\d+[,\\s]+\"([a-f0-9]{32})\""),
        // vcode in hidden input: <input ... name=vcode value="...">
        Pattern.compile("(?i)name\\s*=\\s*['\"]?vcode['\"]?[^>]*?value\\s*=\\s*['\"]([a-f0-9]{32})['\"]"),
        // vcode as parameter in URL or form: &vcode=... or vcode=...  
        Pattern.compile("[&?]vcode\\s*=\\s*['\"]?([a-f0-9]{32})['\"]?"),
        // Bare "vcode=..." snippets used by direct SessionManager seeding from fish act=1.
        Pattern.compile("^vcode\\s*=\\s*['\"]?([a-f0-9]{32})['\"]?$"),
        // Generic vcode = "..."
        Pattern.compile("vcode\\s*[=:]\\s*['\"]([a-f0-9]{32})['\"]"),
        // var vcode = "..."
        Pattern.compile("var\\s+vcode\\s*=\\s*['\"]([a-f0-9]{32})['\"]"),
        // "vcode": "..." (JSON)
        Pattern.compile("['\"]vcode['\"]\\s*:\\s*['\"]([a-f0-9]{32})['\"]")
    };

    /**
     * Приватный конструктор - синглтон.
     */
    private SessionManager() {
        this.currentContext = null;
    }

    /**
     * Получить экземпляр SessionManager.
     */
    public static SessionManager getInstance() {
        return instance;
    }

    /**
     * Парсить VCode из HTML и обновить текущий контекст.
     * Вызывается из WebViewInterceptor для каждого ответа от сервера.
     * 
     * @param html HTML контент страницы
     * @param source Источник (например, "fish", "fight", "main")
     * @return SessionContext если VCode найден, null если не найден
     */
    public SessionContext parseVCodeFromHtml(String html, String source) {
        if (html == null || html.isEmpty()) {
            String msg = "PARSE_VCODE: html is empty, source=" + source;
            AppLog.w("SessionManager", TAG, "⚠️ " + msg);
            return null;
        }

        try {
            String vcode = extractVCode(html);
            if (vcode.isEmpty()) {
                String msg = "PARSE_VCODE_FAILED: no vcode found in HTML, source=" + source;
                AppLog.w("SessionManager", TAG, "⚠️ " + msg);
                return null;
            }

            // Инкрементировать версию контекста
            long newVersion = (currentContext != null ? currentContext.getContextVersion() : 0L) + 1;
            
            SessionContext newContext = new SessionContext(
                    html,
                    vcode,
                    System.currentTimeMillis(),
                    source,
                    newVersion
            );

            // Обновить с write-lock
            contextLock.writeLock().lock();
            try {
                currentContext = newContext;
                String msg = "VCODE_PARSED: source=" + source 
                    + ", vcode=" + vcode.substring(0, 8) + "..."
                    + ", version=" + newVersion
                    + ", ageMs=0";
                AppLog.d("SessionManager", TAG, "✅ " + msg);
            } finally {
                contextLock.writeLock().unlock();
            }

            return newContext;
        } catch (Exception e) {
            String msg = "VCODE_PARSE_ERROR: " + e.getMessage();
            AppLog.e("SessionManager", TAG, "❌ " + msg, e);
            return null;
        }
    }

    /**
     * Получить ВАЛИДНЫЙ VCode для использования в AJAX запросе.
     * 
     * Проверяет:
     * 1. Существует ли контекст
     * 2. Не пуст ли VCode
     * 3. Не истёк ли контекст по времени
     * 
     * Если контекст невалиден - логирует и возвращает null.
     * 
     * @param actionName Название действия (для логирования)
     * @param maxAgeMs Максимальный возраст контекста в миллисекундах
     * @return VCode если валиден, null если невалиден
     */
    public String getValidVCodeForAction(String actionName, long maxAgeMs) {
        contextLock.readLock().lock();
        try {
            // 🎯 Специальная логика для fight_fallback: ВСЕГДА использовать extended timeout (2 минуты)
            // потому что fight_fallback вызывается внутри боя и может нужен vcode из начального боевого ответа
            long actualTimeout = maxAgeMs;
            if ("fight_fallback".equals(actionName)) {
                actualTimeout = FIGHT_CONTEXT_TIMEOUT;  // 2 минуты вместо стандартных 5 минут
                String msg = "FIGHT_FALLBACK_MODE: using extended timeout " + actualTimeout + "ms";
                AppLog.d("SessionManager", TAG, "🎯 " + msg);
            }
            
            if (currentContext == null) {
                // 🎯 Если контекст пуст но идет бой - попробовать использовать cached vcode с начала боя
                if (fightInProgress && fightStartVCode != null && !fightStartVCode.isEmpty()) {
                    String msg = "FIGHT_CACHE: using cached vcode from fight start, vcode=" + fightStartVCode.substring(0, 8) + "...";
                    AppLog.d("SessionManager", TAG, "🎯 " + msg);
                    return fightStartVCode;
                }
                String noSessionMsg = "NO_SESSION: actionName=" + actionName + " - контекст пуст";
                AppLog.w("SessionManager", TAG, "⚠️ " + noSessionMsg);
                return null;
            }

            if (currentContext.getParsedVCode().isEmpty()) {
                String msg = "EMPTY_VCODE: actionName=" + actionName;
                AppLog.w("SessionManager", TAG, "⚠️ " + msg);
                return null;
            }

            if (currentContext.isExpired(actualTimeout)) {
                long ageMs = currentContext.getAgeMs();
                // 🎯 Если vcode устарел но идет бой - использовать cached vcode с начала боя
                if (fightInProgress && fightStartVCode != null && !fightStartVCode.isEmpty()) {
                    String msg = "FIGHT_CACHE: vcode expired but using cached vcode from fight start"
                        + ", ageMs=" + ageMs
                        + ", maxAgeMs=" + actualTimeout
                        + ", cached_vcode=" + fightStartVCode.substring(0, 8) + "...";
                    AppLog.d("SessionManager", TAG, "🎯 " + msg);
                    return fightStartVCode;
                }
                String staleMsg = "STALE_SESSION: actionName=" + actionName 
                    + ", ageMs=" + ageMs 
                    + ", maxAgeMs=" + actualTimeout;
                AppLog.w("SessionManager", TAG, "⚠️ " + staleMsg);
                return null;
            }

            String vcode = currentContext.getParsedVCode();
            String msg = "VALID_VCODE: actionName=" + actionName 
                + ", vcode=" + vcode.substring(0, 8) + "..."
                + ", ageMs=" + currentContext.getAgeMs()
                + ", source=" + currentContext.getSource();
            AppLog.d("SessionManager", TAG, "✅ " + msg);
            
            return vcode;
        } finally {
            contextLock.readLock().unlock();
        }
    }

    /**
     * Перегруженный метод с дефолтным тайм-аутом 5 минут.
     */
    public String getValidVCodeForAction(String actionName) {
        return getValidVCodeForAction(actionName, 300_000L);  // 5 минут
    }

    /**
     * Обработать ошибку "Неверный код защиты" от сервера.
     * Инвалидирует текущий контекст, чтобы следующий вызов getValidVCodeForAction вернул null.
     * 
     * @param failingVCode VCode который был невалиден
     * @param actionName Название действия (для логирования)
     */
    public void onInvalidProtectionCodeError(String failingVCode, String actionName) {
        String msg = "INVALID_CODE_ERROR: actionName=" + actionName 
            + ", failingVCode=" + failingVCode.substring(0, 8) + "...";
        AppLog.e("SessionManager", TAG, "❌ " + msg);
        
        contextLock.writeLock().lock();
        try {
            // Инвалидировать контекст - он больше не может быть использован
            if (currentContext != null &&failingVCode.equals(currentContext.getParsedVCode())) {
                currentContext = null;
                String invalidMsg = "SESSION_INVALIDATED: контекст очищен для переперезагрузки";
                AppLog.d("SessionManager", TAG, "📋 " + invalidMsg);
            }
        } finally {
            contextLock.writeLock().unlock();
        }
    }

    /**
     * Получить текущий контекст (для отладки).
     */
    public SessionContext getCurrentContext() {
        contextLock.readLock().lock();
        try {
            return currentContext;
        } finally {
            contextLock.readLock().unlock();
        }
    }

    /**
     * Инвалидировать контекст (очистить).
     */
    public void invalidateContext(String reason) {
        String msg = "CONTEXT_INVALIDATED: reason=" + reason;
        AppLog.w("SessionManager", TAG, "📋 " + msg);
        contextLock.writeLock().lock();
        try {
            currentContext = null;
        } finally {
            contextLock.writeLock().unlock();
        }
    }

    /**
     * 🎯 Отметить что начался бой - SessionManager должен дольше держать контекст.
     * Вызывается когда обнаружен новый бой (NEW FIGHT detected).
     */
    public void markFightInProgress() {
        fightInProgress = true;
        fightStartTimeMs = System.currentTimeMillis();
        // 🎯 Сохранить текущий vcode для использования во время всего боя
        contextLock.readLock().lock();
        try {
            if (currentContext != null && !currentContext.getParsedVCode().isEmpty()) {
                fightStartVCode = currentContext.getParsedVCode();
                String msg = "FIGHT_STARTED: cached vcode=" + fightStartVCode.substring(0, 8) + "..."
                    + ", will keep for " + (FIGHT_CONTEXT_TIMEOUT / 1000) + " secs";
                AppLog.d("SessionManager", TAG, "🎯 " + msg);
            }
        } finally {
            contextLock.readLock().unlock();
        }
    }

    /**
     * 🎯 Отметить что бой закончился - SessionManager может очистить боевой контекст.
     * Вызывается когда battle завершен.
     */
    public void clearFightContext() {
        fightInProgress = false;
        fightStartTimeMs = 0L;
        fightStartVCode = null;  // 🎯 Очистить кэшированный vcode
        String msg = "FIGHT_ENDED: боевой контекст и кэш vcode очищены, обычный 5-минутный timeout вернулся в силу";
        AppLog.d("SessionManager", TAG, "🎯 " + msg);
    }

    /**
     * Получить текущее боевое состояние (для отладки).
     */
    public boolean isFightInProgress() {
        return fightInProgress;
    }

    /**
     * Проверить, есть ли валидный контекст.
     */
    public boolean hasValidContext(long maxAgeMs) {
        return getValidVCodeForAction("healthCheck", maxAgeMs) != null;
    }

    /**
     * Получить статус сессии для логирования.
     */
    public String getStatusForLogging() {
        contextLock.readLock().lock();
        try {
            if (currentContext == null) {
                return "NO_SESSION";
            }
            return currentContext.toString();
        } finally {
            contextLock.readLock().unlock();
        }
    }

    /**
     * 🎯 КРИТИЧНЫЙ МЕТОДУ: Явно кэшить свежий VCode из fight_pm[4].
     * 
     * Вызывается из LezFight.Parse() сразу после извлечения VCode из fight_pm[4]
     * чтобы гарантировать что SessionManager использует СВЕЖИЙ VCode для боя,
     * а не полагается на асинхронный парсинг из fight.Frame.
     * 
     * При отправке удара через JavaScript (спустя 1-2 сек), VCode может устаpеть.
     * Этот методу гарантирует что у SessionManager есть кэшированный VCode
     * а не стандартный SessionContext, который может быть стаже.
     * 
     * @param vcode VCode из fight_pm[4]
     * @param source Источник (обычно "fight" для боевого контекста)
     */
    public void cacheFightVCode(String vcode, String source) {
        if (vcode == null || vcode.isEmpty()) {
            AppLog.w(TAG, "⚠️ FIGHT_CACHE_SKIP: empty vcode");
            return;
        }
        
        contextLock.writeLock().lock();
        try {
            // Создать SessionContext с явно кэшированным VCode
            long newVersion = (currentContext != null ? currentContext.getContextVersion() : 0L) + 1;
            
            SessionContext fightContext = new SessionContext(
                    "",  // Минимальный HTML (не нужен для кэша)
                    vcode,
                    System.currentTimeMillis(),
                    source != null ? source : "fight",
                    newVersion
            );
            
            currentContext = fightContext;
            fightInProgress = true;
            fightStartTimeMs = System.currentTimeMillis();
            fightStartVCode = vcode;  // Сохранить для fallback во время боя
            
            String msg = "FIGHT_CACHE: cached vcode from fight_pm[4]"
                + ", vcode=" + vcode.substring(0, 8) + "..."
                + ", source=" + source
                + ", version=" + newVersion;
            AppLog.d("SessionManager", TAG, "🎯 " + msg);
        } finally {
            contextLock.writeLock().unlock();
        }
    }

    /**
     * Извлечь VCode из HTML контента.
     * Пытается несколько паттернов для полноты.
     */
    private String extractVCode(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        for (Pattern pattern : VCODE_PATTERNS) {
            try {
                Matcher matcher = pattern.matcher(html);
                if (matcher.find()) {
                    String vcode = matcher.group(1);
                    if (!vcode.isEmpty() && vcode.length() == 32) {
                        String msg = "VCODE_EXTRACTED: pattern matched, vcode=" + vcode.substring(0, 8) + "...";
                        AppLog.d("SessionManager", TAG, "📍 " + msg);
                        return vcode;
                    }
                }
            } catch (Exception e) {
                String msg = "Pattern matching error (non-critical): " + e.getMessage();
                AppLog.w("SessionManager", TAG, msg);
            }
        }

        return "";
    }
}
