import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
      // 라운지 WebSocket. ws: true 여야 HTTP 업그레이드 요청을 WebSocket 으로 넘긴다. 같은 출처(3000)라 로그인 쿠키가 그대로 붙는다
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
