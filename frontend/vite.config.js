import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// W trybie dev zapytania /api trafiają do backendu Spring Boot (port 8080).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
      '/v3/api-docs': 'http://localhost:8080',
    },
  },
})
