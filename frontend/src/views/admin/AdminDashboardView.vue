<script setup>
import { ref, onMounted } from 'vue'
import { listOrders } from '@/api/admin'
import { errorMessage } from '@/lib/problem-detail'
import { orderStatusLabel } from '@/lib/order-enums'
import { formatWon, formatDate } from '@/lib/format'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

// 전용 요약 API 가 없으므로 상태별 카운트는 GET /admin/orders?status=X&size=1 의 totalElements 로 집계한다.
const STATUS_CARDS = [
  { status: 'PAID', label: '결제 완료' },
  { status: 'PREPARING', label: '배송 준비' },
  { status: 'SHIPPED', label: '배송 중' },
  { status: 'DELIVERED', label: '배송 완료' },
]

const counts = ref({})
const recent = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    ...STATUS_CARDS.map((c) => listOrders({ status: c.status, size: 1 })),
    listOrders({ size: 5 }),
  ])
  const recentRes = results[results.length - 1]

  if (results.every((r) => r.status === 'rejected')) {
    error.value = errorMessage(recentRes.reason, '대시보드를 불러오지 못했습니다.')
    loading.value = false
    return
  }

  const next = {}
  STATUS_CARDS.forEach((c, i) => {
    const r = results[i]
    next[c.status] = r.status === 'fulfilled' ? r.value.totalElements : null
  })
  counts.value = next
  recent.value = recentRes.status === 'fulfilled' ? recentRes.value.content : []
  loading.value = false
}

onMounted(load)
</script>

<template>
  <section>
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">대시보드</h1>

    <!-- 운영 안내: 결제 완료 주문은 PAID 에 머문다 → 관리자가 DELIVERED 까지 전환해야 구매자 리뷰 작성 가능. -->
    <div class="mb-6 rounded-lg border border-gold-400/40 bg-gold-400/10 px-4 py-3 text-sm text-vinyl-800">
      <p class="font-medium text-vinyl-black">주문 처리 안내</p>
      <p class="mt-1">
        결제가 끝난 주문은 <b>결제 완료(PAID)</b> 상태에 머뭅니다. 주문 상세에서
        <b>배송 준비 → 배송 중 → 배송 완료</b> 로 전환하면 해당 구매자가 리뷰를 작성할 수 있습니다.
      </p>
    </div>

    <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else>
      <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

      <template v-else>
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <RouterLink
            v-for="c in STATUS_CARDS"
            :key="c.status"
            :to="{ name: 'admin-orders', query: { status: c.status } }"
            class="rounded-lg border border-vinyl-800/15 bg-cream-50 p-4 transition hover:bg-cream-100"
          >
            <p class="text-xs text-vinyl-800/60">{{ c.label }}</p>
            <p class="mt-1 text-2xl font-bold text-vinyl-black">
              {{ counts[c.status] == null ? '—' : counts[c.status] }}
            </p>
          </RouterLink>
        </div>

        <div class="mt-8">
          <div class="mb-3 flex items-center justify-between">
            <h2 class="font-display text-lg font-bold text-vinyl-black">최근 주문</h2>
            <RouterLink :to="{ name: 'admin-orders' }" class="text-sm font-medium text-rust-600 hover:underline">
              전체 보기 →
            </RouterLink>
          </div>

          <div
            v-if="!recent.length"
            class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
          >
            주문이 없습니다.
          </div>

          <ul v-else class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50">
            <li v-for="o in recent" :key="o.orderNumber">
              <RouterLink
                :to="{ name: 'admin-order-detail', params: { orderNumber: o.orderNumber } }"
                class="flex items-center gap-3 px-4 py-3 transition hover:bg-cream-100"
              >
                <div class="min-w-0 flex-1">
                  <p class="font-mono text-xs text-vinyl-800/60">{{ o.orderNumber }}</p>
                  <p class="text-xs text-vinyl-800/40">{{ formatDate(o.createdAt) }}</p>
                </div>
                <span class="rounded-full bg-vinyl-black px-2.5 py-0.5 text-xs font-medium text-cream-50">
                  {{ orderStatusLabel(o.status) }}
                </span>
                <p class="w-24 text-right text-sm font-semibold text-vinyl-black">
                  {{ formatWon(o.totalAmount) }}
                </p>
              </RouterLink>
            </li>
          </ul>
        </div>
      </template>
    </template>
  </section>
</template>
