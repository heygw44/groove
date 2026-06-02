<script setup>
import AlbumCard from './AlbumCard.vue'
import SkeletonCard from './SkeletonCard.vue'

defineProps({
  albums: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  skeletonCount: { type: Number, default: 8 },
  emptyText: { type: String, default: '조건에 맞는 앨범이 없습니다.' },
})
</script>

<template>
  <div v-if="loading" class="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4">
    <SkeletonCard v-for="n in skeletonCount" :key="n" />
  </div>

  <p v-else-if="albums.length === 0" class="py-20 text-center text-sm text-vinyl-800/60">
    {{ emptyText }}
  </p>

  <ul v-else class="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4">
    <li v-for="album in albums" :key="album.id">
      <AlbumCard :album="album" />
    </li>
  </ul>
</template>
