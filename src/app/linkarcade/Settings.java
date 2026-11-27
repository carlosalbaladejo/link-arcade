package app.linkarcade;

import android.app.AlertDialog;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Settings {
    private final MainActivity app;
    private final ControllerProfiles profiles;
    private Runnable back;
    private boolean choosingControllers;
    private PadDiagram diagram;
    private TextView testStatus;
    private Set<String> previous = Collections.emptySet();
    private ToneGenerator tone;
    private String editing;
    private Map<String, String> draft;
    private String capture;

    Settings(MainActivity app, ControllerProfiles profiles) {
        this.app = app;
        this.profiles = profiles;
    }

    private String t(String en, String es) { return app.t(en, es); }

    private Button button(String title, int gap, Runnable action) {
        return app.appendButton(app.screen, title, gap, action);
    }

    private void page(String title, String subtitle, String backLabel, Runnable parent) {
        leaveTest();
        capture = null;
        choosingControllers = false;
        back = parent;
        app.page();
        app.heading(title, subtitle);
        button("‹ " + backLabel, 16, parent).requestFocus();
    }

    void show() {
        page(t("Settings", "Configuración"), "", t("Catalog", "Catálogo"), () -> {
            back = null;
            app.catalog(null);
        });
        button(t("Language", "Idioma") + "   ·   " + (app.spanish() ? "Español" : "English"), 12, this::language);
        button(t("Controller test", "Prueba de mando"), 8, this::test);
        button(t("Controller profiles", "Perfiles de mando"), 8, this::list);
    }

    private void language() {
        new AlertDialog.Builder(app).setTitle(t("Language", "Idioma"))
                .setSingleChoiceItems(new String[]{"Español", "English"}, app.spanish() ? 0 : 1, (dialog, index) -> {
                    app.changeLanguage(index == 0 ? "es" : "en");
                    dialog.dismiss();
                    show();
                }).setNegativeButton(t("Cancel", "Cancelar"), null).show();
    }

    private void list() {
        page(t("Controller profiles", "Perfiles de mando"),
                t("Save a layout once and assign it to any connected controller.",
                        "Guarda un mapeado y asígnalo a cualquier mando conectado."),
                t("Settings", "Configuración"), this::show);
        choosingControllers = true;
        button(t("Create mapping", "Crear mapeado"), 12, this::namePrompt);
        for (String name : profiles.names()) button(name, 8, () -> edit(name));
        app.style.append(app.screen, app.style.text(t("Connected controllers", "Mandos conectados"), 20, Style.TEXT), 20);
        List<InputDevice> connected = Controls.devices();
        if (connected.isEmpty()) {
            app.style.append(app.screen, app.style.text(t("Pair a controller in your device settings.",
                    "Empareja un mando en los ajustes del dispositivo."), 15, Style.MUTED), 8);
        }
        for (InputDevice device : connected) {
            String id = Controls.identity(device);
            String name = profiles.assigned(id);
            button(device.getName() + "   ·   " + (name.isEmpty() ? standard() : name), 8, () -> assign(device, id));
        }
    }

    private String standard() { return t("Standard layout", "Mapeado estándar"); }

    private void assign(InputDevice device, String id) {
        List<String> names = new ArrayList<>();
        names.add("");
        names.addAll(profiles.names());
        String[] labels = names.toArray(new String[0]);
        labels[0] = standard();
        new AlertDialog.Builder(app).setTitle(device.getName())
                .setSingleChoiceItems(labels, names.indexOf(profiles.assigned(id)), (dialog, index) -> {
                    if (!profiles.assign(id, names.get(index))) saveError();
                    dialog.dismiss();
                    list();
                }).setNegativeButton(t("Cancel", "Cancelar"), null).show();
    }

    private void namePrompt() {
        EditText field = new EditText(app);
        field.setSingleLine(true);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        field.setHint(t("Profile name", "Nombre del perfil"));
        AlertDialog dialog = new AlertDialog.Builder(app).setTitle(t("Create mapping", "Crear mapeado"))
                .setView(field).setNegativeButton(t("Cancel", "Cancelar"), null)
                .setPositiveButton(t("Continue", "Continuar"), null).create();
        dialog.setOnShowListener(ignored -> {
            field.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = field.getText().toString().trim();
                if (name.isEmpty() || profiles.names().contains(name) || name.equalsIgnoreCase(standard())) {
                    field.setError(t("Choose a different, non-empty name.", "Escribe un nombre nuevo que no esté vacío."));
                    return;
                }
                dialog.dismiss();
                edit(name);
            });
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void edit(String name) {
        editing = name;
        draft = profiles.edit(name);
        editor(null);
    }

    private void editor(String focus) {
        page(editing, t("Choose a button to remap. If it is in use, the two assignments swap.",
                "Elige un botón. Si el nuevo ya está en uso, se intercambian las asignaciones."),
                t("Profiles", "Perfiles"), this::leaveEditor);
        button(t("Save profile", "Guardar perfil"), 10, () -> {
            if (profiles.save(editing, draft)) list();
            else saveError();
        });
        for (String id : Controls.LAYOUT) {
            String title = label(id) + "   →   " + label(draft.getOrDefault(id, id));
            Button row = button(title, 6, () -> waitForInput(id));
            if (id.equals(focus)) row.requestFocus();
        }
        button(t("Restore standard layout", "Restablecer mapeado estándar"), 12, () -> {
            draft.clear();
            editor(null);
        });
        if (profiles.names().contains(editing)) {
            button(t("Delete profile", "Eliminar perfil"), 8, () -> new AlertDialog.Builder(app)
                    .setTitle(t("Delete profile?", "¿Eliminar perfil?"))
                    .setMessage(t("Assigned controllers will return to their original settings.",
                            "Los mandos asignados volverán a su configuración anterior."))
                    .setNegativeButton(t("Cancel", "Cancelar"), null)
                    .setPositiveButton(t("Delete", "Eliminar"), (dialog, which) -> {
                        if (profiles.delete(editing)) list();
                        else saveError();
                    }).show());
        }
    }

    private void leaveEditor() {
        if (profiles.names().contains(editing) && draft.equals(profiles.edit(editing))) {
            list();
            return;
        }
        new AlertDialog.Builder(app).setTitle(t("Discard unsaved mapping?", "¿Descartar el mapeado sin guardar?"))
                .setNegativeButton(t("Cancel", "Cancelar"), null)
                .setPositiveButton(t("Discard", "Descartar"), (dialog, which) -> list()).show();
    }

    private void waitForInput(String id) {
        page(label(id), t("Release held controls, then press one button or move one stick direction.",
                "Suelta los controles y pulsa un botón o mueve un stick en una dirección."),
                t("Cancel", "Cancelar"), () -> editor(id));
        capture = id;
    }

    private void test() {
        page(t("Controller test", "Prueba de mando"),
                t("Press a button or move a stick. The diagram shows the active profile.",
                        "Pulsa un botón o mueve un stick. El dibujo usa el perfil asignado."),
                t("Settings", "Configuración"), this::show);
        diagram = new PadDiagram(app.style, t("Dual-analog controller", "Mando de doble stick"));
        app.style.append(app.screen, diagram, 10);
        diagram.getLayoutParams().height = app.style.dp(app.isTv() ? 210 : 195);
        testStatus = app.style.text("", 16, Style.MUTED);
        app.style.append(app.screen, testStatus, 6);
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 35);
        } catch (RuntimeException ignored) {
            // Visual feedback remains available even if audio is unavailable.
        }
        updateTest();
    }

    private void leaveTest() {
        diagram = null;
        previous = Collections.emptySet();
        if (tone != null) tone.release();
        tone = null;
    }

    void destroy() { leaveTest(); }

    boolean back() {
        if (back == null) return false;
        back.run();
        return true;
    }

    void devicesChanged() {
        if (choosingControllers) list();
        updateTest();
    }

    boolean consumesInput() { return diagram != null || capture != null; }

    void press(String input) {
        if (capture == null) return;
        String id = capture;
        ControllerProfiles.remap(draft, id, input);
        editor(id);
    }

    void updateTest() {
        if (diagram == null) return;
        Set<String> pressed = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Set<String>>> entry : app.controls.held.entrySet()) {
            if (!entry.getKey().equals("touch")) {
                pressed.addAll(profiles.translated(entry.getKey(), entry.getValue(), false));
            }
        }
        diagram.show(pressed);
        List<String> labels = new ArrayList<>();
        for (String id : pressed) labels.add(label(id));
        String status = !labels.isEmpty() ? String.join("  ·  ", labels) : Controls.devices().isEmpty()
                ? t("Connect a controller to begin.", "Conecta un mando para empezar.")
                : t("Waiting for input…", "Esperando entrada…");
        if (!status.contentEquals(testStatus.getText())) testStatus.setText(status);
        if (tone != null && !previous.containsAll(pressed)) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90);
        previous = pressed;
    }

    private void saveError() {
        app.toast(t("Could not save the profile.", "No se pudo guardar el perfil."));
    }

    String label(String id) {
        switch (id) {
            case "dpad_up": return t("D-pad ↑", "Cruceta ↑");
            case "dpad_down": return t("D-pad ↓", "Cruceta ↓");
            case "dpad_left": return t("D-pad ←", "Cruceta ←");
            case "dpad_right": return t("D-pad →", "Cruceta →");
            case "south": return t("× · Bottom", "× · Abajo");
            case "east": return t("○ · Right", "○ · Derecha");
            case "west": return t("□ · Left", "□ · Izquierda");
            case "north": return t("△ · Top", "△ · Arriba");
            case "left_stick_up": return t("Left stick ↑", "Stick izq. ↑");
            case "left_stick_down": return t("Left stick ↓", "Stick izq. ↓");
            case "left_stick_left": return t("Left stick ←", "Stick izq. ←");
            case "left_stick_right": return t("Left stick →", "Stick izq. →");
            case "right_stick_up": return t("Right stick ↑", "Stick dcho. ↑");
            case "right_stick_down": return t("Right stick ↓", "Stick dcho. ↓");
            case "right_stick_left": return t("Right stick ←", "Stick dcho. ←");
            case "right_stick_right": return t("Right stick →", "Stick dcho. →");
            default: return MainActivity.buttonLabel(id);
        }
    }
}
