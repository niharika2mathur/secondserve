import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies /api to the Java backend, so the browser only ever talks to one origin.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': { target: process.env.BACKEND_URL || 'http://localhost:8080', changeOrigin: true } } }
});
