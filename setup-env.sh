#!/usr/bin/env bash
# Source this file from the project root for the bundled development tools.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="$PROJECT_ROOT/.tools/java17/Contents/Home"
export MAVEN_HOME="$PROJECT_ROOT/.tools/maven"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$HOME/Applications/Docker.app/Contents/Resources/bin:$PATH"
export MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=$PROJECT_ROOT/.tools/m2"
