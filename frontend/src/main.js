import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore, clearLegacyTokenStorage } from '@/stores/auth'
import { tryRefresh, REFRESH_INVALID } from '@/api/client'
import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// 부팅 시 HttpOnly refresh 쿠키로 세션 복원 (이전 로그인 이력 있을 때만), 실패 시 logout.
async function boot() {
  // 이전 빌드가 localStorage 에 남긴 평문 토큰 제거.
  clearLegacyTokenStorage()
  try {
    const auth = useAuthStore(pinia)
    if (auth.hadSession && !auth.isAuthenticated) {
      // ok → 인증됨 / invalid → 세션 정리 / transient → 세션 유지(게스트 마운트, 나중 재시도 허용)
      const status = await tryRefresh()
      if (status === REFRESH_INVALID) auth.logout()
    }
  } catch (e) {
    console.error('세션 복원 실패 — 게스트 상태로 마운트:', e)
  }
  app.mount('#app')
}

boot()
