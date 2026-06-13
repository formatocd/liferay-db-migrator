@echo off
setlocal

:: Find base directory
set BASEDIR=%~dp0

:: Find Java
if defined JAVA_HOME (
    set JAVACMD="%JAVA_HOME%\bin\java.exe"
) else (
    set JAVACMD="java"
)

:: JVM memory settings
set JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseZGC

:: Build Classpath
set CLASSPATH="%BASEDIR%;%BASEDIR%liferay-db-migrator.jar"

echo Starting Liferay Database Migrator...
echo Using Java: %JAVACMD%

:: Run application
%JAVACMD% %JAVA_OPTS% -cp %CLASSPATH% es.formatocd.liferay.tools.LiferayDBMigratorMain %*

endlocal