import { bundled, littleDriveUrl } from './games.js';

const standardButtons = {
    south: 0, east: 1, west: 2, north: 3, left_shoulder: 4, right_shoulder: 5,
    left_trigger: 6, right_trigger: 7, select: 8, start: 9, left_stick: 10,
    right_stick: 11, dpad_up: 12, dpad_down: 13, dpad_left: 14, dpad_right: 15,
};
const touchGroups = [
    { dpad_left: '←', dpad_up: '↑', dpad_down: '↓', dpad_right: '→' },
    { select: 'Select', start: 'Start' },
    { west: '□', south: '×', east: '○', north: '△' },
];

const frame = document.querySelector('#game');
const status = document.querySelector('#play-status');
const controls = document.querySelector('#touch-controls');
const toggle = document.querySelector('#touch-toggle');

async function launch(game) {
    const response = await fetch(game.directory + 'game.json');
    if (!response.ok) throw new Error('Manifest unavailable');
    const { actions } = await response.json();
    const pointers = new Map();
    const touchButtons = new Map();
    let previous = '';
    let ready = false;

    function dispatch(pressed) {
        if (!ready) return;
        const snapshot = JSON.stringify({ type: 'tv-game-input', version: 1, pressed });
        if (snapshot === previous) return;
        frame.contentWindow.postMessage(snapshot, location.origin);
        previous = snapshot;
    }

    function update() {
        const touches = new Set(pointers.values());
        const pad = document.hasFocus() && !document.hidden
            ? Array.from(navigator.getGamepads?.() || []).find(device => device?.mapping === 'standard')
            : null;
        const pressed = actions.filter(action => {
            const index = standardButtons[action.default];
            return touches.has(action.default) || pad?.buttons[index]?.pressed;
        }).map(action => action.id);
        dispatch(pressed);
        for (const [name, button] of touchButtons) button.classList.toggle('held', touches.has(name));
    }

    for (const group of touchGroups) {
        const container = document.createElement('div');
        container.className = 'pad-group';
        for (const [name, symbol] of Object.entries(group)) {
            const action = actions.find(candidate => candidate.default === name);
            if (!action) continue;
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = symbol;
            button.setAttribute('aria-label', action.label);
            button.addEventListener('pointerdown', event => {
                if (event.button !== 0) return;
                event.preventDefault();
                button.setPointerCapture(event.pointerId);
                pointers.set(event.pointerId, name);
                update();
            });
            for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) {
                button.addEventListener(type, event => {
                    pointers.delete(event.pointerId);
                    update();
                });
            }
            touchButtons.set(name, button);
            container.append(button);
        }
        if (container.childElementCount) controls.append(container);
    }

    function showControls(show) {
        controls.hidden = !show;
        toggle.setAttribute('aria-pressed', String(show));
        if (!show) {
            pointers.clear();
            update();
        }
    }

    function clear() {
        pointers.clear();
        for (const button of touchButtons.values()) button.classList.remove('held');
        dispatch([]);
    }

    showControls(matchMedia('(pointer: coarse)').matches);
    toggle.hidden = false;
    toggle.addEventListener('click', () => showControls(controls.hidden));
    window.addEventListener('blur', clear);
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) clear();
    });
    frame.addEventListener('load', () => {
        ready = true;
        previous = '';
        frame.contentWindow.focus();
        update();
    });
    frame.title = game.title;
    document.title = game.title + ' · Link Arcade';
    document.querySelector('#playing').textContent = game.title;
    frame.src = game.directory + 'index.html';
    frame.hidden = false;
    status.hidden = true;

    function tick() {
        update();
        requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
}

const gameId = new URLSearchParams(location.search).get('game');
const game = bundled.find(entry => entry.id === gameId);
if (gameId === 'drive') {
    location.replace(littleDriveUrl);
} else if (game) {
    launch(game).catch(() => { status.textContent = 'Could not load the game. Return to the catalog and try again.'; });
} else {
    status.textContent = 'Game not found.';
}
