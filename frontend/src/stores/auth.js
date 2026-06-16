import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('token') || '')

  async function login(username, password) {
    const res = await api.login({ username, password })
    token.value = res.data.token
    localStorage.setItem('token', token.value)
    await fetchUser()
  }

  async function fetchUser() {
    try {
      const res = await api.getUserInfo()
      user.value = res.data
    } catch { /* ignore */ }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  return { user, token, login, fetchUser, logout }
})
