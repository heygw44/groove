<script setup>
import { ref, computed, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getOrder, cancelOrder } from '@/api/orders'
import * as reviewsApi from '@/api/reviews'
import { useUiStore } from '@/stores/ui'
import { errorMessage } from '@/lib/problem-detail'
import { isCancellableStatus, isPaidStatus, isReviewableStatus } from '@/lib/order-enums'
import { usePolling } from '@/composables/usePolling'
import { useForm } from '@/composables/useForm'
import OrderItemsCard from '@/components/order/OrderItemsCard.vue'
import ShippingTracker from '@/components/order/ShippingTracker.vue'
import RatingInput from '@/components/catalog/RatingInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const ui = useUiStore()

const order = ref(null)
const trackingNumber = ref('')
const trackingPending = ref(false) // 운송장 발급(배송 비동기 생성) 대기 중
const loading = ref(true)
const error = ref('')
const cancelling = ref(false)

const cancellable = computed(() => order.value && isCancellableStatus(order.value.status))
const paid = computed(() => order.value && isPaidStatus(order.value.status))

// ── 리뷰 작성·삭제 (배송완료 주문 한정). 한 번에 한 항목만 인라인 폼을 펼친다. ──────────────────
// 백엔드 계약은 orderNumber + albumId. ReviewResponse/OrderResponse 모두 작성여부·소유권 플래그가
// 없어, 작성 직후 받은 reviewId 로만 같은 세션에서 삭제를 노출한다(새로고침 시 작성됨 표시는 사라짐).
const reviewable = computed(() => order.value && isReviewableStatus(order.value.status))
const reviewedMap = reactive({}) // { [albumId]: reviewId } — 이 세션에서 작성한 리뷰
const activeAlbumId = ref(null) // 현재 폼이 펼쳐진 항목(앨범 id)
const rating = ref(0)
const content = ref('')
const ratingError = ref('')
const deletingId = ref(null)

const { errors, formError, submitting, submit, reset } = useForm(async () => {
  const albumId = activeAlbumId.value
  const res = await reviewsApi.create({
    orderNumber: order.value.orderNumber,
    albumId,
    rating: rating.value,
    content: content.value.trim() || null,
  })
  reviewedMap[albumId] = res.reviewId
})

function clearReviewFields() {
  rating.value = 0
  content.value = ''
  ratingError.value = ''
  reset() // useForm 의 errors/formError 정리
}

function resetReviewForm() {
  activeAlbumId.value = null
  clearReviewFields()
}

function openForm(albumId) {
  activeAlbumId.value = albumId
  clearReviewFields()
}

async function onSubmitReview() {
  ratingError.value = ''
  if (!rating.value) {
    // 평점은 클라에서 즉시 가드(별점 미선택). 내용 길이는 textarea maxlength + 서버 검증에 맡긴다.
    ratingError.value = '평점을 선택해 주세요.'
    return
  }
  if (await submit()) {
    ui.notify('리뷰가 등록되었습니다.', 'success')
    resetReviewForm()
  }
}

async function onDeleteReview(albumId) {
  const reviewId = reviewedMap[albumId]
  if (!reviewId || deletingId.value) return
  if (!window.confirm('작성한 리뷰를 삭제하시겠습니까?')) return
  deletingId.value = reviewId
  try {
    await reviewsApi.remove(reviewId)
    delete reviewedMap[albumId]
    ui.notify('리뷰가 삭제되었습니다.', 'success')
  } catch (e) {
    ui.notify(errorMessage(e, '리뷰를 삭제하지 못했습니다.'), 'error')
  } finally {
    deletingId.value = null
  }
}

const TRACK_POLL_MS = 2000
const TRACK_MAX = 15 // 운송장 발급 대기 ~30s. 도달해도 없으면(시드/관리자 전진 등 배송행 없는 주문) "없음" 처리.
const trackingPoller = usePolling()

let reqSeq = 0

