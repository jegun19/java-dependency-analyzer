#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script, version 3.3.2
#
# Required ENV vars:
#   JAVA_HOME - location of a JDK home dir
#
# This wrapper will download Maven if necessary.
# ----------------------------------------------------------------------------

set -e

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
PROPS="$WRAPPER_DIR/maven-wrapper.properties"
JAR="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS" ]; then
  echo "Missing $PROPS" >&2
  exit 1
fi

DISTRIBUTION_URL=$(grep -E '^distributionUrl=' "$PROPS" | cut -d'=' -f2-)
WRAPPER_URL=$(grep -E '^wrapperUrl=' "$PROPS" | cut -d'=' -f2-)

mkdir -p "$WRAPPER_DIR"

if [ ! -f "$JAR" ]; then
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$JAR"
  else
    echo "Neither curl nor wget is available to download Maven Wrapper" >&2
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi

cd "$BASE_DIR"
exec "$JAVA_CMD" -cp "$JAR" org.apache.maven.wrapper.MavenWrapperMain -Dmaven.multiModuleProjectDirectory="$BASE_DIR" -Dmaven.home="$BASE_DIR/.mvn" -Dmaven.wrapper.distributionUrl="$DISTRIBUTION_URL" "$@"
