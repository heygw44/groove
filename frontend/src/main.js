import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from '@/stores/auth'
import { tryRefresh } from '@/api/client'
import './assets/main.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// #163: accessToken 은 메모리 전용이라 새로고침 시 사라진다. 이전에 로그인한 흔적(email 힌트)이 있으면
// 부팅 시 1회 silent refresh 로 HttpOnly refresh 쿠키를 사용해 세션을 복원한다. 그래야 라우터 가드가
// 보호 라우트를 미인증으로 오판하지 않는다. 실패하면 stale 힌트를 정리한다. 게스트(힌트 없음)는
// refresh 호출 없이 즉시 마운트한다.
async function boot() {
  const auth = useAuthStore(pinia)
  if (auth.email && !auth.isAuthenticated) {
    const restored = await tryRefresh()
    if (!restored) auth.logout()
  }
  app.mount('#app')
}

boot()
