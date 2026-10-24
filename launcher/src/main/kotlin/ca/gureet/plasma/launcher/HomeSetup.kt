// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.edit

/** What the Home button opens now, as Android has it. */
sealed interface HomeNow {
    /** Plasma is the TV's Home app (methods 2 and 3). */
    data object Plasma : HomeNow

    /** Android asks which app to open (the chooser on many boxes, method 3). */
    data object Chooser : HomeNow

    /** Another app, by name: Google TV Home on the owner's TV. */
    data class App(val label: String) : HomeNow

    /** Nothing answers Home, which a TV shouldn't allow. */
    data object Nothing : HomeNow
}

/** How this TV lets an app become its Home app (brief 5.13, method 3). */
enum class DefaultHomeWay {
    /** It asks when Plasma requests Android's Home role. */
    Role,

    /** Its settings have a Home app page. */
    Settings,

    /** Pressing Home shows a chooser, where Plasma and Always can be picked. */
    Chooser,

    /** None of these: method 3 isn't available on this TV. */
    None,
}

/** The ways to make Plasma the Home screen (brief 4.23). */
enum class HomeMethod { Override, Replace, DefaultApp }

/** One of the TV's own launchers: an app besides Plasma that can be its Home. */
data class StockLauncher(val packageName: String, val label: String, val enabled: Boolean)

/** What Home Screen setup knows about this TV (brief 4.23, "detect what applies on this TV"). */
data class HomeSetup(
    /** The TV's model, as it names itself: "Google TV Streamer". */
    val device: String,
    /** Its Android version: "14". */
    val androidVersion: String,
    val home: HomeNow,
    /** Plasma's Home Button Override is turned on in Accessibility. */
    val overrideOn: Boolean,
    /** The TV's own launchers, and whether each is turned on. */
    val launchers: List<StockLauncher>,
    val defaultHome: DefaultHomeWay,
    /** Other apps whose Home overrides are on too, by name: Projectivy Launcher, Button Mapper. */
    val conflicts: List<String>,
    /** Android holds back Plasma's Accessibility switch as a restricted setting (Android 13+). */
    val restricted: Boolean,
) {
    /** Plasma is Home because the TV's own launcher was turned off (method 2). */
    val replaced: Boolean get() = home == HomeNow.Plasma && launchers.any { !it.enabled }

    /** The method that makes Plasma Home now, or null while the TV's own Home answers. */
    val method: HomeMethod?
        get() = when {
            home == HomeNow.Plasma -> if (replaced) HomeMethod.Replace else HomeMethod.DefaultApp
            overrideOn -> HomeMethod.Override
            else -> null
        }

    /** The TV's own launchers by name, in the order Android lists them; the first is its Home. */
    val launcherName: String? get() = (home as? HomeNow.App)?.label ?: launchers.firstOrNull()?.label

    companion object {
        /**
         * Reads this TV: its launchers, what Home opens, the Accessibility switches that are on,
         * and how it lets an app become Home.
         */
        fun read(context: Context): HomeSetup = HomeReadings.of(context).toSetup(Build.MODEL, Build.VERSION.RELEASE)

        /**
         * Android's Home role came back refused without asking: this TV doesn't offer it, so
         * Default Home App falls back to its other ways.
         */
        fun roleRefused(context: Context) = prefs(context).edit { putBoolean(ROLE_REFUSED, true) }

        internal fun isRoleRefused(context: Context): Boolean = prefs(context).getBoolean(ROLE_REFUSED, false)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private const val PREFS = "launcher"
        private const val ROLE_REFUSED = "homeRoleRefused"
    }
}

/** An activity that answers Home: its app and class, the class an alias stands for, its priority, and whether its app is on. */
internal data class HomeCandidate(val packageName: String, val name: String, val target: String?, val priority: Int, val enabled: Boolean)

/** What Android resolves Home to: an app's activity, by its app, or nothing. */
internal sealed interface Resolved {
    data class App(val packageName: String, val name: String) : Resolved
    data object Nothing : Resolved
}

