import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { firstStr } from '@/lib/query'
import { isPaymentResult } from '@/lib/payment-result'

/**
 * 토스 결제 콜백 결과(?payment=success|fail) 캡처 + 표시 후 URL 정리(#308).
 * 서버 302/라우터 가드가 보존한 payment 쿼리를 배너용 값으로 캡처하고, onMounted 에서 payment 키만 제거해
 * 새로고침 시 재노출을 막는다(나머지 쿼리는 보존). 회원(OrderDetailView)·게스트(GuestLookupView)·주문목록(OrderListView) 공용.
 * firstStr 로 중복 키(?payment=a&payment=b)·배열 값을 단일 문자열로 정규화한다.
 */
export function usePaymentResultBanner() {
  const route = useRoute()
  const router = useRouter()
  const value = firstStr(route.query.payment)
  const paymentResult = ref(isPaymentResult(value) ? value : '')
  onMounted(() => {
    if (!paymentResult.value) return
    const { payment, ...rest } = route.query
    router.replace({ query: rest })
  })
  return { paymentResult }
}
