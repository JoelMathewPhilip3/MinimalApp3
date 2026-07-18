package com.joel.minimallauncher.model

data class LauncherSettings(
    val favoriteIds: List<String> = emptyList(),
    val minimalMode: Boolean = false,
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    val reduceGestures: Boolean = false,
    val hapticFeedback: Boolean = true,
    val doubleTapLock: Boolean = false,
    val launcherIdleLockSeconds: Int = 0,
    val showMorningReading: Boolean = true,
    val onboardingComplete: Boolean = false
)
