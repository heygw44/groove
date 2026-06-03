<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { demoLogin } from '@/api/auth'
import { listCoupons, issueCouponWithToken } from '@/api/coupons'
import { errorMessage } from '@/lib/problem-detail'
import { couponDiscountLabel, classifyCouponIssueError } from '@/lib/order-enums'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@/components/base/BaseButton.vue'

// 로컬 시드 데모 전용 계정 풀(demo01~demo30@groove.dev). 운영 데이터 아님.
// 각 계정 토큰으로 동시에 발급을 발사하며, 전역 로그인 세션은 건드리지 않는다(issueCouponWithToken).
const DEMO_PASSWORD = 'demo1234'
const DEMO_ACCOUNTS = Array.from(
  { length: 30 },
  (_, i) => `demo${String(i + 1).padStart(2, '0')}@groove.dev`,
)

// 발사 대상 쿠폰 선택.
const coupons = ref([])
const selectedCouponId = ref('')
const couponOptions = computed(() => [
  { value: '', label: '쿠폰을 선택하세요' },
  ...coupons.value.map((c) => ({
    value: c.couponId,
    label:
      `${c.name} — ${couponDiscountLabel(c)}` +
      (c.remainingQuantity != null ? ` (${c.remainingQuantity}장 남음)` : ''),
  })),
])

// 계정별 결과 행 — reactive 라 단계 전이(로그인중→발급중→결과)가 즉시 표에 반영된다.
const rows = reactive(
  DEMO_ACCOUNTS.map((email) => ({
    email,
    status: 'idle', // idle | logging-in | issuing | done
    outcome: null, // SUCCESS | SOLD_OUT | ALREADY | RATE_LIMIT | NOT_ISSUABLE | ERROR
    message: '',
    elapsedMs: null,
    token: null,
  })),
)
const running = ref(false)

const summary = computed(() => {
  const s = { SUCCESS: 0, SOLD_OUT: 0, ALREADY: 0, RATE_LIMIT: 0, NOT_ISSUABLE: 0, ERROR: 0 }
  for (const r of rows) if (r.outcome) s[r.outcome] += 1
  return s
})

onMounted(async () => {
  try {
    const res = await listCoupons({ size: 20 })
    coupons.value = res.content ?? []
  } catch {
    coupons.value = [] // 목록 로드 실패는 무시 — 선택 옵션만 비게 둔다.
  }
})

// 발급 실패 outcome → 데모 표의 기술 라벨(사유 칸). 분류는 classifyCouponIssueError 공유 헬퍼가 담당.
const RACE_OUTCOME_MESSAGE = {
  SOLD_OUT: '소진 (409)',
  ALREADY: '중복 발급 (409)',
  RATE_LIMIT: 'rate-limit (429)',
  NOT_ISSUABLE: '발급 불가 (422)',
}

async function onFire() {
  if (!selectedCouponId.value || running.value) return
  running.value = true
  // 진행 중 동기 예외가 나도 발사 버튼이 영구 비활성되지 않도록 finally 로 running 을 반드시 해제한다.
  try {
    for (const r of rows) {
      r.status = 'idle'
      r.outcome = null
      r.message = ''
      r.elapsedMs = null
      r.token = null
    }

    // 1) 병렬 로그인 — 토큰만 받고 전역 store 에 저장하지 않는다.
    await Promise.allSettled(
      rows.map(async (r) => {
        r.status = 'logging-in'
        try {
          const res = await demoLogin(r.email, DEMO_PASSWORD)
          r.token = res.accessToken
        } catch {
          r.status = 'done'
          r.outcome = 'ERROR'
          r.message = '로그인 실패'
        }
      }),
    )

    // 2) 병렬 발급 — 토큰 확보 계정만 동시에 발사한다(선착순 경합).
    await Promise.allSettled(
      rows
        .filter((r) => r.token)
        .map(async (r) => {
          r.status = 'issuing'
          const t0 = performance.now()
          try {
            await issueCouponWithToken(selectedCouponId.value, r.token)
            r.outcome = 'SUCCESS'
            r.message = '발급 성공 (201)'
          } catch (e) {
            const outcome = classifyCouponIssueError(e)
            r.outcome = outcome
            r.message = outcome === 'ERROR' ? errorMessage(e, '오류') : RACE_OUTCOME_MESSAGE[outcome]
          } finally {
            r.elapsedMs = Math.round(performance.now() - t0)
            r.status = 'done'
          }
        }),
    )
  } finally {
    running.value = false
  }
}

const OUTCOME_LABEL = {
  SUCCESS: '성공',
  SOLD_OUT: '소진',
  ALREADY: '중복',
  RATE_LIMIT: 'rate-limit',
  NOT_ISSUABLE: '발급불가',
  ERROR: '오류',
}

function outcomeClass(outcome) {
  if (outcome === 'SUCCESS') return 'bg-gold-500 text-vinyl-black'
  if (outcome === 'RATE_LIMIT' || outcome === 'ERROR') return 'bg-rust-500/15 text-rust-600'
  return 'bg-vinyl-800/10 text-vinyl-800/60' // 소진·중복·발급불가.
}

