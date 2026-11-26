import { bundled } from './games.js';

const libraryKey = 'link-arcade-library-v1';
const form = document.querySelector('#add-game');
const input = document.querySelector('#game-url');
const status = document.querySelector('#import-status');
const submit = form.querySelector('button');
const grid = document.querySelector('#games');

async function loadDefaults() {
    try {
        const response = await fetch('./default-games.json');
        if (response.ok) {
            const games = await response.json();
            return games.map(({ url, title, cover, description }) => ({ url, title, cover, description }));
        }
    } catch {
        // The built-in test and saved URLs are still available.
    }
    return [];
}

const defaults = await loadDefaults();

function webUrl(value, base) {
    const url = new URL(value, base);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
        throw new Error('Use an HTTP or HTTPS link without credentials.');
    }
    return url;
}

function readLibrary() {
    let entries = [];
    let seeded = new Set();
    try {
        const saved = JSON.parse(localStorage.getItem(libraryKey) || '[]');
        const games = Array.isArray(saved) ? saved : saved?.games;
        if (Array.isArray(saved?.seeded)) seeded = new Set(saved.seeded);
        if (Array.isArray(games)) entries = games.filter(entry => {
            try {
                return typeof entry.title === 'string' && !!webUrl(entry.url);
            } catch {
                return false;
            }
        });
    } catch {
        // Corrupt or disabled storage starts with a fresh library.
    }
    const additions = defaults.filter(game => {
        if (seeded.has(game.url)) return false;
        seeded.add(game.url);
        return !entries.some(saved => saved.url === game.url);
    });
    return { games: [...additions, ...entries], seeded: [...seeded] };
}

let library = readLibrary();

function saveLibrary(next) {
    library.games = next;
    render();
    try {
        localStorage.setItem(libraryKey, JSON.stringify(library));
        return true;
    } catch {
        return false;
    }
}

async function inspect(url) {
    const options = { credentials: 'omit', referrerPolicy: 'no-referrer', signal: AbortSignal.timeout(3500) };
    let page = url;
    let title = url.hostname;
    try {
        const response = await fetch(url, options);
        if (!response.ok) return { url: url.href, title };
        page = webUrl(response.url);
        page.hash = url.hash;
        if (url.protocol === 'https:' && page.protocol !== 'https:') return { url: url.href, title };
        const markup = document.createElement('template');
        markup.innerHTML = await response.text();
        title = markup.content.querySelector('title')?.textContent.trim().slice(0, 80) || page.hostname;
        const link = markup.content.querySelector('link[rel~="tv-game-manifest" i][href]');
        if (!link) return { url: page.href, title };
        const manifest = webUrl(link.getAttribute('href'), page);
        if (manifest.origin !== page.origin) return { url: page.href, title };
        const metadata = await fetch(manifest, options);
        if (!metadata.ok || new URL(metadata.url).origin !== page.origin) return { url: page.href, title };
        const game = await metadata.json();
        if (game.version !== 1 || typeof game.title !== 'string' || !game.title.trim()) return { url: page.href, title };
        title = game.title.trim().slice(0, 80);
        const cover = typeof game.cover === 'string' && game.cover ? webUrl(game.cover, metadata.url) : null;
        return { url: page.href, title, cover: cover?.origin === page.origin ? cover.href : '' };
    } catch {
        // Cross-origin metadata is optional; the game still opens as a normal link.
        return { url: page.href, title };
    }
}

function card(game, removable = false) {
    const article = document.createElement('article');
    article.className = 'card';
    const cover = document.createElement('div');
    cover.className = 'cover';
    if (game.cover) {
        const image = document.createElement('img');
        image.src = game.cover;
        image.alt = '';
        image.referrerPolicy = 'no-referrer';
        image.addEventListener('error', () => image.remove(), { once: true });
        cover.append(image);
    }
    const body = document.createElement('div');
    body.className = 'card-body';
    const heading = document.createElement('h3');
    heading.textContent = game.title;
    const description = document.createElement('p');
    description.textContent = game.description || new URL(game.url).hostname;
    const actions = document.createElement('div');
    actions.className = 'card-actions';
    const play = document.createElement('a');
    play.className = 'play-link';
    play.href = game.id ? './play.html?game=' + game.id : game.url;
    play.textContent = 'Play';
    play.setAttribute('aria-label', 'Play ' + game.title);
    actions.append(play);
    if (!game.id) {
        play.target = '_blank';
        play.rel = 'noopener noreferrer';
    }
    if (removable) {
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'remove';
        remove.textContent = 'Remove';
        remove.setAttribute('aria-label', 'Remove ' + game.title);
        remove.addEventListener('click', () => {
            const saved = saveLibrary(library.games.filter(entry => entry.url !== game.url));
            status.textContent = saved ? 'Game removed.' : 'Game removed for this visit only.';
            input.focus();
        });
        actions.append(remove);
    }
    body.append(heading, description, actions);
    article.append(cover, body);
    return article;
}

function render() {
    grid.replaceChildren(...library.games.map(game => card(game, true)), ...bundled.map(game => card(game)));
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (submit.disabled) return;
    submit.disabled = true;
    try {
        const url = webUrl(input.value.trim());
        status.textContent = 'Looking up game details…';
        const entry = await inspect(url);
        if (library.games.some(game => game.url === entry.url)) {
            status.textContent = 'This game is already in your library.';
            return;
        }
        const saved = saveLibrary([...library.games, entry]);
        form.reset();
        status.textContent = saved ? 'Game saved in this browser.' : 'Game added for this visit only. The browser could not save it.';
    } catch (error) {
        status.textContent = error.message;
    } finally {
        submit.disabled = false;
    }
});

window.addEventListener('storage', event => {
    if ((event.key === libraryKey || event.key === null) && event.storageArea === localStorage) {
        library = readLibrary();
        render();
    }
});

render();
