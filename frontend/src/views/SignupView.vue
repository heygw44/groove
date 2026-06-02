<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { signupFlow } from '@/api/auth'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const router = useRouter()
const ui = useUiStore()

const form = reactive({ name: '', email: '', password: '', phone: '' })
// signupFlow 는 가입 성공 후 곧바로 로그인까지 수행한다.
const { errors, formError, submitting, submit, clearError } = useForm(() => signupFlow({ ...form }))

async function onSubmit() {
  if (!(await submit())) return
  ui.notify('가입이 완료되었습니다. 환영합니다!', 'success')
  router.replace('/')
}
</script>

<template>
  <section class="mx-auto max-w-sm py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">회원가입</h1>

    <form class="space-y-4" @submit.prevent="onSubmit">
      <p
        v-if="formError"
        class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
        role="alert"
      >
        {{ formError }}
      </p>

      <BaseInput
        v-model="form.name"
        label="이름"
        placeholder="홍길동"
        :error="errors.name"
        @update:model-value="clearError('name')"
      />
      <BaseInput
        v-model="form.email"
        type="email"
        label="이메일"
        placeholder="you@groove.dev"
        :error="errors.email"
        @update:model-value="clearError('email')"
      />
      <div>
        <BaseInput
          v-model="form.password"
          type="password"
          label="비밀번호"
          :error="errors.password"
          @update:model-value="clearError('password')"
        />
        <p class="mt-1 text-xs text-vinyl-800/60">
          10~72자, 영문·숫자·특수문자를 각각 1자 이상 포함해 주세요.
        </p>
      </div>
      <BaseInput
        v-model="form.phone"
        label="휴대전화"
        placeholder="01012345678"
        :error="errors.phone"
        @update:model-value="clearError('phone')"
      />

      <BaseButton type="submit" :loading="submitting" class="w-full">가입하기</BaseButton>
    </form>

    <p class="mt-6 text-center text-sm text-vinyl-800/70">
      이미 계정이 있으신가요?
      <RouterLink :to="{ name: 'login' }" class="text-rust-600 hover:underline">로그인</RouterLink>
    </p>
  </section>
</template>
