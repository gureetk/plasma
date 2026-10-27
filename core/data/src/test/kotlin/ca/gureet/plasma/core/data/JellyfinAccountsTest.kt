// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.FixedClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Each profile's Jellyfin sign-in (brief 5.19), its token kept sealed (6.10). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class JellyfinAccountsTest {
    private lateinit var db: PlasmaDatabase
    private lateinit var accounts: JellyfinAccounts
    private val maya = JellyfinAccount("http://media.home.lan:8096", "srv-1", "Media", "user-maya", "Maya", "tok-maya", "device-1")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlasmaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = JellyfinAccounts(db, FakeSecretBox(), FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC))
    }

    @After fun tearDown() = db.close()

    @Test fun aSignInIsKeptWithItsTokenSealed() = runTest {
        assertNull(accounts.observe().first())

        accounts.save(maya)

        assertEquals(maya, accounts.observe().first())
        val stored = db.jellyfinAccounts().get(DEFAULT_PROFILE)!!
        assertFalse("tok-maya" in stored.accessTokenEnc)
        assertEquals(Instant.parse("2026-10-21T20:41:00Z").toEpochMilli(), stored.signedInAt)
        assertFalse("tok-maya" in maya.toString())
    }

    @Test fun eachProfileHasItsOwnAndForgettingTakesOnlyItsOwn() = runTest {
        val watching = Watching()
        val accounts = JellyfinAccounts(db, FakeSecretBox(), FixedClock(Instant.parse("2026-10-21T20:41:00Z"), ZoneOffset.UTC), watching)
        accounts.save(maya)
        watching.id.value = "leo"
        accounts.save(maya.copy(userId = "user-leo", userName = "Leo", token = "tok-leo"))
        assertEquals("Leo", accounts.observe().first()?.userName)

        // Signing in again replaces the profile's sign-in.
        watching.id.value = DEFAULT_PROFILE
        accounts.save(maya.copy(token = "tok-maya-2"))
        assertEquals("tok-maya-2", accounts.observe().first()?.token)

        accounts.forget()

        assertNull(accounts.observe().first())
        watching.id.value = "leo"
        assertEquals("Leo", accounts.observe().first()?.userName)
    }

    /** Who's watching, as the test switches it. */
    private class Watching : ProfileSource {
        override val id = MutableStateFlow(DEFAULT_PROFILE)

        override suspend fun now(): String = id.value
    }

    @Test fun aTokenThatCantBeOpenedReadsAsSignedOut() = runTest {
        val locked = object : SecretBox {
            override fun seal(plain: String) = "sealed"
            override fun open(sealed: String): String = throw java.security.GeneralSecurityException("Key gone")
        }
        JellyfinAccounts(db, locked, FixedClock(Instant.EPOCH, ZoneOffset.UTC)).save(maya)

        assertNull(JellyfinAccounts(db, locked, FixedClock(Instant.EPOCH, ZoneOffset.UTC)).observe().first())
    }
}
