<script setup>
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { logoutFlow } from '@/api/auth'
import SearchBar from '@/components/SearchBar.vue'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const { user, isAuthenticated } = storeToRefs(auth)

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
        <RouterLink to="/cart" class="hover:text-gold-400">장바구니</RouterLink>
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
