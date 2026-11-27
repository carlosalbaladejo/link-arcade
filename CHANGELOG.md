# Changelog

## [0.1.18]

- Add cancelable game loading and automatic retries for temporary connection failures.
- Add English and Spanish settings; supported games inherit the launcher's selected language.
- Add an offline controller test with visual and audio feedback, and reusable profiles for remapping and assigning controllers.
- Keep the three starter games as links, without bundling their runtimes.
- Warn when a game is already saved, including when its link redirects to a saved page.

## [0.1.15]

- Pre-added Tidy Robot and Firemen Route by URL in both the Android and browser libraries.

## [0.1.14]

- Pre-added A Little Drive by URL in both the Android and browser libraries. The link can be removed and re-added like any other game.
- Removed the offline copy of A Little Drive from the APK. The standalone game keeps the lightweight Fire TV rendering profile; the Controller Test still works offline.

## [0.1.13]

- Added a lighter rendering profile for A Little Drive on Fire TV, with less graphics work per frame and no change to driving controls or saves.
- Avoided building unused touch overlays on TV and switched compatible games to launcher input as soon as they load.
- Moved the driving game to its own repository as A Little Drive. The browser catalog opens the standalone game by URL; the Android build retains an offline copy from a pinned revision.
- Added a GitHub repository link to the web catalog. Saved game links now stay in sync across open tabs in the same browser.

## [0.1.12]

- Localized the browser catalog, Android app and bundled game interfaces in English. Paseo Costero keeps its original title, place names and radio stations.

## [0.1.11]

- Published Link Arcade's source, playable browser catalog, example games and game compatibility specification. The browser catalog supports local URL bookmarks and keyboard, touch and standard controller input in the included games.
- Paseo Costero now has collisions with roadside scenery, trees and bridge rails. Hold the horn button for a continuous sound; releasing it, pausing or leaving the game stops it.

## [0.1.10]

- Paseo Costero now generates more towns in every direction. Added eastern bridges, extended roads and updated traffic and the minimap to follow the new network.

## [0.1.9]

- Added the town of Valdecerro, a signed route, curved roads, an optional minimap and a compass. Softened the gravel sound.

## [0.1.8]

- Added new radio tracks, improved vehicle and surface audio, and added water recovery and more varied woodland. Added a driving timer and optional health display.

## [0.1.7]

- Tightened parking bays, moved the radio display next to the location, and removed the separate in-game horn button. Controller and keyboard horn bindings remain.

## [0.1.6]

- Added marked parking bays to Paseo Costero, with visual and audio feedback for completing a full stop inside a bay.

## [0.1.5]

- Bundled Paseo Costero as the first offline game, with city traffic, service vehicles, radio, driving effects and optional vehicle health.
- Refresh imported game covers when selecting a game, retaining the previous cover if refresh fails.

## [0.1.4]

- Allowed HTTP game links after an explicit unencrypted-connection warning. HTTPS imports still cannot redirect to HTTP.

## [0.1.3]

- Added the first bundled version of Paseo Costero, a procedural 3D driving game with local progress. Added an in-game switch between touch and physical controls in the bundled game.

## [0.1.2]

- Separated and outlined the touch controls so that the game viewport remains full-size beneath them.

## [0.1.1]

- Reworked the touch D-pad and face buttons, preserving Start, Select and existing mappings.

## [0.1.0]

- Introduced the Android launcher, URL imports, a persistent local library, controller remapping and an offline controller test.

[0.1.12]: https://github.com/carlosalbaladejo/link-arcade/releases/tag/v0.1.12
[0.1.11]: https://github.com/carlosalbaladejo/link-arcade/releases/tag/v0.1.11
