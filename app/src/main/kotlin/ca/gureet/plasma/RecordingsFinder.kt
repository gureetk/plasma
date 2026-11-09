// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * Recordings that outlast Plasma (#161): Find Recordings, which asks Android to let Plasma see
 * videos first, and Android's own question before it deletes a recording's file that Plasma saved
 * before it was reinstalled.
 */

/**
 * Find Recordings, from Settings › Recording or an empty Recordings: Android is asked first to let
 * Plasma see videos, where it needs to be, and Plasma looks whatever the answer, for the
 * recordings it may see anyway.
 */
@Composable
fun rememberFindRecordings(graph: AppGraph): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { graph.findRecordings() }
    return remember(context, ask, graph) {
        {
            val permission = RecordingPlaces.findPermission(Build.VERSION.SDK_INT)
            if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) ask.launch(permission) else graph.findRecordings()
        }
    }
}

/**
 * Android's question before it deletes recordings' files that aren't Plasma's (#161): those it
 * saved before it was reinstalled and found again. Said no to, they stay on the drive, where Find
 * Recordings finds them again.
 */
@Composable
fun RecordingDeleteRequests(graph: AppGraph) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    LaunchedEffect(graph) {
        graph.deletesToAsk.collect { files ->
            try {
                val question = MediaStore.createDeleteRequest(context.contentResolver, files)
                ask.launch(IntentSenderRequest.Builder(question.intentSender).build())
            } catch (missing: ActivityNotFoundException) {
                // A TV without Android's question: they stay.
            } catch (refused: IllegalArgumentException) {
                // Not the media store's, after all.
            }
        }
    }
}
