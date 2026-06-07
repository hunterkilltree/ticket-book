/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// The backend is reached through the nginx LoadBalancer at http://localhost.
// Override with VITE_PROXY_TARGET if your cluster is elsewhere.
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: proxyTarget, changeOrigin: true },
      // booking-service STOMP/WebSocket endpoint
      '/ws': { target: proxyTarget, changeOrigin: true, ws: true },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: 'coverage',
    },
  },
});
