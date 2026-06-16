<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, updateProfile } from '@/api/members'
import { errorMessage } from '@/lib/problem-detail'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseButton from '@/components/base/BaseButton.vue'

const router = useRouter()
const ui = useUiStore()

const form = reactive({ name: '', phone: '' })
const loading = ref(true)
const loadError = ref('')

onMounted(async () => {
  try {
    const me = await getMe()
    form.name = me.name ?? ''
    form.phone = me.phone ?? ''
  } catch (e) {
    loadError.value = errorMessage(e, '내 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

// 빈 값은 페이로드에서 제외
function buildPayload() {
  const payload = {}
  if (form.name.trim()) payload.name = form.name.trim()
  if (form.phone.trim()) payload.phone = form.phone.trim()
  return payload
}

const { errors, formError, submitting, submit, clearError } = useForm(() =>
  updateProfile(buildPayload()),
)

async function onSubmit() {
  // 변경 필드가 없으면 PATCH 생략
  if (Object.keys(buildPayload()).length === 0) {
    ui.notify('변경할 내용이 없습니다.', 'info')
    return
  }
  if (!(await submit())) return
  ui.notify('프로필이 수정되었습니다.', 'success')
  router.replace({ name: 'my-page' })
}
</script>

<template>
  <section class="mx-auto max-w-sm py-8">
    <h1 class="mb-6 font-display text-2xl font-bold text-vinyl-black">프로필 수정</h1>

    <p v-if="loadError" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
      {{ loadError }}
    </p>
    <p v-else-if="loading" class="text-sm text-vinyl-800/60">불러오는 중…</p>

    <form v-else class="space-y-4" @submit.prevent="onSubmit">
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
        :error="errors.name"
        @update:model-value="clearError('name')"
      />
      <BaseInput
        v-model="form.phone"
        label="휴대전화"
        placeholder="01012345678"
        :error="errors.phone"
        @update:model-value="clearError('phone')"
      />

      <div class="flex gap-3">
        <BaseButton type="submit" :loading="submitting">저장</BaseButton>
        <BaseButton type="button" variant="ghost" @click="router.push({ name: 'my-page' })">
          취소
        </BaseButton>
      </div>
    </form>
  </section>
</template>
