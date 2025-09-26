# План портирования EventSounds.cs

Файл `EventSounds.cs` — это статический класс-менеджер для воспроизведения коротких звуковых эффектов в приложении.

## Функциональность в C#

*   **Назначение**: Предоставить простой API для проигрывания звуков, соответствующих игровым событиям (тревога, атака, новое сообщение и т.д.).
*   **API**: Содержит набор публичных статических методов: `PlayAlarm()`, `PlayAttack()`, `PlaySndMsg()` и т.д.
*   **Воспроизведение**: Использует один экземпляр `System.Media.SoundPlayer` для асинхронного проигрывания `.wav` файлов.
*   **Управление**: Перед воспроизведением каждый метод проверяет две вещи:
    1.  Включен ли звук для данного конкретного события в настройках профиля (`AppVars.Profile.Sound.DoPlay...`).
    2.  Не проигрывался ли этот же звук в последние 5 секунд (простая защита от флуда).

## План портирования на Android

Логику этого класса необходимо портировать, но с использованием `android.media.SoundPool`, который лучше подходит для коротких звуковых эффектов, чем `MediaPlayer`.

1.  **Скопировать ресурсы**: Все `.wav` файлы из папки `MySounds` необходимо поместить в папку `res/raw` Android-проекта.

2.  **Создать `SoundManager.java`**: Создать класс-синглтон, который будет управлять всеми звуками.

3.  **Реализовать `SoundManager.java`**:
    ```java
    import android.content.Context;
    import android.media.AudioAttributes;
    import android.media.SoundPool;
    import java.util.HashMap;
    import java.util.Map;

    public class SoundManager {
        private static final SoundManager instance = new SoundManager();
        private SoundManager() {}
        public static SoundManager getInstance() { return instance; }

        public enum SoundEvent {
            ALARM, ATTACK, SND_MSG, REFRESH, TIMER, BEAR, DIGITS
        }

        private SoundPool soundPool;
        private boolean isLoaded = false;
        private Map<SoundEvent, Integer> soundMap = new HashMap<>();
        private Map<SoundEvent, Long> lastPlayedMap = new HashMap<>();

        public void loadSounds(Context context) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attributes).build();

            soundMap.put(SoundEvent.ALARM, soundPool.load(context, R.raw.alarm, 1));
            soundMap.put(SoundEvent.ATTACK, soundPool.load(context, R.raw.attack, 1));
            // ... загрузить все остальные звуки ...

            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                // Можно отслеживать, когда все звуки загружены
                isLoaded = true;
            });
        }

        public void play(SoundEvent event) {
            // TODO: Проверить глобальный выключатель звука из SharedPreferences
            if (!isSoundEnabled()) return;

            // TODO: Проверить выключатель для конкретного события из SharedPreferences
            if (!isEventEnabled(event)) return;

            // Проверка на флуд
            long currentTime = System.currentTimeMillis();
            Long lastPlayed = lastPlayedMap.get(event);
            if (lastPlayed != null && (currentTime - lastPlayed) < 5000) {
                return;
            }

            Integer soundId = soundMap.get(event);
            if (soundId != null && isLoaded) {
                soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
                lastPlayedMap.put(event, currentTime);
            }
        }

        public void release() {
            if (soundPool != null) {
                soundPool.release();
                soundPool = null;
            }
        }
    }
    ```

4.  **Интеграция**: В `Application.onCreate()` или в `MainActivity.onCreate()` нужно будет вызвать `SoundManager.getInstance().loadSounds(this)`. В коде, где раньше вызывался `EventSounds.PlayAlarm()`, теперь будет вызываться `SoundManager.getInstance().play(SoundManager.SoundEvent.ALARM)`.
