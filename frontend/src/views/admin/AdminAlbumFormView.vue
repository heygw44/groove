<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useForm } from '@/composables/useForm'
import { detail } from '@/api/albums'
import { list as listArtists } from '@/api/artists'
import { createAlbum, updateAlbum } from '@/api/admin'
import { genres as fetchGenres, labels as fetchLabels } from '@/api/taxonomy'
import { ApiError, errorMessage } from '@/lib/problem-detail'
import { ALBUM_STATUS_OPTIONS, ALBUM_FORMAT_OPTIONS } from '@/lib/admin-enums'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseSpinner from '@/components/base/BaseSpinner.vue'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const id = computed(() => route.params.id)
const isEdit = computed(() => !!id.value)

function createDefaults() {
  return {
    title: '',
    artistId: '',
    genreId: '',
    labelId: '', // '' = 없음
    releaseYear: new Date().getFullYear(),
    format: 'LP_12',
    price: 0,
    stock: 0, // 생성에만 사용 — 수정 시 재고는 목록의 재고 조정 전용.
    status: 'SELLING',
    isLimited: false,
    coverImageUrl: '',
    description: '',
  }
}

const form = reactive(createDefaults())

const artistOptions = ref([{ value: '', label: '선택' }])
const genreOptions = ref([{ value: '', label: '선택' }])
const labelOptions = ref([{ value: '', label: '없음' }])

const loading = ref(true)
const loadError = ref('')

// 드롭다운 옵션은 인스턴스 수명당 한 번만 로드한다(라우트 재사용 시 중복 호출 방지).
let taxonomyLoaded = false
let artistList = []
let genreList = []

async function loadTaxonomy() {
  const [artists, genres, labels] = await Promise.all([
    listArtists({ size: 100 }).then((res) => res.content),
    fetchGenres(),
    fetchLabels(),
  ])
  artistList = artists
  genreList = genres
  artistOptions.value = [{ value: '', label: '선택' }, ...artists.map((a) => ({ value: a.id, label: a.name }))]
  genreOptions.value = [{ value: '', label: '선택' }, ...genres.map((g) => ({ value: g.id, label: g.name }))]
  labelOptions.value = [{ value: '', label: '없음' }, ...labels.map((l) => ({ value: l.id, label: l.name }))]
}

async function loadAlbum() {
  if (isEdit.value) {
    const a = await detail(id.value)
    Object.assign(form, {
      title: a.title,
      artistId: a.artist?.id ?? '',
      genreId: a.genre?.id ?? '',
      labelId: a.label?.id ?? '',
      releaseYear: a.releaseYear,
      format: a.format,
      price: a.price,
      status: a.status,
      isLimited: a.isLimited,
      coverImageUrl: a.coverImageUrl ?? '',
      description: a.description ?? '',
    })
  } else {
    // 생성: 폼을 기본값으로 리셋(인스턴스 재사용 시 직전 수정값 잔류 방지) + 첫 아티스트/장르 자동 선택.
    Object.assign(form, createDefaults())
    if (artistList.length) form.artistId = artistList[0].id
    if (genreList.length) form.genreId = genreList[0].id
  }
}

