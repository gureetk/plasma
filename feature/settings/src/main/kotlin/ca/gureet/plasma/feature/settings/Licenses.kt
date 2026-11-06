// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.R as DesignR

/**
 * What Plasma is made of (About › Open-Source Licenses, brief 5.4.12 and 6.1): each part's name,
 * its license, short and in full, and where its source code is, without the `https://`.
 */
internal class OpenSourcePart(@StringRes val name: Int, val license: License, val source: String)

/** The licenses the parts are under. */
internal enum class License(@StringRes val short: Int, @StringRes val full: Int) {
    Gpl3OrLater(R.string.license_gpl3_later_short, R.string.license_gpl3_later),
    Gpl3(R.string.license_gpl3_short, R.string.license_gpl3),
    Lgpl21OrLater(R.string.license_lgpl21_later_short, R.string.license_lgpl21_later),
    Lgpl3(R.string.license_lgpl3_short, R.string.license_lgpl3),
    Apache2(R.string.license_apache2_short, R.string.license_apache2),
    Mit(R.string.license_mit_short, R.string.license_mit),
    BouncyCastle(R.string.license_bouncy_castle_short, R.string.license_bouncy_castle),
    Ofl(R.string.license_ofl_short, R.string.license_ofl),
}

/**
 * Plasma, then what goes into it, as its build has them. libVLC is under the LGPL 2.1 or later;
 * the mpv build's FFmpeg is built as GPL 3 or later, so mpv and FFmpeg come under that together.
 */
internal val OPEN_SOURCE = listOf(
    OpenSourcePart(R.string.part_plasma, License.Gpl3OrLater, "github.com/gureetk/plasma"),
    OpenSourcePart(R.string.part_libvlc, License.Lgpl21OrLater, "code.videolan.org/videolan/libvlcjni"),
    // Plasma's own build, at the tag of the version in gradle/libs.versions.toml
    OpenSourcePart(R.string.part_mpv, License.Gpl3OrLater, "github.com/gureetk/plasma-libmpv/tree/v0.41.0-plasma.1"),
    OpenSourcePart(R.string.part_media3, License.Apache2, "github.com/androidx/media"),
    OpenSourcePart(R.string.part_androidx, License.Apache2, "android.googlesource.com/platform/frameworks/support"),
    OpenSourcePart(R.string.part_kotlin, License.Apache2, "github.com/JetBrains/kotlin"),
    OpenSourcePart(R.string.part_okhttp, License.Apache2, "github.com/square/okhttp"),
    OpenSourcePart(R.string.part_coil, License.Apache2, "github.com/coil-kt/coil"),
    OpenSourcePart(R.string.part_jellyfin, License.Lgpl3, "github.com/jellyfin/jellyfin-sdk-kotlin"),
    OpenSourcePart(R.string.part_smbj, License.Apache2, "github.com/hierynomus/smbj"),
    OpenSourcePart(R.string.part_mbassador, License.Mit, "github.com/bennidi/mbassador"),
    OpenSourcePart(R.string.part_bouncy_castle, License.BouncyCastle, "github.com/bcgit/bc-java"),
    OpenSourcePart(R.string.part_slf4j, License.Mit, "github.com/qos-ch/slf4j"),
    OpenSourcePart(R.string.part_kotlin_logging, License.Apache2, "github.com/oshai/kotlin-logging"),
    OpenSourcePart(R.string.part_spake2, License.Gpl3, "github.com/Flyfish233/spake2-java"),
    OpenSourcePart(R.string.part_elisabeth, License.Mit, "github.com/cryptography-cafe/curve25519-elisabeth"),
    OpenSourcePart(R.string.part_zxing, License.Apache2, "github.com/zxing/zxing"),
    OpenSourcePart(R.string.part_guava, License.Apache2, "github.com/google/guava"),
    OpenSourcePart(R.string.part_jspecify, License.Apache2, "github.com/jspecify/jspecify"),
    OpenSourcePart(R.string.part_accompanist, License.Apache2, "github.com/google/accompanist"),
    OpenSourcePart(R.string.part_instrument_sans, License.Ofl, "github.com/Instrument/instrument-sans"),
    OpenSourcePart(R.string.part_space_mono, License.Ofl, "github.com/googlefonts/spacemono"),
)

/**
 * Open-Source Licenses' sheet: every part with its license, and OK on one says it in full, with
 * where its source code is.
 */
@Composable
internal fun licensesMenu(): SettingAction.Menu {
    val close = stringResource(DesignR.string.key_close)
    val parts = OPEN_SOURCE.map { part ->
        val name = stringResource(part.name)
        MenuOption(
            name,
            value = stringResource(part.license.short),
            next = SettingAction.Menu(
                title = name,
                subtitle = stringResource(R.string.license_text, name, stringResource(part.license.full), part.source),
                options = listOf(MenuOption(close)),
            ),
        )
    }
    return SettingAction.Menu(title = stringResource(R.string.licenses), subtitle = stringResource(R.string.licenses_text), options = parts)
}
