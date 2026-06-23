<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { requestPayment, getPayment } from '@/api/payments'
import { errorMessage } from '@/lib/problem-detail'
import { formatWon } from '@/lib/format'
import { PAYMENT_METHOD_OPTIONS } from '@/lib/order-enums'
import { randomUuid } from '@/lib/uuid'
import { usePolling } from '@/composables/usePolling'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'
import TossPaymentWidget from '@/components/payment/TossPaymentWidget.vue'

const route = useRoute()
const auth = useAuthStore()
const { isAuthenticated } = storeToRefs(auth)

// 결제 모드 토글 — 'toss' 면 토스 결제위젯, 그 외(기본 mock)는 서버 비동기 + 폴링 흐름.
const isToss = import.meta.env.VITE_PAYMENT_PROVIDER === 'toss'

const orderNumber = route.params.orderNumber
// 체크아웃이 전달한 결제 예정액(표시용) — NaN 만 null 처리
const queryAmount = Number(route.query.amount)
const displayAmount = Number.isFinite(queryAmount) ? queryAmount : null

const method = ref('CARD') // 기본 결제 수단
const idempotencyKey = ref(null) // 결제 시도당 1회 생성, 재시도에도 재사용
const payment = ref(null)
const phase = ref('idle') // idle | requesting | polling | done | failed | timeout | submitted-guest
const failureKind = ref('') // 'request'(요청 실패, 재시도 가능) | 'status'(결제 FAILED 확정)
const formError = ref('')

const POLL_MS = 1500
const MAX_ATTEMPTS = 20 // 폴링 최대 횟수
const poller = usePolling()

const busy = computed(() => phase.value === 'requesting' || phase.value === 'polling')
const amount = computed(() => payment.value?.amount ?? displayAmount)

function beginPolling() {
  phase.value = 'polling'
  poller.start(pollOnce, {
    intervalMs: POLL_MS,
    maxAttempts: MAX_ATTEMPTS,
    onExhausted: () => {
      phase.value = 'timeout'
    },
  })
}

async function pollOnce() {
  const p = await getPayment(payment.value.paymentId)
  payment.value = p
  if (p.status === 'PAID') {
    phase.value = 'done'
    return true
  }
  if (p.status === 'FAILED') {
    // 종결 상태 — 재결제 불가
    failureKind.value = 'status'
    phase.value = 'failed'
    return true
  }
  return false // PENDING — 계속 폴링
}

async function pay() {
  if (busy.value) return // 더블클릭 가드
  // 멱등 키는 최초 1회만 생성, 재시도에도 동일 키 사용
  if (!idempotencyKey.value) idempotencyKey.value = randomUuid()
  formError.value = ''
  failureKind.value = ''
  phase.value = 'requesting'
  try {
    payment.value = await requestPayment(orderNumber, method.value, idempotencyKey.value) // 202 PENDING
  } catch (e) {
    // 요청 자체 실패 — 같은 키로 재시도 가능
    failureKind.value = 'request'
    phase.value = 'failed'
    formError.value = errorMessage(e, '결제 요청에 실패했습니다.')
    return
  }
  if (!isAuthenticated.value) {
    // 게스트는 폴링 불가 — 접수만 알리고 주문조회로 결과 확인
    phase.value = 'submitted-guest'
    return
  }
  beginPolling()
}
</script>

