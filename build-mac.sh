#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JAVA_HOME=$JAVA_HOME"
java -version

./gradlew --stop
./gradlew clean packageDmg