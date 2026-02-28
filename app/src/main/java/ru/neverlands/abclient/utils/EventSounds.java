package ru.neverlands.abclient.utils;

import android.media.AudioManager;
import android.media.ToneGenerator;

public class EventSounds {
    private static ToneGenerator toneGenerator;

    public static void playSndMsg() {
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {
        }
    }
}
