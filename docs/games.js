const games = {
    'little-drive': ['A Little Drive', 'little-drive.language'],
    'tidy-robot': ['Tidy Robot', 'tidy-robot.language'],
    'firemen-route': ['Firemen Route', 'firemen-route.language'],
};

export const littleDriveUrl = 'https://carlosalbaladejo.github.io/little-drive/';

export function managedGame(address) {
    const url = new URL(address);
    if (url.origin !== new URL(littleDriveUrl).origin) return null;
    const path = /^\/([a-z-]+)(?:\/index\.html|\/)?$/.exec(url.pathname);
    const entry = path && Object.hasOwn(games,path[1]) ? games[path[1]] : null;
    return entry ? { title: entry[0], languageKey: entry[1], url: url.href, manifest: new URL('/' + path[1] + '/game.json',url) } : null;
}
