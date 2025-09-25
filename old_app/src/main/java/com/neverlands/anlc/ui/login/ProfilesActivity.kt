package com.neverlands.anlc.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.neverlands.anlc.R
import com.neverlands.anlc.data.local.ProfileManager
import com.neverlands.anlc.data.local.model.Profile
import com.neverlands.anlc.data.remote.AuthResult
import com.neverlands.anlc.databinding.ActivityProfilesBinding
import com.neverlands.anlc.forms.LogListActivity
import com.neverlands.anlc.ui.base.BaseActivity
import com.neverlands.anlc.ui.main.MainActivity

class ProfilesActivity : BaseActivity() {

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModelFactory(application) }
    private lateinit var binding: ActivityProfilesBinding
    private var selectedProfile: Profile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        // Load profiles when the activity resumes to ensure the list is up-to-date
        // This handles cases where a profile is added/edited/deleted in ProfileEditActivity
        // and we return to ProfilesActivity.
        ProfileManager.profiles.value?.let { currentProfiles ->
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_single_choice,
                currentProfiles.map { it.userNick }
            )
            binding.profilesListView.adapter = adapter
            binding.profilesListView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            binding.profilesListView.setOnItemClickListener { _, _, position, _ ->
                selectedProfile = currentProfiles[position]
            }
        }
    }

    private fun setupViews() {
        binding.loginButton.setOnClickListener {
            selectedProfile?.let { profile ->
                PasswordInputDialog { password ->
                    viewModel.authorize(profile, password)
                }.show(supportFragmentManager, "password_dialog")
            } ?: showToast("Пожалуйста, выберите профиль")
        }

        binding.createButton.setOnClickListener {
            val intent = Intent(this, ProfileEditActivity::class.java)
            intent.putExtra("is_new_profile", true)
            startActivity(intent)
        }

        binding.editButton.setOnClickListener {
            selectedProfile?.let { profile ->
                val intent = Intent(this, ProfileEditActivity::class.java)
                intent.putExtra("is_new_profile", false)
                intent.putExtra("profile_to_edit", profile)
                startActivity(intent)
            } ?: showToast("Пожалуйста, выберите профиль для редактирования")
        }

        binding.deleteButton.setOnClickListener {
            selectedProfile?.let { profile ->
                viewModel.removeProfile(profile)
                selectedProfile = null
            } ?: showToast("Пожалуйста, выберите профиль для удаления")
        }

        binding.logsButton.setOnClickListener {
            val intent = Intent(this, LogListActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupObservers() {
        viewModel.profiles.observe(this) { profiles ->
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_single_choice,
                profiles.map { it.userNick }
            )
            binding.profilesListView.adapter = adapter
            binding.profilesListView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            binding.profilesListView.setOnItemClickListener { _, _, position, _ ->
                selectedProfile = profiles[position]
            }
        }

        viewModel.authResult.observe(this) { result ->
            when (result) {
                is AuthResult.Success -> {
                    selectedProfile?.let { profile ->
                        ProfileManager.setCurrentProfile(profile)
                        val intent = Intent(this, MainActivity::class.java).apply {
                            if (result.useHolder) {
                                putExtra("use_holder", true)
                            } else {
                                putExtra("html_content", result.htmlContent)
                            }
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                is AuthResult.Failure -> showToast("Ошибка: ${result.error}")
            }
        }
    }
}

class ProfileViewModelFactory(private val application: android.app.Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}