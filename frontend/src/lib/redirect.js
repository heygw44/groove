/**
 * open-redirect 방지 — 같은 오리진 절대경로('/x')만 허용하고 그 외엔 null.
 *
 * 차단 대상: 비문자열, '/'로 시작하지 않는 값, '//'(protocol-relative),
 * 백슬래시 포함('/\\evil.com' 등 — 브라우저가 '\\'를 '/'로 정규화해 '//' 우회가 될 수 있음).
 * 라우터 가드와 로그인 후 복귀가 동일 규칙을 쓰도록 단일 소스로 둔다.
 */
export function safeRedirect(target) {
  if (typeof target !== 'string') return null
  if (!target.startsWith('/') || target.startsWith('//')) return null
  if (target.includes('\\')) return null
  return target
}
