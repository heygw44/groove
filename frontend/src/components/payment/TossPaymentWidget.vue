<script setup>
import { ref, onMounted } from 'vue'
import { loadTossPayments, ANONYMOUS } from '@tosspayments/tosspayments-sdk'
import { tossCheckout } from '@/api/payments'
import { errorMessage } from '@/lib/problem-detail'
import { formatWon } from '@/lib/format'
import { idempotencyKeyFor } from '@/lib/uuid'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

// 토스 결제위젯(@tosspayments/tosspayments-sdk). checkout 으로 받은 clientKey/orderId/amount/successUrl/failUrl 로
// 위젯을 띄우고 requestPayment 를 호출한다. 성공/실패 시 토스가 successUrl(토큰 포함)/failUrl(서버 콜백)로 리다이렉트한다.
const props = defineProps({
  orderNumber: { type: String, required: true },
})

const loading = ref(true) // checkout + 위젯 렌더 진행 중
const loadError = ref('') // 초기화 실패(설정 누락·네트워크 등)
const ready = ref(false) // 위젯 렌더 완료 → 결제 버튼 활성
const payError = ref('') // requestPayment 실패/취소 안내(재시도 가능)
const submitting = ref(false)
const amount = ref(null) // 서버 payable — checkout 응답으로 채워지며, 버튼은 ready(렌더 완료) 후에만 보인다

let widgets = null
let checkout = null // { clientKey, orderId, amount, successUrl, failUrl }

onMounted(async () => {
  try {
    // 'CARD' 는 잠정 placeholder다 — 실제 결제수단은 아래 결제위젯(renderPaymentMethods)에서 사용자가 고르며,
    // 백엔드가 confirm 응답의 실제 수단으로 Payment.method 를 보정한다(#307). 멱등 키는 주문번호로 스코프해
    // sessionStorage 에 캐시한다 — checkout 재호출(재마운트·HMR·새로고침)이 같은 키를 재사용해 동일 PENDING 을 멱등 반환받는다(#309).
    checkout = await tossCheckout(props.orderNumber, 'CARD', idempotencyKeyFor(props.orderNumber))
    if (!checkout.clientKey || !checkout.successUrl || !checkout.failUrl) {
      // 백엔드가 토스 미설정(local/docker 등 mock 프로파일) — 토스 모드를 쓸 수 없다.
      loadError.value = '토스 결제가 구성되어 있지 않습니다. 백엔드를 dev/prod 프로파일(토스 테스트 키)로 실행해 주세요.'
      return
    }
    amount.value = checkout.amount
    const tossPayments = await loadTossPayments(checkout.clientKey)
    // 데모는 비회원 결제도 허용 — ANONYMOUS customerKey 사용.
    widgets = tossPayments.widgets({ customerKey: ANONYMOUS })
    await widgets.setAmount({ currency: 'KRW', value: checkout.amount }) // 서버 payable(위변조 검증 기준)
    await Promise.all([
      widgets.renderPaymentMethods({ selector: '#toss-payment-method', variantKey: 'DEFAULT' }),
      widgets.renderAgreement({ selector: '#toss-agreement', variantKey: 'AGREEMENT' }),
    ])
    ready.value = true
  } catch (e) {
    loadError.value = errorMessage(e, '결제 위젯을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

async function pay() {
  if (!ready.value || submitting.value || !widgets) return
  payError.value = ''
  submitting.value = true
  try {
    // 성공/실패 시 토스가 successUrl/failUrl(서버 콜백)로 브라우저를 리다이렉트한다. 서버가 confirm 후 /orders/{n}?payment=... 로 보낸다.
    await widgets.requestPayment({
      orderId: checkout.orderId,
      orderName: `주문 ${props.orderNumber}`,
      successUrl: checkout.successUrl,
      failUrl: checkout.failUrl,
    })
  } catch (e) {
    // 사용자가 결제창을 닫거나(USER_CANCEL) 요청 실패 — 인라인 안내 후 재시도 가능. 토스 SDK 에러는 message 를 그대로 노출.
    payError.value = e?.message || '결제를 진행하지 못했습니다.'
    submitting.value = false
  }
}
</script>

<template>
  <div class="mt-6">
    <!-- 초기화 실패(설정 누락·네트워크) — 위젯 컨테이너를 렌더하지 않는다. -->
    <p
      v-if="loadError"
      class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
      role="alert"
    >
      {{ loadError }}
    </p>

    <template v-else>
      <!-- 로딩 스피너. 위젯 컨테이너는 렌더 타깃이라 항상 DOM 에 존재해야 하므로 v-if 로 제거하지 않는다. -->
      <div v-if="loading" class="flex flex-col items-center gap-3 py-10">
        <BaseSpinner size="lg" />
        <p class="text-sm text-vinyl-800/70">결제 수단을 불러오고 있습니다…</p>
      </div>

      <!-- 토스 위젯 컨테이너(onMounted 에서 selector 로 렌더) -->
      <div id="toss-payment-method"></div>
      <div id="toss-agreement"></div>

      <p v-if="payError" class="mt-4 rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600" role="alert">
        {{ payError }}
      </p>

      <BaseButton
        v-if="ready"
        class="mt-4 w-full"
        :loading="submitting"
        :disabled="submitting"
        @click="pay"
      >
        {{ amount != null ? `${formatWon(amount)} 결제하기` : '결제하기' }}
      </BaseButton>
    </template>
  </div>
</template>
