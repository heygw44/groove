<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useGuestCartStore } from '@/stores/guestCart'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import { createOrder } from '@/api/orders'
import { myCoupons } from '@/api/coupons'
import { idempotencyKeyFor, clearIdempotencyKey } from '@/lib/uuid'
import * as albumsApi from '@/api/albums'
import { errorMessage } from '@/lib/problem-detail'
import { formatWon } from '@/lib/format'
import { previewDiscount, couponDiscountLabel } from '@/lib/order-enums'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()
const guestCart = useGuestCartStore()
const ui = useUiStore()
const { isAuthenticated } = storeToRefs(auth)

// 아이템 출처 모드: 바로구매 / 회원 서버카트 / 게스트 로컬카트
const buyNow = computed(() => route.query.albumId != null && route.query.albumId !== '')
const mode = computed(() =>
  buyNow.value ? 'buy-now' : isAuthenticated.value ? 'member-cart' : 'guest-cart',
)

const lines = ref([]) // 정규화된 주문 라인
const loading = ref(true)
const loadError = ref('')

const orderAmount = computed(() => lines.value.reduce((n, l) => n + l.subtotal, 0))

function fromCartItem(i) {
  return {
    albumId: i.albumId,
    title: i.albumTitle,
    artistName: i.artistName,
    coverImageUrl: i.coverImageUrl,
    unitPrice: i.unitPrice,
    quantity: i.quantity,
    subtotal: i.subtotal ?? i.unitPrice * i.quantity,
    available: i.available !== false, // 값이 없으면 가용으로 간주
  }
}

// AlbumDetail → 주문 라인. 바로구매·게스트 재조회 공통 매퍼(서버 현재가 권위).
// status 코드(enums.js: SELLING/SOLD_OUT/HIDDEN)가 'SELLING' 일 때만 구매 가능.
function lineFromAlbum(a, quantity) {
  const available = a.status === 'SELLING'
  return {
    albumId: a.id,
    title: a.title,
    artistName: a.artist?.name ?? '',
    coverImageUrl: a.coverImageUrl,
    unitPrice: a.price,
    quantity,
    subtotal: a.price * quantity,
    available,
    unavailableReason: available ? null : 'unavailable', // 'unavailable'(품절·판매중지) | 'temporary'(일시 조회 실패)
  }
}

// 품절·판매중지 또는 일시 조회 실패 항목이 있으면 주문 차단
const hasUnavailableLine = computed(() => lines.value.some((l) => !l.available))
// 안내 메시지 분기 — 실제 품절·판매중지 vs 네트워크/5xx 일시 실패 구분
const hasRealUnavailableLine = computed(() =>
  lines.value.some((l) => !l.available && l.unavailableReason !== 'temporary'),
)
const hasTemporaryLine = computed(() =>
  lines.value.some((l) => l.unavailableReason === 'temporary'),
)

