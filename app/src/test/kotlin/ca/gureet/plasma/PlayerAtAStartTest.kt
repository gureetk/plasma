// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The player, and libVLC with it, is made off the main thread once the first screen has what it
 * shows, or by a screen that plays as it opens (#127). `PlasmaApp.kt` is what every start composes,
 * whatever its first screen: anything in it that takes the graph's `player` makes the player there,
 * on the main thread, while the app is composed and the launch screen waits. No screen's own tests
 * can see that, so this reads the file: what's composed with the app takes the player only once
 * it's made (`playerIfMade`, `playerNow`).
 */
class PlayerAtAStartTest {
    @Test fun theAppsOwnCompositionTakesThePlayerOnlyOnceItsMade() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val app = File(root, "app/src/main/kotlin/ca/gureet/plasma/PlasmaApp.kt").readText()
        val lines = TAKES_THE_PLAYER.findAll(app).map { match -> app.substring(0, match.range.first).count { it == '\n' } + 1 }.toList()
        assertTrue("PlasmaApp.kt takes graph.player, which makes the player where it's composed, at line(s) $lines", lines.isEmpty())
    }

    private companion object {
        /** `graph.player` itself: `graph.playerIfMade` and `graph.playerNow()` don't make it. */
        val TAKES_THE_PLAYER = Regex("""graph\.player\b""")
    }
}
