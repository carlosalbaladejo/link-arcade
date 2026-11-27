package app.linkarcade;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Controls {
    static final List<String> LAYOUT = Collections.unmodifiableList(Arrays.asList(
            "dpad_up", "dpad_down", "dpad_left", "dpad_right",
            "south", "east", "west", "north", "left_shoulder", "right_shoulder",
            "left_trigger", "right_trigger", "left_stick", "right_stick", "select", "start"));
    static final List<String> STICK_DIRECTIONS = Collections.unmodifiableList(Arrays.asList(
            "left_stick_up", "left_stick_down", "left_stick_left", "left_stick_right",
            "right_stick_up", "right_stick_down", "right_stick_left", "right_stick_right"));

    static boolean mappable(String id) {
        return LAYOUT.contains(id) || STICK_DIRECTIONS.contains(id);
    }

    final Map<String, Map<String, Set<String>>> held = new LinkedHashMap<>();

    boolean set(String profile, String source, Set<String> buttons) {
        Map<String, Set<String>> sources = held.computeIfAbsent(profile, ignored -> new LinkedHashMap<>());
        Set<String> previous = buttons.isEmpty() ? sources.remove(source) : sources.put(source, buttons);
        if (sources.isEmpty()) held.remove(profile);
        return previous == null ? !buttons.isEmpty() : !previous.equals(buttons);
    }

    void removeDevice(int id) {
        for (String profile : new ArrayList<>(held.keySet())) {
            Map<String, Set<String>> sources = held.get(profile);
            String prefix = id + ":";
            sources.keySet().removeIf(source -> source.startsWith(prefix));
            if (sources.isEmpty()) held.remove(profile);
        }
    }

    static boolean controller(InputDevice device) {
        return device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                || device.supportsSource(InputDevice.SOURCE_JOYSTICK));
    }

    static List<InputDevice> devices() {
        List<InputDevice> result = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (controller(device)) result.add(device);
        }
        return result;
    }

    static String profile(InputDevice device) {
        return "pad/" + device.getVendorId() + "/" + device.getProductId() + "/" + device.getName();
    }

    static String identity(InputDevice device) {
        return profile(device) + "/" + device.getDescriptor();
    }

    static String legacyProfile(String identity) {
        return identity.substring(0, identity.lastIndexOf('/'));
    }

    static String button(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: return "dpad_up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "dpad_down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "dpad_left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "dpad_right";
            case KeyEvent.KEYCODE_BUTTON_A: return "south";
            case KeyEvent.KEYCODE_BUTTON_B: return "east";
            case KeyEvent.KEYCODE_BUTTON_X: return "west";
            case KeyEvent.KEYCODE_BUTTON_Y: return "north";
            case KeyEvent.KEYCODE_BUTTON_L1: return "left_shoulder";
            case KeyEvent.KEYCODE_BUTTON_R1: return "right_shoulder";
            case KeyEvent.KEYCODE_BUTTON_L2: return "left_trigger";
            case KeyEvent.KEYCODE_BUTTON_R2: return "right_trigger";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "left_stick";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "right_stick";
            case KeyEvent.KEYCODE_BUTTON_START: return "start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "select";
            default: return null;
        }
    }

    static Set<String> axes(MotionEvent event) {
        Set<String> result = new LinkedHashSet<>();
        directions(result, "dpad", event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y));
        directions(result, "left_stick", axis(event, MotionEvent.AXIS_X), axis(event, MotionEvent.AXIS_Y));
        directions(result, "right_stick", axis(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX),
                axis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY));
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) >= .55f) {
            result.add("left_trigger");
        }
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) >= .55f) {
            result.add("right_trigger");
        }
        return result;
    }

    private static float axis(MotionEvent event, int... candidates) {
        for (int axis : candidates) {
            InputDevice.MotionRange range = event.getDevice().getMotionRange(axis, event.getSource());
            if (range == null || range.getMin() >= 0 || range.getMax() <= 0) continue;
            float value = event.getAxisValue(axis);
            if (Math.abs(value) <= range.getFlat()) return 0;
            return value < 0 ? value / -range.getMin() : value / range.getMax();
        }
        return 0;
    }

    private static void directions(Set<String> result, String prefix, float x, float y) {
        if (x <= -.55f) result.add(prefix + "_left");
        if (x >= .55f) result.add(prefix + "_right");
        if (y <= -.55f) result.add(prefix + "_up");
        if (y >= .55f) result.add(prefix + "_down");
    }

}