onMounted(async () => {
  try {
    if (buyNow.value) {
      const id = Number(route.query.albumId)
      const qty = Math.max(1, Math.min(99, Number(route.query.qty) || 1))
      const album = await albumsApi.detail(id)
      lines.value = [lineFromAlbum(album, qty)]
    } else if (isAuthenticated.value) {
      await cart.load()
      lines.value = cart.items.map(fromCartItem) // 품절 항목도 표시하되 주문은 차단
    } else {
      // 게스트 카트: localStorage unitPrice 는 노후·변조 가능 → 체크아웃 진입 시 서버 현재가로 재조회.
      let priceChanged = false
      let transientFailures = 0 // 네트워크/5xx 등 일시 조회 실패 — 품절(판매중지)과 구분
      const built = await Promise.all(
        guestCart.items.map(async (i) => {
          try {
            const a = await albumsApi.detail(i.albumId)
            if (a.price !== i.unitPrice) priceChanged = true
            return lineFromAlbum(a, i.quantity)
          } catch (e) {
            // 일시 오류(네트워크 status 0·5xx)는 품절이 아니라 재조회 대상 → 사유를 보존해 구분.
            const temporary = e?.status === 0 || e?.status >= 500
            if (temporary) transientFailures++
            // 삭제·판매중지(404 등)·일시오류 모두 현재가를 신뢰할 수 없어 주문은 차단(사유만 구분).
            return {
              ...fromCartItem(i),
              available: false,
              unavailableReason: temporary ? 'temporary' : 'unavailable',
            }
          }
        }),
      )
      // 전 항목이 일시 오류로 실패 → 거짓 '품절'·'장바구니에서 제거' 대신 재시도 가능한 에러로.
      if (transientFailures > 0 && transientFailures === guestCart.items.length) {
        loadError.value = '상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
        return
      }
      lines.value = built
      if (transientFailures > 0) {
        ui.notify('일부 상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error')
      } else if (priceChanged) {
        ui.notify('일부 상품 가격이 변경되었습니다. 변경된 금액을 확인해 주세요.', 'info')
      }
    }
  } catch (e) {
    loadError.value = errorMessage(e, '주문 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }

  if (isAuthenticated.value) loadCoupons()
})

// ── 쿠폰 (회원 전용) ────────────────────────────────────────────────
const coupons = ref([])
const selectedCouponId = ref('')
const selectedCoupon = computed(() =>
  coupons.value.find((c) => String(c.memberCouponId) === String(selectedCouponId.value)),
)
const discount = computed(() => previewDiscount(selectedCoupon.value, orderAmount.value))
const payable = computed(() => Math.max(0, orderAmount.value - discount.value))

async function loadCoupons() {
  try {
    const page = await myCoupons({ status: 'ISSUED', size: 100 })
    coupons.value = page.content ?? []
  } catch {
    coupons.value = [] // 쿠폰 로드 실패는 무시
  }
}

function couponLabel(c) {
  return `${c.name} (${couponDiscountLabel(c)})`
}

// BaseSelect 옵션 — '쿠폰 미적용' + 보유 쿠폰(최소주문금액 미달은 disabled)
const couponOptions = computed(() => [
  { value: '', label: '쿠폰 미적용' },
  ...coupons.value.map((c) => ({
    value: c.memberCouponId,
    label: couponLabel(c) + (orderAmount.value < c.minOrderAmount ? ' — 최소 주문금액 미달' : ''),
    disabled: orderAmount.value < c.minOrderAmount,
  })),
])

// ── 폼 ──────────────────────────────────────────────────────────
const guest = reactive({ email: '', phone: '' })
const shipping = reactive({
  recipientName: '',
  recipientPhone: '',
  address: '',
  addressDetail: '',
  zipCode: '',
  safePackagingRequested: false,
})

const createdOrder = ref(null)
const { errors, formError, submitting, submit } = useForm(async () => {
  const body = {
    items: lines.value.map((l) => ({ albumId: l.albumId, quantity: l.quantity })),
    shipping: { ...shipping },
  }
  if (!isAuthenticated.value) body.guest = { email: guest.email, phone: guest.phone }
  if (isAuthenticated.value && selectedCouponId.value) {
    body.memberCouponId = Number(selectedCouponId.value)
  }
  // 주문 본문 전체를 scope 로 안정 키를 만들어(sessionStorage) 더블클릭·새로고침 재제출에도 같은 키를 보낸다.
  // items 만 쓰면 배송지·게스트·쿠폰 변경 시 같은 키로 서버 fingerprint mismatch(409)가 나므로 본문 전체를 쓴다.
  const scope = 'order:' + JSON.stringify(body)
  createdOrder.value = await createOrder(body, idempotencyKeyFor(scope))
  clearIdempotencyKey(scope) // 성공 후 키를 비워 같은 세션 후속 주문이 replay 되지 않게 한다
})

async function onSubmit() {
  if (!lines.value.length) {
    ui.notify('주문할 상품이 없습니다.', 'error')
    return
  }
  if (hasUnavailableLine.value) {
    ui.notify(
      hasRealUnavailableLine.value
        ? '품절·판매중지된 상품이 있어 주문할 수 없습니다. 장바구니에서 제거해 주세요.'
        : '일부 상품 정보를 일시적으로 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
      'error',
    )
    return
  }
  if (!(await submit())) return
  const order = createdOrder.value
  // 주문 성공 후 카트 정리
  if (mode.value === 'member-cart') {
    try {
      await cart.clear()
    } catch {
      /* 정리 실패는 무시 */
    }
  } else if (mode.value === 'guest-cart') {
    guestCart.clear()
  }
  ui.notify('주문이 생성되었습니다. 결제를 진행해 주세요.', 'success')
  router.replace({
    name: 'order-pay',
    params: { orderNumber: order.orderNumber },
    query: { amount: order.payableAmount },
  })
}
</script>

<template>
  <section class="mx-auto max-w-2xl py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">주문/결제</h1>

    <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

    <p v-else-if="loadError" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
      {{ loadError }}
    </p>

    <div
      v-else-if="!lines.length"
      class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
    >
      주문할 상품이 없습니다.
      <RouterLink to="/catalog" class="ml-1 font-medium text-rust-600 hover:underline">
        카탈로그 둘러보기
      </RouterLink>
    </div>

    <form v-else class="space-y-8" @submit.prevent="onSubmit">
      <p v-if="formError" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600" role="alert">
        {{ formError }}
      </p>

      <!-- 주문 상품 -->
      <div>
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">주문 상품</h2>
        <ul class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50">
          <li v-for="l in lines" :key="l.albumId" class="flex items-center gap-3 px-4 py-3">
            <img
              v-if="l.coverImageUrl"
              :src="l.coverImageUrl"
              :alt="l.title"
              class="h-12 w-12 rounded-md object-cover"
            />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-vinyl-black">{{ l.title }}</p>
              <p class="truncate text-xs text-vinyl-800/60">{{ l.artistName }} · {{ l.quantity }}개</p>
              <p v-if="!l.available" class="text-xs font-medium text-rust-600">
                {{ l.unavailableReason === 'temporary' ? '정보를 불러오지 못함 · 다시 시도' : '품절 · 판매중지' }}
              </p>
            </div>
            <p class="text-sm font-semibold text-vinyl-black">{{ formatWon(l.subtotal) }}</p>
          </li>
        </ul>
      </div>

      <!-- 게스트 정보 -->
      <div v-if="!isAuthenticated">
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">주문자 정보 (비회원)</h2>
        <div class="space-y-3">
          <BaseInput v-model="guest.email" type="email" label="이메일" placeholder="주문 조회에 사용됩니다" :error="errors['guest.email']" />
          <BaseInput v-model="guest.phone" type="tel" label="연락처" placeholder="01012345678 (숫자만)" :error="errors['guest.phone']" />
        </div>
      </div>

      <!-- 배송지 -->
      <div>
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">배송지</h2>
        <div class="space-y-3">
          <BaseInput v-model="shipping.recipientName" label="받는 분" :error="errors['shipping.recipientName']" />
          <BaseInput v-model="shipping.recipientPhone" type="tel" label="연락처" placeholder="01012345678" :error="errors['shipping.recipientPhone']" />
          <BaseInput v-model="shipping.zipCode" label="우편번호" :error="errors['shipping.zipCode']" />
          <BaseInput v-model="shipping.address" label="주소" :error="errors['shipping.address']" />
          <BaseInput v-model="shipping.addressDetail" label="상세주소 (선택)" :error="errors['shipping.addressDetail']" />
          <label class="flex items-center gap-2 text-sm text-vinyl-800">
            <input v-model="shipping.safePackagingRequested" type="checkbox" class="rounded border-vinyl-800/30 text-gold-500 focus:ring-gold-400" />
            안전 포장 요청
          </label>
        </div>
      </div>

      <!-- 쿠폰 (회원 전용) -->
      <div v-if="isAuthenticated">
        <h2 class="mb-3 font-display text-lg font-bold text-vinyl-black">쿠폰</h2>
        <BaseSelect v-model="selectedCouponId" :options="couponOptions" aria-label="쿠폰 선택" />
        <p v-if="!coupons.length" class="mt-2 text-xs text-vinyl-800/50">사용 가능한 쿠폰이 없습니다.</p>
      </div>

      <!-- 합계 -->
      <div class="rounded-lg border border-vinyl-800/15 bg-cream-50 px-4 py-4 text-sm">
        <div class="flex justify-between py-1">
          <span class="text-vinyl-800/70">상품 금액</span>
          <span>{{ formatWon(orderAmount) }}</span>
        </div>
        <div v-if="discount > 0" class="flex justify-between py-1 text-rust-600">
          <span>쿠폰 할인</span>
          <span>− {{ formatWon(discount) }}</span>
        </div>
        <div class="mt-1 flex justify-between border-t border-vinyl-800/10 pt-2 text-base font-bold text-vinyl-black">
          <span>결제 예정 금액</span>
          <span class="text-rust-600">{{ formatWon(isAuthenticated ? payable : orderAmount) }}</span>
        </div>
        <p v-if="isAuthenticated && discount > 0" class="mt-1 text-xs text-vinyl-800/50">
          최종 결제 금액은 주문 생성 시 서버가 확정합니다.
        </p>
      </div>

      <p
        v-if="hasRealUnavailableLine"
        class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
        role="alert"
      >
        품절·판매중지된 상품이 있어 주문할 수 없습니다. 장바구니에서 제거해 주세요.
      </p>
      <p
        v-if="hasTemporaryLine"
        class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
        role="alert"
      >
        일부 상품 정보를 일시적으로 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
      </p>
      <BaseButton type="submit" class="w-full" :loading="submitting" :disabled="hasUnavailableLine">
        결제하기
      </BaseButton>
    </form>
  </section>
</template>
