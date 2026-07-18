package com.joel.minimallauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.joel.minimallauncher.model.AppEntry

class AppRepository(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    @Volatile private var cachedApps: List<AppEntry>? = null

    fun loadLaunchableApps(forceRefresh: Boolean = false): List<AppEntry> {
        cachedApps?.takeUnless { forceRefresh }?.let { return it }

        val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(queryIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(queryIntent, 0)
        }

        val result = resolveInfos.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            if (activityInfo.packageName == context.packageName) return@mapNotNull null
            val label = activityInfo.loadLabel(packageManager).toString().trim()
            if (label.isBlank()) return@mapNotNull null
            AppEntry(
                label = label,
                packageName = activityInfo.packageName,
                activityName = activityInfo.name
            )
        }.distinctBy { it.id }.sortedBy { it.label.lowercase() }

        cachedApps = result
        return result
    }

    fun invalidateCache() { cachedApps = null }

    fun launch(app: AppEntry): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openAppInfo(app: AppEntry): Boolean = runCatching {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
