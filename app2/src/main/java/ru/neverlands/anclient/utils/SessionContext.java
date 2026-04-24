package ru.neverlands.anclient.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Контекст текущей сессии с сервером.
 * 
 * Хранит:
 * - HTML контент текущей страницы
 * - Распарсенный VCode
 * - Метаданные сессии (временные метки, источник, версия)
 * 
 * Используется SessionManager для отслеживания валидности VCode.
 */
public class SessionContext {
    private static final long DEFAULT_MAX_AGE_MS = 300_000L;  // 5 минут

    private final String htmlContent;
    private final String parsedVCode;
    private final long timestamp;
    private final String source;  // "fight", "fish", "main", "pinfo", etc.
    private final long contextVersion;
    private final String phpsessid;
    private final Map<String, String> attributes;

    public SessionContext(
            String htmlContent,
            String parsedVCode,
            long timestamp,
            String source,
            long contextVersion) {
        this(htmlContent, parsedVCode, timestamp, source, contextVersion, "", new HashMap<>());
    }

    public SessionContext(
            String htmlContent,
            String parsedVCode,
            long timestamp,
            String source,
            long contextVersion,
            String phpsessid,
            Map<String, String> attributes) {
        this.htmlContent = htmlContent;
        this.parsedVCode = parsedVCode;
        this.timestamp = timestamp;
        this.source = source;
        this.contextVersion = contextVersion;
        this.phpsessid = phpsessid;
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    /**
     * Проверить, истёк ли этот контекст по времени.
     */
    public boolean isExpired(long maxAgeMs) {
        long ageMs = System.currentTimeMillis() - this.timestamp;
        return ageMs > maxAgeMs;
    }

    /**
     * Проверить, истёк ли этот контекст по времени (с дефолтным тайм-аутом 5 минут).
     */
    public boolean isExpired() {
        return isExpired(DEFAULT_MAX_AGE_MS);
    }

    /**
     * Проверить, принадлежат ли два контекста одной сессии.
     * Два контекста считаются одной сессией, если PHPSESSID одинаковый
     * и версии близки (разница ≤ 1).
     */
    public boolean isSameSessionAs(SessionContext other) {
        if (other == null) {
            return false;
        }
        
        // Если PHPSESSID присутствует - проверяем его
        if (!this.phpsessid.isEmpty() && !other.phpsessid.isEmpty()) {
            return this.phpsessid.equals(other.phpsessid);
        }
        
        // Иначе проверяем версии (должны быть близки)
        return Math.abs(this.contextVersion - other.contextVersion) <= 1;
    }

    /**
     * Получить возраст контекста в миллисекундах.
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - this.timestamp;
    }

    // Getters
    public String getHtmlContent() {
        return htmlContent;
    }

    public String getParsedVCode() {
        return parsedVCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public long getContextVersion() {
        return contextVersion;
    }

    public String getPhpsessid() {
        return phpsessid;
    }

    public Map<String, String> getAttributes() {
        return new HashMap<>(attributes);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public String toString() {
        return "SessionContext{" +
                "source='" + source + '\'' +
                ", vcode='" + (parsedVCode.isEmpty() ? "EMPTY" : parsedVCode.substring(0, Math.min(8, parsedVCode.length())) + "...") + '\'' +
                ", ageMs=" + getAgeMs() +
                ", version=" + contextVersion +
                ", htmlLen=" + htmlContent.length() +
                '}';
    }
}
