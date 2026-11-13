// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.editor

import ca.gureet.plasma.core.model.CategoryKind
import ca.gureet.plasma.core.model.CategoryOrder
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A stand-in TV, for trying Edit in a Browser's page without one: the real server and page, on
 * the boards' made-up channels kept in memory. It runs only when asked, for 45 minutes or until
 * `/tmp/editor-standin.stop` exists:
 *
 * ```
 * EDITOR_STAND_IN=1 ./gradlew :editor:test --tests '*EditorStandInTest*' --rerun
 * ```
 *
 * Then open `http://127.0.0.1:8484/edit` and type the code in `/tmp/editor-standin.txt`. What the
 * stand-in holds is written to `/tmp/editor-standin.state` as it changes, and creating
 * `/tmp/editor-standin.end` ends every browser's editing, as End Editing on the TV does. The
 * parental PIN is 1234.
 */
class EditorStandInTest {
    @Test
    fun standIn() {
        assumeTrue(System.getenv("EDITOR_STAND_IN") != null)
        val words = Regex("""<(string|plurals) name="editor_page_([a-z_]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("../app/src/main/res/values/strings_edit_in_browser.xml").readText())
            .associate { match ->
                val (kind, name, body) = match.destructured
                val key = name.split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }.joinToString("")
                val text = if (kind == "plurals") Regex("""<item quantity="[a-z]+">(.*?)</item>""").findAll(body).joinToString("|") { it.groupValues[1] } else body
                key to text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            } + ("tv" to "Living Room TV")
        val backend = StandIn(File("../core/fixtures/src/main/res/drawable-nodpi"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = EditorServer(backend, EditorPages(words), allows = { true }, idleMs = (System.getenv("EDITOR_IDLE_MS") ?: "900000").toLong())
        server.start(InetAddress.getByName("127.0.0.1"), scope, port = 8484)
        server.openJoining()
        val until = System.currentTimeMillis() + 45 * 60_000
        val stop = File("/tmp/editor-standin.stop")
        while (System.currentTimeMillis() < until && !stop.exists()) {
            File("/tmp/editor-standin.txt").writeText(server.invite.value?.let { "${it.address} ${it.code}\n" } ?: "")
            runCatching { File("/tmp/editor-standin.state").writeText(backend.dump()) }
            if (File("/tmp/editor-standin.end").exists()) {
                File("/tmp/editor-standin.end").delete()
                server.endAll()
            }
            Thread.sleep(200)
        }
        server.close()
    }
}

/** The boards' made-up world, with enough channels to scroll. */
private class StandIn(private val logos: File) : EditorBackend {
    @Volatile override var revision = 1L

    class Ch(val id: String, val n: Int, val name: String, val group: String, val source: String, val logo: String? = null) {
        var hidden = false
        var locked = false
    }

    class Cat(val id: String, val source: String, val kind: CategoryKind, val provider: String, val position: Int, val custom: Boolean = false) {
        var name = provider
        var hidden = false
        var locked: Boolean? = null
        var mine: Int? = null
        val members = mutableListOf<String>()
    }

    private val channels = mutableListOf<Ch>()
    private val cats = mutableListOf<Cat>()
    private val orders = mutableMapOf<String, CategoryOrder>()
    private val favorites = mutableMapOf("avery" to mutableListOf<String>())
    private var favoritesFromOne = true
    private var groupsFromOne = false
    private var made = 0
    private val titles = mutableMapOf<String, MutableList<EditorTitle>>()

    init {
        val home = "src-home"
        val backup = "src-backup"
        val groups = listOf("News", "Sports", "Movies", "Documentary", "Music", "Kids", "Adult Nights", "Local")
        groups.forEachIndexed { i, name -> cats += Cat("$home:g$i", home, CategoryKind.Live, name, i) }
        listOf("World", "Sports Extra", "Cinema").forEachIndexed { i, name -> cats += Cat("$backup:g$i", backup, CategoryKind.Live, name, i) }
        fun g(source: String, name: String) = cats.first { it.source == source && it.provider == name }.id
        val fixture = listOf(
            Triple(101, "Northline News", "News") to "fx_logo_northline.png",
            Triple(102, "Meridian 24", "News") to "fx_logo_meridian.png",
            Triple(103, "Arena Sports News", "Sports") to "fx_logo_arenanews.png",
            Triple(104, "Arena Sports Extra", "Sports") to "fx_logo_arenaextra.png",
            Triple(105, "Arena Sports 1", "Sports") to "fx_logo_arena1.png",
            Triple(106, "Arena Sports 2", "Sports") to "fx_logo_arena2.png",
            Triple(107, "Velocity Motor", "Sports") to "fx_logo_velocity.png",
            Triple(108, "Courtside+", "Sports") to "fx_logo_courtside.png",
            Triple(109, "Fairway Golf", "Sports") to "fx_logo_fairway.png",
            Triple(110, "Combat Arena", "Sports") to "fx_logo_combat.png",
            Triple(120, "Atlas One", "Local") to "fx_logo_atlas.png",
            Triple(140, "Cinema Prime 4K", "Movies") to "fx_logo_cinemaprime.png",
            Triple(180, "Wildframe 4K", "Documentary") to "fx_logo_wildframe.png",
            Triple(200, "Pulse", "Music") to "fx_logo_pulse.png",
            Triple(210, "Orbit Science", "Documentary") to "fx_logo_orbit.png",
        )
        for ((t, logo) in fixture) channels += Ch("$home:${t.first}", t.first, t.second, g(home, t.third), home, logo)
        val first = listOf("Harbor", "Summit", "Meadow", "Granite", "Lumen", "Cascade", "Juniper", "Beacon", "Copper", "Willow", "Falcon", "Prairie", "Tidal", "Ember", "Aurora", "Cedar")
        val second = mapOf(
            "News" to listOf("News", "Report", "Today", "24"),
            "Sports" to listOf("Sports", "Arena", "Racing", "Golf"),
            "Movies" to listOf("Cinema", "Classics", "Premiere", "Films"),
            "Documentary" to listOf("Planet", "History", "Science", "Wild"),
            "Music" to listOf("Hits", "Jazz", "Rock", "Country"),
            "Kids" to listOf("Kids", "Junior", "Cartoons", "Tots"),
            "Adult Nights" to listOf("Late", "After Dark", "Nights", "Velvet"),
            "Local" to listOf("Local", "City", "Community", "Weather"),
        )
        var number = 300
        for ((group, words) in second) for (a in first) for (b in words) {
            channels += Ch("$home:${number}", number, "$a $b", g(home, group), home)
            number++
        }
        for ((i, name) in listOf("Northline World", "Arena Sports Extra 2", "Cinema Prime 2").withIndex()) {
            channels += Ch("$backup:${1001 + i}", 1001 + i, name, g(backup, listOf("World", "Sports Extra", "Cinema")[i]), backup)
        }
        channels.first { it.n == 110 }.locked = true
        channels.first { it.n == 305 }.hidden = true
        cats.first { it.provider == "Adult Nights" }.locked = true
        favorites.getValue("avery") += listOf("$home:101", "$home:105", "$home:107", "$home:140", "$home:180", "$home:200")
        // A category of the user's own.
        cats += Cat("$home:custom:1", home, CategoryKind.Live, "Weekend Sports", -1, custom = true).also { it.members += listOf("$home:105", "$home:106", "$home:107") }
        // Movies and series of the Backup Line.
        listOf("Action", "Comedy", "Drama", "Family").forEachIndexed { i, name -> cats += Cat("$backup:movie:m$i", backup, CategoryKind.Movies, name, i) }
        listOf("Crime", "Science Fiction", "Reality").forEachIndexed { i, name -> cats += Cat("$backup:series:s$i", backup, CategoryKind.Series, name, i) }
        cats += Cat("$backup:movie:custom:2", backup, CategoryKind.Movies, "Friday Night", -1, custom = true).also { it.members += "$backup:lighthouse" }
        titles["movie"] = mutableListOf(
            EditorTitle("$backup:lighthouse", "The Lighthouse Keeper", 2024), EditorTitle("$backup:polar", "Polar Night", 2023),
            EditorTitle("$backup:harbor", "Harbor Lights", 2021), EditorTitle("$backup:summit", "Summit Run", 2022),
        )
        titles["series"] = mutableListOf(EditorTitle("$backup:nightstint", "Night Stint", 2025), EditorTitle("$backup:northof", "North of Here", 2024))
    }

    @Synchronized fun dump(): String = buildString {
        appendLine("favorites=" + favorites.getValue("avery").joinToString(","))
        appendLine("hidden=" + channels.filter { it.hidden }.joinToString(",") { it.id })
        appendLine("locked=" + channels.filter { it.locked }.joinToString(",") { it.id })
        appendLine("lockedGroups=" + cats.filter { it.locked == true }.joinToString(",") { it.id })
        appendLine("numbering=$favoritesFromOne,$groupsFromOne")
        for (c in cats) appendLine("cat ${c.id} name=${c.name} hidden=${c.hidden} mine=${c.mine} members=${c.members.joinToString(",")}")
        appendLine("orders=$orders")
    }

    private fun bump() {
        revision++
    }

    override suspend fun profile() = EditorProfile("avery", "Avery")

    override suspend fun library(profileId: String): EditorLibrary {
        val live = cats.filter { it.kind == CategoryKind.Live }
        val hiddenGroups = live.filter { it.hidden && !it.custom }.map { it.id }.toSet()
        val lockedGroups = live.filter { it.locked == true }.map { it.id }.toSet()
        return EditorLibrary(
            rev = revision, tv = "Living Room TV", profile = "Avery", pin = true,
            favoritesFromOne = favoritesFromOne, groupsFromOne = groupsFromOne,
            sources = listOf(
                EditorSource("src-home", "Home Portal", "stalker", true, channels.count { it.source == "src-home" }, 0, 0),
                EditorSource("src-backup", "Backup Line", "xtream", true, channels.count { it.source == "src-backup" }, 4, 2),
            ),
            groups = sorted("src-home", CategoryKind.Live).plus(sorted("src-backup", CategoryKind.Live)).map {
                EditorGroup(it.id, it.source, it.name, it.provider.takeIf { p -> !it.custom && p != it.name }, it.custom, it.hidden, it.locked == true, it.provider.startsWith("Adult"), it.members.toList())
            },
            channels = channels.sortedBy { it.n }.map {
                EditorChannel(it.id, it.n, it.name, it.group, it.source, it.hidden, !it.hidden && it.group in hiddenGroups, it.locked, it.group in lockedGroups, it.logo != null)
            },
            favorites = favorites.getOrPut(profileId) { mutableListOf() }.toList(),
        )
    }

    private fun sorted(source: String, kind: CategoryKind): List<Cat> {
        val list = cats.filter { it.source == source && it.kind == kind }
        return when (orders["$source|${kind.id}"] ?: CategoryOrder.Provider) {
            CategoryOrder.Provider -> list.filter { it.custom } + list.filter { !it.custom }.sortedBy { it.position }
            CategoryOrder.AToZ -> list.sortedBy { it.name.lowercase() }
            CategoryOrder.Mine -> list.sortedWith(compareBy<Cat> { it.mine ?: Int.MAX_VALUE }.thenBy { it.position })
        }
    }

    override suspend fun categories(sourceId: String, kind: CategoryKind): EditorCategories {
        val count = { c: Cat ->
            when {
                c.custom -> c.members.size
                kind == CategoryKind.Live -> channels.count { it.group == c.id }
                else -> 12 + c.position * 7
            }
        }
        return EditorCategories(
            revision, (orders["$sourceId|${kind.id}"] ?: CategoryOrder.Provider).wire(),
            sorted(sourceId, kind).map { EditorCategory(it.id, it.name, it.provider.takeIf { p -> !it.custom && p != it.name }, it.hidden, it.custom, count(it), it.locked == true, it.provider.startsWith("Adult")) },
        )
    }

    override suspend fun titles(kind: CategoryKind, categoryId: String): List<EditorTitle> {
        val members = cats.first { it.id == categoryId }.members
        return titles.getValue(kind.id).filter { it.id in members }
    }

    override suspend fun searchTitles(kind: CategoryKind, sourceId: String, query: String) =
        titles.getValue(kind.id).filter { it.name.split(' ').any { w -> w.startsWith(query, ignoreCase = true) } }

    override suspend fun favorites(profileId: String, change: FavoritesChange) {
        val list = favorites.getOrPut(profileId) { mutableListOf() }
        when (change) {
            is FavoritesChange.Add -> change.ids.filter { it !in list }.forEach { list += it }
            is FavoritesChange.Remove -> list.removeAll(change.ids.toSet())
            is FavoritesChange.Order -> {
                val named = change.ids.filter { it in list }
                val slots = list.map { it in named }
                val next = named.iterator()
                val arranged = list.mapIndexed { i, id -> if (slots[i]) next.next() else id }
                list.clear()
                list += arranged
            }
        }
        bump()
    }

    override suspend fun hide(channelIds: List<String>, hidden: Boolean) {
        channels.filter { it.id in channelIds }.forEach { it.hidden = hidden }
        bump()
    }

    override suspend fun lock(channelIds: List<String>, locked: Boolean) {
        channels.filter { it.id in channelIds }.forEach { it.locked = locked }
        bump()
    }

    override suspend fun lockGroup(groupId: String, locked: Boolean) {
        cats.first { it.id == groupId }.locked = locked
        bump()
    }

    override suspend fun category(change: CategoryChange): String? {
        var id: String? = null
        when (change) {
            is CategoryChange.Hide -> cats.first { it.id == change.id }.hidden = change.hidden
            is CategoryChange.HideAll -> cats.filter { it.source == change.sourceId && it.kind == change.kind }.forEach { it.hidden = change.hidden }
            is CategoryChange.Order -> {
                if (change.order == CategoryOrder.Mine && cats.none { it.source == change.sourceId && it.kind == change.kind && it.mine != null }) {
                    sorted(change.sourceId, change.kind).forEachIndexed { i, c -> c.mine = i }
                }
                orders["${change.sourceId}|${change.kind.id}"] = change.order
            }
            is CategoryChange.Move -> {
                change.ids.forEachIndexed { i, cid -> cats.firstOrNull { it.id == cid }?.mine = i }
                orders["${change.sourceId}|${change.kind.id}"] = CategoryOrder.Mine
            }
            is CategoryChange.Rename -> cats.first { it.id == change.id }.let { it.name = change.name.ifBlank { it.provider } }
            is CategoryChange.Create -> {
                val kind = if (change.kind == CategoryKind.Live) "" else "${change.kind.id}:"
                id = "${change.sourceId}:${kind}custom:new${++made}"
                cats += Cat(id, change.sourceId, change.kind, change.name, -1 - made, custom = true).also { c -> c.mine = (cats.mapNotNull { it.mine }.minOrNull() ?: 0) - 1 }
            }
            is CategoryChange.Delete -> cats.removeAll { it.id == change.id && it.custom }
            is CategoryChange.Members -> cats.first { it.id == change.id }.members.apply {
                removeAll(change.remove.toSet())
                change.add.filter { it !in this }.forEach { add(it) }
            }
        }
        bump()
        return id
    }

    override suspend fun numbering(favoritesFromOne: Boolean?, groupsFromOne: Boolean?) {
        if (favoritesFromOne != null) this.favoritesFromOne = favoritesFromOne
        if (groupsFromOne != null) this.groupsFromOne = groupsFromOne
        bump()
    }

    private var wrong = 0

    override suspend fun checkPin(pin: String): PinCheck = when {
        pin == "1234" -> PinCheck.Right.also { wrong = 0 }
        ++wrong >= 5 -> PinCheck.Blocked(60)
        else -> PinCheck.Wrong(5 - wrong)
    }

    override fun logo(channelId: String): EditorLogo? {
        val name = channels.firstOrNull { it.id == channelId }?.logo ?: return null
        return File(logos, name).takeIf { it.exists() }?.let { EditorLogo(it.readBytes(), "image/png") }
    }
}
