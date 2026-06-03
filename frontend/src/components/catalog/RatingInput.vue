<script setup>
import { ref, computed } from 'vue'

// 별점 입력 — 표시용 RatingStars 와 시각 일관(★, gold-500/vinyl-800/20). 입력은 좀 더 크게(text-2xl).
// v-model 규약은 BaseInput 과 동일하게 modelValue prop + update:modelValue emit 을 쓴다.
const props = defineProps({
  modelValue: { type: Number, default: 0 }, // 0 = 미선택, 1~5
})
const emit = defineEmits(['update:modelValue'])

const hover = ref(0) // 호버 미리보기(0 = 호버 없음)
const display = computed(() => hover.value || props.modelValue) // 호버 우선, 없으면 선택값
const starRefs = ref([])

function select(n) {
  emit('update:modelValue', n)
}

// radiogroup roving — 좌우/상하 화살표로 선택 이동 후 해당 별에 포커스.
// 이동 기준은 현재 포커스(=roving tabindex)가 놓인 별: 미선택이면 1번 별로 통일(양방향 대칭).
function onKey(e) {
  const cur = props.modelValue || 1
  let next = null
  if (e.key === 'ArrowRight' || e.key === 'ArrowUp') next = Math.min(5, cur + 1)
  else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') next = Math.max(1, cur - 1)
  else return
  e.preventDefault()
  select(next)
  starRefs.value[next - 1]?.focus()
}
</script>

<template>
  <div
    class="flex items-center gap-0.5 text-2xl leading-none"
    role="radiogroup"
    aria-label="평점 선택 (1~5점)"
    @mouseleave="hover = 0"
  >
    <button
      v-for="n in 5"
      :key="n"
      :ref="(el) => { starRefs[n - 1] = el }"
      type="button"
      role="radio"
      :aria-checked="modelValue === n"
      :aria-label="`${n}점`"
      :tabindex="n === (modelValue || 1) ? 0 : -1"
      class="cursor-pointer rounded transition focus:outline-hidden focus-visible:ring-2 focus-visible:ring-gold-400"
      :class="n <= display ? 'text-gold-500' : 'text-vinyl-800/20'"
      @click="select(n)"
      @mouseenter="hover = n"
      @keydown="onKey"
    >
      ★
    </button>
  </div>
</template>
