package ru.neverlands.abclient.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.util.Log;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
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
            if (!fight.IsValid) return;

            if (shouldAutoBattle) {
                if (fight.IsBoi && !fight.IsWaitingForNextTurn) {
                    if (fight.Result != null) {
                        // Result содержит HTML-форму (или ссылку) для отправки действия боя.
                        _submitAction.postValue(fight.Result);
                        Log.d(TAG, BG_TRACE_PREFIX + " processFightHtml: submit posted, len=" + fight.Result.length());
                    }
                }
            }
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
        if (AppVars.IsFightCaptchaDialogVisible) {
            Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: skip, captcha dialog visible");
            return;
        }
        Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: htmlLen=" + html.length());

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) return;

            if (fight.IsBoi && !fight.IsWaitingForNextTurn) {
                if (fight.Result != null) {
                    // Одноразовое действие боя.
                    _submitAction.postValue(fight.Result);
                    Log.d(TAG, BG_TRACE_PREFIX + " autoTurnOnce: submit posted, len=" + fight.Result.length());
                }
            }
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
}
