#!/bin/bash
# Build a signed release bundle and upload it to the Play Console internal track.
#
# Usage:
#   ./deploy-release.sh            # keep versionName, bump versionCode
#   ./deploy-release.sh 0.8.1      # set versionName and bump versionCode
#
# Requires two secrets that are deliberately kept out of this public repo:
#   keystore.properties        - upload signing credentials (see app/build.gradle.kts)
#   play-service-account.json  - Google Cloud service account key, granted
#                                "Release apps to testing tracks" on this app in
#                                Play Console -> Users and permissions.
#                                Override the path with PLAY_SERVICE_ACCOUNT_JSON.

set -euo pipefail

cd "$(dirname "$0")"

PACKAGE_NAME="xyz.five82.takeup"
BUILD_FILE="app/build.gradle.kts"
BUNDLE_PATH="app/build/outputs/bundle/release/app-release.aab"
SERVICE_ACCOUNT_JSON="${PLAY_SERVICE_ACCOUNT_JSON:-play-service-account.json}"

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

# The version bump is written to disk before the build, so back it out if
# anything between here and the commit fails. The clean-tree check above makes
# `git checkout --` safe.
version_bumped=""
restore_version() {
    if [ -n "$version_bumped" ]; then
        git checkout -- "$BUILD_FILE"
        print_error "Reverted the version bump in $BUILD_FILE"
    fi
}
trap restore_version EXIT

print_step "Checking preconditions"

if [ -n "$(git status --porcelain)" ]; then
    print_error "Working tree is dirty. Commit or stash first."
    exit 1
fi

if [ ! -f keystore.properties ]; then
    print_error "keystore.properties is missing; the release build would be unsigned."
    exit 1
fi

if [ ! -f "$SERVICE_ACCOUNT_JSON" ]; then
    print_error "$SERVICE_ACCOUNT_JSON is missing."
    print_error "Create it in Google Cloud Console, then grant it access in"
    print_error "Play Console -> Users and permissions. See AGENTS.md."
    exit 1
fi

if ! command -v fastlane &>/dev/null; then
    print_error "fastlane is required: brew install fastlane"
    exit 1
fi

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

current_version_code=$(grep -E '^[[:space:]]*versionCode = ' "$BUILD_FILE" | sed -E 's/.*= *([0-9]+).*/\1/')
current_version_name=$(grep -E '^[[:space:]]*versionName = ' "$BUILD_FILE" | sed -E 's/.*= *"([^"]*)".*/\1/')

new_version_code=$((current_version_code + 1))
new_version_name="${1:-$current_version_name}"

if ! [[ "$new_version_name" =~ ^[0-9A-Za-z.+_-]+$ ]]; then
    print_error "Invalid version name: $new_version_name"
    exit 1
fi

# A no-arg run reuses the version name, so the version code is what makes a
# tag unique. It is semver build metadata, which is exactly what this is.
tag="v${new_version_name}+${new_version_code}"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    print_error "Tag $tag already exists."
    exit 1
fi

print_success "${current_version_name} (${current_version_code}) -> ${new_version_name} (${new_version_code})"

read -r -p "Upload this to the internal testing track? [y/N] " reply
if [[ ! "$reply" =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

print_step "Running local verification"
./check-ci.sh

print_step "Bumping the version"
sed -i '' -E "s/^([[:space:]]*versionCode = ).*/\1${new_version_code}/" "$BUILD_FILE"
sed -i '' -E "s/^([[:space:]]*versionName = ).*/\1\"${new_version_name}\"/" "$BUILD_FILE"
version_bumped="yes"
print_success "$BUILD_FILE updated"

print_step "Building the signed release bundle"
./gradlew --no-daemon --console=plain bundleRelease
print_success "$BUNDLE_PATH"

print_step "Uploading to the internal testing track"
fastlane supply \
    --package_name "$PACKAGE_NAME" \
    --aab "$BUNDLE_PATH" \
    --track internal \
    --release_status completed \
    --json_key "$SERVICE_ACCOUNT_JSON" \
    --skip_upload_metadata true \
    --skip_upload_changelogs true \
    --skip_upload_images true \
    --skip_upload_screenshots true
print_success "Uploaded ${new_version_name} (${new_version_code})"

print_step "Recording the release"
version_bumped=""
git add "$BUILD_FILE"
git commit -m "Bump the version to ${new_version_name} (${new_version_code}) for internal testing"
git tag "$tag"
print_success "Committed and tagged $tag"

branch=$(git rev-parse --abbrev-ref HEAD)
read -r -p "Push $branch and $tag to origin? [y/N] " push_reply
if [[ "$push_reply" =~ ^[Yy]$ ]]; then
    git push origin "$branch" "$tag"
    print_success "Pushed $branch and $tag"
else
    print_success "Not pushed. When ready: git push origin $branch $tag"
fi

echo -e "\n${GREEN}Released ${new_version_name} (${new_version_code}) to internal testing${NC}"
