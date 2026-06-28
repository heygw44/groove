<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRouteQuery } from '@/composables/useRouteQuery'
import { usePaymentResultBanner } from '@/composables/usePaymentResultBanner'
import { myOrders } from '@/api/orders'
import { errorMessage } from '@/lib/problem-detail'
import { firstStr, pageParam } from '@/lib/query'
import { formatWon, formatDate } from '@/lib/format'
import { orderStatusLabel, ORDER_STATUS_FILTER_OPTIONS } from '@/lib/order-enums'
import Pagination from '@/components/Pagination.vue'
import PaymentResultBanner from '@/components/payment/PaymentResultBanner.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const { patchQuery } = useRouteQuery()
// 토스 결제 취소 콜백: 서버가 orderId 없이 /orders?payment=fail 로 302 할 때 회원에게 결과를 안내하고 URL 을 정리한다.
const { paymentResult } = usePaymentResultBanner()

const page = ref(null)
const loading = ref(true)
const error = ref('')

const PAGE_SIZE = 10
let reqSeq = 0

function buildParams(q) {
  const params = { size: PAGE_SIZE }
  const status = firstStr(q.status)
  if (status !== '') params.status = status
  const p = pageParam(q)
  if (p) params.page = p
  return params
}

async function fetchOrders(q) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  try {
    const res = await myOrders(buildParams(q))
    if (seq !== reqSeq) return
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '주문 내역을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

function onStatusChange(value) {
  // 상태 변경 시 페이지를 처음으로 되돌림
  patchQuery({ status: value, page: undefined })
}

// 조회에 영향 주는 status·page 만 감시. payment 등 무관 쿼리 정리(콜백 후 usePaymentResultBanner)에
// 중복 조회되지 않도록 소스별(getter 배열) 값 비교를 쓴다.
watch(
  [() => firstStr(route.query.status), () => pageParam(route.query)],
  () => fetchOrders(route.query),
  { immediate: true },
)
</script>

<template>
  <section class="mx-auto max-w-2xl py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">주문 내역</h1>

    <!-- 토스 결제 콜백 결과 안내(주로 취소 시 /orders?payment=fail) -->
    <PaymentResultBanner v-if="paymentResult" :result="paymentResult" />

    <div class="mb-4 max-w-xs">
      <BaseSelect
        :model-value="firstStr(route.query.status)"
        :options="ORDER_STATUS_FILTER_OPTIONS"
        aria-label="주문 상태 필터"
        @update:model-value="onStatusChange"
      />
    </div>

    <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else>
      <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

      <div
        v-else-if="!page || !page.content.length"
        class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
      >
        주문 내역이 없습니다.
      </div>

      <ul v-else class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50">
        <li v-for="o in page.content" :key="o.orderNumber">
          <RouterLink
            :to="{ name: 'order-detail', params: { orderNumber: o.orderNumber } }"
            class="flex items-center gap-3 px-4 py-4 transition hover:bg-cream-100"
          >
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-vinyl-black">
                {{ o.representativeAlbumTitle }}
                <span v-if="o.itemCount > 1" class="text-vinyl-800/50">외 {{ o.itemCount - 1 }}건</span>
              </p>
              <p class="font-mono text-xs text-vinyl-800/50">{{ o.orderNumber }}</p>
              <p class="text-xs text-vinyl-800/40">{{ formatDate(o.createdAt) }}</p>
            </div>
            <div class="text-right">
              <span class="rounded-full bg-vinyl-black px-2.5 py-0.5 text-xs font-medium text-cream-50">
                {{ orderStatusLabel(o.status) }}
              </span>
              <p class="mt-1 text-sm font-semibold text-vinyl-black">{{ formatWon(o.totalAmount) }}</p>
            </div>
          </RouterLink>
        </li>
      </ul>

      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
