import at.asitplus.gradle.VcLibVersions
import at.asitplus.gradle.commonImplementationAndApiDependencies
import at.asitplus.gradle.envExtra
import at.asitplus.gradle.exportXCFramework
import at.asitplus.gradle.setupDokka
import at.asitplus.gradle.vckAndroid

plugins {
    id("at.asitplus.gradle.vclib-conventions")
}

/* required for maven publication */
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

    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":vck"))
                implementation("org.multipaz:multipaz:0.100.0-SNAPSHOT")
                implementation("org.multipaz:multipaz-longfellow:0.100.0-SNAPSHOT")
                commonImplementationAndApiDependencies()
            }
        }
        commonTest {
            dependencies {
                implementation("com.squareup.okio:okio:3.15.0")
            }
        }
        jvmTest {
            dependencies {
                implementation("at.asitplus.signum:indispensable-josef:${VcLibVersions.signum}")
                implementation("com.nimbusds:nimbus-jose-jwt:9.31")
                implementation(kotlin("reflect"))
                implementation("org.json:json:${VcLibVersions.Jvm.json}")
                implementation("com.authlete:cbor:${VcLibVersions.Jvm.`authlete-cbor`}")
            }
        }
    }
}