<template>
  <section class="mx-auto max-w-md py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">결제</h1>

    <div class="rounded-lg border border-vinyl-800/15 bg-cream-50 px-4 py-4 text-sm">
      <div class="flex justify-between py-1">
        <span class="text-vinyl-800/70">주문번호</span>
        <span class="font-mono text-xs text-vinyl-black">{{ orderNumber }}</span>
      </div>
      <div v-if="amount != null" class="flex justify-between border-t border-vinyl-800/10 pt-2 text-base font-bold">
        <span>결제 금액</span>
        <span class="text-rust-600">{{ formatWon(amount) }}</span>
      </div>
    </div>

    <!-- 토스 결제위젯 모드(VITE_PAYMENT_PROVIDER=toss) — checkout → 위젯 → successUrl/failUrl 서버 콜백 -->
    <TossPaymentWidget v-if="isToss" :order-number="orderNumber" :display-amount="displayAmount" />

    <!-- mock(데모) 결제 모드 — 서버 비동기 접수 + 클라 폴링 -->
    <template v-else>
    <p v-if="formError" class="mt-4 rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600" role="alert">
      {{ formError }}
    </p>

    <!-- 결제 수단 + 결제 버튼 -->
    <template v-if="phase === 'idle' || phase === 'requesting' || (phase === 'failed' && failureKind === 'request')">
      <div class="mt-6">
        <BaseSelect v-model="method" label="결제 수단" :options="PAYMENT_METHOD_OPTIONS" :disabled="busy" />
      </div>
      <BaseButton class="mt-4 w-full" :loading="busy" :disabled="busy" @click="pay">
        {{ phase === 'failed' ? '다시 시도' : '결제하기' }}
      </BaseButton>
    </template>

    <!-- 폴링 중 -->
    <div v-else-if="phase === 'polling'" class="mt-8 flex flex-col items-center gap-3 py-6">
      <BaseSpinner size="lg" />
      <p class="text-sm text-vinyl-800/70">결제를 확인하고 있습니다…</p>
    </div>

    <!-- 완료 -->
    <div v-else-if="phase === 'done'" class="mt-8 rounded-lg bg-gold-500/10 px-4 py-6 text-center">
      <p class="font-display text-lg font-bold text-vinyl-black">결제가 완료되었습니다 🎉</p>
      <p class="mt-1 text-sm text-vinyl-800/70">{{ formatWon(payment.amount) }} 결제됨</p>
      <RouterLink
        :to="{ name: 'order-detail', params: { orderNumber } }"
        class="mt-4 inline-block rounded-full bg-gold-500 px-5 py-2 text-sm font-medium text-vinyl-black hover:bg-gold-400"
      >
        주문 상세 보기
      </RouterLink>
    </div>

    <!-- 게스트 접수 -->
    <div v-else-if="phase === 'submitted-guest'" class="mt-8 rounded-lg bg-cream-100 px-4 py-6 text-center">
      <p class="font-medium text-vinyl-black">결제가 접수되었습니다.</p>
      <p class="mt-1 text-sm text-vinyl-800/70">
        잠시 후 아래 주문 조회에서 결제 완료를 확인할 수 있습니다.
      </p>
      <RouterLink
        :to="{ name: 'guest-lookup', query: { orderNumber } }"
        class="mt-4 inline-block rounded-full bg-gold-500 px-5 py-2 text-sm font-medium text-vinyl-black hover:bg-gold-400"
      >
        주문 조회하기
      </RouterLink>
    </div>

    <!-- 시간 초과 -->
    <div v-else-if="phase === 'timeout'" class="mt-8 text-center">
      <p class="text-sm text-vinyl-800/70">결제 확인이 지연되고 있습니다.</p>
      <BaseButton class="mt-4" variant="ghost" @click="beginPolling">계속 확인하기</BaseButton>
    </div>

    <!-- 결제 실패(확정) — 재주문 안내 -->
    <div v-else-if="phase === 'failed'" class="mt-8 rounded-lg bg-rust-500/10 px-4 py-6 text-center">
      <p class="font-medium text-rust-600">결제가 실패했습니다.</p>
      <p class="mt-1 text-sm text-vinyl-800/70">이 주문은 다시 결제할 수 없습니다. 장바구니에서 새로 주문해 주세요.</p>
      <div class="mt-4 flex justify-center gap-3">
        <RouterLink
          to="/catalog"
          class="inline-block rounded-full bg-gold-500 px-5 py-2 text-sm font-medium text-vinyl-black hover:bg-gold-400"
        >
          카탈로그로
        </RouterLink>
        <RouterLink
          v-if="isAuthenticated"
          :to="{ name: 'order-detail', params: { orderNumber } }"
          class="inline-block rounded-full border border-vinyl-800/20 px-5 py-2 text-sm text-vinyl-black hover:bg-cream-100"
        >
          주문 상세
        </RouterLink>
      </div>
    </div>
    </template>
  </section>
</template>
