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
import java.io.InputStream;
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
    private final Controls controls = new Controls();
    Style style;
    private Library library;
    private Game diagnostic;
    private Game driving;
    private PlaySession play;
    private LinearLayout screen;
    private InputManager devices;
    private boolean tv;
    private Game mappingGame;
    private String mappingProfile;
    private Game.Action capture;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        style = new Style(this);
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
            diagnostic = bundled(Game.TEST_URL, "controller-test/game.json");
            driving = bundled(Game.DRIVE_URL, "driving/game.json");
            catalog(null);
        } catch (Exception error) {
            page();
            style.append(screen, style.text("No se pudo abrir el catálogo.", 22, Style.TEXT), 0);
        }
    }

    private Game bundled(String url, String manifest) throws Exception {
        try (InputStream stream = getAssets().open(manifest)) {
            return Game.fromManifest(url, "", Imports.manifest(Imports.read(stream, 65536)));
        }
    }

    private void page() {
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

    private void heading(String title, String subtitle) {
        TextView text = style.text(title, tv ? 38 : 32, Style.TEXT);
        text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        style.append(screen, text, 0);
        if (!subtitle.isEmpty()) style.append(screen, style.text(subtitle, 15, Style.MUTED), 5);
    }

    private Button appendButton(LinearLayout parent, String title, int margin, Runnable action) {
        Button button = style.button(title);
        button.setOnClickListener(view -> action.run());
        style.append(parent, button, margin);
        return button;
    }

    private void catalog(Game returnTo) {
        capture = null;
        mappingGame = null;
        mappingProfile = null;
        page();
        heading("Link Arcade", "Catálogo de juegos web");
        Button add = appendButton(screen, "+  Añadir por URL", 18, this::addDialog);
        Game focus = returnTo;
        LinearLayout row = null;
        List<Game> entries = new ArrayList<>();
        entries.add(driving);
        entries.addAll(library.games);
        entries.add(diagnostic);
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
        tile.setContentDescription(game.title + (game.compatible() ? ", con mando" : ", página web"));
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
        style.append(tile, style.text(game.driving() ? "Conducción libre · sin internet" : game.builtin() ? "Prueba incluida · sin internet"
                : game.compatible() ? "Compatible con mando" : "Página web", 13, Style.MUTED), 3);
        tile.setOnClickListener(view -> details(game.builtin() ? game : library.find(game.url)));
        return tile;
    }

    private final class Frame {
        final ImageView view = new ImageView(MainActivity.this);

        Frame(Game game) {
            view.setBackground(style.panel(Style.BG, 0, 8));
            view.setClipToOutline(true);
            view.setImageResource(game.driving() ? R.drawable.driving_cover : R.drawable.arcade);
            view.setScaleType(game.driving() ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            if (game == diagnostic) {
                try (InputStream stream = getAssets().open("controller-test/cover.png")) {
                    Bitmap cover = BitmapFactory.decodeStream(stream);
                    if (cover != null) show(cover);
                } catch (IOException ignored) {
                    // Keep the launcher icon if the local image is unavailable.
                }
            }
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
                .setMessage(game.driving() ? "← → Girar · × Acelerar · □ Frenar / marcha atrás · ○ Claxon · △ Radio" :
                        game.builtin() ? "Comprueba los diez botones, la pausa y dos pulsaciones a la vez."
                        : game.compatible() ? "Compatible con el mando de Link Arcade." : "Usa los controles de la página.")
                .setPositiveButton("Jugar", (dialog, button) -> open(game))
                .setNeutralButton(game.compatible() ? "Controles" : "Cerrar", (dialog, button) -> {
                    if (game.compatible()) profiles(game);
                })
                .setNegativeButton(game.builtin() ? "Cancelar" : "Quitar", (dialog, button) -> {
                    if (!game.builtin()) confirmRemove(game);
                }).show();
        if (!game.builtin()) refreshCover(game, image);
    }

    private void confirmRemove(Game game) {
        new AlertDialog.Builder(this).setTitle("¿Quitar del catálogo?")
                .setMessage(game.title).setNegativeButton("Cancelar", null)
                .setPositiveButton("Quitar", (dialog, button) -> {
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
        url.setHint("https://… o http://…");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setPadding(style.dp(24), style.dp(12), style.dp(24), style.dp(12));
        AlertDialog input = new AlertDialog.Builder(this).setTitle("Añadir juego")
                .setView(url).setNegativeButton("Cancelar", null).setPositiveButton("Buscar", null).create();
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
                if ("http".equals(address.getScheme())) {
                    AlertDialog warning = new AlertDialog.Builder(this).setTitle("Conexión HTTP sin cifrar")
                            .setMessage(Imports.origin(address) + "\n\n"
                                    + "Otras personas en la red podrían ver o modificar los datos del juego. "
                                    + "Continúa solo si confías en el sitio y en la red. No introduzcas contraseñas.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Continuar", (prompt, button) -> lookupGame(input, url, search, address))
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
        search.setEnabled(false);
        search.setText("Buscando…");
        network.execute(() -> {
            try {
                Game game = Imports.inspect(address);
                runOnUiThread(() -> {
                    if (!input.isShowing() || isFinishing()) return;
                    input.dismiss();
                    preview(game);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!input.isShowing() || isFinishing()) return;
                    search.setEnabled(true);
                    search.setText("Buscar");
                    url.setError("No se pudo importar: " + error.getMessage());
                });
            }
        });
    }

    private void preview(Game game) {
        for (Game saved : library.games) {
            if (saved.url.equals(game.url)) {
                toast("Este juego ya está en el catálogo.");
                return;
            }
        }
        LinearLayout panel = style.column();
        panel.setPadding(style.dp(18), style.dp(8), style.dp(18), style.dp(12));
        Frame image = new Frame(game);
        panel.addView(image.view, new LinearLayout.LayoutParams(-1, style.dp(100)));
        loadSavedCover(game, image);
        style.append(panel, style.text(game.compatible() ? "Compatible con mando" : "Página web sin integración", 15, Style.MUTED), 8);
        new AlertDialog.Builder(this).setTitle(game.title).setView(panel)
                .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", (dialog, button) -> {
                    try {
                        library.add(game);
                        catalog(game);
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
        heading("Controles", game.title);
        appendButton(screen, "‹ Catálogo", 16, () -> catalog(game));
        if (!tv) appendButton(screen, "Mando en pantalla", 12, () -> bindings(game, "touch", null));
        Set<String> seen = new java.util.HashSet<>();
        for (InputDevice device : Controls.devices()) {
            String profile = Controls.profile(device);
            if (seen.add(profile)) appendButton(screen, device.getName(), 12, () -> bindings(game, profile, null));
        }
        if (seen.isEmpty()) style.append(screen, style.text("Empareja un mando Bluetooth desde los ajustes del dispositivo.", 15, Style.MUTED), 12);
    }

    private void bindings(Game game, String profile, String focusId) {
        mappingGame = game;
        mappingProfile = profile;
        capture = null;
        page();
        heading("Asignar botones", game.title);
        appendButton(screen, "‹ Mandos", 16, () -> profiles(game));
        Map<String, String> mapping = library.bindings(game, profile);
        for (Game.Action action : game.actions) {
            String id = mapping.get(action.id);
            String label = profile.equals("touch") ? TouchPad.label(id) : buttonLabel(id);
            Button row = appendButton(screen, action.name + "   ·   " + label, 8,
                    () -> chooseBinding(game, profile, action));
            if (action.id.equals(focusId)) row.requestFocus();
        }
        appendButton(screen, "Restaurar valores del juego", 14, () -> {
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
            for (int i = 0; i < options.size(); i++) labels[i] = TouchPad.label(options.get(i));
            new AlertDialog.Builder(this).setTitle(action.name).setItems(labels,
                    (dialog, position) -> saveBinding(action, options.get(position))).setNegativeButton("Cancelar", null).show();
            return;
        }
        capture = action;
        page();
        heading(action.name, "Pulsa el botón o la dirección que quieres asignar.");
        appendButton(screen, "Cancelar", 16, () -> bindings(game, profile, action.id));
        appendButton(screen, "Dejar sin asignar", 8, () -> saveBinding(action, ""));
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
            case "dpad_up": return "Cruceta arriba";
            case "dpad_down": return "Cruceta abajo";
            case "dpad_left": return "Cruceta izquierda";
            case "dpad_right": return "Cruceta derecha";
            case "south": return "A · abajo";
            case "east": return "B · derecha";
            case "west": return "X · izquierda";
            case "north": return "Y · arriba";
            case "left_shoulder": return "L1";
            case "right_shoulder": return "R1";
            case "left_trigger": return "L2";
            case "right_trigger": return "R2";
            case "left_stick": return "L3";
            case "right_stick": return "R3";
            case "start": return "Start";
            case "select": return "Select";
            default: return "Sin asignar";
        }
    }

    private void open(Game game) {
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
            toast("No se pudo abrir el navegador del dispositivo.");
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
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        String button = Controls.button(event.getKeyCode());
        if (button != null && Controls.controller(device)) {
            String profile = Controls.profile(device);
            if (capture != null && profile.equals(mappingProfile) && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                saveBinding(capture, button);
                return true;
            }
            if (play != null && play.game.compatible()) {
                boolean down = event.getAction() == KeyEvent.ACTION_DOWN && !event.isCanceled();
                if (!down || event.getRepeatCount() == 0) {
                    if (controls.set(profile, device.getId() + ":key:" + button,
                            down ? Collections.singleton(button) : Collections.emptySet())) dispatchActions();
                }
                return true;
            }
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
            String profile = Controls.profile(device);
            if (capture != null && profile.equals(mappingProfile) && !buttons.isEmpty()) {
                saveBinding(capture, buttons.iterator().next());
                return true;
            }
            if (play != null && play.game.compatible()) {
                if (controls.set(profile, device.getId() + ":axes", buttons)) dispatchActions();
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onInputDeviceAdded(int id) {
        if (mappingGame != null && mappingProfile == null) profiles(mappingGame);
    }

    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }

    @Override public void onInputDeviceRemoved(int id) {
        controls.removeDevice(id);
        dispatchActions();
        if (mappingGame != null && mappingProfile == null) profiles(mappingGame);
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
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        devices.unregisterInputDeviceListener(this);
        network.shutdownNow();
        if (play != null) play.destroy();
        super.onDestroy();
    }
}
