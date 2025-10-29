#!/bin/sh
if [ -x "./gradlew" ] && [ "$0" != "./gradlew" ]; then
  exec ./gradlew "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle wrapper not found and gradle not installed. Please install Gradle or add the Gradle wrapper."
exit 1
