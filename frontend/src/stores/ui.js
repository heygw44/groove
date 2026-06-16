import { defineStore } from 'pinia'

let toastSeq = 0

/** 전역 UI 상태 — 토스트·검색어 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    toasts: [], // [{id, type, message}]
  }),
  actions: {
    /** 토스트 추가. type=info|success|error, timeout(ms)=0 이면 자동 닫힘 없음. */
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
  },
})
