#!/bin/sh
DIR=$(dirname "$0")
if [ -z "$JAVA_HOME" ]; then
  JAVACMD=$(which java)
else
  JAVACMD="$JAVA_HOME/bin/java"
fi
exec "$JAVACMD" -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
