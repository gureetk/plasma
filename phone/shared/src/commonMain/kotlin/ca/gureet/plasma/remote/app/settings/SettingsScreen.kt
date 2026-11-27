// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.pairing.Pill
import ca.gureet.plasma.remote.app.resources.Res
import ca.gureet.plasma.remote.app.resources.about
import ca.gureet.plasma.remote.app.resources.about_licence
import ca.gureet.plasma.remote.app.resources.about_version
import ca.gureet.plasma.remote.app.resources.cancel
import ca.gureet.plasma.remote.app.resources.component_camera
import ca.gureet.plasma.remote.app.resources.component_font
import ca.gureet.plasma.remote.app.resources.component_kotlin
import ca.gureet.plasma.remote.app.resources.component_okhttp
import ca.gureet.plasma.remote.app.resources.done
import ca.gureet.plasma.remote.app.resources.forget
import ca.gureet.plasma.remote.app.resources.forget_text
import ca.gureet.plasma.remote.app.resources.haptics
import ca.gureet.plasma.remote.app.resources.haptics_text
import ca.gureet.plasma.remote.app.resources.keep_screen_on
import ca.gureet.plasma.remote.app.resources.keep_screen_on_text
import ca.gureet.plasma.remote.app.resources.license_apache
import ca.gureet.plasma.remote.app.resources.license_ofl
import ca.gureet.plasma.remote.app.resources.licenses
import ca.gureet.plasma.remote.app.resources.no_paired_tvs
import ca.gureet.plasma.remote.app.resources.off
import ca.gureet.plasma.remote.app.resources.on
import ca.gureet.plasma.remote.app.resources.paired_tvs
import ca.gureet.plasma.remote.app.resources.remote_section
import ca.gureet.plasma.remote.app.resources.settings
import ca.gureet.plasma.remote.app.resources.source_code
import ca.gureet.plasma.remote.app.resources.try_demo
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.ui.RemoteColors
import ca.gureet.plasma.remote.app.ui.pressedLook
import ca.gureet.plasma.remote.app.ui.rememberPressed
import ca.gureet.plasma.remote.app.ui.tap
import ca.gureet.plasma.remote.app.ui.textStyle
import org.jetbrains.compose.resources.stringResource

/**
 * Settings (brief 5.22): Haptics and Keep Screen On, the paired TVs with Forget, Try the Demo, and
 * About with the licenses. Volume Buttons and Whole-TV Control aren't offered yet.
 */
@Composable
fun SettingsScreen(settings: PhoneSettings, paired: List<PairedTv>, version: String, onForget: (PairedTv) -> Unit, onDemo: () -> Unit, onDone: () -> Unit) {
    var forgetting by remember { mutableStateOf<String?>(null) }
    val links = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(Res.string.settings), Modifier.weight(1f), style = textStyle(22.sp, FontWeight.SemiBold))
            Pill(stringResource(Res.string.done), RemoteColors.Text, RemoteColors.Bg, onTap = onDone)
        }

        Heading(stringResource(Res.string.remote_section))
        Group {
            Toggle(stringResource(Res.string.haptics), stringResource(Res.string.haptics_text), settings.haptics, settings::haptics)
            Line()
            Toggle(stringResource(Res.string.keep_screen_on), stringResource(Res.string.keep_screen_on_text), settings.keepScreenOn, settings::keepScreenOn)
        }

        Heading(stringResource(Res.string.paired_tvs))
        Group {
            if (paired.isEmpty()) {
                BasicText(stringResource(Res.string.no_paired_tvs), Modifier.padding(14.dp), style = textStyle(15.sp, color = RemoteColors.Text2))
            }
            paired.forEachIndexed { at, tv ->
                if (at > 0) Line()
                if (forgetting == tv.id) {
                    Column(Modifier.padding(14.dp)) {
                        BasicText(stringResource(Res.string.forget_text, tv.name), style = textStyle(14.sp, color = RemoteColors.Text2).copy(lineHeight = 19.sp))
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(stringResource(Res.string.cancel), RemoteColors.Button, RemoteColors.Text) { forgetting = null }
                            Spacer(Modifier.weight(1f))
                            Pill(stringResource(Res.string.forget), RemoteColors.Red, RemoteColors.Text) {
                                forgetting = null
                                onForget(tv)
                            }
                        }
                    }
                } else {
                    Row(Modifier.heightIn(min = 60.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(tv.name, style = textStyle(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(tv.host, style = textStyle(12.5.sp, color = RemoteColors.Text3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Pill(stringResource(Res.string.forget), RemoteColors.Button, RemoteColors.Text) { forgetting = tv.id }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Group { Link(stringResource(Res.string.try_demo), onTap = onDemo) }

        Heading(stringResource(Res.string.about))
        Group {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(stringResource(Res.string.about_version, version), style = textStyle(15.sp, FontWeight.SemiBold))
                BasicText(stringResource(Res.string.about_licence), style = textStyle(14.sp, color = RemoteColors.Text2))
            }
            Line()
            Link(stringResource(Res.string.source_code)) { links.openUri(SOURCE) }
        }

        Heading(stringResource(Res.string.licenses))
        Group {
            val apache = stringResource(Res.string.license_apache)
            listOf(Res.string.component_kotlin, Res.string.component_okhttp, Res.string.component_camera).forEachIndexed { at, component ->
                if (at > 0) Line()
                License(stringResource(component), apache) { links.openUri(APACHE) }
            }
            Line()
            License(stringResource(Res.string.component_font), stringResource(Res.string.license_ofl)) { links.openUri(OFL) }
        }
    }
}

@Composable
private fun Heading(text: String) = BasicText(text, Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), style = textStyle(13.sp, color = RemoteColors.Text3))

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RemoteColors.Surface1), content = content)

@Composable
private fun Line() = Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(RemoteColors.Line))

/** A setting that's on or off: its name, a line on what it does, and a switch. The whole row flips it. */
@Composable
private fun Toggle(name: String, text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val pressed = rememberPressed()
    val state = stringResource(if (on) Res.string.on else Res.string.off)
    Row(
        Modifier.fillMaxWidth().pressedLook(pressed).tap(name, pressed = pressed) { onChange(!on) }
            .semantics {
                role = Role.Switch
                stateDescription = state
            }
            .heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(name, style = textStyle(15.sp, FontWeight.SemiBold))
            BasicText(text, style = textStyle(13.sp, color = RemoteColors.Text3))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(width = 46.dp, height = 28.dp).clip(CircleShape).background(if (on) RemoteColors.Ok else RemoteColors.Track).padding(3.dp)) {
            Box(Modifier.offset(x = if (on) 18.dp else 0.dp).size(22.dp).clip(CircleShape).background(RemoteColors.Text))
        }
    }
}

@Composable
private fun Link(text: String, onTap: () -> Unit) {
    val pressed = rememberPressed()
    Box(Modifier.fillMaxWidth().pressedLook(pressed).tap(text, pressed = pressed, action = onTap).heightIn(min = 52.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        BasicText(text, style = textStyle(15.sp, FontWeight.SemiBold))
    }
}

/** A component and its license, which opens the license's text. */
@Composable
private fun License(component: String, license: String, onTap: () -> Unit) {
    val pressed = rememberPressed()
    Column(Modifier.fillMaxWidth().pressedLook(pressed).tap("$component, $license", pressed = pressed, action = onTap).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(component, style = textStyle(15.sp))
        BasicText(license, style = textStyle(13.sp, color = RemoteColors.Text3))
    }
}

private const val SOURCE = "https://github.com/gureetk/plasma/tree/main/phone"
private const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"
private const val OFL = "https://openfontlicense.org"
