<script setup>
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useCartStore } from '@/stores/cart'
import { useGuestCartStore } from '@/stores/guestCart'
import { logoutFlow } from '@/api/auth'
import SearchBar from '@/components/SearchBar.vue'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const cart = useCartStore()
const guestCart = useGuestCartStore()
const { user, isAuthenticated } = storeToRefs(auth)

// 장바구니 뱃지 — 회원은 서버 카트, 게스트는 로컬 카트 개수.
const cartCount = computed(() => (isAuthenticated.value ? cart.itemCount : guestCart.itemCount))

// 인증 상태 전이를 한 곳에서 처리해(로그인/로그아웃/탈퇴/인터셉터 강제로그아웃 모두 커버) 카트를 동기화한다.
//  - 실제 미인증(false) → 인증(true) 전이: 게스트 카트를 서버 카트로 병합. (부팅 즉시실행 prev===undefined 는 제외 —
//    재방문 시 다른 게스트의 잔여 카트가 현재 계정에 섞이지 않게 한다.)
//  - 부팅 시 이미 로그인(prev===undefined && now): 병합 없이 서버 카트만 로드해 뱃지를 채운다.
//  - 인증 → 미인증: 서버 카트 상태를 초기화($reset).
// 병합 중 로그아웃/재전이를 mergeGen 세대 토큰으로 감지해 stale 반영을 막고, 병합 실패한 항목만 게스트 카트에 보존한다.
let mergeGen = 0
watch(
  isAuthenticated,
  async (now, prev) => {
    if (now && prev === false) {
      const gen = ++mergeGen
      const failed = []
      for (const it of [...guestCart.items]) {
        try {
          await cart.add(it.albumId, it.quantity)
        } catch {
          failed.push(it) // 품절·판매중지 등 — 병합 실패분은 보존
        }
        if (gen !== mergeGen) return // 병합 중 로그아웃/재전이 → 중단(stale 반영 방지)
      }
      guestCart.replaceAll(failed) // 성공분은 비우고 실패분만 남긴다(silent 손실 방지)
      try {
        await cart.load()
      } catch {
        // 카트 로드 실패는 뱃지 0 으로 둔다(치명적 아님).
      }
    } else if (now && prev === undefined) {
      try {
        await cart.load()
      } catch {
        // 무시
      }
    } else if (!now && prev) {
      mergeGen++ // 진행 중 병합 무효화
      cart.$reset()
    }
  },
  { immediate: true },
)

async function onLogout() {
  await logoutFlow()
  ui.notify('로그아웃되었습니다.', 'success')
  router.push('/')
}
</script>

<template>
  <header class="border-b border-vinyl-800/20 bg-vinyl-black text-cream-50">
    <div class="mx-auto flex w-full max-w-6xl items-center gap-6 px-4 py-3">
      <RouterLink to="/" class="flex shrink-0 items-center gap-2 font-display text-xl font-bold tracking-tight">
        <span class="inline-block h-6 w-6 rounded-full border-2 border-gold-400 bg-vinyl-900"></span>
        groove
      </RouterLink>

      <div class="hidden flex-1 sm:block">
        <SearchBar />
      </div>

      <nav class="ml-auto flex items-center gap-4 text-sm">
        <RouterLink to="/catalog" class="hover:text-gold-400">카탈로그</RouterLink>
        <RouterLink to="/cart" class="relative hover:text-gold-400">
          장바구니
          <span
            v-if="cartCount > 0"
            class="absolute -right-3 -top-2 inline-flex min-w-4 items-center justify-center rounded-full bg-gold-500 px-1 text-[10px] font-bold leading-4 text-vinyl-black"
          >
            {{ cartCount > 99 ? '99+' : cartCount }}
          </span>
        </RouterLink>
        <template v-if="isAuthenticated">
          <RouterLink v-if="user?.isAdmin" to="/admin" class="hover:text-gold-400">관리자</RouterLink>
          <RouterLink to="/me" class="max-w-[12rem] truncate hover:text-gold-400">
            {{ user?.email || '내 정보' }}
          </RouterLink>
          <button type="button" class="hover:text-rust-500" @click="onLogout">로그아웃</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="hover:text-gold-400">로그인</RouterLink>
          <RouterLink to="/signup" class="hover:text-gold-400">회원가입</RouterLink>
        </template>
      </nav>
    </div>
  </header>
</template>
