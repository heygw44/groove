<script setup>
import { storeToRefs } from 'pinia'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const { toasts } = storeToRefs(ui)

const styleByType = {
  info: 'bg-vinyl-black text-cream-50',
  success: 'bg-gold-500 text-vinyl-black',
  error: 'bg-rust-600 text-cream-50',
}
</script>

<template>
  <div class="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4">
    <div
      v-for="t in toasts"
      :key="t.id"
      class="pointer-events-auto rounded-full px-5 py-2 text-sm shadow-lg"
      :class="styleByType[t.type] || styleByType.info"
      role="status"
      @click="ui.dismiss(t.id)"
    >
      {{ t.message }}
    </div>
  </div>
</template>
