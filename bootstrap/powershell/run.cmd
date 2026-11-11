@echo off
rem Запуск бутстрапа в обход политики скриптов.
rem -ExecutionPolicy Bypass действует только на этот процесс и не требует
rem прав администратора. Если политика задана через GPO или включён
rem AppLocker/Constrained Language Mode — используйте ..\csharp (Bootstrap.exe).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bootstrap.ps1" %*
