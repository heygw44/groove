<script setup>
import { reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loginFlow } from '@/api/auth'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const form = reactive({ email: '', password: '' })
const { errors, formError, submitting, submit, clearError } = useForm(() =>
  loginFlow(form.email, form.password),
)

async function onSubmit() {
  if (!(await submit())) return
  ui.notify('환영합니다.', 'success')
  // 복귀 경로(redirect)가 같은 오리진 절대경로면 그곳으로, 아니면 홈으로.
  const r = route.query.redirect
  const target = typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/'
  router.replace(target)
}
</script>

<template>
  <section class="mx-auto max-w-sm py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">로그인</h1>

    <form class="space-y-4" @submit.prevent="onSubmit">
      <p
        v-if="formError"
        class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
        role="alert"
      >
        {{ formError }}
      </p>

      <BaseInput
        v-model="form.email"
        type="email"
        label="이메일"
        placeholder="you@groove.dev"
        :error="errors.email"
        @update:model-value="clearError('email')"
      />
      <BaseInput
        v-model="form.password"
        type="password"
        label="비밀번호"
        :error="errors.password"
        @update:model-value="clearError('password')"
      />

      <BaseButton type="submit" :loading="submitting" class="w-full">로그인</BaseButton>
    </form>

    <p class="mt-6 text-center text-sm text-vinyl-800/70">
      아직 계정이 없으신가요?
      <RouterLink :to="{ name: 'signup' }" class="text-rust-600 hover:underline">회원가입</RouterLink>
    </p>
  </section>
</template>
