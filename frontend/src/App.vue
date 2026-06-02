<script setup>
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
import ToastHost from '@/components/ToastHost.vue'
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <TheHeader />
    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
      <RouterView />
    </main>
    <TheFooter />
    <ToastHost />
  </div>
</template>
