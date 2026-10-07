import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// Em dev, o Vite repassa /api para o backend. Para o navegador, frontend e
// API ficam na mesma origem, então não precisamos de CORS.
// Em produção o nginx fará o mesmo papel.
const apiProxy = {
  '/api': 'http://localhost:8080',
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Gera o manifest e o service worker (Workbox) no build de produção.
    VitePWA({
      // Versão nova publicada: o service worker se atualiza sozinho na próxima abertura.
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'favicon.ico', 'apple-touch-icon-180x180.png'],
      manifest: {
        name: 'Bicavi',
        short_name: 'Bicavi',
        description: 'Seu assistente financeiro',
        lang: 'pt-BR',
        start_url: '/',
        scope: '/',
        // standalone: abre em tela cheia, sem a barra do navegador.
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#fafafa',
        theme_color: '#18181b',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Cache só da "casca" do app (arquivos gerados pelo build).
        globPatterns: ['**/*.{js,css,html,svg,png,ico}'],
        // Navegar para /categorias etc. offline devolve o index.html (o react-router
        // resolve a rota). /api fica de fora: nunca responder API com HTML.
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        // Nenhuma regra para /api: as chamadas à API SEMPRE vão para a rede e
        // nunca são guardadas (dados financeiros não ficam no aparelho).
        runtimeCaching: [
          {
            // CSS da fonte Inter: usa o cache, mas busca versão nova em segundo plano.
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/,
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'google-fonts-css' },
          },
          {
            // Arquivos da fonte: nunca mudam para a mesma URL, cache por 1 ano.
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-files',
              cacheableResponse: { statuses: [0, 200] },
              expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
        ],
      },
    }),
  ],
  server: {
    proxy: apiProxy,
  },
  // `npm run preview` serve o build de produção (com service worker): precisa do mesmo proxy.
  preview: {
    proxy: apiProxy,
  },
})
