#!/bin/bash

# Find the base directory where the script is located
BASEDIR=$(dirname "$0")

# Find Java installation (uses JAVA_HOME if it exists, otherwise looks in PATH)
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# JVM memory settings (Adjust the 4GB based on your server needs)
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseZGC"

# Build Classpath (includes current folder and the JAR)
CLASSPATH="$BASEDIR:$BASEDIR/liferay-db-migrator.jar"

echo "Starting Liferay Database Migrator..."
echo "Using Java: $JAVACMD"

# Run the application, passing all script arguments ($@) to Java
"$JAVACMD" $JAVA_OPTS -cp "$CLASSPATH" es.formatocd.liferay.tools.LiferayDBMigratorMain "$@"