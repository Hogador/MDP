package com.mdaopay.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingGuardianViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _isHermit = MutableStateFlow(false)
    val isHermit: StateFlow<Boolean> = _isHermit.asStateFlow()

    init {
        viewModelScope.launch {
            _isHermit.value = userPreferences.getRecoveryScenario() == "hermit"
        }
    }
}
