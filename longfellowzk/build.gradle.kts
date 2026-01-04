import at.asitplus.gradle.envExtra
import at.asitplus.gradle.kotest
import org.gradle.kotlin.dsl.provideDelegate
import at.asitplus.gradle.VcLibVersions
import at.asitplus.gradle.androidJvmMain
import at.asitplus.gradle.commonImplementationDependencies
import at.asitplus.gradle.vckAndroid

plugins {
    id("at.asitplus.gradle.vclib-conventions")
}

val disableAppleTargets by envExtra
kotlin {
    jvm()
    vckAndroid()
    if ("true" != disableAppleTargets) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
    }
    sourceSets {

        commonMain {
            dependencies {
                api(project(":vck"))
                commonImplementationDependencies()
            }
        }

        androidJvmMain.dependencies {
            implementation("net.java.dev.jna:jna:${VcLibVersions.jna}")
            implementation("net.java.dev.jna:jna-platform:${VcLibVersions.jna}")
        }

        commonTest {
            dependencies {
                implementation("at.asitplus.wallet:eupidcredential:${VcLibVersions.eupidcredential}")
                implementation("at.asitplus.wallet:mobiledrivinglicence:${VcLibVersions.mdl}")
                implementation(kotest("assertions-core"))
            }
        }

        iosTest
    }
}


