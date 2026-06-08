import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore, clearLegacyTokenStorage } from '@/stores/auth'
import { tryRefresh } from '@/api/client'
import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// #163: accessToken 은 메모리 전용이라 새로고침 시 사라진다. 이전에 로그인한 적이 있으면(auth.hadSession)
// 부팅 시 1회 silent refresh 로 HttpOnly refresh 쿠키를 사용해 세션을 복원한다. 그래야 라우터 가드가
// 보호 라우트를 미인증으로 오판하지 않는다. 실패하면 상태를 정리한다. 게스트(hadSession=false)는
// refresh 호출 없이 즉시 마운트한다.
async function boot() {
  // 이전 빌드가 localStorage 에 남긴 평문 토큰을 1회 제거(import-time 사이드이펙트 회피, 코드리뷰 #5).
  clearLegacyTokenStorage()
  // 세션 복원은 best-effort — tryRefresh 는 내부에서 예외를 삼키지만, useAuthStore/pinia 등
  // 예상 밖 동기 예외가 나도 앱은 반드시 마운트되도록 복원 로직만 try 로 감싼다(CodeRabbit).
  try {
    const auth = useAuthStore(pinia)
    if (auth.hadSession && !auth.isAuthenticated) {
      const restored = await tryRefresh()
      if (!restored) auth.logout()
    }
  } catch (e) {
    console.error('세션 복원 실패 — 게스트 상태로 마운트:', e)
  }
  app.mount('#app')
}

boot()
