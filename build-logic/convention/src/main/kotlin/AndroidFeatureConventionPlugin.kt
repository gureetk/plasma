// SPDX-License-Identifier: GPL-3.0-or-later
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A screen or group of screens (the feature modules): Compose, the design system, sample data and screenshot tests. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("plasma.android.library")
        pluginManager.apply("plasma.android.compose")
        pluginManager.apply("plasma.screenshot.tests")
        dependencies {
            "implementation"(project(":core:design"))
            "implementation"(project(":core:model"))
            "implementation"(project(":player:core"))
            // M0 runs every screen on the boards' sample data (brief appendix A).
            "implementation"(project(":core:fixtures"))
            "implementation"(libs.lib("androidx-activity-compose"))
        }
    }
}
