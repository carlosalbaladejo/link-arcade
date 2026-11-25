#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
bt="$sdk/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
platform="$sdk/platforms/android-34/android.jar"
keystore="${ARCADE_KEYSTORE:-$HOME/.android/debug.keystore}"
mkdir -p lib build/classes build/dex build/assets/controller-test build/assets/driving build/driving-res/drawable-nodpi "$(dirname "$keystore")"
jsoup=lib/jsoup-1.18.3.jar
if [[ ! -f "$jsoup" ]]; then
    curl -fLsS https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.18.3/jsoup-1.18.3.jar -o "$jsoup"
fi
echo "5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302  $jsoup" | sha256sum --check
driving_revision=94ee968da4c3aaa6ff106946c5fde8ff84569827
driving_archive="lib/little-drive-$driving_revision.tar.gz"
if [[ ! -f "$driving_archive" ]]; then
    curl -fLsS "https://codeload.github.com/carlosalbaladejo/little-drive/tar.gz/$driving_revision" -o "$driving_archive.part"
    mv "$driving_archive.part" "$driving_archive"
fi
echo "8aeece0336f043531565558296b4bd3c2725524631f63c1aec10e564a50c2e3a  $driving_archive" | sha256sum --check
if [[ ! -f "$keystore" ]]; then
    keytool -genkeypair -keystore "$keystore" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
find build/classes build/dex -type f -delete
cp docs/test-game/{index.html,game.json,cover.png} build/assets/controller-test/
driving_root="little-drive-$driving_revision/docs"
tar -xzf "$driving_archive" -C build/assets/driving --strip-components=2 "$driving_root"/{index.html,game.json,cover.jpg,drive.js,browser-controls.js,world.js,city.js,traffic.js,audio.js,effects.js,navigation.js,style.css,vendor/three.module.min.js,vendor/LICENSE}
cp build/assets/driving/cover.jpg build/driving-res/drawable-nodpi/driving_cover.jpg
"$bt/aapt2" compile --dir res -o build/resources.zip
"$bt/aapt2" compile --dir build/driving-res -o build/driving-resources.zip
"$bt/aapt2" link --manifest AndroidManifest.xml -I "$platform" --java build \
    -A build/assets -o build/base.apk build/resources.zip build/driving-resources.zip
mapfile -t java_files < <(find src -name '*.java' -print)
javac --release 8 -cp "$platform:$jsoup" -d build/classes build/app/linkarcade/R.java "${java_files[@]}"
jar cf build/classes.jar -C build/classes .
"$bt/d8" --release --min-api 26 --lib "$platform" --output build/dex build/classes.jar "$jsoup"
zip -q -j build/base.apk build/dex/classes*.dex
"$bt/zipalign" -f 4 build/base.apk build/aligned.apk
"$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --ks-key-alias androiddebugkey \
    --out build/link-arcade.apk build/aligned.apk
"$bt/apksigner" verify --verbose build/link-arcade.apk
