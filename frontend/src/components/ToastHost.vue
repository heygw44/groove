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
      class="pointer-events-auto flex items-center gap-3 rounded-full py-2 pl-5 pr-3 text-sm shadow-lg"
      :class="styleByType[t.type] || styleByType.info"
      :role="t.type === 'error' ? 'alert' : 'status'"
    >
      <span>{{ t.message }}</span>
      <button
        type="button"
        class="grid h-5 w-5 shrink-0 place-items-center rounded-full opacity-70 hover:opacity-100 focus:outline-hidden focus:ring-2 focus:ring-current"
        aria-label="알림 닫기"
        @click="ui.dismiss(t.id)"
      >
        ✕
      </button>
    </div>
  </div>
</template>
