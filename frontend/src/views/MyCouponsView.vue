<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRouteQuery } from '@/composables/useRouteQuery'
import { myCoupons } from '@/api/coupons'
import { errorMessage } from '@/lib/problem-detail'
import { firstStr, pageParam } from '@/lib/query'
import { formatDate } from '@/lib/format'
import {
  couponDiscountLabel,
  couponStatusLabel,
  COUPON_STATUS_FILTER_OPTIONS,
} from '@/lib/order-enums'
import Pagination from '@/components/Pagination.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const { patchQuery } = useRouteQuery()

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

async function fetchCoupons(q) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  try {
    const res = await myCoupons(buildParams(q))
    if (seq !== reqSeq) return
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '내 쿠폰을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

function onStatusChange(value) {
  // 상태 변경 시 페이지를 처음으로 되돌림
  patchQuery({ status: value, page: undefined })
}

// status 뱃지 — ISSUED 만 강조, 나머지는 흐리게
function statusClass(status) {
  return status === 'ISSUED' ? 'bg-vinyl-black text-cream-50' : 'bg-vinyl-800/10 text-vinyl-800/60'
}

watch(() => route.query, fetchCoupons, { immediate: true })
</script>

<template>
  <section class="mx-auto max-w-2xl py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">내 쿠폰</h1>

    <div class="mb-4 max-w-xs">
      <BaseSelect
        :model-value="firstStr(route.query.status)"
        :options="COUPON_STATUS_FILTER_OPTIONS"
        aria-label="쿠폰 상태 필터"
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
        보유한 쿠폰이 없습니다.
        <RouterLink :to="{ name: 'coupons' }" class="ml-1 font-medium text-rust-600 hover:underline">
          쿠폰 받으러 가기
        </RouterLink>
      </div>

      <ul
        v-else
        class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50"
      >
        <li v-for="c in page.content" :key="c.memberCouponId" class="px-4 py-4">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-vinyl-black">{{ c.name }}</p>
              <p class="mt-0.5 text-sm font-medium text-rust-600">{{ couponDiscountLabel(c) }}</p>
              <p class="mt-1 text-xs text-vinyl-800/40">
                발급 {{ formatDate(c.issuedAt) }} · ~ {{ formatDate(c.expiresAt) }}까지
              </p>
              <p v-if="c.orderNumber" class="mt-0.5 text-xs text-vinyl-800/50">
                <RouterLink
                  :to="{ name: 'order-detail', params: { orderNumber: c.orderNumber } }"
                  class="hover:underline"
                >
                  주문 {{ c.orderNumber }}에 사용
                </RouterLink>
              </p>
            </div>
            <span
              class="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium"
              :class="statusClass(c.status)"
            >
              {{ couponStatusLabel(c.status) }}
            </span>
          </div>
        </li>
      </ul>

      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