// id(라우트 파라미터)에 반응해 재로드 — 같은 컴포넌트 인스턴스가 new↔edit·edit/1↔edit/2 로 재사용돼도
// 항상 현재 URL 의 앨범을 로드/저장한다(stale 폼으로 다른 앨범을 덮어쓰지 않게).
watch(
  id,
  async () => {
    loading.value = true
    loadError.value = ''
    try {
      if (!taxonomyLoaded) {
        await loadTaxonomy()
        taxonomyLoaded = true
      }
      await loadAlbum()
    } catch (e) {
      loadError.value = errorMessage(e, '폼 데이터를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

// 필수값 클라이언트 검증 — 빈 숫자 필드('')나 미선택(null)이 서버에 그대로 가면 violation 없는 일반 400 으로
// 떨어져 폼이 필드별 에러를 못 띄운다. 사전에 violations 를 만들어 useForm 의 필드 에러 매핑에 위임한다.
function clientViolations() {
  const v = []
  if (!form.title.trim()) v.push({ field: 'title', message: '제목을 입력해 주세요.' })
  if (form.artistId === '') v.push({ field: 'artistId', message: '아티스트를 선택해 주세요.' })
  if (form.genreId === '') v.push({ field: 'genreId', message: '장르를 선택해 주세요.' })
  if (form.releaseYear === '') v.push({ field: 'releaseYear', message: '발매 연도를 입력해 주세요.' })
  if (form.price === '') v.push({ field: 'price', message: '가격을 입력해 주세요.' })
  if (!isEdit.value && form.stock === '') v.push({ field: 'stock', message: '초기 재고를 입력해 주세요.' })
  return v
}

const { errors, formError, submitting, submit, clearError } = useForm(async () => {
  const violations = clientViolations()
  if (violations.length) throw new ApiError({ status: 400, violations })

  const body = {
    title: form.title,
    artistId: form.artistId,
    genreId: form.genreId,
    labelId: form.labelId === '' ? null : form.labelId,
    releaseYear: form.releaseYear,
    format: form.format,
    price: form.price,
    status: form.status,
    isLimited: form.isLimited,
    coverImageUrl: form.coverImageUrl === '' ? null : form.coverImageUrl,
    description: form.description === '' ? null : form.description,
  }
  if (!isEdit.value) body.stock = form.stock
  if (isEdit.value) await updateAlbum(id.value, body)
  else await createAlbum(body)
})

async function handleSubmit() {
  const ok = await submit()
  if (ok) {
    ui.notify(isEdit.value ? '앨범을 수정했습니다.' : '앨범을 등록했습니다.', 'success')
    router.push({ name: 'admin-albums' })
  }
}
</script>

<template>
  <section class="max-w-2xl">
    <div class="mb-6 flex items-center gap-2 text-sm">
      <RouterLink :to="{ name: 'admin-albums' }" class="text-vinyl-800/60 hover:text-rust-600">앨범 관리</RouterLink>
      <span class="text-vinyl-800/40">/</span>
      <span class="font-medium text-vinyl-black">{{ isEdit ? '앨범 수정' : '새 앨범' }}</span>
    </div>

    <div v-if="loading" class="flex justify-center py-24"><BaseSpinner size="lg" /></div>
    <p v-else-if="loadError" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">{{ loadError }}</p>

    <form v-else class="space-y-4" @submit.prevent="handleSubmit">
      <p v-if="formError" role="alert" class="rounded-lg bg-rust-500/10 px-4 py-3 text-sm text-rust-600">
        {{ formError }}
      </p>

      <BaseInput
        v-model="form.title"
        label="제목"
        :error="errors.title"
        @update:model-value="clearError('title')"
      />

      <div class="grid grid-cols-2 gap-4">
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">아티스트</span>
          <BaseSelect v-model="form.artistId" :options="artistOptions" aria-label="아티스트" />
          <span v-if="errors.artistId" class="mt-1 block text-xs text-rust-600">{{ errors.artistId }}</span>
        </label>
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">장르</span>
          <BaseSelect v-model="form.genreId" :options="genreOptions" aria-label="장르" />
          <span v-if="errors.genreId" class="mt-1 block text-xs text-rust-600">{{ errors.genreId }}</span>
        </label>
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">레이블</span>
          <BaseSelect v-model="form.labelId" :options="labelOptions" aria-label="레이블" />
        </label>
        <BaseInput
          v-model="form.releaseYear"
          type="number"
          label="발매 연도"
          :error="errors.releaseYear"
          @update:model-value="clearError('releaseYear')"
        />
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">포맷</span>
          <BaseSelect v-model="form.format" :options="ALBUM_FORMAT_OPTIONS" aria-label="포맷" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm font-medium text-vinyl-800">상태</span>
          <BaseSelect v-model="form.status" :options="ALBUM_STATUS_OPTIONS" aria-label="상태" />
        </label>
        <BaseInput
          v-model="form.price"
          type="number"
          label="가격 (원)"
          :error="errors.price"
          @update:model-value="clearError('price')"
        />
        <BaseInput
          v-if="!isEdit"
          v-model="form.stock"
          type="number"
          label="초기 재고"
          :error="errors.stock"
          @update:model-value="clearError('stock')"
        />
      </div>

      <label class="flex items-center gap-2 text-sm text-vinyl-800">
        <input v-model="form.isLimited" type="checkbox" class="h-4 w-4 accent-gold-500" />
        한정반
      </label>

      <BaseInput
        v-model="form.coverImageUrl"
        label="커버 이미지 URL (선택)"
        placeholder="https://..."
        :error="errors.coverImageUrl"
        @update:model-value="clearError('coverImageUrl')"
      />

      <label class="block">
        <span class="mb-1 block text-sm font-medium text-vinyl-800">설명 (선택)</span>
        <textarea
          v-model="form.description"
          rows="4"
          class="w-full rounded-lg border border-vinyl-800/20 bg-cream-50 px-3 py-2 text-sm text-vinyl-black focus:outline-hidden focus:ring-2 focus:ring-gold-400"
          @input="clearError('description')"
        ></textarea>
        <span v-if="errors.description" class="mt-1 block text-xs text-rust-600">{{ errors.description }}</span>
      </label>

      <div class="flex gap-3 pt-2">
        <BaseButton type="submit" :loading="submitting">{{ isEdit ? '수정 저장' : '앨범 등록' }}</BaseButton>
        <RouterLink
          :to="{ name: 'admin-albums' }"
          class="inline-flex items-center rounded-full border border-vinyl-800/20 px-5 py-2 text-sm font-medium text-vinyl-black transition hover:bg-cream-100"
        >
          취소
        </RouterLink>
      </div>
    </form>
  </section>
</template>
