import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/lib/redirect'

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
      path: '/catalog',
      name: 'catalog',
      component: () => import('@/views/CatalogView.vue'),
    },
    {
      path: '/albums/:id',
      name: 'album-detail',
      component: () => import('@/views/AlbumDetailView.vue'),
    },
    {
      path: '/artists/:id',
      name: 'artist-detail',
      component: () => import('@/views/ArtistDetailView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('@/views/SignupView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/me',
      name: 'my-page',
      component: () => import('@/views/MyPageView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/me/edit',
      name: 'profile-edit',
      component: () => import('@/views/ProfileEditView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/me/password',
      name: 'password-change',
      component: () => import('@/views/PasswordChangeView.vue'),
      meta: { requiresAuth: true },
    },
    // 구매 여정(#116). 결제/체크아웃/게스트조회는 게스트도 써야 하므로 requiresAuth 를 걸지 않는다.
    // 모든 경로는 SpaRoutes(/cart·/checkout·/orders/**)에 매칭돼 새로고침·딥링크가 동작한다(백엔드 무수정).
    {
      path: '/cart',
      name: 'cart',
      component: () => import('@/views/CartView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/checkout',
      name: 'checkout',
      component: () => import('@/views/CheckoutView.vue'),
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrderListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // '/orders/:orderNumber' 보다 먼저 선언 — "lookup" 이 orderNumber 로 매칭되지 않게 한다.
      path: '/orders/lookup',
      name: 'guest-lookup',
      component: () => import('@/views/GuestLookupView.vue'),
    },
    {
      path: '/orders/:orderNumber/pay',
      name: 'order-pay',
      component: () => import('@/views/PaymentView.vue'),
    },
    {
      path: '/orders/:orderNumber',
      name: 'order-detail',
      component: () => import('@/views/OrderDetailView.vue'),
      meta: { requiresAuth: true },
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

// 인증 가드 — 라우트 meta 기반. 클라 UI 보호일 뿐이며 실제 권한 검증은 서버가 한다.
// Pinia store 는 반드시 가드 함수 내부에서 호출한다(모듈 최상단 호출은 설치 순서 의존으로 실패).
router.beforeEach((to) => {
  const auth = useAuthStore()

  // 로그인 필수 라우트에 미인증 접근 → 로그인으로, 복귀 경로(redirect) 보존.
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 관리자 전용 라우트에 비관리자 접근 → 홈. (admin 라우트는 후속 이슈에서 추가)
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
  // 게스트 전용(로그인/회원가입)에 인증 상태 접근 → 안전한 복귀 경로 또는 홈.
  if (to.meta.guestOnly && auth.isAuthenticated) {
    return safeRedirect(to.query.redirect) || { name: 'home' }
  }
})

export default router
