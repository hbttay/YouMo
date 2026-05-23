<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({ email: '', password: '', confirmPassword: '' })
const error = ref('')
const submitting = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!form.value.email || !form.value.password) {
    error.value = '请填写邮箱和密码'
    return
  }
  if (form.value.password.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次密码不一致'
    return
  }
  submitting.value = true
  try {
    await authStore.register(form.value.email, form.value.password)
    router.push({ name: 'Login', query: { registered: '1' } })
  } catch (e) {
    error.value = e.response?.data?.message || '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1>注册</h1>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>邮箱</label>
          <input v-model="form.email" type="email" placeholder="you@example.com" autocomplete="email" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="至少 6 位" autocomplete="new-password" />
        </div>
        <div class="form-group">
          <label>确认密码</label>
          <input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" />
        </div>
        <div v-if="error" class="msg-bar error">{{ error }}</div>
        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="submitting">
            {{ submitting ? '注册中...' : '注册' }}
          </button>
        </div>
      </form>
      <p class="auth-link">
        已有账号？<router-link to="/login">去登录</router-link>
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
  border: 1px solid var(--color-border);
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
