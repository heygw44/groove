<script setup>
import { computed } from 'vue'

const props = defineProps({
  size: { type: String, default: 'md' }, // sm | md | lg
})

const sizeClass = computed(
  () => ({ sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-10 w-10' })[props.size] || 'h-6 w-6',
)
</script>

<template>
  <span
    class="inline-block animate-spin rounded-full border-2 border-gold-400 border-t-transparent"
    :class="sizeClass"
    role="status"
    aria-label="로딩 중"
  ></span>
</template>
