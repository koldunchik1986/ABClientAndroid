package ru.neverlands.anclient.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.neverlands.anclient.utils.AppLog;
import ru.neverlands.anclient.MainActivity;
import ru.neverlands.anclient.lez.LezFight;
import ru.neverlands.anclient.model.AutoboiState;
import ru.neverlands.anclient.postfilter.FightAuto;
import ru.neverlands.anclient.postfilter.MainPhp;
import ru.neverlands.anclient.utils.AppVars;
import ru.neverlands.anclient.utils.FileLogger;
import ru.neverlands.anclient.utils.SessionManager;

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

    /**
     * Пул для фонового разбора боевого HTML (D3).
     *
     * Раньше здесь создавались «сырые» {@code new Thread(...)} на каждый вызов
     * ({@code processFightHtml}, {@code autoTurnOnce}, {@code autoSelect}), при этом у ViewModel
     * не было {@code onCleared()} — потоки не отменялись и не учитывались при уничтожении экрана.
     *
     * Выбран {@code newCachedThreadPool}, а не однопоточный executor, чтобы **сохранить прежнюю
     * параллельность** и не изменить тайминги боевой цепочки (AGENTS: не деградировать автобой).
     * Пул переиспользует простаивающие потоки и корректно останавливается в {@link #onCleared()}.
     */
    private final ExecutorService fightExecutor = Executors.newCachedThreadPool();

    /** true после {@link #onCleared()} — фоновые задачи не должны публиковать результат. */
    private volatile boolean cleared = false;

    /**
     * Окно анти-дубля отправки хода.
     *
     * Почему именно окно, а не жёсткая блокировка:
     * - в норме следующий раунд приходит с новым `vcode`, поэтому guard его не касается;
     * - но если предыдущая отправка была потеряна (оборванный сокет / отброшенный ответ),
     *   ход нужно повторить, иначе бой встанет до истечения таймера раунда.
     * Значение выбрано по замеру реального боя: средняя длительность раунда ~2.8 c.
     */
    private static final long SAME_ROUND_RESUBMIT_GUARD_MS = 3_000L;

    /** vcode раунда, для которого действие уже отправлено (ключ анти-дубля). */
    private volatile String lastSubmittedTurnVCode = "";

    /** Момент последней отправки хода (монотонные часы). */
    private volatile long lastSubmittedTurnAtMs = 0L;

    /**
     * Освобождение ресурсов ViewModel: останавливаем фоновый разбор боя.
     * Без этого фоновые задачи продолжали работать после уничтожения владельца.
     */
    @Override
    protected void onCleared() {
        cleared = true;
        fightExecutor.shutdownNow();
        AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " onCleared: fight executor shutdown requested");
        super.onCleared();
    }

    /**
     * Анти-дубль отправки хода: один раунд — одна отправка.
     *
     * Зачем нужно:
     * - автоход запускается по секундному таймеру и работает по кэшу `AppVars.ContentMainPhp`,
     *   поэтому один и тот же раунд отправлялся по нескольку раз (замер: ~2.6 отправки на раунд);
     * - лишние отправки обрывали предыдущий незавершённый ответ (Broken pipe в прокси),
     *   давали красный `PROXY_FAIL` в статус-строке и забивали очередь запросов (waitMs до 3.6 c).
     *
     * Ключ дедупа — `vcode` из `fight_pm[4]`: сервер выдаёт его заново на каждый раунд.
     * Сам `Result` для этой цели не годится: `inu`/`inb`/`ina` пересчитываются при каждой
     * генерации комбинации, поэтому строка отличается даже внутри одного раунда.
     *
     * @param fight  разобранное состояние боя.
     * @param source имя вызывающей цепочки (для трассировки).
     * @return true — отправку нужно пропустить как дубль.
     */
    private boolean shouldSkipDuplicateTurn(LezFight fight, String source) {
        String roundVCode = fight.getVCode();
        if (roundVCode.isEmpty()) {
            // Без vcode дедуп невозможен — поведение остаётся прежним, чтобы не потерять ход.
            return false;
        }
        long nowMs = android.os.SystemClock.elapsedRealtime();
        String shortVCode = roundVCode.length() > 8 ? roundVCode.substring(0, 8) : roundVCode;

        if (roundVCode.equals(lastSubmittedTurnVCode)) {
            long ageMs = nowMs - lastSubmittedTurnAtMs;
            if (ageMs < SAME_ROUND_RESUBMIT_GUARD_MS) {
                AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " " + source
                        + ": skip duplicate turn, same round vcode=" + shortVCode
                        + ", ageMs=" + ageMs + ", guardMs=" + SAME_ROUND_RESUBMIT_GUARD_MS);
                return true;
            }
            AppLog.w(TAG, TAG, BG_TRACE_PREFIX + " " + source
                    + ": resubmit same round after guard window (предыдущая отправка могла потеряться)"
                    + ", vcode=" + shortVCode + ", ageMs=" + ageMs);
        }

        lastSubmittedTurnVCode = roundVCode;
        lastSubmittedTurnAtMs = nowMs;
        return false;
    }

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
        boolean captchaDialogVisible = isBlockingFightCaptchaVisible();
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
        } catch (Exception e) {
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " uiForeground flags check failed: " + e.getClass().getSimpleName());
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
        SessionManager.getInstance().markFightInProgress();
        LezFight syncFight = new LezFight(html);
        if (syncFight.IsValid && syncFight.IsBoi) {
            long fightPulseNow = System.currentTimeMillis();
            AppVars.LastFightPulseAtMs = fightPulseNow;
            AppVars.LastBoiTimer = new java.util.Date(fightPulseNow);
            String pulseMsg = BG_TRACE_PREFIX + " processFightHtml: EARLY PULSE UPDATE (sync), lastPulse="
                    + fightPulseNow + ", IsBoi=" + syncFight.IsBoi;
            AppLog.d(TAG, TAG, pulseMsg);
        }

        submitFightTask(() -> {
            // Порядок вызовов критичен (AGENTS п.9): markFightInProgress() ПЕРЕД new LezFight(html).
            SessionManager.getInstance().markFightInProgress();
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
                showPendingFightCaptchaFromParsedStateIfNeeded(fight, html);
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

            if (shouldSkipDuplicateTurn(fight, "processFightHtml")) {
                return;
            }

            _submitAction.postValue(fight.Result);
            String msg2 = BG_TRACE_PREFIX + " processFightHtml: submit posted, len=" + fight.Result.length();
            AppLog.d(TAG, TAG, msg2);
        });
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
        if (isBlockingFightCaptchaVisible()) {
            String msg = BG_TRACE_PREFIX + " autoTurnOnce: skip, fight captcha dialog visible";
            AppLog.d(TAG, TAG, msg);
            return;
        }

        String msg3 = BG_TRACE_PREFIX + " autoTurnOnce: htmlLen=" + html.length();
        AppLog.d(TAG, TAG, msg3);

        submitFightTask(() -> {
            // Порядок вызовов критичен (AGENTS п.9): markFightInProgress() ПЕРЕД new LezFight(html).
            SessionManager.getInstance().markFightInProgress();
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
                showPendingFightCaptchaFromParsedStateIfNeeded(fight, html);
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

            if (shouldSkipDuplicateTurn(fight, "autoTurnOnce")) {
                return;
            }

            _submitAction.postValue(fight.Result);
            String msg4 = BG_TRACE_PREFIX + " autoTurnOnce: submit posted, len=" + fight.Result.length();
            AppLog.d(TAG, TAG, msg4);
        });
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

        submitFightTask(() -> {
            // Порядок вызовов критичен (AGENTS п.9): markFightInProgress() ПЕРЕД new LezFight(html).
            SessionManager.getInstance().markFightInProgress();
            LezFight fight = new LezFight(html);
            if (fight.IsValid && fight.Result != null) {
                // При авто-выборе результат отправляется сразу.
                _submitAction.postValue(fight.Result);
                String msg2 = BG_TRACE_PREFIX + " autoSelect: submit posted, len=" + fight.Result.length();
                AppLog.d(TAG, TAG, msg2);
            }
        });
    }

    /**
     * Отправляет задачу разбора боя в управляемый пул (D3).
     *
     * Зачем нужен отдельный метод:
     * - единая точка постановки фоновых боевых задач вместо трёх «сырых» {@code new Thread(...)};
     * - защита от выполнения после {@link #onCleared()} (ViewModel уже уничтожен);
     * - устойчивость к {@link java.util.concurrent.RejectedExecutionException} после shutdown.
     */
    private void submitFightTask(Runnable task) {
        if (cleared) {
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " submitFightTask: skip, view model cleared");
            return;
        }
        try {
            fightExecutor.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Пул уже остановлен (onCleared произошёл между проверкой и постановкой задачи).
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " submitFightTask: rejected, executor is shut down");
        }
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

    private void showPendingFightCaptchaFromParsedStateIfNeeded(LezFight fight, String html) {
        String finishUrl = AppVars.FightLink == null ? "" : AppVars.FightLink.trim();
        String captchaUrl = AppVars.CodeAddress == null ? "" : AppVars.CodeAddress.trim();
        if (finishUrl.isEmpty()
                || captchaUrl.isEmpty()
                || !finishUrl.contains("get_id=61")
                || !finishUrl.contains("act=7")
                || !finishUrl.contains("code=????")) {
            return;
        }
        AppVars.ResumeAutoboiAfterCaptcha = true;
        AppVars.Autoboi = AutoboiState.AutoboiOff;
        AppVars.ContentMainPhp = html;
        String msg = BG_TRACE_PREFIX + " processFightHtml: show pending fight captcha from parsed state"
                + ", finishUrl=" + finishUrl
                + ", captchaUrl=" + captchaUrl
                + ", LogBoi=" + (fight == null ? "" : fight.LogBoi);
        AppLog.d(TAG, TAG, msg);
        FightAuto.showFightCaptchaDialogOnce(captchaUrl, finishUrl, fight == null ? "" : fight.LogBoi);
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
        } catch (Exception e) {
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " updateLastBoiFromLogs failed: " + e.getClass().getSimpleName());
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
            boolean captchaVisible = isBlockingFightCaptchaVisible();
            ru.neverlands.anclient.utils.FightAnnounceHandler.onFightAnnounced(
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

    /**
     * Проверяет только captcha, которая действительно блокирует автобой.
     *
     * Зависимости:
     * - глобальный `AppVars.IsFightCaptchaDialogVisible` выставляется общим captcha popup в MainActivity;
     * - popup Авто-Травника (`alchemy_ajax.php?act=3`) использует тот же dialog-контур для manual fallback,
     *   но не должен стопорить event-driven `requestImmediateAutoTurnOnFightAnnounce()`;
     * - если MainActivity недоступна или проверка упала, fail-safe оставляет старое поведение: captcha считается blocking.
     */
    private boolean isBlockingFightCaptchaVisible() {
        if (!AppVars.IsFightCaptchaDialogVisible) {
            return false;
        }
        try {
            MainActivity activity = AppVars.mainActivity != null ? AppVars.mainActivity.get() : null;
            if (activity != null && activity.isActiveAlchemyCaptchaDialog()) {
                return false;
            }
        } catch (Exception e) {
            AppLog.d(TAG, TAG, BG_TRACE_PREFIX + " isActiveAlchemyCaptchaDialog check failed: " + e.getClass().getSimpleName());
        }
        return true;
    }
}
