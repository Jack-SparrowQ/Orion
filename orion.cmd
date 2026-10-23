@echo off
rem Orion compiler launcher. Build the JAR first with: mvn package
rem UTF-8 output: otherwise Java writes Cp1252 when piped and accents break.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0target\orion.jar" %*
exit /b %errorlevel%
