package app.linkarcade;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Game {
    static final String ASSET_HOST = "appassets.androidplatform.net";
    static final String TEST_ORIGIN = "https://" + ASSET_HOST;
    static final String TEST_URL = TEST_ORIGIN + "/controller-test/index.html";
    static final String DRIVE_URL = TEST_ORIGIN + "/driving/index.html";
    static final Set<String> BUTTONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "dpad_up", "dpad_down", "dpad_left", "dpad_right", "south", "east", "west", "north",
            "left_shoulder", "right_shoulder", "left_trigger", "right_trigger",
            "left_stick", "right_stick", "start", "select")));
    static final List<String> TOUCH = Collections.unmodifiableList(Arrays.asList(
            "dpad_up", "dpad_down", "dpad_left", "dpad_right", "south", "east", "west", "north", "start", "select"));

    private static final List<String> DRIVING_TOUCH = Collections.unmodifiableList(Arrays.asList(
            "dpad_left", "dpad_right", "south", "west", "east", "north", "start", "select"));

    static final class Action {
        final String id, name, button;

        Action(String id, String name, String button) {
            this.id = id;
            this.name = name;
            this.button = button;
        }
    }

    final String url, title, cover;
    final List<Action> actions;

    Game(String url, String title, String cover, List<Action> actions) {
        this.url = url;
        this.title = title;
        this.cover = cover;
        this.actions = actions;
    }

    boolean compatible() { return !actions.isEmpty(); }
    boolean builtin() { return TEST_URL.equals(url) || driving(); }
    boolean driving() { return DRIVE_URL.equals(url); }
    List<String> touchButtons() { return driving() ? DRIVING_TOUCH : TOUCH; }

    static Game fromManifest(String url, String cover, JSONObject manifest) throws JSONException {
        for (java.util.Iterator<String> fields = manifest.keys(); fields.hasNext();) {
            String field = fields.next();
            if (!Arrays.asList("version", "title", "cover", "actions").contains(field)) {
                throw new JSONException("Unknown field: " + field);
            }
        }
        Object version = manifest.opt("version");
        if (!(version instanceof Number) || ((Number) version).doubleValue() != 1) {
            throw new JSONException("Unsupported game manifest version.");
        }
        return new Game(url, label(manifest, "title", 80), cover, readActions(manifest.getJSONArray("actions")));
    }

    private static String label(JSONObject data, String field, int max) throws JSONException {
        Object value = data.get(field);
        if (!(value instanceof String)) throw new JSONException("Invalid field: " + field);
        String string = (String) value;
        if (string.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))
                || string.codePointCount(0, string.length()) > max) throw new JSONException("Invalid field: " + field);
        return string;
    }

    private static List<Action> readActions(JSONArray array) throws JSONException {
        if (array.length() < 1 || array.length() > 32) throw new JSONException("Invalid action count.");
        List<Action> actions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> defaults = new HashSet<>();
        boolean pause = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.getJSONObject(i);
            if (row.length() != (row.has("default") ? 3 : 2) || !(row.opt("id") instanceof String)) {
                throw new JSONException("Invalid action.");
            }
            String id = row.getString("id");
            String name = label(row, "label", 48);
            String button = row.has("default") ? label(row, "default", 32) : "";
            if (!id.matches("[a-z][a-z0-9_]{0,31}") || !ids.add(id)
                    || (!button.isEmpty() && (!BUTTONS.contains(button) || !defaults.add(button)))) {
                throw new JSONException("Duplicate or invalid action or mapping: " + id);
            }
            pause |= id.equals("pause") && button.equals("start");
            actions.add(new Action(id, name, button));
        }
        if (!pause) throw new JSONException("The pause action must be mapped to Start.");
        return Collections.unmodifiableList(actions);
    }

    JSONObject serialize() throws JSONException {
        JSONArray rows = new JSONArray();
        for (Action action : actions) {
            JSONObject row = new JSONObject().put("id", action.id).put("label", action.name);
            if (!action.button.isEmpty()) row.put("default", action.button);
            rows.put(row);
        }
        return new JSONObject().put("url", url).put("title", title).put("cover", cover).put("actions", rows);
    }

    static Game restore(JSONObject row) throws JSONException {
        JSONArray actions = row.getJSONArray("actions");
        return new Game(row.getString("url"), row.getString("title"), row.getString("cover"),
                actions.length() == 0 ? Collections.emptyList() : readActions(actions));
    }
}
