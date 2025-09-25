package com.neverlands.anlc

import android.app.Application
import com.neverlands.anlc.data.local.MapManager
import com.neverlands.anlc.data.local.ProfileManager

class ANLCApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ProfileManager.init(this)
        MapManager.init(this)
    }
}
