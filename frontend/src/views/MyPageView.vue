<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, withdraw } from '@/api/members'
import { errorMessage } from '@/lib/problem-detail'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const me = ref(null)
const loading = ref(true)
const loadError = ref('')

onMounted(async () => {
  try {
    me.value = await getMe()
  } catch (e) {
    loadError.value = errorMessage(e, '내 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

// 위험 구역 — 회원 탈퇴. 비밀번호로 본인 확인. 진행 중 주문이 있으면 서버가 409 로 차단한다.
const password = ref('')
const {
  errors: withdrawErrors,
  formError: withdrawError,
  submitting: withdrawing,
  submit: submitWithdraw,
  clearError: clearWithdrawError,
} = useForm(() => withdraw({ password: password.value }))

async function onWithdraw() {
  if (!window.confirm('정말 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return
  if (!(await submitWithdraw())) return
  auth.logout() // 서버가 토큰을 폐기했으므로 로컬 상태만 비운다.
  ui.notify('탈퇴가 완료되었습니다.', 'success')
  router.replace('/')
}
</script>

<template>
  <section class="mx-auto max-w-lg py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">내 정보</h1>

    <p v-if="loadError" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
      {{ loadError }}
    </p>
    <p v-else-if="loading" class="text-sm text-vinyl-800/60">불러오는 중…</p>

    <template v-else-if="me">
      <dl class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50">
        <div class="flex justify-between px-4 py-3 text-sm">
          <dt class="text-vinyl-800/60">이름</dt>
          <dd class="font-medium text-vinyl-black">{{ me.name }}</dd>
        </div>
        <div class="flex justify-between px-4 py-3 text-sm">
          <dt class="text-vinyl-800/60">이메일</dt>
          <dd class="font-medium text-vinyl-black">{{ me.email }}</dd>
        </div>
        <div class="flex justify-between px-4 py-3 text-sm">
          <dt class="text-vinyl-800/60">휴대전화</dt>
          <dd class="font-medium text-vinyl-black">{{ me.phone }}</dd>
        </div>
        <div class="flex justify-between px-4 py-3 text-sm">
          <dt class="text-vinyl-800/60">회원 등급</dt>
          <dd class="font-medium text-vinyl-black">
            {{ me.role === 'ADMIN' ? '관리자' : '일반 회원' }}
          </dd>
        </div>
      </dl>

      <div class="mt-4 flex gap-3">
        <BaseButton variant="ghost" @click="router.push({ name: 'profile-edit' })">
          프로필 수정
        </BaseButton>
        <BaseButton variant="ghost" @click="router.push({ name: 'password-change' })">
          비밀번호 변경
        </BaseButton>
      </div>

      <div class="mt-10 rounded-lg border border-rust-500/30 bg-rust-500/5 p-4">
        <h2 class="font-display text-lg font-bold text-rust-600">위험 구역</h2>
        <p class="mt-1 text-sm text-vinyl-800/70">
          회원 탈퇴 시 계정은 되돌릴 수 없으며, 같은 이메일로 재가입할 수 없습니다.
        </p>
        <form class="mt-4 space-y-3" @submit.prevent="onWithdraw">
          <p
            v-if="withdrawError"
            class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
            role="alert"
          >
            {{ withdrawError }}
          </p>
          <BaseInput
            v-model="password"
            type="password"
            label="비밀번호 확인"
            :error="withdrawErrors.password"
            @update:model-value="clearWithdrawError('password')"
          />
          <BaseButton type="submit" variant="ghost" :loading="withdrawing">회원 탈퇴</BaseButton>
        </form>
      </div>
    </template>
  </section>
</template>
