<script setup>
// 공통 select — BaseButton/BaseInput 과 동일한 스타일 토큰을 한 곳으로 모은다(쿠폰·결제수단·주문상태 필터 공유).
defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{ value, label, disabled? }]
  label: { type: String, default: '' },
  disabled: Boolean,
  ariaLabel: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <label class="block">
    <span v-if="label" class="mb-1 block text-sm font-medium text-vinyl-800">{{ label }}</span>
    <select
      :value="modelValue"
      :disabled="disabled"
      :aria-label="ariaLabel || label || undefined"
      class="w-full rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400 disabled:cursor-not-allowed disabled:opacity-50"
      @change="emit('update:modelValue', $event.target.value)"
    >
      <option v-for="o in options" :key="o.value" :value="o.value" :disabled="o.disabled">
        {{ o.label }}
      </option>
    </select>
  </label>
</template>
