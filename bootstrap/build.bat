@echo off
rem Сборка бутстрапа БЕЗ Gradle и Maven — только javac из JDK.
rem На удалённой машине без сети это единственный надёжный способ.
setlocal

where javac >nul 2>nul
if errorlevel 1 (
    echo javac not found. Install a JDK, or run:  java Bootstrap.java
    exit /b 1
)

javac -encoding UTF-8 Bootstrap.java SerialLink.java
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

jar --create --file bootstrap.jar --main-class Bootstrap *.class
if errorlevel 1 (
    echo jar failed — classes built, run:  java -cp . Bootstrap
    exit /b 1
)

echo.
echo Built: bootstrap.jar
echo Run:   java -jar bootstrap.jar settings.txt
