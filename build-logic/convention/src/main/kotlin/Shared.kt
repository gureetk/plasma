// SPDX-License-Identifier: GPL-3.0-or-later
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Android levels (brief 6.1): compile against 37 for current AndroidX, target 36, run on 26 and up. */
internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Kotlin on the JDK 21 toolchain, producing Java 17 bytecode, as Android expects. */
internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(21)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
}
