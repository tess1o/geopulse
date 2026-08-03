(function () {
    const META_CACHE = 'geopulse-sw-meta-v1';
    const SUPPORTS_FRESH_SHELL_REQUEST = '/__geopulse_supports_fresh_shell__';
    const RELOAD_ON_ACTIVATE_REQUEST = '/__geopulse_reload_on_activate__';

    self.addEventListener('install', (event) => {
        event.waitUntil((async () => {
            const cache = await caches.open(META_CACHE);
            const previousWorkerSupportsFreshShell = await cache.match(SUPPORTS_FRESH_SHELL_REQUEST);
            const shouldReloadClients = Boolean(self.registration.active && !previousWorkerSupportsFreshShell);

            await cache.put(SUPPORTS_FRESH_SHELL_REQUEST, new Response('1'));
            await cache.put(RELOAD_ON_ACTIVATE_REQUEST, new Response(shouldReloadClients ? '1' : '0'));
        })());
    });

    self.addEventListener('activate', (event) => {
        event.waitUntil((async () => {
            const cache = await caches.open(META_CACHE);
            const reloadFlag = await cache.match(RELOAD_ON_ACTIVATE_REQUEST);
            await cache.delete(RELOAD_ON_ACTIVATE_REQUEST);

            if (!reloadFlag || await reloadFlag.text() !== '1') {
                return;
            }

            await self.clients.claim();

            const clients = await self.clients.matchAll({
                type: 'window',
                includeUncontrolled: true
            });
            const origin = self.location.origin;

            await Promise.all(clients.map((client) => {
                const url = new URL(client.url);
                if (url.origin !== origin || url.pathname.startsWith('/api/')) {
                    return Promise.resolve();
                }

                return client.navigate(client.url).catch(() => undefined);
            }));
        })());
    });
}());
