package ru.neverlands.abclient.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.util.Log;

import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.AppVars;

/**
 * ViewModel для управления боем.
 */
public class FightViewModel extends ViewModel {
    private static final String TAG = "FightViewModel";
    private static final String BG_TRACE_PREFIX = "[BG_TRACE]";

    // Флаг авто-боя для UI: true = авто-удары активны.
    private final MutableLiveData<Boolean> _isAutoBattleActive = new MutableLiveData<>(false);
    public LiveData<Boolean> isAutoBattleActive = _isAutoBattleActive;

    // Команда для отправки действия в WebView (HTML form/post), одноразовое событие.
    private final MutableLiveData<String> _submitAction = new MutableLiveData<>(null);
    public LiveData<String> submitAction = _submitAction;

    // Экспорт LiveData для наблюдения в UI.
    public LiveData<Boolean> getIsAutoBattleActive() {
        return _isAutoBattleActive;
    }

    // Экспорт события отправки (UI подписывается и шлёт действие в бой).
    public LiveData<String> getSubmitAction() {
        return _submitAction;
    }

    // Обрабатывает HTML боя и, если авто‑бой включён, формирует действие для отправки.
    // Парсит HTML боя и, при активном авто-бое, формирует действие на отправку.
    public void processFightHtml(final String html) {
        if (html == null) return;
        if (!containsFightMarkers(html)) {
            Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: skip, no fight markers");
            return;
        }
        // Фикс "залипания после 1-го хода":
        // как только получили HTML с боевыми маркерами, обновляем отдельный pulse для фонового сервиса.
        // Это не заменяет LastBoiTimer (UI-таймер кнопки хода), а дополняет его для isFightSessionLikelyActive(...).
        long fightPulseNow = System.currentTimeMillis();
        AppVars.LastFightPulseAtMs = fightPulseNow;
        AppVars.LastBoiTimer = new java.util.Date(fightPulseNow);

        boolean autoBattleUiEnabled = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        boolean captchaDialogVisible = AppVars.IsFightCaptchaDialogVisible;
        boolean autoBattleRuntimeEnabled = autoBattleUiEnabled
                || AppVars.Autoboi == AutoboiState.AutoboiOn
                || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
        if (captchaDialogVisible) {
            autoBattleRuntimeEnabled = false;
        }

        Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: htmlLen=" + html.length()
                + ", autoBattleUiEnabled=" + autoBattleUiEnabled
                + ", autoBattleRuntimeEnabled=" + autoBattleRuntimeEnabled
                + ", captchaDialogVisible=" + captchaDialogVisible
                + ", appVarsAutoboi=" + AppVars.Autoboi);

