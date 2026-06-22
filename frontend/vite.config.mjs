import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { VitePWA } from 'vite-plugin-pwa';
import Components from 'unplugin-vue-components/vite';
import {PrimeVueResolver} from '@primevue/auto-import-resolver';

const appDescription = 'A self-hosted, privacy-first location tracking platform with automatic trip detection, Immich integration, and detailed analytics.';

// https://vitejs.dev/config/
export default defineConfig({
    build: {
        outDir: 'dist' // explicitly define output directory
    },
    base: "/",
    plugins: [
        vue(),
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
                navigateFallback: '/index.html',
                navigateFallbackDenylist: [
                    /^\/api\//,
                    /^\/config\.js$/,
                    /^\/osm\/tiles\//
                ],
                globPatterns: [
                    '**/*.{js,css,html,ico,png,svg,woff,woff2,ttf,eot}'
                ],
                maximumFileSizeToCacheInBytes: 5 * 1024 * 1024
            }
        })
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    server: {
        host: true,
        port: 5555, // or any port you like
        allowedHosts: [
            'f72f-5-58-105-166.ngrok-free.app'
        ],
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
            }
        }
    },
});
