#!/bin/bash
# Canonical local verification for Takeup.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step() {
    echo -e "\n${BLUE}:: $1${NC}"
}

print_success() {
    echo -e "${GREEN}   $1${NC}"
}

print_error() {
    echo -e "${RED}   $1${NC}"
}

print_step "Checking Java toolchain"

# Homebrew's JDK is not registered with macOS by default. Use it when the
# system Java launcher cannot find a runtime.
if ! java -version &>/dev/null && command -v brew &>/dev/null; then
    HOMEBREW_JAVA_HOME="$(brew --prefix openjdk@17 2>/dev/null || true)/libexec/openjdk.jdk/Contents/Home"
    if [ -x "$HOMEBREW_JAVA_HOME/bin/java" ]; then
        export JAVA_HOME="$HOMEBREW_JAVA_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

if ! java -version &>/dev/null; then
    print_error "JDK 17 or newer is required."
    exit 1
fi

JAVA_SPEC_VERSION=$(java -XshowSettings:properties -version 2>&1 |
    awk -F'= ' '/java.specification.version/ { print $2; exit }')
JAVA_MAJOR=${JAVA_SPEC_VERSION#1.}
JAVA_MAJOR=${JAVA_MAJOR%%.*}
if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || [ "$JAVA_MAJOR" -lt 17 ]; then
    print_error "JDK 17 or newer is required (found ${JAVA_SPEC_VERSION:-unknown})."
    exit 1
fi
print_success "$(java -version 2>&1 | head -n 1)"

print_step "Running unit tests, Android lint, and debug build"
if ./gradlew --no-daemon --console=plain test lint assembleDebug; then
    print_success "Tests, lint, and debug build passed"
else
    print_error "Gradle verification failed"
    exit 1
fi

echo -e "\n${GREEN}All checks passed${NC}"
