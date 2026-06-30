package com.mdaopay.app.core.security

sealed class BiometricAvailability {
    data object Available : BiometricAvailability()
    data object NoHardware : BiometricAvailability()
    data object NoneEnrolled : BiometricAvailability()
}
