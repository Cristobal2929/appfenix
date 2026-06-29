#!/usr/bin/env sh
########################################################################
#   Gradle startup script for UN*X based systems
#######################################################################
APP_HOME="$(dirname "$0")"
exec java -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
