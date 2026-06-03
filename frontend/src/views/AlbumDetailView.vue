<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import * as albumsApi from '@/api/albums'
import { errorMessage } from '@/lib/problem-detail'
import { formatWon, formatDate } from '@/lib/format'
import { formatLabel, statusLabel } from '@/lib/enums'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useGuestCartStore } from '@/stores/guestCart'
import { useUiStore } from '@/stores/ui'
import { usePagination } from '@/composables/usePagination'
import RatingStars from '@/components/catalog/RatingStars.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const guestCart = useGuestCartStore()
const ui = useUiStore()
const { isAuthenticated } = storeToRefs(auth)
const adding = ref(false)
const album = ref(null)
const reviewsPage = ref(null) // PageResponse — 페이징 표시용(단일 출처)
const reviews = computed(() => reviewsPage.value?.content ?? []) // 목록은 현재 페이지에서 파생
const reviewsError = ref(false)
const reviewsLoading = ref(false) // 페이지 이동 중
const loading = ref(true)
const error = ref('')

const REVIEW_PAGE_SIZE = 10
let reqSeq = 0 // 앨범 단위 응답 순서 가드 — 빠른 앨범 전환 시 stale 응답 폐기.
let reviewSeq = 0 // 리뷰 목록(페이지 이동) 단위 가드.

// 리뷰 목록 로드 — 자체 가드(reviewSeq)로 페이지 이동/앨범 전환 stale 응답을 폐기한다.
async function loadReviews(id, pageNo) {
  const seq = ++reviewSeq
  reviewsLoading.value = true
  reviewsError.value = false
  try {
    const r = await albumsApi.reviews(id, { page: pageNo, size: REVIEW_PAGE_SIZE })
    if (seq !== reviewSeq) return
    reviewsPage.value = r
  } catch {
    if (seq !== reviewSeq) return
    reviewsError.value = true
    reviewsPage.value = null
  } finally {
    if (seq === reviewSeq) reviewsLoading.value = false
  }
}

async function fetchAll(id) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  album.value = null
  reviewsPage.value = null
  reviewsError.value = false
  try {
    const detail = await albumsApi.detail(id)
    if (seq !== reqSeq) return
    album.value = detail
    // 리뷰는 부가 정보 — 실패해도 상세는 보여준다(loadReviews 가 자체적으로 에러를 흡수).
    await loadReviews(id, 0)
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '앨범 정보를 불러오지 못했습니다.')
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

// usePagination 에는 ref 를 직접 넘긴다 — 내부가 unref 로 풀기 때문에 getter(()=>...) 를 주면
// unref 가 함수를 그대로 반환해 페이징 값이 전부 0/false 가 된다(hasPages 항상 false).
const { current, isFirst, isLast, hasPages, pages, totalPages } = usePagination(reviewsPage)

function goToReviewPage(p) {
  if (p < 0 || p > totalPages.value - 1 || p === current.value || reviewsLoading.value) return
  loadReviews(route.params.id, p)
}

const pageBtn =
  'min-w-9 rounded-md px-3 py-1.5 text-sm transition disabled:cursor-not-allowed disabled:opacity-40'

// 동일 컴포넌트 재사용(다른 id 로 이동) 시에도 재조회되도록 params.id 를 watch.
watch(() => route.params.id, fetchAll, { immediate: true })

// 담기 — 회원은 서버 카트(POST /cart/items), 게스트는 로컬 카트(렌더용 스냅샷 저장).
function snapshotOf(a) {
  return {
    albumId: a.id,
    albumTitle: a.title,
    artistName: a.artist?.name ?? '',
    coverImageUrl: a.coverImageUrl,
    unitPrice: a.price,
  }
}

async function addToCart() {
  const a = album.value
  if (!a || adding.value) return
  adding.value = true
  try {
    if (isAuthenticated.value) await cart.add(a.id, 1)
    else guestCart.add(snapshotOf(a), 1)
    ui.notify('장바구니에 담았습니다.', 'success')
  } catch (e) {
    ui.notify(errorMessage(e, '장바구니에 담지 못했습니다.'), 'error')
  } finally {
    adding.value = false
  }
}

// 바로 구매 — 회원/게스트 공통. 체크아웃에 단일 항목을 쿼리로 전달(서버/로컬 카트와 분리).
function buyNow() {
  const a = album.value
  if (!a) return
  router.push({ name: 'checkout', query: { albumId: a.id, qty: 1 } })
}
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

        <div v-if="album.status === 'SELLING'" class="mt-5 flex gap-3">
          <BaseButton :loading="adding" @click="addToCart">장바구니 담기</BaseButton>
          <BaseButton variant="ghost" @click="buyNow">바로 구매</BaseButton>
        </div>
        <p v-else class="mt-5 text-sm text-vinyl-800/50">현재 구매할 수 없는 상품입니다.</p>

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
      <h2 class="mb-1 font-display text-xl font-bold text-vinyl-black">
        리뷰 <span class="text-base font-normal text-vinyl-800/50">{{ album.reviewCount }}</span>
      </h2>
      <p class="mb-4 text-xs text-vinyl-800/50">
        배송 완료된 주문의 주문 상세에서 리뷰를 작성할 수 있습니다.
      </p>

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
        <nav
          v-if="hasPages"
          class="mt-6 flex items-center justify-center gap-1"
          aria-label="리뷰 페이지 이동"
        >
          <button
            type="button"
            :class="pageBtn"
            class="hover:bg-cream-100"
            :disabled="isFirst || reviewsLoading"
            @click="goToReviewPage(current - 1)"
          >
            이전
          </button>
          <button
            v-for="p in pages"
            :key="p"
            type="button"
            :class="[
              pageBtn,
              p === current ? 'bg-vinyl-black font-semibold text-cream-50' : 'hover:bg-cream-100',
            ]"
            :aria-current="p === current ? 'page' : undefined"
            :disabled="reviewsLoading"
            @click="goToReviewPage(p)"
          >
            {{ p + 1 }}
          </button>
          <button
            type="button"
            :class="pageBtn"
            class="hover:bg-cream-100"
            :disabled="isLast || reviewsLoading"
            @click="goToReviewPage(current + 1)"
          >
            다음
          </button>
        </nav>
      </template>
    </section>
  </article>
</template>
