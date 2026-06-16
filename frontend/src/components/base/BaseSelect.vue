<script setup>
// 공통 select 컴포넌트
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{ value, label, disabled? }]
  label: { type: String, default: '' },
  disabled: Boolean,
  ariaLabel: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

function onChange(e) {
  // 선택된 옵션의 원본 값(타입 보존)을 emit, selectedIndex 로 매핑
  const opt = props.options[e.target.selectedIndex]
  emit('update:modelValue', opt ? opt.value : e.target.value)
}
</script>

<template>
  <label class="block">
    <span v-if="label" class="mb-1 block text-sm font-medium text-vinyl-800">{{ label }}</span>
    <select
      :value="modelValue"
      :disabled="disabled"
      :aria-label="ariaLabel || label || undefined"
      class="w-full rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400 disabled:cursor-not-allowed disabled:opacity-50"
      @change="onChange"
    >
      <option v-for="o in options" :key="o.value" :value="o.value" :disabled="o.disabled">
        {{ o.label }}
      </option>
    </select>
  </label>
</template>
