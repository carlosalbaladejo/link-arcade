package app.linkarcade;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Library {
    private final SharedPreferences storage;
    final ControllerProfiles profiles;
    final List<Game> games = new ArrayList<>();

    Library(Context context) throws Exception {
        storage = context.getSharedPreferences("library", Context.MODE_PRIVATE);
        profiles = new ControllerProfiles(storage);
        JSONArray saved = new JSONArray(storage.getString("games", "[]"));
        for (int i = 0; i < saved.length(); i++) games.add(Game.restore(saved.getJSONObject(i)));
        retirePreviewEntries();
        try (InputStream asset = context.getAssets().open("default-games.json")) {
            seedDefaults(new JSONArray(new String(Imports.read(asset, 65536), StandardCharsets.UTF_8)));
        }
    }

    private void retirePreviewEntries() throws Exception {
        String migration = "retired_preview_catalog_v1";
        if (storage.getBoolean(migration, false)) return;
        List<Game> next = new ArrayList<>();
        SharedPreferences.Editor editor = storage.edit().putBoolean(migration, true);
        for (Game game : games) {
            android.net.Uri url = android.net.Uri.parse(game.url);
            boolean oldIronEntry = "https://iron-caravan-bxf.pages.dev/".equals(game.url);
            boolean oldLunarEntry = "Liebre Lunar".equals(game.title)
                    && "http".equals(url.getScheme()) && url.getPort() == 8094;
            if (oldIronEntry || oldLunarEntry) clearBindings(editor, game);
            else next.add(game);
        }
        save(next, editor);
    }

    private void seedDefaults(JSONArray defaults) throws Exception {
        Set<String> seeded = new LinkedHashSet<>(storage.getStringSet("seeded_default_urls", Collections.emptySet()));
        List<Game> additions = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < defaults.length(); i++) {
            Game game = Game.restore(defaults.getJSONObject(i));
            if (seeded.add(game.url)) {
                changed = true;
                if (duplicate(game.url) == null) additions.add(game);
            }
        }
        if (changed) {
            additions.addAll(games);
            save(additions, storage.edit().putStringSet("seeded_default_urls", seeded));
        }
    }

    void add(Game game) throws Exception {
        if (duplicate(game.url) != null) throw new DuplicateGame();
        List<Game> next = new ArrayList<>(games);
        next.add(game);
        save(next, storage.edit());
    }

    static final class DuplicateGame extends Exception {}

    Game duplicate(String url) {
        String key = importKey(url);
        for (Game game : games) {
            if (key.equals(importKey(game.url))) return game;
        }
        return null;
    }

    private static String importKey(String url) {
        try {
            java.net.URI normalized = Imports.webUri(url);
            String origin = Imports.origin(normalized);
            String path = normalized.getRawPath();
            // These published games serve the same entry point at both directory and index URLs.
            if (origin.equals("https://carlosalbaladejo.github.io")
                    && path.matches("/(little-drive|tidy-robot|firemen-route)(/index\\.html|/)?")) {
                path = path.replaceFirst("(/index\\.html|/)$", "") + "/";
            }
            String query = normalized.getRawQuery();
            return origin + path + (query == null || query.isEmpty() ? "" : "?" + query);
        } catch (java.io.IOException invalid) {
            return url;
        }
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
        clearBindings(editor, game);
        save(next, editor);
    }

    private void clearBindings(SharedPreferences.Editor editor, Game game) {
        String prefix = key(game, "");
        for (String key : storage.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
    }

    private void save(List<Game> next, SharedPreferences.Editor editor) throws Exception {
        JSONArray result = new JSONArray();
        for (Game game : next) result.put(game.serialize());
        if (!editor.putString("games", result.toString()).commit()) throw new Exception("Could not save the library.");
        games.clear();
        games.addAll(next);
    }

    String language(String fallback) { return storage.getString("language", fallback); }

    boolean saveLanguage(String value) {
        return storage.edit().putString("language", value).commit();
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
            throw new Exception("Could not save the button mapping.");
        }
    }

    void reset(Game game, String profile) throws Exception {
        if (!storage.edit().remove(key(game, profile)).commit()) throw new Exception("Could not restore the default mapping.");
    }

    Set<String> active(Game game, Map<String, Map<String, Set<String>>> input) {
        Set<String> active = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Set<String>>> source : input.entrySet()) {
            String device = source.getKey();
            if ("touch".equals(device)) {
                for (Map.Entry<String, String> action : bindings(game, device).entrySet()) {
                    if (source.getValue().values().stream().anyMatch(held -> held.contains(action.getValue()))) {
                        active.add(action.getKey());
                    }
                }
                continue;
            }
            String legacy = Controls.legacyProfile(device);
            Set<String> mapped = profiles.translated(device, source.getValue(), true);
            // Explicit global profiles take over from the older per-game hardware mappings.
            Map<String, String> bindings = bindings(game, profiles.hasAssignment(device) ? "" : legacy);
            for (Map.Entry<String, String> action : bindings.entrySet()) {
                if (mapped.contains(action.getValue())) active.add(action.getKey());
            }
        }
        return active;
    }
}
