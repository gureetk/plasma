// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.outline
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/** "This TV" over the list on Home Screen setup (board 23): the device, and what Home does now. */
@Composable
internal fun ThisTvCard(label: String, device: String, status: Status) {
    Column(
        Modifier
            .fillMaxWidth()
            .outline(1.5f.px, PlasmaColors.white(0.1f), PlasmaShapes.Card)
            .padding(horizontal = 26.px, vertical = 22.px),
        verticalArrangement = Arrangement.spacedBy(6.px),
    ) {
        PlasmaText(label, plasmaStyle(20f, 600), color = PlasmaColors.Text3)
        PlasmaText(device, plasmaStyle(25f, 500))
        StatusLine(status, weight = 500)
    }
}

/** A point for or against a choice: a green tick, or an amber caution. */
@Immutable
internal class CheckItem(val good: Boolean, val text: String)

/** Ticks and cautions under a description (board 23): 22 px, 620 wide. */
@Composable
internal fun CheckList(items: List<CheckItem>, top: Dp = 30.px) {
    Column(Modifier.padding(top = top).width(620.px), verticalArrangement = Arrangement.spacedBy(16.px)) {
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.px)) {
                PlasmaIcon(
                    if (item.good) PlasmaIcons.Check else PlasmaIcons.Caution,
                    24.px,
                    Modifier.padding(top = 2.px),
                    tint = if (item.good) PlasmaColors.Ok else PlasmaColors.Warn,
                    stroke = if (item.good) 2.4f else 2.2f,
                )
                PlasmaText(item.text, plasmaStyle(22f, 500, lineHeight = 1.35f), color = PlasmaColors.TextSoft3, maxLines = 3)
            }
        }
    }
}

/** A QR code on white, as phones scan it: [size] across, with a quiet zone of [padding]. */
@Composable
internal fun QrCode(image: String, size: Dp, padding: Dp, radius: Dp, modifier: Modifier = Modifier) {
    Box(modifier.background(PlasmaColors.white(1f), RoundedCornerShape(radius)).padding(padding)) {
        PlasmaImage(image, Modifier.size(size), contentScale = ContentScale.Fit, placeholder = PlasmaColors.white(1f))
    }
}

/**
 * A line of text with one part picked out in [highlight], such as "Or open **192.168.1.42:8484**
 * on your phone": [template] holds `%1$s` where [part] goes.
 */
internal fun highlighted(template: String, part: String, highlight: SpanStyle) = buildAnnotatedString {
    val at = template.indexOf(PLACEHOLDER)
    if (at < 0) {
        append(template)
    } else {
        append(template.substring(0, at))
        withStyle(highlight) { append(part) }
        append(template.substring(at + PLACEHOLDER.length))
    }
}

private const val PLACEHOLDER = "%1\$s"

/** "Or open **192.168.1.42:8484** on your phone": the address in white, the rest in `text2`. */
@Composable
internal fun AddressLine(template: String, address: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    PlasmaText(
        highlighted(template, address, SpanStyle(color = PlasmaColors.Text, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")),
        plasmaStyle(21f, 500),
        modifier,
        color = PlasmaColors.Text2,
        align = align,
        maxLines = 2,
    )
}

/**
 * The phone card on the right of Add a Source and Replace Google TV Home (boards 21 and 24): a
 * phone, a heading, what to do, the QR code, and the address to type instead.
 */
@Composable
internal fun PhoneCard(
    title: String,
    text: String,
    qr: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 36.px,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .width(588.px)
            .background(PlasmaColors.Surface1, RoundedCornerShape(22.px))
            .padding(start = 40.px, end = 40.px, top = 38.px, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.px).background(PlasmaColors.white(0.08f), RoundedCornerShape(16.px)), contentAlignment = Alignment.Center) {
            PlasmaIcon(PlasmaIcons.Phone, 32.px, tint = PlasmaColors.TextSoft3, stroke = 1.8f)
        }
        PlasmaText(title, plasmaStyle(30f, 600, trackingPx = -0.3f), Modifier.padding(top = 22.px), align = TextAlign.Center)
        PlasmaText(
            text,
            plasmaStyle(21f, 400, lineHeight = 1.4f),
            Modifier.padding(top = 10.px),
            color = PlasmaColors.Text2,
            align = TextAlign.Center,
            maxLines = 4,
        )
        QrCode(qr, 264.px, 18.px, 18.px, Modifier.padding(top = 28.px))
        content()
    }
}

/** The small print at the bottom of a [PhoneCard], under a hairline. */
@Composable
internal fun PhoneCardNote(text: String) {
    Box(Modifier.padding(top = 26.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
    PlasmaText(
        text,
        plasmaStyle(19f, 500, lineHeight = 1.45f),
        Modifier.padding(top = 22.px),
        color = PlasmaColors.Text3,
        align = TextAlign.Center,
        maxLines = 4,
    )
}

/** A dot and a line of status, in the status's color (board 24's "● Waiting for the code"). */
@Composable
internal fun StatusDotLine(text: String, tone: Tone, sizePx: Float = 20f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
        Box(
            Modifier.size(10.px).background(
                when (tone) {
                    Tone.Ok -> PlasmaColors.Ok
                    Tone.Idle -> PlasmaColors.Text4
                    Tone.Warn -> PlasmaColors.Warn
                },
                CircleShape,
            ),
        )
        PlasmaText(text, plasmaStyle(sizePx, 500), color = PlasmaColors.Text2)
    }
}
