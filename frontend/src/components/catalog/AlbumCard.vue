<script setup>
import { formatWon } from '@/lib/format'
import RatingStars from './RatingStars.vue'

defineProps({
  album: { type: Object, required: true },
})
</script>

<template>
  <RouterLink
    :to="{ name: 'album-detail', params: { id: album.id } }"
    class="group block overflow-hidden rounded-xl border border-vinyl-800/10 bg-cream-50 transition hover:shadow-lg"
  >
    <div class="relative aspect-square overflow-hidden bg-vinyl-900">
      <img
        v-if="album.coverImageUrl"
        :src="album.coverImageUrl"
        :alt="album.title"
        class="h-full w-full object-cover transition group-hover:scale-105"
        loading="lazy"
      />
      <span
        v-if="album.isLimited"
        class="absolute left-2 top-2 rounded-full bg-gold-500 px-2 py-0.5 text-[0.65rem] font-bold uppercase tracking-wide text-vinyl-black"
      >
        한정반
      </span>
      <span
        v-if="album.status === 'SOLD_OUT'"
        class="absolute inset-0 flex items-center justify-center bg-vinyl-black/60 text-sm font-semibold text-cream-50"
      >
        품절
      </span>
    </div>
    <div class="p-3">
      <p class="truncate text-sm font-medium text-vinyl-black">{{ album.title }}</p>
      <p class="truncate text-xs text-vinyl-800/60">{{ album.artist?.name }}</p>
      <p class="mt-1 text-sm font-semibold text-rust-600">{{ formatWon(album.price) }}</p>
      <RatingStars class="mt-1" :rating="album.averageRating" :count="album.reviewCount" />
    </div>
  </RouterLink>
</template>
