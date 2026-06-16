import { defineStore } from 'pinia'
import * as cartApi from '@/api/cart'

// 회원 서버 장바구니 상태. 헤더 뱃지·CartView·체크아웃이 공유한다.
// add/update 는 CartResponse 로 상태 전체 교체, remove/clear 는 204 라 로컬 반영.
export const useCartStore = defineStore('cart', {
  state: () => ({
    cartId: null,
    items: [], // CartItemResponse[] {itemId, albumId, albumTitle, artistName, coverImageUrl, unitPrice, quantity, subtotal, available}
    totalAmount: 0,
    totalItemCount: 0,
    loaded: false, // 최초 로드 완료 여부
  }),
  getters: {
    itemCount: (state) => state.totalItemCount,
    isEmpty: (state) => state.items.length === 0,
    /** 품절·판매중지 항목 포함 여부. */
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
      // 로컬에서 항목 제거·합계 재계산.
      this.items = this.items.filter((i) => i.itemId !== itemId)
      this.totalItemCount = this.items.reduce((n, i) => n + i.quantity, 0)
      this.totalAmount = this.items.reduce((n, i) => n + i.subtotal, 0)
    },
    async clear() {
      await cartApi.clearCart() // 204
      this.apply({ cartId: this.cartId, items: [], totalAmount: 0, totalItemCount: 0 })
    },
    // 로그아웃 시 $reset() 으로 초기화.
  },
})
