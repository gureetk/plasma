// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * An app the TV's launcher shows (brief 4.4): the activity it opens with, its name, and whether it
 * was made for TV (a Leanback entry) and has a banner of its own.
 */
data class TvApp(
    val packageName: String,
    val activity: String,
    val label: String,
    val forTv: Boolean,
    val hasBanner: Boolean,
) {
    val component: ComponentName get() = ComponentName(packageName, activity)
}

/** The TV's apps, from Android's package manager (brief 4.4 and 5.13, "Apps"). */
class TvApps(private val context: Context) {
    /**
     * The apps to show: those made for TV first, by their Leanback entries, then apps with only a
     * phone launcher entry, one entry each, A to Z within each. Plasma isn't one of them.
     */
    fun installed(): List<TvApp> {
        val pm = context.packageManager
        val tv = pm.entries(Intent.CATEGORY_LEANBACK_LAUNCHER).map { it.toApp(pm, forTv = true) }
        val phone = pm.entries(Intent.CATEGORY_LAUNCHER).map { it.toApp(pm, forTv = false) }
        return merged(tv, phone, context.packageName)
    }

    /**
     * The apps, then again whenever one is installed, updated, turned on or off, or removed. The
     * package manager is asked off the main thread.
     */
    fun changes(): Flow<List<TvApp>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.data?.schemeSpecificPart?.let(AppArt::forget)
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // The system's package broadcasts reach a receiver that other apps can't.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().map { installed() }.flowOn(Dispatchers.IO)

    /** Opens [app] at its entry, made for TV where it has one. */
    fun open(app: TvApp): Boolean = start(
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.forTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
    )

    /** The app's page in the TV's settings (4.4, App Info). */
    fun openInfo(packageName: String): Boolean =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Android's own question before an app is removed (4.4, Uninstall). */
    fun uninstall(packageName: String): Boolean =
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** The TV's own settings (4.4, Device Settings). */
    fun openDeviceSettings(): Boolean = start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Whether the Play Store is here, for Get More Apps (4.4). */
    fun hasPlayStore(): Boolean = launchIntentFor(context.packageManager, PLAY_STORE) != null

    /** The Play Store, for Get More Apps. */
    fun openPlayStore(): Boolean = launchIntentFor(context.packageManager, PLAY_STORE)?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } ?: false

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (refused: RuntimeException) {
        // Not on this TV, or not allowed.
        false
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"

        /**
         * The TV entries first, then phone-only ones, one entry per app (its first), A to Z within
         * each, without [own], Plasma.
         */
        internal fun merged(tv: List<TvApp>, phone: List<TvApp>, own: String): List<TvApp> {
            val byName = compareBy<TvApp> { it.label.lowercase() }.thenBy { it.packageName }
            val tvApps = tv.filter { it.packageName != own }.distinctBy { it.packageName }.sortedWith(byName)
            val seen = tvApps.map { it.packageName }.toSet()
            val phoneApps = phone.filter { it.packageName != own && it.packageName !in seen }.distinctBy { it.packageName }.sortedWith(byName)
            return tvApps + phoneApps
        }

        @SuppressLint("QueryPermissionsNeeded") // Launcher entries are declared in the manifest's <queries>.
        @Suppress("DEPRECATION") // The flags' class is API 33.
        private fun PackageManager.entries(category: String): List<ResolveInfo> =
            queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

        private fun ResolveInfo.toApp(pm: PackageManager, forTv: Boolean): TvApp {
            val activity = activityInfo
            val app: ApplicationInfo = activity.applicationInfo
            return TvApp(
                packageName = activity.packageName,
                activity = activity.name,
                label = loadLabel(pm).toString().trim().ifEmpty { activity.packageName },
                forTv = forTv,
                hasBanner = activity.banner != 0 || app.banner != 0,
            )
        }

        /** [packageName]'s own way in: its entry made for TV, else its phone one. */
        internal fun launchIntentFor(pm: PackageManager, packageName: String): Intent? =
            pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
    }
}
