export const buttons = [
    ['south','×',371,148], ['east','○',399,121], ['west','□',343,121], ['north','△',371,94],
    ['left_shoulder','L1',106,52], ['right_shoulder','R1',374,52],
    ['left_trigger','L2',106,23], ['right_trigger','R2',374,23],
    ['select','Select',208,115], ['start','Start',272,115], ['left_stick','L3',179,177], ['right_stick','R3',301,177],
    ['dpad_up','↑',109,94], ['dpad_down','↓',109,148], ['dpad_left','←',80,121], ['dpad_right','→',138,121],
];
export const prefKey = 'link-arcade-settings-v1';

export function preferences() {
    try {
        const value = JSON.parse(localStorage.getItem(prefKey));
        if (value?.profiles && value?.assigned) return value;
    } catch { /* Storage can be blocked. */ }
    return { language: navigator.language.startsWith('es') ? 'es' : 'en', profiles: {}, assigned: {} };
}

export const deviceKey = pad => JSON.stringify([pad.id, pad.mapping, pad.buttons.length, pad.axes.length]);
export const physicalKey = (kind, index, sign = '') => kind + index + sign;
const threshold = .65;
export function physical(pad) {
    const held = new Set();
    Array.from(pad.buttons).forEach((button, i) => {
        if (button.pressed || button.value >= threshold) held.add(physicalKey('b', i));
    });
    Array.from(pad.axes).forEach((axis, i) => {
        if (Math.abs(axis) >= threshold) held.add(physicalKey('a', i, axis > 0 ? '+' : '-'));
    });
    return held;
}

export function connected() {
    try { return Array.from(navigator.getGamepads?.() || []).filter(Boolean); }
    catch { return []; }
}

export function mapping(pad, prefs) {
    const profile = prefs.profiles[prefs.assigned[deviceKey(pad)]];
    if (Array.isArray(profile?.bindings)) return profile.bindings;
    return buttons.map((_, i) => pad.mapping === 'standard' ? physicalKey('b', i) : '');
}

export function logical(pad, prefs, forGame = false) {
    const raw = physical(pad);
    const map = mapping(pad, prefs);
    const held = new Set(buttons.filter((_, i) => raw.has(map[i])).map(row => row[0]));
    if (pad.mapping === 'standard' && forGame && ![...held].some(id => id.startsWith('dpad_'))) {
        for (const [source, id] of [['a0-','left'],['a0+','right'],['a1-','up'],['a1+','down']]) {
            if (raw.has(source) && !map.includes(source)) held.add('dpad_' + id);
        }
    }
    return held;
}
