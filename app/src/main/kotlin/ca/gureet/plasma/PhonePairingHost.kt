// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.os.Build
import android.provider.Settings
import ca.gureet.plasma.feature.settings.PhonePairing
import ca.gureet.plasma.feature.settings.SentFromPhone
import ca.gureet.plasma.pairing.LanAddress
import ca.gureet.plasma.pairing.PairingServer
import ca.gureet.plasma.pairing.PhonePageTexts
import ca.gureet.plasma.pairing.PhoneSubmission
import ca.gureet.plasma.pairing.SourceKind
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ca.gureet.plasma.feature.settings.R as SettingsR
import ca.gureet.plasma.feature.settings.SourceType as AddSourceType

/**
 * Add a Source's phone pairing (brief 5.15): Plasma's local server, from when the screen opens
 * until it closes, and what a phone sends it.
 */
class PhonePairingHost(private val scope: CoroutineScope, private val texts: PhonePageTexts) {
    private val _pairing = MutableStateFlow<PhonePairing>(PhonePairing.Starting)

    /** What the card on Add a Source shows. */
    val pairing: StateFlow<PhonePairing> = _pairing.asStateFlow()

    private val _sent = MutableStateFlow<SentFromPhone?>(null)

    /** The latest source a phone sent. */
    val sent: StateFlow<SentFromPhone?> = _sent.asStateFlow()

    private val sendings = AtomicLong()
    private var job: Job? = null

    @Volatile private var server: PairingServer? = null

    @Volatile private var type: AddSourceType = AddSourceType.Xtream

    /** Opens the local server on the TV's home network address, if it has one. */
    fun start() {
        if (job != null) return
        _pairing.value = PhonePairing.Starting
        job = scope.launch {
            val host = this
            val server = PairingServer(texts, onSubmission = ::received).also { it.kind = kindOf(type) }
            try {
                val started = withContext(Dispatchers.IO) {
                    val address = LanAddress.find() ?: return@withContext false
                    try {
                        server.start(address, host)
                        true
                    } catch (failed: IOException) {
                        false
                    }
                }
                if (!started) {
                    _pairing.value = PhonePairing.Unavailable
                    return@launch
                }
                this@PhonePairingHost.server = server
                server.invite.collect { invite ->
                    _pairing.value = invite?.let { PhonePairing.Ready(it.address, it.code, it.qr) } ?: PhonePairing.Unavailable
                }
            } finally {
                this@PhonePairingHost.server = null
                server.close()
            }
        }
    }

    /** The form changed kind: a phone's form starts on the same one. */
    fun setType(type: AddSourceType) {
        this.type = type
        server?.kind = kindOf(type)
    }

    /** Add a Source closed: the server stops answering (brief 5.15). */
    fun close() {
        job?.cancel()
        job = null
    }

    private fun received(submission: PhoneSubmission) {
        val type = when (submission.kind) {
            SourceKind.Stalker -> AddSourceType.Stalker
            SourceKind.Xtream -> AddSourceType.Xtream
            SourceKind.M3u -> AddSourceType.M3U
        }
        _sent.value = SentFromPhone(sendings.incrementAndGet(), type, submission.fields.mapKeys { (field, _) -> field.key })
    }

    private fun kindOf(type: AddSourceType): SourceKind = when (type) {
        AddSourceType.Stalker -> SourceKind.Stalker
        AddSourceType.Xtream -> SourceKind.Xtream
        AddSourceType.M3U -> SourceKind.M3u
    }

    companion object {
        /** The phone pages' words, from the same strings as Add a Source. */
        fun texts(context: Context): PhonePageTexts {
            val tv = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
            fun text(id: Int) = context.getString(id)
            return PhonePageTexts(
                tvName = tv,
                title = text(SettingsR.string.add_a_source),
                stalker = text(SettingsR.string.tab_stalker),
                xtream = text(SettingsR.string.tab_xtream),
                m3u = text(SettingsR.string.tab_m3u),
                fieldName = text(SettingsR.string.field_name),
                fieldPortal = text(SettingsR.string.field_portal),
                fieldMac = text(SettingsR.string.field_mac),
                macHint = text(SettingsR.string.phone_mac_hint),
                fieldServer = text(SettingsR.string.field_server),
                fieldUsername = text(SettingsR.string.field_username),
                fieldPassword = text(SettingsR.string.field_password),
                fieldPlaylist = text(SettingsR.string.field_playlist),
                fieldGuide = text(SettingsR.string.field_guide),
                send = context.getString(SettingsR.string.phone_send, tv),
                codeTitle = text(SettingsR.string.phone_code_title_add),
                codeText = text(SettingsR.string.phone_code_text_add),
                codeLabel = text(SettingsR.string.phone_code_label),
                next = text(SettingsR.string.phone_continue),
                wrongCode = text(SettingsR.string.phone_wrong_code),
                tooManyTries = text(SettingsR.string.phone_too_many_tries),
                missingAddress = text(SettingsR.string.phone_missing_field),
                sentTitle = context.getString(SettingsR.string.phone_sent_title, tv),
                sentText = text(SettingsR.string.phone_sent_text),
                notFound = text(SettingsR.string.phone_not_found),
                note = text(SettingsR.string.phone_note),
            )
        }

        /** The phone pages' words for Replace Google TV Home's pairing code (brief 4.24). */
        fun debugTexts(context: Context): PhonePageTexts {
            val tv = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
            fun text(id: Int) = context.getString(id)
            return texts(context).copy(
                codeText = text(SettingsR.string.phone_code_text_debug),
                debugTitle = context.getString(SettingsR.string.phone_debug_title, tv),
                debugText = text(SettingsR.string.phone_debug_text),
                fieldDebugCode = text(SettingsR.string.phone_debug_code),
                fieldDebugPort = text(SettingsR.string.phone_debug_port),
                debugPortHint = text(SettingsR.string.phone_debug_port_hint),
                debugSend = text(SettingsR.string.phone_debug_send),
                debugSentTitle = context.getString(SettingsR.string.phone_sent_title, tv),
                debugSentText = text(SettingsR.string.phone_debug_sent_text),
                debugMissing = text(SettingsR.string.phone_debug_missing),
            )
        }
    }
}
