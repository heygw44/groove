<script setup>
import { computed, ref, watch } from 'vue'
import { trackShipping } from '@/api/shippings'
import { SHIPPING_STEPS, shippingStatusLabel } from '@/lib/order-enums'
import { formatDate } from '@/lib/format'
import { usePolling } from '@/composables/usePolling'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

// 운송장 번호로 배송 상태를 조회·폴링해 진행 단계를 보여준다. 공개 엔드포인트라 회원/게스트 공용.
// trackingNumber 가 빈 값이면(배송 미생성/추적 불가) 안내만 표시하고 폴링하지 않는다.
const props = defineProps({ trackingNumber: { type: String, default: '' } })

const shipping = ref(null)
const error = ref('')
const POLL_MS = 2000
const MAX_ATTEMPTS = 30 // ~60s. 로컬 스케줄러는 PREPARING→DELIVERED ~12s.
const TERMINAL_STATUSES = ['DELIVERED', 'CANCELLED'] // 더 이상 진행 없음 — 폴링 종료
const poller = usePolling()

async function pollOnce() {
  shipping.value = await trackShipping(props.trackingNumber)
  error.value = ''
  return TERMINAL_STATUSES.includes(shipping.value.status) // 종착(완료·취소) — 폴링 종료
}

function start() {
  poller.stop()
  shipping.value = null
  error.value = ''
  if (props.trackingNumber) {
    poller.start(pollOnce, {
      intervalMs: POLL_MS,
      maxAttempts: MAX_ATTEMPTS,
      immediate: true,
      onExhausted: () => {
        // 한도까지 한 번도 못 받았으면(배송 생성 직후 404 지속 등) 에러 표시.
        if (!shipping.value) error.value = '배송 정보를 불러오지 못했습니다.'
      },
    })
  }
}

watch(() => props.trackingNumber, start, { immediate: true })

// 발송 전 취소·환불된 배송은 진행 단계 바 대신 취소 안내를 보여준다 (#233).
const isCancelled = computed(() => shipping.value?.status === 'CANCELLED')
const reached = (idx) => SHIPPING_STEPS.indexOf(shipping.value?.status) >= idx
const passed = (idx) => SHIPPING_STEPS.indexOf(shipping.value?.status) > idx
</script>

<template>
  <div
    v-if="!trackingNumber"
    class="rounded-lg bg-cream-100 px-4 py-4 text-sm text-vinyl-800/60"
  >
    배송 추적 정보가 아직 없습니다.
  </div>

  <div v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
    {{ error }}
  </div>

  <div v-else-if="!shipping" class="flex justify-center py-6"><BaseSpinner /></div>

  <div
    v-else-if="isCancelled"
    class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
  >
    {{ shippingStatusLabel(shipping.status) }}되었습니다.
  </div>

  <div v-else class="rounded-lg border border-vinyl-800/15 bg-cream-50 px-4 py-4">
    <ol class="flex items-center">
      <li v-for="(st, idx) in SHIPPING_STEPS" :key="st" class="flex flex-1 items-center last:flex-none">
        <div class="flex flex-col items-center">
          <span
            class="flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold"
            :class="reached(idx) ? 'bg-gold-500 text-vinyl-black' : 'bg-vinyl-800/10 text-vinyl-800/40'"
          >
            {{ idx + 1 }}
          </span>
          <span class="mt-1 text-xs" :class="reached(idx) ? 'text-vinyl-black' : 'text-vinyl-800/40'">
            {{ shippingStatusLabel(st) }}
          </span>
        </div>
        <span
          v-if="idx < SHIPPING_STEPS.length - 1"
          class="mx-1 h-0.5 flex-1"
          :class="passed(idx) ? 'bg-gold-500' : 'bg-vinyl-800/10'"
        ></span>
      </li>
    </ol>

    <dl class="mt-4 space-y-1 text-xs text-vinyl-800/70">
      <div class="flex justify-between gap-2">
        <dt>운송장번호</dt>
        <dd class="truncate font-mono">{{ shipping.trackingNumber }}</dd>
      </div>
      <div v-if="shipping.shippedAt" class="flex justify-between">
        <dt>발송</dt>
        <dd>{{ formatDate(shipping.shippedAt) }}</dd>
      </div>
      <div v-if="shipping.deliveredAt" class="flex justify-between">
        <dt>배송 완료</dt>
        <dd>{{ formatDate(shipping.deliveredAt) }}</dd>
      </div>
    </dl>
  </div>
</template>
