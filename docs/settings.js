import { buttons, connected, deviceKey, logical, mapping, physical, preferences, prefKey } from './pad.js';

const copy = {
    catalog: ['← Catalog','← Catálogo'], settings: ['Settings','Configuración'], language: ['Language','Idioma'],
    test: ['Controller test','Prueba de mando'], profiles: ['Controller profiles','Perfiles de mando'],
    create: ['Create mapping','Crear mapeado'], devices: ['Connected controllers','Mandos conectados'],
    save: ['Save profile','Guardar perfil'], cancel: ['Cancel','Cancelar'],
    name: ['Profile name','Nombre del perfil'], none: ['Press a button on your connected controller.',
        'Pulsa un botón de tu mando conectado.'], waiting: ['Waiting for input…','Esperando entrada…'],
    standard: ['Standard layout','Mapeado estándar'], off: ['Enable sound','Activar sonido'],
    on: ['Mute sound','Silenciar sonido'], choose: ['Choose a button to remap.','Elige un botón para reasignarlo.'],
    capture: ['Release all controls, then press one button or move a stick. Esc cancels.',
        'Suelta los controles y pulsa un botón o mueve un stick. Esc cancela.'],
    remove: ['Delete profile','Eliminar perfil'], confirmDelete: ['Delete this profile?','¿Eliminar este perfil?'],
    unnamed: ['Unassigned','Sin asignar'], exists: ['Choose a different name.','Elige otro nombre.'],
    saved: ['Profile saved. Assign it to a connected controller below.',
        'Perfil guardado. Puedes asignarlo a un mando conectado más abajo.'],
    failed: ['Could not save settings in this browser.','No se pudieron guardar los ajustes en este navegador.'],
    button: ['Button','Botón'], axis: ['Axis','Eje'], disconnected: ['No controller detected.','No se detecta ningún mando.'],
};
const $ = id => document.getElementById(id);
const prefs = preferences();
const t = key => copy[key][prefs.language === 'es' ? 1 : 0];
let editId, draft, target = -1, armed = false, audio, audible = false;
let deviceList = [], signature, previous = new Set();
const glyphs = new Map();

for (const [id, label, x, y] of buttons) {
    const svg = 'http://www.w3.org/2000/svg';
    const group = document.createElementNS(svg,'g');
    group.classList.add('diagram-button');
    const width = label.length > 2 ? 52 : label.startsWith('L') || label.startsWith('R') ? 47 : 24;
    const rect = document.createElementNS(svg,'rect');
    for (const [key, value] of Object.entries({ x: -width/2, y: -12, width, height: 24, rx: 11 })) rect.setAttribute(key,value);
    const text = document.createElementNS(svg,'text');
    text.textContent = label;
    text.setAttribute('y','4');
    group.append(rect,text);
    $('diagram').append(group);
    glyphs.set(id,{group,x,y,label});
}

function persist() {
    try { localStorage.setItem(prefKey, JSON.stringify(prefs)); return true; }
    catch { $('notice').textContent = t('failed'); return false; }
}

function button(label, action) {
    const node = document.createElement('button');
    node.type = 'button';
    node.textContent = label;
    node.addEventListener('click', action);
    return node;
}

function renderProfiles() {
    $('profiles').replaceChildren(...Object.entries(prefs.profiles).map(([id, profile]) =>
        button(profile.name, () => openEditor(id, profile.name, profile.bindings))));
    $('devices').replaceChildren();
    if (!deviceList.length) $('devices').textContent = t('disconnected');
    for (const pad of deviceList) {
        const key = deviceKey(pad);
        const item = document.createElement('label');
        item.textContent = pad.id || t('devices');
        const select = document.createElement('select');
        select.add(new Option(t('standard'),''));
        for (const [id, profile] of Object.entries(prefs.profiles)) select.add(new Option(profile.name,id));
        select.value = prefs.assigned[key] || '';
        item.append(select);
        $('devices').append(item);
        select.addEventListener('change', () => {
            prefs.assigned[key] = select.value;
            persist();
        });
    }
}

function physicalLabel(source) {
    if (!source) return t('unnamed');
    return source.startsWith('b') ? t('button') + ' ' + (Number(source.slice(1)) + 1)
        : t('axis') + ' ' + source.slice(1);
}

function renderEditor(focus = -1) {
    $('capture').textContent = t(target < 0 ? 'choose' : 'capture');
    const entries = buttons.map(([,label], index) => {
        const source = draft.bindings[index];
        const node = button(label + ' → ' + physicalLabel(source), () => {
            target = index;
            armed = false;
            renderEditor(index);
        });
        node.setAttribute('aria-pressed',String(index === target));
        return node;
    });
    $('bind-buttons').replaceChildren(...entries);
    entries[focus]?.focus();
}

function openEditor(id, name, bindings) {
    editId = id;
    draft = { name, bindings: [...bindings] };
    target = -1;
    $('editing').textContent = name;
    $('delete-profile').hidden = !Object.hasOwn(prefs.profiles,id);
    $('editor').hidden = false;
    renderEditor(0);
}

function closeEditor() {
    draft = null;
    target = -1;
    $('editor').hidden = true;
    $('profile-name').focus();
}

