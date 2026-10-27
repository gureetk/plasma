// SPDX-License-Identifier: GPL-3.0-or-later
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Robolectric and Roborazzi screenshot tests (brief 6.9): screens render at 1920 × 1080 on the JVM
 * and are compared with the images in `src/test/screenshots`.
 */
class ScreenshotTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("plasma.robolectric.tests")
        pluginManager.apply("io.github.takahirom.roborazzi")
        dependencies {
            "testImplementation"(project(":core:testing"))
            "debugImplementation"(libs.lib("androidx-compose-ui-test-manifest"))
        }
    }
}
