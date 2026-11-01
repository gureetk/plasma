// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** The update file (brief 5.18), and what it offers each TV. */
class UpdateManifestTest {
    private val address = "https://updates.example/plasma/update.json".toHttpUrl()
    private val arm64 = "a".repeat(64)
    private val arm32 = "B".repeat(64)
    private val universal = "c".repeat(64)

    private val file = """
        {
          "versionCode": 512,
          "versionName": "0.2.0",
          "minSdk": 26,
          "notes": "  Picture-in-picture, and Plasma updates itself.  ",
          "apks": [
            { "abi": "arm64-v8a", "url": "app-arm64-v8a-release.apk", "sha256": "$arm64" },
            { "abi": "armeabi-v7a", "url": "https://files.example/plasma/app-armeabi-v7a-release.apk", "sha256": "$arm32" },
            { "abi": "universal", "url": "app-universal-release.apk", "sha256": "$universal" }
          ],
          "added": "later"
        }
    """.trimIndent()

    private fun expectBadFile(text: String) {
        try {
            UpdateManifest.parse(text)
            fail("Read $text")
        } catch (e: UpdateException) {
            assertEquals(UpdateProblem.BadFile, e.problem)
        }
    }

    @Test fun readsTheFile() {
        val manifest = UpdateManifest.parse(file)
        assertEquals(512L, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)
        assertEquals(26, manifest.minSdk)
        assertEquals("Picture-in-picture, and Plasma updates itself.", manifest.notes)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "universal"), manifest.apks.map { it.abi })
    }

    @Test fun theNotesAndMinSdkCanBeLeftOut() {
        val manifest = UpdateManifest.parse("""{"versionCode": 2, "versionName": "0.1.1", "apks": []}""")
        assertEquals(0, manifest.minSdk)
        assertEquals("", manifest.notes)
    }

    @Test fun anythingElseIsABadFile() {
        expectBadFile("<html>Not found</html>")
        expectBadFile("[]")
        expectBadFile("""{"versionName": "0.2.0", "apks": []}""")
        expectBadFile("""{"versionCode": "soon", "versionName": "0.2.0", "apks": []}""")
        expectBadFile("""{"versionCode": 2, "versionName": "0.2.0", "apks": [{"abi": "arm64-v8a", "url": "a.apk"}]}""")
        // Not a SHA-256.
        expectBadFile("""{"versionCode": 2, "versionName": "0.2.0", "apks": [{"abi": "arm64-v8a", "url": "a.apk", "sha256": "${"z".repeat(64)}"}]}""")
        expectBadFile("""{"versionCode": 2, "versionName": "0.2.0", "apks": [{"abi": "arm64-v8a", "url": "a.apk", "sha256": "abc"}]}""")
    }

    @Test fun eachTvGetsTheApkForItsFirstProcessorType() {
        val apks = UpdateManifest.parse(file).apks
        assertEquals("arm64-v8a", UpdateManifest.pick(apks, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))?.abi)
        // 32-bit Android on a 64-bit chip says armeabi-v7a first.
        assertEquals("armeabi-v7a", UpdateManifest.pick(apks, listOf("armeabi-v7a", "armeabi"))?.abi)
        // Nothing made for it: the universal one.
        assertEquals("universal", UpdateManifest.pick(apks, listOf("x86_64", "arm64-v8a"))?.abi)
        assertNull(UpdateManifest.pick(apks.filter { it.abi != "universal" }, listOf("x86_64")))
    }

    @Test fun offersANewerVersion() {
        val check = UpdateManifest.parse(file).checkFor(installedCode = 480, sdk = 34, abis = listOf("arm64-v8a"), address = address)
        val update = (check as UpdateCheck.Available).update
        assertEquals("0.2.0 (512)", update.label)
        // Relative to the update file's own address.
        assertEquals("https://updates.example/plasma/app-arm64-v8a-release.apk", update.url)
        assertEquals(arm64, update.sha256)
        assertEquals("Picture-in-picture, and Plasma updates itself.", update.notes)

        val elsewhere = UpdateManifest.parse(file).checkFor(480, 34, listOf("armeabi-v7a"), address) as UpdateCheck.Available
        assertEquals("https://files.example/plasma/app-armeabi-v7a-release.apk", elsewhere.update.url)
        // Kept in lowercase, as digests are compared.
        assertEquals(arm32.lowercase(), elsewhere.update.sha256)
    }

    @Test fun theSameOrAnOlderVersionIsUpToDate() {
        val manifest = UpdateManifest.parse(file)
        assertEquals(UpdateCheck.UpToDate, manifest.checkFor(512, 34, listOf("arm64-v8a"), address))
        assertEquals(UpdateCheck.UpToDate, manifest.checkFor(600, 34, listOf("arm64-v8a"), address))
    }

    @Test fun eachTvGetsTheProfileForItsAndroid() {
        val android9 = "d".repeat(64)
        val android12 = "E".repeat(64)
        val manifest = UpdateManifest.parse(
            file.replace(
                "\"added\": \"later\"",
                """
                "profiles": [
                    { "minSdk": 28, "maxSdk": 30, "url": "app-release-sdk28.dm", "sha256": "$android9" },
                    { "minSdk": 31, "maxSdk": 2147483647, "url": "app-release-sdk31.dm", "sha256": "$android12" },
                    { "minSdk": 26, "url": "app-release-sdk26.dm", "sha256": "$android9" }
                ]
                """.trimIndent(),
            ),
        )
        // One Plasma can't read is left out, rather than the update.
        assertEquals(listOf(28, 31), manifest.profiles.map { it.minSdk })

        val android14 = (manifest.checkFor(480, 34, listOf("arm64-v8a"), address) as UpdateCheck.Available).update
        assertEquals("https://updates.example/plasma/app-release-sdk31.dm", android14.profileUrl)
        assertEquals(android12.lowercase(), android14.profileSha256)
        val android11 = (manifest.checkFor(480, 30, listOf("arm64-v8a"), address) as UpdateCheck.Available).update
        assertEquals("https://updates.example/plasma/app-release-sdk28.dm", android11.profileUrl)
        assertEquals(android9, android11.profileSha256)

        // Android 8 has none, and the update goes in without one, as it does from a file without them.
        val android8 = (manifest.checkFor(480, 27, listOf("arm64-v8a"), address) as UpdateCheck.Available).update
        assertNull(android8.profileUrl)
        assertNull(android8.profileSha256)
        val before = (UpdateManifest.parse(file).checkFor(480, 34, listOf("arm64-v8a"), address) as UpdateCheck.Available).update
        assertNull(before.profileUrl)
    }

    @Test fun aVersionThisTvCantTakeIsntOffered() {
        val needsNewer = UpdateManifest.parse(file.replace("\"minSdk\": 26", "\"minSdk\": 31"))
        assertEquals(UpdateCheck.NotForThisTv("0.2.0"), needsNewer.checkFor(480, 30, listOf("arm64-v8a"), address))

        val noUniversal = UpdateManifest.parse(file).let { it.copy(apks = it.apks.filter { apk -> apk.abi != "universal" }) }
        assertEquals(UpdateCheck.NotForThisTv("0.2.0"), noUniversal.checkFor(480, 34, listOf("x86_64"), address))
    }
}
