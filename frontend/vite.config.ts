import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxies API calls to the backend module (see ../backend/src/main/resources/application.yml,
    // server.port: 8081) so the frontend dev server can call relative "/api/..." URLs without
    // running into CORS, matching how the built frontend would be served from the same origin
    // as the backend in a combined deployment.
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
