package ru.neverlands.abclient.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ru.neverlands.abclient.utils.AppLog;
import ru.neverlands.abclient.MainActivity;
import ru.neverlands.abclient.lez.LezFight;
import ru.neverlands.abclient.model.AutoboiState;
import ru.neverlands.abclient.postfilter.MainPhp;
import ru.neverlands.abclient.utils.AppVars;
import ru.neverlands.abclient.utils.FileLogger;

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

    // КРИТИЧНЫЙ ФИХ для "Авто-Бой не бьёт при перелогине/пересоздании активити":
    // При пересоздании ViewModel (например, при relocation/relogin), восстанавливаем состояние из AppVars.
    public FightViewModel() {
        super();
        // Синхронизируем UI-состояние с runtime-состоянием при создании.
        boolean runtimeAutoboi = AppVars.Autoboi == AutoboiState.AutoboiOn 
                || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
        if (runtimeAutoboi) {
            _isAutoBattleActive.setValue(true);
            String msg = BG_TRACE_PREFIX + " FightViewModel constructor: restored UI state from AppVars.Autoboi=" + AppVars.Autoboi;
            AppLog.d(TAG, TAG, msg);
        }
    }

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
            String msg = BG_TRACE_PREFIX + " processFightHtml: skip, no fight markers";
            AppLog.d(TAG, TAG, msg);
            return;
        }
        // Фикс "залипания после 1-го хода":
        // как только получили HTML с боевыми маркерами, обновляем отдельный pulse для фонового сервиса.
        // Это не заменяет LastBoiTimer (UI-таймер кнопки хода), а дополняет его для isFightSessionLikelyActive(...).
        boolean autoBattleUiEnabled = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        boolean captchaDialogVisible = AppVars.IsFightCaptchaDialogVisible;
        boolean autoBattleRuntimeEnabled = autoBattleUiEnabled
                || AppVars.Autoboi == AutoboiState.AutoboiOn
                || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);
        if (captchaDialogVisible) {
            autoBattleRuntimeEnabled = false;
        }
        boolean uiForegroundInteractive = false;
        boolean uiForegroundLikely = false;
        try {
            if (AppVars.mainActivity != null && AppVars.mainActivity.get() != null) {
                uiForegroundInteractive = AppVars.mainActivity.get().isUiForegroundInteractive();
                uiForegroundLikely = AppVars.mainActivity.get().isUiForegroundLikely();
            }
        } catch (Exception ignored) {
        }

        // FIXME: REMOVED неправильная логика блокировки автобоя в foreground:
        // Старая проверка `if (uiForegroundInteractive || uiForegroundLikely) { autoBattleRuntimeEnabled = false; }`
        // ПОЛНОСТЬЮ отключала автобой, если пользователь смотрит на экран.
        // Это вызывало баг: первый удар срабатывает, противник убивается, но следующий враг не получает удара.
        // 
        // Комментарий старой логики говорил "блокируем auto-submit в интерактивном foreground"
        // (от JS-bridge пути `extract_fight_state.js`), но реально блокировалась ВСЕ обработка боя.
        // 
        // Правильное решение: отключать нужно ТОЛЬКО JS-bridge путь (первый ход), но НЕ основной цикл.
        // Такое разделение требует рефакторинга архитектуры, поэтому пока убираем эту проверку полностью.
        // Побочный эффект: первый ход может быть отправлен раньше рендера фрейма (но это меньшая проблема).
        
        String msg1 = BG_TRACE_PREFIX + " processFightHtml: htmlLen=" + html.length()
                + ", autoBattleUiEnabled=" + autoBattleUiEnabled
                + ", autoBattleRuntimeEnabled=" + autoBattleRuntimeEnabled
                + ", captchaDialogVisible=" + captchaDialogVisible
                + ", appVarsAutoboi=" + AppVars.Autoboi
                + ", uiForegroundInteractive=" + uiForegroundInteractive
                + ", uiForegroundLikely=" + uiForegroundLikely;
        AppLog.d(TAG, TAG, msg1);

        final boolean shouldAutoBattle = autoBattleRuntimeEnabled;

        // 🔥 CRITICAL FIX для холодного старта Авто-Боя:
        // ОБЯЗАТЕЛЬНО парсим и обновляем LastFightPulseAtMs СИНХРОННО перед background потоком.
        // Это гарантирует что AutoModeForegroundService увидит актуальный pulse и не заблокирует autoTurn.
        // Иначе возникает race condition: service проверяет isFightSessionLikelyActive() до того как
        // background поток сумеет обновить LastFightPulseAtMs, и первый ход блокируется.
        LezFight syncFight = new LezFight(html);
        if (syncFight.IsValid && syncFight.IsBoi) {
            long fightPulseNow = System.currentTimeMillis();
            AppVars.LastFightPulseAtMs = fightPulseNow;
            AppVars.LastBoiTimer = new java.util.Date(fightPulseNow);
            String pulseMsg = BG_TRACE_PREFIX + " processFightHtml: EARLY PULSE UPDATE (sync), lastPulse="
                    + fightPulseNow + ", IsBoi=" + syncFight.IsBoi;
            AppLog.d(TAG, TAG, pulseMsg);
        }

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) {
                String msg = BG_TRACE_PREFIX + " processFightHtml: skip, parsed fight invalid";
                AppLog.d(TAG, TAG, msg);
                return;
            }
            updateFightPulseIfNeeded(fight);

            announceNewFightIfNeeded(fight, html);

            if (!shouldAutoBattle) {
                return;
            }

            if (!fight.IsBoi) {
                String msg = BG_TRACE_PREFIX + " processFightHtml: skip, IsBoi=false"
                        + ", IsWaitingForNextTurn=" + fight.IsWaitingForNextTurn
                        + ", FightLink=" + (AppVars.FightLink != null ? AppVars.FightLink : "null")
                        + ", Autoboi=" + AppVars.Autoboi
                        + ", LogBoi=" + fight.LogBoi;
                AppLog.d(TAG, TAG, msg);
                return;
            }
            // ⚠️ FIX для group=2+: Не блокировать обработку при IsWaitingForNextTurn в режиме autoboi
            // Позволить FightAuto.processFight() обработать auto-refresh для следующего врага
            if (fight.IsWaitingForNextTurn && AppVars.Autoboi != AutoboiState.AutoboiOn) {
                String msg = BG_TRACE_PREFIX + " processFightHtml: skip, waiting for next turn (not in autoboi)";
                AppLog.d(TAG, TAG, msg);
                return;
            }
            if (fight.Result == null) {
                String msg = BG_TRACE_PREFIX + " processFightHtml: skip, fight result is null";
                AppLog.d(TAG, TAG, msg);
                return;
            }

            _submitAction.postValue(fight.Result);
            String msg2 = BG_TRACE_PREFIX + " processFightHtml: submit posted, len=" + fight.Result.length();
            AppLog.d(TAG, TAG, msg2);
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
            String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, no fight markers";
            AppLog.d(TAG, TAG, msg);
            return;
        }
        // Одиночный автоход тоже считается "живым" боевым пульсом:
        // нужен, чтобы foreground-service не терял бой на кратких переходах между кадрами.
        if (AppVars.IsFightCaptchaDialogVisible) {
            String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, captcha dialog visible";
            AppLog.d(TAG, TAG, msg);
            return;
        }

        String msg3 = BG_TRACE_PREFIX + " autoTurnOnce: htmlLen=" + html.length();
        AppLog.d(TAG, TAG, msg3);

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (!fight.IsValid) {
                String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, parsed fight invalid";
                AppLog.d(TAG, TAG, msg);
                return;
            }
            updateFightPulseIfNeeded(fight);

            announceNewFightIfNeeded(fight, html);

            if (!fight.IsBoi) {
                String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, IsBoi=false"
                        + ", IsWaitingForNextTurn=" + fight.IsWaitingForNextTurn
                        + ", FightLink=" + (AppVars.FightLink != null ? AppVars.FightLink : "null")
                        + ", Autoboi=" + AppVars.Autoboi;
                AppLog.d(TAG, TAG, msg);
                return;
            }
            if (fight.IsWaitingForNextTurn) {
                String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, waiting for next turn";
                AppLog.d(TAG, TAG, msg);
                return;
            }
            if (fight.Result == null) {
                String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, fight result is null";
                AppLog.d(TAG, TAG, msg);
                return;
            }

            _submitAction.postValue(fight.Result);
            String msg4 = BG_TRACE_PREFIX + " autoTurnOnce: submit posted, len=" + fight.Result.length();
            AppLog.d(TAG, TAG, msg4);
        }).start();
    }

    // Переключение состояния авто‑боя.
    // Переключает флаг авто-боя для UI.
    public void toggleAutoBattle() {
        boolean currentState = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        _isAutoBattleActive.setValue(!currentState);
        String msg = BG_TRACE_PREFIX + " toggleAutoBattle: " + currentState + " -> " + !currentState;
        AppLog.d(TAG, TAG, msg);
    }

    // КРИТИЧНЫЙ ФИХ для "Авто-Бой не бьёт при незапланированной атаке":
    // Синхронизирует UI-состояние (_isAutoBattleActive) с runtime-состоянием (AppVars.Autoboi).
    // Вызывается из AutoFunctionsManager.setAutoFightEnabled() после изменения AppVars.Autoboi.
    public void setAutoBattleActive(boolean active) {
        boolean currentState = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
        if (currentState != active) {
            _isAutoBattleActive.setValue(active);
            String msg = BG_TRACE_PREFIX + " setAutoBattleActive: " + currentState + " -> " + active + " (UI state sync)";
            AppLog.d(TAG, TAG, msg);
        }
    }

    // Автовыбор: вычисляет комбинацию и отправляет результат.
    // Автовыбор: рассчитывает комбинации и сразу отдаёт действие на отправку.
    public void autoSelect(final String html) {
        if (html == null) return;
        String msg = BG_TRACE_PREFIX + " autoSelect: htmlLen=" + html.length();
        AppLog.d(TAG, TAG, msg);

        new Thread(() -> {
            LezFight fight = new LezFight(html);
            if (fight.IsValid && fight.Result != null) {
                // При авто-выборе результат отправляется сразу.
                _submitAction.postValue(fight.Result);
                String msg2 = BG_TRACE_PREFIX + " autoSelect: submit posted, len=" + fight.Result.length();
                AppLog.d(TAG, TAG, msg2);
            }
        }).start();
    }

    // Сброс события отправки после выполнения.
    // Сбрасываем событие после того, как UI отправил действие.
    public void onActionSubmitted() {
        _submitAction.setValue(null);
        String msg = BG_TRACE_PREFIX + " onActionSubmitted: submit reset";
        AppLog.d(TAG, TAG, msg);
    }

    /**
     * Обновляет боевой pulse только при активном бою (`IsBoi=true`).
     *
     * Это защищает фоновые эвристики от stale `fight_ty`: завершённый бой больше не продлевает
     * `LastFightPulseAtMs`, из-за чего AutoModeForegroundService не застревает в ложном состоянии "бой активен".
     */
    private void updateFightPulseIfNeeded(LezFight fight) {
        if (fight == null || !fight.IsValid || !fight.IsBoi) {
            return;
        }
        long fightPulseNow = System.currentTimeMillis();
        AppVars.LastFightPulseAtMs = fightPulseNow;
        AppVars.LastBoiTimer = new java.util.Date(fightPulseNow);
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
            AppVars.LastBoiUron = "";
            AppVars.AutoboiReadyCompletedLog = "";
            String msg = BG_TRACE_PREFIX + " announceNewFightIfNeeded: LogBoi changed "
                    + prevLog + " -> " + fight.LogBoi;
            AppLog.d(TAG, TAG, msg);
        }

        try {
            fight.updateLastBoiFromLogs();
        } catch (Exception ignored) {
        }

        MainPhp.notifyNewFightFromExternalSource(fight, html);

        // 🆕 EVENT-DRIVEN: Немедленный запрос хода при анонсе нового боя
        // Это предотвращает 24+ секундную задержку polling-цикла AutoModeForegroundService
        tryTriggerImmediateAutoTurnOnAnnounce();
    }

    /**
     * Попытка немедленно запросить ход при анонсе боя.
     * Делегирует проверки и логику модульному обработчику FightAnnounceHandler.
     */
    private void tryTriggerImmediateAutoTurnOnAnnounce() {
        String msg_entry = BG_TRACE_PREFIX + " tryTriggerImmediateAutoTurnOnAnnounce: ENTERED";
        AppLog.d(TAG, TAG, msg_entry);

        try {
            // Проверяем, активен ли автобой вообще
            boolean autoBattleUiEnabled = Boolean.TRUE.equals(_isAutoBattleActive.getValue());
            boolean autoBattleEnabledViaVm = AppVars.Autoboi == AutoboiState.AutoboiOn
                    || (AppVars.Profile != null && AppVars.Profile.LezDoAutoboi);

            String msg_state = BG_TRACE_PREFIX + " tryTriggerImmediateAutoTurnOnAnnounce: autoBattleUiEnabled="
                    + autoBattleUiEnabled + ", autoBattleEnabledViaVm=" + autoBattleEnabledViaVm;
            AppLog.d(TAG, TAG, msg_state);

            if (!autoBattleUiEnabled && !autoBattleEnabledViaVm) {
                String msg = BG_TRACE_PREFIX + " tryTriggerImmediateAutoTurnOnAnnounce: skip (autoboi disabled)";
                AppLog.d(TAG, TAG, msg);
                return;
            }

            // ✅ Использовать модульный FightAnnounceHandler для всех проверок и call-back'а
            boolean captchaVisible = AppVars.IsFightCaptchaDialogVisible;
            ru.neverlands.abclient.utils.FightAnnounceHandler.onFightAnnounced(
                    "auto-boi",  // fighterNickname
                    captchaVisible,
                    () -> {
                        // Callback выполняется если все проверки в handler'е пройдены
                        MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
                        if (activity != null) {
                            String msg = BG_TRACE_PREFIX + " FightAnnounceHandler approved -> calling requestImmediateAutoTurnOnFightAnnounce";
                            AppLog.d(TAG, TAG, msg);
                            activity.requestImmediateAutoTurnOnFightAnnounce();
                        } else {
                            String msg = BG_TRACE_PREFIX + " FightAnnounceHandler callback: MainActivity unavailable";
                            AppLog.d(TAG, TAG, msg);
                        }
                    }
            );
            
        } catch (Exception e) {
            String msg = BG_TRACE_PREFIX + " tryTriggerImmediateAutoTurnOnAnnounce failed: " + e.getMessage();
            AppLog.e(TAG, TAG, msg);
        }
    }
}
