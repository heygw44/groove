<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const ui = useUiStore()
const keyword = ref('')

function onSubmit() {
  const q = keyword.value.trim()
  ui.setSearchKeyword(q)
  // 카탈로그 검색 라우트는 #114 에서 구현. 현재는 홈으로 이동(골격).
  router.push({ path: '/', query: q ? { q } : {} })
}
</script>

<template>
  <form class="flex items-center" role="search" @submit.prevent="onSubmit">
    <input
      v-model="keyword"
      type="search"
      placeholder="앨범, 아티스트 검색"
      aria-label="검색어"
      class="w-full rounded-l-full border border-vinyl-800/20 bg-cream-50 px-4 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400"
    />
    <button
      type="submit"
      class="shrink-0 rounded-r-full bg-gold-500 px-4 py-2 text-sm font-medium text-vinyl-black hover:bg-gold-400"
    >
      검색
    </button>
  </form>
</template>
