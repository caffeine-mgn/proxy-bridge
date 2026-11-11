// Необязательная сборка через Gradle.
//
// Бутстрап специально не имеет НИ ОДНОЙ зависимости: на удалённой машине
// обычного JDK достаточно (`java Bootstrap.java` или `javac`), а Gradle там
// может отсутствовать или требовать сети для скачивания дистрибутива.
//
// Этот файл нужен, если Gradle на машине уже есть и хочется привычный
// `gradle build` / `gradle run`. Исходники лежат плоско (рядом с этим файлом),
// а не в src/main/java — так их проще «печатать» в Блокнот.

plugins {
    java
    application
}

group = "pw.binom"
version = "1.0.0"

java {
    // Бутстрап должен собираться старым JDK на удалённой машине.
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClass.set("Bootstrap")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("."))
            exclude("build/**", ".gradle/**")
        }
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "Bootstrap"
    }
}
