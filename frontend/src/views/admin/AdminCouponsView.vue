<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useRouteQuery } from '@/composables/useRouteQuery'
import { listCoupons, changeCouponStatus, grantCoupon } from '@/api/admin'
import { firstStr, pageParam } from '@/lib/query'
import { formatDate } from '@/lib/format'
import { couponDiscountLabel } from '@/lib/order-enums'
import {
  ADMIN_COUPON_STATUS_FILTER_OPTIONS,
  couponAdminStatusLabel,
  couponStatusTransitions,
  adminErrorMessage,
} from '@/lib/admin-enums'
import Pagination from '@/components/Pagination.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const ui = useUiStore()
const { patchQuery } = useRouteQuery()

const page = ref(null)
const loading = ref(true)
const error = ref('')
const busyId = ref(null)

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

async function fetchCoupons(q, { silent = false } = {}) {
  const seq = ++reqSeq
  if (!silent) loading.value = true
  error.value = ''
  try {
    const res = await listCoupons(buildParams(q))
    if (seq !== reqSeq) return
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = adminErrorMessage(e, '쿠폰 목록을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

function onStatusChange(value) {
  patchQuery({ status: value, page: undefined })
}

async function onChangeStatus(coupon, target) {
  busyId.value = coupon.couponId
  try {
    await changeCouponStatus(coupon.couponId, target)
    ui.notify(`'${coupon.name}' 상태를 ${couponAdminStatusLabel(target)}(으)로 변경했습니다.`, 'success')
    fetchCoupons(route.query, { silent: true })
  } catch (e) {
    ui.notify(adminErrorMessage(e, '상태 변경에 실패했습니다.'), 'error')
  } finally {
    busyId.value = null
  }
}

async function onGrant(coupon) {
  const input = window.prompt(`'${coupon.name}' 쿠폰을 지급할 회원 ID를 입력하세요.`)
  if (input == null) return
  const memberId = Number(input)
  if (!Number.isInteger(memberId) || memberId <= 0) {
    ui.notify('유효한 회원 ID를 입력해 주세요.', 'error')
    return
  }
  busyId.value = coupon.couponId
  try {
    await grantCoupon(coupon.couponId, memberId)
    // 직접지급은 목록 갱신 없이 토스트만
    ui.notify(`회원 #${memberId}에게 쿠폰을 지급했습니다.`, 'success')
  } catch (e) {
    ui.notify(adminErrorMessage(e, '쿠폰 지급에 실패했습니다.'), 'error')
  } finally {
    busyId.value = null
  }
}

// 쿼리 변경 시 쿠폰 목록 재조회
watch(() => route.query, (q) => fetchCoupons(q), { immediate: true })
</script>

<template>
  <section>
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 class="font-display text-2xl font-bold text-vinyl-black">쿠폰 관리</h1>
      <RouterLink
        :to="{ name: 'admin-coupon-new' }"
        class="rounded-full bg-gold-500 px-5 py-2 text-sm font-medium text-vinyl-black transition hover:bg-gold-400"
      >
        + 새 쿠폰
      </RouterLink>
    </div>

    <div class="mb-4 max-w-xs">
      <BaseSelect
        :model-value="firstStr(route.query.status)"
        :options="ADMIN_COUPON_STATUS_FILTER_OPTIONS"
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
        쿠폰이 없습니다.
      </div>

      <div v-else class="overflow-x-auto rounded-lg border border-vinyl-800/15 bg-cream-50">
        <table class="w-full min-w-[48rem] text-sm">
          <thead class="border-b border-vinyl-800/15 text-left text-xs text-vinyl-800/60">
            <tr>
              <th class="px-4 py-3 font-medium">쿠폰</th>
              <th class="px-4 py-3 font-medium">할인</th>
              <th class="px-4 py-3 font-medium">발급</th>
              <th class="px-4 py-3 font-medium">기간</th>
              <th class="px-4 py-3 font-medium">상태</th>
              <th class="px-4 py-3 text-right font-medium">작업</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-vinyl-800/10">
            <tr v-for="c in page.content" :key="c.couponId">
              <td class="px-4 py-3">
                <p class="font-medium text-vinyl-black">{{ c.name }}</p>
                <p class="text-xs text-vinyl-800/50">회원당 {{ c.perMemberLimit }}장</p>
              </td>
              <td class="px-4 py-3 text-vinyl-800/70">{{ couponDiscountLabel(c) }}</td>
              <td class="px-4 py-3 text-vinyl-800/70">
                {{ c.issuedCount }}<span v-if="c.totalQuantity != null"> / {{ c.totalQuantity }}</span>
                <span v-else class="text-vinyl-800/40"> (무제한)</span>
              </td>
              <td class="px-4 py-3 text-xs text-vinyl-800/50">~ {{ formatDate(c.validUntil) }}</td>
              <td class="px-4 py-3">
                <span
                  class="rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="
                    c.status === 'ACTIVE'
                      ? 'bg-gold-400/20 text-vinyl-black'
                      : c.status === 'SUSPENDED'
                        ? 'bg-rust-500/10 text-rust-600'
                        : 'bg-vinyl-800/10 text-vinyl-800/60'
                  "
                >
                  {{ couponAdminStatusLabel(c.status) }}
                </span>
              </td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap items-center justify-end gap-1.5 text-xs">
                  <button
                    v-for="t in couponStatusTransitions(c.status)"
                    :key="t"
                    type="button"
                    class="rounded border border-vinyl-800/20 px-2 py-1 transition hover:bg-cream-100 disabled:opacity-40"
                    :disabled="busyId === c.couponId"
                    @click="onChangeStatus(c, t)"
                  >
                    {{ couponAdminStatusLabel(t) }}
                  </button>
                  <button
                    type="button"
                    class="rounded border border-vinyl-800/20 px-2 py-1 transition hover:bg-cream-100 disabled:opacity-40"
                    :disabled="busyId === c.couponId"
                    @click="onGrant(c)"
                  >
                    지급
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
