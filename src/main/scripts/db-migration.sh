#!/bin/bash

# Encontrar el directorio base donde se encuentra el script
BASEDIR=$(dirname "$0")

# Buscar la instalación de Java (usa JAVA_HOME si existe, sino busca en el PATH)
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Ajustes de memoria JVM (Ajusta los 4GB según lo que necesite tu servidor)
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseZGC"

# Construir el Classpath (incluye la carpeta actual y el JAR)
CLASSPATH="$BASEDIR:$BASEDIR/liferay-db-migrator.jar"

echo "Iniciando Liferay Database Migrator..."
echo "Usando Java: $JAVACMD"

# Ejecutar la aplicación, pasando todos los argumentos del script ($@) a Java
"$JAVACMD" $JAVA_OPTS -cp "$CLASSPATH" es.formatocd.liferay.tools.LiferayDBMigratorMain "$@"