// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl

/**
 * The update file Plasma reads (brief 5.18): the newest version, the Android it needs, its notes,
 * and an APK for each processor type with its SHA-256.
 *
 * ```json
 * {
 *   "versionCode": 512, "versionName": "0.2.0", "minSdk": 26, "notes": "…",
 *   "apks": [{ "abi": "arm64-v8a", "url": "app-arm64-v8a-release.apk", "sha256": "…" }, …]
 * }
 * ```
 *
 * An APK's address can be relative to the update file's.
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val notes: String,
    val apks: List<UpdateApk>,
) {
    /**
     * What this file offers a TV with Plasma [installedCode], Android [sdk] and processor types
     * [abis] (`Build.SUPPORTED_ABIS`), the file being at [address].
     */
    fun checkFor(installedCode: Long, sdk: Int, abis: List<String>, address: HttpUrl): UpdateCheck {
        if (versionCode <= installedCode) return UpdateCheck.UpToDate
        val apk = pick(apks, abis)
        val url = apk?.let { address.resolve(it.url) }
        if (sdk < minSdk || apk == null || url == null) return UpdateCheck.NotForThisTv(versionName)
        return UpdateCheck.Available(Update(versionCode, versionName, notes, url.toString(), apk.sha256.lowercase()))
    }

    companion object {
        /** The APK built for every processor type, for a TV none of the others fits. */
        const val UNIVERSAL = "universal"

        private val json = Json { ignoreUnknownKeys = true }

        /** Reads an update file; [UpdateProblem.BadFile] when it isn't one. */
        fun parse(text: String): UpdateManifest = try {
            val root = json.parseToJsonElement(text).jsonObject
            UpdateManifest(
                versionCode = root.long("versionCode") ?: throw bad(),
                versionName = root.string("versionName") ?: throw bad(),
                minSdk = (root["minSdk"] as? JsonPrimitive)?.intOrNull ?: 0,
                notes = root.string("notes").orEmpty().trim(),
                apks = (root["apks"] as? JsonArray ?: throw bad()).map { element ->
                    val apk = element as? JsonObject ?: throw bad()
                    val sha256 = apk.string("sha256")?.takeIf(::isSha256) ?: throw bad()
                    UpdateApk(abi = apk.string("abi") ?: throw bad(), url = apk.string("url") ?: throw bad(), sha256 = sha256)
                },
            )
        } catch (unreadable: SerializationException) {
            throw UpdateException(UpdateProblem.BadFile, unreadable)
        } catch (notAnObject: IllegalArgumentException) {
            throw UpdateException(UpdateProblem.BadFile, notAnObject)
        }

        /**
         * The APK for a TV whose processor types are [abis], the best first: the one for the first,
         * which a 32-bit Android on a 64-bit chip reports as `armeabi-v7a`, or else the universal one.
         */
        fun pick(apks: List<UpdateApk>, abis: List<String>): UpdateApk? =
            abis.firstOrNull()?.let { first -> apks.firstOrNull { it.abi == first } } ?: apks.firstOrNull { it.abi == UNIVERSAL }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

        private fun bad() = UpdateException(UpdateProblem.BadFile)

        private fun isSha256(hex: String): Boolean = hex.length == SHA256_HEX && hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }

        private const val SHA256_HEX = 64
    }
}

/** One APK in the update file: the processor type it's for, where it is, and its SHA-256. */
data class UpdateApk(val abi: String, val url: String, val sha256: String)

/** A newer Plasma for this TV, with its APK's address and SHA-256. */
data class Update(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val url: String,
    val sha256: String,
) {
    /** "0.2.0 (512)": the name alone can stay the same from build to build. */
    val label: String get() = "$versionName ($versionCode)"
}

/** What the update file says for this TV. */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(val update: Update) : UpdateCheck

    /** A newer version that this TV can't take: it needs a newer Android, or has no APK for its processor. */
    data class NotForThisTv(val versionName: String) : UpdateCheck
}

/** Why an update couldn't be checked for or installed. */
enum class UpdateProblem {
    /** The update address didn't answer, or answered with an error. */
    CantReach,

    /** Nothing is at the update address. */
    NotThere,

    /** What's there isn't an update file Plasma can read. */
    BadFile,

    /** The APK didn't arrive whole: its SHA-256 isn't the one in the update file. */
    Damaged,

    /** The APK isn't this Plasma, signed with its key, so it can't go over it. */
    NotPlasma,

    /** Android doesn't let Plasma install apps yet ("Install unknown apps"). */
    NotAllowed,

    /** Android's installer didn't install it, or the user said no. */
    Refused,

    /** There isn't room on the TV for the download. */
    NoRoom,

    /** A recording is being written, and Plasma doesn't update in the middle of one. */
    Recording,
}

class UpdateException(val problem: UpdateProblem, cause: Throwable? = null) : Exception(problem.name, cause)
