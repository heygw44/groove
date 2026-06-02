<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as artistsApi from '@/api/artists'
import { errorMessage } from '@/lib/problem-detail'
import { firstStr, pageParam } from '@/lib/query'
import AlbumGrid from '@/components/catalog/AlbumGrid.vue'
import Pagination from '@/components/Pagination.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const artist = ref(null)
const page = ref(null)
const loading = ref(true)
const albumsLoading = ref(true)
const error = ref('')

const PAGE_SIZE = 12
let albumSeq = 0 // 앨범 조회 응답 순서 가드.

function buildParams(q) {
  const params = { size: PAGE_SIZE }
  const p = pageParam(q)
  if (p) params.page = p
  const sort = firstStr(q.sort)
  if (sort) params.sort = sort
  return params
}

async function fetchArtist(id) {
  loading.value = true
  error.value = ''
  artist.value = null
  try {
    artist.value = await artistsApi.detail(id)
  } catch (e) {
    error.value = errorMessage(e, '아티스트 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function fetchAlbums(id, q) {
  const seq = ++albumSeq
  albumsLoading.value = true
  try {
    const res = await artistsApi.albums(id, buildParams(q))
    if (seq !== albumSeq) return // stale 응답 폐기
    page.value = res
  } catch {
    if (seq !== albumSeq) return
    page.value = null
  } finally {
    if (seq === albumSeq) albumsLoading.value = false
  }
}

// 단일 watcher 로 묶어, id 가 바뀐 경우에만 fetchArtist 하고 앨범은 매 변경마다 1회만 조회한다
// (params.id watch + query watch 를 따로 두면 아티스트 전환 시 fetchAlbums 가 중복 호출됨).
watch(
  () => [route.params.id, route.query],
  (curr, prev) => {
    const id = curr[0]
    if (id !== prev?.[0]) fetchArtist(id)
    fetchAlbums(id, route.query)
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

  <p v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
    {{ error }}
  </p>

  <section v-else-if="artist">
    <header class="mb-8">
      <h1 class="font-display text-3xl font-bold text-vinyl-black">{{ artist.name }}</h1>
      <p v-if="artist.description" class="mt-2 max-w-2xl text-sm leading-relaxed text-vinyl-800/70">
        {{ artist.description }}
      </p>
    </header>

    <h2 class="mb-4 font-display text-lg font-bold text-vinyl-black">디스코그래피</h2>
    <AlbumGrid
      :albums="page?.content ?? []"
      :loading="albumsLoading"
      :skeleton-count="PAGE_SIZE"
      empty-text="등록된 앨범이 없습니다."
    />
    <Pagination v-if="page" :page="page" />
  </section>
</template>
