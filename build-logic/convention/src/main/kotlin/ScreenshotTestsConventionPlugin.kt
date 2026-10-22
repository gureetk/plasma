// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

/**
 * Robolectric and Roborazzi screenshot tests (brief 6.9): screens render at 1920 × 1080 on the JVM
 * and are compared with the images in `src/test/screenshots`.
 *
 * Robolectric's Android runtime is fetched by Gradle, with its retries and caching, and used
 * offline, rather than downloaded by Robolectric during the run.
 */
class ScreenshotTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        extensions.findByType(LibraryExtension::class.java)?.testOptions?.unitTests?.isIncludeAndroidResources = true
        extensions.findByType(ApplicationExtension::class.java)?.testOptions?.unitTests?.isIncludeAndroidResources = true

        val androidAll = configurations.create("robolectricAndroidAll") { isTransitive = false }
        dependencies {
            "robolectricAndroidAll"(libs.lib("robolectric-android-all"))
            "testImplementation"(project(":core:testing"))
            "debugImplementation"(libs.lib("androidx-compose-ui-test-manifest"))
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
