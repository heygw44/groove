<script setup>
// 관리자 콘솔 레이아웃(#119) — 좌측 사이드바 + <router-view/>. App.vue 의 max-w-6xl main 안에서 렌더된다.
import { useRoute } from 'vue-router'

const route = useRoute()

const NAV = [
  { to: '/admin', label: '대시보드', exact: true },
  { to: '/admin/albums', label: '앨범' },
  { to: '/admin/orders', label: '주문' },
  { to: '/admin/coupons', label: '쿠폰' },
]

// 대시보드(/admin)는 정확 매칭, 나머지는 접두 매칭(하위 상세/폼에서도 활성 유지).
function isActive(item) {
  return item.exact ? route.path === item.to : route.path.startsWith(item.to)
}
</script>

<template>
  <div class="flex flex-col gap-6 md:flex-row">
    <aside class="shrink-0 md:w-48">
      <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">관리자 콘솔</h2>
      <nav class="flex gap-1 overflow-x-auto md:flex-col" aria-label="관리자 메뉴">
        <RouterLink
          v-for="item in NAV"
          :key="item.to"
          :to="item.to"
          class="whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition"
          :class="
            isActive(item)
              ? 'bg-vinyl-black text-cream-50'
              : 'text-vinyl-800 hover:bg-cream-100'
          "
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="min-w-0 flex-1">
      <RouterView />
    </div>
  </div>
</template>
