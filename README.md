# Link Arcade

Link Arcade is a simple launcher for web games. Play in a browser or install the Android app on a phone or a Fire OS device. No account is required.

- [Play in your browser](https://carlosalbaladejo.github.io/link-arcade/). The included games work with a keyboard, on-screen controls or a standard-mapped gamepad supported by your browser.
- [Download the latest Android APK](https://github.com/carlosalbaladejo/link-arcade/releases/latest). Compatible games can use paired controllers, per-game button mappings, and on-screen controls on phones.

The browser and Android app start with the same pre-added game links. They open the original websites and require a connection, just like links you add yourself. You can remove or re-add them; the built-in **Controller Test** works offline.

The browser and Android app keep separate libraries on the device. Game progress is managed by each game, not by Link Arcade. Saves from the old, offline copy of A Little Drive do not transfer to the standalone website.

## Add a web game

Open **Add by URL** and paste the address of the playable page, not the address of its repository or JSON file. Any game added this way stays in your library; it is not added to everyone else's catalog.

- **Android:** Preview and save the game. Use HTTPS where possible. HTTP is allowed after an unencrypted-connection warning. A compatible game gets the launcher controls; a normal web game uses its own.
- **Browser:** Added games are saved in this browser and stay in the library when you come back. They don't sync across devices or browser profiles, and clearing the site's data removes them. Games open in a new tab and use their own controls. If the host allows the browser to read its metadata (same origin or CORS), Link Arcade shows its title and cover; otherwise it saves the link with the site's hostname.

## Make a game or extend Link Arcade

To integrate with the Android controller mapper, follow the [Link Arcade game compatibility specification](spec/game-compatibility.md) and [manifest schema](spec/game-manifest.schema.json). [A Little Drive](https://carlosalbaladejo.github.io/little-drive/) is a complete example: its [standalone repository](https://github.com/carlosalbaladejo/little-drive) shows how to publish a game, declare its cover and controller actions, and support play outside the launcher. The [Controller Test](docs/test-game/index.html) is a minimal example. Publish your game page, manifest and cover to the same origin and share the page URL. No registration or new APK is needed.

The browser launcher lives in [docs/](docs/); the Android launcher lives in [src/](src/). From the repository root, run `python3 -m http.server 8080 --bind 127.0.0.1 --directory docs` to try the site locally.

To build the APK, use JDK 17, Android SDK Platform 34, Build Tools 34.0.0, curl and zip. Set `ANDROID_SDK_ROOT` if needed, then run `./build-apk.sh`. The build downloads and verifies jsoup and signs the APK as `build/link-arcade.apk`. Keep signing keys private. Android updates require the same signing key as the previous installation.

See the [changelog](CHANGELOG.md) and [release instructions](spec/releases.md).
