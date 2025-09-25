package com.neverlands.anlc.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.remote.AuthRepository
import com.neverlands.anlc.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

import com.neverlands.anlc.data.local.AppVars
import com.neverlands.anlc.data.local.AppTimerManager
import com.neverlands.anlc.data.local.model.AppTimer
import com.neverlands.anlc.data.local.model.AutoboiState
import android.util.Log
import java.util.Date // Import Date for AppVars.lastBoiTimer

import com.anlc.client.inventory.InvEntry
import com.anlc.client.inventory.ParsedDressed

/**
 * ViewModel для главного экрана игры.
 * Отвечает за логику поддержания сессии и обновления UI.
 */
class MainViewModel : BaseViewModel() {

    private val _currentTime = MutableLiveData<String>()
    val currentTime: LiveData<String> = _currentTime

    private val _reloadUrlEvent = MutableSharedFlow<String>()
    val reloadUrlEvent = _reloadUrlEvent.asSharedFlow()

    private val _inventory = MutableLiveData<List<InvEntry>>()
    val inventory: LiveData<List<InvEntry>> = _inventory

    private val _equippedItems = MutableLiveData<ParsedDressed>()
    val equippedItems: LiveData<ParsedDressed> = _equippedItems

    private var heartbeatJob: Job? = null

    /**
     * Запускает "пульс" сессии.
     */
    fun startHeartbeat() {
        ProfileManager.getCurrentProfile()?.let { profile ->
            // Start the session heartbeat from AuthRepository
            AuthRepository.startSessionHeartbeat { time ->
                _currentTime.postValue(time)
            }

            // Start periodic WebView reloads
            heartbeatJob?.cancel() // Cancel any existing job
            heartbeatJob = viewModelScope.launch {
                while (true) {
                    _reloadUrlEvent.emit("http://neverlands.ru/main.php")
                    delay(60000) // Reload every 60 seconds (adjust as needed)
                }
            }
        }
    }

    /**
     * Останавливает "пульс" сессии.
     */
    fun stopHeartbeat() {
        AuthRepository.stopSessionHeartbeat()
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Выполняет выход из системы.
     */
    fun logout() {
        AuthRepository.logout()
        ProfileManager.setCurrentProfile(null) // Очищаем текущий профиль
    }

    fun reloadWebView() {
        viewModelScope.launch {
            _reloadUrlEvent.emit("http://neverlands.ru/main.php")
        }
    }

    /**
     * Обрабатывает перегруз инвентаря при рыбалке.
     */
    fun fishOverload() {
        val profile = ProfileManager.getCurrentProfile()
        if (profile != null && profile.fishStopOverWeight) {
            profile.fishAuto = false
            ProfileManager.saveCurrentProfile()

            // Optionally, trigger UI update in MainActivity if needed
            // For example, if there's a button for auto-fish that needs to be unchecked
        }
    }

    fun updateTimers() {
        // TODO: Implement UI update logic to display timers
        val timers = AppTimerManager.getAppTimers()
        timers.forEach { timer ->
            Log.d("MainViewModel", "Timer: ${timer.description} - ${timer.triggerTime}")
        }
    }

    fun resetCure() {
        val profile = ProfileManager.getCurrentProfile()
        if (profile != null) {
            when (AppVars.autoboi) {
                AutoboiState.Guamod -> changeAutoboiState(AutoboiState.AutoboiOn)
                AutoboiState.Restoring -> {
                    profile.persReady = System.currentTimeMillis() * 10000L + 621355968000000000L // Convert milliseconds to Ticks
                    ProfileManager.saveCurrentProfile()
                }
                else -> {} // Do nothing for other states
            }
        }
    }

    fun traceDrinkPotion(wnickname: String, wnametxt: String) {
        val profile = ProfileManager.getCurrentProfile()
        if (profile == null || !wnickname.equals(profile.userNick, ignoreCase = true)) {
            return
        }

        val h = when (wnametxt) {
            "Зелье Метаболизма" -> 4
            "Зелье Сильной Спины" -> 20
            "Зелье Просветления" -> 5
            "Зелье Сокрушительных Ударов" -> 3
            "Зелье Стойкости" -> 3
            "Зелье Недосягаемости" -> 3
            "Зелье Точного Попадания" -> 3
            "Зелье Ловких Ударов" -> 2
            "Зелье Мужества" -> 2
            "Зелье Жизни" -> 3
            "Зелье Удачи" -> 3
            "Зелье Силы" -> 3
            "Зелье Ловкости" -> 3
            "Зелье Гения" -> 3
            "Зелье Боевой Славы" -> 2
            "Зелье Невидимости" -> 3
            "Зелье Секрет Волшебника" -> 2
            "Зелье Медитации" -> 4
            "Зелье Иммунитета" -> 10
            "Зелье Огненного Ореола" -> 3
            "Зелье Колкости" -> 2
            "Зелье Загрубелой Кожи" -> 3
            "Зелье Панциря" -> 2
            "Зелье Человек-гора" -> 2
            "Зелье Скорости" -> 1
            "Зелье Подвижности" -> 3
            "Зелье Соколиный Взор" -> 3
            "Секретное Зелье" -> 2
            else -> 0
        }

        if (h <= 0) {
            return
        }

        val triggerTime = Date(System.currentTimeMillis() + h * 3600 * 1000L) // Add h hours in milliseconds
        val appTimer = AppTimer(
            description = "Действует $wnametxt",
            triggerTime = triggerTime
        )
        AppTimerManager.addAppTimer(appTimer)
        updateTimers()
    }

    // --- Autoboi related methods ---

    fun autoBoi() {
        changeAutoboiState(AutoboiState.AutoboiOn)
        reloadWebView() // Simulate ReloadMainPhpInvoke
    }

    private fun changeAutoboiState(state: AutoboiState) {
        val previousState = AppVars.autoboi
        AppVars.autoboi = state
        var message = "" // C# had a message variable, but it was used to update a button text.
                         // For now, we'll just log the state change.

        when (state) {
            AutoboiState.AutoboiOn -> {
                message = "Отключение автобоя"
                ProfileManager.getCurrentProfile()?.let { profile ->
                    profile.lezDoAutoboi = true
                    ProfileManager.saveCurrentProfile()
                }
            }
            AutoboiState.AutoboiOff -> {
                message = "Включение автобоя"
                ProfileManager.getCurrentProfile()?.let { profile ->
                    profile.lezDoAutoboi = false
                    ProfileManager.saveCurrentProfile()
                }
            }
            AutoboiState.Restoring -> {
                // C# had logic involving AppVars.Profile.Pers.Ready and TimeSpan
                // TODO: Implement Restoring state logic
                message = "Останов лечения"
            }
            AutoboiState.Timeout -> {
                // C# had logic involving AppVars.Profile.Pers.Ready and TimeSpan
                // TODO: Implement Timeout state logic
                message = "Останов ожидания"
            }
            AutoboiState.Guamod -> {
                message = "Останов расчета"
                if (state != previousState) {
                    // C# had WriteMessageToGuamod("Идет распознавание...")
                    // TODO: Implement Guamod message display
                }
            }
        }
        // Log the message for now. In C#, it updated a button text.
        // Log.d("MainViewModel", "Autoboi State Changed: $message")
    }

    fun updateInventory(newInventory: List<InvEntry>) {
        _inventory.postValue(newInventory)
    }

    fun updateEquippedItems(newEquippedItems: ParsedDressed) {
        _equippedItems.postValue(newEquippedItems)
    }
}