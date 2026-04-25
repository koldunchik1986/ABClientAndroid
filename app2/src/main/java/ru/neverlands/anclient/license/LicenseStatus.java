package ru.neverlands.anclient.license;

public final class LicenseStatus {
    private final boolean allowed;
    private final String title;
    private final String message;
    private final String requestPath;
    private final String licensePath;
    private final LicenseSession session;

    private LicenseStatus(boolean allowed,
                          String title,
                          String message,
                          String requestPath,
                          String licensePath,
                          LicenseSession session) {
        this.allowed = allowed;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.requestPath = requestPath == null ? "" : requestPath;
        this.licensePath = licensePath == null ? "" : licensePath;
        this.session = session;
    }

    public static LicenseStatus allowed(String licensePath) {
        return allowed(licensePath, null);
    }

    public static LicenseStatus allowed(String licensePath, LicenseSession session) {
        return new LicenseStatus(true, "Лицензия подтверждена", "", "", licensePath, session);
    }

    public static LicenseStatus blocked(String title,
                                         String message,
                                         String requestPath,
                                         String licensePath) {
        return new LicenseStatus(false, title, message, requestPath, licensePath, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getLicensePath() {
        return licensePath;
    }

    public LicenseSession getSession() {
        return session;
    }
}
