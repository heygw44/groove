import { defineStore } from 'pinia'
import * as cartApi from '@/api/cart'

// 회원 서버 장바구니 상태. 헤더 뱃지·CartView·체크아웃이 공유한다.
// 게스트는 서버 카트를 못 쓰므로 stores/guestCart(localStorage)를 사용하고, 로그인 시 병합한다(TheHeader).

/**
 * 인증 상태 스토어와 달리, 이 스토어는 항상 서버가 권위다 — add/update API 응답(CartResponse)으로 상태를
 * 통째 교체해 클라-서버 드리프트를 막는다. remove/clear 는 204(본문 없음)라 로컬에서 반영한다
 * (remove=해당 항목 제거·합계 재계산, clear=빈 상태).
 */
export const useCartStore = defineStore('cart', {
  state: () => ({
    cartId: null,
    items: [], // CartItemResponse[] {itemId, albumId, albumTitle, artistName, coverImageUrl, unitPrice, quantity, subtotal, available}
    totalAmount: 0,
    totalItemCount: 0,
    loaded: false, // 최초 로드 완료 여부 — CartView 로딩 표시 가드
  }),
  getters: {
    itemCount: (state) => state.totalItemCount,
    isEmpty: (state) => state.items.length === 0,
    /** 품절·판매중지 항목 포함 여부 — 체크아웃 진행 가드. */
    hasUnavailable: (state) => state.items.some((i) => !i.available),
  },
  actions: {
    /** CartResponse 로 상태 전체 교체. */
    apply(res) {
      this.cartId = res?.cartId ?? null
      this.items = res?.items ?? []
      this.totalAmount = res?.totalAmount ?? 0
      this.totalItemCount = res?.totalItemCount ?? 0
      this.loaded = true
    },
    async load() {
      this.apply(await cartApi.getCart())
    },
    async add(albumId, quantity = 1) {
      this.apply(await cartApi.addItem(albumId, quantity))
    },
    async update(itemId, quantity) {
      this.apply(await cartApi.updateItem(itemId, quantity))
    },
    async remove(itemId) {
      await cartApi.removeItem(itemId) // 204
      // 한 항목 제거는 나머지 항목의 available 에 영향이 없으므로, GET 재조회 없이 로컬에서 제거·합계 재계산한다.
      this.items = this.items.filter((i) => i.itemId !== itemId)
      this.totalItemCount = this.items.reduce((n, i) => n + i.quantity, 0)
      this.totalAmount = this.items.reduce((n, i) => n + i.subtotal, 0)
    },
    async clear() {
      await cartApi.clearCart() // 204
      this.apply({ cartId: this.cartId, items: [], totalAmount: 0, totalItemCount: 0 })
    },
    // 로그아웃 시 $reset() (Pinia options store 기본 제공)으로 loaded=false 까지 초기화한다.
  },
})
