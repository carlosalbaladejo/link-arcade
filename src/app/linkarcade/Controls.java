package app.linkarcade;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Controls {
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
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        // Treat the left stick as a digital direction when the D-pad is idle.
        if (Math.abs(x) < .5f && Math.abs(y) < .5f) {
            x = event.getAxisValue(MotionEvent.AXIS_X);
            y = event.getAxisValue(MotionEvent.AXIS_Y);
        }
        if (x <= -.55f) result.add("dpad_left");
        if (x >= .55f) result.add("dpad_right");
        if (y <= -.55f) result.add("dpad_up");
        if (y >= .55f) result.add("dpad_down");
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) >= .55f) {
            result.add("left_trigger");
        }
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) >= .55f) {
            result.add("right_trigger");
        }
        return result;
    }
}
