// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.view.KeyEvent
import ca.gureet.plasma.core.data.SettingsStore
import ca.gureet.plasma.core.model.RemoteKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Learned keys at work (Settings › Remote & Buttons › Learn a Key): a key Plasma was taught
 * reaches every screen as the key it acts as. The key test and Learn a Key get keys as the remote
 * sends them.
 */
class RemoteKeyMap(settings: SettingsStore, scope: CoroutineScope) {
    /** Each learned key's id, and the key code it acts as. */
    val learned: StateFlow<Map<String, Int>> = settings.learnedKeys.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** True on the key test and Learn a Key, which need keys as the remote sends them. */
    @Volatile var asTheyCome = false

    fun translate(event: KeyEvent): KeyEvent {
        if (asTheyCome) return event
        val code = RemoteKeys.actsAs(learned.value, event.keyCode, event.scanCode) ?: return event
        return KeyEvent(event.downTime, event.eventTime, event.action, code, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source)
    }
}
