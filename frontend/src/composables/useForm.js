import { reactive, ref } from 'vue'
import { ApiError, errorMessage } from '@/lib/problem-detail'

/** 폼 제출 상태(로딩·에러) 관리. violations→필드 에러, 그 외→formError. */
export function useForm(onSubmit) {
  const errors = reactive({}) // { [field]: message }
  const formError = ref('') // 특정 필드에 매핑되지 않는 전역 에러
  const submitting = ref(false)

  /** 특정 필드 에러 제거. */
  function clearError(field) {
    if (field in errors) delete errors[field]
  }

  /** 에러 상태 전체 초기화. */
  function reset() {
    for (const k of Object.keys(errors)) delete errors[k]
    formError.value = ''
  }

  /** 제출 실행. 성공 시 true, 실패 시 false 를 반환하고 에러는 errors/formError 로 노출. */
  async function submit() {
    if (submitting.value) return false // 중복 제출 가드
    reset()
    submitting.value = true
    try {
      await onSubmit()
      return true
    } catch (e) {
      if (e instanceof ApiError && e.violations.length) {
        Object.assign(errors, e.fieldErrors())
        // field 없는 위반(객체/폼 레벨)은 formError 로 노출.
        const general = e.violations
          .filter((v) => v && !v.field)
          .map((v) => v.message)
          .filter(Boolean)
        if (general.length) formError.value = general.join(' ')
      } else {
        formError.value = errorMessage(e, '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
      return false
    } finally {
      submitting.value = false
    }
  }

  return { errors, formError, submitting, submit, clearError, reset }
}
