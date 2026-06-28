<script setup>
// 토스 결제 콜백 결과 배너. 서버가 confirm/실패 처리 후 ?payment=success|fail 로 302 한 값을
// 회원/게스트 양쪽에서 동일 문구로 안내한다.
defineProps({
  result: {
    type: String, // 'success' | 'fail'
    required: true,
  },
})
</script>

<template>
  <div
    v-if="result === 'success'"
    class="mb-6 rounded-lg bg-gold-500/10 px-4 py-3 text-sm text-vinyl-black"
    role="status"
  >
    결제가 완료되었습니다 🎉 아래에서 주문 상태와 배송을 확인하세요.
  </div>
  <div
    v-else-if="result === 'fail'"
    class="mb-6 rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600"
    role="alert"
  >
    결제가 완료되지 않았습니다. 다시 시도하시거나
    <RouterLink to="/catalog" class="underline hover:text-rust-700">상품 목록</RouterLink>에서 새로 주문해 주세요.
  </div>
</template>
