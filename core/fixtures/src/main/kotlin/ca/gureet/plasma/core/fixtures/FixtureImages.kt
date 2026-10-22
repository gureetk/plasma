// SPDX-License-Identifier: GPL-3.0-or-later
// Generated from core/fixtures/src/main/res/drawable-nodpi (fx_*). Re-run tools/gen_fixture_images.sh after adding art.
package ca.gureet.plasma.core.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import ca.gureet.plasma.core.design.component.ImageResolver

/** Sample art from the design hand-off (appendix B), addressed as `fixture://name`. Made up; never ship as branding. */
object FixtureImages {
    const val SCHEME = "fixture://"

    private val ids: Map<String, Int> = mapOf(
        "app_brightside" to R.drawable.fx_app_brightside,
        "app_cloudnest" to R.drawable.fx_app_cloudnest,
        "app_driftwood" to R.drawable.fx_app_driftwood,
        "app_fileport" to R.drawable.fx_app_fileport,
        "app_kinetic" to R.drawable.fx_app_kinetic,
        "app_lantern" to R.drawable.fx_app_lantern,
        "app_pingmeter" to R.drawable.fx_app_pingmeter,
        "app_quarry" to R.drawable.fx_app_quarry,
        "app_reelbox" to R.drawable.fx_app_reelbox,
        "app_shieldline" to R.drawable.fx_app_shieldline,
        "app_snapshot" to R.drawable.fx_app_snapshot,
        "app_sonar" to R.drawable.fx_app_sonar,
        "app_tumble" to R.drawable.fx_app_tumble,
        "aurora2" to R.drawable.fx_aurora2,
        "city2" to R.drawable.fx_city2,
        "concert2" to R.drawable.fx_concert2,
        "ep_atlas_s2e1" to R.drawable.fx_ep_atlas_s2e1,
        "ep_atlas_s2e2" to R.drawable.fx_ep_atlas_s2e2,
        "ep_atlas_s2e3" to R.drawable.fx_ep_atlas_s2e3,
        "ep_atlas_s2e4" to R.drawable.fx_ep_atlas_s2e4,
        "ep_atlas_s2e5" to R.drawable.fx_ep_atlas_s2e5,
        "ep_atlas_s2e6" to R.drawable.fx_ep_atlas_s2e6,
        "ep_atlas_s2e7" to R.drawable.fx_ep_atlas_s2e7,
        "ep_atlas_s2e8" to R.drawable.fx_ep_atlas_s2e8,
        "lighthouse3" to R.drawable.fx_lighthouse3,
        "logo_arena1" to R.drawable.fx_logo_arena1,
        "logo_arena2" to R.drawable.fx_logo_arena2,
        "logo_arenaextra" to R.drawable.fx_logo_arenaextra,
        "logo_arenanews" to R.drawable.fx_logo_arenanews,
        "logo_atlas" to R.drawable.fx_logo_atlas,
        "logo_cinemaprime" to R.drawable.fx_logo_cinemaprime,
        "logo_combat" to R.drawable.fx_logo_combat,
        "logo_courtside" to R.drawable.fx_logo_courtside,
        "logo_fairway" to R.drawable.fx_logo_fairway,
        "logo_meridian" to R.drawable.fx_logo_meridian,
        "logo_northline" to R.drawable.fx_logo_northline,
        "logo_orbit" to R.drawable.fx_logo_orbit,
        "logo_pulse" to R.drawable.fx_logo_pulse,
        "logo_velocity" to R.drawable.fx_logo_velocity,
        "logo_wildframe" to R.drawable.fx_logo_wildframe,
        "motor2" to R.drawable.fx_motor2,
        "orbit2" to R.drawable.fx_orbit2,
        "pair_qr_adb" to R.drawable.fx_pair_qr_adb,
        "pair_qr_local" to R.drawable.fx_pair_qr_local,
        "pair_qr_remote" to R.drawable.fx_pair_qr_remote,
        "poster_carto" to R.drawable.fx_poster_carto,
        "poster_ember" to R.drawable.fx_poster_ember,
        "poster_kite" to R.drawable.fx_poster_kite,
        "poster_lighthouse" to R.drawable.fx_poster_lighthouse,
        "poster_loworbit" to R.drawable.fx_poster_loworbit,
        "poster_neon" to R.drawable.fx_poster_neon,
        "poster_nightdesk" to R.drawable.fx_poster_nightdesk,
        "poster_orchard" to R.drawable.fx_poster_orchard,
        "poster_paper" to R.drawable.fx_poster_paper,
        "poster_polar" to R.drawable.fx_poster_polar,
        "poster_salt" to R.drawable.fx_poster_salt,
        "poster_velvet" to R.drawable.fx_poster_velvet,
        "series_atlas" to R.drawable.fx_series_atlas,
        "series_backstage" to R.drawable.fx_series_backstage,
        "series_harbour" to R.drawable.fx_series_harbour,
        "series_heatindex" to R.drawable.fx_series_heatindex,
        "series_kingsway" to R.drawable.fx_series_kingsway,
        "series_lastorders" to R.drawable.fx_series_lastorders,
        "series_nightstint" to R.drawable.fx_series_nightstint,
        "series_northof" to R.drawable.fx_series_northof,
        "series_panel" to R.drawable.fx_series_panel,
        "series_quiethours" to R.drawable.fx_series_quiethours,
        "series_smallclaims" to R.drawable.fx_series_smallclaims,
        "series_tumble" to R.drawable.fx_series_tumble,
        "stadium2" to R.drawable.fx_stadium2,
        "titleart_atlas" to R.drawable.fx_titleart_atlas,
        "titleart_lighthouse" to R.drawable.fx_titleart_lighthouse,
    )

    fun id(name: String): Int? = ids[name]

    /** Resolves `fixture://name` to the bundled drawable. */
    val resolver = ImageResolver { model ->
        val id = if (model.startsWith(SCHEME)) ids[model.removePrefix(SCHEME)] else null
        if (id != null) painterResource(id) else null
    }
}

/** `fixture://name`, for sample data. */
fun fx(name: String): String = FixtureImages.SCHEME + name
