<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import * as taxonomyApi from '@/api/taxonomy'
import * as artistsApi from '@/api/artists'
import { useRouteQuery } from '@/composables/useRouteQuery'
import { firstStr } from '@/lib/query'
import { FORMAT_OPTIONS, SORT_OPTIONS } from '@/lib/enums'

const { route, patchQuery } = useRouteQuery()

// 필터 키 — 정렬/페이지 같은 비필터 쿼리와 구분(초기화·활성 판정에 사용).
const FILTER_KEYS = ['keyword', 'genreId', 'labelId', 'artistId', 'format', 'isLimited', 'status']

const genres = ref([])
const labels = ref([])
const artists = ref([])

// 옵션 데이터는 거의 불변이라 모듈 레벨 캐시로 1회만 받아 카탈로그 재진입 시 재사용한다.
let optionsCache = null
async function loadOptions() {
  if (!optionsCache) {
    const [g, l, a] = await Promise.all([
      taxonomyApi.genres(),
      taxonomyApi.labels(),
      // 아티스트 옵션은 백엔드 max-page-size(=100)까지. 데모 규모엔 충분.
      artistsApi.list({ size: 100, sort: 'name,asc' }),
    ])
    optionsCache = { genres: g, labels: l, artists: a.content }
  }
  return optionsCache
}

onMounted(async () => {
  try {
    const opts = await loadOptions()
    genres.value = opts.genres
    labels.value = opts.labels
    artists.value = opts.artists
  } catch {
    // 옵션 로드 실패는 무시 — 필터 없이도 목록 탐색 가능.
  }
})

// 단일 필터 키 변경 → page 초기화하며 push(빈 값은 patchQuery 가 키 제거).
function setParam(key, value) {
  patchQuery({ [key]: value, page: undefined })
}

// select 양방향 바인딩 헬퍼 — getter 는 현재 쿼리값(문자열), setter 는 push.
function model(key) {
  return computed({
    get: () => firstStr(route.query[key]),
    set: (v) => setParam(key, v),
  })
}

const genreId = model('genreId')
const labelId = model('labelId')
const artistId = model('artistId')
const format = model('format')
const status = model('status')

// 정렬 기본값(createdAt,desc)은 URL 에 노출하지 않아 깔끔하게 유지.
const sort = computed({
  get: () => firstStr(route.query.sort) || 'createdAt,desc',
  set: (v) => setParam('sort', v === 'createdAt,desc' ? undefined : v),
})

const isLimited = computed({
  get: () => firstStr(route.query.isLimited) === 'true',
  set: (v) => setParam('isLimited', v ? 'true' : undefined),
})

// keyword 는 입력 중 history 오염을 막기 위해 로컬 ref → 제출(Enter) 시 적용.
const keyword = ref(firstStr(route.query.keyword))
watch(
  () => route.query.keyword,
  (v) => {
    keyword.value = firstStr(v)
  },
)
function applyKeyword() {
  setParam('keyword', keyword.value.trim())
}

// 활성 '필터'가 있을 때만 초기화 노출(정렬·페이지만으로는 노출 안 함).
const hasActiveFilter = computed(() => FILTER_KEYS.some((k) => firstStr(route.query[k]) !== ''))
// 초기화는 필터 키와 page 만 제거하고 정렬은 보존한다.
function reset() {
  keyword.value = ''
  const cleared = Object.fromEntries(FILTER_KEYS.map((k) => [k, undefined]))
  patchQuery({ ...cleared, page: undefined })
}

const fieldClass =
  'rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400'
</script>

<template>
  <div class="mb-6 space-y-3 rounded-xl border border-vinyl-800/10 bg-cream-100/60 p-4">
    <!-- 키워드 검색 -->
    <form class="flex gap-2" role="search" @submit.prevent="applyKeyword">
      <input
        v-model="keyword"
        type="search"
        placeholder="앨범 제목·아티스트 검색"
        aria-label="검색어"
        :class="fieldClass"
        class="flex-1"
      />
      <button
        type="submit"
        class="shrink-0 rounded-lg bg-gold-500 px-4 py-2 text-sm font-medium text-vinyl-black hover:bg-gold-400"
      >
        검색
      </button>
    </form>

    <!-- 필터 + 정렬 -->
    <div class="flex flex-wrap items-center gap-2">
      <select v-model="genreId" :class="fieldClass" aria-label="장르">
        <option value="">전체 장르</option>
        <option v-for="g in genres" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
      </select>

      <select v-model="artistId" :class="fieldClass" aria-label="아티스트">
        <option value="">전체 아티스트</option>
        <option v-for="a in artists" :key="a.id" :value="String(a.id)">{{ a.name }}</option>
      </select>

      <select v-model="labelId" :class="fieldClass" aria-label="레이블">
        <option value="">전체 레이블</option>
        <option v-for="l in labels" :key="l.id" :value="String(l.id)">{{ l.name }}</option>
      </select>

      <select v-model="format" :class="fieldClass" aria-label="포맷">
        <option value="">전체 포맷</option>
        <option v-for="f in FORMAT_OPTIONS" :key="f.value" :value="f.value">{{ f.label }}</option>
      </select>

      <select v-model="status" :class="fieldClass" aria-label="판매 상태">
        <option value="">판매중</option>
        <option value="SOLD_OUT">품절</option>
      </select>

      <label class="flex items-center gap-1.5 text-sm text-vinyl-800">
        <input v-model="isLimited" type="checkbox" class="accent-gold-500" />
        한정반만
      </label>

      <select v-model="sort" :class="fieldClass" class="ml-auto" aria-label="정렬">
        <option v-for="s in SORT_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
      </select>

      <button
        v-if="hasActiveFilter"
        type="button"
        class="rounded-lg px-3 py-2 text-sm text-rust-600 hover:underline"
        @click="reset"
      >
        초기화
      </button>
    </div>
  </div>
</template>
