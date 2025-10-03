package ru.neverlands.abclient.model;

import java.net.HttpCookie;
import java.util.List;

public class AuthResult {
    private final boolean isSuccess;
    private final boolean isCaptchaRequired;
    private final String captchaUrl;
    private final String vcode;
    private final String errorMessage;
    private final List<HttpCookie> cookies;

    // Конструктор для успешной авторизации
    public AuthResult(List<HttpCookie> cookies) {
        this.isSuccess = true;
        this.isCaptchaRequired = false;
        this.captchaUrl = null;
        this.vcode = null;
        this.errorMessage = null;
        this.cookies = cookies;
    }

    // Конструктор для случая, когда требуется капча
    public AuthResult(String captchaUrl, String vcode) {
        this.isSuccess = false;
        this.isCaptchaRequired = true;
        this.captchaUrl = captchaUrl;
        this.vcode = vcode;
        this.errorMessage = null;
        this.cookies = null;
    }

    // Конструктор для ошибки
    public AuthResult(String errorMessage) {
        this.isSuccess = false;
        this.isCaptchaRequired = false;
        this.captchaUrl = null;
        this.vcode = null;
        this.errorMessage = errorMessage;
        this.cookies = null;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public boolean isCaptchaRequired() {
        return isCaptchaRequired;
    }

    public String getCaptchaUrl() {
        return captchaUrl;
    }

    public String getVcode() {
        return vcode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<HttpCookie> getCookies() {
        return cookies;
    }
}
