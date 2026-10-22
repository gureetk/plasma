// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose (foundation only: Plasma draws its own components, brief 2 and docs/decisions.md). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
        extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            "implementation"(bom)
            "implementation"(libs.lib("androidx-compose-ui"))
            "implementation"(libs.lib("androidx-compose-foundation"))
            "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
            "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
            "testImplementation"(bom)
        }
    }
}
