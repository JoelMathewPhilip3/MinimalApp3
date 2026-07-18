package com.joel.minimallauncher.model

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String
) {
    val id: String get() = "$packageName/$activityName"
}
