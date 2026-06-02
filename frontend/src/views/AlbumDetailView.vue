<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as albumsApi from '@/api/albums'
import { errorMessage } from '@/lib/problem-detail'
import { formatWon, formatDate } from '@/lib/format'
import { formatLabel, statusLabel } from '@/lib/enums'
import RatingStars from '@/components/catalog/RatingStars.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const album = ref(null)
const reviews = ref([])
const reviewsError = ref(false)
const loading = ref(true)
const error = ref('')

const REVIEW_PAGE_SIZE = 20
let reqSeq = 0 // 응답 순서 가드 — 빠른 앨범 전환 시 stale 응답 폐기.

async function fetchAll(id) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  album.value = null
  reviews.value = []
  reviewsError.value = false
  try {
    const detail = await albumsApi.detail(id)
    if (seq !== reqSeq) return
    album.value = detail
    // 리뷰는 부가 정보 — 실패해도 상세는 보여주되, 빈 목록과 로딩 실패를 구분한다.
    try {
      const r = await albumsApi.reviews(id, { size: REVIEW_PAGE_SIZE })
      if (seq !== reqSeq) return
      reviews.value = r.content
    } catch {
      if (seq !== reqSeq) return
      reviewsError.value = true
    }
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '앨범 정보를 불러오지 못했습니다.')
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

// 동일 컴포넌트 재사용(다른 id 로 이동) 시에도 재조회되도록 params.id 를 watch.
watch(() => route.params.id, fetchAll, { immediate: true })
</script>

<template>
  <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

  <p v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
    {{ error }}
  </p>

  <article v-else-if="album">
    <div class="grid gap-8 md:grid-cols-2">
      <!-- 좌측 대형 커버 -->
      <div class="overflow-hidden rounded-2xl bg-vinyl-900">
        <img
          v-if="album.coverImageUrl"
          :src="album.coverImageUrl"
          :alt="album.title"
          class="aspect-square w-full object-cover"
        />
      </div>

      <!-- 우측 sticky 정보 -->
      <div class="md:sticky md:top-20 md:self-start">
        <div class="flex items-center gap-2">
          <span
            v-if="album.isLimited"
            class="rounded-full bg-gold-500 px-2 py-0.5 text-xs font-bold text-vinyl-black"
            >한정반</span
          >
          <span class="text-xs text-vinyl-800/60">{{ formatLabel(album.format) }}</span>
        </div>

        <h1 class="mt-2 font-display text-3xl font-bold text-vinyl-black">{{ album.title }}</h1>
        <RouterLink
          v-if="album.artist"
          :to="{ name: 'artist-detail', params: { id: album.artist.id } }"
          class="mt-1 inline-block text-rust-600 hover:underline"
        >
          {{ album.artist.name }}
        </RouterLink>

        <RatingStars class="mt-2" :rating="album.averageRating" :count="album.reviewCount" />

        <dl class="mt-5 space-y-1.5 text-sm text-vinyl-800">
          <div class="flex gap-2">
            <dt class="w-20 shrink-0 text-vinyl-800/50">레이블</dt>
            <dd>{{ album.label?.name ?? '-' }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-20 shrink-0 text-vinyl-800/50">장르</dt>
            <dd>{{ album.genre?.name }}</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-20 shrink-0 text-vinyl-800/50">발매</dt>
            <dd>{{ album.releaseYear }}년</dd>
          </div>
          <div class="flex gap-2">
            <dt class="w-20 shrink-0 text-vinyl-800/50">상태</dt>
            <dd>
              {{ statusLabel(album.status) }}
              <span v-if="album.status === 'SELLING'" class="text-vinyl-800/50">
                · 재고 {{ album.stock }}장</span
              >
            </dd>
          </div>
        </dl>

        <p class="mt-5 text-2xl font-bold text-rust-600">{{ formatWon(album.price) }}</p>

        <p
          v-if="album.description"
          class="mt-5 whitespace-pre-line text-sm leading-relaxed text-vinyl-800/80"
        >
          {{ album.description }}
        </p>
      </div>
    </div>

    <!-- 리뷰 -->
    <section class="mt-12">
      <h2 class="mb-4 font-display text-xl font-bold text-vinyl-black">
        리뷰 <span class="text-base font-normal text-vinyl-800/50">{{ album.reviewCount }}</span>
      </h2>

      <p
        v-if="reviewsError"
        class="rounded-lg bg-rust-500/10 px-4 py-6 text-center text-sm text-rust-600"
      >
        리뷰를 불러오지 못했습니다.
      </p>

      <p
        v-else-if="reviews.length === 0"
        class="rounded-lg bg-cream-100 px-4 py-6 text-center text-sm text-vinyl-800/50"
      >
        아직 작성된 리뷰가 없습니다.
      </p>

      <template v-else>
        <ul class="space-y-4">
          <li
            v-for="r in reviews"
            :key="r.reviewId"
            class="rounded-lg border border-vinyl-800/10 bg-cream-50 p-4"
          >
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium text-vinyl-black">{{ r.memberName }}</span>
              <span class="text-xs text-vinyl-800/40">{{ formatDate(r.createdAt) }}</span>
            </div>
            <RatingStars class="mt-1" :rating="r.rating" :count="1" :show-count="false" />
            <p v-if="r.content" class="mt-2 text-sm text-vinyl-800/80">{{ r.content }}</p>
          </li>
        </ul>
        <p
          v-if="album.reviewCount > reviews.length"
          class="mt-3 text-center text-xs text-vinyl-800/40"
        >
          최근 {{ reviews.length }}개 리뷰를 표시하고 있습니다.
        </p>
      </template>
    </section>
  </article>
</template>
