package com.mdaopay.app.core.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeHolder {
    private val _current = MutableStateFlow(AppTheme.DARK)
    val current: StateFlow<AppTheme> = _current.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _current.value = theme
    }
}
