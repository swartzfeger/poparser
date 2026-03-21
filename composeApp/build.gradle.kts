import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val appVersion = "1.0.4"
val appVendor = "Jay Swartzfeger"
val appCopyright = "© 2026 Precision Laboratories"
val appBaseName = "PO Parser"
val appDisplayName = "$appBaseName $appVersion"

// This must stay the same for every future release of the same Windows app.
val windowsUpgradeUuid = "8d9c2db0-3f1d-4b45-9f9d-7d7f0d6b4b2d"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    kotlin("plugin.serialization") version "2.3.10"
}

kotlin {
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            implementation("org.apache.pdfbox:pdfbox:3.0.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.jay.parser.MainKt"

        nativeDistributions {
            // This controls the app/bundle name users see.
            packageName = appDisplayName
            packageVersion = appVersion

            vendor = appVendor
            copyright = appCopyright

            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe
            )

            windows {
                menu = true
                shortcut = true
                menuGroup = appBaseName
                upgradeUuid = windowsUpgradeUuid
                dirChooser = true
                perUserInstall = false
            }

            macOS {
                bundleID = "com.jay.poparser"
            }
        }
    }
}