function statusLabel(r) {
  if (r.status === 'logging-in') return '로그인 중…'
  if (r.status === 'issuing') return '발급 중…'
  if (r.outcome) return OUTCOME_LABEL[r.outcome] || '완료'
  return '대기'
}
</script>

<template>
  <section class="mx-auto max-w-3xl py-8">
    <h1 class="mb-1 font-display text-2xl font-bold text-vinyl-black">쿠폰 동시성 라이브 데모</h1>
    <p class="mb-4 text-sm">
      <RouterLink :to="{ name: 'coupons' }" class="text-rust-600 hover:underline">
        ← 쿠폰 목록
      </RouterLink>
    </p>

    <!-- 안내 배너 -->
    <div
      class="mb-6 space-y-2 rounded-lg border border-gold-400/40 bg-gold-500/10 px-4 py-3 text-sm text-vinyl-800"
    >
      <p>
        <strong>demo01~30@groove.dev</strong> 로컬 시드 계정 30개로 동시에 발급을 발사합니다. 한정
        수량 쿠폰이면 선착순 경합으로 일부만 성공합니다(예: 20장 한정 → 성공 20 · 실패 10).
      </p>
      <p class="text-xs text-vinyl-800/70">
        rate-limit(회원당 분당 10회)에 막히면 429로 분류됩니다. 데모 전
        <code class="rounded bg-vinyl-800/10 px-1">COUPON_RATE_LIMIT_ISSUE_CAPACITY</code>를 30 이상으로
        상향하세요. 이 데모는 전역 로그인 세션에 영향을 주지 않습니다.
      </p>
    </div>

    <!-- 발사 컨트롤 -->
    <div class="mb-6 flex flex-wrap items-end gap-3">
      <div class="min-w-0 flex-1">
        <BaseSelect v-model="selectedCouponId" :options="couponOptions" label="발사 대상 쿠폰" />
      </div>
      <BaseButton :loading="running" :disabled="!selectedCouponId" @click="onFire">
        동시 발급 발사 (30)
      </BaseButton>
    </div>

    <!-- 집계 패널 -->
    <div class="mb-6 grid grid-cols-3 gap-2 sm:grid-cols-6">
      <div class="rounded-lg bg-gold-500/15 px-3 py-2 text-center">
        <p class="text-lg font-bold text-vinyl-black">{{ summary.SUCCESS }}</p>
        <p class="text-xs text-vinyl-800/60">성공</p>
      </div>
      <div class="rounded-lg bg-cream-100 px-3 py-2 text-center">
        <p class="text-lg font-bold text-vinyl-black">{{ summary.SOLD_OUT }}</p>
        <p class="text-xs text-vinyl-800/60">소진</p>
      </div>
      <div class="rounded-lg bg-cream-100 px-3 py-2 text-center">
        <p class="text-lg font-bold text-vinyl-black">{{ summary.ALREADY }}</p>
        <p class="text-xs text-vinyl-800/60">중복</p>
      </div>
      <div class="rounded-lg bg-rust-500/10 px-3 py-2 text-center">
        <p class="text-lg font-bold text-rust-600">{{ summary.RATE_LIMIT }}</p>
        <p class="text-xs text-vinyl-800/60">rate-limit</p>
      </div>
      <div class="rounded-lg bg-cream-100 px-3 py-2 text-center">
        <p class="text-lg font-bold text-vinyl-black">{{ summary.NOT_ISSUABLE }}</p>
        <p class="text-xs text-vinyl-800/60">발급불가</p>
      </div>
      <div class="rounded-lg bg-rust-500/10 px-3 py-2 text-center">
        <p class="text-lg font-bold text-rust-600">{{ summary.ERROR }}</p>
        <p class="text-xs text-vinyl-800/60">오류</p>
      </div>
    </div>

    <!-- 결과 표 (라이브) -->
    <div class="overflow-hidden rounded-lg border border-vinyl-800/15">
      <table class="w-full text-sm">
        <thead class="bg-cream-100 text-left text-xs text-vinyl-800/60">
          <tr>
            <th class="px-4 py-2 font-medium">계정</th>
            <th class="px-4 py-2 font-medium">결과</th>
            <th class="px-4 py-2 font-medium">사유</th>
            <th class="px-4 py-2 text-right font-medium">소요</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-vinyl-800/10 bg-cream-50">
          <tr v-for="r in rows" :key="r.email">
            <td class="px-4 py-2 font-mono text-xs text-vinyl-800">{{ r.email }}</td>
            <td class="px-4 py-2">
              <span
                v-if="r.outcome"
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="outcomeClass(r.outcome)"
              >
                {{ statusLabel(r) }}
              </span>
              <span v-else class="text-xs text-vinyl-800/50">{{ statusLabel(r) }}</span>
            </td>
            <td class="px-4 py-2 text-xs text-vinyl-800/60">{{ r.message }}</td>
            <td class="px-4 py-2 text-right text-xs text-vinyl-800/50">
              {{ r.elapsedMs != null ? `${r.elapsedMs}ms` : '—' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
