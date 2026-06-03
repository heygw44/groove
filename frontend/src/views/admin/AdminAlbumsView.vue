<script setup>
import { ref, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useRouteQuery } from '@/composables/useRouteQuery'
import { listAlbums, adjustStock, deleteAlbum } from '@/api/admin'
import { firstStr, pageParam } from '@/lib/query'
import { formatWon } from '@/lib/format'
import { formatLabel, statusLabel } from '@/lib/enums'
import { ADMIN_ALBUM_STATUS_FILTER_OPTIONS, adminErrorMessage } from '@/lib/admin-enums'
import Pagination from '@/components/Pagination.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const ui = useUiStore()
const { patchQuery } = useRouteQuery()

const page = ref(null)
const loading = ref(true)
const error = ref('')
const deltas = reactive({}) // { [albumId]: number } 인라인 재고 조정 입력
const busyId = ref(null) // 재고 조정/삭제 중인 앨범 — 행별 버튼 가드

const PAGE_SIZE = 10
let reqSeq = 0

function buildParams(q) {
  const params = { size: PAGE_SIZE }
  const status = firstStr(q.status)
  if (status !== '') params.status = status
  const p = pageParam(q)
  if (p) params.page = p
  return params
}

async function fetchAlbums(q, { silent = false } = {}) {
  const seq = ++reqSeq
  if (!silent) loading.value = true
  error.value = ''
  try {
    const res = await listAlbums(buildParams(q))
    if (seq !== reqSeq) return
    page.value = res
  } catch (e) {
    if (seq !== reqSeq) return
    error.value = adminErrorMessage(e, '앨범 목록을 불러오지 못했습니다.')
    page.value = null
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

function onStatusChange(value) {
  patchQuery({ status: value, page: undefined })
}

async function onAdjustStock(album) {
  const delta = deltas[album.id]
  if (!delta) return // 0/빈값은 무시
  busyId.value = album.id
  try {
    await adjustStock(album.id, delta)
    ui.notify(`재고를 ${delta > 0 ? '+' : ''}${delta} 조정했습니다.`, 'success')
    deltas[album.id] = ''
    fetchAlbums(route.query, { silent: true }) // 재고/상태(SOLD_OUT) 갱신.
  } catch (e) {
    ui.notify(adminErrorMessage(e, '재고 조정에 실패했습니다.'), 'error')
  } finally {
    busyId.value = null
  }
}

async function onDelete(album) {
  if (!window.confirm(`'${album.title}' 앨범을 삭제할까요? 되돌릴 수 없습니다.`)) return
  busyId.value = album.id
  try {
    await deleteAlbum(album.id)
    ui.notify('앨범을 삭제했습니다.', 'success')
    fetchAlbums(route.query, { silent: true })
  } catch (e) {
    ui.notify(adminErrorMessage(e, '앨범 삭제에 실패했습니다.'), 'error')
  } finally {
    busyId.value = null
  }
}

watch(() => route.query, fetchAlbums, { immediate: true })
</script>

<template>
  <section>
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 class="font-display text-2xl font-bold text-vinyl-black">앨범 관리</h1>
      <RouterLink
        :to="{ name: 'admin-album-new' }"
        class="rounded-full bg-gold-500 px-5 py-2 text-sm font-medium text-vinyl-black transition hover:bg-gold-400"
      >
        + 새 앨범
      </RouterLink>
    </div>

    <div class="mb-4 max-w-xs">
      <BaseSelect
        :model-value="firstStr(route.query.status)"
        :options="ADMIN_ALBUM_STATUS_FILTER_OPTIONS"
        aria-label="앨범 상태 필터"
        @update:model-value="onStatusChange"
      />
    </div>

    <p v-if="error" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ error }}</p>

    <template v-else>
      <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>

      <div
        v-else-if="!page || !page.content.length"
        class="rounded-lg bg-cream-100 px-4 py-12 text-center text-sm text-vinyl-800/60"
      >
        앨범이 없습니다.
      </div>

      <div v-else class="overflow-x-auto rounded-lg border border-vinyl-800/15 bg-cream-50">
        <table class="w-full min-w-[48rem] text-sm">
          <thead class="border-b border-vinyl-800/15 text-left text-xs text-vinyl-800/60">
            <tr>
              <th class="px-4 py-3 font-medium">앨범</th>
              <th class="px-4 py-3 font-medium">포맷</th>
              <th class="px-4 py-3 font-medium">가격</th>
              <th class="px-4 py-3 font-medium">재고</th>
              <th class="px-4 py-3 font-medium">상태</th>
              <th class="px-4 py-3 text-right font-medium">작업</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-vinyl-800/10">
            <tr v-for="a in page.content" :key="a.id">
              <td class="px-4 py-3">
                <div class="flex items-center gap-3">
                  <img
                    v-if="a.coverImageUrl"
                    :src="a.coverImageUrl"
                    alt=""
                    class="h-10 w-10 shrink-0 rounded object-cover"
                  />
                  <div class="min-w-0">
                    <p class="truncate font-medium text-vinyl-black">{{ a.title }}</p>
                    <p class="truncate text-xs text-vinyl-800/50">{{ a.artist?.name }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3 text-vinyl-800/70">{{ formatLabel(a.format) }}</td>
              <td class="px-4 py-3 text-vinyl-800/70">{{ formatWon(a.price) }}</td>
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <span class="w-8 font-medium text-vinyl-black">{{ a.stock }}</span>
                  <input
                    v-model.number="deltas[a.id]"
                    type="number"
                    placeholder="±"
                    class="w-16 rounded border border-vinyl-800/20 bg-cream-50 px-2 py-1 text-xs focus:outline-hidden focus:ring-2 focus:ring-gold-400"
                  />
                  <button
                    type="button"
                    class="rounded border border-vinyl-800/20 px-2 py-1 text-xs transition hover:bg-cream-100 disabled:opacity-40"
                    :disabled="busyId === a.id || !deltas[a.id]"
                    @click="onAdjustStock(a)"
                  >
                    적용
                  </button>
                </div>
              </td>
              <td class="px-4 py-3">
                <span
                  class="rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="
                    a.status === 'HIDDEN'
                      ? 'bg-vinyl-800/10 text-vinyl-800/70'
                      : a.status === 'SOLD_OUT'
                        ? 'bg-rust-500/10 text-rust-600'
                        : 'bg-gold-400/20 text-vinyl-black'
                  "
                >
                  {{ statusLabel(a.status) }}
                </span>
              </td>
              <td class="px-4 py-3">
                <div class="flex items-center justify-end gap-2 text-xs">
                  <RouterLink
                    :to="{ name: 'admin-album-edit', params: { id: a.id } }"
                    class="rounded border border-vinyl-800/20 px-2 py-1 transition hover:bg-cream-100"
                  >
                    수정
                  </RouterLink>
                  <button
                    type="button"
                    class="rounded border border-rust-500/40 px-2 py-1 text-rust-600 transition hover:bg-rust-500/10 disabled:opacity-40"
                    :disabled="busyId === a.id"
                    @click="onDelete(a)"
                  >
                    삭제
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination v-if="page" :page="page" />
    </template>
  </section>
</template>
