<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as albumsApi from '@/api/albums'
import { errorMessage } from '@/lib/problem-detail'
import { firstStr, pageParam } from '@/lib/query'
import CatalogFilters from '@/components/catalog/CatalogFilters.vue'
import AlbumGrid from '@/components/catalog/AlbumGrid.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const page = ref(null)
const loading = ref(true)
const error = ref('')

const PAGE_SIZE = 12
let reqSeq = 0 // 응답 순서 가드 — 느린 이전 요청이 최신 결과를 덮어쓰지 않도록.

// route.query → 백엔드 검색 params. 빈 값은 제외해 깔끔한 쿼리만 전달.
function buildParams(q) {
  const params = { size: PAGE_SIZE }
  const keys = ['keyword', 'genreId', 'labelId', 'artistId', 'format', 'isLimited', 'status', 'sort']
  for (const k of keys) {
    const v = firstStr(q[k])
    if (v !== '') params[k] = v
  }
  const p = pageParam(q)
  if (p) params.page = p
  return params
}

async function fetchAlbums(q) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  try {
    const res = await albumsApi.list(buildParams(q))
    if (seq !== reqSeq) return // stale 응답 폐기
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '앨범을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

// URL 쿼리 구동 — 필터·정렬·페이지 변경은 router.push 로 query 만 바꾸고, 이 watch 가 재조회한다.
watch(() => route.query, fetchAlbums, { immediate: true })
</script>

<template>
  <section>
    <h1 class="mb-1 font-display text-2xl font-bold text-vinyl-black">카탈로그</h1>
    <p class="mb-6 text-sm text-vinyl-800/70">필터와 정렬로 원하는 레코드를 찾아보세요.</p>

    <CatalogFilters />

    <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else>
      <p v-if="!loading && page" class="mb-3 text-sm text-vinyl-800/60">총 {{ page.totalElements }}장</p>
      <AlbumGrid :albums="page?.content ?? []" :loading="loading" :skeleton-count="PAGE_SIZE" />
      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
