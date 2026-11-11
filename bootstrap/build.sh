#!/bin/sh
# Сборка бутстрапа БЕЗ Gradle и Maven — только javac/jar из JDK.
set -e

BootstrapDir="$(cd "$(dirname "$0")" && pwd)"
cd "$BootstrapDir"

if ! command -v javac >/dev/null 2>&1; then
    echo "javac not found. Install a JDK, or run:  java Bootstrap.java" >&2
    exit 1
fi

javac -encoding UTF-8 Bootstrap.java
jar --create --file bootstrap.jar --main-class Bootstrap ./*.class

echo
echo "Built: bootstrap.jar"
echo "Run:   java -jar bootstrap.jar settings.txt"
