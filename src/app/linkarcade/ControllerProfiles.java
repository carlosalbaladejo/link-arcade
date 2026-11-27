package app.linkarcade;

import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ControllerProfiles {
    private final SharedPreferences storage;
    private final Map<String, Map<String, String>> profiles = new LinkedHashMap<>();

    ControllerProfiles(SharedPreferences storage) {
        this.storage = storage;
        try {
            JSONObject saved = new JSONObject(storage.getString("controller_profiles", "{}"));
            for (java.util.Iterator<String> names = saved.keys(); names.hasNext();) {
                String name = names.next();
                JSONObject row = saved.optJSONObject(name);
                if (row == null) continue;
                Map<String, String> mapping = new LinkedHashMap<>();
                for (java.util.Iterator<String> ids = row.keys(); ids.hasNext();) {
                    String id = ids.next();
                    String input = row.optString(id);
                    if (Controls.mappable(id) && Controls.mappable(input)) mapping.put(id, input);
                }
                profiles.put(name, mapping);
            }
        } catch (JSONException ignored) {
            // Leave the standard layout available if stored preferences are unreadable.
        }
    }

    Set<String> names() { return profiles.keySet(); }

    Map<String, String> edit(String name) {
        Map<String, String> saved = profiles.get(name);
        return saved == null ? new LinkedHashMap<>() : new LinkedHashMap<>(saved);
    }

    String assigned(String device) {
        String name = storage.getString("controller/" + device, "");
        return profiles.containsKey(name) ? name : "";
    }

    boolean assign(String device, String name) {
        return storage.edit().putString("controller/" + device, name).commit();
    }

    boolean save(String name, Map<String, String> mapping) {
        Map<String, Map<String, String>> next = new LinkedHashMap<>(profiles);
        next.put(name, new LinkedHashMap<>(mapping));
        return persist(next);
    }

    boolean delete(String name) {
        Map<String, Map<String, String>> next = new LinkedHashMap<>(profiles);
        next.remove(name);
        return persist(next);
    }

    private boolean persist(Map<String, Map<String, String>> next) {
        Map<String, JSONObject> serialized = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> item : next.entrySet()) {
            serialized.put(item.getKey(), new JSONObject(item.getValue()));
        }
        SharedPreferences.Editor transaction = storage.edit()
                .putString("controller_profiles", new JSONObject(serialized).toString());
        // Remove assignments along with a deleted profile, so recreating a name does not revive them.
        for (Map.Entry<String, ?> item : storage.getAll().entrySet()) {
            if (item.getKey().startsWith("controller/") && !next.containsKey(item.getValue())) {
                transaction.remove(item.getKey());
            }
        }
        if (!transaction.commit()) return false;
        profiles.clear();
        profiles.putAll(next);
        return true;
    }

    private Map<String, String> mapping(String device) {
        return profiles.get(assigned(device));
    }

    static void remap(Map<String, String> draft, String button, String input) {
        String previous = draft.getOrDefault(button, button);
        if (input.equals(previous)) return;
        String displaced = logical(draft, input);
        set(draft, displaced, previous);
        set(draft, button, input);
    }

    private static void set(Map<String, String> draft, String button, String input) {
        if (input.equals(button)) draft.remove(button);
        else draft.put(button, input);
    }

    private static String logical(Map<String, String> mapping, String input) {
        if (mapping != null) {
            for (Map.Entry<String, String> binding : mapping.entrySet()) {
                if (binding.getValue().equals(input)) return binding.getKey();
            }
        }
        return input;
    }

    Set<String> translated(String device, Map<String, Set<String>> sources, boolean forGame) {
        Map<String, String> mapping = mapping(device);
        Set<String> raw = new LinkedHashSet<>();
        for (Set<String> source : sources.values()) raw.addAll(source);
        Set<String> result = new LinkedHashSet<>();
        boolean hasDpad = raw.stream().anyMatch(id -> id.startsWith("dpad_"));
        for (String input : raw) {
            String button = logical(mapping, input);
            result.add(button);
            if (forGame && !hasDpad && input.equals(button) && input.startsWith("left_stick_")) {
                result.add(logical(mapping, "dpad_" + input.substring("left_stick_".length())));
            }
        }
        return result;
    }

    boolean hasAssignment(String device) { return !assigned(device).isEmpty(); }
}
