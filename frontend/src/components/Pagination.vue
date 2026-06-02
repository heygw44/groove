<script setup>
import { useRouteQuery } from '@/composables/useRouteQuery'
import { usePagination } from '@/composables/usePagination'

const props = defineProps({
  page: { type: Object, required: true }, // PageResponse
})

const { patchQuery } = useRouteQuery()
const { current, isFirst, isLast, hasPages, pages, totalPages } = usePagination(() => props.page)

// URL 쿼리 구동 — page 만 갱신하고 나머지 필터 쿼리는 patchQuery 가 보존한다. 0 페이지는 키 제거.
function goTo(p) {
  if (p < 0 || p > totalPages.value - 1 || p === current.value) return
  patchQuery({ page: p === 0 ? undefined : p })
}

const btn =
  'min-w-9 rounded-md px-3 py-1.5 text-sm transition disabled:cursor-not-allowed disabled:opacity-40'
</script>

<template>
  <nav v-if="hasPages" class="mt-8 flex items-center justify-center gap-1" aria-label="페이지 이동">
    <button type="button" :class="btn" class="hover:bg-cream-100" :disabled="isFirst" @click="goTo(current - 1)">
      이전
    </button>
    <button
      v-for="p in pages"
      :key="p"
      type="button"
      :class="[btn, p === current ? 'bg-vinyl-black font-semibold text-cream-50' : 'hover:bg-cream-100']"
      :aria-current="p === current ? 'page' : undefined"
      @click="goTo(p)"
    >
      {{ p + 1 }}
    </button>
    <button type="button" :class="btn" class="hover:bg-cream-100" :disabled="isLast" @click="goTo(current + 1)">
      다음
    </button>
  </nav>
</template>
