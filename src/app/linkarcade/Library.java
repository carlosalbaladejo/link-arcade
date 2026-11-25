package app.linkarcade;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Library {
    private final SharedPreferences storage;
    final List<Game> games = new ArrayList<>();

    Library(Context context) throws JSONException {
        storage = context.getSharedPreferences("library", Context.MODE_PRIVATE);
        JSONArray saved = new JSONArray(storage.getString("games", "[]"));
        for (int i = 0; i < saved.length(); i++) games.add(Game.restore(saved.getJSONObject(i)));
    }

    void add(Game game) throws Exception {
        for (Game saved : games) {
            if (saved.url.equals(game.url)) throw new IllegalArgumentException("Este juego ya está en el catálogo.");
        }
        List<Game> next = new ArrayList<>(games);
        next.add(game);
        save(next, storage.edit());
    }

    Game find(String url) {
        for (Game game : games) {
            if (game.url.equals(url)) return game;
        }
        return null;
    }

    void updateCover(Game game, String cover) throws Exception {
        int index = games.indexOf(game);
        if (index < 0 || game.cover.equals(cover)) return;
        List<Game> next = new ArrayList<>(games);
        next.set(index, new Game(game.url, game.title, cover, game.actions));
        save(next, storage.edit());
    }

    void remove(Game game) throws Exception {
        List<Game> next = new ArrayList<>(games);
        next.removeIf(saved -> saved.url.equals(game.url));
        SharedPreferences.Editor editor = storage.edit();
        String prefix = "map/" + game.url.length() + ":" + game.url + "/";
        for (String key : storage.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        save(next, editor);
    }

    private void save(List<Game> next, SharedPreferences.Editor editor) throws Exception {
        JSONArray result = new JSONArray();
        for (Game game : next) result.put(game.serialize());
        if (!editor.putString("games", result.toString()).commit()) throw new Exception("No se pudo guardar el catálogo.");
        games.clear();
        games.addAll(next);
    }

    private String key(Game game, String profile) {
        return "map/" + game.url.length() + ":" + game.url + "/" + profile;
    }

    Map<String, String> bindings(Game game, String profile) {
        Map<String, String> mapping = new LinkedHashMap<>();
        JSONObject saved = null;
        try {
            String raw = storage.getString(key(game, profile), null);
            if (raw != null) saved = new JSONObject(raw);
        } catch (JSONException ignored) {
            // Invalid stored preferences never override the game's defaults.
        }
        for (Game.Action action : game.actions) {
            String button = saved == null ? action.button : saved.optString(action.id, action.button);
            if (!Game.BUTTONS.contains(button) || ("touch".equals(profile) && !game.touchButtons().contains(button))) button = "";
            mapping.put(action.id, button);
        }
        return mapping;
    }

    void bind(Game game, String profile, String action, String button) throws Exception {
        Map<String, String> mapping = bindings(game, profile);
        for (Map.Entry<String, String> item : mapping.entrySet()) {
            if (item.getValue().equals(button)) item.setValue("");
        }
        mapping.put(action, button);
        if (!storage.edit().putString(key(game, profile), new JSONObject(mapping).toString()).commit()) {
            throw new Exception("No se pudo guardar la asignación.");
        }
    }

    void reset(Game game, String profile) throws Exception {
        if (!storage.edit().remove(key(game, profile)).commit()) throw new Exception("No se pudo restaurar la asignación.");
    }

    Set<String> active(Game game, Map<String, Map<String, Set<String>>> input) {
        Set<String> active = new LinkedHashSet<>();
        Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Set<String>>> source : input.entrySet()) {
            String profile = source.getKey();
            Map<String, String> bindings = profiles.computeIfAbsent(profile, ignored -> bindings(game, profile));
            for (Map.Entry<String, String> action : bindings.entrySet()) {
                for (Set<String> held : source.getValue().values()) {
                    if (held.contains(action.getValue())) active.add(action.getKey());
                }
            }
        }
        return active;
    }
}
