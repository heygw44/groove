<script setup>
import { ref, onMounted } from 'vue'
import * as albumsApi from '@/api/albums'
import { errorMessage } from '@/lib/problem-detail'
import AlbumGrid from '@/components/catalog/AlbumGrid.vue'

const albums = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const page = await albumsApi.list({ page: 0, size: 8, sort: 'createdAt,desc' })
    albums.value = page.content
  } catch (e) {
    error.value = errorMessage(e, '앨범을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-10">
    <!-- 쿠폰 배너 (시드 쿠폰 홍보 — 발급 플로우는 후속 이슈) -->
    <section
      class="flex flex-col items-start gap-3 rounded-2xl bg-vinyl-black px-6 py-8 text-cream-50 sm:flex-row sm:items-center sm:justify-between"
    >
      <div>
        <p class="font-display text-xl font-bold text-gold-400">첫 구매 5,000원 쿠폰</p>
        <p class="mt-1 text-sm text-cream-200/80">첫 주문에 바로 적용 — 지금 바로 카탈로그를 둘러보세요.</p>
      </div>
      <RouterLink
        to="/catalog"
        class="shrink-0 rounded-full bg-gold-500 px-5 py-2 text-sm font-medium text-vinyl-black hover:bg-gold-400"
      >
        쇼핑하러 가기
      </RouterLink>
    </section>

    <!-- 신상 LP 8개 -->
    <section>
      <div class="mb-6 flex items-baseline justify-between">
        <div>
          <h1 class="font-display text-2xl font-bold text-vinyl-black">신상 LP</h1>
          <p class="mt-1 text-sm text-vinyl-800/70">갓 입고된 레코드를 만나보세요.</p>
        </div>
        <RouterLink to="/catalog" class="shrink-0 text-sm text-rust-600 hover:underline"
          >더 보기 →</RouterLink
        >
      </div>

      <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
        {{ error }}
      </p>
      <AlbumGrid
        v-else
        :albums="albums"
        :loading="loading"
        :skeleton-count="8"
        empty-text="아직 등록된 앨범이 없습니다."
      />
    </section>
  </div>
</template>
