// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's strings come from every module, merged by name: two modules that give the same name
 * different words would show one module's words on the other's screen. Each module's own
 * screenshots can't see it, so this looks across them.
 */
class ResourceNamesTest {
    @Test fun noTwoModulesGiveANameDifferentWords() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val found = mutableMapOf<String, MutableMap<String, String>>()
        root.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "xml" && it.parentFile.name == "values" && it.path.contains("${File.separator}src${File.separator}main${File.separator}res${File.separator}") }
            .forEach { file ->
                val module = file.relativeTo(root).path.substringBefore("${File.separator}src${File.separator}")
                ENTRY.findAll(file.readText()).forEach { match ->
                    val (kind, name, words) = match.destructured
                    found.getOrPut("$kind $name") { mutableMapOf() }[module] = words.replace(Regex("\\s+"), " ").trim()
                }
            }
        val clashes = found.filter { (_, byModule) -> byModule.values.toSet().size > 1 }
        assertTrue("The same name with different words: $clashes", clashes.isEmpty())
    }

    private companion object {
        val ENTRY = Regex("""<(string|plurals|string-array) name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    }
}
