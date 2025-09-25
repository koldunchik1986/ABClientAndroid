package com.neverlands.anlc.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.model.Profile
import com.neverlands.anlc.data.remote.AuthRepository
import com.neverlands.anlc.util.CryptoHelper
import com.neverlands.anlc.data.remote.AuthResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    val profiles: LiveData<List<Profile>> = ProfileManager.profiles

    private val _authResult = MutableLiveData<AuthResult>()
    val authResult: LiveData<AuthResult> = _authResult

    fun authorize(profile: Profile, password: String) {
        AuthRepository.authorize(getApplication(), profile, password) { result ->
            _authResult.value = result
        }
    }

    fun addOrUpdateProfile(
        profile: Profile,
        rawPassword: String,
        rawPasswordFlash: String,
        configPassword: String
    ) {
        viewModelScope.launch {
            profile.encryptedPassword = CryptoHelper.encryptString(rawPassword, configPassword)
            profile.encryptedPasswordFlash = CryptoHelper.encryptString(rawPasswordFlash, configPassword)
            profile.configPasswordHash = CryptoHelper.passwordToHash(configPassword)
            ProfileManager.addProfile(profile)
        }
    }

    fun removeProfile(profile: Profile) {
        viewModelScope.launch {
            ProfileManager.removeProfile(profile)
        }
    }
}
