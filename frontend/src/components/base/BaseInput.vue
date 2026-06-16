<script setup>
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' },
  label: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  error: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

function onInput(e) {
  if (props.type === 'number') {
    // 숫자 타입은 number 를 emit, NaN 은 '' 로 보정
    const num = e.target.valueAsNumber
    emit('update:modelValue', Number.isNaN(num) ? '' : num)
  } else {
    emit('update:modelValue', e.target.value)
  }
}
</script>

<template>
  <label class="block">
    <span v-if="label" class="mb-1 block text-sm font-medium text-vinyl-800">{{ label }}</span>
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      class="w-full rounded-lg border bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400"
      :class="error ? 'border-rust-500' : 'border-vinyl-800/20'"
      @input="onInput"
    />
    <span v-if="error" class="mt-1 block text-xs text-rust-600">{{ error }}</span>
  </label>
</template>
