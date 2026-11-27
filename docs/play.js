import { littleDriveUrl, managedGame } from './games.js';
import { connected, logical, preferences, prefKey } from './pad.js';

let prefs = preferences();
window.addEventListener('storage', event => {
    if (event.key === prefKey) prefs = preferences();
});
const es = prefs.language === 'es';
const label = (en, spanish) => es ? spanish : en;
document.documentElement.lang = prefs.language;

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
    const response = await fetch(game.manifest);
    if (new URL(response.url).origin !== game.manifest.origin) throw new Error('Unexpected manifest origin');
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
        frame.contentWindow.postMessage(snapshot, new URL(game.url).origin);
        previous = snapshot;
    }

    function update() {
        const touches = new Set(pointers.values());
        const held = document.hasFocus() && !document.hidden
            ? new Set(connected().flatMap(pad => [...logical(pad,prefs,true)])) : new Set();
        const pressed = actions.filter(action => touches.has(action.default) || held.has(action.default))
            .map(action => action.id);
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
        status.hidden = true;
        frame.focus();
        update();
    });
    frame.title = game.title;
    document.title = game.title + ' · Link Arcade';
    document.querySelector('#playing').textContent = game.title;
    frame.src = game.url;
    frame.hidden = false;

    function tick() {
        update();
        requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
}

const params = new URLSearchParams(location.search);
const gameId = params.get('game');
let game = null;
try { game = managedGame(params.get('url') || ''); } catch { /* Invalid bookmark. */ }
document.querySelector('.brand').textContent = label('← Catalog','← Catálogo');
toggle.textContent = label('Touch controls','Controles táctiles');
status.textContent = label('Loading game…','Cargando juego…');
if (gameId === 'controls') {
    location.replace('./settings.html#controller');
} else if (gameId === 'drive') {
    location.replace('./play.html?url=' + encodeURIComponent(littleDriveUrl));
} else if (game) {
    // GitHub Pages projects share the same origin, including the games' language preferences.
    if (new URL(game.url).origin === location.origin) {
        try { localStorage.setItem(game.languageKey, prefs.language); } catch { /* use the game's own default */ }
    }
    launch(game).catch(() => {
        status.textContent = label('Could not load the game. Return to the catalog and try again.',
            'No se pudo cargar el juego. Vuelve al catálogo e inténtalo de nuevo.');
    });
} else {
    status.textContent = label('Game not found.','No se encontró el juego.');
}
