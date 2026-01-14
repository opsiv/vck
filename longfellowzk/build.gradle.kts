import at.asitplus.gradle.envExtra
import at.asitplus.gradle.kotest
import org.gradle.kotlin.dsl.provideDelegate
import at.asitplus.gradle.VcLibVersions
import at.asitplus.gradle.commonImplementationDependencies
import at.asitplus.gradle.configureLongfellowIosLinking
import at.asitplus.gradle.ktor
import at.asitplus.gradle.vckAndroid

plugins {
    id("at.asitplus.gradle.vclib-conventions")
}


val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion
val disableAppleTargets by envExtra
kotlin {
    jvm()
    vckAndroid()
    if ("true" != disableAppleTargets) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
        configureLongfellowIosLinking(isLongfellowZkModule = true)
    }
    sourceSets {

        commonMain {
            dependencies {
                api(project(":vck"))
                commonImplementationDependencies()
            }
        }

        androidMain.dependencies {
            implementation("net.java.dev.jna:jna:${VcLibVersions.jna}@aar")
        }

        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna:${VcLibVersions.jna}")
            implementation("net.java.dev.jna:jna-platform:${VcLibVersions.jna}")
        }


        commonTest {
            dependencies {
                api(project(":vck-openid-ktor"))
                implementation("at.asitplus.wallet:eupidcredential:${VcLibVersions.eupidcredential}")
                implementation("at.asitplus.wallet:mobiledrivinglicence:${VcLibVersions.mdl}")
                implementation(ktor("client-mock"))
                implementation(kotest("assertions-core"))
            }
        }
    }
}


