import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// 백엔드와 같은 오리진(8080)에서 서빙하기 위해 빌드 산출물을 Spring 의 static/ 으로 출력한다.
// 개발 시에는 5173 dev server 가 /api 요청을 8080 백엔드로 프록시한다(same-origin → CORS 불필요).
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // frontend/ 가 root 이므로 상대경로로 상위의 Spring static 디렉토리를 가리킨다.
    outDir: '../src/main/resources/static',
    // 빌드 전 static/ 을 비운다(M14 잔재 포함). static 엔 빌드 산출물만 둔다 — 손관리 자산은 public/.
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
