<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = ref({ account: '', password: '' })
const error = ref('')
const submitting = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!form.value.account || !form.value.password) {
    error.value = '请填写邮箱或用户名和密码'
    return
  }
  submitting.value = true
  try {
    await authStore.login(form.value.account, form.value.password)
    router.push(route.query.redirect || '/')
  } catch (e) {
    error.value = e.response?.data?.message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>登录</h1>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>邮箱或用户名</label>
          <input v-model="form.account" type="text" placeholder="you@example.com 或 用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <div v-if="error" class="msg-bar error">{{ error }}</div>
        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="submitting">
            {{ submitting ? '登录中...' : '登录' }}
          </button>
        </div>
      </form>
      <p class="auth-link">
        还没有账号？<router-link to="/register">去注册</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
}
.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.auth-card h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 28px;
  text-align: center;
}
.auth-link {
  text-align: center;
  margin: 20px 0 0;
  font-size: 14px;
  color: #888;
}
.auth-link a {
  color: var(--color-brand);
  text-decoration: none;
}
.auth-link a:hover {
  color: var(--color-brand-hover);
}
</style>
