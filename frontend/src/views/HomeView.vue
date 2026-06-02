<script setup>
import { ref, onMounted } from 'vue'
import * as albumsApi from '@/api/albums'
import { ApiError } from '@/lib/problem-detail'
import { formatWon } from '@/lib/format'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const albums = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const page = await albumsApi.list({ page: 0, size: 12 })
    albums.value = page.content
  } catch (e) {
    error.value = e instanceof ApiError ? e.detail || e.title : '앨범을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section>
    <h1 class="mb-1 font-display text-2xl font-bold text-vinyl-black">신상 LP</h1>
    <p class="mb-6 text-sm text-vinyl-800/70">갓 입고된 레코드를 만나보세요.</p>

    <div v-if="loading" class="flex justify-center py-20">
      <BaseSpinner size="lg" />
    </div>

    <p v-else-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
      {{ error }}
    </p>

    <p v-else-if="albums.length === 0" class="py-20 text-center text-sm text-vinyl-800/60">
      아직 등록된 앨범이 없습니다.
    </p>

    <ul v-else class="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4">
      <li
        v-for="album in albums"
        :key="album.id"
        class="group overflow-hidden rounded-xl border border-vinyl-800/10 bg-cream-50 transition hover:shadow-lg"
      >
        <div class="aspect-square overflow-hidden bg-vinyl-900">
          <img
            v-if="album.coverImageUrl"
            :src="album.coverImageUrl"
            :alt="album.title"
            class="h-full w-full object-cover transition group-hover:scale-105"
            loading="lazy"
          />
        </div>
        <div class="p-3">
          <p class="truncate text-sm font-medium text-vinyl-black">{{ album.title }}</p>
          <p class="truncate text-xs text-vinyl-800/60">{{ album.artist?.name }}</p>
          <p class="mt-1 text-sm font-semibold text-rust-600">{{ formatWon(album.price) }}</p>
        </div>
      </li>
    </ul>
  </section>
</template>
