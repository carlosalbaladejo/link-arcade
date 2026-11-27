#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
bt="$sdk/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
platform="$sdk/platforms/android-34/android.jar"
keystore="${ARCADE_KEYSTORE:-$HOME/.android/debug.keystore}"
mkdir -p lib build/classes build/dex "$(dirname "$keystore")"
assets="$(mktemp -d build/assets.XXXXXX)"
trap 'rm -rf -- "$assets"' EXIT
jsoup=lib/jsoup-1.18.3.jar
if [[ ! -f "$jsoup" ]]; then
    curl -fLsS https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.18.3/jsoup-1.18.3.jar -o "$jsoup"
fi
echo "5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302  $jsoup" | sha256sum --check
if [[ ! -f "$keystore" ]]; then
    keytool -genkeypair -keystore "$keystore" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
find build/classes build/dex -type f -delete
cp docs/default-games.json "$assets/"
"$bt/aapt2" compile --dir res -o build/resources.zip
"$bt/aapt2" link --manifest AndroidManifest.xml -I "$platform" --java build \
    -A "$assets" -o build/base.apk build/resources.zip
mapfile -t java_files < <(find src -name '*.java' -print)
javac --release 8 -cp "$platform:$jsoup" -d build/classes build/app/linkarcade/R.java "${java_files[@]}"
jar cf build/classes.jar -C build/classes .
"$bt/d8" --release --min-api 26 --lib "$platform" --output build/dex build/classes.jar "$jsoup"
zip -q -j build/base.apk build/dex/classes*.dex
"$bt/zipalign" -f 4 build/base.apk build/aligned.apk
"$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --ks-key-alias androiddebugkey \
    --out build/link-arcade.apk build/aligned.apk
"$bt/apksigner" verify --verbose build/link-arcade.apk
