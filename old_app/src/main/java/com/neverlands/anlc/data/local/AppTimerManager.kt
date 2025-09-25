package com.neverlands.anlc.data.local

import com.neverlands.anlc.data.local.model.AppTimer
import java.util.Collections
import java.util.Date

object AppTimerManager {
    private val appTimers = Collections.synchronizedList(mutableListOf<AppTimer>())

    fun addAppTimer(appTimer: AppTimer) {
        appTimers.add(appTimer)
        // TODO: Sort timers by triggerTime if needed for display
    }

    fun getAppTimers(): List<AppTimer> {
        return appTimers.toList()
    }

    fun removeAppTimer(appTimer: AppTimer) {
        appTimers.remove(appTimer)
    }

    fun clearAllTimers() {
        appTimers.clear()
    }
}