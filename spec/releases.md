# Publishing a release

GitHub Pages serves the static site from `main:/docs`. Changes there are deployed when pushed to `main`. The web catalog and Android build both use `docs/default-games.json` to pre-add playable URLs. Publish the standalone games first and verify that each default URL, cover and controller action matches the deployed game manifest.

1. Update the version in `AndroidManifest.xml` and increment `versionCode`. Add a matching `## [VERSION]` entry at the top of `CHANGELOG.md`.
2. Build and test with `./build-apk.sh`. For an in-place upgrade, sign with the same private key used for the preceding release. Never commit signing keys, Android credentials or generated builds.
3. Commit and push the changes. With the GitHub CLI authenticated for the repository, run `./scripts/publish-release.sh`. The script checks that the current commit is on the remote default branch, verifies that the APK matches the declared version, and uses that version's changelog section as the GitHub release notes. It attaches the APK and a SHA-256 checksum. An existing, audited APK can be passed as the first argument instead of `build/link-arcade.apk`.
4. Check that the release is visible, that the APK downloads and that the checksum matches. Verify the Pages deployment if the browser files changed.

This project distributes sideloadable builds. The current public APK uses an Android development certificate, not an app-store signing certificate. Install updates from the same trusted release source. Keep the existing signing key for upgrades.
