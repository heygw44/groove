<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useForm } from '@/composables/useForm'
import { usePaymentResultBanner } from '@/composables/usePaymentResultBanner'
import { guestLookup } from '@/api/orders'
import { isPaidStatus } from '@/lib/order-enums'
import OrderItemsCard from '@/components/order/OrderItemsCard.vue'
import ShippingTracker from '@/components/order/ShippingTracker.vue'
import PaymentResultBanner from '@/components/payment/PaymentResultBanner.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const route = useRoute()
const form = reactive({
  orderNumber: typeof route.query.orderNumber === 'string' ? route.query.orderNumber : '',
  email: '',
})
const requiredError = ref('')

// 게스트 토스 결제 콜백 결과(#308) — 라우터 가드가 order-detail 바운스 대신 ?payment= 를 보존해 이리로 보낸다.
const { paymentResult } = usePaymentResultBanner()

const order = ref(null)
const { formError, submitting, submit } = useForm(async () => {
  order.value = await guestLookup(form.orderNumber.trim(), form.email.trim())
})

const trackingNumber = computed(() => order.value?.trackingNumber || '')
const paid = computed(() => order.value && isPaidStatus(order.value.status))

async function onSubmit() {
  requiredError.value = ''
  if (!form.orderNumber.trim() || !form.email.trim()) {
    requiredError.value = '주문번호와 이메일을 모두 입력해 주세요.'
    return
  }
  await submit()
}
</script>

<template>
  <section class="mx-auto max-w-2xl py-8">
    <h1 class="mb-2 font-display text-2xl font-bold text-vinyl-black">비회원 주문 조회</h1>
    <p class="mb-6 text-sm text-vinyl-800/70">주문 시 입력한 주문번호와 이메일로 조회합니다.</p>

    <!-- 토스 결제 콜백 결과 안내(게스트) -->
    <PaymentResultBanner v-if="paymentResult" :result="paymentResult" />

    <form class="space-y-3" @submit.prevent="onSubmit">
      <p
        v-if="formError || requiredError"
        class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
        role="alert"
      >
        {{ formError || requiredError }}
      </p>
      <BaseInput v-model="form.orderNumber" label="주문번호" placeholder="ORD-XXXXXXXX-XXXXXXXXXXXX" />
      <BaseInput v-model="form.email" type="email" label="이메일" />
      <BaseButton type="submit" class="w-full" :loading="submitting">조회하기</BaseButton>
    </form>

    <div v-if="order" class="mt-10">
      <div class="mb-4 flex items-center justify-between">
        <h2 class="font-display text-lg font-bold text-vinyl-black">주문 정보</h2>
        <button type="button" class="text-sm text-vinyl-800/60 hover:text-rust-600" @click="onSubmit">
          새로고침
        </button>
      </div>
      <OrderItemsCard :order="order" />

      <div v-if="paid" class="mt-8">
        <h3 class="mb-3 font-display text-base font-bold text-vinyl-black">배송 추적</h3>
        <ShippingTracker v-if="trackingNumber" :tracking-number="trackingNumber" />
        <div v-else class="rounded-lg bg-cream-100 px-4 py-4 text-sm text-vinyl-800/60">
          배송이 준비되면 추적 정보가 표시됩니다. 위 새로고침으로 다시 확인해 주세요.
        </div>
      </div>
    </div>
  </section>
</template>
