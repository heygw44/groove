<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import { createCoupon } from '@/api/admin'
import { ApiError } from '@/lib/problem-detail'
import { COUPON_DISCOUNT_TYPE_OPTIONS } from '@/lib/admin-enums'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const router = useRouter()
const ui = useUiStore()

// datetime-local 입력 문자열("YYYY-MM-DDTHH:mm") 포맷터
function toLocalInput(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const now = new Date()

const form = reactive({
  name: '',
  discountType: 'FIXED_AMOUNT',
  discountValue: '',
  maxDiscountAmount: '', // PERCENTAGE 일 때만 의미, 빈값 = 상한 없음
  minOrderAmount: 0,
  totalQuantity: '', // 빈값 = 무제한
  perMemberLimit: 1,
  validFrom: toLocalInput(now),
  validUntil: toLocalInput(new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000)),
})

const { errors, formError, submitting, submit, clearError } = useForm(async () => {
  // 필수값 클라이언트 검증 — violations 를 만들어 필드 에러로 표시
  const v = []
  if (!form.name.trim()) v.push({ field: 'name', message: '쿠폰 이름을 입력해 주세요.' })
  if (form.discountValue === '') v.push({ field: 'discountValue', message: '할인값을 입력해 주세요.' })
  if (form.perMemberLimit === '') v.push({ field: 'perMemberLimit', message: '회원당 한도를 입력해 주세요.' })
  const fromMs = Date.parse(form.validFrom)
  const untilMs = Date.parse(form.validUntil)
  if (Number.isNaN(fromMs)) v.push({ field: 'validFrom', message: '유효 시작 일시를 입력해 주세요.' })
  if (Number.isNaN(untilMs)) v.push({ field: 'validUntil', message: '유효 종료 일시를 입력해 주세요.' })
  if (!Number.isNaN(fromMs) && !Number.isNaN(untilMs) && untilMs <= fromMs) {
    v.push({ field: 'validUntil', message: '종료 일시는 시작 일시보다 이후여야 합니다.' })
  }
  if (v.length) throw new ApiError({ status: 400, violations: v })

  const body = {
    name: form.name,
    discountType: form.discountType,
    discountValue: form.discountValue,
    maxDiscountAmount:
      form.discountType === 'PERCENTAGE' && form.maxDiscountAmount !== '' ? form.maxDiscountAmount : null,
    minOrderAmount: form.minOrderAmount === '' ? 0 : form.minOrderAmount,
    totalQuantity: form.totalQuantity === '' ? null : form.totalQuantity,
    perMemberLimit: form.perMemberLimit,
    validFrom: new Date(fromMs).toISOString(),
    validUntil: new Date(untilMs).toISOString(),
  }
  await createCoupon(body)
})

async function handleSubmit() {
  const ok = await submit()
  if (ok) {
    ui.notify('쿠폰을 생성했습니다.', 'success')
    router.push({ name: 'admin-coupons' })
  }
}
</script>

<template>
  <section class="max-w-2xl">
    <div class="mb-6 flex items-center gap-2 text-sm">
      <RouterLink :to="{ name: 'admin-coupons' }" class="text-vinyl-800/60 hover:text-rust-600">쿠폰 관리</RouterLink>
      <span class="text-vinyl-800/40">/</span>
      <span class="font-medium text-vinyl-black">새 쿠폰</span>
    </div>

    <form class="space-y-4" @submit.prevent="handleSubmit">
      <p v-if="formError" role="alert" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
        {{ formError }}
      </p>

      <BaseInput
        v-model="form.name"
        label="쿠폰 이름"
        :error="errors.name"
        @update:model-value="clearError('name')"
      />

      <div class="grid grid-cols-2 gap-4">
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">할인 타입</span>
          <BaseSelect v-model="form.discountType" :options="COUPON_DISCOUNT_TYPE_OPTIONS" aria-label="할인 타입" />
        </label>
        <BaseInput
          v-model="form.discountValue"
          type="number"
          :label="form.discountType === 'PERCENTAGE' ? '할인율 (%)' : '할인액 (원)'"
          :error="errors.discountValue"
          @update:model-value="clearError('discountValue')"
        />
        <BaseInput
          v-if="form.discountType === 'PERCENTAGE'"
          v-model="form.maxDiscountAmount"
          type="number"
          label="최대 할인액 (원, 선택)"
          :error="errors.maxDiscountAmount"
          @update:model-value="clearError('maxDiscountAmount')"
        />
        <BaseInput
          v-model="form.minOrderAmount"
          type="number"
          label="최소 주문액 (원)"
          :error="errors.minOrderAmount"
          @update:model-value="clearError('minOrderAmount')"
        />
        <BaseInput
          v-model="form.totalQuantity"
          type="number"
          label="총 수량 (빈값=무제한)"
          :error="errors.totalQuantity"
          @update:model-value="clearError('totalQuantity')"
        />
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">회원당 한도</span>
          <input
            :value="form.perMemberLimit"
            type="number"
            min="1"
            max="1"
            readonly
            aria-label="회원당 한도"
            class="w-full cursor-not-allowed rounded-lg border border-vinyl-800/20 bg-cream-100 px-3 py-2 text-sm text-vinyl-800/70 focus:outline-hidden"
          />
          <span class="mt-1 block text-xs text-vinyl-800/50">현재 회원당 1장만 지원합니다.</span>
        </label>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">유효 시작</span>
          <input
            v-model="form.validFrom"
            type="datetime-local"
            class="w-full rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400"
          />
          <span v-if="errors.validFrom" class="mt-1 block text-xs text-rust-600">{{ errors.validFrom }}</span>
        </label>
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">유효 종료</span>
          <input
            v-model="form.validUntil"
            type="datetime-local"
            class="w-full rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400"
          />
          <span v-if="errors.validUntil" class="mt-1 block text-xs text-rust-600">{{ errors.validUntil }}</span>
        </label>
      </div>

      <div class="flex gap-3 pt-2">
        <BaseButton type="submit" :loading="submitting">쿠폰 생성</BaseButton>
        <RouterLink
          :to="{ name: 'admin-coupons' }"
          class="inline-flex items-center rounded-full border border-vinyl-800/20 px-5 py-2 text-sm font-medium text-vinyl-black transition hover:bg-cream-100"
        >
          취소
        </RouterLink>
      </div>
    </form>
  </section>
</template>
