// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.NetworkFolderEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * A network folder (brief 5.6): a folder ([path], its parts separated by "/") in the SMB share
 * [share] on [host], signed in to as [username] (with [domain], when it has one), where recordings
 * are copied. [freeBytes] is the share's room when last looked at.
 */
data class NetworkFolder(
    val host: String,
    val share: String,
    val path: String,
    val username: String,
    val password: String,
    val domain: String? = null,
    val freeBytes: Long? = null,
    val checkedAt: Instant? = null,
) {
    /** "nas.home.lan › Media › Recordings", as Settings shows it. */
    val shown: String get() = (listOf(host, share) + path.split('/').filter { it.isNotEmpty() }).joinToString(" › ")

    /**
     * Where [file], a path in this folder, is on the network, as a recording keeps its copy's
     * place: "smb://nas.home.lan/Media/Recordings/TV Recordings/…".
     */
    fun locate(file: String): String = "smb://$host/$share/" + listOf(path.trim('/'), file.trim('/')).filter { it.isNotEmpty() }.joinToString("/")

    override fun toString(): String = "NetworkFolder($shown)"

    companion object {
        /** The server a copy's place ([locate]) is on, as Recordings says it: "On nas.home.lan". */
        fun hostOf(location: String): String? = placeOf(location)?.host

        /** A copy's place ([locate]): its server, its share, and the path in the share. */
        fun placeOf(location: String): NetworkPlace? {
            if (!location.startsWith(SMB)) return null
            val parts = location.removePrefix(SMB).split('/', limit = 3)
            if (parts.size < 3 || parts.any { it.isEmpty() }) return null
            return NetworkPlace(parts[0], parts[1], parts[2])
        }

        private const val SMB = "smb://"
    }
}

/** Where a recording's copy is: [path] (parts separated by "/") in [share] on [host]. */
data class NetworkPlace(val host: String, val share: String, val path: String)

/** The network folder recordings are copied to (Settings › Recording, 4.34), its password sealed (6.10). */
class NetworkFolders(private val db: PlasmaDatabase, private val secrets: SecretBox, private val clock: PlasmaClock) {
    /** The folder, or null when Copy to Network Folder is off. One the Keystore can't open any more reads as off. */
    val folder: Flow<NetworkFolder?> = db.networkFolders().observe(MAIN).map { it?.opened() }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun get(): NetworkFolder? = db.networkFolders().get(MAIN)?.opened()

    /** Copy to Network Folder, to [folder]. */
    suspend fun save(folder: NetworkFolder) {
        db.networkFolders().upsert(
            NetworkFolderEntity(
                id = MAIN,
                host = folder.host,
                share = folder.share,
                path = folder.path,
                domain = folder.domain,
                username = folder.username,
                passwordEnc = secrets.seal(folder.password),
                freeBytes = folder.freeBytes,
                checkedAt = clock.now().toEpochMilli(),
            ),
        )
    }

    /** The share's room, as just looked at. */
    suspend fun setFree(freeBytes: Long?) = db.networkFolders().setFree(MAIN, freeBytes, clock.now().toEpochMilli())

    /** Copy to Network Folder turned off: the folder, and its password, are forgotten. */
    suspend fun forget() = db.networkFolders().delete(MAIN)

    private fun NetworkFolderEntity.opened(): NetworkFolder? {
        val password = runCatching { secrets.open(passwordEnc) }.getOrNull() ?: return null
        return NetworkFolder(host, share, path, username, password, domain, freeBytes, Instant.ofEpochMilli(checkedAt))
    }

    private companion object {
        const val MAIN = "main"
    }
}
