<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import { errorMessage } from '@/lib/problem-detail'
import { formatWon } from '@/lib/format'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const router = useRouter()
const cart = useCartStore()
const ui = useUiStore()

const loading = ref(true)
const error = ref('')
const busy = ref(false) // 수량변경/삭제 in-flight 가드(연타 경합 방지)

onMounted(async () => {
  try {
    await cart.load()
  } catch (e) {
    error.value = errorMessage(e, '장바구니를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

async function changeQty(item, delta) {
  const next = item.quantity + delta
  if (next < 1 || next > 99 || busy.value) return
  busy.value = true
  try {
    await cart.update(item.itemId, next)
  } catch (e) {
    ui.notify(errorMessage(e, '수량을 변경하지 못했습니다.'), 'error')
  } finally {
    busy.value = false
  }
}

async function removeItem(item) {
  if (busy.value) return
  busy.value = true
  try {
    await cart.remove(item.itemId)
    ui.notify('장바구니에서 제거했습니다.', 'success')
  } catch (e) {
    ui.notify(errorMessage(e, '항목을 삭제하지 못했습니다.'), 'error')
  } finally {
    busy.value = false
  }
}

async function clearAll() {
  if (busy.value || !window.confirm('장바구니를 비우시겠습니까?')) return
  busy.value = true
  try {
    await cart.clear()
  } catch (e) {
    ui.notify(errorMessage(e, '장바구니를 비우지 못했습니다.'), 'error')
  } finally {
    busy.value = false
  }
}

function goCheckout() {
  if (cart.hasUnavailable) {
    ui.notify('품절·판매중지된 상품을 제거한 뒤 주문해 주세요.', 'error')
    return
  }
  router.push({ name: 'checkout' })
}
</script>

<template>
  <section class="mx-auto max-w-3xl py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">장바구니</h1>

    <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

    <p v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
      {{ error }}
    </p>

    <div
      v-else-if="cart.isEmpty"
      class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
    >
      장바구니가 비어 있습니다.
      <RouterLink to="/catalog" class="ml-1 font-medium text-rust-600 hover:underline">
        카탈로그 둘러보기
      </RouterLink>
    </div>

    <template v-else>
      <ul class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50">
        <li
          v-for="item in cart.items"
          :key="item.itemId"
          class="flex items-center gap-4 px-4 py-4"
          :class="{ 'opacity-50': !item.available }"
        >
          <RouterLink :to="{ name: 'album-detail', params: { id: item.albumId } }" class="shrink-0">
            <img
              v-if="item.coverImageUrl"
              :src="item.coverImageUrl"
              :alt="item.albumTitle"
              class="h-16 w-16 rounded-md object-cover"
            />
            <span v-else class="block h-16 w-16 rounded-md bg-vinyl-900"></span>
          </RouterLink>

          <div class="min-w-0 flex-1">
            <RouterLink
              :to="{ name: 'album-detail', params: { id: item.albumId } }"
              class="block truncate font-medium text-vinyl-black hover:underline"
            >
              {{ item.albumTitle }}
            </RouterLink>
            <p class="truncate text-sm text-vinyl-800/60">{{ item.artistName }}</p>
            <p class="text-sm text-vinyl-800/80">{{ formatWon(item.unitPrice) }}</p>
            <p v-if="!item.available" class="mt-0.5 text-xs font-medium text-rust-600">
              품절 · 판매중지 — 주문 전 제거해 주세요
            </p>
          </div>

          <div class="flex flex-col items-end gap-2">
            <div class="flex items-center rounded-md border border-vinyl-800/20">
              <button
                type="button"
                class="px-2 py-1 text-vinyl-800 disabled:opacity-30"
                :disabled="item.quantity <= 1 || busy"
                @click="changeQty(item, -1)"
              >
                −
              </button>
              <span class="min-w-8 px-2 text-center text-sm">{{ item.quantity }}</span>
              <button
                type="button"
                class="px-2 py-1 text-vinyl-800 disabled:opacity-30"
                :disabled="item.quantity >= 99 || busy"
                @click="changeQty(item, 1)"
              >
                ＋
              </button>
            </div>
            <p class="text-sm font-semibold text-vinyl-black">{{ formatWon(item.subtotal) }}</p>
            <button
              type="button"
              class="text-xs text-vinyl-800/50 hover:text-rust-600 disabled:opacity-40"
              :disabled="busy"
              @click="removeItem(item)"
            >
              삭제
            </button>
          </div>
        </li>
      </ul>

      <div class="mt-6 flex items-center justify-between">
        <button type="button" class="text-sm text-vinyl-800/60 hover:text-rust-600" @click="clearAll">
          전체 비우기
        </button>
        <p class="text-lg">
          합계 <span class="font-bold text-rust-600">{{ formatWon(cart.totalAmount) }}</span>
        </p>
      </div>

      <BaseButton class="mt-4 w-full" :disabled="busy || cart.hasUnavailable" @click="goCheckout">
        주문하기
      </BaseButton>
    </template>
  </section>
</template>
