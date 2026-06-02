import { reactive, ref } from 'vue'
import { ApiError, errorMessage } from '@/lib/problem-detail'

/**
 * 폼 제출 상태(로딩·에러)를 관리하는 컴포저블.
 *
 * 서버 ProblemDetail 의 violations 는 필드별 에러(errors)로, 그 외(자격증명 오류·중복 등
 * violations 없는 비즈니스 에러)는 폼 전역 메시지(formError)로 매핑한다.
 * 폼 값 자체는 호출부가 reactive/ref 로 보유하고 onSubmit 클로저에서 참조한다.
 *
 * @param {() => Promise<any>} onSubmit 실제 제출 동작(API/스토어 호출).
 * @returns {{errors:Record<string,string>, formError:import('vue').Ref<string>,
 *   submitting:import('vue').Ref<boolean>, submit:() => Promise<boolean>,
 *   clearError:(field:string) => void}}
 */
export function useForm(onSubmit) {
  const errors = reactive({}) // { [field]: message }
  const formError = ref('') // 특정 필드에 매핑되지 않는 전역 에러
  const submitting = ref(false)

  /** 특정 필드 에러 제거 — 입력 변경 시(@update:model-value) 호출해 수정 중 에러를 지운다. */
  function clearError(field) {
    if (field in errors) delete errors[field]
  }

  /** 에러 상태 전체 초기화. 뷰가 클라이언트 사전 검증 전에 직전 서버 에러를 지울 때도 쓴다. */
  function reset() {
    for (const k of Object.keys(errors)) delete errors[k]
    formError.value = ''
  }

  /** 제출 실행. 성공 시 true, 실패 시 false 를 반환하고 에러는 errors/formError 로 노출한다(예외 전파 안 함). */
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
        // 입력에 매핑되지 않는 위반(field 없음 = 객체/폼 레벨)은 배너로 노출해 silent 실패를 막는다.
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
