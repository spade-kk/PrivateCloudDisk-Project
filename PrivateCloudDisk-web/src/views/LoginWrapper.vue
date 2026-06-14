<template>
  <AlreadyLoggedInView v-if="auth.isLoggedIn" />
  <LoginView v-else />
</template>

<script setup>
import { useAuthStore } from '@/stores/authStore'
import AlreadyLoggedInView from '@/views/website/AlreadyLoggedInView.vue'
import LoginView from '@/views/LoginView.vue'

const auth = useAuthStore()
</script>