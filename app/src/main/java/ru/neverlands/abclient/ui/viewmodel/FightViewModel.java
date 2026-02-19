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

    private final MutableLiveData<Boolean> _isAutoBattleActive = new MutableLiveData<>(false);
    public LiveData<Boolean> isAutoBattleActive = _isAutoBattleActive;

    private final MutableLiveData<String> _submitAction = new MutableLiveData<>(null);
    public LiveData<String> submitAction = _submitAction;

    public LiveData<Boolean> getIsAutoBattleActive() {
        return _isAutoBattleActive;
    }

    public LiveData<String> getSubmitAction() {
        return _submitAction;
    }

    public void processFightHtml(final String html) {
        if (html == null) return;
        
        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) return;

            if (Boolean.TRUE.equals(_isAutoBattleActive.getValue())) {
                if (fight.IsBoi && !fight.IsWaitingForNextTurn) {
                    if (fight.Result != null) {
                        _submitAction.postValue(fight.Result);
                    }
                }
            }
        }).start();
    }

    public void toggleAutoBattle() {
        boolean currentState = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        _isAutoBattleActive.setValue(!currentState);
    }

    public void autoSelect(final String html) {
        if (html == null) return;
        
        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (fight.IsValid && fight.Result != null) {
                _submitAction.postValue(fight.Result);
            }
        }).start();
    }

    public void onActionSubmitted() {
        _submitAction.setValue(null);
    }
}
