package ru.neverlands.abclient.utils;

import java.util.Random;

public class AutoAnswerMachine {
    private static final String[] DEFAULT_ANSWERS = new String[]{
            "привет",
            "ок",
            "сейчас отвечу",
            "одну минуту",
            "да",
            "нет",
            "понял"
    };
    private static final Random RANDOM = new Random();

    public static String getNextAnswer() {
        if (DEFAULT_ANSWERS.length == 0) return "";
        return DEFAULT_ANSWERS[RANDOM.nextInt(DEFAULT_ANSWERS.length)];
    }
}
