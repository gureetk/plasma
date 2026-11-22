// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ca.gureet.plasma.R

/**
 * Search's mic key (brief 5.10, #120): Android's speech recognizer, on its own screen, which
 * records and recognizes by itself, so Plasma needs no microphone permission. What it heard goes
 * to the listener the key was pressed with. Null on a TV without a recognizer, whose keyboard then
 * has no mic key.
 */
@Composable
internal fun rememberVoiceInput(): ((onHeard: (String) -> Unit) -> Unit)? {
    val context = LocalContext.current
    val available = remember(context) { speechIntent(context).resolveActivity(context.packageManager) != null }
    var listener by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) listener?.invoke(heard)
        listener = null
    }
    if (!available) return null
    return remember(recognizer, context) {
        { onHeard ->
            listener = onHeard
            try {
                recognizer.launch(speechIntent(context))
            } catch (gone: ActivityNotFoundException) {
                // Uninstalled or turned off since Plasma looked: the key does nothing this time.
                listener = null
            }
        }
    }
}

/** What Search asks the recognizer for: one result, for a search, with the prompt it shows. */
private fun speechIntent(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
