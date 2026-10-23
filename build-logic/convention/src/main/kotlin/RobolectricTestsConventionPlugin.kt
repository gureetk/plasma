// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

/**
 * Unit tests that need Android (Room, DataStore, screens) run on Robolectric. Its Android runtime
 * is fetched by Gradle, with its retries and caching, and used offline, rather than downloaded by
 * Robolectric during the run. Tests use SDK 35 (`@Config(sdk = [35])`), the runtime fetched here.
 */
class RobolectricTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.findByType(LibraryExtension::class.java)?.testOptions?.unitTests?.isIncludeAndroidResources = true
        extensions.findByType(ApplicationExtension::class.java)?.testOptions?.unitTests?.isIncludeAndroidResources = true

        val androidAll = configurations.create("robolectricAndroidAll") { isTransitive = false }
        dependencies {
            "robolectricAndroidAll"(libs.lib("robolectric-android-all"))
            "testImplementation"(libs.lib("junit"))
            "testImplementation"(libs.lib("robolectric"))
            "testImplementation"(libs.lib("androidx-test-ext-junit"))
        }
        val runtime = androidAll.incoming.files
        tasks.withType<Test>().configureEach {
            inputs.files(runtime).withPropertyName("robolectricAndroidAll")
            systemProperty("robolectric.offline", "true")
            jvmArgumentProviders.add(
                CommandLineArgumentProvider {
                    listOf("-Drobolectric.dependency.dir=${runtime.singleFile.parentFile.absolutePath}")
                },
            )
            maxHeapSize = "3g"
        }
    }
}
