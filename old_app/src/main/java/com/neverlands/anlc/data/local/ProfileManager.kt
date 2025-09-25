package com.neverlands.anlc.data.local

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neverlands.anlc.data.local.model.Profile
import java.io.File

object ProfileManager {

    private lateinit var profilesFile: File
    private val gson = Gson()
    private val _profiles = MutableLiveData<List<Profile>>()
    val profiles: LiveData<List<Profile>> = _profiles
    private var currentProfile: Profile? = null

    fun init(context: Context) {
        profilesFile = File(context.filesDir, "profiles.json")
        loadProfiles()
    }

    private fun loadProfiles() {
        if (profilesFile.exists()) {
            val json = profilesFile.readText()
            val type = object : TypeToken<MutableList<Profile>>() {}.type
            _profiles.value = gson.fromJson(json, type) ?: mutableListOf()
        }
    }

    private fun saveProfiles() {
        val json = gson.toJson(_profiles.value)
        profilesFile.writeText(json)
    }

    fun getProfileByLogin(login: String): Profile? {
        return _profiles.value?.find { it.userNick.equals(login, ignoreCase = true) }
    }

    fun setCurrentProfile(profile: Profile?) {
        currentProfile = profile
    }

    fun getCurrentProfile(): Profile? {
        return currentProfile
    }

    fun addProfile(profile: Profile) {
        val currentList = _profiles.value?.toMutableList() ?: mutableListOf()
        currentList.removeAll { it.userNick.equals(profile.userNick, ignoreCase = true) }
        currentList.add(profile)
        _profiles.value = currentList
        saveProfiles()
    }

    fun removeProfile(profile: Profile) {
        val currentList = _profiles.value?.toMutableList() ?: mutableListOf()
        currentList.remove(profile)
        _profiles.value = currentList
        saveProfiles()
    }

    fun saveCurrentProfile() {
        currentProfile?.let { profile ->
            val currentList = _profiles.value?.toMutableList() ?: mutableListOf()
            val index = currentList.indexOfFirst { it.userNick.equals(profile.userNick, ignoreCase = true) }
            if (index != -1) {
                currentList[index] = profile
            } else {
                currentList.add(profile)
            }
            _profiles.value = currentList
            saveProfiles()
        }
    }
}
