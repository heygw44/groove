<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRouteQuery } from '@/composables/useRouteQuery'
import { listOrders } from '@/api/admin'
import { firstStr, pageParam } from '@/lib/query'
import { formatWon, formatDate } from '@/lib/format'
import { orderStatusLabel } from '@/lib/order-enums'
import { ADMIN_ORDER_STATUS_FILTER_OPTIONS, adminErrorMessage } from '@/lib/admin-enums'
import Pagination from '@/components/Pagination.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const router = useRouter()
const { patchQuery } = useRouteQuery()

function goDetail(orderNumber) {
  router.push({ name: 'admin-order-detail', params: { orderNumber } })
}

const page = ref(null)
const loading = ref(true)
const error = ref('')
const memberIdInput = ref(firstStr(route.query.memberId))

const PAGE_SIZE = 15
let reqSeq = 0

function buildParams(q) {
  const params = { size: PAGE_SIZE }
  const status = firstStr(q.status)
  if (status !== '') params.status = status
  const memberId = firstStr(q.memberId)
  if (memberId !== '') params.memberId = memberId
  const p = pageParam(q)
  if (p) params.page = p
  return params
}

async function fetchOrders(q) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  try {
    const res = await listOrders(buildParams(q))
    if (seq !== reqSeq) return
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = adminErrorMessage(e, '주문 목록을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

function onStatusChange(value) {
  patchQuery({ status: value, page: undefined })
}

function onMemberIdSubmit() {
  patchQuery({ memberId: memberIdInput.value === '' ? undefined : memberIdInput.value, page: undefined })
}

watch(
  () => route.query,
  (q) => {
    memberIdInput.value = firstStr(q.memberId)
    fetchOrders(q)
  },
  { immediate: true },
)
</script>

<template>
  <section>
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">주문 관리</h1>

    <div class="mb-4 flex flex-wrap items-end gap-3">
      <div class="max-w-xs flex-1">
        <BaseSelect
          :model-value="firstStr(route.query.status)"
          :options="ADMIN_ORDER_STATUS_FILTER_OPTIONS"
          aria-label="주문 상태 필터"
          @update:model-value="onStatusChange"
        />
      </div>
      <form class="flex items-end gap-2" @submit.prevent="onMemberIdSubmit">
        <input
          v-model="memberIdInput"
          type="number"
          placeholder="회원 ID"
          class="w-28 rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm focus:outline-hidden focus:ring-2 focus:ring-gold-400"
        />
        <button
          type="submit"
          class="rounded-lg border border-vinyl-800/20 px-4 py-2 text-sm font-medium transition hover:bg-cream-100"
        >
          조회
        </button>
      </form>
    </div>

    <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else>
      <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

      <div
        v-else-if="!page || !page.content.length"
        class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
      >
        주문이 없습니다.
      </div>

      <div v-else class="overflow-x-auto rounded-lg border border-vinyl-800/15 bg-cream-50">
        <table class="w-full min-w-[48rem] text-sm">
          <thead class="border-b border-vinyl-800/15 text-left text-xs text-vinyl-800/60">
            <tr>
              <th class="px-4 py-3 font-medium">주문번호</th>
              <th class="px-4 py-3 font-medium">주문자</th>
              <th class="px-4 py-3 font-medium">상품</th>
              <th class="px-4 py-3 font-medium">금액</th>
              <th class="px-4 py-3 font-medium">상태</th>
              <th class="px-4 py-3 font-medium">주문일</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-vinyl-800/10">
            <tr
              v-for="o in page.content"
              :key="o.orderNumber"
              class="cursor-pointer transition hover:bg-cream-100 focus:bg-cream-100 focus:outline-hidden focus:ring-2 focus:ring-inset focus:ring-gold-400"
              tabindex="0"
              role="button"
              :aria-label="`주문 ${o.orderNumber} 상세`"
              @click="goDetail(o.orderNumber)"
              @keydown.enter.prevent="goDetail(o.orderNumber)"
              @keydown.space.prevent="goDetail(o.orderNumber)"
            >
              <td class="px-4 py-3 font-mono text-xs text-vinyl-black">{{ o.orderNumber }}</td>
              <td class="px-4 py-3 text-vinyl-800/70">
                <span v-if="o.memberId">회원 #{{ o.memberId }}</span>
                <span v-else class="text-vinyl-800/50">게스트 · {{ o.guestEmail }}</span>
              </td>
              <td class="px-4 py-3 text-vinyl-800/70">{{ o.itemCount }}건</td>
              <td class="px-4 py-3 font-medium text-vinyl-black">{{ formatWon(o.totalAmount) }}</td>
              <td class="px-4 py-3">
                <span class="rounded-full bg-vinyl-black px-2.5 py-0.5 text-xs font-medium text-cream-50">
                  {{ orderStatusLabel(o.status) }}
                </span>
              </td>
              <td class="px-4 py-3 text-xs text-vinyl-800/50">{{ formatDate(o.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
