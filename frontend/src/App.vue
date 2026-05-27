<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const authStore = useAuthStore()
const { isDark, toggle } = useTheme()

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <router-link to="/" class="logo">余墨 YouMo</router-link>
      <nav>
        <button class="theme-toggle" :title="isDark() ? '亮色模式' : '暗色模式'" @click="toggle">
          {{ isDark() ? '☀' : '☾' }}
        </button>
        <template v-if="authStore.isLoggedIn">
          <router-link to="/user/center" class="user-email">{{ authStore.user?.email }}</router-link>
          <router-link to="/books/create" class="btn-primary">新建书籍</router-link>
          <button class="btn-link" @click="handleLogout">退出</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-link">登录</router-link>
          <router-link to="/register" class="btn-primary">注册</router-link>
        </template>
      </nav>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-surface);
}

.logo {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  text-decoration: none;
}

.theme-toggle {
  font-size: 18px;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--text-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  vertical-align: middle;
  transition: background 0.15s;
  line-height: 1;
}
.theme-toggle:hover { background: var(--bg-surface-hover); }

.app-main {
  flex: 1;
  padding: 32px;
  max-width: 1140px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}

.user-email {
  font-size: 13px;
  color: var(--text-muted);
  margin-right: 12px;
  text-decoration: none;
  cursor: pointer;
}
.user-email:hover {
  color: var(--color-brand);
}

.btn-link {
  font-size: 14px;
  color: var(--color-brand);
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: none;
  padding: 8px 14px;
  font-family: inherit;
}

.btn-link:hover {
  color: var(--color-brand-hover);
}

@media (max-width: 640px) {
  .app-header {
    padding: 12px 16px;
    flex-wrap: wrap;
    gap: 8px;
    justify-content: center;
  }
  .logo {
    font-size: 17px;
  }
  .app-main {
    padding: 16px;
  }
  .user-email {
    font-size: 12px;
    margin-right: 8px;
  }
}
</style>
