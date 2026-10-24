// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

/** A slice of the boards' guide (brief appendix A), as an XMLTV file would carry it. */
class XmltvReaderTest {
    private val guide = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="arena1.example">
            <display-name lang="en">Arena Sports 1</display-name>
            <display-name>Arena Sports 1 HD</display-name>
            <icon src="http://img.example.net/arena1.png" />
          </channel>
          <channel id="Velocity.example"><display-name>Velocity Motor</display-name></channel>
          <programme start="20261021194500 +0000" stop="20261021220000 +0000" channel="arena1.example">
            <title lang="en">Harbour City v Redmoor</title>
            <title lang="fr">Harbour City contre Redmoor</title>
            <sub-title>City League, Matchweek 9</sub-title>
            <desc>Live coverage from the Harbour.</desc>
            <category>Football</category>
            <category>Sports</category>
            <icon src="http://img.example.net/harbour.jpg"/>
            <episode-num system="onscreen">S2026E09</episode-num>
            <episode-num system="xmltv_ns">0.8.0/1</episode-num>
            <new />
            <rating system="VCHIP"><value>TV-PG</value></rating>
            <rating system="MPAA"><value>PG</value></rating>
          </programme>
          <programme start="20261021230000 +0100" stop="20261022000000 +0100" channel="arena1.example">
            <title>Match of the Week Extra</title>
            <rating><icon src="http://img.example.net/16.png" /><value> 16 </value></rating>
          </programme>
          <programme start="20261021180000" channel="Velocity.example"><title>Pit Lane</title></programme>
          <programme start="20261020180000 +0000" stop="20261020190000 +0000" channel="arena1.example"><title>Yesterday</title></programme>
          <programme stop="20261021190000 +0000" channel="arena1.example"><title>No start, so left out</title></programme>
        </tv>
    """.trimIndent()

    private fun at(text: String) = Instant.parse(text).toEpochMilli()

    private fun read(bytes: ByteArray, reader: XmltvReader = XmltvReader()): Pair<List<XmltvChannel>, List<XmltvProgramme>> {
        val channels = mutableListOf<XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()
        reader.read(bytes.inputStream(), onChannel = { channels += it }, onProgramme = { programmes += it })
        return channels to programmes
    }

    @Test fun readsChannelsAndProgrammes() {
        val (channels, programmes) = read(guide.toByteArray())

        assertEquals(
            listOf(
                XmltvChannel("arena1.example", listOf("Arena Sports 1", "Arena Sports 1 HD"), "http://img.example.net/arena1.png"),
                XmltvChannel("Velocity.example", listOf("Velocity Motor"), null),
            ),
            channels,
        )
        assertEquals(listOf("Harbour City v Redmoor", "Match of the Week Extra", "Pit Lane", "Yesterday"), programmes.map { it.title })

        val match = programmes[0]
        assertEquals(at("2026-10-21T19:45:00Z"), match.start)
        assertEquals(at("2026-10-21T22:00:00Z"), match.end)
        assertEquals("City League, Matchweek 9", match.subtitle)
        assertEquals("Live coverage from the Harbour.", match.description)
        assertEquals("Football", match.category)
        assertEquals("http://img.example.net/harbour.jpg", match.icon)
        // xmltv_ns wins over onscreen, and counts from zero.
        assertEquals(1, match.season)
        assertEquals(9, match.episode)
        assertTrue(match.isNew)
        // The first rating counts; the value is kept as written.
        assertEquals("TV-PG", match.rating)
        assertEquals("16", programmes[1].rating)
        assertNull(programmes[2].rating)
    }

    @Test fun honoursOffsetsAndFillsInAMissingStop() {
        val (_, programmes) = read(guide.toByteArray())

        val extra = programmes[1]
        assertEquals(at("2026-10-21T22:00:00Z"), extra.start)
        assertEquals(at("2026-10-21T23:00:00Z"), extra.end)
        assertFalse(extra.isNew)
        assertNull(extra.season)

        // No offset means UTC; no stop means an hour for now.
        val pitLane = programmes[2]
        assertEquals(at("2026-10-21T18:00:00Z"), pitLane.start)
        assertEquals(at("2026-10-21T19:00:00Z"), pitLane.end)
    }

    @Test fun skipsWhatEndedBeforeTheWindowAndAppliesTheSourceCorrection() {
        val reader = XmltvReader(notBefore = at("2026-10-21T00:00:00Z"), offsetMillis = -3_600_000)
        val (_, programmes) = read(guide.toByteArray(), reader)

        assertEquals(listOf("Harbour City v Redmoor", "Match of the Week Extra", "Pit Lane"), programmes.map { it.title })
        assertEquals(at("2026-10-21T18:45:00Z"), programmes[0].start)
    }

    @Test fun readsAGzippedGuide() {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(guide.toByteArray()) } }.toByteArray()
        val (channels, programmes) = read(zipped)
        assertEquals(2, channels.size)
        assertEquals(4, programmes.size)
    }

    @Test fun parsesTimes() {
        assertEquals(at("2026-10-21T19:45:00Z"), XmltvReader.parseTime("20261021194500 +0000"))
        assertEquals(at("2026-10-21T18:45:00Z"), XmltvReader.parseTime("20261021194500 +0100"))
        assertEquals(at("2026-10-21T23:15:00Z"), XmltvReader.parseTime("20261021194500 -0330"))
        assertEquals(at("2026-10-21T19:45:00Z"), XmltvReader.parseTime("202610211945"))
        assertNull(XmltvReader.parseTime("yesterday"))
    }

    @Test fun parsesEpisodeNumbers() {
        assertEquals(2 to 5, Episodes.parse("xmltv_ns", "1.4.0/1"))
        assertEquals(null to 3, Episodes.parse("xmltv_ns", " . 2 . "))
        assertEquals(3 to 12, Episodes.parse("onscreen", "S03E12"))
        assertNull(Episodes.parse("dd_progid", "EP012345670015"))
        assertNull(Episodes.parse("onscreen", "Part two"))
    }
}
