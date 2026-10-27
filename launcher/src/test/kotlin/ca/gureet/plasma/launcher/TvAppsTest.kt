// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The TV's apps as Apps lists them (brief 4.4 and 5.13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TvAppsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun install(packageName: String, label: String, category: String, activityBanner: Int = 0, appBanner: Int = 0) {
        val app = ApplicationInfo().apply {
            this.packageName = packageName
            banner = appBanner
        }
        val entry = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.MainActivity"
                applicationInfo = app
                banner = activityBanner
            }
            nonLocalizedLabel = label
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(category), entry)
    }

    @Test fun appsMadeForTvComeFirstThenPhoneApps() {
        install("tv.example.sonar", "Sonar", Intent.CATEGORY_LEANBACK_LAUNCHER, activityBanner = 1)
        install("tv.example.cloudnest", "Cloudnest", Intent.CATEGORY_LEANBACK_LAUNCHER, appBanner = 1)
        install("tv.example.quarry", "quarry", Intent.CATEGORY_LAUNCHER)
        install("tv.example.brightside", "Brightside Weather", Intent.CATEGORY_LAUNCHER)
        // An app with both entries is one app made for TV.
        install("tv.example.sonar", "Sonar", Intent.CATEGORY_LAUNCHER)
        // Plasma itself isn't one of them.
        install(context.packageName, "Plasma", Intent.CATEGORY_LEANBACK_LAUNCHER, activityBanner = 1)

        val apps = TvApps(context).installed()

        assertEquals(listOf("Cloudnest", "Sonar", "Brightside Weather", "quarry"), apps.map { it.label })
        assertEquals(listOf(true, true, false, false), apps.map { it.forTv })
        assertEquals(listOf(true, true, false, false), apps.map { it.hasBanner })
        assertEquals("tv.example.sonar.MainActivity", apps[1].activity)
    }

    @Test fun anAppWithoutANameGoesByItsPackage() {
        install("tv.example.tumble", "  ", Intent.CATEGORY_LEANBACK_LAUNCHER)

        assertEquals("tv.example.tumble", TvApps(context).installed().single().label)
    }

    @Test fun appArtNamesTheBannerOrTheIcon() {
        val withBanner = TvApp("tv.example.sonar", "tv.example.sonar.MainActivity", "Sonar", forTv = true, hasBanner = true)
        val without = withBanner.copy(hasBanner = false)

        assertEquals("app-banner:tv.example.sonar/tv.example.sonar.MainActivity", AppArt.bannerOf(withBanner))
        assertEquals(null, AppArt.bannerOf(without))
        assertEquals("app-icon:tv.example.sonar/tv.example.sonar.MainActivity", AppArt.iconOf(without))
        assertTrue(AppArt.isAppArt(AppArt.iconOf(without)))
        assertFalse(AppArt.isAppArt("fixture://app_sonar"))
    }
}
