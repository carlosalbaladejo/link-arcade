package app.linkarcade;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements InputManager.InputDeviceListener {
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> covers = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final Map<String, Frame> catalogCovers = new HashMap<>();
    private final Map<String, Frame> coverRefreshes = new HashMap<>();
    final Controls controls = new Controls();
    Style style;
    private Library library;
    private Settings settings;
    private String language;
    private final Set<String> capturedKeys = new java.util.HashSet<>();
    private PlaySession play;
    LinearLayout screen;
    private InputManager devices;
    private boolean tv;
    private Game mappingGame;
    private String mappingProfile;
    private Game.Action capture;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        style = new Style(this);
        language = "es".equals(java.util.Locale.getDefault().getLanguage()) ? "es" : "en";
        UiModeManager modes = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        tv = modes.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION
                || getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")
                || getPackageManager().hasSystemFeature("android.software.leanback");
        devices = (InputManager) getSystemService(Context.INPUT_SERVICE);
        devices.registerInputDeviceListener(this, null);
        getWindow().setStatusBarColor(Style.BG);
        getWindow().setNavigationBarColor(Style.BG);
        try {
            library = new Library(this);
            language = library.language(language);
            settings = new Settings(this, library.profiles);
            catalog(null);
        } catch (Exception error) {
            page();
            style.append(screen, style.text(t("Could not open the catalog.", "No se pudo abrir el catálogo."), 22, Style.TEXT), 0);
        }
    }

    String language() { return language; }

    boolean spanish() { return "es".equals(language); }

    boolean isTv() { return tv; }

    String t(String en, String es) { return spanish() ? es : en; }

    void changeLanguage(String value) {
        if (library.saveLanguage(value)) language = value;
        else toast(t("Could not save the language.", "No se pudo guardar el idioma."));
    }

    void page() {
        catalogCovers.clear();
        setPlayCutout(false);
        getWindow().getDecorView().setSystemUiVisibility(tv ? immersive() : 0);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Style.BG);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        screen = style.column();
        int side = style.dp(tv ? 52 : 18);
        screen.setPadding(side, style.dp(tv ? 36 : 24), side, side);
        scroll.addView(screen, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private int immersive() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (!tv && play != null) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        }
        return flags;
    }

    private void setPlayCutout(boolean playing) {
        if (tv || android.os.Build.VERSION.SDK_INT < 28) return;
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.layoutInDisplayCutoutMode = playing
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        getWindow().setAttributes(attributes);
    }

    void heading(String title, String subtitle) {
        TextView text = style.text(title, tv ? 38 : 32, Style.TEXT);
        text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        style.append(screen, text, 0);
        if (!subtitle.isEmpty()) style.append(screen, style.text(subtitle, 15, Style.MUTED), 5);
    }

    Button appendButton(LinearLayout parent, String title, int margin, Runnable action) {
        Button button = style.button(title);
        button.setOnClickListener(view -> action.run());
        style.append(parent, button, margin);
        return button;
    }

    void catalog(Game returnTo) {
        capture = null;
        mappingGame = null;
        mappingProfile = null;
        page();
        heading("Link Arcade", t("Web game catalog", "Catálogo de juegos web"));
        LinearLayout toolbar = style.row();
        style.append(screen, toolbar, 18);
        Button add = style.button(t("+ Add by URL", "+ Añadir por URL"));
        add.setOnClickListener(view -> addDialog());
        toolbar.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        Button settingsButton = style.button(t("Settings", "Configuración"));
        settingsButton.setOnClickListener(view -> settings.show());
        LinearLayout.LayoutParams settingsLayout = new LinearLayout.LayoutParams(0, -2, 1);
        settingsLayout.leftMargin = style.dp(8);
        toolbar.addView(settingsButton, settingsLayout);
        Game focus = returnTo;
        LinearLayout row = null;
        List<Game> entries = new ArrayList<>();
        entries.addAll(library.games);
        for (int i = 0; i < entries.size(); i++) {
            Game game = entries.get(i);
            if (!tv || i % 3 == 0) {
                row = style.row();
                row.setGravity(Gravity.TOP);
                style.append(screen, row, 14);
            }
            View card = tile(game);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (tv && i % 3 != 0) params.leftMargin = style.dp(12);
            row.addView(card, params);
            if (focus != null && game.url.equals(focus.url)) card.requestFocus();
            if (tv && i == entries.size() - 1) {
                for (int column = i % 3 + 1; column < 3; column++) {
                    LinearLayout.LayoutParams filler = new LinearLayout.LayoutParams(0, 1, 1);
                    filler.leftMargin = style.dp(12);
                    row.addView(new View(this), filler);
                }
            }
        }
        if (focus == null && tv) add.requestFocus();
    }

    private View tile(Game game) {
        LinearLayout tile = style.column();
        tile.setPadding(style.dp(9), style.dp(9), style.dp(9), style.dp(9));
        tile.setBackground(style.panel(Style.CARD, Style.EDGE, 15));
        tile.setFocusable(true);
        tile.setContentDescription(game.title + (game.compatible() ? t(", gamepad compatible", ", compatible con mando") : t(", web page", ", página web")));
        tile.setOnFocusChangeListener((view, focused) -> tile.setBackground(style.panel(
                focused ? 0xff244a50 : Style.CARD, focused ? Style.MINT : Style.EDGE, 15)));
        Frame image = new Frame(game);
        catalogCovers.put(game.url, image);
        loadSavedCover(game, image);
        tile.addView(image.view, new LinearLayout.LayoutParams(-1, style.dp(tv ? 94 : 82)));
        TextView title = style.text(game.title, 18, Style.TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        style.append(tile, title, 7);
        style.append(tile, style.text(game.compatible() ? t("Gamepad compatible", "Compatible con mando") : t("Web page", "Página web"), 13, Style.MUTED), 3);
        tile.setOnClickListener(view -> details(library.find(game.url)));
        return tile;
    }

    private final class Frame {
        final ImageView view = new ImageView(MainActivity.this);

        Frame(Game game) {
            view.setBackground(style.panel(Style.BG, 0, 8));
            view.setClipToOutline(true);
            view.setImageResource(R.drawable.arcade);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap cached = covers.get(game.url);
            if (cached != null) show(cached);
        }

        void show(Bitmap cover) {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(cover);
        }
    }

    private void loadSavedCover(Game game, Frame frame) {
        if (game.cover.isEmpty() || covers.get(game.url) != null) return;
        boolean saved = library.find(game.url) == game;
        network.execute(() -> {
            try {
                Bitmap image = Imports.cover(game);
                if (image == null) return;
                runOnUiThread(() -> {
                    if (isDestroyed() || (saved && library.find(game.url) != game)) return;
                    Bitmap current = covers.get(game.url);
                    if (current == null && library.find(game.url) == game) covers.put(game.url, image);
                    frame.show(current == null ? image : current);
                    Frame detail = coverRefreshes.get(game.url);
                    if (detail != null && current == null) detail.show(image);
                });
            } catch (Exception ignored) {
                // Keep the current cover or placeholder while the game is unavailable.
            }
        });
    }

    private void refreshCover(Game game, Frame detail) {
        coverRefreshes.put(game.url, detail);
        network.execute(() -> {
            try {
                URI address = Imports.webUri(game.url);
                Game inspected = Imports.inspect(address, Imports.origin(address));
                if (!inspected.cover.isEmpty()) {
                    Bitmap image = Imports.cover(inspected);
                    if (image != null) {
                        runOnUiThread(() -> applyCover(game, detail, inspected.cover, image));
                    }
                }
            } catch (Exception ignored) {
                // A failed refresh must not replace a working cover or interrupt play.
            } finally {
                runOnUiThread(() -> coverRefreshes.remove(game.url, detail));
            }
        });
    }

    private void applyCover(Game game, Frame detail, String url, Bitmap image) {
        if (isDestroyed() || coverRefreshes.get(game.url) != detail || library.find(game.url) != game) return;
        try {
            library.updateCover(game, url);
        } catch (Exception ignored) {
            return;
        }
        covers.put(game.url, image);
        detail.show(image);
        Frame tile = catalogCovers.get(game.url);
        if (tile != null) tile.show(image);
    }

    private void details(Game game) {
        if (game == null) return;
        mappingGame = null;
        capture = null;
        Frame image = new Frame(game);
        LinearLayout panel = style.column();
        panel.setPadding(style.dp(24), style.dp(6), style.dp(24), style.dp(8));
        panel.addView(image.view, new LinearLayout.LayoutParams(-1, style.dp(100)));
        new AlertDialog.Builder(this).setTitle(game.title)
                .setView(panel)
                .setMessage(game.compatible() ? t("Works with Link Arcade gamepad controls.", "Compatible con los controles de Link Arcade.")
                        : t("Uses the web page’s own controls.", "Usa los controles de la página web."))
                .setPositiveButton(t("Play", "Jugar"), (dialog, button) -> open(game))
                .setNeutralButton(game.compatible() ? t("Controls", "Controles") : t("Close", "Cerrar"), (dialog, button) -> {
                    if (game.compatible()) profiles(game);
                })
                .setNegativeButton(t("Remove", "Quitar"), (dialog, button) -> confirmRemove(game)).show();
        refreshCover(game, image);
    }

    private void confirmRemove(Game game) {
        new AlertDialog.Builder(this).setTitle(t("Remove from your library?", "¿Quitar de tu biblioteca?"))
                .setMessage(game.title).setNegativeButton(t("Cancel", "Cancelar"), null)
                .setPositiveButton(t("Remove", "Quitar"), (dialog, button) -> {
                    try {
                        library.remove(game);
                        covers.remove(game.url);
                        coverRefreshes.remove(game.url);
                        catalog(null);
                    } catch (Exception error) {
                        toast(error.getMessage());
                    }
                }).show();
    }

    private void addDialog() {
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://… or http://…");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setPadding(style.dp(24), style.dp(12), style.dp(24), style.dp(12));
        AlertDialog input = new AlertDialog.Builder(this).setTitle(t("Add game", "Añadir juego"))
                .setView(url).setNegativeButton(t("Cancel", "Cancelar"), null).setPositiveButton(t("Look up", "Buscar"), null).create();
        input.setOnShowListener(dialog -> {
            url.requestFocus();
            Button search = input.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable lookup = () -> {
                URI address;
                try {
                    address = Imports.webUri(url.getText().toString().trim());
                } catch (IOException error) {
                    url.setError(error.getMessage());
                    return;
                }
                if (library.duplicate(address.toString()) != null) {
                    showDuplicate(url);
                    return;
                }
                if ("http".equals(address.getScheme())) {
                    AlertDialog warning = new AlertDialog.Builder(this).setTitle(t("Unencrypted HTTP connection", "Conexión HTTP sin cifrar"))
                            .setMessage(Imports.origin(address) + "\n\n"
                                    + t("Other people on the network could see or change game data. Continue only if you trust the site and network. Do not enter passwords.",
                                    "Otras personas en la red podrían ver o modificar los datos. Continúa solo si confías en la web y la red. No introduzcas contraseñas."))
                            .setNegativeButton(t("Cancel", "Cancelar"), null)
                            .setPositiveButton(t("Continue", "Continuar"), (prompt, button) -> lookupGame(input, url, search, address))
                            .show();
                    warning.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
                } else {
                    lookupGame(input, url, search, address);
                }
            };
            search.setOnClickListener(view -> lookup.run());
            url.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
            url.setOnEditorActionListener((view, action, event) -> {
                if (action != android.view.inputmethod.EditorInfo.IME_ACTION_GO) return false;
                if (search.isEnabled()) lookup.run();
                return true;
            });
        });
        input.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        input.show();
    }

    private void lookupGame(AlertDialog input, EditText url, Button search, URI address) {
        url.setError(null);
        search.setEnabled(false);
        search.setText(t("Looking up…", "Buscando…"));
        network.execute(() -> {
            try {
                Game game = Imports.inspect(address);
                runOnUiThread(() -> {
                    if (!input.isShowing() || isFinishing()) return;
                    if (library.duplicate(game.url) != null) {
                        search.setEnabled(true);
                        search.setText(t("Look up", "Buscar"));
                        showDuplicate(url);
                        return;
                    }
                    input.dismiss();
                    preview(game);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!input.isShowing() || isFinishing()) return;
                    search.setEnabled(true);
                    search.setText(t("Look up", "Buscar"));
                    url.setError(t("Could not import: ", "No se pudo importar: ") + error.getMessage());
                });
            }
        });
    }

    private void showDuplicate(EditText url) {
        url.requestFocus();
        url.setError(duplicateMessage());
    }

    private String duplicateMessage() {
        return t("This game is already in your library.", "Este juego ya está añadido a tu biblioteca.");
    }

    private void preview(Game game) {
        LinearLayout panel = style.column();
        panel.setPadding(style.dp(18), style.dp(8), style.dp(18), style.dp(12));
        Frame image = new Frame(game);
        panel.addView(image.view, new LinearLayout.LayoutParams(-1, style.dp(100)));
        loadSavedCover(game, image);
        style.append(panel, style.text(game.compatible() ? t("Gamepad compatible", "Compatible con mando") : t("Web page with its own controls", "Página web con controles propios"), 15, Style.MUTED), 8);
        new AlertDialog.Builder(this).setTitle(game.title).setView(panel)
                .setNegativeButton(t("Cancel", "Cancelar"), null).setPositiveButton(t("Save", "Guardar"), (dialog, button) -> {
                    try {
                        library.add(game);
                        catalog(game);
                    } catch (Library.DuplicateGame duplicate) {
                        toast(duplicateMessage());
                    } catch (Exception error) {
                        toast(error.getMessage());
                    }
                }).show();
    }

    private void profiles(Game game) {
        capture = null;
        mappingGame = game;
        mappingProfile = null;
        page();
        heading(t("Controls", "Controles"), game.title);
        appendButton(screen, t("‹ Catalog", "‹ Catálogo"), 16, () -> catalog(game));
        if (!tv) appendButton(screen, t("On-screen controls", "Controles en pantalla"), 12, () -> bindings(game, "touch", null));
        Set<String> seen = new java.util.HashSet<>();
        for (InputDevice device : Controls.devices()) {
            String profile = Controls.profile(device);
            String assigned = library.profiles.assigned(Controls.identity(device));
            if (!assigned.isEmpty()) {
                style.append(screen, style.text(device.getName() + " · " + assigned + "\n"
                        + t("Managed in Controller profiles.", "Se gestiona en Perfiles de mando."), 15, Style.MUTED), 12);
            } else if (seen.add(profile)) {
                appendButton(screen, device.getName(), 12, () -> bindings(game, profile, null));
            }
        }
        if (Controls.devices().isEmpty()) style.append(screen, style.text(t("Pair a Bluetooth gamepad in your device settings.", "Empareja un mando Bluetooth en los ajustes del dispositivo."), 15, Style.MUTED), 12);
    }

    private void bindings(Game game, String profile, String focusId) {
        mappingGame = game;
        mappingProfile = profile;
        capture = null;
        page();
        heading(t("Map buttons", "Asignar botones"), game.title);
        appendButton(screen, t("‹ Controllers", "‹ Mandos"), 16, () -> profiles(game));
        Map<String, String> mapping = library.bindings(game, profile);
        for (Game.Action action : game.actions) {
            String id = mapping.get(action.id);
            String label = id == null || id.isEmpty() ? t("Unassigned", "Sin asignar")
                    : profile.equals("touch") ? TouchPad.label(id) : settings.label(id);
            Button row = appendButton(screen, action.name + "   ·   " + label, 8,
                    () -> chooseBinding(game, profile, action));
            if (action.id.equals(focusId)) row.requestFocus();
        }
        appendButton(screen, t("Restore game defaults", "Restaurar controles del juego"), 14, () -> {
            try {
                library.reset(game, profile);
                bindings(game, profile, null);
            } catch (Exception error) {
                toast(error.getMessage());
            }
        });
    }

    private void chooseBinding(Game game, String profile, Game.Action action) {
        if (profile.equals("touch")) {
            List<String> options = new ArrayList<>(game.touchButtons());
            options.add("");
            String[] labels = new String[options.size()];
            for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).isEmpty() ? t("Unassigned", "Sin asignar") : TouchPad.label(options.get(i));
            new AlertDialog.Builder(this).setTitle(action.name).setItems(labels,
                    (dialog, position) -> saveBinding(action, options.get(position))).setNegativeButton(t("Cancel", "Cancelar"), null).show();
            return;
        }
        capture = action;
        page();
        heading(action.name, "Press the button or direction you want to assign.");
        appendButton(screen, t("Cancel", "Cancelar"), 16, () -> bindings(game, profile, action.id));
        appendButton(screen, t("Leave unassigned", "Dejar sin asignar"), 8, () -> saveBinding(action, ""));
    }

    private void saveBinding(Game.Action action, String button) {
        try {
            library.bind(mappingGame, mappingProfile, action.id, button);
            bindings(mappingGame, mappingProfile, action.id);
        } catch (Exception error) {
            toast(error.getMessage());
        }
    }

    static String buttonLabel(String id) {
        switch (id == null ? "" : id) {
            case "dpad_up": return "D-pad up";
            case "dpad_down": return "D-pad down";
            case "dpad_left": return "D-pad left";
            case "dpad_right": return "D-pad right";
            case "south": return "A · bottom";
            case "east": return "B · right";
            case "west": return "X · left";
            case "north": return "Y · top";
            case "left_shoulder": return "L1";
            case "right_shoulder": return "R1";
            case "left_trigger": return "L2";
            case "right_trigger": return "R2";
            case "left_stick": return "L3";
            case "right_stick": return "R3";
            case "start": return "Start";
            case "select": return "Select";
            default: return "Unassigned";
        }
    }

    private void open(Game game) {
        clearInputs();
        try {
            PlaySession session = new PlaySession(this, game, tv, Controls.devices().isEmpty());
            play = session;
            if (!tv) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            setPlayCutout(true);
            getWindow().getDecorView().setSystemUiVisibility(immersive());
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setContentView(play.content);
            play.start();
        } catch (Exception error) {
            play = null;
            toast(t("Could not open the device browser.", "No se pudo abrir el navegador del dispositivo."));
        }
    }

    void closeGame() {
        if (play == null) return;
        Game game = play.game;
        clearInputs();
        play.destroy();
        play = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!tv) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        catalog(game);
    }

    void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    void touchButton(String id, boolean down) {
        if (play == null) return;
        if (controls.set("touch", id, down ? Collections.singleton(id) : Collections.emptySet())) dispatchActions();
    }

    void clearTouch() {
        if (controls.held.remove("touch") != null) dispatchActions();
    }

    void clearInputs() {
        controls.held.clear();
        dispatchActions();
    }

    private void dispatchActions() {
        if (play != null && play.game.compatible()) play.actions(library.active(play.game, controls.held));
        if (settings != null) settings.updateTest();
    }

    private boolean captureInput(InputDevice device, String button) {
        if (settings != null && settings.consumesInput()) {
            settings.press(button);
            return true;
        }
        if (capture == null || !Controls.profile(device).equals(mappingProfile) || !Game.BUTTONS.contains(button)) return false;
        saveBinding(capture, button);
        return true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        String button = Controls.button(event.getKeyCode());
        if (button != null && Controls.controller(device)) {
            String identity = Controls.identity(device);
            String source = device.getId() + ":key:" + button;
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN && !event.isCanceled();
            boolean changed = controls.set(identity, source,
                    down ? Collections.singleton(button) : Collections.emptySet());
            boolean alreadyCaptured = down ? capturedKeys.contains(source) : capturedKeys.remove(source);
            boolean reading = settings != null && settings.consumesInput();
            if (changed && down && event.getRepeatCount() == 0 && captureInput(device, button)) {
                capturedKeys.add(source);
                dispatchActions();
                return true;
            }
            if (changed) dispatchActions();
            if (alreadyCaptured || reading || (play != null && play.game.compatible() && play.acceptsInput())) return true;
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A && event.getAction() != KeyEvent.ACTION_MULTIPLE) {
                KeyEvent click = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                        KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(),
                        event.getDeviceId(), event.getScanCode(), event.getFlags(), event.getSource());
                return super.dispatchKeyEvent(click);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (Controls.controller(device) && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            Set<String> buttons = Controls.axes(event);
            String identity = Controls.identity(device);
            String source = device.getId() + ":axes";
            Set<String> previous = controls.held.getOrDefault(identity, Collections.emptyMap())
                    .getOrDefault(source, Collections.emptySet());
            boolean changed = controls.set(identity, source, buttons);
            if (changed) {
                if (previous.isEmpty() && buttons.size() == 1) {
                    String button = buttons.iterator().next();
                    if (capture != null && button.startsWith("left_stick_")) {
                        button = "dpad_" + button.substring("left_stick_".length());
                    }
                    captureInput(device, button);
                }
                dispatchActions();
            }
            if (play != null || (settings != null && settings.consumesInput())) return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onInputDeviceAdded(int id) {
        if (settings != null) settings.devicesChanged();
        if (mappingGame != null && mappingProfile == null) profiles(mappingGame);
    }

    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }

    @Override public void onInputDeviceRemoved(int id) {
        controls.removeDevice(id);
        capturedKeys.removeIf(source -> source.startsWith(id + ":"));
        dispatchActions();
        onInputDeviceAdded(id);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) clearInputs();
        else if (play != null) getWindow().getDecorView().setSystemUiVisibility(immersive());
    }

    @Override protected void onPause() {
        clearInputs();
        if (play != null) play.suspend();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (play != null) play.resume();
    }

    @Override public void onBackPressed() {
        if (play != null) closeGame();
        else if (capture != null) bindings(mappingGame, mappingProfile, capture.id);
        else if (mappingProfile != null) profiles(mappingGame);
        else if (mappingGame != null) catalog(mappingGame);
        else if (settings != null && settings.back()) return;
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        devices.unregisterInputDeviceListener(this);
        network.shutdownNow();
        if (settings != null) settings.destroy();
        if (play != null) play.destroy();
        super.onDestroy();
    }
}
