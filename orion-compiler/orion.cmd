@echo off
rem Orion compiler launcher. Build the JAR first with: mvn package
java -jar "%~dp0target\orion.jar" %*
exit /b %errorlevel%
