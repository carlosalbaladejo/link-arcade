# Link Arcade game compatibility specification (v1)

A web game can be added by its launch page. For Android controller remapping and on-screen controls, implement this contract. Games without a manifest still open with their own controls. The web catalog opens imported games as standalone pages, so support keyboard, touch or the browser's Gamepad API independently if you want those players to use them. The web launcher uses the same input snapshots for its own built-in games.

## 1. Publish a launch page and link its manifest

Serve a playable HTML page at a URL reachable from the player's device. Use HTTPS for hosted games; HTTP is supported for local servers after the player accepts the warning. The launch-page URL is the only URL a player enters. Put this declaration in the HTML served to the client, not in script-generated markup:

```html
<link rel="tv-game-manifest" href="./game.json">
```

Here is a complete example manifest. Include a distinct cover image for your game. The field is optional for compatibility with existing games.

```json
{
  "version": 1,
  "title": "Signal Run",
  "cover": "./cover.png",
  "actions": [
    { "id": "move_left", "label": "Move left", "default": "dpad_left" },
    { "id": "move_right", "label": "Move right", "default": "dpad_right" },
    { "id": "jump", "label": "Jump", "default": "south" },
    { "id": "pause", "label": "Pause / resume", "default": "start" }
  ]
}
```

The [JSON Schema](game-manifest.schema.json) defines the fields and allowed buttons. Resolve the manifest URL against the final game page URL (ignore HTML `<base>`); resolve the cover against the final manifest URL. Both must be same-origin (scheme, host, and port) with the game page, including after redirects. Relative paths work in GitHub Pages project subdirectories. A declared but invalid, unreachable, or unsupported manifest is an import error, not a silent downgrade to ordinary mode.

The launcher imports metadata and launches the same resolved game page. The contract does not care whether that page is static or talks to a backend. No special API, hosting provider, installer, or central registration is needed.

## 2. Read logical actions, not physical buttons

In a compatible game, the launcher translates physical and on-screen controller input to the same action IDs. The game receives a JSON string in a standard window `message` event, for example:

```json
{"type":"tv-game-input","version":1,"pressed":["move_right","jump"]}
```

`pressed` is the **complete** set of currently held actions, with unique IDs in unspecified order. An empty list means all actions are released. Register the listener during page startup and start with an empty set. The launcher sends a snapshot on each state change, clears state on controller disconnect and app focus loss, and starts neutral on a new page. It consumes mapped native input during play to avoid delivering the same gesture again as a browser key. Compatible games should not also poll the Gamepad API for the same controller while using launcher input.

```js
let controllerActions = new Set();

window.addEventListener("message", ({ data }) => {
  if (typeof data !== "string") return;
  let message;
  try {
    message = JSON.parse(data);
  } catch {
    return;
  }
  if (
    message?.type !== "tv-game-input" ||
    message.version !== 1 ||
    !Array.isArray(message.pressed)
  ) return;

  const next = new Set(message.pressed);
  if (next.has("pause") && !controllerActions.has("pause")) togglePause();
  if (next.has("jump") && !controllerActions.has("jump")) jump();
  controllerActions = next;
});

window.addEventListener("blur", () => { controllerActions.clear(); });
document.addEventListener("visibilitychange", () => {
  if (document.hidden) controllerActions.clear();
});

// In the update loop, use controllerActions.has("move_left"), etc.
// Keep keyboard input separate so the game can also run in a normal browser.
```

For the native transport, send the string to the top-level WebView document with an explicit target origin (for example, via AndroidX WebKit's feature-checked `postWebMessage`). No JavaScript-to-native interface is needed. Keep top-level gameplay navigation on the saved origin; embedded pages do not receive controller messages. Do not grant catalog, filesystem, or controller-setting access to loaded web content.

The v1 controls are digital. Normalize D-pad/hat events and common Android controller events to the schema's button names. The names `south`, `east`, `west` and `north` refer to positions, not printed letters. `left_stick` and `right_stick` are stick clicks, and triggers are treated as pressed or released even when reported as axes. Continuous analog values, rumble and multi-player controller routing are not part of v1.

Every compatible game declares a `pause` action with `start` as its default. Handle it on press, not continuously while held, and let a second press resume. The game implements the pause: stop local gameplay, or, if a shared online world cannot stop, open a local pause menu and stop sending gameplay inputs. Select has no reserved action; assign it with `"default": "select"` if needed. Other defaults are optional. Defaults unavailable on the on-screen controller leave their actions unbound on that profile until remapped. Give all essential actions an on-screen binding. A manifest must not assign the same default button to multiple actions. If the player reassigns a button, move the binding from its previous action.

### Optional language support

Android Link Arcade sends a JSON string to the top-level game page after load:
`{"type":"tv-game-language","version":1,"language":"es"}`. Register a window
`message` listener during startup, validate the version and accept only
`en` and `es`. Use the message as a locale preference, not as permission
to perform any other operation. Games that do not support the preference
can ignore it. The launcher also sends an `Accept-Language` header on its
direct top-level game request; maintain normal language selection when
the page is played outside Link Arcade.

## 3. Make it playable from the sofa

Build for a responsive landscape viewport, with readable text on TV and on phones when the touch controls reduce the available game height. Do not rely on touching the game area, mouse input, or typing to start and play. If the game has a start, pause, or retry screen, support controller actions there too. Keep a keyboard fallback so the game can be tested in a desktop browser.


## 4. Test and share

1. Check that the game URL, JSON manifest and cover load without signing in, and that the manifest and cover resolve to the game page's origin after any redirects.
2. Try keyboard and touch in a normal mobile or desktop browser. For browser gamepad support, test with a standard-mapped controller after a button press exposes it to the browser.
3. In Android Link Arcade, add the playable page with **Add by URL**, check the title and cover, and try pressing two actions together, releasing them independently, pausing and resuming, and remapping a button.
4. Share the playable page URL. The [controller test manifest](../docs/test-game/game.json) and [receiver code](../docs/test-game/index.html) are working examples.

For browser catalog metadata on another origin, allow cross-origin reads of both the HTML and JSON with `Access-Control-Allow-Origin` for the catalog's origin (or `*` for public assets). This is not required by the Android importer. The browser still saves a playable link when it cannot read the metadata.
