import { defineStore } from 'pinia'

// 게스트(비회원) 장바구니 — localStorage 영속. 표시 스냅샷(제목·가격·커버)을 함께 저장.
// 로그인 시 TheHeader 가 서버 카트로 병합(cart.add)한 뒤 clear() 한다.

const KEY = 'groove.guestCart'
const MAX_QTY = 99

function loadItems() {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export const useGuestCartStore = defineStore('guestCart', {
  state: () => ({
    // [{albumId, albumTitle, artistName, coverImageUrl, unitPrice, quantity}]
    items: loadItems(),
  }),
  getters: {
    itemCount: (state) => state.items.reduce((n, i) => n + i.quantity, 0),
    totalAmount: (state) => state.items.reduce((n, i) => n + i.unitPrice * i.quantity, 0),
    isEmpty: (state) => state.items.length === 0,
  },
  actions: {
    persist() {
      localStorage.setItem(KEY, JSON.stringify(this.items))
    },
    /** 담기. 동일 albumId 는 수량 누적(최대 99). snapshot = {albumId, albumTitle, artistName, coverImageUrl, unitPrice}. */
    add(snapshot, quantity = 1) {
      const existing = this.items.find((i) => i.albumId === snapshot.albumId)
      if (existing) {
        existing.quantity = Math.min(MAX_QTY, Math.max(1, existing.quantity + quantity))
      } else {
        this.items.push({ ...snapshot, quantity: Math.min(MAX_QTY, Math.max(1, quantity)) })
      }
      this.persist()
    },
    /** 수량 절대값 교체(1~99). */
    update(albumId, quantity) {
      const it = this.items.find((i) => i.albumId === albumId)
      if (it) {
        it.quantity = Math.min(MAX_QTY, Math.max(1, quantity))
        this.persist()
      }
    },
    remove(albumId) {
      this.items = this.items.filter((i) => i.albumId !== albumId)
      this.persist()
    },
    /** 항목 목록 통째 교체. */
    replaceAll(items) {
      this.items = items
      this.persist()
    },
    clear() {
      this.items = []
      localStorage.removeItem(KEY)
    },
  },
})
