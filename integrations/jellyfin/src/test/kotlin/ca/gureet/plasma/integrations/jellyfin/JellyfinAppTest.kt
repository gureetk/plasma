// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.jellyfin

import android.app.Application
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opening a Jellyfin item in the Jellyfin app (brief 5.19), as the app takes it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class JellyfinAppTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun withoutTheAppNothingOpens() {
        assertFalse(JellyfinApp.installed(context))
        assertFalse(JellyfinApp.open(context, "11111111-1111-1111-1111-111111111111"))
        assertEquals(null, shadowOf(context).nextStartedActivity)
    }

    @Test fun theAppOpensTheItemsPage() {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = JellyfinApp.PACKAGE
                applicationInfo = ApplicationInfo().apply { packageName = JellyfinApp.PACKAGE }
            },
        )
        shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(JellyfinApp.PACKAGE, "org.jellyfin.androidtv.ui.startup.StartupActivity"))

        assertTrue(JellyfinApp.installed(context))
        assertTrue(JellyfinApp.open(context, "11111111-1111-1111-1111-111111111111"))

        val started = shadowOf(context).nextStartedActivity
        assertEquals("org.jellyfin.androidtv.ui.startup.StartupActivity", started.component?.className)
        assertEquals("11111111-1111-1111-1111-111111111111", started.getStringExtra("ItemId"))
    }
}
