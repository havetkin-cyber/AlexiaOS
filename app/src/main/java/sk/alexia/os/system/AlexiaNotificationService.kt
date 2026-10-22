package sk.alexia.os.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notifikácia v centre notifikácií AlexiaOS. */
data class OsNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
)

/**
 * Číta notifikácie celého telefónu, aby ich AlexiaOS mohla zobraziť vo vlastnom centre.
 * Nič sa neukladá ani neodosiela – zoznam žije len v pamäti.
 */
class AlexiaNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
        _items.value = list
            .filter { it.packageName != packageName }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .mapNotNull { sbn ->
                val e = sbn.notification.extras
                val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
                    ?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val label = runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                }.getOrDefault(sbn.packageName)
                OsNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                    time = sbn.postTime,
                    clearable = sbn.isClearable,
                    contentIntent = sbn.notification.contentIntent,
                )
            }
            .sortedByDescending { it.time }
    }

    companion object {
        private val _items = MutableStateFlow<List<OsNotification>>(emptyList())
        val items: StateFlow<List<OsNotification>> = _items.asStateFlow()

        @Volatile private var instance: AlexiaNotificationService? = null

        fun hasAccess(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun accessIntent(ctx: Context): android.content.Intent =
            if (Build.VERSION.SDK_INT >= 30) {
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(ctx, AlexiaNotificationService::class.java).flattenToString(),
                    )
            } else {
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        fun clearAll() {
            runCatching { instance?.cancelAllNotifications() }
        }

        fun open(n: OsNotification) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = android.app.ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    n.contentIntent?.send(null, 0, null, null, null, null, opts.toBundle())
                } else {
                    n.contentIntent?.send()
                }
            }
            if (n.clearable) dismiss(n.key)
        }
    }
}
