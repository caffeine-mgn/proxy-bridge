plugins {
    id("com.github.johnrengelman.shadow") version pw.binom.Versions.SHADOW_VERSION apply false
}
allprojects {
    repositories {
        maven(url = "https://repo.binom.pw")
        mavenCentral()
        mavenLocal()
    }
}
