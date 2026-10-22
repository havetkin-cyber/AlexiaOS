package sk.alexia.os.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Skutočné appky v telefóne, ktoré sa dajú spustiť z AlexiaOS. */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activity: String,
    val icon: Drawable?,
)

object InstalledApps {

    @Suppress("DEPRECATION")
    suspend fun load(ctx: Context): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        list.asSequence()
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { ri ->
                LaunchableApp(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    activity = ri.activityInfo.name,
                    icon = runCatching { ri.loadIcon(pm) }.getOrNull(),
                )
            }
            .distinctBy { it.packageName + "/" + it.activity }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(ctx: Context, app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { ctx.startActivity(intent) }
    }

    fun launchPackage(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }
}
