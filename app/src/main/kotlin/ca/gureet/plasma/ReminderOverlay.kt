// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ca.gureet.plasma.core.data.Reminder
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.component.LocalImageResolver
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.ReminderToast
import ca.gureet.plasma.core.design.component.rememberNow
import ca.gureet.plasma.live.reminderInfo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Reminders over other apps (brief 5.7): when a reminder comes due while another app is in front,
 * its toast shows over that app, where Android lets Plasma "display over other apps". Switch Now
 * brings Plasma back on the channel; Dismiss or Back puts the toast away. Plasma's own screens
 * show it themselves.
 */
class ReminderOverlay(private val context: Context, private val graph: AppGraph) {
    private val window by lazy { OverlayWindow(context) }

    /** Whether Android lets Plasma show the toast over other apps. */
    fun allowed(): Boolean = Settings.canDrawOverlays(context)

    fun start() {
        graph.scope.launch {
            combine(graph.reminderClock.due, graph.standby.inFrontNow) { due, inFront -> due.takeUnless { inFront } }
                .distinctUntilChanged()
                .collect { due -> if (due != null && allowed()) window.show { Toast(due) } else window.hide() }
        }
    }

    @Composable
    private fun Toast(due: Reminder) {
        val images = remember(graph) { AppImages(graph.images) }
        PlasmaTheme {
            CompositionLocalProvider(LocalPlasmaClock provides graph.clock, LocalImageResolver provides images) {
                val world by graph.world.collectAsState()
                val now by rememberNow()
                val reminder = world?.let { reminderInfo(it, due) } ?: return@CompositionLocalProvider
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            // Back puts it away, as in Plasma.
                            if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                                graph.reminderClock.done(due.id)
                                true
                            } else {
                                event.key == Key.Back
                            }
                        },
                ) {
                    ReminderToast(
                        reminder = reminder,
                        now = now.toInstant(),
                        onSwitch = {
                            graph.reminderClock.done(due.id)
                            context.startActivity(watchIntent(context, reminder.channelId))
                        },
                        onDismiss = { graph.reminderClock.done(due.id) },
                    )
                }
            }
        }
    }

    companion object {
        private const val ACTION_WATCH = "ca.gureet.plasma.action.WATCH"
        private const val EXTRA_CHANNEL = "channel"

        /** Plasma, opened on [channelId] full screen. */
        fun watchIntent(context: Context, channelId: String): Intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_WATCH)
            .putExtra(EXTRA_CHANNEL, channelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** The channel [intent] asks Plasma to open, if it's a reminder's Switch Now. */
        fun channelOf(intent: Intent?): String? = intent?.takeIf { it.action == ACTION_WATCH }?.getStringExtra(EXTRA_CHANNEL)
    }
}

/**
 * A window over other apps (`TYPE_APPLICATION_OVERLAY`) for Compose: the whole screen, see-through
 * but for what's drawn in it, taking the remote's keys while it's up.
 */
private class OverlayWindow(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun show(content: @Composable () -> Unit) {
        view?.let {
            it.setContent(content)
            return
        }
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            saved.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setContent(content)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windows.addView(compose, params)
        } catch (refused: RuntimeException) {
            // Android took back "display over other apps" in the meantime.
            return
        }
        view = compose
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun hide() {
        val shown = view ?: return
        view = null
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        try {
            windows.removeView(shown)
        } catch (gone: IllegalArgumentException) {
            // Already gone with its window.
        }
    }
}
