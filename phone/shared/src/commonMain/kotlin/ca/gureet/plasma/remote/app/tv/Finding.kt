// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

import ca.gureet.plasma.remote.protocol.RemoteAdvert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A TV advertising itself on the Wi-Fi now (brief 5.22): its advert, and the address it was found at. */
data class FoundTv(val advert: RemoteAdvert, val host: String) {
    val endpoint: TvEndpoint get() = TvEndpoint(host, advert.tls)
}

/**
 * Finds TVs running Plasma on the Wi-Fi: `_plasma-remote._tcp` over mDNS. The flow gives every TV
 * found so far each time one comes or goes, starting with none, for as long as it's collected.
 * Android browses with NsdManager, iPhone with NSNetService.
 */
fun interface TvFinder {
    fun tvs(): Flow<List<FoundTv>>
}

/**
 * The paired TVs with each moved to where it's been found now, by its id: a TV whose address
 * changed (a new lease from the router, another network) is found again by itself. Its name too,
 * if it was renamed. The TVs that weren't found, or are where they were, are as they were.
 */
fun List<PairedTv>.followedTo(found: List<FoundTv>): List<PairedTv> = map { paired ->
    val now = found.firstOrNull { it.advert.id == paired.id } ?: return@map paired
    if (now.host == paired.host && now.advert.tls == paired.port && now.advert.name == paired.name) paired
    else paired.copy(host = now.host, port = now.advert.tls, name = now.advert.name)
}

/** Where a phone keeps a few bytes only it can read: Android Keystore-sealed storage, or the iPhone's Keychain. */
interface Vault {
    fun read(): ByteArray?

    /** Keeps [bytes], or forgets what was kept with null. */
    fun write(bytes: ByteArray?)
}

/**
 * The TVs this phone is paired with (brief 5.22), kept in the phone's [Vault], tokens and all,
 * as JSON. Something the vault can't give back is no paired TVs: pairing again sets it right.
 */
class PairedTvs(private val vault: Vault) {
    private val format = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PairedTv.serializer())

    fun all(): List<PairedTv> = runCatching { vault.read()?.decodeToString()?.let { format.decodeFromString(serializer, it) } }.getOrNull().orEmpty()

    /** Keeps [tv], in place of what was kept for the same TV. */
    fun save(tv: PairedTv) = keep(all().filter { it.id != tv.id } + tv)

    /** Moves the TV to the end, where the app opens next time: the paired TVs are kept in the order they were last used. */
    fun use(id: String) {
        val before = all()
        val tv = before.firstOrNull { it.id == id } ?: return
        if (before.last() != tv) keep(before - tv + tv)
    }

    /** Forget: the phone no longer has this TV's token. */
    fun remove(id: String) = keep(all().filter { it.id != id })

    /** Keeps where [found] says each paired TV is now; true when something changed. */
    fun follow(found: List<FoundTv>): Boolean {
        val before = all()
        val after = before.followedTo(found)
        if (after == before) return false
        keep(after)
        return true
    }

    private fun keep(tvs: List<PairedTv>) = vault.write(if (tvs.isEmpty()) null else format.encodeToString(serializer, tvs).encodeToByteArray())
}
