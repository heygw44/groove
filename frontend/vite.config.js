import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// 백엔드와 같은 오리진(8080)에서 서빙하려고 빌드 산출물을 Spring static/ 으로 출력한다.
// 개발 시 5173 dev server 가 /api 를 8080 으로 프록시한다(same-origin 이라 CORS 불필요).
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // 형제 backend/ 의 Spring static 디렉토리로 출력.
    outDir: '../backend/src/main/resources/static',
    // 빌드 전 static/ 을 비운다. static 엔 빌드 산출물만, 손관리 자산은 public/.
    emptyOutDir: true,
    // SecurityConfig 의 /assets/** GET permitAll 과 일치시킨다.
    assetsDir: 'assets',
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
