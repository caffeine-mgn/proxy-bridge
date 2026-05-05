plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    mingwX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
//    watchosArm32()
    watchosArm64()
//    watchosDeviceArm64()
    watchosSimulatorArm64()
    wasmWasi {
        nodejs {
            testTask {
                useKarma()
            }
        }
    }
    wasmJs {
        browser()
        nodejs {
            testTask {
                useKarma()
            }
        }
        d8 {
            testTask {
                useKarma()
            }
        }
    }
    js {
        browser()
        nodejs {
            testTask {
                useKarma()
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.io.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.loggeing)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }
//        val runnableTest by creating {
//            dependsOn(commonTest.get())
//            dependsOn(jvmTest.get())
//            dependsOn(linuxTest.get())
//            dependsOn(mingwTest.get())
//        }
    }
}