async function fetchOrder(orderNumber) {
  const seq = ++reqSeq
  loading.value = true
  error.value = ''
  trackingPoller.stop()
  trackingNumber.value = ''
  trackingPending.value = false
  // 다른 주문으로 이동 시 리뷰 폼·세션 작성 상태를 초기화(앨범 id 가 겹쳐도 섞이지 않게).
  resetReviewForm()
  for (const k of Object.keys(reviewedMap)) delete reviewedMap[k]
  try {
    const o = await getOrder(orderNumber)
    if (seq !== reqSeq) return
    order.value = o
    if (o.trackingNumber) {
      trackingNumber.value = o.trackingNumber
    } else if (isPaidStatus(o.status)) {
      // 결제 완료인데 아직 운송장 미발급이면 주문을 재폴링해 번호를 확보 → ShippingTracker 가 이어받음.
      // 끝까지 안 나오면(배송행 없는 주문) trackingPending 을 풀어 "배송 정보 없음" 으로 마무리한다.
      startTrackingWait(orderNumber, seq)
    }
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = errorMessage(e, '주문을 불러오지 못했습니다.')
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

function startTrackingWait(orderNumber, seq) {
  trackingPending.value = true
  trackingPoller.start(
    async () => {
      const o = await getOrder(orderNumber)
      // usePolling.stop() 은 다음 tick 만 막고 진행 중 콜백은 못 멈추므로, await 직후 시퀀스를 재확인한다.
      // 라우트 변경(다른 주문) 후 늦게 resolve 된 응답이 새 주문 상태를 덮어쓰는 것을 막고 폴링을 종료한다.
      if (seq !== reqSeq) return true
      order.value = o
      if (o.trackingNumber) {
        trackingNumber.value = o.trackingNumber
        trackingPending.value = false
        return true
      }
      return false
    },
    {
      intervalMs: TRACK_POLL_MS,
      maxAttempts: TRACK_MAX,
      onExhausted: () => {
        trackingPending.value = false
      },
    },
  )
}

async function onCancel() {
  if (!window.confirm('주문을 취소하시겠습니까?')) return
  cancelling.value = true
  // 진행 중 배송폴링을 멈춰, 취소 결과가 stale getOrder 응답으로 덮이지 않게 한다(취소→PAID 복귀 레이스 방지).
  trackingPoller.stop()
  trackingPending.value = false
  try {
    order.value = await cancelOrder(order.value.orderNumber)
    ui.notify('주문이 취소되었습니다.', 'success')
  } catch (e) {
    ui.notify(errorMessage(e, '주문을 취소하지 못했습니다.'), 'error')
  } finally {
    cancelling.value = false
  }
}

watch(() => route.params.orderNumber, fetchOrder, { immediate: true })
</script>

<template>
  <section class="mx-auto max-w-2xl py-8">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="font-display text-2xl font-bold text-vinyl-black">주문 상세</h1>
      <RouterLink :to="{ name: 'orders' }" class="text-sm text-vinyl-800/60 hover:text-rust-600">
        주문 내역
      </RouterLink>
    </div>

    <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

    <p v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
      {{ error }}
    </p>

    <template v-else-if="order">
      <OrderItemsCard :order="order">
        <template #item-action="{ item }">
          <div v-if="reviewable" class="mt-2">
            <!-- 이 세션에서 작성 완료 → 삭제 가능 -->
            <div v-if="reviewedMap[item.albumId]" class="flex items-center gap-3 text-xs">
              <span class="text-vinyl-800/60">리뷰 작성됨</span>
              <button
                type="button"
                class="text-rust-600 hover:underline disabled:opacity-50"
                :disabled="!!deletingId"
                @click="onDeleteReview(item.albumId)"
              >
                삭제
              </button>
            </div>

            <!-- 작성 폼(펼침) -->
            <div
              v-else-if="activeAlbumId === item.albumId"
              class="rounded-lg border border-vinyl-800/15 bg-cream-100 p-3"
            >
              <RatingInput v-model="rating" @update:model-value="ratingError = ''" />
              <p v-if="ratingError" class="mt-1 text-xs text-rust-600">{{ ratingError }}</p>
              <textarea
                v-model="content"
                rows="3"
                maxlength="2000"
                placeholder="리뷰를 남겨 주세요 (선택)"
                class="mt-2 w-full rounded-lg border bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400"
                :class="errors.content ? 'border-rust-500' : 'border-vinyl-800/20'"
              ></textarea>
              <p v-if="errors.content" class="mt-1 text-xs text-rust-600">{{ errors.content }}</p>
              <p
                v-if="formError"
                class="mt-2 rounded bg-rust-500/10 px-3 py-2 text-xs text-rust-600"
              >
                {{ formError }}
              </p>
              <div class="mt-2 flex gap-2">
                <BaseButton :loading="submitting" @click="onSubmitReview">작성</BaseButton>
                <BaseButton variant="ghost" :disabled="submitting" @click="resetReviewForm">
                  취소
                </BaseButton>
              </div>
            </div>

            <!-- 작성 버튼 -->
            <button
              v-else
              type="button"
              class="text-xs text-rust-600 hover:underline"
              @click="openForm(item.albumId)"
            >
              리뷰 작성
            </button>
          </div>
        </template>
      </OrderItemsCard>

      <div v-if="cancellable" class="mt-4">
        <BaseButton variant="ghost" :loading="cancelling" @click="onCancel">주문 취소</BaseButton>
      </div>

      <div v-if="paid" class="mt-8">
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">배송 추적</h2>
        <ShippingTracker v-if="trackingNumber" :tracking-number="trackingNumber" />
        <div
          v-else-if="trackingPending"
          class="flex items-center gap-3 rounded-lg bg-cream-100 px-4 py-4 text-sm text-vinyl-800/60"
        >
          <BaseSpinner />
          배송 정보를 확인하고 있습니다…
        </div>
        <div v-else class="rounded-lg bg-cream-100 px-4 py-4 text-sm text-vinyl-800/60">
          배송 추적 정보가 없습니다.
        </div>
      </div>
    </template>
  </section>
</template>
