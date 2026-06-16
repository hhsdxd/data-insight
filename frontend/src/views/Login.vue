<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const isLogin = ref(true)
const form = ref({ username: '', password: '', email: '' })
const loading = ref(false)
const msg = ref('')

async function submit() {
  loading.value = true; msg.value = ''
  try {
    const { data } = await axios.post('/api/user/' + (isLogin.value ? 'login' : 'register'), form.value)
    localStorage.setItem('token', data.data.token)
    router.push('/dashboard')
  } catch (e) { msg.value = e.response?.data?.msg || '操作失败' }
  finally { loading.value = false }
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>
    <div class="login-card">
      <div class="login-logo">📊</div>
      <h1>DataInsight</h1>
      <p class="subtitle">AI 数据分析平台</p>
      <div class="tab-switch">
        <button :class="{ active: isLogin }" @click="isLogin = true">登录</button>
        <button :class="{ active: !isLogin }" @click="isLogin = false">注册</button>
      </div>
      <div v-if="msg" class="error-msg">{{ msg }}</div>
      <div class="input-group">
        <span class="input-icon">👤</span>
        <input v-model="form.username" placeholder="用户名" />
      </div>
      <div class="input-group">
        <span class="input-icon">🔒</span>
        <input v-model="form.password" type="password" placeholder="密码" />
      </div>
      <div v-if="!isLogin" class="input-group">
        <span class="input-icon">📧</span>
        <input v-model="form.email" placeholder="邮箱（选填）" />
      </div>
      <button class="btn-submit" :disabled="loading" @click="submit">
        <span v-if="loading" class="spinner"></span>
        {{ loading ? '' : (isLogin ? '登 录' : '注 册') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
  position: relative;
  overflow: hidden;
}
.login-bg-shapes .shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.08;
}
.shape-1 { width: 600px; height: 600px; background: #409EFF; top: -200px; right: -100px; }
.shape-2 { width: 400px; height: 400px; background: #67C23A; bottom: -100px; left: -50px; }
.shape-3 { width: 300px; height: 300px; background: #E6A23C; top: 50%; left: 50%; transform: translate(-50%, -50%); }
.login-card {
  background: rgba(255,255,255,0.97);
  padding: 48px 40px;
  border-radius: 20px;
  width: 400px;
  text-align: center;
  box-shadow: 0 25px 60px rgba(0,0,0,0.3);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(20px);
}
.login-logo { font-size: 48px; margin-bottom: 8px; }
h1 { margin: 0; font-size: 26px; color: #1e293b; font-weight: 700; }
.subtitle { color: #94a3b8; font-size: 14px; margin: 4px 0 24px; }
.tab-switch {
  display: flex;
  background: #f1f5f9;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 24px;
}
.tab-switch button {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 15px;
  color: #64748b;
  transition: all 0.3s;
}
.tab-switch button.active {
  background: #409EFF;
  color: white;
  box-shadow: 0 4px 12px rgba(64,158,255,0.4);
}
.error-msg {
  background: #FEF0F0;
  color: #F56C6C;
  padding: 10px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
}
.input-group {
  display: flex;
  align-items: center;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 14px;
  transition: border-color 0.3s;
}
.input-group:focus-within { border-color: #409EFF; }
.input-icon { padding: 0 12px; font-size: 18px; opacity: 0.5; }
.input-group input {
  flex: 1;
  padding: 14px 12px 14px 0;
  border: none;
  outline: none;
  font-size: 15px;
  background: transparent;
  color: #1e293b;
}
.btn-submit {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #409EFF, #3b82f6);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 17px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 8px;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50px;
}
.btn-submit:hover { transform: translateY(-2px); box-shadow: 0 8px 25px rgba(64,158,255,0.45); }
.btn-submit:disabled { opacity: 0.7; transform: none; }
.spinner {
  width: 22px; height: 22px;
  border: 3px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
