# Бутстрап: Gradle-проект с библиотекой

Честная сборка: `jSerialComm` для работы с портом, `application` plugin,
исполняемый jar со всеми зависимостями.

```bash
gradle run                                  # собрать и запустить одной командой
gradle run --args="other-settings.txt"      # другой файл настроек
```

Если нужен переносимый артефакт:

```bash
gradle build
java -jar build/libs/bootstrap-1.0.0.jar settings.txt
```

## Maven или Gradle

В папке лежат оба build-файла, исходники общие — выберите один инструмент:

| Инструмент | Собрать и запустить | Переносимый jar |
|---|---|---|
| Gradle | `gradle run` | `gradle build` → `build/libs/bootstrap-1.0.0.jar` |
| Maven | `mvn -q compile exec:java` | `mvn -q package` → `target/bootstrap-1.0.0.jar` |

Фронт печатает команду запуска кнопкой: `Запустить через gradle` /
`Запустить через maven` (файлы `launch-gradle.txt`, `launch-maven.txt`).

## Почему не годится как «доставка клавиатурой»

Единственный канал на ограниченную машину — ввод с клавиатуры (текст). Gradle
проекту для сборки нужны **сам Gradle и сеть** (скачать дистрибутив и
`jSerialComm`). Поэтому в реальной ситуации без сети используйте
`../powershell` (Windows) или `../standalone` (любая ОС с JRE).

Этот вариант удобен, когда:

- машина с Gradle и сетью (корпоративный ноутбук разработчика);
- бутстрап доставляется не клавиатурой, а вместе с образом/архивом;
- нужна проверка протокола в CI (на стенде проверено: 15 МБ, md5 совпал).

## Что внутри

| Файл | Назначение |
|---|---|
| `Bootstrap.java` | приём файлов, разбор протокола, запись на диск |
| `Settings.java` | чтение `settings.txt` (`key=value`) |
| `build.gradle.kts` | сборка fat-jar (`Main-Class` в манифесте) |
| `pom.xml` | то же для Maven (`exec-maven-plugin` + манифест) |
| `settings.txt` | `port=`, `baud=`, `workdir=` |

Исходники лежат плоско (`setSrcDirs(listOf("."))`), чтобы их можно было
напечатать в Блокнот без создания каталогов `src/main/java`.
