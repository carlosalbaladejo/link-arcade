#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
apk="${1:-build/link-arcade.apk}"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
tools="$sdk/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
version=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)
code=$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' AndroidManifest.xml)
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$code" =~ ^[0-9]+$ ]]
tag="v$version"

git diff --quiet
git diff --cached --quiet
repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
branch=$(gh repo view "$repo" --json defaultBranchRef --jq .defaultBranchRef.name)
commit=$(git rev-parse HEAD)
remote_commit=$(gh api "repos/$repo/commits/$branch" --jq .sha)
if [[ "$commit" != "$remote_commit" ]]; then
    echo "Push the release commit to $branch before publishing." >&2
    exit 1
fi

expected="package: name='app.linkarcade' versionCode='$code' versionName='$version'"
badging=$("$tools/aapt2" dump badging "$apk")
if [[ "$badging" != "$expected "* ]]; then
    echo "APK package or version does not match AndroidManifest.xml." >&2
    exit 1
fi
"$tools/apksigner" verify "$apk"

# Reject another signing key if there is a previous public APK.
previous=$(gh release list --repo "$repo" --limit 30 --json tagName,isDraft,isPrerelease --jq '
    first(.[] | select(.isDraft == false and .isPrerelease == false) | .tagName) // empty')
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
if [[ -n "$previous" && "$previous" != "$tag" ]]; then
    gh release download "$previous" --repo "$repo" --pattern '*.apk' --dir "$temp/previous"
    old_certificate=$("$tools/apksigner" verify --print-certs "$temp/previous/"*.apk | grep 'certificate SHA-256 digest:')
    new_certificate=$("$tools/apksigner" verify --print-certs "$apk" | grep 'certificate SHA-256 digest:')
    if [[ "$old_certificate" != "$new_certificate" ]]; then
        echo "The APK cannot upgrade the previous release: signer mismatch." >&2
        exit 1
    fi
fi
awk -v version="$version" '
    /^## / {
        if (section) exit;
        section = ($2 == "[" version "]" || $2 == version);
        next;
    }
    section && !/^\[[^]]+\]:/ { print; }
' CHANGELOG.md > "$temp/notes.md"
if ! grep -q '[^[:space:]]' "$temp/notes.md"; then
    echo "No changelog notes found for $version." >&2
    exit 1
fi

name="link-arcade-$version.apk"
cp "$apk" "$temp/$name"
(cd "$temp" && sha256sum "$name" > "$name.sha256")
gh release create "$tag" "$temp/$name" "$temp/$name.sha256" --repo "$repo" \
    --target "$commit" --title "Link Arcade $version" --notes-file "$temp/notes.md"
