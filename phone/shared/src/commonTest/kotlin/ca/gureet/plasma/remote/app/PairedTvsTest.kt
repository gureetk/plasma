// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import ca.gureet.plasma.remote.app.tv.FoundTv
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.tv.PairedTvs
import ca.gureet.plasma.remote.app.tv.Vault
import ca.gureet.plasma.remote.protocol.RemoteAdvert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paired TVs kept (brief 5.22) and found again at a new address by their ids. */
class PairedTvsTest {
    private class Memory : Vault {
        var kept: ByteArray? = null
        override fun read() = kept
        override fun write(bytes: ByteArray?) {
            kept = bytes
        }
    }

    private val vault = Memory()
    private val tvs = PairedTvs(vault)
    private val livingRoom = PairedTv("tv-1", "Living Room TV", "192.168.1.42", 8486, "A".repeat(43), "t0k3n")
    private val bedroom = PairedTv("tv-2", "Bedroom TV", "192.168.1.50", 8486, "B".repeat(43), "s3cr3t")

    @Test fun pairedTvsAreKeptTokensAndAll() {
        tvs.save(livingRoom)
        tvs.save(bedroom)
        tvs.save(livingRoom.copy(token = "n3w"))

        assertEquals(listOf(bedroom, livingRoom.copy(token = "n3w")), PairedTvs(vault).all())
    }

    @Test fun aTvReinstalledAtTheSameAddressTakesTheOldOnesPlace() {
        tvs.save(livingRoom)
        tvs.save(bedroom)
        // Plasma reinstalled: a new id and certificate, the same name and address.
        val again = livingRoom.copy(id = "tv-7", fingerprint = "C".repeat(43), token = "n3w")
        tvs.save(again)
        assertEquals(listOf(bedroom, again), tvs.all())
    }

    @Test fun theTvLastUsedIsLast() {
        tvs.save(livingRoom)
        tvs.save(bedroom)
        tvs.use("tv-1")
        assertEquals(listOf(bedroom, livingRoom), tvs.all())
        tvs.use("tv-9")
        assertEquals(listOf(bedroom, livingRoom), tvs.all())
    }

    @Test fun forgettingTheLastTvLeavesNothingKept() {
        tvs.save(livingRoom)
        tvs.remove("tv-1")

        assertEquals(emptyList(), tvs.all())
        assertNull(vault.kept)
    }

    @Test fun whatTheVaultCantGiveBackIsNoTvs() {
        vault.kept = byteArrayOf(1, 2, 3)
        assertEquals(emptyList(), tvs.all())
    }

    @Test fun aTvFoundAtANewAddressIsFollowedByItsId() {
        tvs.save(livingRoom)
        tvs.save(bedroom)
        val found = listOf(
            FoundTv(RemoteAdvert("tv-1", "Living Room TV", 1, 8490), "192.168.1.77"),
            FoundTv(RemoteAdvert("tv-9", "Kitchen TV", 1, 8486), "192.168.1.60"),
        )

        assertTrue(tvs.follow(found))
        // In the order they were paired, the one that moved where it was.
        assertEquals(listOf(livingRoom.copy(host = "192.168.1.77", port = 8490), bedroom), tvs.all())
        // The same again changes nothing.
        assertFalse(tvs.follow(found))
    }
}
