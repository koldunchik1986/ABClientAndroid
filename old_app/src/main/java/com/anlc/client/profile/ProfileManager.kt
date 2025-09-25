package com.anlc.client.profile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class ProfileManager(context: Context, private val gson: Gson) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("profile_settings", Context.MODE_PRIVATE)

    private val PROFILE_SETTINGS_KEY = "profile_settings_key"

    fun saveProfileSettings(settings: ProfileSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString(PROFILE_SETTINGS_KEY, json).apply()
    }

    fun loadProfileSettings(): ProfileSettings {
        val json = sharedPreferences.getString(PROFILE_SETTINGS_KEY, null)
        return if (json != null) {
            gson.fromJson(json, ProfileSettings::class.java)
        } else {
            // Return default settings if no saved settings are found
            ProfileSettings()
        }
    }
}
