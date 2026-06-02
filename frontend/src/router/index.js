import { createRouter, createWebHistory } from 'vue-router'

// History(clean URL) 라우팅. 서버는 SpaForwardConfig 가 등록된 SPA 경로를 index.html 로
// forward 하므로 /cart 등 직접 진입·새로고침에도 SPA 셸이 로드된다.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      // 클라이언트 라우팅 중 매칭 실패한 경로용 catch-all.
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
