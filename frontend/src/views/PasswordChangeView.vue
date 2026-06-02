<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '@/api/members'
import { logoutFlow } from '@/api/auth'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const router = useRouter()
const ui = useUiStore()

const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const confirmError = ref('') // 새 비밀번호 확인 불일치(클라이언트 검증)
// "새 비밀번호 == 현재" 는 서버가 입력 필드에 매핑되지 않는 이름(newPasswordDistinct)으로 내려주므로
// 클라이언트에서 즉시 newPassword 필드 에러로 표시한다(현재/새 값을 모두 보유하므로 사전 검증 가능).
const sameError = ref('')

const { errors, formError, submitting, submit, clearError, reset } = useForm(() =>
  changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword }),
)

function onNewPasswordInput() {
  clearError('newPassword')
  sameError.value = ''
}

function onCurrentPasswordInput() {
  clearError('currentPassword')
  sameError.value = '' // 현재 비번을 고쳐 "새==현재"를 해소해도 메시지가 남지 않도록 함께 지운다.
}

async function onSubmit() {
  // 클라이언트 사전 검증 — 서버 호출 전에 직전 서버 에러를 초기화해 stale 메시지가 남지 않게 한다.
  reset()
  confirmError.value = ''
  sameError.value = ''
  if (form.newPassword === form.currentPassword) {
    sameError.value = '새 비밀번호는 현재 비밀번호와 달라야 합니다.'
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    confirmError.value = '새 비밀번호가 일치하지 않습니다.'
    return
  }
  if (!(await submit())) return
  // 서버가 모든 refresh 토큰을 폐기했으므로 로컬 로그아웃 후 재로그인을 유도한다.
  await logoutFlow()
  ui.notify('비밀번호가 변경되었습니다. 다시 로그인해 주세요.', 'success')
  router.replace({ name: 'login' })
}
</script>

<template>
  <section class="mx-auto max-w-sm py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">비밀번호 변경</h1>

    <form class="space-y-4" @submit.prevent="onSubmit">
      <p
        v-if="formError"
        class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
        role="alert"
      >
        {{ formError }}
      </p>

      <BaseInput
        v-model="form.currentPassword"
        type="password"
        label="현재 비밀번호"
        :error="errors.currentPassword"
        @update:model-value="onCurrentPasswordInput"
      />
      <div>
        <BaseInput
          v-model="form.newPassword"
          type="password"
          label="새 비밀번호"
          :error="errors.newPassword || sameError"
          @update:model-value="onNewPasswordInput"
        />
        <p class="mt-1 text-xs text-vinyl-800/60">
          10~72자, 영문·숫자·특수문자를 각각 1자 이상 포함해 주세요.
        </p>
      </div>
      <BaseInput
        v-model="form.confirmPassword"
        type="password"
        label="새 비밀번호 확인"
        :error="confirmError"
        @update:model-value="confirmError = ''"
      />

      <div class="flex gap-3">
        <BaseButton type="submit" :loading="submitting">변경</BaseButton>
        <BaseButton type="button" variant="ghost" @click="router.push({ name: 'my-page' })">
          취소
        </BaseButton>
      </div>
    </form>
  </section>
</template>
