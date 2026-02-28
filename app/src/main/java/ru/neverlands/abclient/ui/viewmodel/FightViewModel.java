package ru.neverlands.abclient.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.utils.AppVars;

/**
 * ViewModel для управления боем.
 */
public class FightViewModel extends ViewModel {

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
        
        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) return;

            if (Boolean.TRUE.equals(_isAutoBattleActive.getValue())) {
                if (fight.IsBoi && !fight.IsWaitingForNextTurn) {
                    if (fight.Result != null) {
                        // Result содержит HTML-форму (или ссылку) для отправки действия боя.
                        _submitAction.postValue(fight.Result);
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

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) return;

            if (fight.IsBoi && !fight.IsWaitingForNextTurn) {
                if (fight.Result != null) {
                    // Одноразовое действие боя.
                    _submitAction.postValue(fight.Result);
                }
            }
        }).start();
    }

    // Переключение состояния авто‑боя.
    // Переключает флаг авто-боя для UI.
    public void toggleAutoBattle() {
        boolean currentState = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        _isAutoBattleActive.setValue(!currentState);
    }

    // Автовыбор: вычисляет комбинацию и отправляет результат.
    // Автовыбор: рассчитывает комбинации и сразу отдаёт действие на отправку.
    public void autoSelect(final String html) {
        if (html == null) return;
        
        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (fight.IsValid && fight.Result != null) {
                // При авто-выборе результат отправляется сразу.
                _submitAction.postValue(fight.Result);
            }
        }).start();
    }

    // Сброс события отправки после выполнения.
    // Сбрасываем событие после того, как UI отправил действие.
    public void onActionSubmitted() {
        _submitAction.setValue(null);
    }
}
