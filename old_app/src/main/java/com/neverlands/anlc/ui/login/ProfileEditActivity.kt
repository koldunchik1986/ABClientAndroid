package com.neverlands.anlc.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import com.neverlands.anlc.data.local.model.Profile
import com.neverlands.anlc.databinding.ActivityProfileEditBinding
import com.neverlands.anlc.ui.base.BaseActivity
import com.neverlands.anlc.util.parcelable

/**
 * Activity для создания и редактирования профилей.
 */
class ProfileEditActivity : BaseActivity() {

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var binding: ActivityProfileEditBinding
    private var isNewProfile: Boolean = true
    private var originalProfile: Profile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isNewProfile = intent.getBooleanExtra("is_new_profile", true)
        if (!isNewProfile) {
            originalProfile = intent.parcelable("profile_to_edit") // Profile needs to be Parcelable
            originalProfile?.let { profile ->
                // Заполняем поля для редактирования
                binding.editTextNick.setText(profile.userNick)
                // Пароли не отображаем из соображений безопасности
                binding.checkBoxAutoLogin.isChecked = profile.useAutoLogon
                binding.checkBoxUseProxy.isChecked = profile.useProxy
                binding.checkBoxAutoClearCookies.isChecked = profile.autoClearCookies
            }
        }

        setupViews()
    }

    private fun setupViews() {
        binding.buttonSaveProfile.setOnClickListener {
            saveProfile()
        }

        binding.buttonCancel.setOnClickListener {
            finish() // Просто закрываем Activity
        }
    }

    private fun saveProfile() {
        val nick = binding.editTextNick.text.toString()
        val password = binding.editTextPassword.text.toString()
        val passwordFlash = binding.editTextPasswordFlash.text.toString()
        val configPassword = binding.editTextConfigPassword.text.toString()
        val autoLogin = binding.checkBoxAutoLogin.isChecked
        val useProxy = binding.checkBoxUseProxy.isChecked
        val autoClearCookies = binding.checkBoxAutoClearCookies.isChecked

        if (nick.isBlank() || password.isBlank() || configPassword.isBlank()) {
            showToast("Пожалуйста, заполните ник, пароль и пароль конфигурации.")
            return
        }

        val profile = originalProfile?.copy(
            userNick = nick,
            useAutoLogon = autoLogin,
            useProxy = useProxy,
            autoClearCookies = autoClearCookies
        ) ?: Profile(
            userNick = nick,
            useAutoLogon = autoLogin,
            useProxy = useProxy,
            autoClearCookies = autoClearCookies
        )

        viewModel.addOrUpdateProfile(profile, password, passwordFlash, configPassword)
        showToast("Профиль сохранен!")
        finish() // Закрываем Activity после сохранения
    }
}
