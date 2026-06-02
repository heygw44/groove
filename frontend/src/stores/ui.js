import { defineStore } from 'pinia'

let toastSeq = 0

/** 전역 UI 상태 — 토스트 알림·검색어 등. (#113 골격, 확장은 후속 이슈) */
export const useUiStore = defineStore('ui', {
  state: () => ({
    toasts: [], // [{id, type, message}]
    searchKeyword: '',
  }),
  actions: {
    /**
     * 토스트 추가.
     * @param {string} message
     * @param {'info'|'success'|'error'} [type]
     * @param {number} [timeout] ms, 0 이면 자동 닫힘 없음
     */
    notify(message, type = 'info', timeout = 3000) {
      const id = ++toastSeq
      this.toasts.push({ id, type, message })
      if (timeout > 0) {
        setTimeout(() => this.dismiss(id), timeout)
      }
      return id
    },
    dismiss(id) {
      this.toasts = this.toasts.filter((t) => t.id !== id)
    },
    setSearchKeyword(keyword) {
      this.searchKeyword = keyword
    },
  },
})
