// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A value that goes to its target as `animate*AsState`'s does, frame for frame, with nothing set
 * up for it until its target first changes (#127).
 *
 * `animate*AsState` makes an animation, a channel and a coroutine for a value when it's first
 * composed, and wakes that coroutine at every recomposition after, to find the target the same.
 * Everything that can take focus has three such values and most never do: a screen of tiles, rows
 * or Guide cells made all of that for each one, and went through it again for each that was
 * composed again in place. Here a value at rest is its target, in one small object. The animation
 * is made, and a coroutine started, when the target changes.
 *
 * From then on it's the same `Animatable.animateTo` with the same spec, started at the same
 * moment: the value first moves in the frame after the one that brought the new target, and a
 * change of mind part way starts again from where the value is.
 */
@Stable
internal class Following<T, V : AnimationVector>(
    initial: T,
    private val converter: TwoWayConverter<T, V>,
    private val visibilityThreshold: T?,
) : State<T> {
    /** Made when the target first changes. Until then this is all there is to watch. */
    private var moving: Animatable<T, V>? by mutableStateOf(null)

    /** Where it's headed, or where it is while nothing has moved it. */
    private var target: T = initial

    override val value: T get() = moving?.value ?: target

    /** Whether it's at [target] or already on its way there. */
    fun headsFor(target: T): Boolean = target == this.target

    /** Whether anything has been set up for it yet. */
    val setUp: Boolean get() = Snapshot.withoutReadObservation { moving != null }

    /** Sends it to [target] by [spec], from where it is, in [scope]. */
    fun follow(target: T, spec: AnimationSpec<T>, scope: CoroutineScope) {
        if (target == this.target) return
        val from = moving ?: Animatable(this.target, converter, visibilityThreshold).also { moving = it }
        this.target = target
        scope.launch { from.animateTo(target, spec) }
    }
}

/**
 * Tells [this] of its [target] once this composition is applied, as `animate*AsState` does, and
 * only when it's a new one.
 */
@Composable
private fun <T, V : AnimationVector> Following<T, V>.toward(target: T, spec: AnimationSpec<T>, scope: CoroutineScope): State<T> {
    if (!headsFor(target)) SideEffect { follow(target, spec, scope) }
    return this
}

/**
 * `animateFloatAsState`, with nothing set up until [target] first changes ([Following]). Read it
 * where it's used, as with any state. Give several values of one composable the same [scope]
 * (`rememberCoroutineScope()`); it's what their animations run in, and nothing does until then.
 */
@Composable
fun followFloatAsState(target: Float, spec: AnimationSpec<Float>, scope: CoroutineScope = rememberCoroutineScope()): State<Float> =
    remember { Following(target, Float.VectorConverter, FLOAT_VISIBILITY) }.toward(target, spec, scope)

/** `animateDpAsState`, with nothing set up until [target] first changes: see [followFloatAsState]. */
@Composable
fun followDpAsState(target: Dp, spec: AnimationSpec<Dp>, scope: CoroutineScope = rememberCoroutineScope()): State<Dp> =
    remember { Following(target, Dp.VectorConverter, null) }.toward(target, spec, scope)

/** `animateColorAsState`, with nothing set up until [target] first changes: see [followFloatAsState]. */
@Composable
fun followColorAsState(target: Color, spec: AnimationSpec<Color>, scope: CoroutineScope = rememberCoroutineScope()): State<Color> =
    remember { Following(target, Color.VectorConverter(target.colorSpace), null) }.toward(target, spec, scope)

/** `animateFloatAsState`'s own: what a spring takes as arrived. A tween doesn't use it. */
private const val FLOAT_VISIBILITY = 0.01f
