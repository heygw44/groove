<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { listCoupons, issueCoupon } from '@/api/coupons'
import { errorMessage } from '@/lib/problem-detail'
import { pageParam } from '@/lib/query'
import { formatDate } from '@/lib/format'
import {
  couponDiscountLabel,
  classifyCouponIssueError,
  COUPON_ISSUE_ERROR_MESSAGE,
} from '@/lib/order-enums'
import Pagination from '@/components/Pagination.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()
const { isAuthenticated } = storeToRefs(auth)

// 동시성 라이브 데모 라우트는 개발 빌드에만 등록되므로(router/index.js), 진입 링크도 dev 에서만 노출한다.
const isDev = import.meta.env.DEV

const page = ref(null)
const loading = ref(true)
const error = ref('')
const issuingId = ref(null) // 발급 중인 쿠폰 — 카드별 버튼 로딩 가드.

const PAGE_SIZE = 12
let reqSeq = 0

function buildParams(q) {
  const params = { size: PAGE_SIZE }
  const p = pageParam(q)
  if (p) params.page = p
  return params
}

async function fetchCoupons(q, { silent = false } = {}) {
  const seq = ++reqSeq
  if (!silent) loading.value = true // 발급 후 조용한 갱신(silent)은 그리드를 스피너로 가리지 않는다.
  error.value = ''
  try {
    const res = await listCoupons(buildParams(q))
    if (seq !== reqSeq) return
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '쿠폰 목록을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

async function onIssue(coupon) {
  issuingId.value = coupon.couponId
  try {
    await issueCoupon(coupon.couponId)
    ui.notify('쿠폰이 발급되었습니다. 내 쿠폰함에서 확인하세요.', 'success')
    fetchCoupons(route.query, { silent: true }) // 잔여 수량만 조용히 갱신(그리드 깜빡임 방지).
  } catch (e) {
    const kind = classifyCouponIssueError(e)
    ui.notify(COUPON_ISSUE_ERROR_MESSAGE[kind] ?? errorMessage(e, '쿠폰 발급에 실패했습니다.'), 'error')
    if (kind === 'SOLD_OUT') fetchCoupons(route.query, { silent: true }) // 소진이면 잔여 갱신.
  } finally {
    issuingId.value = null
  }
}

watch(() => route.query, fetchCoupons, { immediate: true })
</script>

<template>
  <section class="mx-auto max-w-3xl py-8">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-2">
      <h1 class="font-display text-2xl font-bold text-vinyl-black">쿠폰</h1>
      <div class="flex items-center gap-3 text-sm">
        <RouterLink :to="{ name: 'my-coupons' }" class="font-medium text-rust-600 hover:underline">
          내 쿠폰함 →
        </RouterLink>
        <RouterLink
          v-if="isDev"
          :to="{ name: 'coupon-race-demo' }"
          class="text-vinyl-800/60 hover:text-rust-600 hover:underline"
        >
          동시성 라이브 데모 →
        </RouterLink>
      </div>
    </div>

    <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else>
      <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

      <div
        v-else-if="!page || !page.content.length"
        class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
      >
        발급 가능한 쿠폰이 없습니다.
      </div>

      <ul v-else class="grid gap-4 sm:grid-cols-2">
        <li
          v-for="c in page.content"
          :key="c.couponId"
          class="flex flex-col gap-3 rounded-lg border border-vinyl-800/15 bg-cream-50 p-4"
        >
          <div class="flex-1">
            <p class="font-display text-lg font-bold text-vinyl-black">{{ c.name }}</p>
            <p class="mt-1 text-sm font-medium text-rust-600">{{ couponDiscountLabel(c) }}</p>
            <p class="mt-2 text-xs text-vinyl-800/50">~ {{ formatDate(c.validUntil) }}까지</p>
            <p
              class="mt-0.5 text-xs"
              :class="c.remainingQuantity === 0 ? 'font-medium text-rust-600' : 'text-vinyl-800/50'"
            >
              <span v-if="c.remainingQuantity == null">수량 제한 없음</span>
              <span v-else-if="c.remainingQuantity === 0">소진됨</span>
              <span v-else>{{ c.remainingQuantity }}장 남음</span>
            </p>
          </div>

          <RouterLink
            v-if="!isAuthenticated"
            :to="{ name: 'login', query: { redirect: '/coupons' } }"
            class="rounded-full border border-vinyl-800/20 px-5 py-2 text-center text-sm font-medium text-vinyl-black transition hover:bg-cream-100"
          >
            로그인 후 발급
          </RouterLink>
          <BaseButton
            v-else
            :disabled="c.remainingQuantity === 0"
            :loading="issuingId === c.couponId"
            @click="onIssue(c)"
          >
            {{ c.remainingQuantity === 0 ? '소진됨' : '발급받기' }}
          </BaseButton>
        </li>
      </ul>

      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
