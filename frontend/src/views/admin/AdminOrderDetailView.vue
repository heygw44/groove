<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { getOrder, changeOrderStatus, refundOrder } from '@/api/admin'
import { formatWon, formatDate } from '@/lib/format'
import { orderStatusLabel, isReviewableStatus } from '@/lib/order-enums'
import { nextForceableStatus, isRefundableStatus, adminErrorMessage } from '@/lib/admin-enums'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const ui = useUiStore()
// 라우트 파라미터를 반응형으로 읽는다 — 같은 인스턴스가 다른 주문 URL 로 재사용돼도 stale 한 주문번호로
// 잘못된 주문을 조회/전환/환불하지 않게 한다(아래 watch 가 재로드).
const orderNumber = computed(() => route.params.orderNumber)

const order = ref(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const reason = ref('관리자 처리')

const next = computed(() => (order.value ? nextForceableStatus(order.value.status) : null))
const canRefund = computed(() => order.value && isRefundableStatus(order.value.status))

async function load() {
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrder(orderNumber.value)
  } catch (e) {
    error.value = adminErrorMessage(e, '주문을 불러오지 못했습니다.')
    order.value = null
  } finally {
    loading.value = false
  }
}

async function onAdvance() {
  if (!reason.value.trim()) {
    ui.notify('전환 사유를 입력해 주세요.', 'error')
    return
  }
  busy.value = true
  try {
    order.value = await changeOrderStatus(orderNumber.value, { target: next.value, reason: reason.value.trim() })
    ui.notify(`상태를 '${orderStatusLabel(order.value.status)}'(으)로 전환했습니다.`, 'success')
  } catch (e) {
    ui.notify(adminErrorMessage(e, '상태 전환에 실패했습니다.'), 'error')
  } finally {
    busy.value = false
  }
}

async function onRefund() {
  if (!window.confirm('이 주문을 환불(취소) 처리할까요?')) return
  busy.value = true
  try {
    const res = await refundOrder(orderNumber.value, { reason: '관리자 환불' })
    ui.notify(res.alreadyRefunded ? '이미 환불된 주문입니다.' : '환불 처리했습니다.', 'success')
    await load()
  } catch (e) {
    ui.notify(adminErrorMessage(e, '환불에 실패했습니다.'), 'error')
  } finally {
    busy.value = false
  }
}

watch(() => route.params.orderNumber, load, { immediate: true })
</script>

<template>
  <section class="max-w-3xl">
    <div class="mb-6 flex items-center gap-2 text-sm">
      <RouterLink :to="{ name: 'admin-orders' }" class="text-vinyl-800/60 hover:text-rust-600">주문 관리</RouterLink>
      <span class="text-vinyl-800/40">/</span>
      <span class="font-mono font-medium text-vinyl-black">{{ orderNumber }}</span>
    </div>

    <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>
    <p v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else-if="order">
      <!-- 상태 + 전환/환불 패널 -->
      <div class="mb-6 rounded-lg border border-vinyl-800/15 bg-cream-50 p-5">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-xs text-vinyl-800/60">현재 상태</p>
            <p class="mt-1 text-xl font-bold text-vinyl-black">{{ orderStatusLabel(order.status) }}</p>
          </div>
          <span class="text-sm text-vinyl-800/60">{{ order.memberId ? `회원 #${order.memberId}` : `게스트 · ${order.guestEmail}` }}</span>
        </div>

        <p
          v-if="isReviewableStatus(order.status)"
          class="mt-3 rounded bg-gold-400/15 px-3 py-2 text-xs text-vinyl-800"
        >
          배송이 완료되어 구매자가 이 주문의 앨범에 리뷰를 작성할 수 있습니다.
        </p>

        <div v-if="next" class="mt-4 border-t border-vinyl-800/10 pt-4">
          <label class="block">
            <span class="mb-1 block text-sm font-medium text-vinyl-800">전환 사유</span>
            <input
              v-model="reason"
              type="text"
              maxlength="500"
              class="w-full rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm focus:outline-hidden focus:ring-2 focus:ring-gold-400"
            />
          </label>
          <BaseButton class="mt-3" :loading="busy" @click="onAdvance">
            {{ orderStatusLabel(order.status) }} → {{ orderStatusLabel(next) }} 전환
          </BaseButton>
        </div>
        <p v-else class="mt-4 border-t border-vinyl-800/10 pt-4 text-sm text-vinyl-800/50">
          더 이상 전환할 수 있는 단계가 없습니다.
        </p>

        <button
          v-if="canRefund"
          type="button"
          class="mt-3 text-sm font-medium text-rust-600 hover:underline disabled:opacity-40"
          :disabled="busy"
          @click="onRefund"
        >
          환불(취소) 처리
        </button>
      </div>

      <!-- 상품 -->
      <div class="mb-6 rounded-lg border border-vinyl-800/15 bg-cream-50 p-5">
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">주문 상품</h2>
        <ul class="divide-y divide-vinyl-800/10">
          <li v-for="it in order.items" :key="it.albumId" class="flex items-center justify-between py-2 text-sm">
            <span class="text-vinyl-black">{{ it.albumTitle }} <span class="text-vinyl-800/50">× {{ it.quantity }}</span></span>
            <span class="font-medium text-vinyl-black">{{ formatWon(it.subtotal) }}</span>
          </li>
        </ul>
        <div class="mt-3 flex items-center justify-between border-t border-vinyl-800/10 pt-3 text-sm">
          <span class="text-vinyl-800/60">합계</span>
          <span class="text-lg font-bold text-vinyl-black">{{ formatWon(order.totalAmount) }}</span>
        </div>
      </div>

      <!-- 배송지 -->
      <div class="rounded-lg border border-vinyl-800/15 bg-cream-50 p-5 text-sm">
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">배송지</h2>
        <dl class="space-y-1 text-vinyl-800/80">
          <div class="flex gap-2"><dt class="w-20 shrink-0 text-vinyl-800/50">받는 분</dt><dd>{{ order.shipping.recipientName }} ({{ order.shipping.recipientPhone }})</dd></div>
          <div class="flex gap-2"><dt class="w-20 shrink-0 text-vinyl-800/50">주소</dt><dd>[{{ order.shipping.zipCode }}] {{ order.shipping.address }} {{ order.shipping.addressDetail }}</dd></div>
          <div class="flex gap-2"><dt class="w-20 shrink-0 text-vinyl-800/50">주문일</dt><dd>{{ formatDate(order.createdAt) }}</dd></div>
          <div v-if="order.cancelledReason" class="flex gap-2"><dt class="w-20 shrink-0 text-vinyl-800/50">취소 사유</dt><dd>{{ order.cancelledReason }}</dd></div>
        </dl>
      </div>
    </template>
  </section>
</template>
