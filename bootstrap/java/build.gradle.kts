plugins {
    java
    application
}

group = "pw.binom"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Честная библиотека для COM-портов: без нативных DLL/JNI под каждую ОС,
    // один jar со встроенными бинарниками для Windows/Linux/macOS.
    implementation("com.fazecast:jSerialComm:2.11.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("pw.binom.bootstrap.Bootstrap")
}

// Исходники лежат плоско, рядом с этим файлом: так их проще «печатать» в
// Блокнот на удалённой машине (каталог src/main/java набирать не нужно).
sourceSets {
    main {
        java {
            setSrcDirs(listOf("."))
            exclude("build/**", ".gradle/**")
        }
    }
}

// Исполняемый jar со всеми зависимостями: java -jar bootstrap.jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "pw.binom.bootstrap.Bootstrap"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
}
