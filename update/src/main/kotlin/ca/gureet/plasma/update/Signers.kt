// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * What an APK is and who signed it, for checking an update before it's installed (brief 5.18):
 * Android only lets an APK signed with the same key go over Plasma, and checking first gives a
 * clear answer instead of the installer's.
 *
 * Signing certificates are kept as the SHA-256 of each, in hexadecimal.
 */
class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    /** The certificates the APK is signed with now. */
    val signers: Set<String>,
    /** Those, and the ones its key was rotated from (APK Signature Scheme v3), oldest first. */
    val lineage: List<String> = signers.toList(),
)

object Signers {
    /** The installed Plasma, as [ApkFacts]; null if Android won't say. */
    fun installed(context: Context): ApkFacts? = try {
        factsOf(context.packageManager.getPackageInfo(context.packageName, flags()))
    } catch (gone: PackageManager.NameNotFoundException) {
        null
    }

    /** The APK at [file], as [ApkFacts]; null when it isn't an APK Android can read. */
    fun ofApk(context: Context, file: File): ApkFacts? =
        context.packageManager.getPackageArchiveInfo(file.path, flags())?.let(::factsOf)

    /**
     * Whether [apk] can go over [installed] as version [versionCode]: the same app, that version,
     * signed with the same key, or with a new key it was rotated to from the one installed.
     */
    fun fits(apk: ApkFacts, installed: ApkFacts, versionCode: Long): Boolean = when {
        apk.packageName != installed.packageName || apk.versionCode != versionCode -> false
        apk.signers.isEmpty() || installed.signers.isEmpty() -> false
        apk.signers == installed.signers -> true
        else -> installed.signers.size == 1 && apk.signers.size == 1 && installed.signers.single() in apk.lineage
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun flags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else signaturesFlag()

    // Android 8 has only the old flag; the certificates are checked as a whole, as above.
    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signaturesFlag(): Int = PackageManager.GET_SIGNATURES

    private fun factsOf(info: PackageInfo): ApkFacts? {
        val (signers, lineage) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return null
            val now = signing.apkContentsSigners.orEmpty().map(::digest)
            val past = if (signing.hasMultipleSigners()) now else signing.signingCertificateHistory.orEmpty().map(::digest)
            now.toSet() to past
        } else {
            val now = oldSignatures(info).map(::digest)
            now.toSet() to now
        }
        return ApkFacts(info.packageName, PackageInfoCompat.getLongVersionCode(info), signers, lineage)
    }

    @Suppress("DEPRECATION")
    private fun oldSignatures(info: PackageInfo): List<Signature> = info.signatures.orEmpty().toList()

    private fun digest(signature: Signature): String = UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))
}
