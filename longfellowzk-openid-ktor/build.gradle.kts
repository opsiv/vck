import at.asitplus.gradle.envExtra
import at.asitplus.gradle.kotest
import at.asitplus.gradle.ktor
import org.gradle.kotlin.dsl.provideDelegate
import at.asitplus.gradle.VcLibVersions
import at.asitplus.gradle.androidJvmMain
import at.asitplus.gradle.commonImplementationDependencies
import at.asitplus.gradle.hasAndroidSdk
import at.asitplus.gradle.vckAndroid
import at.asitplus.gradle.configureLongfellowIosLinking

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
        configureLongfellowIosLinking()
    }
    sourceSets {

        commonMain {
            dependencies {
                api(project(":vck-openid-ktor"))
                api(project(":longfellowzk"))
                commonImplementationDependencies()
            }
        }

        commonTest {
            dependencies {
                implementation("at.asitplus.wallet:eupidcredential:${VcLibVersions.eupidcredential}")
                implementation("at.asitplus.wallet:mobiledrivinglicence:${VcLibVersions.mdl}")
                implementation(ktor("client-mock"))
                implementation(kotest("assertions-core"))
            }
        }

        iosTest
    }
}


