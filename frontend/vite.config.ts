import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Em dev, o Vite repassa /api para o backend. Para o navegador, frontend e
    // API ficam na mesma origem (localhost:5173), então não precisamos de CORS.
    // Em produção o nginx fará o mesmo papel.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
