<template>
  <div class="flex min-h-screen flex-col bg-white">
    <PublicHeader />
    <main class="flex-1 pt-16 sm:pt-18">
      <router-view v-slot="{ Component, route: viewRoute }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" :key="viewRoute.fullPath" />
        </transition>
      </router-view>
    </main>
    <PublicFooter />
    <BackToTop />
    <CookieConsent />
  </div>
</template>

<script setup lang="ts">
import PublicHeader from './PublicHeader.vue'
import PublicFooter from './PublicFooter.vue'
import BackToTop from './BackToTop.vue'
import CookieConsent from './CookieConsent.vue'
</script>

<style scoped>
.page-fade-enter-active,
.page-fade-leave-active {
  transition: all 0.2s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>