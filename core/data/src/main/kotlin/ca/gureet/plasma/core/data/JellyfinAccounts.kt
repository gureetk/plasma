// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.JellyfinAccountEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** A profile's Jellyfin sign-in (brief 5.19), its token opened. */
data class JellyfinAccount(
    /** The address the server answered at. */
    val server: String,
    val serverId: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val token: String,
    val deviceId: String,
) {
    // It carries the token.
    override fun toString(): String = "JellyfinAccount($serverName, $userName)"
}

/**
 * Each profile's Jellyfin sign-in (brief 5.19): each profile can link its own Jellyfin user. The
 * token is sealed by the Keystore, like a source's password (6.10).
 */
class JellyfinAccounts(
    private val db: PlasmaDatabase,
    private val secrets: SecretBox,
    private val clock: PlasmaClock,
    private val profile: ProfileSource = ProfileSource.fixed(),
) {
    /**
     * The sign-in of the profile watching now, or null without one. A token the Keystore can't
     * open any more (after a restore to another TV, say) reads as signed out.
     */
    fun observe(): Flow<JellyfinAccount?> = profile.watching { db.jellyfinAccounts().observe(it) }
        .map { it?.opened() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /** Keeps [account] as the sign-in of the profile watching, in place of any it had. */
    suspend fun save(account: JellyfinAccount) {
        db.jellyfinAccounts().upsert(
            JellyfinAccountEntity(
                profileId = profile.now(),
                serverUrl = account.server,
                serverId = account.serverId,
                serverName = account.serverName,
                userId = account.userId,
                userName = account.userName,
                accessTokenEnc = secrets.seal(account.token),
                deviceId = account.deviceId,
                signedInAt = clock.now().toEpochMilli(),
            ),
        )
    }

    /** Forget Server: the sign-in of the profile watching goes. */
    suspend fun forget() = db.jellyfinAccounts().delete(profile.now())

    private fun JellyfinAccountEntity.opened(): JellyfinAccount? {
        val token = runCatching { secrets.open(accessTokenEnc) }.getOrNull() ?: return null
        return JellyfinAccount(serverUrl, serverId, serverName, userId, userName, token, deviceId)
    }
}
