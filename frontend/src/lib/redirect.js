/** open-redirect 방지 — 같은 오리진 절대경로('/x')만 허용, 그 외 null. */
export function safeRedirect(target) {
  if (typeof target !== 'string') return null
  if (!target.startsWith('/') || target.startsWith('//')) return null
  if (target.includes('\\')) return null
  return target
}
