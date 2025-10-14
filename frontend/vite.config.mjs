import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import {PrimeVueResolver} from '@primevue/auto-import-resolver';

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