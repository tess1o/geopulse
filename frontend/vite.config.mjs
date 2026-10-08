import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { VitePWA } from 'vite-plugin-pwa';
import Components from 'unplugin-vue-components/vite';
import {PrimeVueResolver} from '@primevue/auto-import-resolver';

const appDescription = 'A self-hosted, privacy-first location tracking platform with automatic trip detection, Immich integration, and detailed analytics.';

const noStoreHeaders = {
    'Content-Type': 'application/javascript; charset=utf-8',
    'Cache-Control': 'no-store, no-cache, must-revalidate',
    'Service-Worker-Allowed': '/'
};

const devServiceWorkerCleanupScript = `
self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        if ('caches' in self) {
            const cacheNames = await caches.keys();
            await Promise.all(cacheNames.map((cacheName) => caches.delete(cacheName)));
        }

        await self.clients.claim();
        await self.registration.unregister();
    })());
});
`;

const devRegisterServiceWorkerCleanupScript = `
(function () {
    async function cleanupDevelopmentServiceWorkers() {
        try {
            if ('serviceWorker' in navigator) {
                const registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
                await registration.update();
            }
        } catch (error) {
            console.warn('Failed to register development service worker cleanup', error);
        }

        try {
            if ('serviceWorker' in navigator) {
                const registrations = await navigator.serviceWorker.getRegistrations();
                await Promise.all(registrations.map((registration) => registration.unregister()));
            }
        } catch (error) {
            console.warn('Failed to unregister development service workers', error);
        }

        try {
            if ('caches' in window) {
                const cacheNames = await caches.keys();
                await Promise.all(cacheNames.map((cacheName) => caches.delete(cacheName)));
            }
        } catch (error) {
            console.warn('Failed to clear development caches', error);
        }
    }

    if ('serviceWorker' in navigator || 'caches' in window) {
        window.addEventListener('load', () => {
            cleanupDevelopmentServiceWorkers();
        });
    }
}());
`;

function devServiceWorkerCleanupPlugin() {
    return {
        name: 'geopulse-dev-service-worker-cleanup',
        apply: 'serve',
        configureServer(server) {
            server.middlewares.use((req, res, next) => {
                const pathname = new URL(req.url || '/', 'http://localhost').pathname;

                if (pathname === '/sw.js') {
                    res.writeHead(200, noStoreHeaders);
                    res.end(devServiceWorkerCleanupScript);
                    return;
                }

                if (pathname === '/registerSW.js') {
                    res.writeHead(200, noStoreHeaders);
                    res.end(devRegisterServiceWorkerCleanupScript);
                    return;
                }

                next();
            });
        }
    };
}

// leaflet.heat and leaflet.markercluster are pre-ESM plugins that patch the global `L` as soon as they run.
// Leaflet itself is CommonJS, so the build only executes it (and sets window.L) when something first calls its
// wrapped require. Nothing ties that to the plugins' chunks, so a lazy route can evaluate a plugin chunk first and
// fail with "L is not defined". Importing leaflet into the plugin modules gives Rollup a real dependency edge and
// binds `L` locally.
const LEAFLET_GLOBAL_PLUGINS = /[\\/]node_modules[\\/]leaflet\.(heat|markercluster)[\\/]dist[\\/][^/\\]+\.js$/;

function leafletPluginImportsPlugin() {
    return {
        name: 'geopulse-leaflet-plugin-imports',
        apply: 'build',
        enforce: 'pre',
        transform(code, id) {
            if (!LEAFLET_GLOBAL_PLUGINS.test(id.split('?')[0])) return null;
            return { code: `import L from 'leaflet';\n${code}`, map: null };
        }
    };
}

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
    esbuild: mode === 'production'
        ? { pure: ['console.log', 'console.info', 'console.debug'] }
        : undefined,
    build: {
        outDir: 'dist' // explicitly define output directory
    },
    base: "/",
    plugins: [
        vue({
            template: {
                compilerOptions: {
                    isCustomElement: tag => tag.startsWith('pnx-')
                }
            }
        }),
        devServiceWorkerCleanupPlugin(),
        leafletPluginImportsPlugin(),
        Components({
            resolvers: [
                PrimeVueResolver()
            ]
        }),
        VitePWA({
            registerType: 'autoUpdate',
            injectRegister: 'script-defer',
            includeAssets: [
                'favicon-16x16.png',
                'favicon-32x32.png',
                'apple-touch-icon.png',
                'geopulse-logo.svg'
            ],
            manifest: {
                name: 'GeoPulse',
                short_name: 'GeoPulse',
                description: appDescription,
                theme_color: '#1a56db',
                background_color: '#ffffff',
                display: 'standalone',
                start_url: '/',
                scope: '/',
                icons: [
                    {
                        src: 'pwa-192x192.png',
                        sizes: '192x192',
                        type: 'image/png',
                        purpose: 'any'
                    },
                    {
                        src: 'pwa-512x512.png',
                        sizes: '512x512',
                        type: 'image/png',
                        purpose: 'any'
                    },
                    {
                        src: 'pwa-512x512.png',
                        sizes: '512x512',
                        type: 'image/png',
                        purpose: 'maskable'
                    }
                ]
            },
            workbox: {
                cleanupOutdatedCaches: true,
                clientsClaim: true,
                skipWaiting: true,
                navigateFallback: null,
                globPatterns: [
                    '**/*.{js,css,ico,png,svg,woff,woff2,ttf,eot}'
                ],
                globIgnores: [
                    '**/index.html',
                    // Panoramax photo viewer (~1.5 MB) is only loaded when its dialog opens; cache on first use instead.
                    '**/index_photoviewer-*.js'
                ],
                runtimeCaching: [
                    {
                        urlPattern: /\/assets\/index_photoviewer-[^/]+\.js$/,
                        handler: 'CacheFirst',
                        options: {
                            cacheName: 'panoramax-viewer',
                            expiration: {maxEntries: 2}
                        }
                    }
                ],
                maximumFileSizeToCacheInBytes: 5 * 1024 * 1024
            }
        })
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
            '@panoramax/web-viewer-photoviewer': fileURLToPath(new URL('./node_modules/@panoramax/web-viewer/build/cjs/index_photoviewer.js', import.meta.url))
        }
    },
    server: {
        host: true,
        port: 5555, // or any port you like
        proxy: {
            '/osm/tiles': {
                configure: (proxy, options) => {
                    proxy.on('proxyReq', (proxyReq, req, res) => {
                        // Determine which subdomain
                        const match = req.url.match(/^\/osm\/tiles\/([abc])\//);
                        if (match) {
                            const subdomain = match[1];
                            const newPath = req.url.replace(/^\/osm\/tiles\/[abc]/, '');

                            // Completely override the target
                            proxyReq.path = newPath;
                            proxyReq.host = `${subdomain}.tile.openstreetmap.org`;
                            proxyReq.removeHeader('cookie');
                            proxyReq.setHeader('host', `${subdomain}.tile.openstreetmap.org`);
                        }
                    });
                },
                target: 'https://a.tile.openstreetmap.org',
                changeOrigin: true
            },
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                secure: false,
                ws: true, // proxy websockets
                configure: (proxy) => {
                    proxy.on('error', (err) => {
                        console.log('proxy error', err);
                    });
                    proxy.on('proxyReq', (proxyReq, req) => {
                        console.log('Sending Request to the Target:', req.method, req.url);
                    });
                    proxy.on('proxyRes', (proxyRes, req) => {
                        console.log('Received Response from the Target:', proxyRes.statusCode, req.url);
                    });
                },
            },
            '/mcp': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                secure: false
            }
        }
    },
}));
