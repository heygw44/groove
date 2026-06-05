<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const keyword = ref('')

function onSubmit() {
  const q = keyword.value.trim()
  // 이미 카탈로그면 적용된 필터/정렬을 보존하고 keyword 만 갱신(페이지는 초기화).
  // 다른 화면에서 검색하면 새 카탈로그 조회로 시작한다.
  const base = route.name === 'catalog' ? route.query : {}
  router.push({ name: 'catalog', query: { ...base, keyword: q || undefined, page: undefined } })
}
</script>

<template>
  <form class="flex items-stretch" role="search" @submit.prevent="onSubmit">
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