$('create-profile').addEventListener('submit', event => {
    event.preventDefault();
    const name = $('profile-name').value.trim();
    if (!name || Object.values(prefs.profiles).some(profile => profile.name.toLowerCase() === name.toLowerCase())) {
        $('notice').textContent = t('exists');
        return;
    }
    const pad = deviceList[0];
    const initial = pad ? mapping(pad, { profiles:{}, assigned:{} }) : buttons.map((_, i) => 'b' + i);
    openEditor(crypto.randomUUID(), name, initial);
});
$('save').onclick = () => {
    prefs.profiles[editId] = draft;
    if (persist()) $('notice').textContent = t('saved');
    $('profile-name').value = '';
    closeEditor();
    renderProfiles();
};
$('cancel').onclick = closeEditor;
$('delete-profile').onclick = () => {
    if (!window.confirm(t('confirmDelete'))) return;
    delete prefs.profiles[editId];
    for (const [key,id] of Object.entries(prefs.assigned)) {
        if (id === editId) delete prefs.assigned[key];
    }
    persist();
    closeEditor();
    renderProfiles();
};
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && target >= 0) {
        const focus = target;
        target = -1;
        renderEditor(focus);
    }
});
function translate() {
    document.documentElement.lang = prefs.language;
    document.title = t('settings') + ' · Link Arcade';
    document.querySelectorAll('[data-text]').forEach(node => node.textContent = t(node.dataset.text));
    $('profile-name').placeholder = t('name');
    $('profile-name').setAttribute('aria-label',t('name'));
    $('sound').textContent = t(audible ? 'on' : 'off');
    renderProfiles();
    if (draft) renderEditor();
}
$('language').value = prefs.language;
$('language').onchange = () => {
    prefs.language = $('language').value;
    translate();
    persist();
};
$('sound').onclick = async () => {
    try {
        audio ||= new AudioContext();
        await audio.resume();
        audible = !audible;
        $('sound').textContent = t(audible ? 'on' : 'off');
        $('sound').setAttribute('aria-pressed',String(audible));
    } catch { /* Visual test still works. */ }
};

function beep() {
    const oscillator = audio.createOscillator();
    const gain = audio.createGain();
    oscillator.connect(gain).connect(audio.destination);
    gain.gain.setValueAtTime(.12,audio.currentTime);
    gain.gain.exponentialRampToValueAtTime(.001,audio.currentTime + .09);
    oscillator.start();
    oscillator.stop(audio.currentTime + .09);
}

function tick() {
    const pads = connected();
    const nextSignature = JSON.stringify(pads.map(pad => [pad.index,deviceKey(pad)]));
    deviceList = pads;
    if (nextSignature !== signature) {
        signature = nextSignature;
        renderProfiles();
    }
    const active = !document.hidden && document.hasFocus() ? pads : [];
    const inputs = active.map(pad => physical(pad));
    if (target >= 0) {
        const held = inputs.flatMap(set => [...set]);
        if (!held.length) armed = active.length > 0;
        else if (armed && held.length) {
            if (held.length === 1) {
                const displaced = draft.bindings.indexOf(held[0]);
                if (displaced >= 0) draft.bindings[displaced] = draft.bindings[target];
                draft.bindings[target] = held[0];
                const focus = target;
                target = -1;
                renderEditor(focus);
            }
            armed = false;
        }
    }
    const held = new Set(active.flatMap(pad => [...logical(pad,prefs)]));
    const analog = [0,1,2,3].map(i => active.reduce((value,pad) => {
        const axis = pad.mapping === 'standard' ? pad.axes[i] || 0 : 0;
        return Math.abs(axis) > Math.abs(value) ? axis : value;
    },0));
    for (const [id, {group,x,y}] of glyphs) {
        const axis = id === 'left_stick' ? 0 : id === 'right_stick' ? 2 : -1;
        const move = axis >= 0 && Math.hypot(analog[axis],analog[axis+1]) > .2;
        if (move) held.add(id + '_motion');
        const position = move ? 'translate(' + (x+analog[axis]*9) + ',' + (y+analog[axis+1]*9) + ')' : 'translate(' + x + ',' + y + ')';
        if (group.getAttribute('transform') !== position) group.setAttribute('transform',position);
        group.classList.toggle('pressed',held.has(id) || move);
    }
    const visual = [...held].map(id => glyphs.get(id.replace('_motion','')).label);
    active.forEach((pad,i) => {
        const map = mapping(pad,prefs);
        for (const source of inputs[i]) {
            if (!map.includes(source) && (pad.mapping !== 'standard' || source.startsWith('b'))) {
                const id = 'raw:' + source;
                if (!held.has(id)) visual.push(physicalLabel(source));
                held.add(id);
            }
        }
    });
    const status = visual.length ? visual.join(' · ') : t(pads.length ? 'waiting' : 'none');
    if ($('feedback').textContent !== status) $('feedback').textContent = status;
    if (audible && [...held].some(id => !previous.has(id))) beep();
    previous = held;
    requestAnimationFrame(tick);
}
translate();
requestAnimationFrame(tick);
