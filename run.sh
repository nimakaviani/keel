#!/bin/bash
set -ex

SPRING_CONFIG_NAME=keel SPRING_PROFILES_ACTIVE=local SPRING_CONFIG_LOCATION="/Users/nkaviani/workspaces/spinnaker/keel/keel-web/config/" /usr/bin/java  -Xms64m -Xdock:name=Gradle -Xdock:icon=/Users/nima/dev/spinnaker/gate/media/gradle.icns -Dorg.gradle.appname=gradlew -classpath /Users/nkaviani/workspaces/spinnaker/keel/gradle/wrapper/gradle-wrapper.jar:springboot.jks  org.gradle.wrapper.GradleWrapperMain
