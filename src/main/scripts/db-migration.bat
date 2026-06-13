@echo off
setlocal

:: Encontrar el directorio base
set BASEDIR=%~dp0

:: Buscar Java
if defined JAVA_HOME (
    set JAVACMD="%JAVA_HOME%\bin\java.exe"
) else (
    set JAVACMD="java"
)

:: Ajustes de memoria JVM
set JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseZGC

:: Construir el Classpath
set CLASSPATH="%BASEDIR%;%BASEDIR%liferay-db-migrator.jar"

echo Iniciando Liferay Database Migrator...
echo Usando Java: %JAVACMD%

:: Ejecutar la aplicación
%JAVACMD% %JAVA_OPTS% -cp %CLASSPATH% es.formatocd.liferay.tools.LiferayDBMigratorMain %*

endlocal