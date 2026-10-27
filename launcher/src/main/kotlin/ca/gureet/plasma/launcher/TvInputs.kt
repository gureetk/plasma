// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** One of the TV's inputs (brief 4.4, Inputs): an HDMI port, by the name the TV gives it. */
data class TvInput(val id: String, val label: String)

/**
 * The TV's own inputs, from `TvInputManager` (brief 4.4 and 5.13, "Inputs"). A box has none, so
 * its Inputs tile doesn't show.
 */
class TvInputs(private val context: Context) {
    /** The inputs a source can be plugged into (HDMI, composite…), as the TV names them. */
    fun list(): List<TvInput> {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            manager.tvInputList.filter { it.isPassthroughInput && !it.isHidden(context) }.map { input -> TvInput(input.id, nameOf(input)) }
        } catch (refused: SecurityException) {
            emptyList()
        }
    }

    /** Switches the TV to the input with [id], in the TV's own app for its inputs. */
    fun open(id: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (refused: RuntimeException) {
        false
    }

    /** The name the user gave it ("Game console"), else the TV's ("HDMI 2"). */
    private fun nameOf(input: TvInputInfo): String =
        (input.loadCustomLabel(context)?.takeIf { it.isNotBlank() } ?: input.loadLabel(context)).toString()
}
