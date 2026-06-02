<script setup>
defineProps({
  type: { type: String, default: 'button' },
  variant: { type: String, default: 'primary' }, // primary | ghost
  disabled: Boolean,
  loading: Boolean,
})

const variantClass = {
  primary: 'bg-gold-500 text-vinyl-black hover:bg-gold-400',
  ghost: 'border border-vinyl-800/20 text-vinyl-black hover:bg-cream-100',
}
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    class="inline-flex items-center justify-center gap-2 rounded-full px-5 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50"
    :class="variantClass[variant] || variantClass.primary"
  >
    <span
      v-if="loading"
      class="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
    ></span>
    <slot />
  </button>
</template>