/** What [HomeSetup] is read from, kept apart from Android so it can be tested. */
internal class HomeReadings(
    val ownPackage: String,
    val candidates: List<HomeCandidate>,
    val resolved: Resolved,
    /** The Accessibility services turned on, Plasma's among them or not. */
    val enabledServices: List<ComponentName>,
    val ownService: ComponentName,
    val roleAvailable: Boolean,
    val homeSettings: Boolean,
    val restricted: Boolean,
    val label: (String) -> String,
) {
    /** The TV's own launchers' Home activities, which the override watches for. */
    fun stockHomes(): StockHomes = stockHomes(ownPackage, candidates)

    fun toSetup(device: String, androidVersion: String): HomeSetup {
        val launchers = candidates
            .filter { it.packageName != ownPackage && it.packageName !in SETTINGS_PACKAGES }
            .groupBy { it.packageName }
            // A fallback that only steps in when nothing else is there isn't a launcher, unless it
            // turns the launcher back on (Google TV's setup, brief 5.13).
            .filter { (packageName, activities) -> packageName in FALLBACK_LAUNCHERS || activities.any { it.priority >= 0 } }
            .map { (packageName, activities) -> StockLauncher(packageName, label(packageName), activities.first().enabled) }
        val home = when (resolved) {
            is Resolved.App -> when {
                resolved.packageName == ownPackage -> HomeNow.Plasma
                candidates.any { it.packageName == resolved.packageName && it.name == resolved.name } -> HomeNow.App(label(resolved.packageName))
                // Not one of the Home apps: Android's chooser.
                else -> HomeNow.Chooser
            }
            Resolved.Nothing -> HomeNow.Nothing
        }
        val homePackages = candidates.map { it.packageName }.toSet()
        val conflicts = enabledServices.map { it.packageName }.distinct()
            .filter { it != ownPackage && (it in KNOWN_OVERRIDES || it in homePackages) }
            .map(label)
        val defaultHome = when {
            roleAvailable -> DefaultHomeWay.Role
            homeSettings -> DefaultHomeWay.Settings
            home == HomeNow.Chooser -> DefaultHomeWay.Chooser
            else -> DefaultHomeWay.None
        }
        val overrideOn = ownService in enabledServices
        return HomeSetup(
            device = device,
            androidVersion = androidVersion,
            home = home,
            overrideOn = overrideOn,
            launchers = launchers,
            defaultHome = defaultHome,
            conflicts = conflicts,
            restricted = restricted && !overrideOn,
        )
    }

    companion object {
        /** Settings' own fallback Home, shown while the TV starts: never one to turn off. */
        val SETTINGS_PACKAGES = setOf("com.android.settings", "com.android.tv.settings")

        /** Google TV's setup, which turns its launcher back on if that's turned off alone (5.13). */
        val FALLBACK_LAUNCHERS = setOf("com.google.android.tungsten.setupwraith")

        /** Apps known to take over the Home button with an Accessibility service of their own. */
        val KNOWN_OVERRIDES = setOf("com.spocky.projengmenu", "flar2.homebutton", "dev.vodik7.tvquickactions")

        /** The TV's own launchers' Home activities, read on their own for the override. */
        fun stockHomesOf(context: Context): StockHomes = stockHomes(context.packageName, candidatesOf(context.packageManager))

        private fun stockHomes(ownPackage: String, candidates: List<HomeCandidate>): StockHomes {
            val others = candidates.filter { it.packageName != ownPackage && it.packageName !in SETTINGS_PACKAGES }
            return StockHomes(
                packages = others.map { it.packageName }.toSet(),
                activities = others.flatMap { listOfNotNull(it.name, it.target) }.toSet(),
            )
        }

        private val HOME = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        /** Every activity that answers Home, Plasma's own and those of apps turned off included. */
        private fun candidatesOf(pm: PackageManager): List<HomeCandidate> = pm.homeActivities(HOME).map { info ->
            val activity = info.activityInfo
            HomeCandidate(activity.packageName, activity.name, activity.targetActivity, info.priority, pm.isOn(activity.packageName, activity.applicationInfo.enabled))
        }

        fun of(context: Context): HomeReadings {
            val pm = context.packageManager
            val resolved = pm.resolveHome(HOME)?.activityInfo?.let { Resolved.App(it.packageName, it.name) } ?: Resolved.Nothing
            return HomeReadings(
                ownPackage = context.packageName,
                candidates = candidatesOf(pm),
                resolved = resolved,
                enabledServices = enabledServices(context),
                ownService = ComponentName(context, HomeButtonService::class.java),
                roleAvailable = roleAvailable(context) && !HomeSetup.isRoleRefused(context),
                homeSettings = pm.resolveHome(Intent(Settings.ACTION_HOME_SETTINGS)) != null,
                restricted = restricted(context),
                label = { packageName -> pm.labelOf(packageName) },
            )
        }

        /** The Accessibility services turned on, from Android's own list of them. */
        private fun enabledServices(context: Context): List<ComponentName> =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
                .split(':')
                .mapNotNull { ComponentName.unflattenFromString(it.trim()) }

        private fun roleAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_HOME) == true

        /**
         * Android 13 and later hold back the Accessibility switch of an app installed from a file
         * (a "restricted setting", 5.13) until it's allowed in App info or over ADB.
         */
        private fun restricted(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            return try {
                ops.unsafeCheckOpNoThrow(RESTRICTED_SETTINGS_OP, Process.myUid(), context.packageName) == AppOpsManager.MODE_ERRORED
            } catch (unknown: IllegalArgumentException) {
                // A TV that doesn't know the operation doesn't restrict.
                false
            }
        }

        private const val RESTRICTED_SETTINGS_OP = "android:access_restricted_settings"

        @SuppressLint("QueryPermissionsNeeded") // Home apps are declared in the manifest's <queries>.
        @Suppress("DEPRECATION") // The flags' class is API 33; these work on every version.
        private fun PackageManager.homeActivities(home: Intent): List<ResolveInfo> =
            queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_DISABLED_COMPONENTS)

        @Suppress("DEPRECATION")
        private fun PackageManager.resolveHome(intent: Intent): ResolveInfo? = resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        /** Whether [packageName] is turned on: `pm disable-user` turns a launcher off (method 2). */
        private fun PackageManager.isOn(packageName: String, fallback: Boolean): Boolean = try {
            when (getApplicationEnabledSetting(packageName)) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> fallback
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
        } catch (unknown: IllegalArgumentException) {
            fallback
        }

        @Suppress("DEPRECATION")
        private fun PackageManager.labelOf(packageName: String): String = try {
            getApplicationLabel(getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
        } catch (gone: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}

/** The TV's own launchers: the apps besides Plasma that answer Home (brief 5.13), and their Home activities. */
internal class StockHomes(val packages: Set<String>, private val activities: Set<String>) {
    /**
     * Whether a window that came to the front is one of them showing its Home: a new window, not
     * a pane inside one, of one of their Home activities. Their other screens, such as a settings
     * panel, are left alone.
     */
    fun isHome(packageName: CharSequence?, className: CharSequence?, contentChangeTypes: Int): Boolean =
        contentChangeTypes == 0 && packageName?.toString() in packages && className?.toString() in activities

    companion object {
        val NONE = StockHomes(emptySet(), emptySet())
    }
}
