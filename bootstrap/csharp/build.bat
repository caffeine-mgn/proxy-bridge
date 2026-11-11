@echo off
rem Сборка бутстрапа встроенным компилятором C# (.NET Framework 4.x).
rem Ничего устанавливать не нужно, никаких скриптовых политик не участвует:
rem это обычная компиляция в обычный .exe.
setlocal

set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist "%CSC%" (
    echo Cannot find csc.exe ^(.NET Framework 4.x required^).
    exit /b 1
)

"%CSC%" /nologo /target:exe /out:Bootstrap.exe /optimize+ Program.cs
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo.
echo Built: Bootstrap.exe
echo Run:   Bootstrap.exe settings.txt
