package com.mdaopay.app.core.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BadgeManager @Inject constructor(
    @ApplicationContext private val app: Context
) {
    fun getBadgeCount(): Int = 0

}