        final boolean shouldAutoBattle = autoBattleRuntimeEnabled;

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) {
                Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: skip, parsed fight invalid");
                return;
            }

            announceNewFightIfNeeded(fight, html);

            if (!shouldAutoBattle) {
                return;
            }

            if (!fight.IsBoi) {
                Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: skip, IsBoi=false");
                return;
            }
            if (fight.IsWaitingForNextTurn) {
                Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: skip, waiting for next turn");
                return;
            }
            if (fight.Result == null) {
                Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: skip, fight result is null");
                return;
            }

            _submitAction.postValue(fight.Result);
            Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: submit posted, len=" + fight.Result.length());
        }).start();
    }

    /**
     * Выполнить один "автоход" (1 раз) независимо от состояния авто-боя.
     * Используется кнопкой "Автоход" из верхнего фрейма боя.
     */
    // Выполняет один авто‑ход (без включения постоянного авто‑боя).
    // Однократный авто-ход: не включает постоянный авто-бой.
    public void autoTurnOnce(final String html) {
        if (html == null) return;
        if (!containsFightMarkers(html)) {
            Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, no fight markers");
            return;
        }
        // Одиночный автоход тоже считается "живым" боевым пульсом:
        // нужен, чтобы foreground-service не терял бой на кратких переходах между кадрами.
        long fightPulseNow = System.currentTimeMillis();
        AppVars.LastFightPulseAtMs = fightPulseNow;
        AppVars.LastBoiTimer = new java.util.Date(fightPulseNow);
        if (AppVars.IsFightCaptchaDialogVisible) {
            Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, captcha dialog visible");
            return;
        }

        Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: htmlLen=" + html.length());

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) {
                Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, parsed fight invalid");
                return;
            }

            announceNewFightIfNeeded(fight, html);

            if (!fight.IsBoi) {
                Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, IsBoi=false");
                return;
            }
            if (fight.IsWaitingForNextTurn) {
                Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, waiting for next turn");
                return;
            }
            if (fight.Result == null) {
                Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, fight result is null");
                return;
            }

            _submitAction.postValue(fight.Result);
            Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: submit posted, len=" + fight.Result.length());
        }).start();
    }

    // Переключение состояния авто‑боя.
    // Переключает флаг авто-боя для UI.
    public void toggleAutoBattle() {
        boolean currentState = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        _isAutoBattleActive.setValue(!currentState);
        Log.d(TAG, BG_TRACE_PREFIX + " toggleAutoBattle: " + currentState + " -> " + !currentState);
    }

    // Автовыбор: вычисляет комбинацию и отправляет результат.
    // Автовыбор: рассчитывает комбинации и сразу отдаёт действие на отправку.
    public void autoSelect(final String html) {
        if (html == null) return;
        Log.d(TAG, BG_TRACE_PREFIX + " autoSelect: htmlLen=" + html.length());

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (fight.IsValid && fight.Result != null) {
                // При авто-выборе результат отправляется сразу.
                _submitAction.postValue(fight.Result);
                Log.d(TAG, BG_TRACE_PREFIX + " autoSelect: submit posted, len=" + fight.Result.length());
            }
        }).start();
    }

    // Сброс события отправки после выполнения.
    // Сбрасываем событие после того, как UI отправил действие.
    public void onActionSubmitted() {
        _submitAction.setValue(null);
        Log.d(TAG, BG_TRACE_PREFIX + " onActionSubmitted: submit reset");
    }

    private boolean containsFightMarkers(String html) {
        return html.contains("var fight_ty") || html.contains("magic_slots();");
    }

    /**
     * Резервный анонс старта нового боя для JS-bridge пути.
     *
     * Почему нужен:
     * - в части сценариев `mainPhpFight: NEW FIGHT detected` не срабатывает,
     *   потому что кадр проходит через `WebAppInterface.processFightHtml(...)` и дальше FightViewModel;
     * - из-за этого не приходило локальное сообщение "Нападение" в чат.
     *
     * Зависимости:
     * - `AppVars.LastBoiLog`: дедупликация по log-id боя;
     * - `LezFight.updateLastBoiFromLogs()`: наполняет состав противников для текста уведомления;
     * - `MainPhp.notifyNewFightFromExternalSource(...)`: публикация локального анонса + UnderAttack announce.
     */
    private void announceNewFightIfNeeded(LezFight fight, String html) {
        if (fight == null || !fight.IsBoi || fight.LogBoi == null || fight.LogBoi.isEmpty()) {
            return;
        }

        synchronized (FightViewModel.class) {
            if (fight.LogBoi.equals(AppVars.LastBoiLog)) {
                return;
            }
            String prevLog = AppVars.LastBoiLog;
            AppVars.LastBoiLog = fight.LogBoi;
            AppVars.AutoboiReadyCompletedLog = "";
            Log.d(TAG, BG_TRACE_PREFIX + " announceNewFightIfNeeded: LogBoi changed "
                    + prevLog + " -> " + fight.LogBoi);
        }

        try {
            fight.updateLastBoiFromLogs();
        } catch (Exception ignored) {
        }

        MainPhp.notifyNewFightFromExternalSource(fight, html);
    }
}
