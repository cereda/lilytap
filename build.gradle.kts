// SPDX-License-Identifier: MIT
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotless.changelog)
    alias(libs.plugins.tapmoc)
    alias(libs.plugins.kover)
    alias(libs.plugins.versions)
    application
}

group = "com.github.cereda"

// the version is driven by the changelog (Keep a Changelog + SemVer)
// via spotless-changelog; `./gradlew changelogCheck` enforces it.
spotlessChangelog {
    changelogFile("CHANGELOG.md")
    tagPrefix("v")
    commitMessage("Release v{{version}}")
    remote("origin")
    branch("main")
}

version = spotlessChangelog.versionNext

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.github.cereda.lilytap.MainKt")
    applicationName = "lilytap"
}

// Shadow: the Kotlin module metadata transformer needs every `META-INF/*.kotlin_module`
// file, so the default EXCLUDE strategy has to be relaxed to avoid silent drops.
// The fat jar has a stable name: `java -jar build/libs/lilytap.jar`.
tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("lilytap.jar")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

// tapmoc: enforce dependency compatibility against the underlying JVM (>= 17),
// no JVM toolchains. The targets live in gradle.properties.
tapmoc {
    java(providers.gradleProperty("lilytap.target.jvm").get().toInt())
    kotlin(providers.gradleProperty("lilytap.target.kotlin").get())
    checkDependencies()
}

// kover: coverage reports and an 80% line coverage floor.
// Only the public API is unit-testable: the Swing UI needs a display and the metronome
// needs a sound card, so those are left out of the gate.
val koverCoreClasses =
    listOf(
        "com.github.cereda.lilytap.Rhythm*",
        "com.github.cereda.lilytap.Score*",
        "com.github.cereda.lilytap.GridView",
        "com.github.cereda.lilytap.Config",
        "com.github.cereda.lilytap.TimeSignature*",
        "com.github.cereda.lilytap.Tap*",
        "com.github.cereda.lilytap.HeldNote",
        "com.github.cereda.lilytap.Onset",
        "com.github.cereda.lilytap.Atom",
        "com.github.cereda.lilytap.Event",
        "com.github.cereda.lilytap.SnappedNote",
        "com.github.cereda.lilytap.QuantizeResult",
        "com.github.cereda.lilytap.Transcription",
    )

kover {
    reports {
        filters {
            includes {
                classes(koverCoreClasses)
            }
        }
        verify {
            rule {
                minBound(80, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
            }
        }
    }
}

// enforce the changelog format and coverage floor as part of `check`
tasks.named("check") {
    dependsOn("changelogCheck", "koverVerify")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    // pre-releases are rejected by the plugin by default
    checkForGradleUpdate = false
}

private val spdxHeader = "// SPDX-License-Identifier: MIT"
private val spdxDelimiterKotlin = "(package |import )"
private val spdxDelimiterGradleKts =
    "(import |plugins |pluginManagement |dependencyResolutionManagement |rootProject|tasks\\.register|tasks\\.create)"

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        licenseHeader(spdxHeader, spdxDelimiterKotlin)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("build.gradle.kts", "settings.gradle.kts")
        ktlint()
        licenseHeader(spdxHeader, spdxDelimiterGradleKts)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    ignoreFailures = false
    buildUponDefaultConfig = true
    config.from(files("config/detekt/detekt.yml"))
}
