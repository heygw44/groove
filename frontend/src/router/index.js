import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from '@/lib/redirect'
import { firstStr } from '@/lib/query'
import { isPaymentResult } from '@/lib/payment-result'

// History(clean URL) 라우팅.
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
    // 구매 여정. 결제/체크아웃/게스트조회는 requiresAuth 없음(게스트 허용).
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
      // '/orders/:orderNumber' 보다 먼저 선언.
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
    // 쿠폰. 목록은 public, 내 쿠폰은 회원 전용.
    {
      path: '/coupons',
      name: 'coupons',
      component: () => import('@/views/CouponListView.vue'),
    },
    {
      path: '/me/coupons',
      name: 'my-coupons',
      component: () => import('@/views/MyCouponsView.vue'),
      meta: { requiresAuth: true },
    },
    // 관리자 콘솔. 중첩 라우트 — AdminLayout 아래 자식 뷰. 부모 meta 가 자식으로 머지돼 모든 하위 보호.
    {
      path: '/admin',
      component: () => import('@/components/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', name: 'admin-dashboard', component: () => import('@/views/admin/AdminDashboardView.vue') },
        { path: 'albums', name: 'admin-albums', component: () => import('@/views/admin/AdminAlbumsView.vue') },
        { path: 'albums/new', name: 'admin-album-new', component: () => import('@/views/admin/AdminAlbumFormView.vue') },
        {
          path: 'albums/:id/edit',
          name: 'admin-album-edit',
          component: () => import('@/views/admin/AdminAlbumFormView.vue'),
        },
        { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/AdminOrdersView.vue') },
        {
          path: 'orders/:orderNumber',
          name: 'admin-order-detail',
          component: () => import('@/views/admin/AdminOrderDetailView.vue'),
        },
        { path: 'coupons', name: 'admin-coupons', component: () => import('@/views/admin/AdminCouponsView.vue') },
        { path: 'coupons/new', name: 'admin-coupon-new', component: () => import('@/views/admin/AdminCouponFormView.vue') },
      ],
    },
    {
      // 매칭 실패 경로용 catch-all.
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

// 인증 가드 — 라우트 meta 기반.
router.beforeEach((to) => {
  const auth = useAuthStore()

  // 게스트 토스 결제 콜백(#308): 서버가 /orders/{n}?payment= (취소 시 orderId 없어 /orders?payment=fail) 로 302하는데
  // 이 라우트들은 requiresAuth라 미인증 게스트가 로그인으로 바운스된다. 결과 확인용 공개 라우트(guest-lookup)로 안내한다.
  const payment = firstStr(to.query.payment)
  if (
    isPaymentResult(payment) &&
    !auth.isAuthenticated &&
    (to.name === 'order-detail' || to.name === 'orders')
  ) {
    const query = { payment }
    if (to.params.orderNumber) query.orderNumber = to.params.orderNumber
    return { name: 'guest-lookup', query }
  }

  // 로그인 필수 라우트에 미인증 접근 → 로그인으로, redirect 보존.
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 관리자 전용 라우트에 비관리자 접근 → 홈.
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
  // 게스트 전용에 인증 상태 접근 → 복귀 경로 또는 홈.
  if (to.meta.guestOnly && auth.isAuthenticated) {
    return safeRedirect(to.query.redirect) || { name: 'home' }
  }
})

export default router
