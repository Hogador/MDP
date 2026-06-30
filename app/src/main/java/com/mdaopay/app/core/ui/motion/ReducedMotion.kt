package com.mdaopay.app.core.ui.motion

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object ReducedMotion {
    val isEnabled: Boolean
        @Composable get() {
            val context = LocalContext.current
            return remember(context) {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                am?.isEnabled == true && am.isTouchExplorationEnabled
            }
        }

    val spring: androidx.compose.animation.core.Easing
        @Composable get() = if (isEnabled) MDAOEasing.easeOut else MDAOEasing.spring

    val fast: Int
        @Composable get() = if (isEnabled) 1 else MDAODuration.fast

    val quick: Int
        @Composable get() = if (isEnabled) 1 else MDAODuration.quick

    val base: Int
        @Composable get() = if (isEnabled) 1 else MDAODuration.base

    val page: Int
        @Composable get() = if (isEnabled) 1 else MDAODuration.page

    val sheet: Int
        @Composable get() = if (isEnabled) 1 else MDAODuration.sheet

    val result: Int
        @Composable get() = if (isEnabled) 1 else MDAODuration.result
}
