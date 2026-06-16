<script setup>
import { computed } from 'vue'

const props = defineProps({
  rating: { type: Number, default: null }, // averageRating, 리뷰 없으면 null
  count: { type: Number, default: 0 },
  showEmpty: { type: Boolean, default: true }, // 리뷰 없을 때 "리뷰 없음" 표시 여부
  showCount: { type: Boolean, default: true }, // 별 옆 (리뷰수) 표시 여부
})

const hasReviews = computed(() => props.rating != null && props.count > 0)
// 반올림한 정수 별 개수(0~5)
const filled = computed(() => Math.round(props.rating ?? 0))
const label = computed(() =>
  hasReviews.value ? `평점 ${props.rating.toFixed(1)} / 5, 리뷰 ${props.count}건` : '리뷰 없음',
)
</script>

<template>
  <div class="flex items-center gap-1 text-xs" :aria-label="label">
    <template v-if="hasReviews">
      <span class="flex" aria-hidden="true">
        <span
          v-for="n in 5"
          :key="n"
          :class="n <= filled ? 'text-gold-500' : 'text-vinyl-800/20'"
          >★</span
        >
      </span>
      <span class="text-vinyl-800/70">{{ rating.toFixed(1) }}</span>
      <span v-if="showCount" class="text-vinyl-800/50">({{ count }})</span>
    </template>
    <span v-else-if="showEmpty" class="text-vinyl-800/40">리뷰 없음</span>
  </div>
</template>
