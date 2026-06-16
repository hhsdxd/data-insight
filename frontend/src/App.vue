<script setup>
import { watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

watch(() => localStorage.getItem('token'), (t) => {
  if (!t && route.path !== '/login') router.push('/login')
}, { immediate: true })
</script>

<template>
  <router-view />
</template>
