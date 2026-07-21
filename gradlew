#!/usr/bin/env sh

##############################################################################
#
# Copyright (c) 2015 the original authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file or her in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software is
# distributed on an "AS IS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
# ANY KIND, either express or implied. The License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied. See the License for the specific language and
# governing permissions and limitations under the License.
#
##############################################################################

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Add default JVM options here. You can also use JAVA_OPTS or GRADLE_OPTS.
DEFAULT_JVM_OPTS=""

# Use the defaults, unless the user has specified a custom Java home.
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)" 2>/dev/null || echo /usr/bin/java)")")"
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the current working directory and set APP_HOME.
APP_HOME=$(cd "$(dirname "$0")" && pwd)

# Load the Gradle wrapper properties
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" ]; then
  . "$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
fi

# The Gradle wrapper jar is not present. Download it if needed.
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Gradle wrapper JAR not found. Downloading..."
  mkdir -p "$APP_HOME/gradle/wrapper"
  curl -sSL https://repo1.maven.org/maven2/org/gradle/gradle-wrapper/8.10/gradle-wrapper-8.10.jar -o "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
fi

exec "$JAVA_HOME/bin/java" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
