<script setup>
import { formatWon, formatDate } from '@/lib/format'
import { orderStatusLabel } from '@/lib/order-enums'

// 주문 응답(OrderResponse)을 읽기 전용으로 렌더하는 표시 컴포넌트 — OrderDetailView·GuestLookupView 공유.
defineProps({ order: { type: Object, required: true } })
</script>

<template>
  <div class="space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <p class="font-mono text-xs text-vinyl-800/60">{{ order.orderNumber }}</p>
        <p class="text-xs text-vinyl-800/50">{{ formatDate(order.createdAt) }}</p>
      </div>
      <span class="rounded-full bg-vinyl-black px-3 py-1 text-xs font-medium text-cream-50">
        {{ orderStatusLabel(order.status) }}
      </span>
    </div>

    <ul class="divide-y divide-vinyl-800/10 rounded-lg border border-vinyl-800/15 bg-cream-50">
      <li v-for="it in order.items" :key="it.albumId" class="px-4 py-3 text-sm">
        <div class="flex items-center justify-between">
          <span class="min-w-0 flex-1 truncate text-vinyl-black">
            {{ it.albumTitle }} <span class="text-vinyl-800/50">× {{ it.quantity }}</span>
          </span>
          <span class="ml-3 font-medium text-vinyl-black">{{ formatWon(it.subtotal) }}</span>
        </div>
        <!-- 항목별 액션(예: 리뷰 작성). 슬롯 미전달 시(게스트 조회 등) 아무것도 렌더하지 않는다. -->
        <slot name="item-action" :item="it" />
      </li>
    </ul>

    <div class="rounded-lg border border-vinyl-800/15 bg-cream-50 px-4 py-3 text-sm">
      <div class="flex justify-between py-0.5">
        <span class="text-vinyl-800/70">상품 금액</span>
        <span>{{ formatWon(order.totalAmount) }}</span>
      </div>
      <div v-if="order.discountAmount > 0" class="flex justify-between py-0.5 text-rust-600">
        <span>쿠폰 할인</span>
        <span>− {{ formatWon(order.discountAmount) }}</span>
      </div>
      <div class="mt-1 flex justify-between border-t border-vinyl-800/10 pt-2 font-bold text-vinyl-black">
        <span>결제 금액</span>
        <span class="text-rust-600">{{ formatWon(order.payableAmount) }}</span>
      </div>
    </div>

    <div v-if="order.shipping" class="rounded-lg border border-vinyl-800/15 bg-cream-50 px-4 py-3 text-sm text-vinyl-800/80">
      <p class="mb-1 font-medium text-vinyl-black">배송지</p>
      <p>{{ order.shipping.recipientName }} · {{ order.shipping.recipientPhone }}</p>
      <p>[{{ order.shipping.zipCode }}] {{ order.shipping.address }} {{ order.shipping.addressDetail }}</p>
      <p v-if="order.shipping.safePackagingRequested" class="mt-0.5 text-xs text-vinyl-800/50">
        안전 포장 요청됨
      </p>
    </div>
  </div>
</template>
