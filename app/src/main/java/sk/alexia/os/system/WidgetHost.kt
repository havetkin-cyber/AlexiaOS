package sk.alexia.os.system

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Hostiteľ systémových widgetov (hodiny, počasie, kalendár… z iných appiek). */
object WidgetHost {
    private const val HOST_ID = 0xA1E
    @Volatile private var host: AppWidgetHost? = null

    fun get(ctx: Context): AppWidgetHost =
        host ?: synchronized(this) { host ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it } }

    fun manager(ctx: Context): AppWidgetManager = AppWidgetManager.getInstance(ctx)

    fun providers(ctx: Context): List<AppWidgetProviderInfo> =
        runCatching { manager(ctx).installedProviders }.getOrDefault(emptyList())
            .sortedBy { it.loadLabel(ctx.packageManager).lowercase() }

    fun info(ctx: Context, id: Int): AppWidgetProviderInfo? = runCatching { manager(ctx).getAppWidgetInfo(id) }.getOrNull()

    fun allocate(ctx: Context): Int = get(ctx).allocateAppWidgetId()

    fun delete(ctx: Context, id: Int) = runCatching { get(ctx).deleteAppWidgetId(id) }

    fun start(ctx: Context) = runCatching { get(ctx).startListening() }
    fun stop(ctx: Context) = runCatching { get(ctx).stopListening() }
}
